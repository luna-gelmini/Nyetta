package com.ladyluh.nyetta.commands.impl.moderation;

import com.ladyluh.nyetta.commands.Command;
import com.ladyluh.nyetta.commands.CommandContext;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class MuteCommand implements Command {
    @Override
    public String getName() {
        return "mute";
    }

    @Override
    public List<String> getAliases() {
        return List.of("timeout");
    }

    @Override
    public String getDescription() {
        return "Silencia um usuário por um tempo determinado (em segundos).";
    }

    @Override
    public String getUsage() {
        return "mute <user> <seconds> [reason]";
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
        if (ctx.getArgs().size() < 2) {
            return ctx.reply("Uso: " + getUsage());
        }
        String userId = ctx.getArgs().get(0).replaceAll("[<@!>]", "");
        int seconds;
        try {
            seconds = Integer.parseInt(ctx.getArgs().get(1));
        } catch (NumberFormatException e) {
            return ctx.reply("Duração inválida.");
        }
        String reasonArg = ctx.getArgs().size() > 2 ? String.join(" ", ctx.getArgs().subList(2, ctx.getArgs().size()))
                : null;
        final String reason = reasonArg != null ? reasonArg : "Sem motivo fornecido";

        return ctx.getClient().timeoutMember(ctx.getGuildId(), userId, seconds, reason)
                .thenCompose(v -> ctx
                        .reply("Usuário <@" + userId + "> silenciado por " + seconds + " segundos. Motivo: " + reason))
                .exceptionally(t -> {
                    ctx.reply("Falha ao silenciar usuário: " + t.getMessage());
                    return null;
                });
    }
}
