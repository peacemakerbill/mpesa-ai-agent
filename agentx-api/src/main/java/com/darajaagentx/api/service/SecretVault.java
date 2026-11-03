package com.darajaagentx.api.service;

import com.darajaagentx.api.config.VaultProperties;
import com.darajaagentx.api.exception.VaultNotConfiguredException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Locks and unlocks the business credentials with AES-256-GCM.
 *
 * <p>Each value gets its own random nonce, and the owner and field name are bound into the lock, so a stored value
 * copied onto another row or another field fails to open. The stored text looks like {@code v1:BASE64}. The
 * {@code v1:} prefix leaves room for a new key or method later.
 */
@Component
public class SecretVault {

    private static final String PREFIX = "v1:";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public SecretVault(VaultProperties props) {
        this(props.key());
    }

    public SecretVault(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            this.key = null;
            return;
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("AGENTX_VAULT_KEY is not valid Base64. Make one with: openssl rand -base64 32");
        }
        if (raw.length != 32) {
            throw new IllegalStateException("AGENTX_VAULT_KEY must be 32 bytes (44 Base64 characters). Make one with: openssl rand -base64 32");
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    public boolean configured() {
        return key != null;
    }

    /** @param context who and what this value belongs to, for example the owner's phone and the field name */
    public String encrypt(String plain, String context) {
        requireKey();
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            byte[] sealed = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            ByteBuffer out = ByteBuffer.allocate(nonce.length + sealed.length);
            out.put(nonce).put(sealed);
            return PREFIX + Base64.getEncoder().encodeToString(out.array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not lock the value", e);
        }
    }

    public String decrypt(String stored, String context) {
        requireKey();
        if (stored == null || !stored.startsWith(PREFIX)) {
            throw new IllegalStateException("The stored value is not in a known format");
        }
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            if (all.length <= NONCE_BYTES) {
                throw new IllegalStateException("The stored value is too short");
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, NONCE_BYTES));
            cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(all, NONCE_BYTES, all.length - NONCE_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // Wrong key, changed value or wrong owner. Say nothing more than that.
            throw new IllegalStateException("A saved credential could not be opened. Was AGENTX_VAULT_KEY changed?");
        }
    }

    private void requireKey() {
        if (key == null) {
            throw new VaultNotConfiguredException();
        }
    }
}
