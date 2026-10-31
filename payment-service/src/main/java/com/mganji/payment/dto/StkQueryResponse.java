package com.mganji.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Answer from /mpesa/stkpushquery/v1/query. A non empty ResultCode means the transaction has a final outcome. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StkQueryResponse(
        @JsonProperty("MerchantRequestID") String merchantRequestId,
        @JsonProperty("CheckoutRequestID") String checkoutRequestId,
        @JsonProperty("ResponseCode") String responseCode,
        @JsonProperty("ResponseDescription") String responseDescription,
        @JsonProperty("ResultCode") String resultCode,
        @JsonProperty("ResultDesc") String resultDesc
) {
    public boolean hasFinalOutcome() {
        return resultCode != null && !resultCode.isBlank();
    }

    public StkResult toResult(String requestedCheckoutId) {
        String id = checkoutRequestId != null ? checkoutRequestId : requestedCheckoutId;
        return new StkResult(id, resultCode, resultDesc, null, null);
    }
}
