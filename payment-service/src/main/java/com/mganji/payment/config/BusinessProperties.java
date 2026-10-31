package com.mganji.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Branding used on receipts and QR codes. */
@ConfigurationProperties(prefix = "business")
public record BusinessProperties(
        @DefaultValue("Our Business") String name,
        @DefaultValue("") String supportPhone
) {
}
