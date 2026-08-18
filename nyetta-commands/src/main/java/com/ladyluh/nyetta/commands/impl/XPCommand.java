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
        return "Show your XP status or the server ranking.";
    }

    @Override
    public String getUsage() {
        return "xp [@user] / xp top";
    }

    @Override
    public boolean isGuildOnly() {
        return true;
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        if (ctx.getArgs().isEmpty()) {
            return handleXPStatus(ctx, ctx.getAuthor());
        }
        String first = ctx.getArgs().getFirst();
        if (first.equalsIgnoreCase("top")) {
            return handleXPLeaderboard(ctx);
        }
        if (first.matches("<@!?[0-9]+>")) {
            String mentionedUserId = first.replaceAll("[<@!>]", "");
            return ctx.getClient().getUserById(mentionedUserId)
                    .thenCompose(user -> {
                        if (user == null) {
                            return ctx.reply("Could not find that user.");
                        }
                        return handleXPStatus(ctx, user);
                    });
        }
        String prefix = ctx.getConfig().getCommandPrefix();
        return ctx.reply("Usage: `" + prefix + "xp [@user]` or `" + prefix + "xp top`");
    }

    private CompletableFuture<Void> handleXPStatus(CommandContext ctx, User targetUser) {
        String guildId = ctx.getGuildId();
        if (guildId == null)
            return ctx.reply("This command can only be used in a server.");

        return dbManager.getUserXP(guildId, targetUser.getId()).thenCompose(userXP -> {
            int xpRemainingForNextLevel = UserXP.calculateXpForLevel(userXP.getLevel() + 1) - userXP.getXp();
            String xpStatus = String.format("Level **%d** (XP: %d/%d)", userXP.getLevel(), userXP.getXp(),
                    UserXP.calculateXpForLevel(userXP.getLevel() + 1));

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("📊 XP status for " + escapeMarkdown(
                            targetUser.getGlobalName() != null ? targetUser.getGlobalName() : targetUser.getUsername()))
                    .setDescription(xpStatus)
                    .setColor(Color.BLUE)
                    .setThumbnail(targetUser.getEffectiveAvatarUrl())
                    .addField("XP until next level", xpRemainingForNextLevel + " XP", true)
                    .setTimestamp(OffsetDateTime.now());

            return ctx.getClient().sendMessage(ctx.getChannelId(), new MessageBuilder().addEmbed(embed).build())
                    .thenAccept(m -> {
                    });
        });
    }

    private CompletableFuture<Void> handleXPLeaderboard(CommandContext ctx) {
        String guildId = ctx.getGuildId();
        if (guildId == null)
            return ctx.reply("This command can only be used in a server.");

        return dbManager.getTopXPUsers(guildId, 10).thenCompose(topUsers -> {
            if (topUsers.isEmpty()) {
                return ctx.reply("Nobody has earned XP in this server yet!");
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
