package com.darajaagentx.agent.chat;

import com.darajaagentx.agent.TestSupport;
import com.darajaagentx.agent.store.InMemoryStateStore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatSessionsTest {

    private static final String A = "254700000001";
    private static final String B = "254700000002";

    private final InMemoryStateStore state = new InMemoryStateStore();
    private final ChatLog log = new ChatLog(state, TestSupport.mapper());
    private final ChatSessions sessions = new ChatSessions(state, TestSupport.mapper(), log);

    private void customer(String phone, String text) {
        ChatMessage m = new ChatMessage();
        m.dir = "in";
        m.kind = "TEXT";
        m.text = text;
        log.append(phone, m);
        state.appendToList("chat:history:" + phone, "{\"role\":\"user\",\"text\":\"" + text + "\"}", 20, java.time.Duration.ofHours(1));
    }

    private void assistant(String phone, String text) {
        ChatMessage m = new ChatMessage();
        m.dir = "out";
        m.kind = "TEXT";
        m.text = text;
        log.append(phone, m);
        state.appendToList("chat:history:" + phone, "{\"role\":\"assistant\",\"text\":\"" + text + "\"}", 20, java.time.Duration.ofHours(1));
    }

    private static List<String> titles(ChatSessions.Listing listing) {
        return listing.chats().stream().map(ChatSessions.Summary::title).toList();
    }

    private static ChatSessions.Summary chat(ChatSessions.Listing listing, String title) {
        return listing.chats().stream().filter(c -> c.title().equals(title)).findFirst().orElseThrow();
    }

    private static ChatSessions.Failure failure(Runnable action) {
        try {
            action.run();
        } catch (ChatSessions.Failure e) {
            return e;
        }
        throw new AssertionError("expected the call to be refused");
    }

    private List<String> liveTexts(String phone) {
        return log.after(phone, 0, 500).stream().map(m -> m.text).toList();
    }

    @Test
    void aCustomerStartsWithOneEmptyChat() {
        ChatSessions.Listing listing = sessions.list(A);

        assertThat(listing.chats()).hasSize(1);
        assertThat(listing.chats().get(0).title()).isEqualTo("New chat");
        assertThat(listing.chats().get(0).active()).isTrue();
        assertThat(listing.chats().get(0).messages()).isEqualTo(0);
        assertThat(listing.activeId()).isEqualTo(listing.chats().get(0).id());
    }

    @Test
    void theLiveChatIsNamedAfterTheFirstThingTheCustomerSaid() {
        customer(A, "How much is in my wallet today?");
        assistant(A, "You have KES 1,200.");

        ChatSessions.Summary only = sessions.list(A).chats().get(0);

        assertThat(only.title()).isEqualTo("How much is in my wallet today?");
        assertThat(only.preview()).isEqualTo("You have KES 1,200.");
        assertThat(only.messages()).isEqualTo(2);
    }

    @Test
    void aLongFirstMessageIsCutAtAWordAndTheNameDoesNotChangeLater() {
        customer(A, "Please send two hundred shillings to my brother for the school books he needs");
        String first = sessions.list(A).chats().get(0).title();
        customer(A, "Something else entirely");

        assertThat(first).isEqualTo("Please send two hundred shillings to my...");
        assertThat(sessions.list(A).chats().get(0).title()).isEqualTo(first);
    }

    @Test
    void aPhotoWithoutWordsIsCalledPhoto() {
        ChatMessage m = new ChatMessage();
        m.dir = "in";
        m.kind = "IMAGE";
        m.mediaId = "x";
        log.append(A, m);

        assertThat(sessions.list(A).chats().get(0).title()).isEqualTo("Photo");
    }

    @Test
    void aNewChatKeepsTheOldOneAndStartsEmpty() {
        customer(A, "First topic");
        assistant(A, "Sure");

        ChatSessions.Listing listing = sessions.create(A);

        assertThat(listing.chats()).hasSize(2);
        assertThat(titles(listing)).contains("First topic", "New chat");
        assertThat(chat(listing, "New chat").active()).isTrue();
        assertThat(chat(listing, "First topic").messages()).isEqualTo(2);
        assertThat(log.after(A, 0, 100)).isEmpty();
        assertThat(state.readList("chat:history:" + A)).isEmpty();
    }

    @Test
    void askingForAnotherNewChatWhileTheCurrentOneIsEmptyChangesNothing() {
        customer(A, "First topic");
        sessions.create(A);

        ChatSessions.Listing again = sessions.create(A);

        assertThat(again.chats()).hasSize(2);
    }

    @Test
    void openingAChatBringsBackItsMessagesAndWhatTheAssistantRemembers() {
        customer(A, "Topic one");
        assistant(A, "Answer one");
        ChatSessions.Listing afterNew = sessions.create(A);
        customer(A, "Topic two");
        assistant(A, "Answer two");
        String one = chat(sessions.list(A), "Topic one").id();

        ChatSessions.Listing opened = sessions.open(A, one);

        assertThat(opened.activeId()).isEqualTo(one);
        assertThat(liveTexts(A)).isEqualTo(List.of("Topic one", "Answer one"));
        assertThat(state.readList("chat:history:" + A)).hasSize(2);
        assertThat(state.readList("chat:history:" + A).get(0)).contains("Topic one");
        assertThat(chat(opened, "Topic two").messages()).isEqualTo(2);
        assertThat(chat(opened, "Topic two").active()).isEqualTo(false);
        assertThat(afterNew.chats()).hasSize(2);

        ChatSessions.Listing back = sessions.open(A, chat(opened, "Topic two").id());

        assertThat(liveTexts(A)).isEqualTo(List.of("Topic two", "Answer two"));
        assertThat(back.chats()).hasSize(2);
    }

    @Test
    void restoredMessagesGetNumbersAboveWhatAnOpenAppHasSeen() {
        customer(A, "Topic one");
        assistant(A, "Answer one");
        long seen = log.lastId(A);
        sessions.create(A);
        customer(A, "Topic two");
        String one = chat(sessions.list(A), "Topic one").id();

        sessions.open(A, one);

        List<ChatMessage> live = log.after(A, 0, 100);
        assertThat(live.get(0).id > seen).isTrue();
        assertThat(live.get(1).id > live.get(0).id).isTrue();
        assertThat(log.lastId(A)).isEqualTo(live.get(1).id);
    }

    @Test
    void openingAnotherChatDropsTheCurrentOneWhenItWasEmptyAndPlain() {
        customer(A, "Topic one");
        sessions.create(A);
        String one = chat(sessions.list(A), "Topic one").id();

        ChatSessions.Listing opened = sessions.open(A, one);

        assertThat(opened.chats()).hasSize(1);
        assertThat(opened.activeId()).isEqualTo(one);
    }

    @Test
    void openingTheCurrentChatChangesNothing() {
        customer(A, "Topic one");
        String one = sessions.list(A).activeId();

        ChatSessions.Listing opened = sessions.open(A, one);

        assertThat(opened.chats()).hasSize(1);
        assertThat(liveTexts(A)).isEqualTo(List.of("Topic one"));
    }

    @Test
    void aChatThatCannotBeFoundIsRefused() {
        assertThat(failure(() -> sessions.open(A, "nope")).status()).isEqualTo(404);
        assertThat(failure(() -> sessions.rename(A, "nope", "x")).status()).isEqualTo(404);
        assertThat(failure(() -> sessions.pin(A, "nope", true)).status()).isEqualTo(404);
        assertThat(failure(() -> sessions.delete(A, "nope")).status()).isEqualTo(404);
    }

    @Test
    void aSavedChatThatHasExpiredOpensEmptyInsteadOfFailing() {
        customer(A, "Topic one");
        sessions.create(A);
        ChatSessions.Summary one = chat(sessions.list(A), "Topic one");
        state.delete("chat:saved:" + A + ":" + one.id());

        ChatSessions.Listing opened = sessions.open(A, one.id());

        assertThat(opened.activeId()).isEqualTo(one.id());
        assertThat(log.after(A, 0, 100)).isEmpty();
    }

    @Test
    void renamingGivesTheChatYourNameAndAnEmptyNameGoesBack() {
        customer(A, "Topic one");
        String id = sessions.list(A).activeId();

        ChatSessions.Listing renamed = sessions.rename(A, id, "  Rent   and   bills ");
        assertThat(renamed.chats().get(0).title()).isEqualTo("Rent and bills");

        customer(A, "More words");
        assertThat(sessions.list(A).chats().get(0).title()).isEqualTo("Rent and bills");

        assertThat(sessions.rename(A, id, "  ").chats().get(0).title()).isEqualTo("Topic one");
    }

    @Test
    void aRenamedChatKeepsItsNameWhileSetAside() {
        customer(A, "Topic one");
        sessions.rename(A, sessions.list(A).activeId(), "Rent");
        sessions.create(A);

        assertThat(titles(sessions.list(A))).contains("Rent", "New chat");
    }

    @Test
    void aNameOverEightyCharactersIsRefused() {
        String id = sessions.list(A).activeId();

        ChatSessions.Failure e = failure(() -> sessions.rename(A, id, "x".repeat(81)));

        assertThat(e.status()).isEqualTo(400);
        assertThat(e).hasMessageContaining("80");
        sessions.rename(A, id, "x".repeat(80));
    }

    @Test
    void pinnedChatsComeFirstThenTheMostRecentlyUsed() throws Exception {
        customer(A, "Oldest");
        sessions.create(A);
        Thread.sleep(3);
        customer(A, "Middle");
        sessions.create(A);
        Thread.sleep(3);
        customer(A, "Newest");
        ChatSessions.Listing before = sessions.list(A);
        assertThat(titles(before)).isEqualTo(List.of("Newest", "Middle", "Oldest"));

        ChatSessions.Listing pinned = sessions.pin(A, chat(before, "Oldest").id(), true);

        assertThat(titles(pinned)).isEqualTo(List.of("Oldest", "Newest", "Middle"));
        assertThat(pinned.chats().get(0).pinned()).isTrue();
        assertThat(titles(sessions.pin(A, chat(pinned, "Oldest").id(), false))).isEqualTo(List.of("Newest", "Middle", "Oldest"));
    }

    @Test
    void aPinnedEmptyChatIsKeptWhenYouLeaveIt() {
        customer(A, "Topic one");
        String one = sessions.list(A).activeId();
        sessions.create(A);
        sessions.pin(A, sessions.list(A).activeId(), true);

        ChatSessions.Listing opened = sessions.open(A, one);

        assertThat(opened.chats()).hasSize(2);
    }

    @Test
    void deletingAnotherChatRemovesItAndItsSavedCopy() {
        customer(A, "Topic one");
        ChatSessions.Summary one = chat(sessions.list(A), "Topic one");
        sessions.create(A);
        assertThat(state.get("chat:saved:" + A + ":" + one.id()).isPresent()).isTrue();

        ChatSessions.Listing after = sessions.delete(A, one.id());

        assertThat(after.chats()).hasSize(1);
        assertThat(state.get("chat:saved:" + A + ":" + one.id()).isPresent()).isFalse();
    }

    @Test
    void deletingTheCurrentChatEmptiesItAndLeavesANewOne() {
        customer(A, "Topic one");
        sessions.create(A);
        customer(A, "Topic two");
        assistant(A, "Answer two");
        ChatSessions.Summary two = chat(sessions.list(A), "Topic two");

        ChatSessions.Listing after = sessions.delete(A, two.id());

        assertThat(after.chats()).hasSize(2);
        assertThat(titles(after)).contains("Topic one", "New chat");
        assertThat(after.activeId().equals(two.id())).isFalse();
        assertThat(log.after(A, 0, 100)).isEmpty();
        assertThat(state.readList("chat:history:" + A)).isEmpty();
    }

    @Test
    void deletingTheOnlyChatLeavesOneEmptyChat() {
        customer(A, "Topic one");
        String id = sessions.list(A).activeId();

        ChatSessions.Listing after = sessions.delete(A, id);

        assertThat(after.chats()).hasSize(1);
        assertThat(after.chats().get(0).title()).isEqualTo("New chat");
        assertThat(after.activeId().equals(id)).isFalse();
    }

    @Test
    void deletingEverythingRemovesPinnedChatsToo() {
        customer(A, "Topic one");
        sessions.pin(A, sessions.list(A).activeId(), true);
        sessions.create(A);
        customer(A, "Topic two");
        ChatSessions.Summary one = chat(sessions.list(A), "Topic one");

        ChatSessions.Listing after = sessions.deleteAll(A);

        assertThat(after.chats()).hasSize(1);
        assertThat(after.chats().get(0).title()).isEqualTo("New chat");
        assertThat(log.after(A, 0, 100)).isEmpty();
        assertThat(state.get("chat:saved:" + A + ":" + one.id()).isPresent()).isFalse();
    }

    @Test
    void switchingIsRefusedWhileTheAssistantIsReplying() {
        customer(A, "Topic one");
        sessions.create(A);
        customer(A, "Topic two");
        String one = chat(sessions.list(A), "Topic one").id();
        String two = sessions.list(A).activeId();
        log.typing(A, true);

        assertThat(failure(() -> sessions.create(A)).status()).isEqualTo(409);
        assertThat(failure(() -> sessions.open(A, one)).status()).isEqualTo(409);
        assertThat(failure(() -> sessions.delete(A, two)).status()).isEqualTo(409);
        assertThat(failure(() -> sessions.deleteAll(A)).status()).isEqualTo(409);

        // Naming, pinning and deleting a saved chat do not touch the live one
        sessions.rename(A, one, "Still fine");
        sessions.pin(A, one, true);
        sessions.delete(A, one);
        log.typing(A, false);
        assertThat(sessions.open(A, sessions.list(A).activeId()).chats()).hasSize(1);
    }

    @Test
    void thereIsALimitOnHowManyChatsOneCustomerCanHave() {
        for (int i = 0; i < ChatSessions.MAX_CHATS - 1; i++) {
            customer(A, "Chat " + i);
            sessions.create(A);
        }
        assertThat(sessions.list(A).chats()).hasSize(ChatSessions.MAX_CHATS);
        customer(A, "One more");

        ChatSessions.Failure e = failure(() -> sessions.create(A));

        assertThat(e.status()).isEqualTo(409);
        assertThat(e).hasMessageContaining("Delete one");
    }

    @Test
    void clearingTheChatTheOldWayResetsItsName() {
        customer(A, "Topic one");
        assertThat(sessions.list(A).chats().get(0).title()).isEqualTo("Topic one");

        log.clear(A);

        ChatSessions.Summary only = sessions.list(A).chats().get(0);
        assertThat(only.title()).isEqualTo("New chat");
        assertThat(only.messages()).isEqualTo(0);
    }

    @Test
    void eachCustomerHasTheirOwnChats() {
        customer(A, "Mine");
        customer(B, "Theirs");
        sessions.create(A);

        assertThat(sessions.list(A).chats()).hasSize(2);
        assertThat(sessions.list(B).chats()).hasSize(1);
        assertThat(sessions.list(B).chats().get(0).title()).isEqualTo("Theirs");
        assertThat(liveTexts(B)).isEqualTo(List.of("Theirs"));
    }

    @Test
    void anUnreadableChatListStartsAgainWithoutFailing() {
        state.set("chat:sessions:" + A, "not json", java.time.Duration.ofHours(1));
        customer(A, "Topic one");

        ChatSessions.Listing listing = sessions.list(A);

        assertThat(listing.chats()).hasSize(1);
        assertThat(listing.chats().get(0).title()).isEqualTo("Topic one");
    }
}