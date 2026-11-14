package com.darajaagentx.api.devtunnel;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TunnelOutputTest {

    @Test
    void zrokAddressIsFoundAmongItsOwnLogLines() {
        String output = "[ INFO] connecting to https://api-v2.zrok.io\n"
                + "[ INFO] see https://docs.zrok.io for help\n"
                + "[ INFO] access your zrok share at the following endpoints:\n"
                + "https://abc123xyz.share.zrok.io\n";
        assertThat(TunnelOutput.zrok(output)).contains("https://abc123xyz.share.zrok.io");
    }

    @Test
    void colourCodesDoNotGetInTheWay() {
        assertThat(TunnelOutput.zrok("\u001B[32mhttps://abc.share.zrok.io\u001B[0m")).contains("https://abc.share.zrok.io");
    }

    @Test
    void ownServicesAndEmptyOutputGiveNothing() {
        assertThat(TunnelOutput.zrok("connecting to https://api-v2.zrok.io and https://docs.zrok.io")).isEmpty();
        assertThat(TunnelOutput.zrok("")).isEmpty();
        assertThat(TunnelOutput.zrok(null)).isEmpty();
    }

    @Test
    void ngrokAddressIsTheOneForThisPort() {
        String json = "{\"tunnels\":[{\"name\":\"a\",\"public_url\":\"https://other.ngrok-free.app\",\"proto\":\"https\",\"config\":{\"addr\":\"http://localhost:3000\"}},"
                + "{\"name\":\"b\",\"public_url\":\"https://mine.ngrok-free.app\",\"proto\":\"https\",\"config\":{\"addr\":\"http://localhost:8084\"}}],\"uri\":\"/api/tunnels\"}";
        assertThat(TunnelOutput.ngrok(json, 8084)).contains("https://mine.ngrok-free.app");
        assertThat(TunnelOutput.ngrok(json, 3000)).contains("https://other.ngrok-free.app");
        assertThat(TunnelOutput.ngrok(json, 9999)).isEmpty();
    }

    @Test
    void ngrokWithNoAnswerGivesNothing() {
        assertThat(TunnelOutput.ngrok(null, 8084)).isEmpty();
        assertThat(TunnelOutput.ngrok("{\"tunnels\":[]}", 8084)).isEmpty();
    }
}
