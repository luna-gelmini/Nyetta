package com.ladyluh.nyetta.cache;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class SnipeCache {
    public record SnipedMessage(String authorId, String authorTag, String content, long deletedAt) {
    }

    private final Map<String, LiveMessage> live = new ConcurrentHashMap<>();
    private final Map<String, SnipedMessage> lastDeleted = new ConcurrentHashMap<>();

    public void remember(String channelId, String messageId, String authorId, String authorTag, String content) {
        if (channelId == null || messageId == null) {
            return;
        }
        live.put(messageId, new LiveMessage(channelId, authorId, authorTag, content == null ? "" : content));
        if (live.size() > 500) {
            live.keySet().stream().findFirst().ifPresent(live::remove);
        }
    }

    public void onDelete(String channelId, String messageId) {
        LiveMessage liveMessage = live.remove(messageId);
        if (liveMessage == null || channelId == null) {
            return;
        }
        lastDeleted.put(channelId, new SnipedMessage(
                liveMessage.authorId,
                liveMessage.authorTag,
                liveMessage.content,
                System.currentTimeMillis()));
    }

    public Optional<SnipedMessage> lastDeleted(String channelId) {
        return Optional.ofNullable(lastDeleted.get(channelId));
    }

    private record LiveMessage(String channelId, String authorId, String authorTag, String content) {
    }
}
