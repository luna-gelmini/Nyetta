package com.ladyluh.nyetta.commands.impl.utility;

import com.ladyluh.nyetta.commands.Command;
import com.ladyluh.nyetta.commands.CommandContext;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class BotInfoCommand implements Command {
    @Override
    public String getName() {
        return "botinfo";
    }

    @Override
    public List<String> getAliases() {
        return List.of("bi", "info");
    }

    @Override
    public String getDescription() {
        return "Show info about the bot.";
    }

    @Override
    public String getUsage() {
        return "botinfo";
    }

    @Override
    public boolean isGuildOnly() {
        return false;
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        String info = "**Nyetta Info**\n" +
                "Library: Fluxer4J\n" +
                "Developer: LadyLuh\n" +
                "Version: 0.1.0";
        return ctx.reply(info);
    }
}
