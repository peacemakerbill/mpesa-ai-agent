package com.darajaagentx.api.service;

import com.darajaagentx.api.config.AgentXProperties;
import com.darajaagentx.api.config.AppEnvironment;
import com.darajaagentx.api.config.CodeVisibility;
import com.darajaagentx.api.config.EnvironmentPolicy;
import com.darajaagentx.api.config.SmsProperties;
import com.darajaagentx.api.exception.CodeDeliveryException;
import com.darajaagentx.api.util.Phones;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Pattern;

/**
 * Gets a sign-in code to the person who asked for it. With no WhatsApp in the middle, this is the only proof that
 * a phone number belongs to whoever is typing it, so two settings decide what happens, and they are independent.
 *
 * How the code is delivered (CODE_DELIVERY):
 *  - sms:  sent as a text message through Africa's Talking.
 *  - log:  written to this service's log, for a developer sitting at the server. The default.
 *  - dev:  the old name for log with the code shown in the app. Still works.
 *
 * Whether the app also shows the code on screen (CODE_SHOW_IN_APP, see CodeVisibility):
 *  - on by default in development and staging, so a tester can sign in without a phone that receives texts;
 *  - never in production, whatever is set. Turn it off in staging as soon as SMS works.
 *
 * When the code is shown and the text message fails (a sandbox key, a provider outage), the sign-in still works,
 * because the person can read the code on screen. Without it, a failed text is reported as an error.
 *
 * A code that is shown in the app is not written to the log: the log only holds a code that has nowhere else to go.
 *
 * The Android app can read the text by itself, with no SMS permission, when the text ends with the 11 character hash
 * of the app (Google's SMS Retriever). The app sends its hash with the request, and it is added here.
 */
@Service
public class CodeDelivery {

    private static final Logger log = LoggerFactory.getLogger(CodeDelivery.class);

    public enum Mode { LOG, DEV, SMS }

    /**
     * @param devCode filled only when the app may show the code on screen
     * @param smsSent true when a text message was handed to the provider
     */
    public record Result(String devCode, boolean smsSent) {
        public Result(String devCode) {
            this(devCode, false);
        }
    }

    private final Mode mode;
    private final SmsProperties sms;
    private final String serviceName;
    private final boolean showInApp;

    @Autowired
    public CodeDelivery(AgentXProperties props, SmsProperties sms, @Value("${agentx.app-name:Daraja Agent-X}") String appName,
                        EnvironmentPolicy policy, @Value("${agentx.show-code-in-app:auto}") String showSetting) {
        this.mode = parse(props.codeDelivery());
        this.sms = sms;
        this.serviceName = appName;
        this.showInApp = CodeVisibility.parse(showSetting).shownIn(policy.env());
        if (showInApp) {
            log.warn("Sign-in codes are shown in the app ({}), so anyone who knows a phone number can sign in as it. "
                    + "Set CODE_SHOW_IN_APP=false once codes are sent by SMS.", policy.env().label());
        }
    }

    /** For tests and tools: a development copy that shows codes unless told not to. */
    CodeDelivery(AgentXProperties props, SmsProperties sms, String appName) {
        this(props, sms, appName, new EnvironmentPolicy(AppEnvironment.DEVELOPMENT, false), "auto");
    }

    public Mode mode() {
        return mode;
    }

    /** True when the answer to a code request carries the code, so the app can show it. */
    public boolean showsCodes() {
        return showInApp;
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

    private static final Pattern APP_HASH = Pattern.compile("^[A-Za-z0-9+/]{11}$");

    /** The hash if it has the right shape, otherwise null. A wrong hash must never stop a sign-in. */
    static String cleanHash(String appHash) {
        if (appHash == null) {
            return null;
        }
        String h = appHash.strip();
        return APP_HASH.matcher(h).matches() ? h : null;
    }

    public Result deliver(String phone, String code, Duration validFor) {
        return deliver(phone, code, validFor, null);
    }

    public Result deliver(String phone, String code, Duration validFor, String appHash) {
        return dispatch(phone, code, "sign-in", smsText(serviceName, code, validFor, cleanHash(appHash)));
    }

    /**
     * A code that approves one request. The text says what it approves, so a customer who did not ask for it
     * can see that something is wrong. It is delivered the same ways as a sign-in code.
     */
    public Result deliverApproval(String phone, String code, Duration validFor, String what) {
        return dispatch(phone, code, "approval", approvalSmsText(serviceName, code, validFor, what));
    }

    private Result dispatch(String phone, String code, String label, String smsMessage) {
        String shown = showInApp ? code : null;
        switch (mode) {
            case SMS -> {
                try {
                    sendSms(phone, smsMessage);
                    return new Result(shown, true);
                } catch (CodeDeliveryException e) {
                    if (shown == null) {
                        throw e;
                    }
                    log.warn("The {} SMS to {} was not sent ({}). The code is shown in the app instead.", label, Phones.mask(phone), e.getMessage());
                    return new Result(shown, false);
                }
            }
            default -> {
                // log (and the old name dev). The log holds the code only when nothing else shows it.
                if (showInApp) {
                    log.info("A {} code for {} was issued and is shown in the app (code-delivery is {})", label, Phones.mask(phone),
                            mode == Mode.DEV ? "dev" : "log");
                } else {
                    log.warn("{} code for {} is {} (code-delivery is {}, so it is only written here)", capitalise(label), Phones.mask(phone), code,
                            mode == Mode.DEV ? "dev" : "log");
                }
                return new Result(shown, false);
            }
        }
    }

    private static String capitalise(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** One text message of at most 160 characters. The description of the request is cut short first if it has to be. */
    static String approvalSmsText(String appName, String code, Duration validFor, String what) {
        String tail = ". Valid " + Math.max(1, validFor.toMinutes()) + " min. Never share it.";
        String head = appName + " approval code " + code + " for: ";
        String w = what == null ? "" : what.strip();
        int room = 160 - head.length() - tail.length();
        if (room < 12) {
            return appName + " approval code " + code + tail;
        }
        if (w.length() > room) {
            w = w.substring(0, Math.max(0, room - 3)).stripTrailing() + "...";
        }
        return head + w + tail;
    }

    /**
     * The text message. With an app hash it starts with the marker and ends with the hash on its own line, which is what
     * Android's SMS Retriever needs, and it stays within the 140 bytes it allows.
     */
    static String smsText(String appName, String code, Duration validFor, String appHash) {
        String full = appName + " code: " + code + ". It works for " + validFor.toMinutes() + " minutes. Never share it with anyone.";
        if (appHash == null) {
            return full;
        }
        String head = "<#> ";
        String tail = "\n" + appHash;
        String text = head + full + tail;
        if (fits(text)) {
            return text;
        }
        text = head + appName + " code: " + code + ". Never share it." + tail;
        if (fits(text)) {
            return text;
        }
        String name = appName.length() > 40 ? appName.substring(0, 40) : appName;
        return head + name + " code: " + code + tail;
    }

    private static boolean fits(String text) {
        return text.getBytes(StandardCharsets.UTF_8).length <= 140;
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
