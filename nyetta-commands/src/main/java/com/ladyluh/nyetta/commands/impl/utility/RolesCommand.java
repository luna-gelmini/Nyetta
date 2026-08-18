package com.ladyluh.nyetta.commands.impl.utility;

import com.ladyluh.nyetta.commands.Command;
import com.ladyluh.nyetta.commands.CommandContext;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class RolesCommand implements Command {
    @Override
    public String getName() {
        return "roles";
    }

    @Override
    public List<String> getAliases() {
        return List.of("cargos");
    }

    @Override
    public String getDescription() {
        return "Lista os cargos do servidor.";
    }

    @Override
    public String getUsage() {
        return "roles";
    }

    @Override
    public boolean isGuildOnly() {
        return true;
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        return ctx.getClient().getGuildRoles(ctx.getGuildId())
                .thenCompose(roles -> {
                    if (roles == null || roles.isEmpty()) {
                        return ctx.reply("Este servidor não tem cargos listáveis.");
                    }
                    String list = roles.stream()
                            .sorted(Comparator.reverseOrder())
                            .map(role -> role.getName() + " (`" + role.getId() + "`)")
                            .collect(Collectors.joining("\n"));
                    if (list.length() > 1800) {
                        list = list.substring(0, 1797) + "...";
                    }
                    return ctx.reply("**Cargos** (" + roles.size() + ")\n" + list);
                });
    }
}
