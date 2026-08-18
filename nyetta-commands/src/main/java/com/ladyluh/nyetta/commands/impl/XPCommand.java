package com.ladyluh.nyetta.commands.impl;

import flux.api.entities.User;
import flux.builder.EmbedBuilder;
import flux.builder.MessageBuilder;
import com.ladyluh.nyetta.commands.Command;
import com.ladyluh.nyetta.commands.CommandContext;
import com.ladyluh.nyetta.database.DatabaseManager;
import com.ladyluh.nyetta.database.UserXP;
import com.ladyluh.nyetta.services.ScoreboardImageService;

import java.awt.*;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class XPCommand implements Command {
    private final DatabaseManager dbManager;
    private final ScoreboardImageService scoreboardImageService;

    public XPCommand(DatabaseManager dbManager, ScoreboardImageService scoreboardImageService) {
        this.dbManager = dbManager;
        this.scoreboardImageService = scoreboardImageService;
    }

    @Override
    public String getName() {
        return "xp";
    }

    @Override
    public List<String> getAliases() {
        return List.of("level");
    }

    @Override
    public String getDescription() {
        return "Mostra seu status de XP ou o ranking do servidor.";
    }

    @Override
    public String getUsage() {
        return "xp [@membro] / xp top";
    }

    @Override
    public boolean isGuildOnly() {
        return true;
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        if (Objects.equals(ctx.getArgs().getFirst(), "")) {
            return handleXPStatus(ctx, ctx.getAuthor());
        } else if (ctx.getArgs().getFirst().equalsIgnoreCase("top")) {
            return handleXPLeaderboard(ctx);
        } else if (ctx.getArgs().getFirst().matches("<@!?[0-9]+>")) {
            String mentionedUserId = ctx.getArgs().getFirst().replaceAll("[<@!>]", "");
            return ctx.getClient().getUserById(mentionedUserId)
                    .thenCompose(user -> {
                        if (user == null) {
                            return ctx.reply("Não consegui encontrar esse usuário.");
                        }
                        return handleXPStatus(ctx, user);
                    });
        } else {
            return ctx.reply("Uso: `!xp [@membro]` ou `!xp top`");
        }
    }

    private CompletableFuture<Void> handleXPStatus(CommandContext ctx, User targetUser) {
        String guildId = ctx.getGuildId();
        if (guildId == null)
            return ctx.reply("Este comando requer estar em um servidor.");

        return dbManager.getUserXP(guildId, targetUser.getId()).thenCompose(userXP -> {
            int xpRemainingForNextLevel = UserXP.calculateXpForLevel(userXP.getLevel() + 1) - userXP.getXp();
            String xpStatus = String.format("Nível **%d** (XP: %d/%d)", userXP.getLevel(), userXP.getXp(),
                    UserXP.calculateXpForLevel(userXP.getLevel() + 1));

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("📊 Status de XP de " + escapeMarkdown(
                            targetUser.getGlobalName() != null ? targetUser.getGlobalName() : targetUser.getUsername()))
                    .setDescription(xpStatus)
                    .setColor(Color.BLUE)
                    .setThumbnail(targetUser.getEffectiveAvatarUrl())
                    .addField("XP Faltando para o Próximo Nível", xpRemainingForNextLevel + " XP", true)
                    .setTimestamp(OffsetDateTime.now());

            return ctx.getClient().sendMessage(ctx.getChannelId(), new MessageBuilder().addEmbed(embed).build())
                    .thenAccept(m -> {
                    });
        });
    }

    private CompletableFuture<Void> handleXPLeaderboard(CommandContext ctx) {
        String guildId = ctx.getGuildId();
        if (guildId == null)
            return ctx.reply("Este comando requer estar em um servidor.");

        return dbManager.getTopXPUsers(guildId, 10).thenCompose(topUsers -> {
            if (topUsers.isEmpty()) {
                return ctx.reply("Ninguém ganhou XP ainda neste servidor!");
            }

            return scoreboardImageService
                    .generateLeaderboardImage(topUsers, userId -> ctx.getClient().getUserById(userId))
                    .thenCompose(imageBytes -> {
                        return ctx.getClient().sendMessage(ctx.getChannelId(), new MessageBuilder()
                                .addAttachment("leaderboard.png", imageBytes)
                                .build());
                    })
                    .thenAccept(m -> {
                    });
        });
    }

    private static String escapeMarkdown(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        StringBuilder escapedText = new StringBuilder();
        for (char c : text.toCharArray()) {
            switch (c) {
                case '*':
                case '_':
                case '~':
                case '`':
                case '|':
                case '[':
                case ']':
                case '(':
                case ')':
                case '\\':
                    escapedText.append('\\');
                    escapedText.append(c);
                    break;
                default:
                    escapedText.append(c);
                    break;
            }
        }
        return escapedText.toString();
    }
}
