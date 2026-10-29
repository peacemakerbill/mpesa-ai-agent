package com.mpesaagent.agent.service;

import com.mpesaagent.agent.chat.ChatChannel;
import com.mpesaagent.agent.chat.ChatChannel.Button;
import com.mpesaagent.agent.chat.ChatChannel.ListRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ReplySenderTest {

    private static final String PHONE = "254700000001";

    @Mock ChatChannel channel;

    private ReplySender sender;

    @BeforeEach
    void setUp() {
        sender = new ReplySender(channel);
    }

    @Test
    void textGoesToTheChatTrimmed() {
        sender.text(PHONE, "  **Payment received**\n");

        verify(channel).sendText(PHONE, "**Payment received**");
    }

    @Test
    void blankTextIsNotSent() {
        sender.text(PHONE, "   ");
        sender.text(PHONE, null);

        verifyNoInteractions(channel);
    }

    @Test
    void aNotificationWithoutButtonsIsPlainText() {
        sender.notifyWithButtons(PHONE, "Paid", null, List.of());

        verify(channel).sendText(PHONE, "Paid");
        verify(channel, never()).sendButtons(anyString(), anyString(), isNull(), org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void aNotificationWithButtonsKeepsThem() {
        List<Button> buttons = List.of(new Button("RCPT:1", "Get receipt"));

        sender.notifyWithButtons(PHONE, "Paid", null, buttons);

        verify(channel).sendButtons(eq(PHONE), eq("Paid"), isNull(), eq(buttons));
    }

    @Test
    void listsFilesAndTheContactFinderGoStraightToTheChannel() {
        List<ListRow> rows = List.of(new ListRow("MENU:send", "Send money", null));

        sender.list(PHONE, "Hello", "Choose", "How can I help?", rows);
        sender.document(PHONE, new byte[]{1}, "application/pdf", "receipt.pdf", "Your receipt");
        sender.audio(PHONE, new byte[]{2});
        sender.contactLookup(PHONE, "Mama");

        verify(channel).sendList(PHONE, "Hello", "Choose", "How can I help?", rows);
        verify(channel).sendMedia(PHONE, "DOCUMENT", new byte[]{1}, "application/pdf", "receipt.pdf", "Your receipt");
        verify(channel).sendMedia(PHONE, "AUDIO", new byte[]{2}, "audio/mpeg", "reply.mp3", null);
        verify(channel).sendContactLookup(PHONE, "Mama");
    }
}
