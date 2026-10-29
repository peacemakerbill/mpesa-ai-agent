package com.mpesaagent.companion.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Map;

@Validated
@ConfigurationProperties(prefix = "companion")
public record CompanionProperties(
        @NotBlank @Size(min = 32, message = "must be at least 32 characters") String tokenSecret,
        @DefaultValue("P30D") Duration tokenTtl,
        @DefaultValue("PT10M") Duration linkCodeTtl,
        @DefaultValue("5") int linkMaxAttempts,
        @DefaultValue("3") int linkCodesPerHour,
        @DefaultValue("PT30M") Duration actionTtl,
        @DefaultValue("10") int maxOpenActions,
        @DefaultValue("250000") int maxAmount,
        @DefaultValue("5") int minAirtime,
        @DefaultValue("http://localhost:8084") String publicBaseUrl,
        @DefaultValue("") String androidPackage,
        @DefaultValue("") String androidSha256,
        @DefaultValue("") String iosAppId,
        @DefaultValue("log") String codeDelivery,
        @DefaultValue HandOff handOff
) {

    /** What the phone app uses to start M-Pesa. Values come from configuration so they can change without a new app. */
    public record HandOff(
            @DefaultValue("*334#") String ussdMain,
            @DefaultValue("") String mpesaAndroidPackage,
            @DefaultValue("") String mpesaIosUrl,
            Map<String, String> ussdTemplates
    ) {
        public HandOff {
            ussdTemplates = ussdTemplates == null ? Map.of() : Map.copyOf(ussdTemplates);
        }
    }
}
