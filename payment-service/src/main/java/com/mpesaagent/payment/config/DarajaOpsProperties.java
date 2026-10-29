package com.mpesaagent.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the Daraja APIs beyond STK Push. All optional: the service starts without them and the
 * operations that need them answer 503 until they are filled in.
 *
 * The initiator is the M-Pesa portal user that is allowed to run B2C, balance, status and reversal.
 * Its password is encrypted with Safaricom's public certificate into the SecurityCredential, or you can
 * paste a ready made securityCredential instead of the password and certificate.
 */
@ConfigurationProperties(prefix = "mpesa.operations")
public record DarajaOpsProperties(
        String initiatorName,
        String initiatorPassword,
        String certificatePath,
        String securityCredential,
        String b2cPath,
        String c2bRegisterPath,
        String c2bResponseType,
        String qrMerchantName,
        String qrTrxCode,
        String qrCpi
) {
    public DarajaOpsProperties {
        b2cPath = blankTo(b2cPath, "/mpesa/b2c/v3/paymentrequest");
        c2bRegisterPath = blankTo(c2bRegisterPath, "/mpesa/c2b/v2/registerurl");
        c2bResponseType = blankTo(c2bResponseType, "Completed");
        qrTrxCode = blankTo(qrTrxCode, "PB");
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
