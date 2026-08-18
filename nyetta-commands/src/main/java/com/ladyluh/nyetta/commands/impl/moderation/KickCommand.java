package com.ladyluh.nyetta.commands.impl.moderation;

import com.ladyluh.nyetta.commands.Command;
import com.ladyluh.nyetta.commands.CommandContext;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class KickCommand implements Command {
    @Override
    public String getName() {
        return "kick";
    }

    @Override
    public List<String> getAliases() {
        return Collections.emptyList();
    }

    @Override
    public String getDescription() {
        return "Expulsa um usuário do servidor.";
    }

    @Override
    public String getUsage() {
        return "kick <user> [reason]";
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
            return ctx.reply("Uso: " + getUsage());
        }
        String userId = ctx.getArgs().get(0).replaceAll("[<@!>]", "");
        String reasonArg = ctx.getArgs().size() > 1 ? String.join(" ", ctx.getArgs().subList(1, ctx.getArgs().size()))
                : null;
        final String reason = reasonArg != null ? reasonArg : "Sem motivo fornecido";

        return ctx.getClient().kickMember(ctx.getGuildId(), userId, reason)
                .thenCompose(v -> ctx.reply("Usuário <@" + userId + "> expulso. Motivo: " + reason))
                .exceptionally(t -> {
                    ctx.reply("Falha ao expulsar usuário: " + t.getMessage());
                    return null;
                });
    }
}
