package com.mganji.payment.dto;

/**
 * Normalised outcome of an STK Push, whether it came from the callback or from the STK query API.
 * {@code receipt} and {@code amount} are only known from callbacks.
 */
public record StkResult(
        String checkoutRequestId,
        String resultCode,
        String resultDesc,
        String receipt,
        Integer amount
) {
}
