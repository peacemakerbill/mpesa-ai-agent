package com.darajaagentx.agent.controller;

import com.darajaagentx.agent.TestSupport;
import com.darajaagentx.agent.chat.ChatLog;
import com.darajaagentx.agent.chat.ChatMessage;
import com.darajaagentx.agent.chat.ChatSessions;
import com.darajaagentx.agent.store.InMemoryStateStore;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChatSessionsControllerTest {

    private static final String PHONE = "254700000001";

    private final InMemoryStateStore state = new InMemoryStateStore();
    private final ChatLog log = new ChatLog(state, TestSupport.mapper());
    private final ChatSessionsController controller = new ChatSessionsController(new ChatSessions(state, TestSupport.mapper(), log));

    private void customerSays(String text) {
        ChatMessage m = new ChatMessage();
        m.dir = "in";
        m.kind = "TEXT";
        m.text = text;
        log.append(PHONE, m);
    }

    private static ChatSessions.Listing listing(ResponseEntity<?> response) {
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return (ChatSessions.Listing) response.getBody();
    }

    @Test
    void everyCallAnswersWithTheUpdatedList() {
        customerSays("Topic one");

        ChatSessions.Listing created = listing(controller.create(PHONE));
        assertThat(created.chats()).hasSize(2);

        String one = created.chats().stream().filter(c -> c.title().equals("Topic one")).findFirst().orElseThrow().id();
        assertThat(listing(controller.rename(PHONE, one, new ChatSessionsController.TitleRequest("Rent"))).chats().size()).isEqualTo(2);
        assertThat(listing(controller.pin(PHONE, one, new ChatSessionsController.PinRequest(true))).chats().get(0).pinned()).isTrue();
        assertThat(listing(controller.open(PHONE, one)).activeId()).isEqualTo(one);
        assertThat(listing(controller.list(PHONE)).chats()).hasSize(1);
        assertThat(listing(controller.delete(PHONE, one)).chats()).hasSize(1);
        assertThat(listing(controller.deleteAll(PHONE)).chats()).hasSize(1);
    }

    @Test
    void refusalsBecomeAProblemWithTheStatusAndAMessage() {
        ResponseEntity<?> missing = controller.open(PHONE, "nope");

        assertThat(missing.getStatusCode().value()).isEqualTo(404);
        Map<?, ?> body = (Map<?, ?>) missing.getBody();
        assertThat(body.get("status")).isEqualTo(404);
        assertThat(body.get("message")).isEqualTo("That chat no longer exists.");
    }

    @Test
    void aBusyAssistantAnswersWithConflict() {
        customerSays("Topic one");
        log.typing(PHONE, true);

        assertThat(controller.create(PHONE).getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void pinningNeedsAnAnswerAndNamesAreChecked() {
        String id = listing(controller.list(PHONE)).activeId();

        assertThat(controller.pin(PHONE, id, new ChatSessionsController.PinRequest(null)).getStatusCode().value()).isEqualTo(400);
        assertThat(controller.pin(PHONE, id, null).getStatusCode().value()).isEqualTo(400);
        assertThat(controller.rename(PHONE, id, new ChatSessionsController.TitleRequest("x".repeat(90))).getStatusCode().value())
                .isEqualTo(400);
    }

    @Test
    void anInvalidNumberIsRefused() {
        assertThat(controller.list("abc").getStatusCode().value()).isEqualTo(400);
    }
}