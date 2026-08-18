package com.ladyluh.nyetta.listeners;

import flux.api.FluxClient;
import flux.api.entities.Guild;
import flux.api.entities.Member;
import flux.api.entities.User;
import flux.api.event.guild.member.GuildMemberAddEvent;
import com.ladyluh.nyetta.database.DatabaseManager;
import com.ladyluh.nyetta.database.GuildConfig;
import org.junit.Test;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GuildEventListenerTest {

    @Test
    public void doesNotSendWelcomeEmbedToWelcomeChannel() {
        FluxClient client = mock(FluxClient.class);
        DatabaseManager dbManager = mock(DatabaseManager.class);
        GuildEventListener listener = new GuildEventListener(client, dbManager);

        GuildConfig guildConfig = new GuildConfig("guild-1");
        guildConfig.welcomeChannelId = "welcome-1";
        when(dbManager.getGuildConfig("guild-1")).thenReturn(CompletableFuture.completedFuture(Optional.of(guildConfig)));

        GuildMemberAddEvent event = mock(GuildMemberAddEvent.class);
        Member member = mock(Member.class);
        User user = mock(User.class);
        Guild guild = mock(Guild.class);

        when(event.getMember()).thenReturn(member);
        when(event.getGuildId()).thenReturn("guild-1");
        when(event.getGuild()).thenReturn(CompletableFuture.completedFuture(guild));
        when(guild.getName()).thenReturn("Guild Test");
        when(member.getId()).thenReturn("user-1");
        when(member.getEffectiveName()).thenReturn("tester");
        when(member.getUser()).thenReturn(user);
        when(user.getAsTag()).thenReturn("tester#0001");

        listener.onEvent(event);

        verify(client, never()).sendMessage(eq("welcome-1"), any(flux.api.payload.send.MessageSendPayload.class));
    }
}
