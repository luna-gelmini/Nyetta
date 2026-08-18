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
    private static final String[] EMOJIS = {
            "1️⃣", "2️⃣", "3️⃣", "4️⃣", "5️⃣", "6️⃣", "7️⃣", "8️⃣", "9️⃣", "🔟"
    };

    @Override
    public String getName() {
        return "poll";
    }

    @Override
    public List<String> getAliases() {
        return List.of("enquete", "voto");
    }

    @Override
    public String getDescription() {
        return "Cria uma enquete com reações.";
    }

    @Override
    public String getUsage() {
        return "poll <pergunta> | <opção 1> | <opção 2> [| ...]";
    }

    @Override
    public boolean isGuildOnly() {
        return true;
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        List<String> parts = splitOptions(ctx.getArgs());
        if (parts.size() < 3) {
            return ctx.reply("Uso: `" + getUsage() + "`");
        }
        String question = parts.getFirst();
        List<String> options = parts.subList(1, parts.size());
        if (options.size() > EMOJIS.length) {
            return ctx.reply("No máximo " + EMOJIS.length + " opções.");
        }

        StringBuilder body = new StringBuilder("📊 **").append(question).append("**\n");
        for (int i = 0; i < options.size(); i++) {
            body.append(EMOJIS[i]).append(" ").append(options.get(i)).append("\n");
        }

        return ctx.getClient().sendMessage(ctx.getChannelId(), body.toString().trim())
                .thenCompose(message -> addReactions(ctx, message, options.size()))
                .thenAccept(v -> {
                });
    }

    private CompletableFuture<Void> addReactions(CommandContext ctx, Message message, int count) {
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (int i = 0; i < count; i++) {
            final String emoji = EMOJIS[i];
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
