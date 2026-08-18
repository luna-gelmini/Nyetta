package com.ladyluh.nyetta.commands.impl.utility;

import flux.api.entities.User;
import com.ladyluh.nyetta.commands.Command;
import com.ladyluh.nyetta.commands.CommandContext;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class AvatarCommand implements Command {
    @Override
    public String getName() {
        return "avatar";
    }

    @Override
    public List<String> getAliases() {
        return List.of("av");
    }

    @Override
    public String getDescription() {
        return "Mostra o avatar de um usuário.";
    }

    @Override
    public String getUsage() {
        return "avatar [user]";
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
                    .thenCompose(user -> {
                        if (user == null) {
                            return ctx.reply("Usuário não encontrado.");
                        }
                        return ctx.reply(user.getUsername() + "'s Avatar: " + user.getEffectiveAvatarUrl());
                    });
        }
        User user = ctx.getAuthor();
        return ctx.reply(user.getUsername() + "'s Avatar: " + user.getEffectiveAvatarUrl());
    }
}
