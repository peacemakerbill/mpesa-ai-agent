package com.mpesaagent.companion.service;

import com.mpesaagent.companion.config.CompanionProperties;
import com.mpesaagent.companion.util.Hmac;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * Login tokens for the phone app: base64url(phone|deviceId|expiry) + "." + HMAC. Small and dependency free.
 * A token alone is not enough: the filter also checks that the device has not been revoked.
 */
@Service
public class TokenService {

    public record Claims(String phone, String deviceId, Instant expires) {
    }

    private static final String KEY_CONTEXT = "device-token:";

    private final CompanionProperties props;
    private final Clock clock;

    public TokenService(CompanionProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
    }

    public Instant expiryFromNow() {
        return clock.instant().plus(props.tokenTtl());
    }

    public String issue(String phone, String deviceId, Instant expires) {
        String payload = phone + "|" + deviceId + "|" + expires.getEpochSecond();
        String body = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return body + "." + Hmac.base64Url(Hmac.sha256(KEY_CONTEXT + props.tokenSecret(), body));
    }

    public Optional<Claims> parse(String token) {
        if (token == null) {
            return Optional.empty();
        }
        int dot = token.indexOf('.');
        if (dot < 1 || dot == token.length() - 1) {
            return Optional.empty();
        }
        String body = token.substring(0, dot);
        byte[] expected = Hmac.base64Url(Hmac.sha256(KEY_CONTEXT + props.tokenSecret(), body)).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, token.substring(dot + 1).getBytes(StandardCharsets.UTF_8))) {
            return Optional.empty();
        }
        try {
            String payload = new String(Base64.getUrlDecoder().decode(body), StandardCharsets.UTF_8);
            String[] parts = payload.split("\\|");
            if (parts.length != 3) {
                return Optional.empty();
            }
            Instant expires = Instant.ofEpochSecond(Long.parseLong(parts[2]));
            if (!clock.instant().isBefore(expires)) {
                return Optional.empty();
            }
            return Optional.of(new Claims(parts[0], parts[1], expires));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
