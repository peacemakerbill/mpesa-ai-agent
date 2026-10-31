package com.mpesaagent.payment.dto;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class DarajaResultPayloadTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void parsesB2cSuccessWithParameterList() throws Exception {
        String json = """
                {"Result":{"ResultType":0,"ResultCode":0,"ResultDesc":"The service request is processed successfully.",
                 "OriginatorConversationID":"orig-1","ConversationID":"AG_1","TransactionID":"QGH7ABC123",
                 "ResultParameters":{"ResultParameter":[
                    {"Key":"TransactionAmount","Value":1000},
                    {"Key":"TransactionReceipt","Value":"QGH7ABC123"},
                    {"Key":"ReceiverPartyPublicName","Value":"254722000000 - JOHN DOE"}]},
                 "ReferenceData":{"ReferenceItem":{"Key":"QueueTimeoutURL","Value":"https://x"}}}}
                """;

        DarajaResultPayload.Result result = mapper.readValue(json, DarajaResultPayload.class).result();

        assertThat(result.isWellFormed()).isTrue();
        assertThat(result.resultCode()).isEqualTo("0");
        assertThat(result.originatorConversationId()).isEqualTo("orig-1");
        assertThat(result.parameters())
                .containsEntry("TransactionReceipt", "QGH7ABC123")
                .containsEntry("TransactionAmount", "1000");
    }

    @Test
    void acceptsASingleParameterObjectInsteadOfAList() throws Exception {
        String json = """
                {"Result":{"ResultCode":"0","ResultDesc":"ok","OriginatorConversationID":"o","ConversationID":"c",
                 "ResultParameters":{"ResultParameter":{"Key":"AccountBalance","Value":"Working Account|KES|10.00|10.00|0.00|0.00"}}}}
                """;

        DarajaResultPayload.Result result = mapper.readValue(json, DarajaResultPayload.class).result();

        assertThat(result.parameters()).containsKey("AccountBalance");
    }

    @Test
    void failureWithoutParametersIsStillWellFormed() throws Exception {
        String json = """
                {"Result":{"ResultCode":2001,"ResultDesc":"The initiator information is invalid.",
                 "OriginatorConversationID":"orig-2","ConversationID":"AG_2"}}
                """;

        DarajaResultPayload.Result result = mapper.readValue(json, DarajaResultPayload.class).result();

        assertThat(result.isWellFormed()).isTrue();
        assertThat(result.resultCode()).isEqualTo("2001");
        assertThat(result.parameters()).isEmpty();
    }

    @Test
    void missingIdsOrCodeIsMalformed() throws Exception {
        DarajaResultPayload noIds = mapper.readValue("{\"Result\":{\"ResultCode\":0}}", DarajaResultPayload.class);
        DarajaResultPayload noCode = mapper.readValue("{\"Result\":{\"OriginatorConversationID\":\"o\"}}", DarajaResultPayload.class);

        assertThat(noIds.result().isWellFormed()).isFalse();
        assertThat(noCode.result().isWellFormed()).isFalse();
    }
}