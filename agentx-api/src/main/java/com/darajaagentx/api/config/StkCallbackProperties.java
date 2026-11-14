package com.darajaagentx.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where Safaricom sends the answer to an STK Push (the customer paid, cancelled, or never answered).
 *
 * @param callbackUrl the public https address Safaricom should call, for example the address of a tunnel while testing on a laptop.
 *                    Blank means the public address of this service (AGENTX_PUBLIC_BASE_URL). Only the address goes here: the path
 *                    and the secret token of each request are added by the service.
 */
@ConfigurationProperties(prefix = "agentx.stk")
public record StkCallbackProperties(@DefaultValue("") String callbackUrl) {
}
