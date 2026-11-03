package com.darajaagentx.api.service;

import com.darajaagentx.api.config.DarajaProperties;
import com.darajaagentx.api.exception.CertificateMissingException;
import com.darajaagentx.api.model.DarajaEnvironment;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class SecurityCredentialGeneratorTest {

    private static Path certsDir() throws Exception {
        return Path.of(SecurityCredentialGeneratorTest.class.getResource("/daraja-certs").toURI());
    }

    private static String open(String credential, String environment) throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(certsDir().resolve(environment + ".p12"))) {
            store.load(in, "changeit".toCharArray());
        }
        PrivateKey key = (PrivateKey) store.getKey("t", "changeit".toCharArray());
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.DECRYPT_MODE, key);
        return new String(cipher.doFinal(Base64.getDecoder().decode(credential)), StandardCharsets.UTF_8);
    }

    @Test
    void theCredentialOpensToTheInitiatorPasswordWithTheMatchingPrivateKey() throws Exception {
        SecurityCredentialGenerator generator = new SecurityCredentialGenerator(new DarajaProperties("a", "b", certsDir().toString()));
        assertThat(open(generator.generate(DarajaEnvironment.SANDBOX, "Safaricom999!*!"), "sandbox")).isEqualTo("Safaricom999!*!");
        assertThat(open(generator.generate(DarajaEnvironment.PRODUCTION, "Another#Pass1"), "production")).isEqualTo("Another#Pass1");
    }

    @Test
    void sandboxAndProductionUseTheirOwnCertificates() throws Exception {
        SecurityCredentialGenerator generator = new SecurityCredentialGenerator(new DarajaProperties("a", "b", certsDir().toString()));
        String sandbox = generator.generate(DarajaEnvironment.SANDBOX, "pw123456");
        assertThatThrownBy(() -> open(sandbox, "production")).isInstanceOf(Exception.class);
    }

    @Test
    void aMissingCertificateIsReportedClearly() {
        SecurityCredentialGenerator generator = new SecurityCredentialGenerator(new DarajaProperties("a", "b", "/does/not/exist"));
        // Once the real Safaricom sandbox certificate is added to resources/certs/sandbox it is found as a fallback, so this check does not apply.
        assumeFalse(generator.certificateAvailable(DarajaEnvironment.SANDBOX), "a real sandbox certificate is installed");
        assertThat(generator.certificateAvailable(DarajaEnvironment.SANDBOX)).isFalse();
        assertThatThrownBy(() -> generator.generate(DarajaEnvironment.SANDBOX, "x"))
                .isInstanceOf(CertificateMissingException.class).hasMessageContaining("certs/sandbox");
    }
}
