package com.ladyluh.nyetta.commands.impl;

import flux.api.FluxClient;
import flux.api.entities.Member;
import flux.api.entities.Message;
import flux.api.entities.User;
import flux.api.payload.permission.Permission;
import com.ladyluh.nyetta.cache.VoiceStateCacheManager;
import com.ladyluh.nyetta.commands.CommandContext;
import com.ladyluh.nyetta.config.ConfigManager;
import com.ladyluh.nyetta.database.DatabaseManager;
import com.ladyluh.nyetta.database.GuildConfig;
import flux.model.gateway.MessageCreateEvent;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ConfigCommandTest {

    @Test
    public void auditSubcommandIsAdvertisedInUsage() {
        ConfigCommand command = new ConfigCommand((DatabaseManager) null);

        assertTrue(command.getUsage().contains("audit"));
    }

    @Test
    public void auditReportsMissingWelcomeAndLogChannels() throws Exception {
        FluxClient client = mock(FluxClient.class);
        DatabaseManager dbManager = mock(DatabaseManager.class);
        ConfigCommand command = new ConfigCommand(dbManager);

        GuildConfig guildConfig = new GuildConfig("guild-1");
        guildConfig.logChannelId = "";
        guildConfig.welcomeChannelId = "";
        when(dbManager.getGuildConfig("guild-1")).thenReturn(CompletableFuture.completedFuture(Optional.of(guildConfig)));

        Member member = mock(Member.class);
        when(member.hasPermission(Permission.ADMINISTRATOR)).thenReturn(CompletableFuture.completedFuture(true));
        when(client.getGuildMember("guild-1", "user-1")).thenReturn(CompletableFuture.completedFuture(member));
        when(client.sendMessage(eq("channel-1"), any(flux.api.payload.send.MessageSendPayload.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        command.execute(context(client, List.of("audit")));

        verify(client).sendMessage(eq("channel-1"), any(flux.api.payload.send.MessageSendPayload.class));
    }

    @Test
    public void invalidConfigKeyProducesClearError() throws Exception {
        FluxClient client = mock(FluxClient.class);
        DatabaseManager dbManager = mock(DatabaseManager.class);
        ConfigCommand command = new ConfigCommand(dbManager);

        when(dbManager.getGuildConfig("guild-1")).thenReturn(CompletableFuture.completedFuture(Optional.of(new GuildConfig("guild-1"))));

        Member member = mock(Member.class);
        when(member.hasPermission(Permission.ADMINISTRATOR)).thenReturn(CompletableFuture.completedFuture(true));
        when(client.getGuildMember("guild-1", "user-1")).thenReturn(CompletableFuture.completedFuture(member));
        when(client.sendMessage(eq("channel-1"), any(flux.api.payload.send.MessageSendPayload.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        command.execute(context(client, List.of("set", "nope", "value")));

        ArgumentCaptor<flux.api.payload.send.MessageSendPayload> payloadCaptor = ArgumentCaptor.forClass(flux.api.payload.send.MessageSendPayload.class);
        verify(client).sendMessage(eq("channel-1"), payloadCaptor.capture());
        assertEquals("Unknown config key: 'nope'. Use `!config show` or `!config audit` to review the current options.", payloadCaptor.getValue().getContent());
    }

    @Test
    public void logChannelKeyIsAcceptedByConfigCommand() throws Exception {
        FluxClient client = mock(FluxClient.class);
        DatabaseManager dbManager = mock(DatabaseManager.class);
        ConfigCommand command = new ConfigCommand(dbManager);

        GuildConfig guildConfig = new GuildConfig("guild-1");
        when(dbManager.getGuildConfig("guild-1")).thenReturn(CompletableFuture.completedFuture(Optional.of(guildConfig)));
        when(dbManager.updateGuildConfig(any(GuildConfig.class))).thenReturn(CompletableFuture.completedFuture(null));

        Member member = mock(Member.class);
        when(member.hasPermission(Permission.ADMINISTRATOR)).thenReturn(CompletableFuture.completedFuture(true));
        when(client.getGuildMember("guild-1", "user-1")).thenReturn(CompletableFuture.completedFuture(member));
        when(client.sendMessage(eq("channel-1"), any(flux.api.payload.send.MessageSendPayload.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        command.execute(context(client, List.of("set", "log_channel", "123"))).get();

        org.mockito.Mockito.verify(dbManager).updateGuildConfig(any(GuildConfig.class));
        assertEquals("123", guildConfig.logChannelId);
    }

    private CommandContext context(FluxClient client, List<String> args) throws Exception {
        MessageCreateEvent event = mock(MessageCreateEvent.class);
        Message message = mock(Message.class);
        User user = mock(User.class);

        when(event.getMessage()).thenReturn(message);
        when(event.getAuthor()).thenReturn(user);
        when(event.getChannelId()).thenReturn("channel-1");
        when(message.getGuildId()).thenReturn("guild-1");
        when(message.getId()).thenReturn("message-1");
        when(user.getId()).thenReturn("user-1");
        when(user.getAsTag()).thenReturn("tester#0001");
        when(user.getEffectiveAvatarUrl()).thenReturn("https://example/avatar.png");
        when(client.getSelfUser()).thenReturn(user);

        return new CommandContext(
                client,
                new ConfigManager(new Properties()),
                mock(DatabaseManager.class),
                mock(VoiceStateCacheManager.class),
                event,
                "config",
                args
        );
    }
}
