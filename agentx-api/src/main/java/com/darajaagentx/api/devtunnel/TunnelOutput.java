package com.darajaagentx.api.devtunnel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the public address out of what zrok2 prints and out of ngrok's local API. */
final class TunnelOutput {

    private static final Pattern ANSI = Pattern.compile("\u001B\\[[0-9;]*[A-Za-z]");
    private static final Pattern ADDRESS = Pattern.compile("https://[A-Za-z0-9._-]+");
    private static final Pattern OWN_SERVICES = Pattern.compile("^https://(api|docs|www|github|netfoundry|openziti)[.-].*|.*api-v[0-9].*");
    private static final Pattern PUBLIC_URL = Pattern.compile("\"public_url\"\\s*:\\s*\"(https://[^\"]+)\"");

    private TunnelOutput() {
    }

    /** An https address in zrok2's output that is not zrok's own API or documentation. The one that mentions zrok or share is preferred. */
    static Optional<String> zrok(String output) {
        if (output == null) {
            return Optional.empty();
        }
        Matcher m = ADDRESS.matcher(ANSI.matcher(output).replaceAll(""));
        List<String> candidates = new ArrayList<>();
        while (m.find()) {
            String found = m.group();
            if (!OWN_SERVICES.matcher(found).matches() && !candidates.contains(found)) {
                candidates.add(found);
            }
        }
        for (String c : candidates) {
            if (c.contains("zrok") || c.contains("share")) {
                return Optional.of(c);
            }
        }
        return candidates.stream().findFirst();
    }

    /** The https address of the ngrok tunnel whose local side is this port, from ngrok's GET /api/tunnels answer. */
    static Optional<String> ngrok(String json, int port) {
        if (json == null) {
            return Optional.empty();
        }
        Pattern local = Pattern.compile("\"addr\"\\s*:\\s*\"[^\"]*:" + port + "\"");
        // one piece per tunnel: each starts at its "name"
        for (String tunnel : json.split("(?=\"name\"\\s*:)")) {
            if (local.matcher(tunnel).find()) {
                Matcher m = PUBLIC_URL.matcher(tunnel);
                if (m.find()) {
                    return Optional.of(m.group(1));
                }
            }
        }
        return Optional.empty();
    }
}
