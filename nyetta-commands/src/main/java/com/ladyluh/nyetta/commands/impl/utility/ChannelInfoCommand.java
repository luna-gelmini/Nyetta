package com.ladyluh.nyetta.commands.impl.utility;

import com.ladyluh.nyetta.commands.Command;
import com.ladyluh.nyetta.commands.CommandContext;
import flux.api.entities.channel.Channel;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ChannelInfoCommand implements Command {
    @Override
    public String getName() {
        return "channelinfo";
    }

    @Override
    public List<String> getAliases() {
        return List.of("ci");
    }

    @Override
    public String getDescription() {
        return "Show info about this channel (or an ID).";
    }

    @Override
    public String getUsage() {
        return "channelinfo [channel]";
    }

    @Override
    public boolean isGuildOnly() {
        return true;
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        String channelId = ctx.getChannelId();
        if (!ctx.getArgs().isEmpty()) {
            channelId = ctx.getArgs().getFirst().replaceAll("[<# >]", "");
        }
        return ctx.getClient().getChannelById(channelId)
                .thenCompose(channel -> ctx.reply(format(channel)))
                .exceptionally(ex -> {
                    ctx.reply("Could not find that channel.");
                    return null;
                });
    }

    private static String format(Channel channel) {
        String type = channel.getType() == null ? "unknown" : channel.getType().name();
        return "**Channel**\n"
                + "Name: " + channel.getName() + "\n"
                + "ID: `" + channel.getId() + "`\n"
                + "Type: " + type;
    }
}
