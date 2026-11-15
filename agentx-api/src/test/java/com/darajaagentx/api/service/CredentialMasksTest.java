package com.darajaagentx.api.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CredentialMasksTest {

    @Test
    void aLongKeyShowsItsFirstThreeAndLastFour() {
        assertThat(CredentialMasks.key("AbCdEfGhIjKlMnOpQrStUvWxYz123456")).isEqualTo("AbC••••3456");
    }

    @Test
    void aSecretShowsOnlyItsLastFour() {
        assertThat(CredentialMasks.secret("s3cr3tS3cr3tS3cr3t")).isEqualTo("••••••••cr3t");
    }

    @Test
    void aPasskeyShowsItsFirstAndLastFour() {
        assertThat(CredentialMasks.passkey("bfb279f9aa9bdbcf158e97dd71a467cd2e0c893059b10f78e6b72ada1ed2c919")).isEqualTo("bfb2••••c919");
    }

    @Test
    void aPasswordShowsOnlyDots() {
        String m = CredentialMasks.password("Safaricom999!*!");
        assertThat(m).isEqualTo("•".repeat(12));
        assertThat(CredentialMasks.password("abc123")).isEqualTo("•".repeat(6));
    }

    @Test
    void shortOrEmptyValuesNeverLeakTheirCharacters() {
        assertThat(CredentialMasks.secret("abcdefgh")).isEqualTo("•".repeat(8));
        assertThat(CredentialMasks.passkey("short")).isEqualTo("•".repeat(5));
        assertThat(CredentialMasks.key(null)).isEmpty();
        assertThat(CredentialMasks.password("")).isEmpty();
    }
}
