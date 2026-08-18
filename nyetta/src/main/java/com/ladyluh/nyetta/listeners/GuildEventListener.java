package com.ladyluh.nyetta.listeners;

import flux.api.FluxClient;
import flux.api.entities.Guild;
import flux.api.entities.Member;
import flux.api.event.Event;
import flux.api.event.EventListener;
import flux.api.event.guild.member.GuildMemberAddEvent;
import flux.builder.EmbedBuilder;
import flux.builder.MessageBuilder;
import com.ladyluh.nyetta.database.DatabaseManager;
import com.ladyluh.nyetta.database.GuildConfig;
import flux.model.gateway.ReadyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.time.OffsetDateTime;

public class GuildEventListener implements EventListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(GuildEventListener.class);
    private final FluxClient client;
    private final DatabaseManager dbManager;

    public GuildEventListener(FluxClient client, DatabaseManager dbManager) {
        this.client = client;
        this.dbManager = dbManager;

    }

    @Override
    public void onEvent(Event event) {
        if (event instanceof ReadyEvent readyEvent) {
            handleReady(readyEvent);
        } else if (event instanceof GuildMemberAddEvent gmaEvent) {
            handleGuildMemberAdd(gmaEvent);
        }
    }

    private void handleReady(ReadyEvent event) {
        LOGGER.info("Bot is READY! Logged in as: {} (ID: {})",
                event.getSelfUser().getAsTag(), event.getSelfUser().getId());
        LOGGER.info("Session ID: {}, Resume URL: {}", event.getSessionId(), event.getResumeGatewayUrl());
    }

    private void handleGuildMemberAdd(GuildMemberAddEvent event) {
        Member newMember = event.getMember();
        String guildId = event.getGuildId();

        if (guildId == null) {
            LOGGER.warn("GuildMemberAddEvent missing guildId for member {}. Ignoring.", newMember.getId());
            return;
        }

        dbManager.getGuildConfig(guildId)
                .thenAccept(configOpt -> {

                    GuildConfig guildConfig = configOpt.orElse(new GuildConfig(guildId));

                    String autoAssignRoleId = guildConfig.autoAssignRoleId;
                    String welcomeChannelId = guildConfig.welcomeChannelId;

                    LOGGER.info("New member {} (ID: {}) joined guild {}",
                            (newMember.getUser() != null ? newMember.getUser().getAsTag() : newMember.getId()),
                            newMember.getId(),
                            event.getGuild().thenApply(Guild::getName).exceptionally(ex -> "ID: " + guildId).join());

                    if (autoAssignRoleId != null && !autoAssignRoleId.isEmpty()) {
                        LOGGER.info("Trying to add role {} to {}", autoAssignRoleId, newMember.getEffectiveName());
                        client.addRoleToMember(guildId, newMember.getId(), autoAssignRoleId)
                                .thenRun(() -> LOGGER.info("Role {} assigned to {}!", autoAssignRoleId,
                                        newMember.getEffectiveName()))
                                .exceptionally(ex -> {
                                    LOGGER.error("Failed to assign role {} to {}:", autoAssignRoleId,
                                            newMember.getEffectiveName(), ex);
                                    return null;
                                });
                    } else {
                        LOGGER.debug("Auto-assign role ID not configured for guild {}.", guildId);
                    }

                })
                .exceptionally(ex -> {
                    LOGGER.error("Failed to load guild config for GuildMemberAddEvent:");
                    return null;
                });
    }
}
