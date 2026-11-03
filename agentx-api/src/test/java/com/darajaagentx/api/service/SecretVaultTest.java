package com.darajaagentx.api.service;

import com.darajaagentx.api.exception.VaultNotConfiguredException;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretVaultTest {

    private static String key(int fill) {
        byte[] raw = new byte[32];
        java.util.Arrays.fill(raw, (byte) fill);
        return Base64.getEncoder().encodeToString(raw);
    }

    @Test
    void aLockedValueOpensAgainWithTheSameOwnerAndField() {
        SecretVault vault = new SecretVault(key(7));
        String locked = vault.encrypt("pässwörd✓", "254712345678|initiatorPassword");
        assertThat(locked).startsWith("v1:").doesNotContain("pässwörd");
        assertThat(vault.decrypt(locked, "254712345678|initiatorPassword")).isEqualTo("pässwörd✓");
    }

    @Test
    void everyLockUsesANewNonce() {
        SecretVault vault = new SecretVault(key(7));
        assertThat(vault.encrypt("same", "c")).isNotEqualTo(vault.encrypt("same", "c"));
    }

    @Test
    void aValueCopiedToAnotherOwnerOrFieldDoesNotOpen() {
        SecretVault vault = new SecretVault(key(7));
        String locked = vault.encrypt("secret", "254712345678|consumerSecret");
        assertThatThrownBy(() -> vault.decrypt(locked, "254799999999|consumerSecret")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> vault.decrypt(locked, "254712345678|passkey")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anotherKeyCannotOpenIt() {
        String locked = new SecretVault(key(7)).encrypt("secret", "c");
        assertThatThrownBy(() -> new SecretVault(key(8)).decrypt(locked, "c")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aChangedValueDoesNotOpen() {
        SecretVault vault = new SecretVault(key(7));
        String locked = vault.encrypt("secret", "c");
        String changed = locked.substring(0, locked.length() - 3) + (locked.endsWith("AAA") ? "BBB" : "AAA");
        assertThatThrownBy(() -> vault.decrypt(changed, "c")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void withoutAKeyTheVaultIsOffAndSaysSo() {
        SecretVault off = new SecretVault("");
        assertThat(off.configured()).isFalse();
        assertThatThrownBy(() -> off.encrypt("x", "c")).isInstanceOf(VaultNotConfiguredException.class);
        assertThatThrownBy(() -> off.decrypt("v1:AAAA", "c")).isInstanceOf(VaultNotConfiguredException.class);
    }

    @Test
    void aBadKeyStopsTheServiceAtStartInsteadOfLockingWithAWeakKey() {
        assertThatThrownBy(() -> new SecretVault(Base64.getEncoder().encodeToString(new byte[16]))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new SecretVault("not base64 !!")).isInstanceOf(IllegalStateException.class);
    }
}
