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
        return List.of("ci", "canal");
    }

    @Override
    public String getDescription() {
        return "Mostra informações deste canal (ou de um ID).";
    }

    @Override
    public String getUsage() {
        return "channelinfo [canal]";
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
                    ctx.reply("Não encontrei esse canal.");
                    return null;
                });
    }

    private static String format(Channel channel) {
        String type = channel.getType() == null ? "desconhecido" : channel.getType().name();
        return "**Canal**\n"
                + "Nome: " + channel.getName() + "\n"
                + "ID: `" + channel.getId() + "`\n"
                + "Tipo: " + type;
    }
}
