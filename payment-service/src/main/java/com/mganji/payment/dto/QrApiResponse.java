package com.mganji.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record QrApiResponse(
        @JsonProperty("ResponseCode") String responseCode,
        @JsonProperty("RequestID") String requestId,
        @JsonProperty("ResponseDescription") String responseDescription,
        @JsonProperty("QRCode") String qrCode
) {
}
