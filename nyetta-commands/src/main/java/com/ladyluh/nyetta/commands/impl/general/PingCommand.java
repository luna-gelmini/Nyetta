package com.ladyluh.nyetta.commands.impl.general;

import com.ladyluh.nyetta.commands.Command;
import com.ladyluh.nyetta.commands.CommandContext;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class PingCommand implements Command {
    @Override
    public String getName() {
        return "ping";
    }

    @Override
    public List<String> getAliases() {
        return List.of("pong");
    }

    @Override
    public String getDescription() {
        return "Check if the bot is online.";
    }

    @Override
    public String getUsage() {
        return "ping";
    }

    @Override
    public boolean isGuildOnly() {
        return false;
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        long start = System.currentTimeMillis();
        return ctx.getClient().sendMessage(ctx.getChannelId(), "Pong")
                .thenAccept(m -> {
                    long ms = System.currentTimeMillis() - start;
                    ctx.getClient().editMessage(ctx.getChannelId(), m.getId(),
                            new flux.builder.MessageBuilder("Pong — " + ms + "ms").build());
                });
    }
}
