package com.mganji.agent.chat;

import com.mganji.agent.TestSupport;
import com.mganji.agent.exception.ChatException;
import com.mganji.agent.store.InMemoryStateStore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatChannelTest {

    private static final String A = "254700000001";
    private static final String B = "254700000002";

    private final InMemoryStateStore state = new InMemoryStateStore();
    private final ChatLog log = new ChatLog(state, TestSupport.mapper());
    private final ChatMedia media = new ChatMedia(state, TestSupport.mapper());
    private final ChatChannel channel = new ChatChannel(log, media);

    @Test
    void messagesAreNumberedInOrderAndReadBack() {
        channel.sendText(A, "one");
        channel.sendButtons(A, "two", "footer", List.of(new ChatChannel.Button("X", "Do it")));
        channel.sendList(A, "three", "Choose", "Section", List.of(new ChatChannel.ListRow("R1", "Row", "About")));

        List<ChatMessage> all = log.after(A, 0, 100);

        assertThat(all).extracting(m -> m.id).containsExactly(1L, 2L, 3L);
        assertThat(all).extracting(m -> m.kind).containsExactly("TEXT", "BUTTONS", "LIST");
        assertThat(all.get(1).buttons).hasSize(1);
        assertThat(all.get(1).footer).isEqualTo("footer");
        assertThat(all.get(2).rows.get(0).title()).isEqualTo("Row");
        assertThat(log.lastId(A)).isEqualTo(3L);
    }

    @Test
    void anAppAsksOnlyForWhatIsNewerThanItHas() {
        channel.sendText(A, "one");
        channel.sendText(A, "two");
        channel.sendText(A, "three");

        assertThat(log.after(A, 2, 100)).extracting(m -> m.text).containsExactly("three");
        assertThat(log.after(A, 0, 2)).extracting(m -> m.text).containsExactly("one", "two");
        assertThat(log.after(A, 3, 100)).isEmpty();
    }

    @Test
    void eachCustomerHasTheirOwnChat() {
        channel.sendText(A, "for a");
        channel.sendText(B, "for b");

        assertThat(log.after(A, 0, 10)).extracting(m -> m.text).containsExactly("for a");
        assertThat(log.after(B, 0, 10)).extracting(m -> m.text).containsExactly("for b");
        assertThat(log.after(B, 0, 10).get(0).id).isEqualTo(1L);
    }

    @Test
    void aReplyEndsTheTypingLine() {
        channel.typing(A, true);
        assertThat(log.isTyping(A)).isTrue();

        channel.sendText(A, "done");

        assertThat(log.isTyping(A)).isFalse();
    }

    @Test
    void theCustomersOwnMessageDoesNotEndTheTypingLine() {
        channel.typing(A, true);

        log.append(A, ChatMessage.fromInbound(com.mganji.agent.dto.InboundMessage.text("m1", A, "hello")));

        assertThat(log.isTyping(A)).isTrue();
        assertThat(log.after(A, 0, 10).get(0).dir).isEqualTo("in");
    }

    @Test
    void aFileIsStoredAndOnlyItsOwnerCanFetchIt() {
        channel.sendMedia(A, "DOCUMENT", new byte[]{1, 2, 3}, "application/pdf", "receipt.pdf", "Your receipt");

        ChatMessage m = log.after(A, 0, 10).get(0);
        assertThat(m.kind).isEqualTo("DOCUMENT");
        assertThat(m.fileName).isEqualTo("receipt.pdf");
        assertThat(m.caption).isEqualTo("Your receipt");

        ChatChannel.Media got = channel.download(A, m.mediaId);
        assertThat(got.bytes()).containsExactly(1, 2, 3);
        assertThat(got.mimeType()).isEqualTo("application/pdf");
        assertThatThrownBy(() -> channel.download(B, m.mediaId)).isInstanceOf(ChatException.class)
                .satisfies(e -> assertThat(((ChatException) e).isNotFound()).isTrue());
    }

    @Test
    void aMadeUpFileIdIsNotFound() {
        assertThatThrownBy(() -> channel.download(A, "../etc/passwd")).isInstanceOf(ChatException.class);
        assertThatThrownBy(() -> channel.download(A, null)).isInstanceOf(ChatException.class);
    }

    @Test
    void theContactFinderIsAMessageTheAppCanRecognise() {
        channel.sendContactLookup(A, "Mama");

        ChatMessage m = log.after(A, 0, 10).get(0);
        assertThat(m.kind).isEqualTo("CONTACT_LOOKUP");
        assertThat(m.query).isEqualTo("Mama");
    }

    @Test
    void clearingStartsTheChatOverButKeepsNumbering() {
        channel.sendText(A, "old");
        log.clear(A);
        channel.sendText(A, "new");

        List<ChatMessage> all = log.after(A, 0, 10);
        assertThat(all).extracting(m -> m.text).containsExactly("new");
        assertThat(all.get(0).id).isEqualTo(2L);
    }

    @Test
    void theLogKeepsOnlyTheNewestMessages() {
        for (int i = 0; i < ChatLog.KEEP + 20; i++) {
            channel.sendText(A, "m" + i);
        }

        List<ChatMessage> all = log.after(A, 0, 1000);
        assertThat(all).hasSize(ChatLog.KEEP);
        assertThat(all.get(all.size() - 1).text).isEqualTo("m" + (ChatLog.KEEP + 19));
    }
}
