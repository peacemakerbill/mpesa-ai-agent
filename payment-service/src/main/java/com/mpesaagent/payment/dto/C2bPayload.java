package com.mpesaagent.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.stream.Collectors;

/** Validation or confirmation request Safaricom sends when someone pays our Paybill or Till directly. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record C2bPayload(
        @JsonProperty("TransactionType") String transactionType,
        @JsonProperty("TransID") String transId,
        @JsonProperty("TransTime") String transTime,
        @JsonProperty("TransAmount") String transAmount,
        @JsonProperty("BusinessShortCode") String businessShortCode,
        @JsonProperty("BillRefNumber") String billRefNumber,
        @JsonProperty("InvoiceNumber") String invoiceNumber,
        @JsonProperty("OrgAccountBalance") String orgAccountBalance,
        @JsonProperty("ThirdPartyTransID") String thirdPartyTransId,
        @JsonProperty("MSISDN") String msisdn,
        @JsonProperty("FirstName") String firstName,
        @JsonProperty("MiddleName") String middleName,
        @JsonProperty("LastName") String lastName
) {
    public boolean isWellFormed() {
        return transId != null && !transId.isBlank() && amount() != null;
    }

    public BigDecimal amount() {
        try {
            return new BigDecimal(transAmount.trim());
        } catch (RuntimeException e) {
            return null;
        }
    }

    public String payerName() {
        return Arrays.stream(new String[]{firstName, middleName, lastName})
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(" "));
    }
}
