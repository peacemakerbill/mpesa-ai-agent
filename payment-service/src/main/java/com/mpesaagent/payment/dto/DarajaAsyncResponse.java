package com.mpesaagent.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Immediate answer to B2C, status, balance, reversal and register URL calls. The real result comes later. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DarajaAsyncResponse(
        @JsonProperty("ConversationID") String conversationId,
        @JsonProperty("OriginatorConversationID") String originatorConversationId,
        @JsonProperty("ResponseCode") String responseCode,
        @JsonProperty("ResponseDescription") String responseDescription
) {
    public boolean accepted() {
        return "0".equals(responseCode);
    }
}
