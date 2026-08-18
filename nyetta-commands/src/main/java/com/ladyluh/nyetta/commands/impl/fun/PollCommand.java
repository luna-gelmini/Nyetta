package com.ladyluh.nyetta.commands.impl.fun;

import com.ladyluh.nyetta.commands.Command;
import com.ladyluh.nyetta.commands.CommandContext;
import flux.api.entities.Message;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class PollCommand implements Command {
    private static final String[] NUMBER_EMOJIS = {
            "1️⃣", "2️⃣", "3️⃣", "4️⃣", "5️⃣", "6️⃣", "7️⃣", "8️⃣", "9️⃣", "🔟"
    };
    private static final String[] YES_NO_EMOJIS = {"👍", "👎"};

    @Override
    public String getName() {
        return "poll";
    }

    @Override
    public List<String> getAliases() {
        return List.of("vote");
    }

    @Override
    public String getDescription() {
        return "Create a reaction poll.";
    }

    @Override
    public String getUsage() {
        return "poll <question> [| option 1 | option 2 ...]";
    }

    @Override
    public boolean isGuildOnly() {
        return true;
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        if (ctx.getArgs().isEmpty()) {
            return usage(ctx);
        }
        String raw = String.join(" ", ctx.getArgs()).trim();
        if (!raw.contains("|")) {
            return postPoll(ctx, raw, List.of(), YES_NO_EMOJIS);
        }
        List<String> parts = splitOptions(ctx.getArgs());
        if (parts.size() < 3) {
            return usage(ctx);
        }
        String question = parts.getFirst();
        List<String> options = parts.subList(1, parts.size());
        if (options.size() > NUMBER_EMOJIS.length) {
            return ctx.reply("At most " + NUMBER_EMOJIS.length + " options.");
        }
        return postPoll(ctx, question, options, Arrays.copyOf(NUMBER_EMOJIS, options.size()));
    }

    private CompletableFuture<Void> usage(CommandContext ctx) {
        return ctx.reply("Usage: `" + ctx.getConfig().getCommandPrefix() + getUsage() + "`");
    }

    private CompletableFuture<Void> postPoll(CommandContext ctx, String question, List<String> options, String[] emojis) {
        StringBuilder body = new StringBuilder("📊 **").append(question).append("**");
        for (int i = 0; i < options.size(); i++) {
            body.append("\n").append(emojis[i]).append(" ").append(options.get(i));
        }
        return ctx.getClient().sendMessage(ctx.getChannelId(), body.toString())
                .thenCompose(message -> addReactions(ctx, message, emojis))
                .thenAccept(v -> {
                });
    }

    private CompletableFuture<Void> addReactions(CommandContext ctx, Message message, String[] emojis) {
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (String emoji : emojis) {
            chain = chain.thenCompose(v -> ctx.getClient().rest().channels
                    .addReaction(ctx.getChannelId(), message.getId(), emoji, Map.of())
                    .thenApply(ignored -> null));
        }
        return chain;
    }

    static List<String> splitOptions(List<String> args) {
        if (args == null || args.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(String.join(" ", args).split("\\|"))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
