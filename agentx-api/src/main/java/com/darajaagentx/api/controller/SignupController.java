package com.darajaagentx.api.controller;

import com.darajaagentx.api.dto.CodeRequestResponse;
import com.darajaagentx.api.dto.SignupRequest;
import com.darajaagentx.api.dto.TokenResponse;
import com.darajaagentx.api.dto.VerifyRequest;
import com.darajaagentx.api.exception.TooManyRequestsException;
import com.darajaagentx.api.service.SignupService;
import com.darajaagentx.api.service.SlidingWindowLimiter;
import com.darajaagentx.api.util.Phones;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Creating an account. Both calls are public (no token), like signing in: the person is not signed in yet. They are
 * rate limited per caller address and per phone number.
 */
@RestController
@RequestMapping("/api/auth/signup")
public class SignupController {

    private static final Duration WINDOW = Duration.ofMinutes(10);

    private final SignupService signup;
    private final SlidingWindowLimiter limiter;

    public SignupController(SignupService signup, SlidingWindowLimiter limiter) {
        this.signup = signup;
        this.limiter = limiter;
    }

    /** Saves the details and sends a 6 digit code to the phone by SMS. */
    @PostMapping("/request")
    public CodeRequestResponse request(@Valid @RequestBody SignupRequest request, HttpServletRequest http) {
        if (!limiter.allow("signup-ip:" + http.getRemoteAddr(), 20, WINDOW)) {
            throw new TooManyRequestsException("Too many sign up attempts. Please wait a few minutes.");
        }
        return signup.request(request);
    }

    /** Checks the code. The answer is the same as signing in: a token for this device. */
    @PostMapping("/verify")
    public TokenResponse verify(@Valid @RequestBody VerifyRequest request, HttpServletRequest http) {
        if (!limiter.allow("signup-verify-ip:" + http.getRemoteAddr(), 120, WINDOW)
                || !limiter.allow("signup-verify-phone:" + safePhone(request.phone()), 15, WINDOW)) {
            throw new TooManyRequestsException("Too many attempts. Please wait a few minutes.");
        }
        return signup.verify(request);
    }

    private static String safePhone(String raw) {
        try {
            return Phones.normalize(raw);
        } catch (RuntimeException e) {
            return String.valueOf(raw);
        }
    }
}
