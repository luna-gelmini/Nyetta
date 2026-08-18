package com.ladyluh.nyetta.commands.impl.fun;

import com.ladyluh.nyetta.commands.Command;
import com.ladyluh.nyetta.commands.CommandContext;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

public class EightBallCommand implements Command {
    private final String[] responses = {
            "It is certain.", "It is decidedly so.", "Without a doubt.", "Yes, definitely.",
            "You may rely on it.", "As I see it, yes.", "Most likely.", "Outlook good.",
            "Yes.", "Signs point to yes.", "Reply hazy, try again.", "Ask again later.",
            "Better not tell you now.", "Cannot predict now.", "Concentrate and ask again.",
            "Don't count on it.", "My reply is no.", "My sources say no.", "Outlook not so good.",
            "Very doubtful."
    };
    private final Random random = new Random();

    @Override
    public String getName() {
        return "8ball";
    }

    @Override
    public List<String> getAliases() {
        return Collections.emptyList();
    }

    @Override
    public String getDescription() {
        return "Ask the magic 8-ball.";
    }

    @Override
    public String getUsage() {
        return "8ball <question>";
    }

    @Override
    public boolean isGuildOnly() {
        return false;
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        String response = responses[random.nextInt(responses.length)];
        return ctx.reply("🎱 " + response);
    }
}
