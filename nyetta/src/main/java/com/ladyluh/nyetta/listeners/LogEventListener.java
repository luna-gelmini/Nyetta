package com.ladyluh.nyetta.listeners;

import flux.api.ApiEnvironment;
import flux.api.FluxClient;
import flux.api.entities.Message;
import flux.api.entities.User;
import flux.api.event.Event;
import flux.api.event.EventListener;
import flux.api.event.message.MessageDeleteEvent;
import flux.api.event.message.MessageUpdateEvent;
import flux.builder.EmbedBuilder;
import flux.builder.MessageBuilder;
import com.ladyluh.nyetta.database.DatabaseManager;
import com.ladyluh.nyetta.database.GuildConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

import flux.api.event.voice.VoiceStateUpdateEvent;
import flux.api.event.guild.member.GuildMemberAddEvent;
import flux.api.event.guild.member.GuildMemberRemoveEvent;

public class LogEventListener implements EventListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(LogEventListener.class);
    private final com.ladyluh.nyetta.services.WelcomeImageService welcomeImageService;
    private final FluxClient client;
    private final DatabaseManager dbManager;
    private final com.ladyluh.nyetta.cache.SnipeCache snipeCache;

    public LogEventListener(FluxClient client, DatabaseManager dbManager, com.ladyluh.nyetta.services.WelcomeImageService welcomeImageService, com.ladyluh.nyetta.cache.SnipeCache snipeCache) {
        this.client = client;
        this.dbManager = dbManager;
        this.welcomeImageService = welcomeImageService;
        this.snipeCache = snipeCache;
    }

    @Override
    public void onEvent(Event event) {
        if (event instanceof MessageUpdateEvent muEvent) {
            handleMessageUpdate(muEvent);
        } else if (event instanceof MessageDeleteEvent mdEvent) {
            handleMessageDelete(mdEvent);
        } else if (event instanceof VoiceStateUpdateEvent vsuEvent) {
            handleVoiceStateUpdate(vsuEvent);
        } else if (event instanceof GuildMemberAddEvent gmaEvent) {
            handleMemberJoin(gmaEvent);
        } else if (event instanceof GuildMemberRemoveEvent gmrEvent) {
            handleMemberLeave(gmrEvent);
        }
    }

    private void handleMemberJoin(GuildMemberAddEvent event) {
        String guildId = event.getGuildId();
        if (guildId == null) return;

        dbManager.getGuildConfig(guildId)
            .thenAccept(configOpt -> {
                GuildConfig guildConfig = configOpt.orElse(new GuildConfig(guildId));

                String welcomeChannelId = guildConfig.welcomeChannelId;
                if (welcomeChannelId != null && !welcomeChannelId.isEmpty()) {
                    java.io.File imageFile = welcomeImageService.generateWelcomeImage(event.getMember().getUser(), null);
                    if (imageFile != null) {
                         okhttp3.MultipartBody.Builder builder = new okhttp3.MultipartBody.Builder().setType(okhttp3.MultipartBody.FORM);
                         String welcomeMsg = String.format("Bem-vindo(a) ao servidor, <@%s>!", event.getMember().getUser().getId());
                         builder.addFormDataPart("payload_json", "{\"content\": \"" + welcomeMsg + "\"}");
                         builder.addFormDataPart("files[0]", imageFile.getName(),
                                 okhttp3.RequestBody.create(imageFile, okhttp3.MediaType.parse("image/png")));

                         client.sendMessage(welcomeChannelId, builder.build())
                                 .thenRun(() -> {
                                     try { java.nio.file.Files.deleteIfExists(imageFile.toPath()); } catch (Exception ignored) {}
                                 });
                    }
                }

                String logChannelId = guildConfig.logChannelId;
                if (logChannelId == null || logChannelId.isEmpty()) return;

                String userId = event.getMember().getUser().getId();

                EmbedBuilder logEmbed = new EmbedBuilder()
                        .setTitle("👋 Membro Entrou")
                        .setColor(Color.GREEN)
                        .addField("Usuário", "<@" + userId + ">", true)
                        .addField("ID", "`" + userId + "`", true)
                        .setTimestamp(OffsetDateTime.now());

                 client.sendMessage(logChannelId, new MessageBuilder().addEmbed(logEmbed).build())
                        .exceptionally(ex -> null);
            });
    }

    private void handleMessageUpdate(MessageUpdateEvent event) {
        String guildId = event.getMessage().getGuildId();
        if (guildId == null) return;

        dbManager.getGuildConfig(guildId)
                .thenAccept(configOpt -> {
                    GuildConfig guildConfig = configOpt.orElse(new GuildConfig(guildId));
                    String logChannelId = guildConfig.logChannelId;

                    if (logChannelId == null || logChannelId.isEmpty()) {
                        LOGGER.trace("Log channel ID not configured for guild {}. Skipping MessageUpdate log.", guildId);
                        return;
                    }

                    Message updatedMessage = event.getMessage();
                    User author = updatedMessage.getAuthor();

                    if (author != null && author.getId().equals(client.getSelfUser().getId())) {
                        return;
                    }

                    if (updatedMessage.getContentRaw() == null) {
                        return;
                    }

                    EmbedBuilder logEmbed = new EmbedBuilder()
                            .setTitle("📝 Mensagem Editada")
                            .setColor(Color.ORANGE)
                            .addField("Canal", "<#" + updatedMessage.getChannelId() + "> (`" + updatedMessage.getChannelId() + "`)", true);

                    if (author != null) {
                        logEmbed.addField("Autor", author.getAsTag() + " (`" + author.getId() + "`)", true);
                    } else if (updatedMessage.getAuthor() != null && updatedMessage.getAuthor().getId() != null) {
                        logEmbed.addField("Autor ID", "`" + updatedMessage.getAuthor().getId() + "`", true);
                    } else {
                        logEmbed.addField("Autor", "Desconhecido", true);
                    }

                    logEmbed.addField("ID da Mensagem", "`" + updatedMessage.getId() + "`", false)
                            .addField("Novo Conteúdo", updatedMessage.getContentRaw() != null && !updatedMessage.getContentRaw().isEmpty() ? updatedMessage.getContentRaw() : "*Conteúdo não presente ou embed editado*", false)
                            .setTimestamp(OffsetDateTime.now());

                    String messageLink = ApiEnvironment.channelMessageUrl(
                            guildId,
                            updatedMessage.getChannelId(),
                            updatedMessage.getId());
                    logEmbed.addField("Link", "[Pular para Mensagem](" + messageLink + ")", false);

                    client.sendMessage(logChannelId, new MessageBuilder().addEmbed(logEmbed).build())
                            .exceptionally(ex -> {
                                LOGGER.error("Failed to send MessageUpdate log for guild {}:", guildId, ex);
                                return null;
                            });
                    LOGGER.info("Log: message {} edited in channel {} of guild {}", updatedMessage.getId(), updatedMessage.getChannelId(), guildId);
                })
                .exceptionally(ex -> {
                    LOGGER.error("Failed to load guild config for MessageUpdateEvent");
                    return null;
                });
    }

    private void handleMessageDelete(MessageDeleteEvent event) {
        snipeCache.onDelete(event.getChannelId(), event.getMessageId());
        String guildId = event.getGuildId();
        if (guildId == null) return;

        dbManager.getGuildConfig(guildId)
                .thenAccept(configOpt -> {
                    GuildConfig guildConfig = configOpt.orElse(new GuildConfig(guildId));
                    String logChannelId = guildConfig.logChannelId;

                    if (logChannelId == null || logChannelId.isEmpty()) {
                        LOGGER.trace("Log channel ID not configured for guild {}. Skipping MessageDelete log.", guildId);
                        return;
                    }

                    EmbedBuilder logEmbed = new EmbedBuilder()
                            .setTitle("🗑️ Mensagem Deletada")
                            .setColor(Color.RED)
                            .addField("Canal", "<#" + event.getChannelId() + "> (`" + event.getChannelId() + "`)", false)
                            .addField("ID da Mensagem", "`" + event.getMessageId() + "`", false)
                            .setTimestamp(OffsetDateTime.now());

                    logEmbed.addField("Servidor ID", "`" + guildId + "`", true);

                    client.sendMessage(logChannelId, new MessageBuilder().addEmbed(logEmbed).build())
                            .exceptionally(ex -> {
                                LOGGER.error("Failed to send MessageDelete log for guild {}:", guildId, ex);
                                return null;
                            });
                    LOGGER.info("Log: message {} deleted in channel {} of guild {}", event.getMessageId(), event.getChannelId(), guildId);
                })
                .exceptionally(ex -> {
                    LOGGER.error("Failed to load guild config for MessageDeleteEvent");
                    return null;
                });
    }

    private void handleVoiceStateUpdate(VoiceStateUpdateEvent event) {
        String guildId = event.getGuildId();
        if (guildId == null) return;

        dbManager.getGuildConfig(guildId)
            .thenAccept(configOpt -> {
                GuildConfig guildConfig = configOpt.orElse(new GuildConfig(guildId));
                String logChannelId = guildConfig.logChannelId;

                if (logChannelId == null || logChannelId.isEmpty()) return;

                String userId = event.getUserId();
                String channelId = event.getChannelId();

                EmbedBuilder logEmbed = new EmbedBuilder()
                        .setTimestamp(OffsetDateTime.now());

                if (channelId != null) {
                    logEmbed.setTitle("🔊 Atividade de Voz: Conectado")
                            .setColor(Color.GREEN)
                            .addField("Usuário", "<@" + userId + ">", true)
                            .addField("Canal", "<#" + channelId + "> (`" + channelId + "`)", true);
                } else {
                    logEmbed.setTitle("🔇 Atividade de Voz: Desconectado")
                            .setColor(Color.RED)
                            .addField("Usuário", "<@" + userId + ">", true)
                            .addField("Canal", "*Desconectado de canal de voz*", true);
                }

                client.sendMessage(logChannelId, new MessageBuilder().addEmbed(logEmbed).build())
                        .exceptionally(ex -> null);
            });
    }

    private void handleMemberLeave(GuildMemberRemoveEvent event) {
        String guildId = event.getGuildId();
        if (guildId == null) return;

        dbManager.getGuildConfig(guildId)
            .thenAccept(configOpt -> {
                GuildConfig guildConfig = configOpt.orElse(new GuildConfig(guildId));
                String logChannelId = guildConfig.logChannelId;

                if (logChannelId == null || logChannelId.isEmpty()) return;

                String userId = event.getUser().getId();

                EmbedBuilder logEmbed = new EmbedBuilder()
                        .setTitle("👋 Membro Saiu")
                        .setColor(Color.RED)
                        .addField("Usuário", "<@" + userId + ">", true)
                        .addField("ID", "`" + userId + "`", true)
                        .setTimestamp(OffsetDateTime.now());

                 client.sendMessage(logChannelId, new MessageBuilder().addEmbed(logEmbed).build())
                        .exceptionally(ex -> null);
            });
    }
}
