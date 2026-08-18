package com.ladyluh.nyetta.commands.impl.general;

import com.ladyluh.nyetta.commands.Command;
import com.ladyluh.nyetta.commands.CommandContext;
import com.ladyluh.nyetta.commands.CommandManager;
import flux.builder.EmbedBuilder;
import flux.builder.MessageBuilder;

import java.awt.Color;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class HelpCommand implements Command {
    private final CommandManager commandManager;

    public HelpCommand(CommandManager commandManager) {
        this.commandManager = commandManager;
    }

    @Override
    public String getName() {
        return "help";
    }

    @Override
    public List<String> getAliases() {
        return List.of("commands", "cmds");
    }

    @Override
    public String getDescription() {
        return "List available commands.";
    }

    @Override
    public String getUsage() {
        return "help [command]";
    }

    @Override
    public boolean isGuildOnly() {
        return false;
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        String prefix = ctx.getConfig().getCommandPrefix();
        if (!ctx.getArgs().isEmpty() && !ctx.getArgs().getFirst().isBlank()) {
            Command command = commandManager.getCommand(ctx.getArgs().getFirst());
            if (command == null) {
                return ctx.reply("Unknown command. Use `" + prefix + "help`.");
            }
            return ctx.reply("**" + prefix + command.getName() + "** — " + command.getDescription()
                    + "\nUsage: `" + prefix + command.getUsage() + "`");
        }

        String list = commandManager.listCommands().stream()
                .sorted(Comparator.comparing(Command::getName))
                .map(command -> "`" + prefix + command.getName() + "` — " + command.getDescription())
                .collect(Collectors.joining("\n"));
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Nyetta commands")
                .setColor(new Color(0x5865F2))
                .setDescription("Prefix: `" + prefix + "`\n\n" + list
                        + "\n\nDetails: `" + prefix + "help <command>`");
        return ctx.getClient()
                .sendMessage(ctx.getChannelId(), new MessageBuilder().addEmbed(embed).build())
                .thenAccept(m -> {
                });
    }
}
