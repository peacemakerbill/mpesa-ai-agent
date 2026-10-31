package com.mganji.payment.controller;

import com.mganji.payment.config.CallbackGuard;
import com.mganji.payment.config.MpesaProperties;
import com.mganji.payment.dto.DarajaResultPayload;
import com.mganji.payment.service.OperationStateService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Results and queue timeouts for B2C, transaction status, account balance and reversal. */
@RestController
public class DarajaCallbackController {

    private static final Logger log = LoggerFactory.getLogger(DarajaCallbackController.class);

    private final CallbackGuard guard;
    private final OperationStateService state;

    public DarajaCallbackController(CallbackGuard guard, OperationStateService state) {
        this.guard = guard;
        this.state = state;
    }

    @PostMapping(MpesaProperties.OPS_RESULT_PATH + "/{token}")
    public ResponseEntity<Map<String, Object>> result(@PathVariable String token,
                                                      @RequestBody DarajaResultPayload payload,
                                                      HttpServletRequest request) {
        return handle(token, payload, request, false);
    }

    @PostMapping(MpesaProperties.OPS_TIMEOUT_PATH + "/{token}")
    public ResponseEntity<Map<String, Object>> timeout(@PathVariable String token,
                                                       @RequestBody DarajaResultPayload payload,
                                                       HttpServletRequest request) {
        return handle(token, payload, request, true);
    }

    private ResponseEntity<Map<String, Object>> handle(String token, DarajaResultPayload payload,
                                                       HttpServletRequest request, boolean timedOut) {
        if (!guard.allowed(token, request)) {
            log.warn("Rejected Daraja callback from {}", request.getRemoteAddr());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("ResultCode", 1, "ResultDesc", "Rejected"));
        }
        DarajaResultPayload.Result result = payload.result();
        if (result == null || !result.isWellFormed()) {
            log.warn("Malformed Daraja callback ignored");
            return ResponseEntity.badRequest().body(Map.of("ResultCode", 1, "ResultDesc", "Malformed"));
        }
        log.info("Daraja {} callback conversation={} resultCode={}",
                timedOut ? "timeout" : "result", result.originatorConversationId(), result.resultCode());
        state.applyResult(result, timedOut);
        return ResponseEntity.ok(Map.of("ResultCode", 0, "ResultDesc", "Accepted"));
    }
}
