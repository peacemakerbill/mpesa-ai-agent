package com.mganji.ganji.service;

import com.mganji.ganji.config.GanjiProperties;
import com.mganji.ganji.dto.LinkCodeResponse;
import com.mganji.ganji.dto.TokenResponse;
import com.mganji.ganji.dto.VerifyRequest;
import com.mganji.ganji.exception.AuthFailedException;
import com.mganji.ganji.exception.TooManyRequestsException;
import com.mganji.ganji.model.Device;
import com.mganji.ganji.model.LinkCode;
import com.mganji.ganji.repository.DeviceRepository;
import com.mganji.ganji.repository.LinkCodeRepository;
import com.mganji.ganji.util.Hmac;
import com.mganji.ganji.util.Phones;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Proves that a phone owns a number. A six digit code is delivered to the number (see CodeDelivery) and the person
 * types it into the app. Codes are short, so they live ten minutes and allow only a few wrong tries.
 */
@Service
public class LinkService {

    static final String INVALID = "That code is not valid or has expired. Ask for a new one.";

    private final LinkCodeRepository codes;
    private final DeviceRepository devices;
    private final TokenService tokens;
    private final GanjiProperties props;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public LinkService(LinkCodeRepository codes, DeviceRepository devices, TokenService tokens,
                       GanjiProperties props, Clock clock) {
        this.codes = codes;
        this.devices = devices;
        this.tokens = tokens;
        this.props = props;
        this.clock = clock;
    }

    @Transactional
    public LinkCodeResponse issue(String rawPhone) {
        String phone = Phones.normalize(rawPhone);
        Instant now = clock.instant();
        if (codes.countByPhoneAndCreatedAtAfter(phone, now.minus(Duration.ofHours(1))) >= props.linkCodesPerHour()) {
            throw new TooManyRequestsException("Too many codes were requested. Please try again in an hour.");
        }
        // Only the newest code works
        for (LinkCode old : codes.findByPhoneAndUsedAtIsNull(phone)) {
            old.setUsedAt(now);
        }
        String code = String.format("%06d", random.nextInt(1_000_000));
        Instant expires = now.plus(props.linkCodeTtl());
        codes.save(new LinkCode(phone, hash(phone, code), expires));
        return new LinkCodeResponse(code, expires);
    }

    /** Wrong tries must be remembered even though the request fails, so this does not roll back on AuthFailed. */
    @Transactional(noRollbackFor = AuthFailedException.class)
    public TokenResponse verify(VerifyRequest request) {
        String phone = Phones.normalize(request.phone());
        Instant now = clock.instant();

        LinkCode stored = codes.findFirstByPhoneAndUsedAtIsNullOrderByCreatedAtDesc(phone)
                .filter(c -> now.isBefore(c.getExpiresAt()))
                .orElseThrow(() -> new AuthFailedException(INVALID));
        if (stored.getAttempts() >= props.linkMaxAttempts()) {
            stored.setUsedAt(now);
            throw new AuthFailedException(INVALID);
        }
        stored.setAttempts(stored.getAttempts() + 1);

        boolean match = MessageDigest.isEqual(stored.getCodeHash().getBytes(StandardCharsets.UTF_8),
                hash(phone, request.code()).getBytes(StandardCharsets.UTF_8));
        if (!match) {
            if (stored.getAttempts() >= props.linkMaxAttempts()) {
                stored.setUsedAt(now);
            }
            throw new AuthFailedException(INVALID);
        }
        stored.setUsedAt(now);

        Optional<Device> existing = devices.findByPhoneAndDeviceId(phone, request.deviceId());
        Device device = existing.orElseGet(() -> new Device(phone, request.deviceId(), request.deviceName()));
        device.setRevoked(false);
        device.setLastSeenAt(now);
        if (request.deviceName() != null) {
            device.setDeviceName(request.deviceName());
        }
        devices.save(device);

        Instant expires = tokens.expiryFromNow();
        return new TokenResponse(tokens.issue(phone, request.deviceId(), expires), phone, expires);
    }

    @Transactional
    public void revoke(String phone, String deviceId) {
        devices.findByPhoneAndDeviceId(phone, deviceId).ifPresent(d -> {
            d.setRevoked(true);
            devices.save(d);
        });
    }

    @Transactional(readOnly = true)
    public boolean isActive(String phone, String deviceId) {
        return devices.findByPhoneAndDeviceId(phone, deviceId).filter(d -> !d.isRevoked()).isPresent();
    }

    private String hash(String phone, String code) {
        return Hmac.hex("link-code:" + props.tokenSecret(), phone + ":" + code);
    }
}
