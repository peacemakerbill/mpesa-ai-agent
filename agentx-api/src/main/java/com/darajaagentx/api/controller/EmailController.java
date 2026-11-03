package com.darajaagentx.api.controller;

import com.darajaagentx.api.config.DeviceAuthFilter;
import com.darajaagentx.api.config.DevicePrincipal;
import com.darajaagentx.api.dto.EmailLogView;
import com.darajaagentx.api.dto.RecipientImportResult;
import com.darajaagentx.api.dto.RecipientRequest;
import com.darajaagentx.api.dto.RecipientView;
import com.darajaagentx.api.dto.ReportEmailRequest;
import com.darajaagentx.api.dto.ReportEmailResult;
import com.darajaagentx.api.exception.InvalidActionRequestException;
import com.darajaagentx.api.repository.EmailLogRepository;
import com.darajaagentx.api.service.RecipientService;
import com.darajaagentx.api.service.ReportMailService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Saved email recipients, sending a report, and the list of emails sent for this customer. */
@RestController
@RequestMapping("/api/email")
public class EmailController {

    private final RecipientService recipients;
    private final ReportMailService reports;
    private final EmailLogRepository log;

    public EmailController(RecipientService recipients, ReportMailService reports, EmailLogRepository log) {
        this.recipients = recipients;
        this.reports = reports;
        this.log = log;
    }

    @GetMapping("/recipients")
    public List<RecipientView> list(@RequestAttribute(DeviceAuthFilter.PRINCIPAL) DevicePrincipal who) {
        return recipients.list(who.phone());
    }

    @PostMapping("/recipients")
    public ResponseEntity<RecipientView> add(@Valid @RequestBody RecipientRequest request, @RequestAttribute(DeviceAuthFilter.PRINCIPAL) DevicePrincipal who) {
        return ResponseEntity.status(HttpStatus.CREATED).body(recipients.add(who.phone(), request.email(), request.name()));
    }

    @DeleteMapping("/recipients/{id}")
    public ResponseEntity<Void> remove(@PathVariable UUID id, @RequestAttribute(DeviceAuthFilter.PRINCIPAL) DevicePrincipal who) {
        recipients.remove(who.phone(), id);
        return ResponseEntity.noContent().build();
    }

    /** Pasted text: {"text": "a@x.com, b@y.com"}. */
    @PostMapping("/recipients/paste")
    public RecipientImportResult paste(@RequestBody Map<String, String> body, @RequestAttribute(DeviceAuthFilter.PRINCIPAL) DevicePrincipal who) {
        return recipients.addMany(who.phone(), body == null ? null : body.get("text"));
    }

    /** An uploaded CSV or text file (form field "file"). */
    @PostMapping("/recipients/import")
    public RecipientImportResult upload(@RequestParam MultipartFile file, @RequestAttribute(DeviceAuthFilter.PRINCIPAL) DevicePrincipal who) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new InvalidActionRequestException("Choose a CSV or text file with email addresses.");
        }
        return recipients.importFile(who.phone(), file.getBytes());
    }

    /** Makes the report and emails it. The answer says who it went to and what the file was called. */
    @PostMapping("/report")
    public ReportEmailResult report(@RequestBody ReportEmailRequest request, @RequestAttribute(DeviceAuthFilter.PRINCIPAL) DevicePrincipal who) {
        return reports.send(who.phone(), request);
    }

    /** The latest emails sent for this customer: what, to whom and whether it worked. */
    @GetMapping("/log")
    public List<EmailLogView> log(@RequestAttribute(DeviceAuthFilter.PRINCIPAL) DevicePrincipal who) {
        return log.findTop30ByOwnerPhoneOrderByCreatedAtDesc(who.phone()).stream().map(EmailLogView::of).toList();
    }
}
