package com.darajaagentx.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where Safaricom sends the final answer to a request that is not answered at once: B2C, B2B, Pay to Pochi la Biashara,
 * account balance, transaction status and reversals.
 *
 * <p>Each of the two takes either an address or a template:
 * <ul>
 *   <li>An address such as {@code https://hooks.example.com}: the service adds the path and the secret token of each
 *       request, so every answer can be matched to the request it belongs to.</li>
 *   <li>A template with {@code {id}} and {@code {token}} in it, such as
 *       {@code https://hooks.example.com/safaricom/{id}/{token}/result}: used exactly as written, with the two values filled in.</li>
 * </ul>
 * Blank means the public address of this service (AGENTX_PUBLIC_BASE_URL), or the development tunnel when one is open.
 *
 * @param resultUrl  where the final result goes (AGENTX_RESULT_URL)
 * @param timeoutUrl where Safaricom reports that a request waited too long in its queue (AGENTX_TIMEOUT_URL)
 */
@ConfigurationProperties(prefix = "agentx.initiator")
public record InitiatorCallbackProperties(@DefaultValue("") String resultUrl, @DefaultValue("") String timeoutUrl) {
}
