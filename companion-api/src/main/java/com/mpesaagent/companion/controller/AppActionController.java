package com.mpesaagent.companion.controller;

import com.mpesaagent.companion.config.CompanionProperties;
import com.mpesaagent.companion.config.DeviceAuthFilter;
import com.mpesaagent.companion.config.DevicePrincipal;
import com.mpesaagent.companion.dto.ActionResponse;
import com.mpesaagent.companion.dto.AppConfigResponse;
import com.mpesaagent.companion.dto.ApproveRequest;
import com.mpesaagent.companion.dto.ReportRequest;
import com.mpesaagent.companion.dto.SmsEvidenceRequest;
import com.mpesaagent.companion.service.ActionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** What the phone app calls. The caller's number comes from the login token, never from the request. */
@RestController
@RequestMapping("/api")
public class AppActionController {

    private final ActionService actions;
    private final CompanionProperties props;

    public AppActionController(ActionService actions, CompanionProperties props) {
        this.actions = actions;
        this.props = props;
    }

    @GetMapping("/config")
    public AppConfigResponse config() {
        CompanionProperties.HandOff h = props.handOff();
        return new AppConfigResponse(h.ussdMain(), h.mpesaAndroidPackage(), h.mpesaIosUrl(), h.ussdTemplates());
    }

    @GetMapping("/actions")
    public List<ActionResponse> list(@RequestAttribute(DeviceAuthFilter.PRINCIPAL) DevicePrincipal who) {
        return actions.list(who.phone(), 20).stream().map(ActionResponse::from).toList();
    }

    @GetMapping("/actions/{id}")
    public ActionResponse get(@PathVariable UUID id, @RequestAttribute(DeviceAuthFilter.PRINCIPAL) DevicePrincipal who) {
        return ActionResponse.from(actions.get(id, who.phone()));
    }

    @PostMapping("/actions/{id}/approve")
    public ActionResponse approve(@PathVariable UUID id, @Valid @RequestBody ApproveRequest request,
                                  @RequestAttribute(DeviceAuthFilter.PRINCIPAL) DevicePrincipal who) {
        return ActionResponse.from(actions.approve(id, who.phone(), who.deviceId(), request.method()));
    }

    @PostMapping("/actions/{id}/handoff")
    public ActionResponse handOff(@PathVariable UUID id, @RequestAttribute(DeviceAuthFilter.PRINCIPAL) DevicePrincipal who) {
        return ActionResponse.from(actions.handOff(id, who.phone()));
    }

    @PostMapping("/actions/{id}/report")
    public ActionResponse report(@PathVariable UUID id, @Valid @RequestBody ReportRequest request,
                                 @RequestAttribute(DeviceAuthFilter.PRINCIPAL) DevicePrincipal who) {
        return ActionResponse.from(actions.report(id, who.phone(), request.outcome(), request.receipt()));
    }

    /** The app found an M-Pesa message on this phone that fits the request and sends the parsed fields, not the text. */
    @PostMapping("/actions/{id}/sms-evidence")
    public ActionResponse smsEvidence(@PathVariable UUID id, @Valid @RequestBody SmsEvidenceRequest request,
                                      @RequestAttribute(DeviceAuthFilter.PRINCIPAL) DevicePrincipal who) {
        return ActionResponse.from(actions.confirmWithSms(id, who.phone(), request));
    }

    @PostMapping("/actions/{id}/decline")
    public ActionResponse decline(@PathVariable UUID id, @RequestAttribute(DeviceAuthFilter.PRINCIPAL) DevicePrincipal who) {
        return ActionResponse.from(actions.decline(id, who.phone()));
    }
}
