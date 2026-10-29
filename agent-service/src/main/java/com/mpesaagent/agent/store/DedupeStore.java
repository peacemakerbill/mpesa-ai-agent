package com.mpesaagent.agent.store;

import org.springframework.stereotype.Component;

import java.time.Duration;

/** An app may send the same message twice when its connection drops and it retries. Each message id is handled once. */
@Component
public class DedupeStore {

    private final StateStore store;

    public DedupeStore(StateStore store) {
        this.store = store;
    }

    /** True the first time an id is seen, false after that. */
    public boolean firstTime(String key) {
        return store.setIfAbsent("seen:" + key, "1", Duration.ofHours(48));
    }

    /** Lets a message be accepted again, used when we could not even queue it. */
    public void forget(String key) {
        store.delete("seen:" + key);
    }
}
