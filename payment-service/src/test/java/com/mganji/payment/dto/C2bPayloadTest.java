package com.mganji.payment.dto;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class C2bPayloadTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void parsesConfirmation() throws Exception {
        String json = """
                {"TransactionType":"Pay Bill","TransID":"RKTQDM7W6S","TransTime":"20261029191500","TransAmount":"150.00",
                 "BusinessShortCode":"600638","BillRefNumber":"254700000010","OrgAccountBalance":"49197.00",
                 "MSISDN":"2547 ***** 126","FirstName":"John","MiddleName":"","LastName":"Doe","SomethingNew":"ignored"}
                """;

        C2bPayload payload = mapper.readValue(json, C2bPayload.class);

        assertThat(payload.isWellFormed()).isTrue();
        assertThat(payload.amount()).isEqualByComparingTo("150.00");
        assertThat(payload.payerName()).isEqualTo("John Doe");
    }

    @Test
    void badAmountOrMissingIdIsMalformed() throws Exception {
        assertThat(mapper.readValue("{\"TransID\":\"A\",\"TransAmount\":\"abc\"}", C2bPayload.class).isWellFormed()).isFalse();
        assertThat(mapper.readValue("{\"TransAmount\":\"10\"}", C2bPayload.class).isWellFormed()).isFalse();
        assertThat(mapper.readValue("{\"TransID\":\"A\"}", C2bPayload.class).isWellFormed()).isFalse();
    }
}