package com.mganji.payment.controller;

import com.mganji.payment.config.MpesaProperties;
import com.mganji.payment.dto.StkCallbackPayload;
import com.mganji.payment.service.PaymentStateService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * Receives the result of an STK push from Safaricom. This is the ONLY way a payment becomes SUCCESS.
 * The secret in the URL stops anyone who does not know the callback address from posting fake results,
 * and an optional IP allowlist narrows it further.
 */
@RestController
public class MpesaCallbackController {

    private static final Logger log = LoggerFactory.getLogger(MpesaCallbackController.class);

    private final PaymentStateService paymentState;
    private final MpesaProperties props;

    public MpesaCallbackController(PaymentStateService paymentState, MpesaProperties props) {
        this.paymentState = paymentState;
        this.props = props;
    }

    @PostMapping(MpesaProperties.STK_CALLBACK_PATH + "/{token}")
    public ResponseEntity<Map<String, Object>> stkCallback(@PathVariable String token,
                                                           @RequestBody StkCallbackPayload payload,
                                                           HttpServletRequest request) {
        if (!tokenMatches(token) || !ipAllowed(request)) {
            log.warn("Rejected STK callback from {}", request.getRemoteAddr());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("ResultCode", 1, "ResultDesc", "Rejected"));
        }

        StkCallbackPayload.StkCallback callback = payload.body() == null ? null : payload.body().stkCallback();
        if (callback == null || !callback.isWellFormed()) {
            log.warn("Malformed STK callback ignored");
            return ResponseEntity.badRequest().body(Map.of("ResultCode", 1, "ResultDesc", "Malformed"));
        }

        log.info("STK callback received checkoutRequestId={} resultCode={}",
                callback.checkoutRequestId(), callback.resultCode());
        paymentState.applyResult(callback.toResult(), "callback");

        // Always acknowledge quickly, duplicates and unknown ids are handled inside applyResult.
        return ResponseEntity.ok(Map.of("ResultCode", 0, "ResultDesc", "Accepted"));
    }

    private boolean tokenMatches(String token) {
        return MessageDigest.isEqual(
                props.callbackSecret().getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8));
    }

    private boolean ipAllowed(HttpServletRequest request) {
        return props.callbackAllowedIps().isEmpty() || props.callbackAllowedIps().contains(request.getRemoteAddr());
    }
}
