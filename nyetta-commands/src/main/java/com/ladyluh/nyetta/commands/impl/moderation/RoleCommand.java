package com.ladyluh.nyetta.commands.impl.moderation;

import com.ladyluh.nyetta.commands.Command;
import com.ladyluh.nyetta.commands.CommandContext;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class RoleCommand implements Command {
    @Override
    public String getName() {
        return "role";
    }

    @Override
    public List<String> getAliases() {
        return List.of();
    }

    @Override
    public String getDescription() {
        return "Add or remove a member's role.";
    }

    @Override
    public String getUsage() {
        return "role <add|remove> <user> <role>";
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
        if (ctx.getArgs().size() < 3) {
            return ctx.reply("Usage: `" + getUsage() + "`");
        }
        String action = ctx.getArgs().getFirst().toLowerCase();
        String userId = stripMention(ctx.getArgs().get(1));
        String roleId = stripMention(ctx.getArgs().get(2));
        if (userId.isEmpty() || roleId.isEmpty()) {
            return ctx.reply("Usage: `" + getUsage() + "`");
        }

        return switch (action) {
            case "add" -> ctx.getClient().addRoleToMember(ctx.getGuildId(), userId, roleId)
                    .thenCompose(v -> ctx.reply("Added <@&" + roleId + "> to <@" + userId + ">."));
            case "remove", "rem" -> ctx.getClient().removeRoleFromMember(ctx.getGuildId(), userId, roleId)
                    .thenCompose(v -> ctx.reply("Removed <@&" + roleId + "> from <@" + userId + ">."));
            default -> ctx.reply("Usage: `" + getUsage() + "`");
        };
    }

    private static String stripMention(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("[<@&!>]", "");
    }
}
