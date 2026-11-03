package com.darajaagentx.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where Safaricom's Daraja lives and where the public certificates are.
 *
 * @param sandboxUrl     Daraja sandbox address
 * @param productionUrl  Daraja production address
 * @param certsDirectory Optional folder on the server with sandbox and production sub folders holding the certificates.
 *                       Blank means the certificates inside the application, under resources/certs.
 */
@ConfigurationProperties(prefix = "agentx.daraja")
public record DarajaProperties(
        @DefaultValue("https://sandbox.safaricom.co.ke") String sandboxUrl,
        @DefaultValue("https://api.safaricom.co.ke") String productionUrl,
        @DefaultValue("") String certsDirectory
) {
}
