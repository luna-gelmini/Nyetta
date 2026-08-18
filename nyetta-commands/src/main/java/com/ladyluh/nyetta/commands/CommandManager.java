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
        addCommand(new SalaCommand(dbManager, treeService));
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
    }

    private void addCommand(Command command) {
        uniqueCommands.add(command);
        commands.put(command.getName().toLowerCase(), command);
        for (String alias : command.getAliases()) {
            commands.put(alias.toLowerCase(), command);
        }
        LOGGER.info("Command '{}' and aliases {} registered.", command.getName(), command.getAliases());
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
            client.sendMessage(event.getChannelId(), "Este comando só pode ser usado em um servidor.");
            return;
        }

        CommandContext ctx = new CommandContext(client, config, dbManager, voiceStateCacheManager, event, commandName,
                args);

        if (command.requiresAdministrator()) {
            if (ctx.getGuildId() == null) {
                client.sendMessage(event.getChannelId(), "Este comando só pode ser usado em um servidor.");
                return;
            }
            try {
                flux.api.entities.Member member = client.getGuildMember(ctx.getGuildId(), ctx.getAuthor().getId()).join();
                if (member == null) {
                    client.sendMessage(event.getChannelId(), "Erro ao identificar suas permissões.");
                    return;
                }
                LOGGER.info("[AUTH] (Message) Checking administrator permission for {} ({})", member.getEffectiveName(), member.getId());
                Boolean hasPerm = member.hasPermission(flux.api.payload.permission.Permission.ADMINISTRATOR).join();
                LOGGER.info("[AUTH] (Message) Result for {}: {}", member.getEffectiveName(), hasPerm);
                if (hasPerm == null || !hasPerm) {
                    ctx.reply("Você não tem permissão para usar este comando. (Requer Administrador)");
                    return;
                }
            } catch (Exception e) {
                LOGGER.error("[AUTH] Failed to check permissions:", e);
                ctx.reply("Erro ao verificar permissões.");
                return;
            }
        }
        LOGGER.info("Running command '{}' for user '{}' with args: {}", command.getName(),
                ctx.getAuthor().getAsTag(), args);

        command.execute(ctx).exceptionally(ex -> {
            LOGGER.error("Failed to run command '{}' for user '{}':", command.getName(),
                    ctx.getAuthor().getAsTag(), ex);

            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            ctx.reply("Ocorreu um erro ao executar este comando: " + cause.getMessage()).exceptionally(e -> {
                LOGGER.error("Failed to send command error message:", e);
                return null;
            });
            return null;
        });
    }
}
