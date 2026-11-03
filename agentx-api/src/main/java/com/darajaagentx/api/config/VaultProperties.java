package com.darajaagentx.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The key that locks the business credentials (consumer secrets, initiator passwords, passkeys) in the database.
 *
 * @param key 32 random bytes, Base64 encoded. Make one with: openssl rand -base64 32. Blank means the vault is off
 *            and business accounts cannot be saved. Losing the key means saved credentials can no longer be read.
 */
@ConfigurationProperties(prefix = "agentx.vault")
public record VaultProperties(@DefaultValue("") String key) {
}
