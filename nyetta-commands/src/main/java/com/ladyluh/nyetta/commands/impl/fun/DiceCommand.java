package com.ladyluh.nyetta.commands.impl.fun;

import com.ladyluh.nyetta.commands.Command;
import com.ladyluh.nyetta.commands.CommandContext;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

public class DiceCommand implements Command {
    private final Random random = new Random();

    @Override
    public String getName() {
        return "dice";
    }

    @Override
    public List<String> getAliases() {
        return List.of("roll");
    }

    @Override
    public String getDescription() {
        return "Rola um dado de 6 lados.";
    }

    @Override
    public String getUsage() {
        return "dice";
    }

    @Override
    public boolean isGuildOnly() {
        return false;
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        int result = random.nextInt(6) + 1;
        return ctx.reply("🎲 You rolled a " + result);
    }
}
