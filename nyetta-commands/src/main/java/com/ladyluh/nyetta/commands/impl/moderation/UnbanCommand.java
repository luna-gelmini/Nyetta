package com.ladyluh.nyetta.commands.impl.moderation;

import com.ladyluh.nyetta.commands.Command;
import com.ladyluh.nyetta.commands.CommandContext;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class UnbanCommand implements Command {
    @Override
    public String getName() {
        return "unban";
    }

    @Override
    public List<String> getAliases() {
        return List.of();
    }

    @Override
    public String getDescription() {
        return "Unban a user.";
    }

    @Override
    public String getUsage() {
        return "unban <userId>";
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
        String userId = ctx.getArgs().getFirst().replaceAll("[<@!>]", "");
        if (userId.isBlank()) {
            return ctx.reply("Usage: `" + getUsage() + "`");
        }
        return ctx.getClient().rest().guilds.unbanGuildMember(ctx.getGuildId(), userId)
                .thenCompose(ignored -> ctx.reply("Unbanned `" + userId + "`."));
    }
}
