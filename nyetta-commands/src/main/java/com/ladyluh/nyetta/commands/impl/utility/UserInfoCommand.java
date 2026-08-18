package com.ladyluh.nyetta.commands.impl.utility;

import flux.api.entities.User;
import com.ladyluh.nyetta.commands.Command;
import com.ladyluh.nyetta.commands.CommandContext;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class UserInfoCommand implements Command {
    @Override
    public String getName() {
        return "userinfo";
    }

    @Override
    public List<String> getAliases() {
        return List.of("whois", "ui");
    }

    @Override
    public String getDescription() {
        return "Show info about a user.";
    }

    @Override
    public String getUsage() {
        return "userinfo [user]";
    }

    @Override
    public boolean isGuildOnly() {
        return false;
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        if (!ctx.getArgs().isEmpty() && ctx.getArgs().getFirst().matches("<@!?[0-9]+>")) {
            String userId = ctx.getArgs().getFirst().replaceAll("[<@!>]", "");
            return ctx.getClient().getUserById(userId)
                    .thenCompose(this::showUserInfo)
                    .thenCompose(ctx::reply);
        }
        return showUserInfo(ctx.getAuthor()).thenCompose(ctx::reply);
    }

    private CompletableFuture<String> showUserInfo(User user) {
        String info = "**User info**\n" +
                "ID: " + user.getId() + "\n" +
                "Username: " + user.getUsername() + "\n" +
                "Global name: " + (user.getGlobalName() != null ? user.getGlobalName() : "N/A") + "\n" +
                "Bot: " + (user.isBot() ? "Yes" : "No");

        return CompletableFuture.completedFuture(info);
    }
}
