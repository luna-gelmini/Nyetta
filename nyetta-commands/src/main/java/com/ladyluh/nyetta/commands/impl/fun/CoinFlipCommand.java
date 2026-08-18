package com.ladyluh.nyetta.commands.impl.fun;

import com.ladyluh.nyetta.commands.Command;
import com.ladyluh.nyetta.commands.CommandContext;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

public class CoinFlipCommand implements Command {
    private final Random random = new Random();

    @Override
    public String getName() {
        return "coinflip";
    }

    @Override
    public List<String> getAliases() {
        return List.of("flip", "coin");
    }

    @Override
    public String getDescription() {
        return "Flip a coin.";
    }

    @Override
    public String getUsage() {
        return "coinflip";
    }

    @Override
    public boolean isGuildOnly() {
        return false;
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        String result = random.nextBoolean() ? "Heads" : "Tails";
        return ctx.reply("🪙 " + result);
    }
}
