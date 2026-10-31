package com.mganji.payment.service;

import com.mganji.payment.config.DarajaOpsProperties;
import com.mganji.payment.exception.OperationNotConfiguredException;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityCredentialServiceTest {

    private static DarajaOpsProperties props(String name, String password, String cert, String credential) {
        return new DarajaOpsProperties(name, password, cert, credential, null, null, null, null, null, null);
    }

    @Test
    void encryptedPasswordDecryptsBackWithThePrivateKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();

        String encrypted = SecurityCredentialService.encrypt("Safaricom999!*!", pair.getPublic());

        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.DECRYPT_MODE, pair.getPrivate());
        String decrypted = new String(cipher.doFinal(Base64.getDecoder().decode(encrypted)), StandardCharsets.UTF_8);
        assertThat(decrypted).isEqualTo("Safaricom999!*!");
    }

    @Test
    void readyMadeCredentialIsUsedAsIs() {
        SecurityCredentialService service = new SecurityCredentialService(props("init", "", "", "  READYMADE  "));

        assertThat(service.securityCredential()).isEqualTo("READYMADE");
        assertThat(service.initiatorName()).isEqualTo("init");
    }

    @Test
    void missingSettingsGiveAClearConfigurationError() {
        SecurityCredentialService empty = new SecurityCredentialService(props("", "", "", ""));

        assertThatThrownBy(empty::initiatorName).isInstanceOf(OperationNotConfiguredException.class);
        assertThatThrownBy(empty::securityCredential).isInstanceOf(OperationNotConfiguredException.class);
    }

    @Test
    void unreadableCertificateIsAConfigurationError() {
        SecurityCredentialService service = new SecurityCredentialService(props("init", "pw", "/does/not/exist.cer", ""));

        assertThatThrownBy(service::securityCredential).isInstanceOf(OperationNotConfiguredException.class);
    }
}
