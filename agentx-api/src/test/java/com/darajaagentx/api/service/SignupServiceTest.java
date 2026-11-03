package com.darajaagentx.api.service;

import com.darajaagentx.api.config.ProfileProperties;
import com.darajaagentx.api.config.AgentXProperties;
import com.darajaagentx.api.dto.CodeRequestResponse;
import com.darajaagentx.api.dto.LinkCodeResponse;
import com.darajaagentx.api.dto.SignupRequest;
import com.darajaagentx.api.dto.TokenResponse;
import com.darajaagentx.api.dto.VerifyRequest;
import com.darajaagentx.api.exception.AuthFailedException;
import com.darajaagentx.api.exception.InvalidActionRequestException;
import com.darajaagentx.api.model.UserProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Sign up: details wait, the SMS code proves the phone, the email gets its own code. */
class SignupServiceTest {

    static final String PHONE = "254712345678";

    /** A text message that the stand-in CodeDelivery was asked to send. */
    private record SmsSent(String phone, String code) { }

    private final Fakes.Rig rig = new Fakes.Rig();
    /** The one time codes the stand-in LinkService has issued and not yet accepted, by phone. */
    private final Map<String, String> codes = new HashMap<>();
    private final List<SmsSent> texts = new ArrayList<>();
    private final LinkService links = mock(LinkService.class);
    private final CodeDelivery delivery = mock(CodeDelivery.class);
    /** SignupService only reads how long a code lives, so the many other settings are left out. */
    private final AgentXProperties agentx = mock(AgentXProperties.class);
    private SignupService signup;

    @BeforeEach
    void standIns() {
        when(agentx.linkCodeTtl()).thenReturn(Duration.ofMinutes(10));
        AtomicInteger issued = new AtomicInteger();
        when(links.issue(anyString())).thenAnswer(call -> {
            String phone = call.getArgument(0);
            String code = String.format("%06d", 100_000 + issued.incrementAndGet());
            codes.put(phone, code);
            return new LinkCodeResponse(code, Instant.now().plusSeconds(600));
        });
        when(links.verify(any(VerifyRequest.class))).thenAnswer(call -> {
            VerifyRequest r = call.getArgument(0);
            if (!r.code().equals(codes.get(r.phone()))) {
                throw new AuthFailedException("That code is not valid or has expired. Ask for a new one.");
            }
            codes.remove(r.phone());
            return new TokenResponse("token-for-" + r.phone() + "-" + r.deviceId(), r.phone(), Instant.now().plusSeconds(3600));
        });
        when(delivery.deliver(anyString(), anyString(), any(Duration.class))).thenAnswer(call -> {
            texts.add(new SmsSent(call.getArgument(0), call.getArgument(1)));
            return new CodeDelivery.Result(null);
        });
        signup = build(true);
    }

    private SignupService build(boolean enabled) {
        return new SignupService(rig.profiles.repo(), links, delivery, rig.service,
                new ProfileProperties(enabled, Duration.ofMinutes(15), 200), agentx, rig.clock);
    }

    private static SignupRequest request(String phone, String email) {
        return new SignupRequest(phone, "Amina", "Wanjiru", "Otieno", email);
    }

    private static VerifyRequest verify(String phone, String code) {
        return new VerifyRequest(phone, code, "device-1234", "Pixel 8");
    }

    @Test
    void theDetailsWaitUntilTheSmsCodeIsTyped() {
        CodeRequestResponse sent = signup.request(request("0712 345 678", "amina@example.com"));
        assertThat(sent.expiresAt()).isNotNull();
        assertThat(texts).hasSize(1);
        assertThat(texts.get(0).phone()).isEqualTo(PHONE);

        UserProfile p = rig.profiles.repo().findByPhone(PHONE).orElseThrow();
        assertThat(p.getStatus().name()).isEqualTo("PENDING");
        assertThat(p.getFirstName()).isEqualTo("Amina");
        assertThat(p.getMiddleName()).isEqualTo("Wanjiru");
        assertThat(p.getEmail()).isNull();                       // nothing is trusted yet
        assertThat(p.getPendingEmail()).isEqualTo("amina@example.com");
        assertThat(rig.mailbox.sent).isEmpty();                  // and nobody is emailed before the phone is proven
    }

