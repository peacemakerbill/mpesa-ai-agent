package com.darajaagentx.api.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The paths and JSON bodies for each Daraja API, built from plain values. Nothing here calls the network or reads a
 * secret from storage, so it can be checked on its own. The spellings Daraja really uses are kept, including
 * "Occassion" for B2C and B2Pochi and "RecieverIdentifierType".
 *
 * <p>STK Push, STK query, B2C, status, balance, reversal and QR follow the calls payment-service already makes.
 * B2Pochi and Pull follow Safaricom's published pages. B2B follows the usual Daraja shape and has not been run
 * against a live shortcode.
 */
public final class DarajaRequests {

    public record Call(String path, Map<String, Object> body) {
    }

    private static final ZoneId NAIROBI = ZoneId.of("Africa/Nairobi");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private DarajaRequests() {
    }

    public static Call stkPush(ResolvedCredentials c, String phone, int amount, String reference, String description,
                               String callbackUrl, Instant now) {
        String stamp = STAMP.format(now.atZone(NAIROBI));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("BusinessShortCode", c.shortcode());
        body.put("Password", password(c, stamp));
        body.put("Timestamp", stamp);
        body.put("TransactionType", c.stkTransactionType());
        body.put("Amount", amount);
        body.put("PartyA", phone);
        body.put("PartyB", c.partyB());
        body.put("PhoneNumber", phone);
        body.put("CallBackURL", callbackUrl);
        body.put("AccountReference", reference);
        body.put("TransactionDesc", description);
        return new Call("/mpesa/stkpush/v1/processrequest", body);
    }

    public static Call stkQuery(ResolvedCredentials c, String checkoutRequestId, Instant now) {
        String stamp = STAMP.format(now.atZone(NAIROBI));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("BusinessShortCode", c.shortcode());
        body.put("Password", password(c, stamp));
        body.put("Timestamp", stamp);
        body.put("CheckoutRequestID", checkoutRequestId);
        return new Call("/mpesa/stkpushquery/v1/query", body);
    }

    /** Buy Goods QR pays the till (code BG), a Paybill QR pays the Paybill with the reference as the account (code PB). */
    public static Call qr(ResolvedCredentials c, String merchantName, String reference, int amount) {
        boolean buyGoods = c.shortcodeType() == com.darajaagentx.api.model.ShortcodeType.BUY_GOODS;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("MerchantName", merchantName);
        body.put("RefNo", reference);
        body.put("Amount", amount);
        body.put("TrxCode", buyGoods ? "BG" : "PB");
        body.put("CPI", c.partyB());
        body.put("Size", "300");
        return new Call("/mpesa/qrcode/v1/generate", body);
    }

    public static Call b2c(ResolvedCredentials c, String securityCredential, String command, String conversationId, String phone,
                           int amount, String remarks, String occasion, String resultUrl, String timeoutUrl) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("OriginatorConversationID", conversationId);
        body.put("InitiatorName", c.initiatorName());
        body.put("SecurityCredential", securityCredential);
        body.put("CommandID", command);
        body.put("Amount", amount);
        body.put("PartyA", c.shortcode());
        body.put("PartyB", phone);
        body.put("Remarks", remarks);
        body.put("QueueTimeOutURL", timeoutUrl);
        body.put("ResultURL", resultUrl);
        body.put("Occassion", occasion);
        return new Call("/mpesa/b2c/v3/paymentrequest", body);
    }

    public static Call b2pochi(ResolvedCredentials c, String securityCredential, String conversationId, String phone,
                               int amount, String remarks, String occasion, String resultUrl, String timeoutUrl) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("OriginatorConversationID", conversationId);
        body.put("InitiatorName", c.initiatorName());
        body.put("SecurityCredential", securityCredential);
        body.put("CommandID", "BusinessPayToPochi");
        body.put("Amount", amount);
        body.put("PartyA", c.shortcode());
        body.put("PartyB", phone);
        body.put("Remarks", remarks);
        body.put("QueueTimeOutURL", timeoutUrl);
        body.put("ResultURL", resultUrl);
        body.put("Occassion", occasion);
        return new Call("/mpesa/b2pochi/v1/paymentrequest", body);
    }

    /** @param command BusinessPayBill or BusinessBuyGoods. The account reference is used only for Paybill. */
    public static Call b2b(ResolvedCredentials c, String securityCredential, String command, String receiver,
                           String accountReference, int amount, String remarks, String resultUrl, String timeoutUrl) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("Initiator", c.initiatorName());
        body.put("SecurityCredential", securityCredential);
        body.put("CommandID", command);
        body.put("SenderIdentifierType", "4");
        body.put("RecieverIdentifierType", "4");
        body.put("Amount", amount);
        body.put("PartyA", c.shortcode());
        body.put("PartyB", receiver);
        if ("BusinessPayBill".equals(command)) {
            body.put("AccountReference", accountReference);
        }
        body.put("Remarks", remarks);
        body.put("QueueTimeOutURL", timeoutUrl);
        body.put("ResultURL", resultUrl);
        return new Call("/mpesa/b2b/v1/paymentrequest", body);
    }

    public static Call balance(ResolvedCredentials c, String securityCredential, String remarks, String resultUrl, String timeoutUrl) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("Initiator", c.initiatorName());
        body.put("SecurityCredential", securityCredential);
        body.put("CommandID", "AccountBalance");
        body.put("PartyA", c.shortcode());
        body.put("IdentifierType", "4");
        body.put("Remarks", remarks);
        body.put("QueueTimeOutURL", timeoutUrl);
        body.put("ResultURL", resultUrl);
        return new Call("/mpesa/accountbalance/v1/query", body);
    }

    public static Call status(ResolvedCredentials c, String securityCredential, String receipt, String remarks, String resultUrl, String timeoutUrl) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("Initiator", c.initiatorName());
        body.put("SecurityCredential", securityCredential);
        body.put("CommandID", "TransactionStatusQuery");
        body.put("TransactionID", receipt);
        body.put("PartyA", c.shortcode());
        body.put("IdentifierType", "4");
        body.put("ResultURL", resultUrl);
        body.put("QueueTimeOutURL", timeoutUrl);
        body.put("Remarks", remarks);
        body.put("Occasion", "");
        return new Call("/mpesa/transactionstatus/v1/query", body);
    }

    public static Call reversal(ResolvedCredentials c, String securityCredential, String receipt, int amount, String remarks,
                                String resultUrl, String timeoutUrl) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("Initiator", c.initiatorName());
        body.put("SecurityCredential", securityCredential);
        body.put("CommandID", "TransactionReversal");
        body.put("TransactionID", receipt);
        body.put("Amount", amount);
        body.put("ReceiverParty", c.shortcode());
        body.put("RecieverIdentifierType", "11");
        body.put("ResultURL", resultUrl);
        body.put("QueueTimeOutURL", timeoutUrl);
        body.put("Remarks", remarks);
        body.put("Occasion", "");
        return new Call("/mpesa/reversal/v1/request", body);
    }

    /** Safaricom's page lists this as a GET with a JSON body. It is sent as a POST, as most clients do, and not yet run live. */
    public static Call pull(ResolvedCredentials c, String start, String end, int offset) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ShortCode", c.shortcode());
        body.put("StartDate", start);
        body.put("EndDate", end);
        body.put("OffSetValue", Integer.toString(offset));
        return new Call("/pulltransactions/v1/query", body);
    }

    private static String password(ResolvedCredentials c, String stamp) {
        String raw = c.shortcode() + c.passkey() + stamp;
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
