package com.ladyluh.nyetta.listeners;

import flux.api.FluxClient;
import flux.api.entities.channel.Channel;
import flux.api.entities.channel.ChannelType;
import flux.api.event.Event;
import flux.api.event.EventListener;
import flux.api.event.channel.ChannelUpdateEvent;
import flux.api.payload.channel.ChannelModifyPayload;
import com.ladyluh.nyetta.database.DatabaseManager;
import com.ladyluh.nyetta.database.GuildConfig;
import com.ladyluh.nyetta.database.TemporaryChannelRecord;
import com.ladyluh.nyetta.database.UserChannelPreference;
import com.ladyluh.nyetta.services.TemporaryChannelTreeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChannelNameEnforcementListener implements EventListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChannelNameEnforcementListener.class);

    private final FluxClient client;
    private final DatabaseManager dbManager;
    private final TemporaryChannelTreeService treeService;

    public ChannelNameEnforcementListener(FluxClient client, DatabaseManager dbManager,
            TemporaryChannelTreeService treeService) {
        this.client = client;
        this.dbManager = dbManager;
        this.treeService = treeService;
    }

    @Override
    public void onEvent(Event event) {
        if (event instanceof ChannelUpdateEvent updateEvent) {
            handleChannelUpdate(updateEvent);
        }
    }

    private void handleChannelUpdate(ChannelUpdateEvent event) {
        Channel channel = event.getChannel();
        if (channel == null)
            return;

        ChannelType type = channel.getType();
        if (type != ChannelType.GUILD_VOICE)
            return;

        String channelId = channel.getId();
        String guildId = event.getGuildId();
        String currentName = channel.getName();

        dbManager.getTemporaryChannel(channelId)
                .thenAccept(tempChannelOpt -> {
                    if (tempChannelOpt.isEmpty())
                        return;

                    TemporaryChannelRecord record = tempChannelOpt.get();
                    String ownerId = record.ownerUserId;

                    dbManager.getUserChannelPreference(guildId, ownerId)
                            .thenCompose(prefsOpt -> dbManager.getGuildConfig(guildId)
                                    .thenCompose(gcfgOpt -> client.getGuildMember(guildId, ownerId)
                                            .thenAccept(member -> {
                                                if (member == null)
                                                    return;

                                                UserChannelPreference prefs = prefsOpt.orElse(
                                                        new UserChannelPreference(guildId, ownerId));
                                                GuildConfig config = gcfgOpt.orElse(new GuildConfig(guildId));

                                                String nameTemplate = prefs.preferredName != null
                                                        && !prefs.preferredName.isEmpty()
                                                                ? prefs.preferredName
                                                                : config.tempChannelNamePrefix;
                                                String baseName = (nameTemplate != null ? nameTemplate
                                                        : "%username%'s room")
                                                        .replace("%username%", member.getEffectiveName());

                                                treeService.isLastChannel(guildId, channelId)
                                                        .thenAccept(isLast -> {
                                                            String expectedName = treeService.applyTreePrefix(baseName,
                                                                    isLast);
                                                            if (expectedName.length() > 100) {
                                                                expectedName = expectedName.substring(0, 100);
                                                            }

                                                            if (!expectedName.equals(currentName)) {
                                                                LOGGER.info(
                                                                        "Channel {} was renamed incorrectly. Reverting from '{}' to '{}'",
                                                                        channelId, currentName, expectedName);
                                                                ChannelModifyPayload payload = new ChannelModifyPayload();
                                                                payload.setName(expectedName);
                                                                client.modifyChannel(channelId, payload)
                                                                        .exceptionally(ex -> {
                                                                            LOGGER.error(
                                                                                    "Failed to revert name of channel {}:",
                                                                                    channelId, ex);
                                                                            return null;
                                                                        });
                                                            }
                                                        });
                                            })))
                            .exceptionally(ex -> {
                                LOGGER.error("Failed to check name of channel {}:", channelId, ex);
                                return null;
                            });
                })
                .exceptionally(ex -> {
                    LOGGER.error("Failed to look up temporary channel {}:", channelId, ex);
                    return null;
                });
    }
}
