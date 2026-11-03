package com.darajaagentx.api.service;

import com.darajaagentx.api.config.DarajaProperties;
import com.darajaagentx.api.exception.CertificateMissingException;
import com.darajaagentx.api.model.DarajaEnvironment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Makes the SecurityCredential Daraja asks for: the initiator password encrypted with Safaricom's public
 * certificate (RSA, PKCS1 padding) and Base64 encoded.
 *
 * <p>The certificates are public. Put the sandbox one in {@code resources/certs/sandbox} and the production one in
 * {@code resources/certs/production}. Any file ending in .cer, .crt, .pem or .der is used (PEM or DER inside).
 * If {@code agentx.daraja.certs-directory} is set, the same two sub folders are looked for there first.
 */
@Component
public class SecurityCredentialGenerator {

    private static final List<String> EXTENSIONS = List.of(".cer", ".crt", ".pem", ".der");

    private final DarajaProperties props;
    private final Map<DarajaEnvironment, PublicKey> keys = new ConcurrentHashMap<>();

    public SecurityCredentialGenerator(DarajaProperties props) {
        this.props = props;
    }

    /** True when a certificate file for the environment can be found and read. */
    public boolean certificateAvailable(DarajaEnvironment env) {
        try {
            publicKey(env);
            return true;
        } catch (CertificateMissingException e) {
            return false;
        }
    }

    public String generate(DarajaEnvironment env, String initiatorPassword) {
        try {
            return encrypt(initiatorPassword, publicKey(env));
        } catch (GeneralSecurityException e) {
            throw new CertificateMissingException("Could not use the " + folder(env) + " certificate: " + e.getMessage());
        }
    }

    public static String encrypt(String password, PublicKey key) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        return Base64.getEncoder().encodeToString(cipher.doFinal(password.getBytes(StandardCharsets.UTF_8)));
    }

    private PublicKey publicKey(DarajaEnvironment env) {
        PublicKey cached = keys.get(env);
        if (cached != null) {
            return cached;
        }
        PublicKey loaded = load(env);
        keys.put(env, loaded);
        return loaded;
    }

    private PublicKey load(DarajaEnvironment env) {
        String folder = folder(env);
        try (InputStream in = open(folder)) {
            if (in == null) {
                throw new CertificateMissingException("The Safaricom " + folder + " certificate is missing. Download it from the Daraja portal and put it in src/main/resources/certs/" + folder + "/");
            }
            return CertificateFactory.getInstance("X.509").generateCertificate(in).getPublicKey();
        } catch (IOException | GeneralSecurityException e) {
            throw new CertificateMissingException("The " + folder + " certificate file could not be read. It must be the certificate Safaricom gives out (.cer), not a private key.");
        }
    }

    private InputStream open(String folder) throws IOException {
        String dir = props.certsDirectory();
        if (dir != null && !dir.isBlank()) {
            Path path = Path.of(dir, folder);
            if (Files.isDirectory(path)) {
                List<Path> found = new ArrayList<>();
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                    for (Path p : stream) {
                        if (isCertificate(p.getFileName().toString())) {
                            found.add(p);
                        }
                    }
                }
                found.sort(Comparator.comparing(p -> p.getFileName().toString()));
                if (!found.isEmpty()) {
                    return Files.newInputStream(found.get(0));
                }
            }
        }
        Resource[] resources = new PathMatchingResourcePatternResolver().getResources("classpath*:certs/" + folder + "/*");
        List<Resource> certs = new ArrayList<>();
        for (Resource r : resources) {
            String name = r.getFilename();
            if (name != null && isCertificate(name) && r.isReadable()) {
                certs.add(r);
            }
        }
        certs.sort(Comparator.comparing(Resource::getFilename));
        return certs.isEmpty() ? null : certs.get(0).getInputStream();
    }

    private static boolean isCertificate(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        return EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    private static String folder(DarajaEnvironment env) {
        return env == DarajaEnvironment.PRODUCTION ? "production" : "sandbox";
    }
}
