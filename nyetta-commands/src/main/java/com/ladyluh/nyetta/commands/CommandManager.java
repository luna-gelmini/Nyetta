package com.ladyluh.nyetta.commands;

import flux.api.FluxClient;
import com.ladyluh.nyetta.cache.SnipeCache;
import com.ladyluh.nyetta.cache.VoiceStateCacheManager;
import com.ladyluh.nyetta.commands.impl.*;
import com.ladyluh.nyetta.commands.impl.fun.*;
import com.ladyluh.nyetta.commands.impl.moderation.*;
import com.ladyluh.nyetta.commands.impl.utility.*;
import com.ladyluh.nyetta.commands.impl.general.HelpCommand;
import com.ladyluh.nyetta.commands.impl.general.PingCommand;
import com.ladyluh.nyetta.commands.impl.general.StatsCommand;
import com.ladyluh.nyetta.config.ConfigManager;
import com.ladyluh.nyetta.database.DatabaseManager;
import flux.model.gateway.MessageCreateEvent;
import com.ladyluh.nyetta.services.ScoreboardImageService;
import com.ladyluh.nyetta.services.TemporaryChannelTreeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommandManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommandManager.class);
    private final Map<String, Command> commands = new HashMap<>();
    private final List<Command> uniqueCommands = new ArrayList<>();
    private final FluxClient client;
    private final ConfigManager config;
    private final DatabaseManager dbManager;
    private final VoiceStateCacheManager voiceStateCacheManager;

    public CommandManager(FluxClient client, ConfigManager config, DatabaseManager dbManager,
            VoiceStateCacheManager voiceStateCacheManager,
            TemporaryChannelTreeService treeService,
            SnipeCache snipeCache) {
        this.client = client;
        this.config = config;
        this.dbManager = dbManager;
        this.voiceStateCacheManager = voiceStateCacheManager;
        ScoreboardImageService scoreboardImageService = new ScoreboardImageService();
        addCommand(new HelpCommand(this));
        addCommand(new PingCommand());
        addCommand(new XPCommand(dbManager, scoreboardImageService));
        addCommand(new XPTopCommand(dbManager, scoreboardImageService));
        addCommand(new ConfigCommand(dbManager));
        addCommand(new RoomCommand(dbManager, treeService));
        addCommand(new BanCommand());
        addCommand(new KickCommand());
        addCommand(new MuteCommand());
        addCommand(new PurgeCommand());
        addCommand(new UserInfoCommand());
        addCommand(new ServerInfoCommand());
        addCommand(new AvatarCommand());
        addCommand(new BotInfoCommand());
        addCommand(new EchoCommand());
        addCommand(new SnipeCommand(snipeCache));
        addCommand(new ChannelInfoCommand());
        addCommand(new RolesCommand());
        addCommand(new UnbanCommand());
        addCommand(new RoleCommand());
        addCommand(new SlowmodeCommand());
        addCommand(new EightBallCommand());
        addCommand(new CoinFlipCommand());
        addCommand(new DiceCommand());
        addCommand(new ChooseCommand());
        addCommand(new PollCommand());
        addCommand(new StatsCommand(client));
        LOGGER.info("registered {} commands", uniqueCommands.size());
    }

    private void addCommand(Command command) {
        uniqueCommands.add(command);
        commands.put(command.getName().toLowerCase(), command);
        for (String alias : command.getAliases()) {
            commands.put(alias.toLowerCase(), command);
        }
    }

    public Command getCommand(String name) {
        return commands.get(name.toLowerCase());
    }

    public List<Command> listCommands() {
        return List.copyOf(uniqueCommands);
    }

    public void handleCommand(String commandName, List<String> args, MessageCreateEvent event) {
        Command command = commands.get(commandName.toLowerCase());
        if (command == null) {
            return;
        }

        if (command.isGuildOnly() && event.getMessage().getGuildId() == null) {
            client.sendMessage(event.getChannelId(), "This command can only be used in a server.");
            return;
        }

        CommandContext ctx = new CommandContext(client, config, dbManager, voiceStateCacheManager, event, commandName,
                args);

        if (command.requiresAdministrator()) {
            if (ctx.getGuildId() == null) {
                client.sendMessage(event.getChannelId(), "This command can only be used in a server.");
                return;
            }
            try {
                flux.api.entities.Member member = client.getGuildMember(ctx.getGuildId(), ctx.getAuthor().getId()).join();
                if (member == null) {
                    client.sendMessage(event.getChannelId(), "Could not check your permissions.");
                    return;
                }
                LOGGER.debug("checking administrator for {} ({})", member.getEffectiveName(), member.getId());
                Boolean hasPerm = member.hasPermission(flux.api.payload.permission.Permission.ADMINISTRATOR).join();
                LOGGER.debug("administrator check for {}: {}", member.getEffectiveName(), hasPerm);
                if (hasPerm == null || !hasPerm) {
                    ctx.reply("You don't have permission to use this command. (Requires Administrator)");
                    return;
                }
            } catch (Exception e) {
                LOGGER.error("failed to check administrator permission", e);
                ctx.reply("Failed to check permissions.");
                return;
            }
        }
        LOGGER.info("{}  {}{}{}", ctx.getAuthor().getAsTag(), config.getCommandPrefix(), command.getName(),
                args.isEmpty() ? "" : " " + String.join(" ", args));

        try {
            command.execute(ctx).exceptionally(ex -> {
                LOGGER.error("Failed to run command '{}' for user '{}':", command.getName(),
                        ctx.getAuthor().getAsTag(), ex);

                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                ctx.reply("This command failed: " + cause.getMessage()).exceptionally(e -> {
                    LOGGER.error("Failed to send command error message:", e);
                    return null;
                });
                return null;
            });
        } catch (Exception ex) {
            LOGGER.error("Failed to run command '{}' for user '{}':", command.getName(),
                    ctx.getAuthor().getAsTag(), ex);
            ctx.reply("This command failed: " + ex.getMessage());
        }
    }
}
