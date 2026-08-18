package com.ladyluh.nyetta.commands;

import flux.api.FluxClient;
import flux.api.entities.Member;
import flux.api.entities.Message;
import flux.api.entities.User;
import flux.api.payload.permission.Permission;
import com.ladyluh.nyetta.cache.VoiceStateCacheManager;
import com.ladyluh.nyetta.cache.SnipeCache;
import com.ladyluh.nyetta.config.ConfigManager;
import com.ladyluh.nyetta.database.DatabaseManager;
import flux.model.gateway.MessageCreateEvent;
import com.ladyluh.nyetta.services.TemporaryChannelTreeService;
import org.junit.Test;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CommandManagerTest {

    @Test
    public void doesNotGrantAdminAccessByUsernameAloneForMessageCommands() throws Exception {
        FluxClient client = mock(FluxClient.class);
        ConfigManager config = new ConfigManager(new Properties());
        DatabaseManager dbManager = mock(DatabaseManager.class);
        VoiceStateCacheManager voiceStateCacheManager = mock(VoiceStateCacheManager.class);
        TemporaryChannelTreeService treeService = mock(TemporaryChannelTreeService.class);
        CommandManager commandManager = new CommandManager(
                client,
                config,
                dbManager,
                voiceStateCacheManager,
                treeService,
                new SnipeCache()
        );

        MessageCreateEvent event = messageEvent("guild-1", "channel-1", "config", "neko.lun", "user-1");
        Member member = mock(Member.class);
        when(member.getEffectiveName()).thenReturn("neko.lun");
        when(member.getId()).thenReturn("user-1");
        when(member.hasPermission(Permission.ADMINISTRATOR)).thenReturn(CompletableFuture.completedFuture(false));
        when(client.getGuildMember("guild-1", "user-1")).thenReturn(CompletableFuture.completedFuture(member));
        when(client.sendMessage(any(String.class), any(String.class))).thenReturn(CompletableFuture.completedFuture(null));
        when(client.sendMessage(eq("channel-1"), any(flux.api.payload.send.MessageSendPayload.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        commandManager.handleCommand("config", List.of("show"), event);

        verify(client, never()).sendMessage(eq("channel-1"), eq("This command can only be used in a server."));
        verify(client).getGuildMember("guild-1", "user-1");
        verify(client).sendMessage(eq("channel-1"), any(flux.api.payload.send.MessageSendPayload.class));
    }

    private MessageCreateEvent messageEvent(String guildId, String channelId, String content, String username, String userId) {
        FluxClient client = mock(FluxClient.class);
        Message message = mock(Message.class);
        User author = mock(User.class);

        when(author.isBot()).thenReturn(false);
        when(author.getUsername()).thenReturn(username);
        when(author.getAsTag()).thenReturn(username + "#0001");
        when(author.getId()).thenReturn(userId);
        when(message.getAuthor()).thenReturn(author);
        when(message.getContentRaw()).thenReturn(content);
        when(message.getGuildId()).thenReturn(guildId);
        when(message.getChannelId()).thenReturn(channelId);
        when(message.getId()).thenReturn("message-1");

        return new MessageCreateEvent(client, message);
    }
}
