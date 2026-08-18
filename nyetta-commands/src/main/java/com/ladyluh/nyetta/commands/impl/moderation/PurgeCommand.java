package com.ladyluh.nyetta.commands.impl.moderation;

import flux.api.entities.Message;
import com.ladyluh.nyetta.commands.Command;
import com.ladyluh.nyetta.commands.CommandContext;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class PurgeCommand implements Command {
    @Override
    public String getName() {
        return "purge";
    }

    @Override
    public List<String> getAliases() {
        return List.of("clear");
    }

    @Override
    public String getDescription() {
        return "Delete a number of messages.";
    }

    @Override
    public String getUsage() {
        return "purge <amount>";
    }

    @Override
    public boolean isGuildOnly() {
        return true;
    }

    @Override
    public boolean requiresAdministrator() {
        return true;
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        if (ctx.getArgs().isEmpty() || ctx.getArgs().get(0).isEmpty()) {
            return ctx.reply("Specify how many messages to delete.");
        }
        int amount;
        try {
            amount = Integer.parseInt(ctx.getArgs().get(0));
        } catch (NumberFormatException e) {
            return ctx.reply("Invalid number.");
        }

        if (amount < 2 || amount > 100) {
            return ctx.reply("You can only delete between 2 and 100 messages at a time.");
        }

        return ctx.getClient().getMessages(ctx.getChannelId(), amount)
                .thenCompose(messages -> {
                    List<String> messageIds = messages.stream().map(Message::getId).collect(Collectors.toList());
                    return ctx.getClient().bulkDeleteMessages(ctx.getChannelId(), messageIds)
                            .thenCompose(v -> ctx.reply(messageIds.size() + " messages deleted."));
                })
                .exceptionally(throwable -> {
                    ctx.reply("Failed to fetch/delete messages: " + throwable.getMessage());
                    return null;
                });
    }
}
