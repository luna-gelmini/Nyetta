package com.ladyluh.nyetta.commands;

import flux.api.FluxClient;
import flux.api.entities.User;
import com.ladyluh.nyetta.cache.VoiceStateCacheManager;
import com.ladyluh.nyetta.config.ConfigManager;
import com.ladyluh.nyetta.database.DatabaseManager;
import flux.model.gateway.MessageCreateEvent;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class CommandContext {
    private final FluxClient client;
    private final ConfigManager config;
    private final DatabaseManager dbManager;
    private final VoiceStateCacheManager voiceStateCacheManager;
    private final MessageCreateEvent event;
    private final List<String> args;
    private final String commandName;

    public CommandContext(FluxClient client, ConfigManager config, DatabaseManager dbManager, VoiceStateCacheManager voiceStateCacheManager, MessageCreateEvent event, String commandName, List<String> args) {
        this.client = client;
        this.config = config;
        this.dbManager = dbManager;
        this.voiceStateCacheManager = voiceStateCacheManager;
        this.event = event;
        this.commandName = commandName;
        this.args = args;
    }

    public FluxClient getClient() {
        return client;
    }

    public ConfigManager getConfig() {
        return config;
    }

    public DatabaseManager getDbManager() {
        return dbManager;
    }

    public VoiceStateCacheManager getVoiceStateCacheManager() {
        return voiceStateCacheManager;
    }

    public MessageCreateEvent getEvent() {
        return event;
    }

    public List<String> getArgs() {
        return args;
    }

    public String getCommandName() {
        return commandName;
    }

    public User getAuthor() {
        return event.getAuthor();
    }

    public String getChannelId() {
        return event.getChannelId();
    }

    public String getGuildId() {
        return (event != null && event.getMessage() != null) ? event.getMessage().getGuildId() : null;
    }

    public String getMessageId() {
        return event.getMessage().getId();
    }

    public String getRawContent() {
        return event.getContentRaw();
    }

    public CompletableFuture<Void> reply(String message) {
        if (event == null || event.getMessage() == null) {
            return client.sendMessage(getChannelId(), message).thenAccept(m -> {});
        }

        flux.api.payload.send.MessageSendPayload payload =
            new flux.builder.MessageBuilder(message)
                .setReplyTo(event.getMessage().getId())
                .build();

        return client.sendMessage(getChannelId(), payload)
                .thenAccept(m -> {})
                .exceptionally(ex -> {
                    System.err.println("Erro ao enviar resposta: " + ex.getMessage());
                    return null;
                });
    }
}
