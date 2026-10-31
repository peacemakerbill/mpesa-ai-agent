package com.mganji.ganji.controller;

import com.mganji.ganji.config.DeviceAuthFilter;
import com.mganji.ganji.config.DevicePrincipal;
import com.mganji.ganji.config.GanjiProperties;
import com.mganji.ganji.dto.CodeRequestResponse;
import com.mganji.ganji.dto.LinkCodeResponse;
import com.mganji.ganji.dto.PhoneRequest;
import com.mganji.ganji.dto.TokenResponse;
import com.mganji.ganji.dto.VerifyRequest;
import com.mganji.ganji.exception.TooManyRequestsException;
import com.mganji.ganji.service.CodeDelivery;
import com.mganji.ganji.service.LinkService;
import com.mganji.ganji.service.SlidingWindowLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mganji.ganji.util.Phones;

import java.time.Duration;

/** The two public endpoints that need no token: asking for a sign-in code, and exchanging it for a token. */
@RestController
@RequestMapping("/api")
public class AuthController {

    private static final Duration WINDOW = Duration.ofMinutes(10);

    private final LinkService links;
    private final SlidingWindowLimiter limiter;
    private final CodeDelivery delivery;
    private final GanjiProperties props;

    public AuthController(LinkService links, SlidingWindowLimiter limiter, CodeDelivery delivery, GanjiProperties props) {
        this.links = links;
        this.limiter = limiter;
        this.delivery = delivery;
        this.props = props;
    }

    /** Sends a six digit code to the number. The answer is the same whether or not the number was seen before. */
    @PostMapping("/auth/request-code")
    public CodeRequestResponse requestCode(@Valid @RequestBody PhoneRequest request, HttpServletRequest http) {
        String phone = Phones.normalize(request.phone());
        if (!limiter.allow("code-ip:" + http.getRemoteAddr(), 30, WINDOW)) {
            throw new TooManyRequestsException("Too many codes were requested. Please wait a few minutes.");
        }
        LinkCodeResponse issued = links.issue(phone);
        CodeDelivery.Result result = delivery.deliver(phone, issued.code(), props.linkCodeTtl());
        return new CodeRequestResponse(issued.expiresAt(), result.devCode());
    }

    @PostMapping("/auth/verify")
    public TokenResponse verify(@Valid @RequestBody VerifyRequest request, HttpServletRequest http) {
        // Two limits: per caller address (generous, mobile networks share addresses) and per phone number
        if (!limiter.allow("ip:" + http.getRemoteAddr(), 120, WINDOW) || !limiter.allow("phone:" + request.phone(), 15, WINDOW)) {
            throw new TooManyRequestsException("Too many attempts. Please wait a few minutes.");
        }
        return links.verify(request);
    }

    /** Sign this phone out. */
    @DeleteMapping("/devices/me")
    public ResponseEntity<Void> signOut(@RequestAttribute(DeviceAuthFilter.PRINCIPAL) DevicePrincipal who) {
        links.revoke(who.phone(), who.deviceId());
        return ResponseEntity.noContent().build();
    }
}
