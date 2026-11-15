package com.darajaagentx.api.service;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CallbackUrlsTest {

    private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test
    void blankUsesTheFallbackAddressWithThePathAndToken() {
        assertThat(CallbackUrls.resolve("", "https://api.example.com", ID, "tok", "result"))
                .isEqualTo("https://api.example.com/results/" + ID + "/tok");
        assertThat(CallbackUrls.resolve(null, "https://api.example.com", ID, "tok", "timeout"))
                .isEqualTo("https://api.example.com/timeout/" + ID + "/tok");
    }

    @Test
    void anAddressGetsThePathAndTokenAndLosesItsClosingSlash() {
        assertThat(CallbackUrls.resolve(" https://hooks.example.org// ", "https://x.example.com", ID, "tok", "result"))
                .isEqualTo("https://hooks.example.org/results/" + ID + "/tok");
    }

    @Test
    void aTemplateIsUsedAsWrittenWithTheValuesFilledIn() {
        assertThat(CallbackUrls.resolve("https://h.example.org/s/{id}/{token}/{kind}", "https://x.example.com", ID, "tok", "timeout"))
                .isEqualTo("https://h.example.org/s/" + ID + "/tok/timeout");
        assertThat(CallbackUrls.resolve("https://h.example.org/{kind}/{id}/{token}", "https://x.example.com", ID, "tok", "result"))
                .isEqualTo("https://h.example.org/results/" + ID + "/tok");
    }

    @Test
    void theShortWordsAreFixedAndCarryNoBannedKeywords() {
        assertThat(CallbackUrls.word("callback")).isEqualTo("stk-callback");
        assertThat(CallbackUrls.word("result")).isEqualTo("results");
        assertThat(CallbackUrls.word("timeout")).isEqualTo("timeout");
        assertThat(CallbackUrls.resolve("", "https://api.example.com", ID, "tok", "callback"))
                .isEqualTo("https://api.example.com/stk-callback/" + ID + "/tok");
        for (String kind : new String[] {"callback", "result", "timeout"}) {
            assertThat(CallbackUrls.resolve("", "https://api.example.com", ID, "tok", kind).toLowerCase())
                    .doesNotContain("mpesa").doesNotContain("m-pesa").doesNotContain("safaricom");
        }
    }

    @Test
    void problemsAreNamedAndUsableValuesPass() {
        assertThat(CallbackUrls.problem("")).isNull();
        assertThat(CallbackUrls.problem("https://hooks.example.org")).isNull();
        assertThat(CallbackUrls.problem("https://hooks.example.org/{id}/{token}/result")).isNull();
        assertThat(CallbackUrls.problem("https://hooks.example.org/{id}/result")).contains("both {id} and {token}");
        assertThat(CallbackUrls.problem("http://localhost:8084")).contains("cannot reach");
        assertThat(CallbackUrls.problem("http://hooks.example.org")).contains("https");
    }
}
