package com.ladyluh.nyetta.listeners;

import flux.api.FluxClient;
import flux.api.entities.Message;
import flux.api.entities.User;
import com.ladyluh.nyetta.commands.CommandManager;
import com.ladyluh.nyetta.database.DatabaseManager;
import flux.model.gateway.MessageCreateEvent;
import com.ladyluh.nyetta.services.XPRoleService;
import org.junit.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MessageEventListenerTest {

    @Test
    public void usesConfiguredPrefixInsteadOfHardcodedExclamationMark() {
        CommandManager commandManager = mock(CommandManager.class);
        MessageEventListener listener = new MessageEventListener(
                mock(FluxClient.class),
                mock(DatabaseManager.class),
                commandManager,
                mock(XPRoleService.class),
                "?"
        );

        listener.onEvent(messageEvent("?ping"));

        verify(commandManager).handleCommand(eq("ping"), any(List.class), any(MessageCreateEvent.class));
    }

    @Test
    public void ignoresExclamationMarkWhenConfiguredPrefixIsDifferent() {
        CommandManager commandManager = mock(CommandManager.class);
        MessageEventListener listener = new MessageEventListener(
                mock(FluxClient.class),
                mock(DatabaseManager.class),
                commandManager,
                mock(XPRoleService.class),
                "?"
        );

        listener.onEvent(messageEvent("!ping"));

        verify(commandManager, never()).handleCommand(any(), any(), any());
    }

    @Test
    public void defaultsToExclamationMarkWhenPrefixIsBlank() {
        CommandManager commandManager = mock(CommandManager.class);
        MessageEventListener listener = new MessageEventListener(
                mock(FluxClient.class),
                mock(DatabaseManager.class),
                commandManager,
                mock(XPRoleService.class),
                "   "
        );

        listener.onEvent(messageEvent("!ping"));

        verify(commandManager).handleCommand(eq("ping"), any(List.class), any(MessageCreateEvent.class));
    }

    private MessageCreateEvent messageEvent(String content) {
        FluxClient client = mock(FluxClient.class);
        Message message = mock(Message.class);
        User author = mock(User.class);

        when(author.isBot()).thenReturn(false);
        when(author.getAsTag()).thenReturn("tester#0001");
        when(author.getId()).thenReturn("user-1");
        when(message.getAuthor()).thenReturn(author);
        when(message.getContentRaw()).thenReturn(content);
        when(message.getGuildId()).thenReturn(null);
        when(message.getChannelId()).thenReturn("channel-1");
        when(message.getId()).thenReturn("message-1");

        return new MessageCreateEvent(client, message);
    }
}
