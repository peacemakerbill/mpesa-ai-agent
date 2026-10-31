package com.mganji.payment.dto;

import com.mganji.payment.dto.StkCallbackPayload.StkCallback;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class StkCallbackPayloadTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void parsesSuccessCallbackWithNumericResultCode() throws Exception {
        String json = """
                {"Body":{"stkCallback":{
                  "MerchantRequestID":"29115-34620561-1",
                  "CheckoutRequestID":"ws_CO_191220191020363925",
                  "ResultCode":0,
                  "ResultDesc":"The service request is processed successfully.",
                  "CallbackMetadata":{"Item":[
                    {"Name":"Amount","Value":1.00},
                    {"Name":"MpesaReceiptNumber","Value":"NLJ7RT61SV"},
                    {"Name":"TransactionDate","Value":20191219102115},
                    {"Name":"PhoneNumber","Value":254708374149}
                  ]}}}}
                """;

        StkCallback callback = mapper.readValue(json, StkCallbackPayload.class).body().stkCallback();

        assertThat(callback.isWellFormed()).isTrue();
        StkResult result = callback.toResult();
        assertThat(result.checkoutRequestId()).isEqualTo("ws_CO_191220191020363925");
        assertThat(result.resultCode()).isEqualTo("0");
        assertThat(result.receipt()).isEqualTo("NLJ7RT61SV");
        assertThat(result.amount()).isEqualTo(1);
    }

    @Test
    void parsesCancelledCallbackWithoutMetadata() throws Exception {
        String json = """
                {"Body":{"stkCallback":{
                  "MerchantRequestID":"8555-67195-1",
                  "CheckoutRequestID":"ws_CO_27072017154747416",
                  "ResultCode":1032,
                  "ResultDesc":"Request cancelled by user"}}}
                """;

        StkResult result = mapper.readValue(json, StkCallbackPayload.class).body().stkCallback().toResult();

        assertThat(result.resultCode()).isEqualTo("1032");
        assertThat(result.receipt()).isNull();
        assertThat(result.amount()).isNull();
    }

    @Test
    void acceptsAlphanumericResultCode() throws Exception {
        String json = """
                {"Body":{"stkCallback":{"CheckoutRequestID":"ws_CO_1","ResultCode":"SFC_IC0003","ResultDesc":"timeout"}}}
                """;

        StkCallback callback = mapper.readValue(json, StkCallbackPayload.class).body().stkCallback();

        assertThat(callback.resultCode()).isEqualTo("SFC_IC0003");
        assertThat(callback.isWellFormed()).isTrue();
    }

    @Test
    void flagsMalformedCallback() throws Exception {
        StkCallbackPayload empty = mapper.readValue("{}", StkCallbackPayload.class);
        assertThat(empty.body()).isNull();

        StkCallback missingId = mapper.readValue(
                "{\"Body\":{\"stkCallback\":{\"ResultCode\":0}}}", StkCallbackPayload.class).body().stkCallback();
        assertThat(missingId.isWellFormed()).isFalse();
    }
}