package com.mpesaagent.payment.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@ConfigurationProperties(prefix = "mpesa")
public record MpesaProperties(
        @NotBlank String baseUrl,
        @NotBlank String consumerKey,
        @NotBlank String consumerSecret,
        @NotBlank String shortcode,
        @NotBlank String passkey,
        @NotBlank String transactionType,
        @NotBlank String publicBaseUrl,
        @NotBlank String callbackSecret,
        List<String> callbackAllowedIps
) {
    /**
     * Daraja rejects callback URLs that contain words like "mpesa" or "safaricom", so the path is neutral.
     */
    public static final String STK_CALLBACK_PATH = "/api/callbacks/stk";

    public MpesaProperties {
        callbackAllowedIps = callbackAllowedIps == null ? List.of() : List.copyOf(callbackAllowedIps);
    }

    /** Result and timeout URLs for B2C, transaction status, account balance and reversal. */
    public static final String OPS_RESULT_PATH = "/api/callbacks/ops/result";
    public static final String OPS_TIMEOUT_PATH = "/api/callbacks/ops/timeout";

    /** Safaricom calls these when a customer pays our Paybill or Till directly. */
    public static final String C2B_VALIDATION_PATH = "/api/callbacks/c2b/validation";
    public static final String C2B_CONFIRMATION_PATH = "/api/callbacks/c2b/confirmation";

    public String stkCallbackUrl() {
        return urlFor(STK_CALLBACK_PATH);
    }

    public String opsResultUrl() {
        return urlFor(OPS_RESULT_PATH);
    }

    public String opsTimeoutUrl() {
        return urlFor(OPS_TIMEOUT_PATH);
    }

    public String c2bValidationUrl() {
        return urlFor(C2B_VALIDATION_PATH);
    }

    public String c2bConfirmationUrl() {
        return urlFor(C2B_CONFIRMATION_PATH);
    }

    private String urlFor(String path) {
        String base = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
        return base + path + "/" + callbackSecret;
    }
}
