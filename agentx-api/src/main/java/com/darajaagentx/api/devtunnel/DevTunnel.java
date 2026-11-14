package com.darajaagentx.api.devtunnel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ArrayList;

/**
 * Opens a zrok2 or ngrok tunnel to this service, installing the program first when it is missing. Written to be used on Linux, macOS
 * and Windows alike: no shell scripts, no sudo, nothing outside the user's own folders.
 */
public final class DevTunnel {

    private static final Logger log = LoggerFactory.getLogger(DevTunnel.class);

    /** A tunnel that is open. [running] is null when an ngrok tunnel somebody else started was reused. */
    public record Open(String url, Launcher.Running running) {
    }

    /** Reads an address, for ngrok's local API. Returns null when nothing answers. */
    @FunctionalInterface
    public interface Reader {
        String read(String address);
    }

    private final DevTunnelProperties props;
    private final Platform platform;
    private final ToolLocator locator;
    private final Launcher launcher;
    private final ToolInstaller installer;
    private final Reader reader;
    private final Path home;
    private final Path installFolder;
    private final Path stateFolder;

    public DevTunnel(DevTunnelProperties props, Platform platform, ToolLocator locator, Launcher launcher, ToolInstaller installer,
                     Reader reader, Path home, Path installFolder, Path stateFolder) {
        this.props = props;
        this.platform = platform;
        this.locator = locator;
        this.launcher = launcher;
        this.installer = installer;
        this.reader = reader;
        this.home = home;
        this.installFolder = installFolder;
        this.stateFolder = stateFolder;
    }

