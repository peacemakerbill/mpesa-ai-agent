package com.darajaagentx.api.controller;

import com.darajaagentx.api.dto.CodeRequestResponse;
import com.darajaagentx.api.dto.EmailChangeRequest;
import com.darajaagentx.api.dto.EmailCodeRequest;
import com.darajaagentx.api.dto.ProfileUpdate;
import com.darajaagentx.api.dto.ProfileView;
import com.darajaagentx.api.dto.RecipientImportResult;
import com.darajaagentx.api.dto.RecipientRequest;
import com.darajaagentx.api.dto.RecipientView;
import com.darajaagentx.api.dto.ReportEmailRequest;
import com.darajaagentx.api.dto.ReportEmailResult;
import com.darajaagentx.api.exception.ActionNotFoundException;
import com.darajaagentx.api.exception.InvalidActionRequestException;
import com.darajaagentx.api.service.ProfileService;
import com.darajaagentx.api.service.RecipientService;
import com.darajaagentx.api.service.ReportMailService;
import com.darajaagentx.api.util.Phones;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * For agent-service only (the X-Internal-Key filter protects /internal/**). The assistant can read and change the profile of
 * the customer it is chatting with, keep their email recipients, and email a report. The phone number is the one the customer
 * is chatting from, never one the model chose. An emailed verification code is never returned here: the customer reads it in
 * their inbox and types it into the chat.
 */
@RestController
@RequestMapping("/internal")
public class InternalProfileController {

    private final ProfileService profiles;
    private final RecipientService recipients;
    private final ReportMailService reports;

    public InternalProfileController(ProfileService profiles, RecipientService recipients, ReportMailService reports) {
        this.profiles = profiles;
        this.recipients = recipients;
        this.reports = reports;
    }

    @GetMapping("/profile")
    public ProfileView profile(@RequestParam String phone) {
        return profiles.view(Phones.normalize(phone));
    }

    @PutMapping("/profile")
    public ProfileView update(@RequestParam String phone, @RequestBody ProfileUpdate update) {
        return profiles.update(Phones.normalize(phone), update);
    }

    @PostMapping("/profile/email/request-code")
    public Map<String, Object> requestCode(@RequestParam String phone, @Valid @RequestBody EmailChangeRequest request) {
        CodeRequestResponse sent = profiles.requestEmailCode(Phones.normalize(phone), request.email());
        return Map.of("expiresAt", sent.expiresAt().toString());
    }

    @PostMapping("/profile/email/verify")
    public ProfileView verify(@RequestParam String phone, @Valid @RequestBody EmailCodeRequest request) {
        return profiles.verifyEmail(Phones.normalize(phone), request.code());
    }

    @GetMapping("/email/recipients")
    public List<RecipientView> recipients(@RequestParam String phone) {
        return recipients.list(Phones.normalize(phone));
    }

    @PostMapping("/email/recipients")
    public RecipientView addRecipient(@RequestParam String phone, @Valid @RequestBody RecipientRequest request) {
        return recipients.add(Phones.normalize(phone), request.email(), request.name());
    }

    /** Several addresses at once from pasted text, for example a list the customer typed or an attached file read by the assistant. */
    @PostMapping("/email/recipients/paste")
    public RecipientImportResult paste(@RequestParam String phone, @RequestBody Map<String, String> body) {
        return recipients.addMany(Phones.normalize(phone), body == null ? null : body.get("text"));
    }

    /** Removes an address by its address or its name. */
    @DeleteMapping("/email/recipients")
    public Map<String, String> removeRecipient(@RequestParam String phone, @RequestParam String target) {
        if (target == null || target.isBlank()) {
            throw new InvalidActionRequestException("Say which address or name to remove.");
        }
        var removed = recipients.removeBy(Phones.normalize(phone), target)
                .orElseThrow(() -> new ActionNotFoundException("\"" + target + "\" is not on your list."));
        return Map.of("removed", removed.getEmail());
    }

    @PostMapping("/email/report")
    public ReportEmailResult report(@RequestParam String phone, @RequestBody ReportEmailRequest request) {
        return reports.send(Phones.normalize(phone), request);
    }
}
