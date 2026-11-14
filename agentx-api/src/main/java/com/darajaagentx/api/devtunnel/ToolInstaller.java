package com.darajaagentx.api.devtunnel;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Downloads zrok2 or ngrok into one folder of the user's own, so no administrator rights are needed. */
public final class ToolInstaller {

    /** Gets the bytes at an address. http, https and file addresses all work. */
    @FunctionalInterface
    public interface Downloader {
        byte[] get(String url) throws IOException;
    }

    private static final Pattern RELEASE_TAG = Pattern.compile("\"tag_name\"\\s*:\\s*\"(v2[^\"]*)\"");

    private final Platform platform;
    private final DevTunnelProperties props;
    private final Downloader downloader;

    public ToolInstaller(Platform platform, DevTunnelProperties props, Downloader downloader) {
        this.platform = platform;
        this.props = props;
        this.downloader = downloader;
    }

    public static byte[] fetch(String address) throws IOException {
        URLConnection connection = URI.create(address).toURL().openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(120_000);
        connection.setRequestProperty("User-Agent", "agentx-dev-tunnel");
        if (connection instanceof HttpURLConnection http) {
            http.setInstanceFollowRedirects(true);
        }
        try (InputStream in = connection.getInputStream()) {
            return in.readAllBytes();
        }
    }

    /** Downloads the program for this system into folder and returns where it is. */
    public Path install(Provider provider, Path folder) throws IOException {
        String url = provider == Provider.ZROK ? zrokUrl() : ngrokUrl();
        byte[] data;
        try {
            data = downloader.get(url);
        } catch (IOException e) {
            throw new IOException("could not download " + url + " (" + e.getMessage() + ")", e);
        }
        return Archives.extract(data, platform.exe(provider.program()), folder);
    }

    String zrokUrl() throws IOException {
        if (!props.zrokDownloadUrl().isBlank()) {
            return props.zrokDownloadUrl().strip();
        }
        String version = props.zrokVersion().strip();
        if (version.isEmpty()) {
            String json = new String(downloader.get("https://api.github.com/repos/openziti/zrok/releases?per_page=40"), StandardCharsets.UTF_8);
            Matcher m = RELEASE_TAG.matcher(json);
            while (m.find()) {
                if (!m.group(1).contains("-")) {   // a release candidate has a dash
                    version = m.group(1);
                    break;
                }
            }
        }
        if (version.isEmpty()) {
            throw new IOException("no zrok2 release was found, set ZROK2_VERSION (for example v2.0.0)");
        }
        String plain = version.startsWith("v") ? version.substring(1) : version;
        String v = version.startsWith("v") ? version : "v" + version;
        return "https://github.com/openziti/zrok/releases/download/" + v + "/zrok2_" + plain + "_" + platform.os() + "_" + platform.arch() + ".tar.gz";
    }

    String ngrokUrl() {
        if (!props.ngrokDownloadUrl().isBlank()) {
            return props.ngrokDownloadUrl().strip();
        }
        String extension = platform.os().equals("linux") ? "tgz" : "zip";
        return "https://bin.equinox.io/c/bNyj1mQVY4c/ngrok-v3-stable-" + platform.os() + "-" + platform.arch() + "." + extension;
    }

    /** What to tell the person when the download did not work. */
    public static String manualHelp(Provider provider, Platform platform) {
        if (provider == Provider.ZROK) {
            return platform.windows()
                    ? "Install zrok2 yourself (https://netfoundry.io/docs/zrok/get-started/install-zrok2) and put it on your PATH."
                    : "Install zrok2 yourself: curl -sSf https://get.openziti.io/install.bash | sudo bash -s zrok2";
        }
        return "Install ngrok yourself from https://ngrok.com/download and put it on your PATH.";
    }
}