    @Test
    void theRightCodeCreatesTheAccountSignsTheDeviceInAndEmailsTheSecondCode() {
        signup.request(request(PHONE, "amina@example.com"));
        String sms = codes.get(PHONE);
        TokenResponse token = signup.verify(verify(PHONE, sms));
        assertThat(token.token()).startsWith("token-for-" + PHONE);
        UserProfile p = rig.profiles.repo().findByPhone(PHONE).orElseThrow();
        assertThat(p.getStatus().name()).isEqualTo("ACTIVE");
        assertThat(p.getSignupExpiresAt()).isNull();
        // the email is not verified yet, its own code is on the way
        assertThat(p.isEmailVerified()).isFalse();
        assertThat(rig.mailbox.sent).hasSize(1);
        assertThat(rig.mailbox.sent.get(0).to()).containsExactly("amina@example.com");
        rig.service.verifyEmail(PHONE, rig.mailbox.lastCode());
        assertThat(rig.service.view(PHONE).emailVerified()).isTrue();
    }

    @Test
    void anEmailIsOptional() {
        signup.request(request(PHONE, null));
        signup.verify(verify(PHONE, codes.get(PHONE)));
        assertThat(rig.service.view(PHONE).complete()).isTrue();
        assertThat(rig.mailbox.sent).isEmpty();
    }

    @Test
    void aWrongCodeLeavesTheSignUpWaiting() {
        signup.request(request(PHONE, null));
        assertThatThrownBy(() -> signup.verify(verify(PHONE, "999999"))).isInstanceOf(AuthFailedException.class);
        assertThat(rig.profiles.repo().findByPhone(PHONE).orElseThrow().getStatus().name()).isEqualTo("PENDING");
        signup.verify(verify(PHONE, codes.get(PHONE)));
        assertThat(rig.profiles.repo().findByPhone(PHONE).orElseThrow().getStatus().name()).isEqualTo("ACTIVE");
    }

    @Test
    void aSignUpThatWaitsTooLongMustStartAgain() {
        signup.request(request(PHONE, null));
        rig.clock.advance(Duration.ofMinutes(16));
        assertThatThrownBy(() -> signup.verify(verify(PHONE, codes.get(PHONE)))).hasMessageContaining("took too long");
    }

    @Test
    void verifyingWithoutStartingIsRefused() {
        assertThatThrownBy(() -> signup.verify(verify(PHONE, "123456"))).hasMessageContaining("no sign up waiting");
    }

    @Test
    void aNumberThatAlreadyHasAnAccountIsToldToSignIn() {
        signup.request(request(PHONE, null));
        signup.verify(verify(PHONE, codes.get(PHONE)));
        assertThatThrownBy(() -> signup.request(request(PHONE, null))).hasMessageContaining("Sign in instead");
    }

    @Test
    void startingAgainReplacesTheEarlierDetails() {
        signup.request(request(PHONE, "first@example.com"));
        signup.request(new SignupRequest(PHONE, "Brian", null, "Kamau", "second@example.com"));
        UserProfile p = rig.profiles.repo().findByPhone(PHONE).orElseThrow();
        assertThat(p.getFirstName()).isEqualTo("Brian");
        assertThat(p.getMiddleName()).isNull();
        assertThat(p.getPendingEmail()).isEqualTo("second@example.com");
        assertThat(rig.profiles.rows).hasSize(1);
    }

    @Test
    void badDetailsAreRefusedBeforeAnyCodeIsSent() {
        assertThatThrownBy(() -> signup.request(new SignupRequest(PHONE, "", null, "Otieno", null))).hasMessageContaining("first name");
        assertThatThrownBy(() -> signup.request(new SignupRequest(PHONE, "Amina", null, "", null))).hasMessageContaining("last name");
        assertThatThrownBy(() -> signup.request(new SignupRequest(PHONE, "Am<b>", null, "Otieno", null))).hasMessageContaining("letters");
        assertThatThrownBy(() -> signup.request(new SignupRequest(PHONE, "Amina", null, "Otieno", "nope"))).hasMessageContaining("not a valid email");
        assertThatThrownBy(() -> signup.request(new SignupRequest("12345", "Amina", null, "Otieno", null))).isInstanceOf(InvalidActionRequestException.class);
        assertThat(texts).isEmpty();
        assertThat(rig.profiles.rows).isEmpty();
    }

    @Test
    void signUpCanBeClosed() {
        SignupService closed = build(false);
        assertThatThrownBy(() -> closed.request(request(PHONE, null))).hasMessageContaining("closed");
    }

    @Test
    void aFailingMailServerDoesNotStopTheAccountBeingMade() {
        signup.request(request(PHONE, "amina@example.com"));
        rig.mailbox.fail = true;
        TokenResponse token = signup.verify(verify(PHONE, codes.get(PHONE)));
        assertThat(token.token()).isNotBlank();
        assertThat(rig.profiles.repo().findByPhone(PHONE).orElseThrow().getStatus().name()).isEqualTo("ACTIVE");
    }
}