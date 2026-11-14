package com.darajaagentx.api.devtunnel;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformTest {

    @Test
    void knowsLinuxMacAndWindows() {
        assertThat(Platform.of("Linux", "amd64")).isEqualTo(new Platform("linux", "amd64"));
        assertThat(Platform.of("Linux", "aarch64")).isEqualTo(new Platform("linux", "arm64"));
        assertThat(Platform.of("Mac OS X", "aarch64")).isEqualTo(new Platform("darwin", "arm64"));
        assertThat(Platform.of("Mac OS X", "x86_64")).isEqualTo(new Platform("darwin", "amd64"));
        assertThat(Platform.of("Darwin", "x86_64")).isEqualTo(new Platform("darwin", "amd64"));
        assertThat(Platform.of("Windows 11", "amd64")).isEqualTo(new Platform("windows", "amd64"));
        assertThat(Platform.of("Windows 10", "x86")).isEqualTo(new Platform("windows", "386"));
        assertThat(Platform.of("Linux", "arm")).isEqualTo(new Platform("linux", "arm"));
    }

    @Test
    void programsGetTheirWindowsSuffix() {
        assertThat(new Platform("windows", "amd64").exe("zrok2")).isEqualTo("zrok2.exe");
        assertThat(new Platform("linux", "amd64").exe("zrok2")).isEqualTo("zrok2");
        assertThat(new Platform("darwin", "arm64").exe("ngrok")).isEqualTo("ngrok");
    }
}
