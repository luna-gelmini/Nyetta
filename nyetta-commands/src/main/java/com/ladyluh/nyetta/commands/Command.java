package com.ladyluh.nyetta.commands;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface Command {
    String getName();

    List<String> getAliases();

    String getDescription();

    String getUsage();

    boolean isGuildOnly();

    default boolean requiresAdministrator() {
        return false;
    }

    CompletableFuture<Void> execute(CommandContext ctx);
}
