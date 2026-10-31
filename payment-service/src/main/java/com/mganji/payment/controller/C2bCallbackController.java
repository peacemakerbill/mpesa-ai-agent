package com.mganji.payment.controller;

import com.mganji.payment.config.CallbackGuard;
import com.mganji.payment.config.MpesaProperties;
import com.mganji.payment.dto.C2bPayload;
import com.mganji.payment.service.C2bService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Safaricom calls these when a customer pays our Paybill or Till directly. */
@RestController
public class C2bCallbackController {

    private static final Logger log = LoggerFactory.getLogger(C2bCallbackController.class);

    private final CallbackGuard guard;
    private final C2bService c2b;

    public C2bCallbackController(CallbackGuard guard, C2bService c2b) {
        this.guard = guard;
        this.c2b = c2b;
    }

    /** Only called if validation is switched on for the shortcode. We accept every payment. */
    @PostMapping(MpesaProperties.C2B_VALIDATION_PATH + "/{token}")
    public ResponseEntity<Map<String, Object>> validation(@PathVariable String token,
                                                          @RequestBody C2bPayload payload,
                                                          HttpServletRequest request) {
        if (!guard.allowed(token, request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("ResultCode", "C2B00016", "ResultDesc", "Rejected"));
        }
        return ResponseEntity.ok(Map.of("ResultCode", "0", "ResultDesc", "Accepted"));
    }

    @PostMapping(MpesaProperties.C2B_CONFIRMATION_PATH + "/{token}")
    public ResponseEntity<Map<String, Object>> confirmation(@PathVariable String token,
                                                            @RequestBody C2bPayload payload,
                                                            HttpServletRequest request) {
        if (!guard.allowed(token, request)) {
            log.warn("Rejected C2B confirmation from {}", request.getRemoteAddr());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("ResultCode", "C2B00016", "ResultDesc", "Rejected"));
        }
        if (!payload.isWellFormed()) {
            log.warn("Malformed C2B confirmation ignored");
            return ResponseEntity.badRequest().body(Map.of("ResultCode", "C2B00012", "ResultDesc", "Malformed"));
        }
        try {
            c2b.recordConfirmation(payload);
        } catch (DataIntegrityViolationException e) {
            log.info("Duplicate C2B confirmation {} lost a race, already recorded", payload.transId());
        }
        return ResponseEntity.ok(Map.of("ResultCode", "0", "ResultDesc", "Success"));
    }
}
