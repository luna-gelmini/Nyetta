package com.ladyluh.nyetta.listeners;

import flux.api.FluxClient;
import flux.api.entities.User;
import flux.api.event.Event;
import flux.api.event.EventListener;
import flux.builder.EmbedBuilder;
import flux.builder.MessageBuilder;
import com.ladyluh.nyetta.commands.CommandManager;
import com.ladyluh.nyetta.database.DatabaseManager;
import com.ladyluh.nyetta.database.UserXP;
import flux.model.gateway.MessageCreateEvent;
import com.ladyluh.nyetta.services.XPRoleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

public class MessageEventListener implements EventListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageEventListener.class);
    private static final long XP_COOLDOWN_MILLIS = 60 * 1000;
    private static final int XP_MIN_PER_MESSAGE = 15;
    private static final int XP_MAX_PER_MESSAGE = 30;
    private final FluxClient client;
    private final DatabaseManager dbManager;
    private final String commandPrefix;
    private final CommandManager commandManager;
    private final XPRoleService xpRoleService;
    private final com.ladyluh.nyetta.cache.SnipeCache snipeCache;

    public MessageEventListener(FluxClient client, DatabaseManager dbManager, CommandManager commandManager,
            XPRoleService xpRoleService) {
        this(client, dbManager, commandManager, xpRoleService, "!", new com.ladyluh.nyetta.cache.SnipeCache());
    }

    public MessageEventListener(FluxClient client, DatabaseManager dbManager, CommandManager commandManager,
            XPRoleService xpRoleService, String commandPrefix) {
        this(client, dbManager, commandManager, xpRoleService, commandPrefix, new com.ladyluh.nyetta.cache.SnipeCache());
    }

    public MessageEventListener(FluxClient client, DatabaseManager dbManager, CommandManager commandManager,
            XPRoleService xpRoleService, String commandPrefix, com.ladyluh.nyetta.cache.SnipeCache snipeCache) {

        this.client = client;
        this.dbManager = dbManager;
        this.commandPrefix = commandPrefix == null || commandPrefix.isBlank() ? "!" : commandPrefix;
        this.commandManager = commandManager;
        this.xpRoleService = xpRoleService;
        this.snipeCache = snipeCache;

    }

    @Override
    public void onEvent(Event event) {
        if (event instanceof MessageCreateEvent mcEvent) {
            handleMessageCreate(mcEvent);
        }
    }

    private void handleMessageCreate(MessageCreateEvent event) {
        User author = event.getAuthor();
        String content = event.getContentRaw();
        String guildId = event.getMessage().getGuildId();
        String channelId = event.getChannelId();

        if (author == null || author.isBot()) {
            return;
        }

        snipeCache.remember(channelId, event.getMessage().getId(), author.getId(), author.getAsTag(), content);

        String guildIdInfo = guildId != null ? " (Guild: " + guildId + ")" : " (DM)";
        LOGGER.debug("Message from {}{}: {}", author.getAsTag(), guildIdInfo, content);

        if (guildId != null) {
            dbManager.getUserXP(guildId, author.getId())
                    .thenAccept(currentXP -> {
                        long now = System.currentTimeMillis();

                        if (now - currentXP.getLastMessageTimestamp() > XP_COOLDOWN_MILLIS) {
                            int xpGained = ThreadLocalRandom.current().nextInt(XP_MIN_PER_MESSAGE,
                                    XP_MAX_PER_MESSAGE + 1);
                            int oldLevel = currentXP.getLevel();
                            currentXP.setXp(currentXP.getXp() + xpGained);
                            currentXP.setLastMessageTimestamp(now);

                            int calculatedLevel = currentXP.getLevel();
                            while (currentXP.getXp() >= UserXP.calculateXpForLevel(calculatedLevel + 1)) {
                                calculatedLevel++;
                            }

                            boolean leveledUp = false;
                            if (calculatedLevel > currentXP.getLevel()) {
                                currentXP.setLevel(calculatedLevel);
                                leveledUp = true;
                            }

                            final boolean finalLeveledUp = leveledUp;

                            dbManager
                                    .updateUserXP(currentXP.getGuildId(), currentXP.getUserId(), currentXP.getXp(),
                                            currentXP.getLevel(), currentXP.getLastMessageTimestamp())
                                    .thenRun(() -> LOGGER.debug("{} gained {} XP. Total: {}, level: {}",
                                            author.getAsTag(), xpGained, currentXP.getXp(), currentXP.getLevel()))
                                    .thenCompose(v -> {
                                        if (finalLeveledUp) {
                                            sendLevelUpMessage(channelId, author, currentXP.getLevel());
                                            return xpRoleService.assignXPRoles(guildId, author.getId(), oldLevel,
                                                    currentXP.getLevel());
                                        }
                                        return CompletableFuture.completedFuture(null);
                                    })
                                    .exceptionally(ex -> {
                                        LOGGER.error("XP logic error for user {}:", author.getAsTag(), ex);
                                        return null;
                                    });
                        }
                    })
                    .exceptionally(ex -> {
                        LOGGER.error("Failed to load/process initial XP for user {}:", author.getAsTag(), ex);
                        return null;
                    });
        }

        if (content.startsWith(commandPrefix)) {
            String commandLine = content.substring(commandPrefix.length()).trim();
            if (commandLine.isEmpty())
                return;

            String[] parts = commandLine.split("\\s+", 2);
            String commandName = parts[0].toLowerCase();
            String argsString = parts.length > 1 ? parts[1].trim() : "";
            List<String> argsList = argsString.isEmpty() ? List.of() : Arrays.asList(argsString.split("\\s+"));

            commandManager.handleCommand(commandName, argsList, event);
        }
    }

    private void sendLevelUpMessage(String channelId, User user, int newLevel) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🎉 LEVEL UP!")
                .setDescription("Parabéns, <@" + user.getId() + ">! Você alcançou o **Nível " + newLevel + "**!")
                .setColor(new Color(0xFFD700))
                .setThumbnail(user.getEffectiveAvatarUrl())
                .addField("XP Total", String.valueOf(UserXP.calculateXpForLevel(newLevel)), true)
                .addField("Próximo Nível", UserXP.calculateXpForLevel(newLevel + 1) + " XP", true)
                .setFooter("Continue conversando para ganhar mais XP!", null)
                .setTimestamp(OffsetDateTime.now());

        client.sendMessage(channelId, new MessageBuilder().addEmbed(embed).build())
                .exceptionally(ex -> {
                    LOGGER.error("Failed to send level-up message to {}:", user.getAsTag(), ex);
                    return null;
                });
    }
}
