package com.mganji.ganji.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class TokenServiceTest {

    private final TokenService tokens = new TokenService(TestSupport.props(), TestSupport.clock());

    @Test
    void aFreshTokenCarriesThePhoneAndDevice() {
        String token = tokens.issue("254712345678", "device-abc12345", tokens.expiryFromNow());

        TokenService.Claims claims = tokens.parse(token).orElseThrow();

        assertThat(claims.phone()).isEqualTo("254712345678");
        assertThat(claims.deviceId()).isEqualTo("device-abc12345");
        assertThat(claims.expires()).isEqualTo(TestSupport.NOW.plus(Duration.ofDays(30)));
    }

    @Test
    void anExpiredTokenIsRefused() {
        String token = tokens.issue("254712345678", "device-abc12345", TestSupport.NOW.minusSeconds(1));

        assertThat(tokens.parse(token)).isEmpty();
    }

    @Test
    void tokenIsRefusedOnceTheClockPassesItsExpiry() {
        String token = tokens.issue("254712345678", "device-abc12345", tokens.expiryFromNow());
        TokenService later = new TokenService(TestSupport.props(),
                Clock.fixed(TestSupport.NOW.plus(Duration.ofDays(31)), ZoneOffset.UTC));

        assertThat(later.parse(token)).isEmpty();
    }

    @Test
    void changingAnythingInTheTokenBreaksTheSignature() {
        String token = tokens.issue("254712345678", "device-abc12345", tokens.expiryFromNow());
        String other = tokens.issue("254799999999", "device-abc12345", tokens.expiryFromNow());

        String forged = other.substring(0, other.indexOf('.')) + token.substring(token.indexOf('.'));

        assertThat(tokens.parse(forged)).isEmpty();
        assertThat(tokens.parse(token + "x")).isEmpty();
    }

    @Test
    void aTokenSignedWithAnotherSecretIsRefused() {
        var otherProps = new com.mganji.ganji.config.GanjiProperties("a-completely-different-secret-of-32-chars!!",
                Duration.ofDays(30), Duration.ofMinutes(10), 5, 3, Duration.ofMinutes(30), 10, 250_000, 5, "", "", "", "", "",
                new com.mganji.ganji.config.GanjiProperties.HandOff("*334#", "", "", java.util.Map.of()));
        String foreign = new TokenService(otherProps, TestSupport.clock()).issue("254712345678", "device-abc12345", Instant.parse("2027-01-01T00:00:00Z"));

        assertThat(tokens.parse(foreign)).isEmpty();
    }

    @Test
    void garbageIsRefusedWithoutThrowing() {
        assertThat(tokens.parse(null)).isEmpty();
        assertThat(tokens.parse("")).isEmpty();
        assertThat(tokens.parse("abc")).isEmpty();
        assertThat(tokens.parse(".")).isEmpty();
        assertThat(tokens.parse("a.b")).isEmpty();
    }
}
