package com.darajaagentx.api.controller;

import com.darajaagentx.api.storage.DatabaseFileStore;
import com.darajaagentx.api.storage.FileStore;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Chat files kept through agentx-api, with files stored in the database. This also proves the stored_files migration matches the
 * entity: the test database checks the tables against the entities when it starts.
 */
@SpringBootTest(properties = {"agentx.storage.type=database", "agentx.storage.database.max-file-bytes=1000"})
@AutoConfigureMockMvc
class InternalFilesIntegrationTest {

    private static final String KEY = "test-internal-key";
    private static final String ID = "6f1c2b9e-0000-4000-8000-0000000000a1";

    @Autowired MockMvc mvc;
    @Autowired FileStore store;
    @MockitoBean RabbitTemplate rabbit;

    @Test
    void theDatabaseIsTheStoreWhenSoConfigured() {
        assertThat(store).isInstanceOf(DatabaseFileStore.class);
        assertThat(store.healthy()).isTrue();
    }

    @Test
    void aFileAndItsMetaCanBeSavedReadAndRemoved() throws Exception {
        byte[] bytes = {1, 2, 3, (byte) 250};
        mvc.perform(put("/internal/files/chat-media/" + ID).header("X-Internal-Key", KEY)
                        .contentType(MediaType.APPLICATION_OCTET_STREAM).content(bytes))
                .andExpect(status().isNoContent());
        mvc.perform(put("/internal/files/chat-media/" + ID + ".meta?keepSeconds=3600").header("X-Internal-Key", KEY)
                        .contentType(MediaType.APPLICATION_OCTET_STREAM).content("{\"phone\":\"254700000001\"}".getBytes()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/internal/files/chat-media/" + ID).header("X-Internal-Key", KEY))
                .andExpect(status().isOk()).andExpect(content().bytes(bytes));
        mvc.perform(get("/internal/files/chat-media/" + ID + ".meta").header("X-Internal-Key", KEY))
                .andExpect(status().isOk()).andExpect(content().string("{\"phone\":\"254700000001\"}"));

        mvc.perform(delete("/internal/files/chat-media/" + ID).header("X-Internal-Key", KEY)).andExpect(status().isNoContent());
        mvc.perform(delete("/internal/files/chat-media/" + ID + ".meta").header("X-Internal-Key", KEY)).andExpect(status().isNoContent());
        mvc.perform(get("/internal/files/chat-media/" + ID).header("X-Internal-Key", KEY)).andExpect(status().isNotFound());
        mvc.perform(delete("/internal/files/chat-media/" + ID).header("X-Internal-Key", KEY)).andExpect(status().isNoContent());
    }

    @Test
    void aFileOverTheLimitIsRefused() throws Exception {
        mvc.perform(put("/internal/files/chat-media/6f1c2b9e-0000-4000-8000-0000000000a2").header("X-Internal-Key", KEY)
                        .contentType(MediaType.APPLICATION_OCTET_STREAM).content(new byte[2000]))
                .andExpect(status().is(413));
    }

    @Test
    void onlyOurOwnIdsAreAccepted() throws Exception {
        mvc.perform(put("/internal/files/chat-media/not-an-id").header("X-Internal-Key", KEY)
                .contentType(MediaType.APPLICATION_OCTET_STREAM).content(new byte[]{1})).andExpect(status().isBadRequest());
        mvc.perform(get("/internal/files/chat-media/not-an-id").header("X-Internal-Key", KEY)).andExpect(status().isNotFound());
        mvc.perform(delete("/internal/files/chat-media/..%2Fsecret").header("X-Internal-Key", KEY)).andExpect(status().is4xxClientError());
    }

    @Test
    void theInternalKeyIsRequired() throws Exception {
        mvc.perform(get("/internal/files/chat-media/" + ID)).andExpect(status().isUnauthorized());
        mvc.perform(put("/internal/files/chat-media/" + ID).contentType(MediaType.APPLICATION_OCTET_STREAM).content(new byte[]{1}))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void filesWithAKeepTimeAreRemovedOnceItHasPassed() throws Exception {
        store.put("chat-media/6f1c2b9e-0000-4000-8000-0000000000a3", new byte[]{7}, Duration.ofMillis(1));
        Thread.sleep(50);
        assertThat(store.get("chat-media/6f1c2b9e-0000-4000-8000-0000000000a3")).isEmpty();
        assertThat(((DatabaseFileStore) store).purgeExpired()).isGreaterThanOrEqualTo(1);
    }
}
