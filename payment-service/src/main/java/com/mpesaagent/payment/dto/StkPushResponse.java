package com.mpesaagent.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Synchronous answer from /mpesa/stkpush/v1/processrequest. ResponseCode "0" only means the request was accepted. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StkPushResponse(
        @JsonProperty("MerchantRequestID") String merchantRequestId,
        @JsonProperty("CheckoutRequestID") String checkoutRequestId,
        @JsonProperty("ResponseCode") String responseCode,
        @JsonProperty("ResponseDescription") String responseDescription,
        @JsonProperty("CustomerMessage") String customerMessage
) {
    public boolean accepted() {
        return "0".equals(responseCode) && checkoutRequestId != null && !checkoutRequestId.isBlank();
    }
}
