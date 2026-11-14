package com.darajaagentx.api.devtunnel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DevTunnelTest {

    /** Pretends to be the operating system: remembers what was started and writes the output a program would print. */
    static class FakeLauncher implements Launcher {
        final List<List<String>> started = new ArrayList<>();
        final List<List<String>> ranToEnd = new ArrayList<>();
        final List<Map<String, String>> environments = new ArrayList<>();
        String output = "";
        boolean dies = false;
        int enableExit = 0;
        Path home;
        boolean stopped = false;
        Runnable onStart = () -> { };

        @Override
        public Running start(List<String> command, Map<String, String> env, Path log) throws IOException {
            started.add(command);
            environments.add(env);
            Files.writeString(log, output);
            onStart.run();
            return new Running() {
                @Override
                public boolean alive() {
                    return !dies && !stopped;
                }

                @Override
                public void stop() {
                    stopped = true;
                }
            };
        }

        @Override
        public int runToEnd(List<String> command, Map<String, String> env, Path log, Duration timeout) throws IOException {
            ranToEnd.add(command);
            Files.writeString(log, enableExit == 0 ? "ok" : "bad token");
            if (enableExit == 0) {
                Files.createDirectories(home.resolve(".zrok2"));
                Files.writeString(home.resolve(".zrok2").resolve("environment.json"), "{}");
            }
            return enableExit;
        }
    }

    private static final String ZROK_OUTPUT = "[ INFO] access your zrok share at the following endpoints:\nhttps://abc123xyz.share.zrok.io\n";
    private static final String NGROK_JSON = "{\"tunnels\":[{\"name\":\"command_line\",\"public_url\":\"https://fake-1234.ngrok-free.app\",\"proto\":\"https\","
            + "\"config\":{\"addr\":\"http://localhost:8084\"}}]}";

    private FakeLauncher launcher;
    private Path home;
    private Path tools;
    private Path state;

    private DevTunnel tunnel(Path dir, DevTunnelProperties props, Platform platform, ToolInstaller.Downloader downloader, DevTunnel.Reader reader) {
        home = dir.resolve("home");
        tools = dir.resolve("tools");
        state = dir.resolve("state");
        launcher = new FakeLauncher();
        launcher.home = home;
        ToolLocator locator = new ToolLocator(platform, List.of(dir.resolve("onpath"), tools));
        return new DevTunnel(props, platform, locator, launcher, new ToolInstaller(platform, props, downloader), reader, home, tools, state);
    }

    private static DevTunnelProperties props(boolean install, String zrokToken, String ngrokToken) {
        return new DevTunnelProperties("auto", "auto", install, 1, zrokToken, ngrokToken, "v2.0.0", "", "", "", "", "http://127.0.0.1:4040");
    }

    private static void putProgram(Path folder, String file) throws IOException {
        Files.createDirectories(folder);
        Path p = folder.resolve(file);
        Files.writeString(p, "x");
        p.toFile().setExecutable(true);
    }

    private static final Platform LINUX = new Platform("linux", "amd64");

    @Test
    void zrokIsEnabledWithTheTokenThenSharesThePortAndReturnsItsAddress(@TempDir Path dir) throws IOException {
        DevTunnel t = tunnel(dir, props(true, "tok123", ""), LINUX, url -> new byte[0], address -> null);
        putProgram(dir.resolve("onpath"), "zrok2");
        launcher.output = ZROK_OUTPUT;

        DevTunnel.Open open = t.open(Provider.ZROK, 8084);

        assertThat(open.url()).isEqualTo("https://abc123xyz.share.zrok.io");
        assertThat(launcher.ranToEnd).hasSize(1);
        assertThat(launcher.ranToEnd.get(0).subList(1, 3)).containsExactly("enable", "tok123");
        assertThat(launcher.started.get(0).subList(1, launcher.started.get(0).size())).containsExactly("share", "public", "--headless", "8084");
    }

    @Test
    void anAlreadyEnabledZrokIsNotEnabledAgain(@TempDir Path dir) throws IOException {
        DevTunnel t = tunnel(dir, props(true, "", ""), LINUX, url -> new byte[0], address -> null);
        putProgram(dir.resolve("onpath"), "zrok2");
        Files.createDirectories(home.resolve(".zrok2"));
        Files.writeString(home.resolve(".zrok2").resolve("environment.json"), "{}");
        launcher.output = ZROK_OUTPUT;

        assertThat(t.open(Provider.ZROK, 8084).url()).isEqualTo("https://abc123xyz.share.zrok.io");
        assertThat(launcher.ranToEnd).isEmpty();
        assertThat(t.ready(Provider.ZROK)).isTrue();
    }

    @Test
    void extraShareArgumentsAreAddedBeforeThePort(@TempDir Path dir) throws IOException {
        DevTunnelProperties p = new DevTunnelProperties("auto", "auto", true, 1, "", "", "", "--open", "", "", "", "http://127.0.0.1:4040");
        DevTunnel t = tunnel(dir, p, LINUX, url -> new byte[0], address -> null);
        putProgram(dir.resolve("onpath"), "zrok2");
        Files.createDirectories(home.resolve(".zrok2"));
        Files.writeString(home.resolve(".zrok2").resolve("environment.json"), "{}");
        launcher.output = ZROK_OUTPUT;
        t.open(Provider.ZROK, 9090);
        List<String> command = launcher.started.get(0);
        assertThat(command.subList(command.size() - 2, command.size())).containsExactly("--open", "9090");
    }

    @Test
    void aMissingZrokIsDownloadedIntoTheToolFolderAndUsed(@TempDir Path dir) throws IOException {
        byte[] archive = ArchivesTest.tarGz("zrok2", "program");
        DevTunnel t = tunnel(dir, props(true, "tok", ""), LINUX, url -> archive, address -> null);
        launcher.output = ZROK_OUTPUT;

        assertThat(t.installed(Provider.ZROK)).isFalse();
        assertThat(t.open(Provider.ZROK, 8084).url()).isEqualTo("https://abc123xyz.share.zrok.io");
        assertThat(Files.isRegularFile(tools.resolve("zrok2"))).isTrue();
        assertThat(launcher.started.get(0).get(0)).isEqualTo(tools.resolve("zrok2").toString());
        assertThat(t.installed(Provider.ZROK)).isTrue();
    }

    @Test
    void onWindowsTheProgramHasTheExeSuffix(@TempDir Path dir) throws IOException {
        Platform windows = new Platform("windows", "amd64");
        byte[] archive = ArchivesTest.tarGz("zrok2.exe", "program");
        DevTunnel t = tunnel(dir, props(true, "tok", ""), windows, url -> archive, address -> null);
        launcher.output = ZROK_OUTPUT;
        t.open(Provider.ZROK, 8084);
        assertThat(launcher.started.get(0).get(0)).isEqualTo(tools.resolve("zrok2.exe").toString());
    }

    @Test
    void switchingInstallOffMeansNothingIsDownloaded(@TempDir Path dir) {
        DevTunnel t = tunnel(dir, props(false, "tok", ""), LINUX, url -> {
            throw new IOException("must not be called");
        }, address -> null);
        assertThatThrownBy(() -> t.open(Provider.ZROK, 8084)).isInstanceOf(IOException.class).hasMessageContaining("DEV_TUNNEL_INSTALL is off");
    }

    @Test
    void aFailedDownloadTellsThePersonHowToInstallItThemselves(@TempDir Path dir) {
        DevTunnel t = tunnel(dir, props(true, "tok", ""), LINUX, url -> {
            throw new IOException("offline");
        }, address -> null);
        assertThatThrownBy(() -> t.open(Provider.ZROK, 8084)).isInstanceOf(IOException.class)
                .hasMessageContaining("offline").hasMessageContaining("get.openziti.io");
    }

    @Test
    void zrokWithoutATokenOrAnEnabledMachineExplains(@TempDir Path dir) throws IOException {
        DevTunnel t = tunnel(dir, props(true, "", ""), LINUX, url -> new byte[0], address -> null);
        putProgram(dir.resolve("onpath"), "zrok2");
        assertThatThrownBy(() -> t.open(Provider.ZROK, 8084)).isInstanceOf(IOException.class).hasMessageContaining("ZROK_ENABLE_TOKEN");
        assertThat(launcher.started).isEmpty();
    }

    @Test
    void aFailedEnableIsReportedWithZroksOwnWords(@TempDir Path dir) throws IOException {
        DevTunnel t = tunnel(dir, props(true, "bad", ""), LINUX, url -> new byte[0], address -> null);
        putProgram(dir.resolve("onpath"), "zrok2");
        launcher.enableExit = 1;
        assertThatThrownBy(() -> t.open(Provider.ZROK, 8084)).isInstanceOf(IOException.class).hasMessageContaining("bad token");
    }

    @Test
    void aProgramThatStopsEarlyShowsWhatItSaid(@TempDir Path dir) throws IOException {
        DevTunnel t = tunnel(dir, props(true, "tok", ""), LINUX, url -> new byte[0], address -> null);
        putProgram(dir.resolve("onpath"), "zrok2");
        launcher.output = "error: invalid environment";
        launcher.dies = true;
        assertThatThrownBy(() -> t.open(Provider.ZROK, 8084)).isInstanceOf(IOException.class)
                .hasMessageContaining("stopped before it showed an address").hasMessageContaining("invalid environment");
    }

    @Test
    void aProgramThatNeverShowsAnAddressIsStoppedAfterTheWait(@TempDir Path dir) throws IOException {
        DevTunnel t = tunnel(dir, props(true, "tok", ""), LINUX, url -> new byte[0], address -> null);
        putProgram(dir.resolve("onpath"), "zrok2");
        launcher.output = "starting";
        assertThatThrownBy(() -> t.open(Provider.ZROK, 8084)).isInstanceOf(IOException.class).hasMessageContaining("showed no address within 1 seconds");
        assertThat(launcher.stopped).isTrue();
    }

    @Test
    void anAlreadyRunningNgrokTunnelForThisPortIsReused(@TempDir Path dir) throws IOException {
        DevTunnel t = tunnel(dir, props(true, "", ""), LINUX, url -> new byte[0], address -> address.endsWith("/api/tunnels") ? NGROK_JSON : null);
        DevTunnel.Open open = t.open(Provider.NGROK, 8084);
        assertThat(open.url()).isEqualTo("https://fake-1234.ngrok-free.app");
        assertThat(open.running()).isNull();
        assertThat(launcher.started).isEmpty();
    }

    @Test
    void ngrokIsStartedWithTheTokenInItsEnvironmentAndItsLocalApiGivesTheAddress(@TempDir Path dir) throws IOException {
        AtomicBoolean running = new AtomicBoolean(false);
        DevTunnel t = tunnel(dir, props(true, "", "ng-token"), LINUX, url -> new byte[0], address -> running.get() ? NGROK_JSON : null);
        putProgram(dir.resolve("onpath"), "ngrok");
        launcher.onStart = () -> running.set(true);

        DevTunnel.Open open = t.open(Provider.NGROK, 8084);

        assertThat(open.url()).isEqualTo("https://fake-1234.ngrok-free.app");
        List<String> command = launcher.started.get(0);
        assertThat(command.subList(1, command.size())).containsExactly("http", "8084", "--log=stdout");
        assertThat(launcher.environments.get(0).get("NGROK_AUTHTOKEN")).isEqualTo("ng-token");
    }

    @Test
    void ngrokWithoutAnyTokenExplains(@TempDir Path dir) throws IOException {
        DevTunnel t = tunnel(dir, props(true, "", ""), LINUX, url -> new byte[0], address -> null);
        putProgram(dir.resolve("onpath"), "ngrok");
        assertThatThrownBy(() -> t.open(Provider.NGROK, 8084)).isInstanceOf(IOException.class).hasMessageContaining("NGROK_AUTHTOKEN");
    }

    @Test
    void anNgrokConfigFileWithAnAuthtokenCountsAsReady(@TempDir Path dir) throws IOException {
        DevTunnel t = tunnel(dir, props(true, "", ""), LINUX, url -> new byte[0], address -> null);
        assertThat(t.ready(Provider.NGROK)).isFalse();
        Files.createDirectories(home.resolve(".config/ngrok"));
        Files.writeString(home.resolve(".config/ngrok/ngrok.yml"), "version: 3\nagent:\n  authtoken: abc\n");
        assertThat(t.ready(Provider.NGROK)).isTrue();
    }
}
