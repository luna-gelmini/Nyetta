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
        return List.of("say");
    }

    @Override
    public String getDescription() {
        return "Send a message as the bot.";
    }

    @Override
    public String getUsage() {
        return "echo <text>";
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
            return ctx.reply("Usage: `" + getUsage() + "`");
        }
        String text = String.join(" ", ctx.getArgs()).trim();
        if (text.isEmpty()) {
            return ctx.reply("Usage: `" + getUsage() + "`");
        }
        return ctx.getClient().sendMessage(ctx.getChannelId(), text).thenAccept(m -> {
        });
    }
}
