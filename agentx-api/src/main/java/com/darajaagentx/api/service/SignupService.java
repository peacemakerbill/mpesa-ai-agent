package com.darajaagentx.api.service;

import com.darajaagentx.api.config.AgentXProperties;
import com.darajaagentx.api.config.ProfileProperties;
import com.darajaagentx.api.dto.CodeRequestResponse;
import com.darajaagentx.api.dto.LinkCodeResponse;
import com.darajaagentx.api.dto.SignupRequest;
import com.darajaagentx.api.dto.TokenResponse;
import com.darajaagentx.api.dto.VerifyRequest;
import com.darajaagentx.api.exception.InvalidActionRequestException;
import com.darajaagentx.api.mail.MailDeliveryException;
import com.darajaagentx.api.model.ProfileStatus;
import com.darajaagentx.api.model.UserProfile;
import com.darajaagentx.api.repository.UserProfileRepository;
import com.darajaagentx.api.util.Emails;
import com.darajaagentx.api.util.PersonNames;
import com.darajaagentx.api.util.Phones;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Creating an account. Two steps, the same one time code as signing in:
 *
 * <ol>
 *   <li>{@link #request}: the person gives phone, names and (optionally) an email. The details wait in a PENDING profile and a 6 digit code goes to the phone by SMS.</li>
 *   <li>{@link #verify}: the person types the code. The phone is now proven, the profile becomes ACTIVE, a device is signed in (the answer is the usual token) and, when an email was given, a second code is emailed to verify it.</li>
 * </ol>
 *
 * Nothing about the person is trusted before the SMS code is typed. An email is trusted only after its own code is typed.
 */
@Service
public class SignupService {

    private static final Logger log = LoggerFactory.getLogger(SignupService.class);

    private final UserProfileRepository profiles;
    private final LinkService links;
    private final CodeDelivery delivery;
    private final ProfileService profileService;
    private final ProfileProperties props;
    private final Duration codeTtl;
    private final Clock clock;

    public SignupService(UserProfileRepository profiles, LinkService links, CodeDelivery delivery, ProfileService profileService,
                         ProfileProperties props, AgentXProperties agentx, Clock clock) {
        this.profiles = profiles;
        this.links = links;
        this.delivery = delivery;
        this.profileService = profileService;
        this.props = props;
        this.codeTtl = agentx.linkCodeTtl();
        this.clock = clock;
    }

    @Transactional
    public CodeRequestResponse request(SignupRequest r) {
        if (!props.signupEnabled()) {
            throw new InvalidActionRequestException("Sign up is closed right now. If you already have an account, sign in with your phone number.");
        }
        if (r == null) {
            throw new InvalidActionRequestException("Enter your details.");
        }
        String phone = Phones.normalize(r.phone());
        String first = PersonNames.required(r.firstName(), "first name");
        String last = PersonNames.required(r.lastName(), "last name");
        String middle = PersonNames.clean(r.middleName(), "middle name");
        String email = r.email() == null || r.email().isBlank() ? null : Emails.require(r.email());

        Optional<UserProfile> existing = profiles.findByPhone(phone);
        if (existing.isPresent() && existing.get().getStatus() == ProfileStatus.ACTIVE) {
            throw new InvalidActionRequestException("This phone number already has an account. Sign in instead.");
        }
        Instant now = clock.instant();
        LinkCodeResponse issued = links.issue(phone);           // rate limited per phone
        UserProfile p = existing.orElseGet(() -> new UserProfile(phone));
        p.setStatus(ProfileStatus.PENDING);
        p.setFirstName(first);
        p.setMiddleName(middle);
        p.setLastName(last);
        p.clearEmailCode();
        p.setPendingEmail(email);
        p.setSignupExpiresAt(now.plus(props.signupTtl()));
        profiles.save(p);
        CodeDelivery.Result sent = delivery.deliver(phone, issued.code(), codeTtl);
        return new CodeRequestResponse(issued.expiresAt(), sent.devCode());
    }

    @Transactional
    public TokenResponse verify(VerifyRequest r) {
        String phone = Phones.normalize(r.phone());
        UserProfile p = profiles.findByPhone(phone)
                .filter(x -> x.getStatus() == ProfileStatus.PENDING)
                .orElseThrow(() -> new InvalidActionRequestException("There is no sign up waiting for this number. Start again."));
        Instant now = clock.instant();
        if (p.getSignupExpiresAt() == null || !now.isBefore(p.getSignupExpiresAt())) {
            throw new InvalidActionRequestException("This sign up took too long. Start again.");
        }
        TokenResponse token = links.verify(new VerifyRequest(phone, r.code(), r.deviceId(), r.deviceName()));   // wrong code: AuthFailedException, the sign up keeps waiting
        String email = p.getPendingEmail();
        p.setStatus(ProfileStatus.ACTIVE);
        p.setSignupExpiresAt(null);
        p.clearEmailCode();
        profiles.save(p);
        if (email != null) {
            try {
                profileService.requestEmailCode(phone, email);
            } catch (MailDeliveryException | InvalidActionRequestException e) {
                // the account is made; the app offers "Verify your email" again from the profile
                log.warn("Could not send the first email code for {}: {}", Phones.mask(phone), e.getMessage());
            }
        }
        return token;
    }
}
