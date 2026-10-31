package com.mpesaagent.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Optional;

/**
 * Body Safaricom posts to our callback URL once the customer has answered the PIN prompt.
 * ResultCode arrives as a number in most callbacks, so it is read as text to accept both forms.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StkCallbackPayload(@JsonProperty("Body") Body body) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(@JsonProperty("stkCallback") StkCallback stkCallback) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StkCallback(
            @JsonProperty("MerchantRequestID") String merchantRequestId,
            @JsonProperty("CheckoutRequestID") String checkoutRequestId,
            @JsonProperty("ResultCode") String resultCode,
            @JsonProperty("ResultDesc") String resultDesc,
            @JsonProperty("CallbackMetadata") CallbackMetadata callbackMetadata
    ) {
        public boolean isWellFormed() {
            return checkoutRequestId != null && !checkoutRequestId.isBlank()
                    && resultCode != null && !resultCode.isBlank();
        }

        public StkResult toResult() {
            String receipt = null;
            Integer amount = null;
            if (callbackMetadata != null) {
                receipt = callbackMetadata.item("MpesaReceiptNumber").map(JsonNode::asString).orElse(null);
                amount = callbackMetadata.item("Amount").map(JsonNode::asInt).orElse(null);
            }
            return new StkResult(checkoutRequestId, resultCode, resultDesc, receipt, amount);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CallbackMetadata(@JsonProperty("Item") List<Item> items) {

        public Optional<JsonNode> item(String name) {
            if (items == null) {
                return Optional.empty();
            }
            return items.stream()
                    .filter(i -> name.equals(i.name()) && i.value() != null && !i.value().isNull())
                    .map(Item::value)
                    .findFirst();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(@JsonProperty("Name") String name, @JsonProperty("Value") JsonNode value) {
    }
}