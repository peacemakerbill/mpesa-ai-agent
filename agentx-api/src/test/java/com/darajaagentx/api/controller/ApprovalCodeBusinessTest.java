package com.darajaagentx.api.controller;

import com.darajaagentx.api.dto.ApprovalCodeResponse;
import com.darajaagentx.api.exception.InvalidActionRequestException;
import com.darajaagentx.api.exception.InvalidActionStateException;
import com.darajaagentx.api.model.BusinessOperation;
import com.darajaagentx.api.model.BusinessOperationStatus;
import com.darajaagentx.api.service.ApprovalCodeService;
import com.darajaagentx.api.service.BusinessOperationService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** A code for a request on a business account. The business service is replaced, the code store is the real one. */
@SpringBootTest(properties = {"agentx.code-delivery=log", "agentx.show-code-in-app=true"})
class ApprovalCodeBusinessTest {

    private static final String PHONE = "254799000111";

    @Autowired ApprovalCodeService service;
    @MockitoBean BusinessOperationService business;
    @MockitoBean RabbitTemplate rabbit;

    private BusinessOperation waiting(UUID id) {
        BusinessOperation op = mock(BusinessOperation.class);
        when(op.getStatus()).thenReturn(BusinessOperationStatus.PENDING_APPROVAL);
        when(op.getSummary()).thenReturn("Pay KES 500 to 254700111222 from Main shop");
        when(business.get(PHONE, id)).thenReturn(op);
        return op;
    }

    @Test
    void theOwnerGetsACodeAndItApprovesOnlyThatRequest() {
        UUID id = UUID.randomUUID();
        waiting(id);

        ApprovalCodeResponse sent = service.sendForBusiness(PHONE, id);

        assertThat(sent.devCode()).matches("\\d{6}");
        assertThat(sent.resendAfterSeconds()).isEqualTo(30);
        assertThatThrownBy(() -> service.check(PHONE, ApprovalCodeService.Kind.BUSINESS, UUID.randomUUID(), sent.devCode()))
                .isInstanceOf(InvalidActionRequestException.class);
        service.check(PHONE, ApprovalCodeService.Kind.BUSINESS, id, sent.devCode());
        // used up
        assertThatThrownBy(() -> service.check(PHONE, ApprovalCodeService.Kind.BUSINESS, id, sent.devCode()))
                .isInstanceOf(InvalidActionRequestException.class);
    }

    @Test
    void aWrongCodeIsRefusedAndAnotherPhoneCannotUseTheCode() {
        UUID id = UUID.randomUUID();
        waiting(id);
        String code = service.sendForBusiness(PHONE, id).devCode();
        String wrong = code.equals("000000") ? "111111" : "000000";

        assertThatThrownBy(() -> service.check(PHONE, ApprovalCodeService.Kind.BUSINESS, id, wrong))
                .isInstanceOf(InvalidActionRequestException.class).hasMessageContaining("not right");
        assertThatThrownBy(() -> service.check("254799000222", ApprovalCodeService.Kind.BUSINESS, id, code))
                .isInstanceOf(InvalidActionRequestException.class);
        service.check(PHONE, ApprovalCodeService.Kind.BUSINESS, id, code);
    }

    @Test
    void aRequestThatIsNoLongerWaitingGetsNoCode() {
        UUID id = UUID.randomUUID();
        BusinessOperation op = mock(BusinessOperation.class);
        when(op.getStatus()).thenReturn(BusinessOperationStatus.SUBMITTED);
        when(business.get(PHONE, id)).thenReturn(op);

        assertThatThrownBy(() -> service.sendForBusiness(PHONE, id))
                .isInstanceOf(InvalidActionStateException.class).hasMessageContaining("no longer waiting");
    }
}
