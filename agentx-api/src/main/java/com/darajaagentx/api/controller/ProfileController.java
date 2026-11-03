package com.darajaagentx.api.controller;

import com.darajaagentx.api.config.DeviceAuthFilter;
import com.darajaagentx.api.config.DevicePrincipal;
import com.darajaagentx.api.config.StorageProperties;
import com.darajaagentx.api.dto.CodeRequestResponse;
import com.darajaagentx.api.dto.EmailChangeRequest;
import com.darajaagentx.api.dto.EmailCodeRequest;
import com.darajaagentx.api.dto.ProfileUpdate;
import com.darajaagentx.api.dto.ProfileView;
import com.darajaagentx.api.exception.InvalidActionRequestException;
import com.darajaagentx.api.service.ProfileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;

/**
 * The signed in customer's own profile. The phone number always comes from the login token. The picture travels as the raw
 * file in the request body (like chat files), is checked and shrunk on the server, and is kept in the file store chosen by
 * agentx.storage.type.
 */
@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profiles;
    private final int maxPictureBytes;

    public ProfileController(ProfileService profiles, StorageProperties storage) {
        this.profiles = profiles;
        this.maxPictureBytes = storage.maxPictureBytes();
    }

    @GetMapping
    public ProfileView get(@RequestAttribute(DeviceAuthFilter.PRINCIPAL) DevicePrincipal who) {
        return profiles.view(who.phone());
    }

    @PutMapping
    public ProfileView update(@RequestBody ProfileUpdate update, @RequestAttribute(DeviceAuthFilter.PRINCIPAL) DevicePrincipal who) {
        return profiles.update(who.phone(), update);
    }

    // ------------------------------------------------------------------ email

    /** Sends a 6 digit code to the address. It becomes the profile email once the code is typed back. */
    @PostMapping("/email/request-code")
    public CodeRequestResponse requestEmailCode(@Valid @RequestBody EmailChangeRequest request, @RequestAttribute(DeviceAuthFilter.PRINCIPAL) DevicePrincipal who) {
        return profiles.requestEmailCode(who.phone(), request.email());
    }

    @PostMapping("/email/verify")
    public ProfileView verifyEmail(@Valid @RequestBody EmailCodeRequest request, @RequestAttribute(DeviceAuthFilter.PRINCIPAL) DevicePrincipal who) {
        return profiles.verifyEmail(who.phone(), request.code());
    }

    @DeleteMapping("/email")
    public ProfileView removeEmail(@RequestAttribute(DeviceAuthFilter.PRINCIPAL) DevicePrincipal who) {
        return profiles.removeEmail(who.phone());
    }

    // ------------------------------------------------------------------ picture

    @PutMapping("/picture")
    public ProfileView putPicture(HttpServletRequest http, @RequestAttribute(DeviceAuthFilter.PRINCIPAL) DevicePrincipal who) throws IOException {
        long declared = http.getContentLengthLong();
        if (declared > maxPictureBytes) {
            throw new InvalidActionRequestException("That picture is too large.");
        }
        byte[] bytes = readLimited(http.getInputStream(), maxPictureBytes);
        return profiles.setPicture(who.phone(), bytes);
    }

    /** The picture file, or 404 when there is none. The version in the ETag changes with every new picture. */
    @GetMapping("/picture")
    public ResponseEntity<byte[]> getPicture(@RequestAttribute(DeviceAuthFilter.PRINCIPAL) DevicePrincipal who) {
        return profiles.picture(who.phone())
                .map(p -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(p.type()))
                        .header(HttpHeaders.ETAG, "\"" + p.version() + "\"")
                        .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                        .header("X-Content-Type-Options", "nosniff")
                        .body(p.data()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/picture")
    public ProfileView deletePicture(@RequestAttribute(DeviceAuthFilter.PRINCIPAL) DevicePrincipal who) {
        return profiles.removePicture(who.phone());
    }

    /** Reads at most [limit] bytes. A longer body is refused without reading the rest into memory. */
    static byte[] readLimited(InputStream in, int limit) throws IOException {
        byte[] data = in.readNBytes(limit + 1);
        if (data.length > limit) {
            throw new InvalidActionRequestException("That picture is too large.");
        }
        return data;
    }
}
