package com.mpesaagent.payment.service;

import com.mpesaagent.payment.config.DarajaOpsProperties;
import com.mpesaagent.payment.exception.OperationNotConfiguredException;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.Base64;

/**
 * Builds the SecurityCredential Daraja wants for B2C, balance, status and reversal: the initiator password
 * encrypted with Safaricom's public certificate (RSA, PKCS1 padding) and Base64 encoded.
 */
@Service
public class SecurityCredentialService {

    private final DarajaOpsProperties props;
    private volatile String cached;

    public SecurityCredentialService(DarajaOpsProperties props) {
        this.props = props;
    }

    public String initiatorName() {
        if (isBlank(props.initiatorName())) {
            throw new OperationNotConfiguredException("The M-Pesa initiator name is not configured");
        }
        return props.initiatorName();
    }

    public String securityCredential() {
        String value = cached;
        if (value != null) {
            return value;
        }
        synchronized (this) {
            if (cached != null) {
                return cached;
            }
            cached = build();
            return cached;
        }
    }

    private String build() {
        if (!isBlank(props.securityCredential())) {
            return props.securityCredential().trim();
        }
        if (isBlank(props.initiatorPassword()) || isBlank(props.certificatePath())) {
            throw new OperationNotConfiguredException(
                    "Set the M-Pesa initiator password and certificate path, or a ready made security credential");
        }
        try (InputStream in = Files.newInputStream(Path.of(props.certificatePath()))) {
            return encrypt(props.initiatorPassword(), publicKey(in));
        } catch (IOException | GeneralSecurityException e) {
            throw new OperationNotConfiguredException("Could not use the M-Pesa certificate: " + e.getMessage());
        }
    }

    public static PublicKey publicKey(InputStream certificate) throws CertificateException {
        Certificate cert = CertificateFactory.getInstance("X.509").generateCertificate(certificate);
        return cert.getPublicKey();
    }

    public static String encrypt(String password, PublicKey key) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        return Base64.getEncoder().encodeToString(cipher.doFinal(password.getBytes(StandardCharsets.UTF_8)));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
