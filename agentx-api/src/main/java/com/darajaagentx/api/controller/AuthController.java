package com.darajaagentx.api.controller;

import com.darajaagentx.api.config.DeviceAuthFilter;
import com.darajaagentx.api.config.DevicePrincipal;
import com.darajaagentx.api.config.AgentXProperties;
import com.darajaagentx.api.dto.CodeRequestResponse;
import com.darajaagentx.api.dto.LinkCodeResponse;
import com.darajaagentx.api.dto.PhoneRequest;
import com.darajaagentx.api.dto.TokenResponse;
import com.darajaagentx.api.dto.VerifyRequest;
import com.darajaagentx.api.exception.TooManyRequestsException;
import com.darajaagentx.api.service.CodeDelivery;
import com.darajaagentx.api.service.CustomerMailer;
import com.darajaagentx.api.service.LinkService;
import com.darajaagentx.api.service.SlidingWindowLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.darajaagentx.api.util.Phones;

import java.time.Duration;

/** The two public endpoints that need no token: asking for a sign-in code, and exchanging it for a token. */
@RestController
@RequestMapping("/api")
public class AuthController {

    private static final Duration WINDOW = Duration.ofMinutes(10);

    private final LinkService links;
    private final SlidingWindowLimiter limiter;
    private final CodeDelivery delivery;
    private final AgentXProperties props;
    private final CustomerMailer mailer;

    public AuthController(LinkService links, SlidingWindowLimiter limiter, CodeDelivery delivery, AgentXProperties props, CustomerMailer mailer) {
        this.links = links;
        this.limiter = limiter;
        this.delivery = delivery;
        this.props = props;
        this.mailer = mailer;
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
        TokenResponse token = links.verify(request);
        // An email to the customer's verified address, at most once in a while. It never blocks or fails the sign in.
        mailer.signedIn(Phones.normalize(request.phone()), request.deviceName());
        return token;
    }

    /** Sign this phone out. */
    @DeleteMapping("/devices/me")
    public ResponseEntity<Void> signOut(@RequestAttribute(DeviceAuthFilter.PRINCIPAL) DevicePrincipal who) {
        links.revoke(who.phone(), who.deviceId());
        return ResponseEntity.noContent().build();
    }
}
