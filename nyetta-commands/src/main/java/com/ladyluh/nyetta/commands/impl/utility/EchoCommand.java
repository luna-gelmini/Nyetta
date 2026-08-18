package com.ladyluh.nyetta.commands.impl.utility;

import com.ladyluh.nyetta.commands.Command;
import com.ladyluh.nyetta.commands.CommandContext;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class EchoCommand implements Command {
    @Override
    public String getName() {
        return "echo";
    }

    @Override
    public List<String> getAliases() {
        return List.of("say", "falar");
    }

    @Override
    public String getDescription() {
        return "Envia uma mensagem como o bot.";
    }

    @Override
    public String getUsage() {
        return "echo <texto>";
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
        String text = String.join(" ", ctx.getArgs()).trim();
        if (text.isEmpty()) {
            return ctx.reply("Uso: `" + getUsage() + "`");
        }
        return ctx.getClient().sendMessage(ctx.getChannelId(), text).thenAccept(m -> {
        });
    }
}
