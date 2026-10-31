package com.mganji.agent.store;

import com.mganji.agent.config.AgentProperties;
import com.mganji.agent.model.ChatTurn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Short term memory of each chat for the model (text only, newest 20 turns, 24 hours). The full chat the customer
 * sees is kept separately in ChatLog.
 */
@Component
public class ConversationStore {

    private static final Logger log = LoggerFactory.getLogger(ConversationStore.class);

    private final StateStore store;
    private final ObjectMapper mapper;
    private final AgentProperties props;

    public ConversationStore(StateStore store, ObjectMapper mapper, AgentProperties props) {
        this.store = store;
        this.mapper = mapper;
        this.props = props;
    }

    public void append(String phone, String role, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        try {
            store.appendToList("chat:history:" + phone, mapper.writeValueAsString(new ChatTurn(role, text)),
                    props.historySize(), props.historyTtl());
        } catch (JacksonException e) {
            log.warn("Could not remember a chat turn", e);
        }
    }

    /**
     * The remembered turns in the shape the model API needs: it must start with a user turn and the roles
     * must alternate, so leading assistant turns are dropped and neighbours of the same role are merged.
     */
    public List<ChatTurn> history(String phone) {
        List<ChatTurn> raw = new ArrayList<>();
        for (String json : store.readList("chat:history:" + phone)) {
            try {
                raw.add(mapper.readValue(json, ChatTurn.class));
            } catch (JacksonException e) {
                log.warn("Skipping an unreadable chat turn");
            }
        }
        List<ChatTurn> result = new ArrayList<>();
        for (ChatTurn turn : raw) {
            if (result.isEmpty() && !"user".equals(turn.role())) {
                continue;
            }
            if (!result.isEmpty() && result.get(result.size() - 1).role().equals(turn.role())) {
                ChatTurn last = result.remove(result.size() - 1);
                result.add(new ChatTurn(last.role(), last.text() + "\n\n" + turn.text()));
            } else {
                result.add(turn);
            }
        }
        return result;
    }

    /** Forgets what was said, used when the customer starts a new chat. */
    public void clear(String phone) {
        store.delete("chat:history:" + phone);
    }
}