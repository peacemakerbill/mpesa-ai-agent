package com.mganji.payment.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Decides whether a request to a Safaricom callback URL is genuine: secret in the path, optional IP list. */
@Component
public class CallbackGuard {

    private final MpesaProperties props;

    public CallbackGuard(MpesaProperties props) {
        this.props = props;
    }

    public boolean allowed(String token, HttpServletRequest request) {
        return tokenMatches(token) && ipAllowed(request);
    }

    private boolean tokenMatches(String token) {
        return token != null && MessageDigest.isEqual(
                props.callbackSecret().getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8));
    }

    private boolean ipAllowed(HttpServletRequest request) {
        return props.callbackAllowedIps().isEmpty() || props.callbackAllowedIps().contains(request.getRemoteAddr());
    }
}
