package com.mganji.payment.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Result or timeout notification for B2C, transaction status, account balance and reversal.
 * ResultCode 0 means success. ResultParameter can arrive as a single object instead of a list.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DarajaResultPayload(@JsonProperty("Result") Result result) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(
            @JsonProperty("ResultType") String resultType,
            @JsonProperty("ResultCode") String resultCode,
            @JsonProperty("ResultDesc") String resultDesc,
            @JsonProperty("OriginatorConversationID") String originatorConversationId,
            @JsonProperty("ConversationID") String conversationId,
            @JsonProperty("TransactionID") String transactionId,
            @JsonProperty("ResultParameters") ResultParameters resultParameters
    ) {
        public boolean isWellFormed() {
            return resultCode != null && !resultCode.isBlank()
                    && ((originatorConversationId != null && !originatorConversationId.isBlank())
                    || (conversationId != null && !conversationId.isBlank()));
        }

        /** Key and value pairs as text, in the order Safaricom sent them. */
        public Map<String, String> parameters() {
            Map<String, String> map = new LinkedHashMap<>();
            if (resultParameters != null && resultParameters.items() != null) {
                for (Param p : resultParameters.items()) {
                    if (p.key() != null && p.value() != null && !p.value().isNull()) {
                        map.put(p.key(), p.value().asString());
                    }
                }
            }
            return map;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResultParameters(
            @JsonProperty("ResultParameter")
            @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            List<Param> items
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Param(@JsonProperty("Key") String key, @JsonProperty("Value") JsonNode value) {
    }
}