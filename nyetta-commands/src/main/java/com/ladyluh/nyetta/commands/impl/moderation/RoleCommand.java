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
        return List.of("cargo");
    }

    @Override
    public String getDescription() {
        return "Adiciona ou remove um cargo de um membro.";
    }

    @Override
    public String getUsage() {
        return "role <add|remove> <usuário> <cargo>";
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
            return ctx.reply("Uso: `" + getUsage() + "`");
        }
        String action = ctx.getArgs().getFirst().toLowerCase();
        String userId = stripMention(ctx.getArgs().get(1));
        String roleId = stripMention(ctx.getArgs().get(2));
        if (userId.isEmpty() || roleId.isEmpty()) {
            return ctx.reply("Uso: `" + getUsage() + "`");
        }

        return switch (action) {
            case "add", "dar" -> ctx.getClient().addRoleToMember(ctx.getGuildId(), userId, roleId)
                    .thenCompose(v -> ctx.reply("Cargo <@&" + roleId + "> adicionado a <@" + userId + ">."));
            case "remove", "tirar", "rem" -> ctx.getClient().removeRoleFromMember(ctx.getGuildId(), userId, roleId)
                    .thenCompose(v -> ctx.reply("Cargo <@&" + roleId + "> removido de <@" + userId + ">."));
            default -> ctx.reply("Uso: `" + getUsage() + "`");
        };
    }

    private static String stripMention(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("[<@&!>]", "");
    }
}
