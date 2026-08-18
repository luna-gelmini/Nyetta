package com.ladyluh.nyetta.listeners;

import flux.api.FluxClient;
import flux.api.entities.Guild;
import flux.api.entities.TargetType;
import flux.api.entities.User;
import flux.api.entities.channel.Channel;
import flux.api.entities.channel.ChannelType;
import flux.api.event.Event;
import flux.api.event.EventListener;
import flux.api.event.guild.GuildCreateEvent;
import flux.api.event.voice.VoiceStateUpdateEvent;
import flux.api.payload.channel.ChannelModifyPayload;
import flux.api.payload.channel.CreateGuildChannelPayload;
import flux.api.payload.permission.Permission;
import com.ladyluh.nyetta.cache.VoiceStateCacheManager;
import com.ladyluh.nyetta.config.ConfigManager;
import com.ladyluh.nyetta.database.DatabaseManager;
import com.ladyluh.nyetta.database.GuildConfig;
import com.ladyluh.nyetta.database.TemporaryChannelRecord;
import com.ladyluh.nyetta.database.UserChannelPreference;
import com.ladyluh.nyetta.services.TemporaryChannelTreeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class TemporaryChannelListener implements EventListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(TemporaryChannelListener.class);

    private final ConfigManager config;
    private final FluxClient client;
    private final DatabaseManager dbManager;
    private final VoiceStateCacheManager voiceStateCacheManager;
    private final TemporaryChannelTreeService treeService;

    private final ConcurrentHashMap<String, CompletableFuture<Channel>> userCreationAttempts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock> channelLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> pendingDeletions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> pendingMoves = new ConcurrentHashMap<>();

    public TemporaryChannelListener(ConfigManager config, FluxClient client, DatabaseManager dbManager,
            VoiceStateCacheManager voiceStateCacheManager, TemporaryChannelTreeService treeService) {
        this.config = config;
        this.client = client;
        this.dbManager = dbManager;
        this.voiceStateCacheManager = voiceStateCacheManager;
        this.treeService = treeService;
    }

    @Override
    public void onEvent(Event event) {
        if (event instanceof GuildCreateEvent gcEvent) {
            voiceStateCacheManager.onGuildCreate(gcEvent);
            Guild guild = gcEvent.getGuild();
            if (guild != null) {
                String guildId = guild.getId();
                purgeStaleTemporaryChannels(guildId);
                logBotVoicePermissions(guildId);
            }
        } else if (event instanceof VoiceStateUpdateEvent vsEvent) {
            handleVoiceStateUpdate(vsEvent);
        }
    }

    private GuildConfig applyEnvDefaults(GuildConfig guildConfig) {
        if (isBlank(guildConfig.tempHubChannelId)) {
            guildConfig.tempHubChannelId = config.getHubChannelId();
        }
        if (isBlank(guildConfig.tempChannelCategoryId)) {
            guildConfig.tempChannelCategoryId = config.getTempChannelCategoryId();
        }
        if (isBlank(guildConfig.tempChannelNamePrefix)) {
            String prefix = config.getTempChannelNamePrefix();
            if (!isBlank(prefix)) {
                guildConfig.tempChannelNamePrefix = prefix;
            }
        }
        if (guildConfig.defaultTempChannelUserLimit == null) {
            guildConfig.defaultTempChannelUserLimit = config.getTempChannelUserLimit();
        }
        if (guildConfig.defaultTempChannelLock == null) {
            guildConfig.defaultTempChannelLock = config.getTempChannelDefaultLock();
        }
        return guildConfig;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private ReentrantLock getChannelLock(String channelId) {
        return channelLocks.computeIfAbsent(channelId, k -> new ReentrantLock());
    }

    private void releaseChannelLock(String channelId) {
        ReentrantLock lock = channelLocks.get(channelId);
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    private void handleVoiceStateUpdate(VoiceStateUpdateEvent event) {
        String guildId = event.getGuildId();
        if (guildId == null)
            return;

        String userId = event.getUserId();
        String newChannelId = event.getChannelId();
        String oldChannelId = voiceStateCacheManager.getUserVoiceChannelId(guildId, userId);

        if (Objects.equals(oldChannelId, newChannelId)) {
            return;
        }

        LOGGER.debug("Voice state change for user {}: {} -> {} (connection {})",
                userId, oldChannelId, newChannelId, event.getConnectionId());

        if (oldChannelId != null) {
            boolean wasChannelEmpty = voiceStateCacheManager.getMembersInVoiceChannel(guildId, oldChannelId)
                    .size() <= 1;
            voiceStateCacheManager.onVoiceStateUpdate(event);
            if (!(newChannelId == null && pendingMoves.containsKey(userId))) {
                checkChannelOnUserLeave(guildId, oldChannelId, userId, wasChannelEmpty);
            }
        } else {
            voiceStateCacheManager.onVoiceStateUpdate(event);
        }

        if (newChannelId != null) {
            pendingMoves.remove(userId);
            dbManager.getGuildConfig(guildId)
                    .thenAccept(configOpt -> {
                        GuildConfig guildConfig = applyEnvDefaults(configOpt.orElse(new GuildConfig(guildId)));
                        String hubId = guildConfig.tempHubChannelId;
                        if (hubId != null && !hubId.isBlank() && hubId.equals(newChannelId)) {
                            handleHubJoin(event, guildId, userId, guildConfig);
                        }
                    })
                    .exceptionally(ex -> {
                        LOGGER.error("Failed to load guild {} config for hub join:", guildId, ex);
                        return null;
                    });
        }
    }

    private void handleHubJoin(VoiceStateUpdateEvent event, String guildId, String userId, GuildConfig guildConfig) {
        CompletableFuture<Channel> marker = new CompletableFuture<>();
        if (userCreationAttempts.putIfAbsent(userId, marker) != null) {
            LOGGER.debug("Ignoring duplicate hub join for {} (already creating or moving).", userId);
            return;
        }
        pendingMoves.put(userId, "");

        dbManager.getTemporaryChannelByOwner(guildId, userId)
                .thenCompose(existingChannelOpt -> {
                    if (existingChannelOpt.isPresent()) {
                        String existingChannelId = existingChannelOpt.get().channelId;
                        return client.getChannelById(existingChannelId)
                                .thenCompose(channel -> moveUserToTemporaryChannel(event, guildId, userId,
                                        existingChannelId))
                                .handle((ok, ex) -> {
                                    if (ex != null && isNotFound(ex)) {
                                        LOGGER.warn("Stale temp channel {} for user {}. Recreating.", existingChannelId,
                                                userId);
                                        return dbManager.removeTemporaryChannel(existingChannelId)
                                                .thenCompose(v -> createTemporaryChannelForUser(event, guildId, userId,
                                                        guildConfig, new java.util.concurrent.atomic.AtomicReference<>()));
                                    }
                                    if (ex != null) {
                                        logVoiceMoveFailure(userId, existingChannelId, ex);
                                        return CompletableFuture.<Channel>failedFuture(ex);
                                    }
                                    return CompletableFuture.completedFuture((Channel) null);
                                })
                                .thenCompose(f -> f);
                    }
                    LOGGER.info("User {} joined the hub. Creating temporary channel.", userId);
                    java.util.concurrent.atomic.AtomicReference<String> createdChannelIdRef = new java.util.concurrent.atomic.AtomicReference<>();
                    return createTemporaryChannelForUser(event, guildId, userId, guildConfig, createdChannelIdRef)
                            .whenComplete((channel, ex) -> {
                                if (ex != null) {
                                    pendingMoves.remove(userId);
                                    if (isMissingPermissions(ex)) {
                                        LOGGER.error(
                                                "Temporary channel created but bot cannot move user {} (403). "
                                                        + "If this user is the guild owner or has a higher role than the bot, Fluxer will refuse the move.",
                                                userId);
                                        String keptChannelId = createdChannelIdRef.get();
                                        if (keptChannelId != null) {
                                            LOGGER.warn(
                                                    "Keeping channel {} — join it manually until permissions/hierarchy allow the move.",
                                                    keptChannelId);
                                        }
                                    } else if (isUserNotInVoice(ex)) {
                                        LOGGER.warn(
                                                "User {} left voice before the move finished. Keeping the created room.",
                                                userId);
                                    } else {
                                        LOGGER.error("Failed to create temporary channel for {}:", userId, ex);
                                        String createdChannelId = createdChannelIdRef.get();
                                        if (createdChannelId != null) {
                                            LOGGER.warn("Cleaning up orphaned channel {} after failure.",
                                                    createdChannelId);
                                            deleteTemporaryChannel(createdChannelId);
                                        }
                                    }
                                } else if (channel != null) {
                                    LOGGER.info("Channel {} created and user moved for {}.", channel.getId(), userId);
                                }
                            });
                })
                .whenComplete((channel, ex) -> {
                    if (ex != null) {
                        marker.completeExceptionally(ex);
                    } else {
                        marker.complete(channel);
                    }
                    userCreationAttempts.remove(userId, marker);
                });
    }

    private CompletableFuture<Void> moveUserToTemporaryChannel(VoiceStateUpdateEvent event, String guildId,
            String userId, String channelId) {
        LOGGER.info("User {} already has channel {}. Moving.", userId, channelId);
        pendingMoves.put(userId, channelId);
        return moveUserToChannelWithFallback(event, guildId, userId, channelId)
                .whenComplete((ok, ex) -> {
                    if (ex != null) {
                        pendingMoves.remove(userId, channelId);
                        logVoiceMoveFailure(userId, channelId, ex);
                    }
                });
    }

    private CompletableFuture<Void> moveUserToChannelWithFallback(VoiceStateUpdateEvent event, String guildId,
            String userId, String channelId) {
        String connectionId = event.getConnectionId();
        return client.modifyGuildMemberVoiceChannel(guildId, userId, channelId, connectionId)
                .exceptionallyCompose(ex -> {
                    if (isMissingPermissions(ex) || isUserNotInVoice(ex)) {
                        return CompletableFuture.failedFuture(ex);
                    }
                    LOGGER.warn("Move with connection_id {} failed for {}, retrying without it.",
                            connectionId, userId);
                    return client.modifyGuildMemberVoiceChannel(guildId, userId, channelId, null);
                });
    }

    private void purgeStaleTemporaryChannels(String guildId) {
        dbManager.getTemporaryChannelsByGuild(guildId)
                .thenAccept(channels -> {
                    if (channels.isEmpty()) {
                        return;
                    }
                    LOGGER.info("Checking {} temporary channel record(s) for guild {}.", channels.size(), guildId);
                    for (TemporaryChannelRecord record : channels) {
                        client.getChannelById(record.channelId)
                                .exceptionally(ex -> {
                                    if (isNotFound(ex)) {
                                        LOGGER.warn("Removing stale temp channel {} from DB (channel no longer exists).",
                                                record.channelId);
                                        dbManager.removeTemporaryChannel(record.channelId)
                                                .exceptionally(dbEx -> {
                                                    LOGGER.error("Failed to remove stale channel {} from DB:",
                                                            record.channelId, dbEx);
                                                    return null;
                                                });
                                    } else {
                                        LOGGER.error("Failed to verify temp channel {}:", record.channelId, ex);
                                    }
                                    return null;
                                });
                    }
                })
                .exceptionally(ex -> {
                    LOGGER.error("Failed to purge stale temporary channels for guild {}:", guildId, ex);
                    return null;
                });
    }

    private static boolean isChannelAlreadyDeleted(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && (message.contains("404") || message.contains("UNKNOWN_CHANNEL")
                    || message.contains("No content to map"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean isNotFound(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && (message.contains("404") || message.contains("UNKNOWN_CHANNEL"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean isUserNotInVoice(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && (message.contains("USER_NOT_IN_VOICE") || message.contains("isn't in voice"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean isMissingPermissions(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            String message = current.getMessage();
            if (message != null
                    && (message.contains("403") || message.contains("MISSING_PERMISSIONS"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void logBotVoicePermissions(String guildId) {
        User self = client.getSelfUser();
        if (self == null) {
            return;
        }
        client.getGuildMember(guildId, self.getId())
                .thenCompose(member -> member.hasPermissions(
                        EnumSet.of(Permission.MOVE_MEMBERS, Permission.CONNECT, Permission.MANAGE_CHANNELS)))
                .thenAccept(hasAll -> {
                    if (hasAll) {
                        LOGGER.info("Bot has MOVE_MEMBERS, CONNECT, and MANAGE_CHANNELS in guild {}.", guildId);
                    } else {
                        LOGGER.warn(
                                "Bot is missing voice move permissions in guild {}. "
                                        + "Temp rooms will be created but users will not be auto-moved. "
                                        + "Enable MOVE_MEMBERS + CONNECT on the bot role and place it above target members.",
                                guildId);
                    }
                })
                .exceptionally(ex -> {
                    LOGGER.warn("Could not verify bot permissions in guild {}:", guildId, ex);
                    return null;
                });
    }

    private void logVoiceMoveFailure(String userId, String channelId, Throwable ex) {
        if (isUserNotInVoice(ex)) {
            LOGGER.warn("User {} is no longer in voice; skipping move to {}.", userId, channelId);
            return;
        }
        if (isMissingPermissions(ex)) {
            LOGGER.error(
                    "Cannot move user {} to channel {}: Fluxer returned 403. "
                            + "Guild owners and members with a higher role than the bot cannot be moved, "
                            + "even if MOVE_MEMBERS is enabled.",
                    userId, channelId);
            return;
        }
        LOGGER.error("Failed to move user {} to channel {}:", userId, channelId, ex);
    }

    private CompletableFuture<Channel> createTemporaryChannelForUser(VoiceStateUpdateEvent event, String guildId,
            String userId, GuildConfig guildConfig, java.util.concurrent.atomic.AtomicReference<String> createdChannelIdRef) {
        return event.retrieveMember().thenComposeAsync(member -> {
            if (member == null || member.getUser() == null) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("Could not retrieve member " + userId));
            }

            return dbManager.getUserChannelPreference(guildId, userId)
                    .thenCompose(prefsOpt -> {
                        UserChannelPreference prefs = prefsOpt.orElse(new UserChannelPreference(guildId, userId));

                        Integer finalUserLimit = prefs.preferredUserLimit != null
                                ? prefs.preferredUserLimit
                                : guildConfig.defaultTempChannelUserLimit;
                        String finalNameTemplate = prefs.preferredName != null && !prefs.preferredName.isEmpty()
                                ? prefs.preferredName
                                : guildConfig.tempChannelNamePrefix;
                        Integer finalDefaultLocked = prefs.defaultLocked != null
                                ? prefs.defaultLocked
                                : guildConfig.defaultTempChannelLock;

                        String nameTemplate = finalNameTemplate != null ? finalNameTemplate : "";
                        if (nameTemplate.isBlank()) {
                            nameTemplate = "%username%'s room";
                        } else if (!nameTemplate.contains("%username%")) {
                            nameTemplate = nameTemplate.trim() + " %username%";
                        }
                        String baseChannelName = nameTemplate.replace("%username%", member.getEffectiveName());
                        String channelName = treeService.applyTreePrefix(baseChannelName, true);
                        if (channelName.length() > 100) {
                            channelName = channelName.substring(0, 100);
                        }

                        LOGGER.info("Creating channel '{}' for {}", channelName, member.getEffectiveName());

                        CreateGuildChannelPayload payload = new CreateGuildChannelPayload(channelName,
                                ChannelType.GUILD_VOICE);
                        if (!isBlank(guildConfig.tempChannelCategoryId)) {
                            payload.setParentId(guildConfig.tempChannelCategoryId);
                        }
                        if (finalUserLimit != null && finalUserLimit != 0) {
                            payload.setUserLimit(finalUserLimit);
                        }

                        return client.createGuildChannel(guildId, payload)
                                .thenCompose(createdChannel -> {
                                    String channelId = createdChannel.getId();
                                    createdChannelIdRef.set(channelId);
                                    return dbManager.addTemporaryChannel(channelId, guildId, userId)
                                            .thenCompose(v -> applyChannelPermissions(createdChannel, userId,
                                                    finalDefaultLocked))
                                            .thenCompose(v -> treeService.updateTreePrefixes(guildId))
                                            .thenCompose(v -> {
                                                String currentChannel = voiceStateCacheManager
                                                        .getUserVoiceChannelId(guildId, userId);
                                                if (currentChannel != null
                                                        && !currentChannel.equals(guildConfig.tempHubChannelId)
                                                        && !currentChannel.equals(channelId)) {
                                                    LOGGER.warn(
                                                            "User {} left the hub before being moved. Channel {} will be cleaned up.",
                                                            userId, channelId);
                                                    deleteTemporaryChannel(channelId);
                                                    return CompletableFuture.completedFuture(createdChannel);
                                                }
                                                pendingMoves.put(userId, channelId);
                                                return moveUserToChannelWithFallback(event, guildId, userId, channelId)
                                                        .whenComplete((ok, moveEx) -> {
                                                            if (moveEx != null) {
                                                                pendingMoves.remove(userId, channelId);
                                                                logVoiceMoveFailure(userId, channelId, moveEx);
                                                            }
                                                        })
                                                        .thenCompose(ignored -> CompletableFuture.completedFuture(createdChannel));
                                            });
                                });
                    });
        });
    }

    private CompletableFuture<Void> applyChannelPermissions(Channel channel, String ownerId, Integer defaultLock) {
        boolean isLocked = defaultLock != null && defaultLock == 1;

        EnumSet<Permission> allowEveryone = isLocked
                ? EnumSet.noneOf(Permission.class)
                : EnumSet.of(Permission.CONNECT, Permission.SPEAK, Permission.VIEW_CHANNEL);
        EnumSet<Permission> denyEveryone = isLocked
                ? EnumSet.of(Permission.CONNECT)
                : EnumSet.noneOf(Permission.class);

        CompletableFuture<Void> everyonePerms = client.editChannelPermissions(
                channel.getId(), channel.getGuildId(), TargetType.ROLE, allowEveryone, denyEveryone)
                .exceptionally(ex -> {
                    LOGGER.error("Failed to apply @everyone permissions on channel {}:", channel.getId(), ex);
                    return null;
                });

        CompletableFuture<Void> ownerPerms = client.editChannelPermissions(
                channel.getId(), ownerId, TargetType.MEMBER,
                EnumSet.of(Permission.CONNECT, Permission.SPEAK, Permission.VIEW_CHANNEL),
                EnumSet.noneOf(Permission.class))
                .exceptionally(ex -> {
                    LOGGER.error("Failed to apply owner {} permissions on channel {}:", ownerId, channel.getId(), ex);
                    return null;
                });

        return CompletableFuture.allOf(everyonePerms, ownerPerms);
    }

    private void checkChannelOnUserLeave(String guildId, String channelId, String userIdWhoLeft,
            boolean wasLastMember) {
        if (pendingDeletions.containsKey(channelId)) {
            LOGGER.debug("Channel {} is already being deleted. Ignoring.", channelId);
            return;
        }

        dbManager.getTemporaryChannel(channelId)
                .thenAccept(tempChannelOpt -> {
                    if (tempChannelOpt.isEmpty()) {
                        return;
                    }

                    TemporaryChannelRecord record = tempChannelOpt.get();
                    ReentrantLock lock = getChannelLock(channelId);

                    if (!lock.tryLock()) {
                        LOGGER.debug("Channel {} is in use by another operation. Ignoring.", channelId);
                        return;
                    }

                    try {
                        if (wasLastMember || voiceStateCacheManager.isVoiceChannelEmpty(guildId, channelId)) {
                            LOGGER.info("Channel {} is empty. Deleting.", channelId);
                            deleteTemporaryChannelInternal(channelId, guildId);
                            return;
                        }

                        if (record.ownerUserId.equals(userIdWhoLeft)) {
                            handleOwnerLeave(guildId, record);
                        }
                    } finally {
                        releaseChannelLock(channelId);
                    }
                })
                .exceptionally(ex -> {
                    LOGGER.error("Failed to check channel {} after leave:", channelId, ex);
                    return null;
                });
    }

    private void handleOwnerLeave(String guildId, TemporaryChannelRecord channelRecord) {
        dbManager.getUserChannelPreference(guildId, channelRecord.ownerUserId)
                .thenAccept(ownerPrefsOpt -> {
                    boolean autoSwitchEnabled = ownerPrefsOpt
                            .map(prefs -> prefs.autoOwnerSwitching == null || prefs.autoOwnerSwitching == 1)
                            .orElse(true);

                    if (autoSwitchEnabled) {
                        LOGGER.info("Auto-switch enabled. Transferring ownership of {}.", channelRecord.channelId);
                        transferChannelOwnership(guildId, channelRecord.channelId);
                    } else {
                        LOGGER.info("Auto-switch disabled. Deleting channel {}.", channelRecord.channelId);
                        deleteTemporaryChannelInternal(channelRecord.channelId, guildId);
                    }
                })
                .exceptionally(ex -> {
                    LOGGER.error("Failed to load owner preferences. Deleting channel {}:", channelRecord.channelId,
                            ex);
                    deleteTemporaryChannelInternal(channelRecord.channelId, guildId);
                    return null;
                });
    }

    private void transferChannelOwnership(String guildId, String channelId) {
        Set<String> membersInChannel = voiceStateCacheManager.getMembersInVoiceChannel(guildId, channelId);
        if (membersInChannel.isEmpty()) {
            LOGGER.warn("Channel {} became empty during transfer. Deleting.", channelId);
            deleteTemporaryChannelInternal(channelId, guildId);
            return;
        }

        String newOwnerId = membersInChannel.iterator().next();
        LOGGER.info("Transferring ownership of {} to {}", channelId, newOwnerId);

        dbManager.updateTemporaryChannelOwner(channelId, newOwnerId)
                .thenCompose(v -> client.getGuildMember(guildId, newOwnerId))
                .thenCompose(newOwnerMember -> {
                    if (newOwnerMember == null || newOwnerMember.getUser() == null) {
                        return CompletableFuture.failedFuture(
                                new IllegalStateException("Could not retrieve member " + newOwnerId));
                    }

                    return dbManager.getUserChannelPreference(guildId, newOwnerId)
                            .thenCompose(prefsOpt -> dbManager.getGuildConfig(guildId)
                                    .thenCompose(gcfgOpt -> {
                                        UserChannelPreference prefs = prefsOpt
                                                .orElse(new UserChannelPreference(guildId, newOwnerId));
                                        GuildConfig guildConfig = gcfgOpt.orElse(new GuildConfig(guildId));

                                        String nameTemplate = prefs.preferredName != null
                                                && !prefs.preferredName.isEmpty()
                                                        ? prefs.preferredName
                                                        : guildConfig.tempChannelNamePrefix;
                                        String baseName = (nameTemplate != null ? nameTemplate : "%username%'s room")
                                                .replace("%username%", newOwnerMember.getEffectiveName());

                                        return treeService.isLastChannel(guildId, channelId)
                                                .thenCompose(isLast -> {
                                                    String finalName = treeService.applyTreePrefix(baseName, isLast);
                                                    if (finalName.length() > 100) {
                                                        finalName = finalName.substring(0, 100);
                                                    }

                                                    ChannelModifyPayload payload = new ChannelModifyPayload();
                                                    payload.setName(finalName);

                                                    Integer limit = prefs.preferredUserLimit != null
                                                            ? prefs.preferredUserLimit
                                                            : guildConfig.defaultTempChannelUserLimit;
                                                    if (limit != null) {
                                                        payload.setUserLimit(limit == 0 ? null : limit);
                                                    }

                                                    return client.modifyChannel(channelId, payload)
                                                            .thenCompose(v2 -> client.getChannelById(channelId))
                                                            .thenCompose(channel -> applyChannelPermissions(
                                                                    channel, newOwnerId, prefs.defaultLocked));
                                                });
                                    }));
                })
                .thenRun(() -> LOGGER.info("Channel {} updated for new owner {}.", channelId, newOwnerId))
                .exceptionally(ex -> {
                    LOGGER.error("Failed to transfer ownership of {}. Deleting:", channelId, ex);
                    deleteTemporaryChannelInternal(channelId, guildId);
                    return null;
                });
    }

    public void deleteTemporaryChannel(String channelId) {
        if (channelId == null) {
            LOGGER.warn("Attempted to delete a channel with a null ID.");
            return;
        }

        dbManager.getTemporaryChannel(channelId)
                .thenAccept(tempChannelOpt -> {
                    String guildId = tempChannelOpt.map(tc -> tc.guildId).orElse(null);
                    deleteTemporaryChannelInternal(channelId, guildId);
                })
                .exceptionally(ex -> {
                    LOGGER.error("Failed to fetch channel {} for deletion:", channelId, ex);
                    return null;
                });
    }

    private void deleteTemporaryChannelInternal(String channelId, String guildId) {
        if (pendingDeletions.putIfAbsent(channelId, true) != null) {
            LOGGER.debug("Channel {} is already being deleted.", channelId);
            return;
        }

        LOGGER.info("Deleting temporary channel: {}", channelId);

        client.deleteChannel(channelId)
                .handle((v, ex) -> ex)
                .thenCompose(ex -> {
                    if (ex == null) {
                        LOGGER.info("Channel {} deleted from Fluxer. Removing from DB.", channelId);
                        return dbManager.removeTemporaryChannel(channelId);
                    }
                    if (isChannelAlreadyDeleted(ex)) {
                        LOGGER.info("Channel {} deleted from Fluxer. Removing from DB.", channelId);
                        return dbManager.removeTemporaryChannel(channelId);
                    }
                    LOGGER.error("Failed to delete channel {}:", channelId, ex);
                    return CompletableFuture.completedFuture(null);
                })
                .thenCompose(v -> guildId != null ? treeService.updateTreePrefixes(guildId)
                        : CompletableFuture.completedFuture(null))
                .exceptionally(ex -> {
                    LOGGER.error("Failed to finish deleting channel {}:", channelId, ex);
                    return null;
                })
                .whenComplete((v, ex) -> {
                    pendingDeletions.remove(channelId);
                    channelLocks.remove(channelId);
                });
    }
}
