package com.ladyluh.nyetta.services;

import flux.api.FluxClient;
import flux.api.payload.channel.ChannelModifyPayload;
import com.ladyluh.nyetta.database.DatabaseManager;
import com.ladyluh.nyetta.database.TemporaryChannelRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TemporaryChannelTreeService {
    private static final Logger LOGGER = LoggerFactory.getLogger(TemporaryChannelTreeService.class);

    private static final String TREE_MIDDLE = "├";
    private static final String TREE_END = "└";
    private static final Pattern TREE_PREFIX_PATTERN = Pattern.compile("^[├└]\\s*");

    private static final long UPDATE_DELAY_SECONDS = 10;
    private static final long PERIODIC_CHECK_MINUTES = 5;

    private ScheduledExecutorService scheduler;

    private final FluxClient client;
    private final DatabaseManager dbManager;

    public TemporaryChannelTreeService(FluxClient client, DatabaseManager dbManager) {
        this.client = client;
        this.dbManager = dbManager;
    }

    public CompletableFuture<Void> updateTreePrefixes(String guildId) {
        return dbManager.getTemporaryChannelsByGuild(guildId)
                .thenCompose(channels -> {
                    if (channels.isEmpty()) {
                        LOGGER.debug("No temporary channels in guild {} to update prefixes.", guildId);
                        return CompletableFuture.completedFuture(null);
                    }

                    LOGGER.debug("updating tree prefixes for {} channels in guild {}", channels.size(), guildId);
                    return updateChannelPrefixes(channels);
                })
                .exceptionally(ex -> {
                    LOGGER.error("Failed to update tree prefixes for guild {}:", guildId, ex);
                    return null;
                });
    }

    private CompletableFuture<Void> updateChannelPrefixes(List<TemporaryChannelRecord> channels) {
        if (channels.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<?>[] futures = new CompletableFuture[channels.size()];
        int lastIndex = channels.size() - 1;

        for (int i = 0; i < channels.size(); i++) {
            TemporaryChannelRecord channel = channels.get(i);
            String expectedPrefix = (i == lastIndex) ? TREE_END : TREE_MIDDLE;
            futures[i] = updateChannelPrefix(channel.channelId, expectedPrefix);
        }

        return CompletableFuture.allOf(futures);
    }

    private CompletableFuture<Void> updateChannelPrefix(String channelId, String expectedPrefix) {
        return client.getChannelById(channelId)
                .thenCompose(channel -> {
                    if (channel == null) {
                        LOGGER.warn("Channel {} not found, removing from the database.", channelId);
                        return dbManager.removeTemporaryChannel(channelId);
                    }

                    String currentName = channel.getName();
                    String baseName = extractBaseName(currentName);
                    String newName = expectedPrefix + " " + baseName;

                    if (newName.length() > 100) {
                        newName = newName.substring(0, 100);
                    }

                    if (currentName.equals(newName)) {
                        LOGGER.debug("Channel {} already has the correct prefix.", channelId);
                        return CompletableFuture.completedFuture(null);
                    }

                    LOGGER.debug("Updating channel {} name from '{}' to '{}'", channelId, currentName, newName);

                    ChannelModifyPayload payload = new ChannelModifyPayload();
                    payload.setName(newName);
                    return client.modifyChannel(channelId, payload).thenApply(v -> (Void) null);
                })
                .handle((v, ex) -> ex)
                .thenCompose(ex -> {
                    if (ex == null) {
                        return CompletableFuture.completedFuture((Void) null);
                    }
                    String errorMessage = ex.getMessage();
                    if (errorMessage != null
                            && (errorMessage.contains("10003") || errorMessage.contains("Unknown Channel"))) {
                        LOGGER.warn("Channel {} was deleted on Fluxer, removing from the database.", channelId);
                        return dbManager.removeTemporaryChannel(channelId);
                    }
                    LOGGER.error("Failed to update prefix of channel {}:", channelId, ex);
                    return CompletableFuture.completedFuture((Void) null);
                });
    }

    public String extractBaseName(String channelName) {
        if (channelName == null) {
            return "";
        }
        Matcher matcher = TREE_PREFIX_PATTERN.matcher(channelName);
        return matcher.replaceFirst("").trim();
    }

    public String applyTreePrefix(String baseName, boolean isLast) {
        String prefix = isLast ? TREE_END : TREE_MIDDLE;
        String result = prefix + " " + baseName;
        if (result.length() > 100) {
            result = result.substring(0, 100);
        }
        return result;
    }

    public CompletableFuture<Boolean> isLastChannel(String guildId, String channelId) {
        return dbManager.getTemporaryChannelsByGuild(guildId)
                .thenApply(channels -> {
                    if (channels.isEmpty()) {
                        return true;
                    }
                    TemporaryChannelRecord lastChannel = channels.get(channels.size() - 1);
                    return lastChannel.channelId.equals(channelId);
                });
    }

    public void startPeriodicVerification(String guildId) {
        if (scheduler != null && !scheduler.isShutdown()) {
            LOGGER.warn("Periodic check already running for guild {}", guildId);
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "TemporaryChannelTreeScheduler-" + guildId);
            t.setDaemon(true);
            return t;
        });

        LOGGER.debug("periodic prefix checks every {} min for guild {}",
                PERIODIC_CHECK_MINUTES, guildId);

        scheduler.scheduleAtFixedRate(
                () -> verifyAndUpdatePrefixesWithDelay(guildId),
                1,
                PERIODIC_CHECK_MINUTES,
                TimeUnit.MINUTES);
    }

    public void stopPeriodicVerification() {
        if (scheduler != null && !scheduler.isShutdown()) {
            LOGGER.info("Stopping periodic prefix checks.");
            scheduler.shutdown();
        }
    }

    private void verifyAndUpdatePrefixesWithDelay(String guildId) {
        dbManager.getTemporaryChannelsByGuild(guildId)
                .thenAccept(channels -> {
                    if (channels.isEmpty()) {
                        LOGGER.debug("No temporary channels to check in guild {}.", guildId);
                        return;
                    }

                    LOGGER.debug("checking prefixes of {} channels in guild {}", channels.size(), guildId);
                    int lastIndex = channels.size() - 1;

                    for (int i = 0; i < channels.size(); i++) {
                        TemporaryChannelRecord channel = channels.get(i);
                        String expectedPrefix = (i == lastIndex) ? TREE_END : TREE_MIDDLE;

                        try {
                            updateChannelPrefixSync(channel.channelId, expectedPrefix);
                            if (i < lastIndex) {
                                TimeUnit.SECONDS.sleep(UPDATE_DELAY_SECONDS);
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            LOGGER.warn("Periodic check interrupted.");
                            return;
                        }
                    }

                    LOGGER.debug("prefix check finished for guild {}", guildId);
                })
                .exceptionally(ex -> {
                    LOGGER.error("Periodic prefix check failed:", ex);
                    return null;
                });
    }

    private void updateChannelPrefixSync(String channelId, String expectedPrefix) {
        try {
            var channel = client.getChannelById(channelId).join();

            if (channel == null) {
                LOGGER.warn("Channel {} not found, removing from the database.", channelId);
                dbManager.removeTemporaryChannel(channelId).join();
                return;
            }

            String currentName = channel.getName();
            String baseName = extractBaseName(currentName);
            String newName = expectedPrefix + " " + baseName;

            if (newName.length() > 100) {
                newName = newName.substring(0, 100);
            }

            if (currentName.equals(newName)) {
                LOGGER.debug("Channel {} already has the correct prefix.", channelId);
                return;
            }

            LOGGER.debug("Fixing channel {} name from '{}' to '{}'", channelId, currentName, newName);

            ChannelModifyPayload payload = new ChannelModifyPayload();
            payload.setName(newName);
            client.modifyChannel(channelId, payload).join();

        } catch (Exception ex) {
            String message = ex.getMessage();
            if (message != null && (message.contains("10003") || message.contains("Unknown Channel"))) {
                LOGGER.warn("Channel {} was deleted on Fluxer, removing from the database.", channelId);
                dbManager.removeTemporaryChannel(channelId).join();
                return;
            }
            LOGGER.error("Failed to check prefix of channel {}:", channelId, ex);
        }
    }
}
