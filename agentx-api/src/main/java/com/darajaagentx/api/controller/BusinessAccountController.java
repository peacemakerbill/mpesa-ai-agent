package com.darajaagentx.api.controller;

import com.darajaagentx.api.config.DeviceAuthFilter;
import com.darajaagentx.api.config.DevicePrincipal;
import com.darajaagentx.api.dto.BusinessAccountRequest;
import com.darajaagentx.api.dto.BusinessAccountView;
import com.darajaagentx.api.dto.ImportResult;
import com.darajaagentx.api.dto.VerifyResult;
import com.darajaagentx.api.exception.InvalidActionRequestException;
import com.darajaagentx.api.service.BusinessAccountCsv;
import com.darajaagentx.api.service.BusinessAccountService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The business shortcodes and Daraja credentials of the signed-in customer. The owner always comes from the login
 * token. Secrets go in but never come out: responses only say whether each one is saved.
 */
@RestController
@RequestMapping("/api/business-accounts")
public class BusinessAccountController {

    private final BusinessAccountService service;

    public BusinessAccountController(BusinessAccountService service) {
        this.service = service;
    }

    @GetMapping
    public List<BusinessAccountView> list(@RequestAttribute(DeviceAuthFilter.PRINCIPAL) DevicePrincipal who) {
        return service.list(who.phone());
    }

    @PostMapping
    public ResponseEntity<BusinessAccountView> create(@RequestBody BusinessAccountRequest request,
                                                      @RequestAttribute(DeviceAuthFilter.PRINCIPAL) DevicePrincipal who) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(who.phone(), request));
    }

    @GetMapping("/{id}")
    public BusinessAccountView get(@PathVariable UUID id, @RequestAttribute(DeviceAuthFilter.PRINCIPAL) DevicePrincipal who) {
        return service.get(who.phone(), id);
    }

    @PutMapping("/{id}")
    public BusinessAccountView update(@PathVariable UUID id, @RequestBody BusinessAccountRequest request,
                                      @RequestAttribute(DeviceAuthFilter.PRINCIPAL) DevicePrincipal who) {
        return service.update(who.phone(), id, request);
    }

    /** Deleting a head account also deletes its branches. The answer says how many accounts were removed. */
    @DeleteMapping("/{id}")
    public Map<String, Integer> delete(@PathVariable UUID id, @RequestAttribute(DeviceAuthFilter.PRINCIPAL) DevicePrincipal who) {
        return Map.of("removed", service.delete(who.phone(), id));
    }

    /** Asks Safaricom whether the saved consumer key and secret work, and checks the certificate for the initiator password. */
    @PostMapping("/{id}/verify")
    public VerifyResult verify(@PathVariable UUID id, @RequestAttribute(DeviceAuthFilter.PRINCIPAL) DevicePrincipal who) {
        return service.verify(who.phone(), id);
    }

    @GetMapping("/csv-template")
    public ResponseEntity<byte[]> template() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"agentx-business-accounts-template.csv\"")
                .body(BusinessAccountCsv.TEMPLATE.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Adds or updates many accounts from one CSV file. With dryRun=true nothing is saved and the answer shows what
     * would happen. If any row has a problem nothing is saved, so the file can be fixed and sent again.
     */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResult importCsv(@RequestParam MultipartFile file,
                                  @RequestParam(defaultValue = "true") boolean dryRun,
                                  @RequestAttribute(DeviceAuthFilter.PRINCIPAL) DevicePrincipal who) {
        if (file.getSize() > BusinessAccountCsv.MAX_BYTES) {
            throw new InvalidActionRequestException("The file is bigger than 1 MB. Split it into smaller files.");
        }
        try {
            return service.importCsv(who.phone(), file.getBytes(), dryRun);
        } catch (IOException e) {
            throw new InvalidActionRequestException("The file could not be read.");
        }
    }
}
