package com.darajaagentx.api.service;

import com.darajaagentx.api.controller.DarajaPingController;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StkCallbackTest {

    @Test
    void anAddressIsCleanedUp() {
        assertThat(StkCallback.normalize("  https://a.example.com/// ")).isEqualTo("https://a.example.com");
        assertThat(StkCallback.normalize(null)).isEmpty();
        assertThat(StkCallback.normalize("   ")).isEmpty();
    }

    @Test
    void onlyAPublicHttpsAddressLooksUsable() {
        assertThat(StkCallback.problem("https://abc-123.ngrok-free.app")).isNull();
        assertThat(StkCallback.problem("https://agentx.example.com/api")).isNull();
        assertThat(StkCallback.problem("http://agentx.example.com")).contains("https");
        assertThat(StkCallback.problem("http://localhost:8084")).contains("cannot reach localhost");
        assertThat(StkCallback.problem("https://127.0.0.1:8084")).contains("cannot reach");
        assertThat(StkCallback.problem("http://192.168.1.20:8084")).contains("cannot reach");
        assertThat(StkCallback.problem("")).contains("no address");
        assertThat(StkCallback.problem("not a url")).isNotNull();
        assertThat(StkCallback.problem("ftp://files.example.com")).contains("not a web address");
    }

    @Test
    void knownCodesAreExplainedAndUnknownOnesKeepSafaricomsWords() {
        assertThat(StkCallback.failureText("1032", "Request cancelled by user")).isEqualTo("The customer cancelled the request on their phone. (code 1032)");
        assertThat(StkCallback.failureText("2001", "x")).contains("wrong PIN");
        assertThat(StkCallback.failureText("1", "x")).contains("balance");
        assertThat(StkCallback.failureText("1037", null)).contains("did not answer");
        assertThat(StkCallback.failureText("4242", "Something new")).isEqualTo("Something new. (code 4242)");
        assertThat(StkCallback.failureText("4242", " ")).isEqualTo("The customer did not complete the payment. (code 4242)");
        assertThat(StkCallback.failureText(null, null)).isEqualTo("The customer did not complete the payment.");
    }

    @Test
    void thePingAnswersWithoutShowingAnythingPrivate() {
        assertThat(new DarajaPingController().ping()).containsEntry("status", "ok").hasSize(2);
    }
}
