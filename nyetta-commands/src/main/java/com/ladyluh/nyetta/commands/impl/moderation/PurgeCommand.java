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
        return "Deleta um número específico de mensagens.";
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
            return ctx.reply("Por favor, especifique o número de mensagens a deletar.");
        }
        int amount;
        try {
            amount = Integer.parseInt(ctx.getArgs().get(0));
        } catch (NumberFormatException e) {
            return ctx.reply("Número inválido.");
        }

        if (amount < 2 || amount > 100) {
            return ctx.reply("Você só pode deletar entre 2 e 100 mensagens por vez.");
        }

        return ctx.getClient().getMessages(ctx.getChannelId(), amount)
                .thenCompose(messages -> {
                    List<String> messageIds = messages.stream().map(Message::getId).collect(Collectors.toList());
                    return ctx.getClient().bulkDeleteMessages(ctx.getChannelId(), messageIds)
                            .thenCompose(v -> ctx.reply(messageIds.size() + " mensagens deletadas."));
                })
                .exceptionally(throwable -> {
                    ctx.reply("Falha ao buscar/deletar mensagens: " + throwable.getMessage());
                    return null;
                });
    }
}
