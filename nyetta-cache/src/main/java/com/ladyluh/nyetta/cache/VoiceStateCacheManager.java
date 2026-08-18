package com.ladyluh.nyetta.cache;

import flux.api.event.guild.GuildCreateEvent;
import flux.api.event.voice.VoiceStateUpdateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

public class VoiceStateCacheManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(VoiceStateCacheManager.class);

    public final ConcurrentHashMap<String, ConcurrentHashMap<String, ConcurrentSkipListSet<String>>> guildVoiceChannelMembers;

    public VoiceStateCacheManager() {
        this.guildVoiceChannelMembers = new ConcurrentHashMap<>();
        LOGGER.info("VoiceStateCacheManager: voice state cache initialized.");
    }

    public void onGuildCreate(GuildCreateEvent event) {
        String guildId = event.getGuild().getId();
        LOGGER.info("VoiceStateCacheManager: GuildCreateEvent for guild {}. Filling cache...", guildId);

        ConcurrentHashMap<String, ConcurrentSkipListSet<String>> guildChannelsMap =
                guildVoiceChannelMembers.computeIfAbsent(guildId, k -> new ConcurrentHashMap<>());

        guildChannelsMap.clear();

        event.getGuild().getVoiceStates().forEach(voiceState -> {
            String channelId = voiceState.getChannelId();
            String userId = voiceState.getUserId();
            if (channelId != null && userId != null) {
                guildChannelsMap.computeIfAbsent(channelId, k -> new ConcurrentSkipListSet<>()).add(userId);
                LOGGER.debug("Cache (init): user {} added to channel {}.", userId, channelId);
            }
        });
        LOGGER.info("VoiceStateCacheManager: cache filled for guild {}. Tracked voice channels: {}", guildId, guildChannelsMap.size());
    }

    public void onVoiceStateUpdate(VoiceStateUpdateEvent event) {
        String guildId = event.getGuildId();
        String userId = event.getUserId();
        String newChannelId = event.getChannelId();

        if (guildId == null || userId == null) return;

        ConcurrentHashMap<String, ConcurrentSkipListSet<String>> guildChannelsMap =
                guildVoiceChannelMembers.computeIfAbsent(guildId, k -> new ConcurrentHashMap<>());

        guildChannelsMap.forEach((channelIdInMap, usersInChannel) -> {
            if (usersInChannel.remove(userId)) {
                LOGGER.debug("Cache (update): user {} removed from old channel {}.", userId, channelIdInMap);
                if (usersInChannel.isEmpty()) {
                    guildChannelsMap.remove(channelIdInMap);
                    LOGGER.debug("Cache (update): old channel {} is empty and was removed from cache.", channelIdInMap);
                }
            }
        });

        if (newChannelId != null) {
            guildChannelsMap.computeIfAbsent(newChannelId, k -> new ConcurrentSkipListSet<>()).add(userId);
            LOGGER.debug("Cache (update): user {} added to new channel {}.", userId, newChannelId);
        }
    }

    public Set<String> getMembersInVoiceChannel(String guildId, String channelId) {
        return Optional.ofNullable(guildVoiceChannelMembers.get(guildId))
                .map(channels -> channels.get(channelId))
                .orElse(new ConcurrentSkipListSet<>());
    }

    public boolean isVoiceChannelEmpty(String guildId, String channelId) {
        Set<String> membersInChannel = getMembersInVoiceChannel(guildId, channelId);
        LOGGER.debug("isVoiceChannelEmpty: channel {} in guild {} has {} members in cache. Empty? {}",
                channelId, guildId, membersInChannel.size(), membersInChannel.isEmpty());
        return membersInChannel.isEmpty();
    }

    public String getUserVoiceChannelId(String guildId, String userId) {
        return Optional.ofNullable(guildVoiceChannelMembers.get(guildId))
                .flatMap(channels -> {
                    for (Map.Entry<String, ConcurrentSkipListSet<String>> entry : channels.entrySet()) {
                        if (entry.getValue().contains(userId)) {
                            return Optional.of(entry.getKey());
                        }
                    }
                    return Optional.empty();
                })
                .orElse(null);
    }
}
