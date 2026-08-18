package com.ladyluh.nyetta.commands.impl;

import flux.builder.MessageBuilder;
import com.ladyluh.nyetta.commands.Command;
import com.ladyluh.nyetta.commands.CommandContext;
import com.ladyluh.nyetta.database.DatabaseManager;
import com.ladyluh.nyetta.services.ScoreboardImageService;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class XPTopCommand implements Command {
    private final DatabaseManager dbManager;
    private final ScoreboardImageService scoreboardImageService;

    public XPTopCommand(DatabaseManager dbManager, ScoreboardImageService scoreboardImageService) {
        this.dbManager = dbManager;
        this.scoreboardImageService = scoreboardImageService;
    }

    @Override
    public String getName() {
        return "xptop";
    }

    @Override
    public List<String> getAliases() {
        return List.of("leaderboard", "ranking");
    }

    @Override
    public String getDescription() {
        return "Mostra o ranking de XP do servidor.";
    }

    @Override
    public String getUsage() {
        return "xptop";
    }

    @Override
    public boolean isGuildOnly() {
        return true;
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        String guildId = ctx.getGuildId();
        if (guildId == null)
            return ctx.reply("Este comando requer estar em um servidor.");

        return dbManager.getTopXPUsers(guildId, 10).thenCompose(topUsers -> {
            if (topUsers.isEmpty()) {
                return ctx.reply("Ninguém ganhou XP ainda neste servidor!");
            }

            return scoreboardImageService
                    .generateLeaderboardImage(topUsers, userId -> ctx.getClient().getUserById(userId))
                    .thenCompose(imageBytes -> ctx.getClient().sendMessage(ctx.getChannelId(),
                            new MessageBuilder()
                                    .addAttachment("leaderboard.png", imageBytes)
                                    .build()))
                    .thenAccept(m -> {
                    });
        });
    }
}
