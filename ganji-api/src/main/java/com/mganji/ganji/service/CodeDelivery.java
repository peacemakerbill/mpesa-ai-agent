package com.mganji.ganji.service;

import com.mganji.ganji.config.GanjiProperties;
import com.mganji.ganji.config.SmsProperties;
import com.mganji.ganji.exception.CodeDeliveryException;
import com.mganji.ganji.util.Phones;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;

/**
 * Gets a sign-in code to the person who asked for it. With no WhatsApp in the middle, this is the only proof that
 * a phone number belongs to whoever is typing it, so the way of delivery is a deliberate setting:
 *
 *  - log:  the code is written to this service's log, for a developer sitting at the server. The default.
 *  - dev:  the code is also returned to the app, so a test console can sign in on its own. Never use on a real system.
 *  - sms:  the code is sent as a text message through Africa's Talking.
 */
@Service
public class CodeDelivery {

    private static final Logger log = LoggerFactory.getLogger(CodeDelivery.class);

    public enum Mode { LOG, DEV, SMS }

    /** devCode is only filled in dev mode. */
    public record Result(String devCode) {
    }

    private final Mode mode;
    private final SmsProperties sms;
    private final String serviceName;

    public CodeDelivery(GanjiProperties props, SmsProperties sms, @Value("${ganji.app-name:M-Ganji}") String appName) {
        this.mode = parse(props.codeDelivery());
        this.sms = sms;
        this.serviceName = appName;
    }

    public Mode mode() {
        return mode;
    }

    static Mode parse(String value) {
        if (value == null || value.isBlank()) {
            return Mode.LOG;
        }
        return switch (value.strip().toLowerCase()) {
            case "dev" -> Mode.DEV;
            case "sms" -> Mode.SMS;
            default -> Mode.LOG;
        };
    }

    public Result deliver(String phone, String code, Duration validFor) {
        switch (mode) {
            case DEV -> {
                log.warn("DEV sign-in code for {} is {} (returned to the app because code-delivery is dev)", Phones.mask(phone), code);
                return new Result(code);
            }
            case SMS -> {
                sendSms(phone, serviceName + " code: " + code + ". It works for " + validFor.toMinutes()
                        + " minutes. Never share it with anyone.");
                return new Result(null);
            }
            default -> {
                log.warn("Sign-in code for {} is {} (code-delivery is log, so it is only written here)", Phones.mask(phone), code);
                return new Result(null);
            }
        }
    }

    private void sendSms(String phone, String message) {
        if (sms.apiKey() == null || sms.apiKey().isBlank()) {
            throw new CodeDeliveryException("SMS is switched on but SMS_API_KEY is not set");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", sms.username());
        form.add("to", "+" + phone);
        form.add("message", message);
        if (sms.senderId() != null && !sms.senderId().isBlank()) {
            form.add("from", sms.senderId());
        }
        try {
            RestClient.builder().baseUrl(sms.baseUrl()).build().post().uri("/version1/messaging")
                    .header("apiKey", sms.apiKey())
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.error("Could not send the sign-in SMS to {}: {}", Phones.mask(phone), e.getMessage());
            throw new CodeDeliveryException("The code could not be sent right now. Please try again in a moment.");
        }
    }
}
