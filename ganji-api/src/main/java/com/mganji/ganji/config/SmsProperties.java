package com.mganji.ganji.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings for sending sign-in codes by SMS through Africa's Talking, used when ganji.code-delivery is "sms".
 * The sandbox address does not reach real phones. Use https://api.africastalking.com with a live key.
 */
@ConfigurationProperties(prefix = "sms")
public record SmsProperties(
        @DefaultValue("sandbox") String username,
        @DefaultValue("") String apiKey,
        @DefaultValue("") String senderId,
        @DefaultValue("https://api.sandbox.africastalking.com") String baseUrl
) {
}
