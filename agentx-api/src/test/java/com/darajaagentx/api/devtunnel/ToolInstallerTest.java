package com.darajaagentx.api.devtunnel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolInstallerTest {

    static DevTunnelProperties props(String zrokVersion, String zrokUrl, String ngrokUrl) {
        return new DevTunnelProperties("auto", "auto", true, 1, "", "", zrokVersion, "", zrokUrl, ngrokUrl, "", "http://127.0.0.1:4040");
    }

    @Test
    void zrokAddressIsBuiltFromTheVersionAndTheSystem() throws IOException {
        ToolInstaller linux = new ToolInstaller(new Platform("linux", "amd64"), props("v2.0.1", "", ""), url -> new byte[0]);
        assertThat(linux.zrokUrl()).isEqualTo("https://github.com/openziti/zrok/releases/download/v2.0.1/zrok2_2.0.1_linux_amd64.tar.gz");
        ToolInstaller mac = new ToolInstaller(new Platform("darwin", "arm64"), props("2.0.1", "", ""), url -> new byte[0]);
        assertThat(mac.zrokUrl()).isEqualTo("https://github.com/openziti/zrok/releases/download/v2.0.1/zrok2_2.0.1_darwin_arm64.tar.gz");
    }

    @Test
    void theLatestVersionTwoReleaseIsFoundAndCandidatesAreSkipped() throws IOException {
        String releases = "[{\"tag_name\":\"v2.1.0-rc1\"},{\"tag_name\":\"v1.9.9\"},{\"tag_name\":\"v2.0.4\"},{\"tag_name\":\"v2.0.3\"}]";
        ToolInstaller installer = new ToolInstaller(new Platform("linux", "amd64"), props("", "", ""), url -> releases.getBytes(StandardCharsets.UTF_8));
        assertThat(installer.zrokUrl()).contains("/v2.0.4/zrok2_2.0.4_linux_amd64.tar.gz");
    }

    @Test
    void noReleaseIsAClearError() {
        ToolInstaller installer = new ToolInstaller(new Platform("linux", "amd64"), props("", "", ""), url -> "[]".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(installer::zrokUrl).isInstanceOf(IOException.class).hasMessageContaining("ZROK2_VERSION");
    }

    @Test
    void ngrokUsesZipOnWindowsAndMacAndTgzOnLinux() {
        assertThat(new ToolInstaller(new Platform("linux", "amd64"), props("", "", ""), url -> null).ngrokUrl()).endsWith("ngrok-v3-stable-linux-amd64.tgz");
        assertThat(new ToolInstaller(new Platform("windows", "amd64"), props("", "", ""), url -> null).ngrokUrl()).endsWith("ngrok-v3-stable-windows-amd64.zip");
        assertThat(new ToolInstaller(new Platform("darwin", "arm64"), props("", "", ""), url -> null).ngrokUrl()).endsWith("ngrok-v3-stable-darwin-arm64.zip");
    }

    @Test
    void aGivenDownloadAddressWins() throws IOException {
        ToolInstaller installer = new ToolInstaller(new Platform("linux", "amd64"), props("", "https://mirror.example.org/z.tgz", "https://mirror.example.org/n.tgz"), url -> null);
        assertThat(installer.zrokUrl()).isEqualTo("https://mirror.example.org/z.tgz");
        assertThat(installer.ngrokUrl()).isEqualTo("https://mirror.example.org/n.tgz");
    }

    @Test
    void installsTheProgramIntoTheFolder(@TempDir Path folder) throws IOException {
        byte[] archive = ArchivesTest.tarGz("zrok2", "program");
        ToolInstaller installer = new ToolInstaller(new Platform("linux", "amd64"), props("v2.0.0", "", ""), url -> archive);
        Path installed = installer.install(Provider.ZROK, folder);
        assertThat(installed).isEqualTo(folder.resolve("zrok2"));
        assertThat(Files.readString(installed)).isEqualTo("program");
    }

    @Test
    void installsTheWindowsProgramWithItsSuffix(@TempDir Path folder) throws IOException {
        byte[] archive = ArchivesTest.zip("ngrok.exe", "program");
        ToolInstaller installer = new ToolInstaller(new Platform("windows", "amd64"), props("", "", ""), url -> archive);
        assertThat(installer.install(Provider.NGROK, folder)).isEqualTo(folder.resolve("ngrok.exe"));
    }

    @Test
    void aFailedDownloadSaysWhichAddress(@TempDir Path folder) {
        ToolInstaller installer = new ToolInstaller(new Platform("linux", "amd64"), props("v2.0.0", "", ""), url -> {
            throw new IOException("HTTP 404");
        });
        assertThatThrownBy(() -> installer.install(Provider.ZROK, folder)).isInstanceOf(IOException.class)
                .hasMessageContaining("zrok2_2.0.0_linux_amd64.tar.gz").hasMessageContaining("404");
    }

    @Test
    void aFileAddressWorksForTheRealDownloader(@TempDir Path folder) throws IOException {
        Path archive = folder.resolve("z.tar.gz");
        Files.write(archive, ArchivesTest.tarGz("zrok2", "program"));
        assertThat(java.util.Arrays.equals(ToolInstaller.fetch(archive.toUri().toString()), Files.readAllBytes(archive))).isTrue();
    }
}
