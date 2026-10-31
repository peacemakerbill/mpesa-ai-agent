package com.mganji.payment.service;

import com.mganji.payment.config.BusinessProperties;
import com.mganji.payment.config.DarajaOpsProperties;
import com.mganji.payment.config.MpesaProperties;
import com.mganji.payment.dto.DarajaAsyncResponse;
import com.mganji.payment.dto.QrApiResponse;
import com.mganji.payment.exception.MpesaApiException;
import com.mganji.payment.exception.OperationNotConfiguredException;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the request bodies for the Daraja APIs beyond STK Push. Authentication, HTTP and error handling
 * are shared with {@link MpesaClient}. Check the exact paths and fields against your Daraja portal, since
 * Safaricom versions these endpoints (for example B2C v1 and v3).
 */
@Service
public class MpesaOperationsClient {

    private final MpesaClient mpesa;
    private final MpesaProperties props;
    private final DarajaOpsProperties ops;
    private final SecurityCredentialService credentials;
    private final BusinessProperties business;

    public MpesaOperationsClient(MpesaClient mpesa,
                                 MpesaProperties props,
                                 DarajaOpsProperties ops,
                                 SecurityCredentialService credentials,
                                 BusinessProperties business) {
        this.mpesa = mpesa;
        this.props = props;
        this.ops = ops;
        this.credentials = credentials;
        this.business = business;
    }

    /** Fails fast, before anything is saved, when the initiator or certificate is missing. */
    public void requireInitiator() {
        credentials.initiatorName();
        credentials.securityCredential();
    }

    /** B2C: pay money from the business account to a customer's M-Pesa. */
    public DarajaAsyncResponse b2c(String originatorConversationId, int amount, String phone,
                                   String remarks, String occasion) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("OriginatorConversationID", originatorConversationId);
        body.put("InitiatorName", credentials.initiatorName());
        body.put("SecurityCredential", credentials.securityCredential());
        body.put("CommandID", "BusinessPayment");
        body.put("Amount", amount);
        body.put("PartyA", props.shortcode());
        body.put("PartyB", phone);
        body.put("Remarks", truncate(remarks, 100, "Payment"));
        body.put("QueueTimeOutURL", props.opsTimeoutUrl());
        body.put("ResultURL", props.opsResultUrl());
        body.put("Occassion", truncate(occasion, 100, ""));   // Daraja really spells it this way
        return mpesa.postJson("B2C payment", ops.b2cPath(), body, DarajaAsyncResponse.class);
    }

    /** Look up any transaction by its M-Pesa receipt number. The answer arrives on the result URL. */
    public DarajaAsyncResponse transactionStatus(String receipt, String remarks) {
        Map<String, Object> body = initiatorBody("Initiator");
        body.put("CommandID", "TransactionStatusQuery");
        body.put("TransactionID", receipt);
        body.put("PartyA", props.shortcode());
        body.put("IdentifierType", "4");
        body.put("ResultURL", props.opsResultUrl());
        body.put("QueueTimeOutURL", props.opsTimeoutUrl());
        body.put("Remarks", truncate(remarks, 100, "Status check"));
        body.put("Occasion", "");
        return mpesa.postJson("Transaction status", "/mpesa/transactionstatus/v1/query", body, DarajaAsyncResponse.class);
    }

    /** Ask for the business account balance. The answer arrives on the result URL. */
    public DarajaAsyncResponse accountBalance(String remarks) {
        Map<String, Object> body = initiatorBody("Initiator");
        body.put("CommandID", "AccountBalance");
        body.put("PartyA", props.shortcode());
        body.put("IdentifierType", "4");
        body.put("Remarks", truncate(remarks, 100, "Balance check"));
        body.put("QueueTimeOutURL", props.opsTimeoutUrl());
        body.put("ResultURL", props.opsResultUrl());
        return mpesa.postJson("Account balance", "/mpesa/accountbalance/v1/query", body, DarajaAsyncResponse.class);
    }

    /** Reverse a customer to business transaction. */
    public DarajaAsyncResponse reversal(String receipt, int amount, String remarks) {
        Map<String, Object> body = initiatorBody("Initiator");
        body.put("CommandID", "TransactionReversal");
        body.put("TransactionID", receipt);
        body.put("Amount", amount);
        body.put("ReceiverParty", props.shortcode());
        body.put("RecieverIdentifierType", "11");   // Daraja really spells it this way
        body.put("ResultURL", props.opsResultUrl());
        body.put("QueueTimeOutURL", props.opsTimeoutUrl());
        body.put("Remarks", truncate(remarks, 100, "Reversal"));
        body.put("Occasion", "");
        return mpesa.postJson("Reversal", "/mpesa/reversal/v1/request", body, DarajaAsyncResponse.class);
    }

    /** One time setup (per shortcode): tell Safaricom where to send direct Paybill and Till payments. */
    public DarajaAsyncResponse registerC2bUrls() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ShortCode", props.shortcode());
        body.put("ResponseType", ops.c2bResponseType());
        body.put("ConfirmationURL", props.c2bConfirmationUrl());
        body.put("ValidationURL", props.c2bValidationUrl());
        return mpesa.postJson("C2B register URL", ops.c2bRegisterPath(), body, DarajaAsyncResponse.class);
    }

    /** Dynamic M-Pesa QR code. Returns the PNG as Base64. */
    public String generateQr(int amount, String reference) {
        String merchant = ops.qrMerchantName() != null && !ops.qrMerchantName().isBlank()
                ? ops.qrMerchantName() : business.name();
        String cpi = ops.qrCpi() != null && !ops.qrCpi().isBlank() ? ops.qrCpi() : props.shortcode();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("MerchantName", truncate(merchant, 26, "Merchant"));
        body.put("RefNo", reference);
        body.put("Amount", amount);
        body.put("TrxCode", ops.qrTrxCode());
        body.put("CPI", cpi);
        body.put("Size", "300");
        QrApiResponse response = mpesa.postJson("QR code", "/mpesa/qrcode/v1/generate", body, QrApiResponse.class);
        if (response.qrCode() == null || response.qrCode().isBlank()) {
            throw new MpesaApiException(response.responseCode(), response.responseDescription());
        }
        return response.qrCode();
    }

    private Map<String, Object> initiatorBody(String initiatorKey) {
        if (initiatorKey == null) {
            throw new OperationNotConfiguredException("Initiator key missing");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(initiatorKey, credentials.initiatorName());
        body.put("SecurityCredential", credentials.securityCredential());
        return body;
    }

    private static String truncate(String value, int max, String fallback) {
        String text = value == null || value.isBlank() ? fallback : value.trim();
        return text.length() <= max ? text : text.substring(0, max);
    }
}
