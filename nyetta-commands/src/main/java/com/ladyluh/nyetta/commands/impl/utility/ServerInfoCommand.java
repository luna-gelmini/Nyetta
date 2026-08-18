package com.ladyluh.nyetta.commands.impl.utility;

import com.ladyluh.nyetta.commands.Command;
import com.ladyluh.nyetta.commands.CommandContext;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ServerInfoCommand implements Command {
    @Override
    public String getName() {
        return "serverinfo";
    }

    @Override
    public List<String> getAliases() {
        return List.of("si", "guildinfo");
    }

    @Override
    public String getDescription() {
        return "Show info about this server.";
    }

    @Override
    public String getUsage() {
        return "serverinfo";
    }

    @Override
    public boolean isGuildOnly() {
        return true;
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        return ctx.getClient().getGuildById(ctx.getGuildId())
                .thenCompose(guild -> {
                    String info = "**Server info**\n" +
                            "Name: " + guild.getName() + "\n" +
                            "ID: " + guild.getId() + "\n" +
                            "Owner ID: " + guild.getOwnerId();
                    return ctx.reply(info);
                })
                .exceptionally(throwable -> {
                    ctx.reply("Failed to fetch info: " + throwable.getMessage());
                    return null;
                });
    }
}
