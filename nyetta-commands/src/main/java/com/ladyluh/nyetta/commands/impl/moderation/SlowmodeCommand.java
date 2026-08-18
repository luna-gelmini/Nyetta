package com.ladyluh.nyetta.commands.impl.moderation;

import com.ladyluh.nyetta.commands.Command;
import com.ladyluh.nyetta.commands.CommandContext;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class SlowmodeCommand implements Command {
    @Override
    public String getName() {
        return "slowmode";
    }

    @Override
    public List<String> getAliases() {
        return List.of("slow", "modolento");
    }

    @Override
    public String getDescription() {
        return "Define o intervalo entre mensagens neste canal (em segundos).";
    }

    @Override
    public String getUsage() {
        return "slowmode <segundos|off>";
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
        if (ctx.getArgs().isEmpty()) {
            return ctx.reply("Uso: `" + getUsage() + "`");
        }
        String raw = ctx.getArgs().getFirst().trim();
        int seconds;
        if (raw.equalsIgnoreCase("off") || raw.equals("0")) {
            seconds = 0;
        } else {
            try {
                seconds = Integer.parseInt(raw);
            } catch (NumberFormatException e) {
                return ctx.reply("Valor inválido. Use um número de segundos ou `off`.");
            }
        }
        if (seconds < 0 || seconds > 21600) {
            return ctx.reply("Slowmode deve ser entre 0 e 21600 segundos.");
        }

        String body = "{\"rate_limit_per_user\":" + seconds + "}";
        return ctx.getClient().rest().channels.updateChannel(ctx.getChannelId(), body)
                .thenCompose(ignored -> ctx.reply(seconds == 0
                        ? "Slowmode desativado."
                        : "Slowmode definido para " + seconds + "s."));
    }
}
