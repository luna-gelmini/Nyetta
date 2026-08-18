package com.ladyluh.nyetta.commands.impl.utility;

import com.ladyluh.nyetta.commands.Command;
import com.ladyluh.nyetta.commands.CommandContext;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ServerInfoCommand implements Command {
    @Override
    public String getName() {
        return "serverinfo";
    }

    @Override
    public List<String> getAliases() {
        return List.of("si", "guildinfo");
    }

    @Override
    public String getDescription() {
        return "Mostra informações sobre o servidor.";
    }

    @Override
    public String getUsage() {
        return "serverinfo";
    }

    @Override
    public boolean isGuildOnly() {
        return true;
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        return ctx.getClient().getGuildById(ctx.getGuildId())
                .thenCompose(guild -> {
                    String info = "**Informações do Servidor**\n" +
                            "Nome: " + guild.getName() + "\n" +
                            "ID: " + guild.getId() + "\n" +
                            "Owner ID: " + guild.getOwnerId();
                    return ctx.reply(info);
                })
                .exceptionally(throwable -> {
                    ctx.reply("Falha ao buscar informações: " + throwable.getMessage());
                    return null;
                });
    }
}