    /** A reader that asks a local address over http and gives up quickly. */
    public static Reader httpReader() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        return address -> {
            try {
                HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create(address)).timeout(Duration.ofSeconds(2)).build(),
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                return response.statusCode() == 200 ? response.body() : null;
            } catch (IOException | RuntimeException e) {
                return null;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        };
    }

    // ------------------------------------------------------------------ facts for the planner

    public boolean installed(Provider provider) {
        return locator.find(provider.program()).isPresent();
    }

    public boolean ready(Provider provider) {
        return provider == Provider.ZROK ? zrokEnabled() || !props.zrokToken().isEmpty() : ngrokConfigured() || !props.ngrokToken().isEmpty();
    }

    boolean zrokEnabled() {
        return Files.isRegularFile(home.resolve(".zrok2").resolve("environment.json"));
    }

    boolean ngrokConfigured() {
        List<Path> files = new ArrayList<>(List.of(
                home.resolve(".config/ngrok/ngrok.yml"),
                home.resolve("Library/Application Support/ngrok/ngrok.yml"),
                home.resolve(".ngrok2/ngrok.yml"),
                home.resolve("AppData/Local/ngrok/ngrok.yml")));
        for (Path file : files) {
            try {
                if (Files.isRegularFile(file) && Files.readString(file).contains("authtoken")) {
                    return true;
                }
            } catch (IOException | RuntimeException ignored) {
                // an unreadable config is the same as none
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ opening

    /**
     * Opens a tunnel to the local port.
     *
     * @throws IOException with a sentence the person can act on
     */
    public Open open(Provider provider, int port) throws IOException {
        Files.createDirectories(stateFolder);
        return provider == Provider.ZROK ? openZrok(port) : openNgrok(port);
    }

    private Path program(Provider provider) throws IOException {
        Optional<Path> found = locator.find(provider.program());
        if (found.isPresent()) {
            return found.get();
        }
        if (!props.install()) {
            throw new IOException(provider.program() + " is not installed and DEV_TUNNEL_INSTALL is off");
        }
        log.info("{} is not installed, downloading it into {}", provider.program(), installFolder);
        try {
            Path installed = installer.install(provider, installFolder);
            log.info("Installed {}", installed);
            return installed;
        } catch (IOException e) {
            throw new IOException(e.getMessage() + ". " + ToolInstaller.manualHelp(provider, platform), e);
        }
    }

    private Open openZrok(int port) throws IOException {
        Path exe = program(Provider.ZROK);
        if (!zrokEnabled()) {
            if (props.zrokToken().isEmpty()) {
                throw new IOException("zrok2 is installed but this machine is not enabled. Set ZROK_ENABLE_TOKEN in .env or run: zrok2 enable YOUR_TOKEN");
            }
            log.info("Enabling zrok2 on this machine");
            Path enableLog = stateFolder.resolve("zrok-enable.log");
            try {
                int code = launcher.runToEnd(List.of(exe.toString(), "enable", props.zrokToken()), Map.of(), enableLog, Duration.ofSeconds(60));
                if (code != 0) {
                    throw new IOException("zrok2 enable failed (exit " + code + "): " + tail(enableLog));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted while enabling zrok2");
            }
        }
        Path output = stateFolder.resolve("zrok-" + port + ".log");
        List<String> command = new ArrayList<>(List.of(exe.toString(), "share", "public", "--headless"));
        if (!props.zrokShareArgs().isBlank()) {
            command.addAll(Arrays.asList(props.zrokShareArgs().strip().split("\\s+")));
        }
        command.add(String.valueOf(port));
        log.info("Opening a zrok2 tunnel to port {}", port);
        Launcher.Running running = launcher.start(command, Map.of(), output);
        return await(running, () -> TunnelOutput.zrok(read(output)).orElse(null), output, "zrok2");
    }

    private Open openNgrok(int port) throws IOException {
        Optional<String> existing = TunnelOutput.ngrok(reader.read(props.ngrokApi() + "/api/tunnels"), port);
        if (existing.isPresent()) {
            log.info("ngrok is already tunnelling port {}, using it", port);
            return new Open(existing.get(), null);
        }
        Path exe = program(Provider.NGROK);
        if (!ngrokConfigured() && props.ngrokToken().isEmpty()) {
            throw new IOException("ngrok has no authtoken. Set NGROK_AUTHTOKEN in .env or run: ngrok config add-authtoken YOUR_TOKEN");
        }
        Map<String, String> environment = props.ngrokToken().isEmpty() ? Map.of() : Map.of("NGROK_AUTHTOKEN", props.ngrokToken());
        Path output = stateFolder.resolve("ngrok-" + port + ".log");
        log.info("Opening an ngrok tunnel to port {}", port);
        Launcher.Running running = launcher.start(List.of(exe.toString(), "http", String.valueOf(port), "--log=stdout"), environment, output);
        return await(running, () -> TunnelOutput.ngrok(reader.read(props.ngrokApi() + "/api/tunnels"), port).orElse(null), output, "ngrok");
    }

    private Open await(Launcher.Running running, java.util.function.Supplier<String> address, Path output, String name) throws IOException {
        long deadline = System.nanoTime() + Duration.ofSeconds(props.waitSeconds()).toNanos();
        while (true) {
            String url = address.get();
            if (url != null) {
                return new Open(url, running);
            }
            if (!running.alive()) {
                String said = tail(output);
                running.stop();
                throw new IOException(name + " stopped before it showed an address. " + said);
            }
            if (System.nanoTime() > deadline) {
                String said = tail(output);
                running.stop();
                throw new IOException(name + " showed no address within " + props.waitSeconds() + " seconds. " + said);
            }
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                running.stop();
                throw new IOException("interrupted while waiting for " + name);
            }
        }
    }

    private static String read(Path file) {
        try {
            return Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
        } catch (IOException e) {
            return "";
        }
    }

    private static String tail(Path file) {
        String text = read(file).replaceAll("\u001B\\[[0-9;]*[A-Za-z]", "").strip();
        String[] lines = text.split("\\R");
        int from = Math.max(0, lines.length - 6);
        return "Its output: " + String.join(" | ", Arrays.copyOfRange(lines, from, lines.length));
    }

}
