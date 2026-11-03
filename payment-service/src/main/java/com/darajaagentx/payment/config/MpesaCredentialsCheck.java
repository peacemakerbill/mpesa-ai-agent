package com.darajaagentx.payment.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Lets payment-service start without Daraja credentials and says so in the log instead of refusing to boot.
 *
 * <p>Calls that need Safaricom (STK Push, refunds, payouts, QR codes) fail until MPESA_CONSUMER_KEY, MPESA_CONSUMER_SECRET and
 * MPESA_PASSKEY are set in payment-service/.env. Everything else in the service still runs.
 */
@Component
public class MpesaCredentialsCheck {

    private static final Logger log = LoggerFactory.getLogger(MpesaCredentialsCheck.class);

    private final MpesaProperties props;

    public MpesaCredentialsCheck(MpesaProperties props) {
        this.props = props;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warnAtStartup() {
        List<String> missing = missingCredentials(props);
        if (!missing.isEmpty()) {
            log.warn("Daraja credentials are not set: {}. payment-service is running, but every call to Safaricom (STK Push, refunds, payouts, QR) "
                    + "will fail until you set them in payment-service/.env and restart.", String.join(", ", missing));
        }
        if (callbacksUnreachable(props)) {
            log.warn("PUBLIC_BASE_URL is {}. Safaricom cannot reach that address, so payment results will not arrive. "
                    + "Use a public HTTPS address, or a tunnel while developing.", props.publicBaseUrl());
        }
    }

    /** The environment variable names of the Daraja credentials that are empty. */
    static List<String> missingCredentials(MpesaProperties p) {
        List<String> missing = new ArrayList<>();
        if (blank(p.consumerKey())) {
            missing.add("MPESA_CONSUMER_KEY");
        }
        if (blank(p.consumerSecret())) {
            missing.add("MPESA_CONSUMER_SECRET");
        }
        if (blank(p.passkey())) {
            missing.add("MPESA_PASSKEY");
        }
        return missing;
    }

    /** True when the public address is still the placeholder or points at this machine. */
    static boolean callbacksUnreachable(MpesaProperties p) {
        String url = p.publicBaseUrl() == null ? "" : p.publicBaseUrl().toLowerCase();
        return url.isBlank() || url.contains("example.com") || url.contains("localhost") || url.contains("127.0.0.1");
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}