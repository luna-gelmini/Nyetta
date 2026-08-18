package com.ladyluh.nyetta.commands.impl.fun;

import com.ladyluh.nyetta.commands.Command;
import com.ladyluh.nyetta.commands.CommandContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class ChooseCommand implements Command {
    @Override
    public String getName() {
        return "choose";
    }

    @Override
    public List<String> getAliases() {
        return List.of("pick");
    }

    @Override
    public String getDescription() {
        return "Pick a random option.";
    }

    @Override
    public String getUsage() {
        return "choose <option 1> | <option 2> [| ...]";
    }

    @Override
    public boolean isGuildOnly() {
        return false;
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        List<String> options = Arrays.stream(String.join(" ", ctx.getArgs()).split("\\|"))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .collect(Collectors.toCollection(ArrayList::new));
        if (options.size() < 2) {
            return ctx.reply("Usage: `" + getUsage() + "`");
        }
        String pick = options.get(ThreadLocalRandom.current().nextInt(options.size()));
        return ctx.reply("I pick: **" + pick + "**");
    }
}
