package com.darajaagentx.api.dto;

/**
 * Asks for a QR code from the app. Nothing here is a secret.
 *
 * @param account      the saved business account that makes the call to Safaricom, and the one paid when payTo is blank
 * @param amount       whole shillings
 * @param reference    shown to the payer. For a Paybill it is the account number to pay into
 * @param merchantName name shown on the code, at most 26 characters. Blank uses the account name
 * @param payTo        account (default), till, paybill or phone
 * @param payToNumber  the Till or Paybill number, or the phone number, when payTo is not account
 */
public record QrCodeRequest(String account, Integer amount, String reference, String merchantName, String payTo, String payToNumber) {
}
