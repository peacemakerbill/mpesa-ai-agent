package com.darajaagentx.api.controller;

import com.darajaagentx.api.config.AgentXProperties;
import com.darajaagentx.api.config.DeviceAuthFilter;
import com.darajaagentx.api.config.DevicePrincipal;
import com.darajaagentx.api.config.EnvironmentPolicy;
import com.darajaagentx.api.dto.ActionResponse;
import com.darajaagentx.api.dto.ApprovalCodeResponse;
import com.darajaagentx.api.dto.AppConfigResponse;
import com.darajaagentx.api.dto.ApproveRequest;
import com.darajaagentx.api.dto.ClearRequest;
import com.darajaagentx.api.dto.ReportRequest;
import com.darajaagentx.api.dto.SmsEvidenceRequest;
import com.darajaagentx.api.service.ActionService;
import com.darajaagentx.api.service.ApprovalCodeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    private final ApprovalCodeService approvalCodes;
    private final AgentXProperties props;
    private final EnvironmentPolicy environment;

    public AppActionController(ActionService actions, ApprovalCodeService approvalCodes, AgentXProperties props, EnvironmentPolicy environment) {
        this.actions = actions;
        this.approvalCodes = approvalCodes;
        this.props = props;
        this.environment = environment;
    }

    @GetMapping("/config")
    public AppConfigResponse config() {
        AgentXProperties.HandOff h = props.handOff();
        return new AppConfigResponse(h.ussdMain(), h.mpesaAndroidPackage(), h.mpesaIosUrl(), h.ussdTemplates(), environment.env().label());
    }

    @GetMapping("/actions")
    public List<ActionResponse> list(@RequestAttribute(DeviceAuthFilter.PRINCIPAL) DevicePrincipal who) {
        return actions.list(who.phone(), 20).stream().map(ActionResponse::from).toList();
    }

    @GetMapping("/actions/{id}")
    public ActionResponse get(@PathVariable UUID id, @RequestAttribute(DeviceAuthFilter.PRINCIPAL) DevicePrincipal who) {
        return ActionResponse.from(actions.get(id, who.phone()));
    }

    /** Sends a code by text message to the customer's own number, for approving this request with OTP. */
    @PostMapping("/actions/{id}/approval-code")
    public ApprovalCodeResponse approvalCode(@PathVariable UUID id, @RequestAttribute(DeviceAuthFilter.PRINCIPAL) DevicePrincipal who) {
        return approvalCodes.sendForAction(who.phone(), id);
    }

    @PostMapping("/actions/{id}/approve")
    public ActionResponse approve(@PathVariable UUID id, @Valid @RequestBody ApproveRequest request,
                                  @RequestAttribute(DeviceAuthFilter.PRINCIPAL) DevicePrincipal who) {
        if (request.method() == ApproveRequest.Method.OTP) {
            approvalCodes.check(who.phone(), ApprovalCodeService.Kind.ACTION, id, request.code());
        }
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

    /** Takes one finished request off the customer's list. The record stays on the server. A request still open is refused. */
    @DeleteMapping("/actions/{id}")
    public ActionService.ClearResult clear(@PathVariable UUID id, @RequestAttribute(DeviceAuthFilter.PRINCIPAL) DevicePrincipal who) {
        return actions.clear(id, who.phone());
    }

    /** Clears the chosen requests that are finished. Open ones are left and counted as skipped. */
    @PostMapping("/actions/clear")
    public ActionService.ClearResult clearSelected(@Valid @RequestBody ClearRequest request,
                                                   @RequestAttribute(DeviceAuthFilter.PRINCIPAL) DevicePrincipal who) {
        return actions.clearMany(who.phone(), request.ids());
    }

    /** Clears every finished request on the list. Open ones stay. */
    @DeleteMapping("/actions")
    public ActionService.ClearResult clearFinished(@RequestAttribute(DeviceAuthFilter.PRINCIPAL) DevicePrincipal who) {
        return actions.clearFinished(who.phone());
    }

    @PostMapping("/actions/{id}/decline")
    public ActionResponse decline(@PathVariable UUID id, @RequestAttribute(DeviceAuthFilter.PRINCIPAL) DevicePrincipal who) {
        return ActionResponse.from(actions.decline(id, who.phone()));
    }
}
