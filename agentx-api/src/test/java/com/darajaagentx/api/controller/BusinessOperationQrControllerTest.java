package com.darajaagentx.api.controller;

import com.darajaagentx.api.config.DevicePrincipal;
import com.darajaagentx.api.dto.BusinessOperationView;
import com.darajaagentx.api.dto.QrCodeRequest;
import com.darajaagentx.api.model.BusinessOperation;
import com.darajaagentx.api.model.BusinessOperationType;
import com.darajaagentx.api.service.ApprovalCodeService;
import com.darajaagentx.api.service.BusinessOperationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The app asks for a QR code. The customer is the one in the login token, and the code comes back once, not stored. */
class BusinessOperationQrControllerTest {

    private final BusinessOperationService service = mock(BusinessOperationService.class);
    private final BusinessOperationController controller = new BusinessOperationController(service, mock(ApprovalCodeService.class));
    private final DevicePrincipal who = new DevicePrincipal("254700000001", "device-1");

    @Test
    @SuppressWarnings("unchecked")
    void theRequestBecomesAnAppQrOperationForTheSignedInCustomer() {
        BusinessOperation op = new BusinessOperation("254700000001", UUID.randomUUID(), "Main shop", BusinessOperationType.DYNAMIC_QR);
        when(service.prepare(any(), any(), any(), any(), any(), any())).thenReturn(new BusinessOperationService.Prepared(op, "BASE64PNG"));

        BusinessOperationView view = controller.qr(new QrCodeRequest("Main shop", 300, " TBL5 ", "", "till", "123456"), who);

        ArgumentCaptor<Map<String, String>> params = ArgumentCaptor.forClass(Map.class);
        verify(service).prepare(eq("254700000001"), eq("Main shop"), eq("DYNAMIC_QR"), params.capture(), eq("APP"), isNull());
        assertThat(params.getValue()).containsEntry("amount", "300").containsEntry("reference", "TBL5").containsEntry("pay_to_type", "till")
                .containsEntry("pay_to_number", "123456").doesNotContainKey("merchant_name");
        assertThat(view.qrCode()).isEqualTo("BASE64PNG");
    }

    @Test
    @SuppressWarnings("unchecked")
    void aMissingAmountIsLeftOutSoTheServiceCanSayWhatIsNeeded() {
        BusinessOperation op = new BusinessOperation("254700000001", UUID.randomUUID(), "Main shop", BusinessOperationType.DYNAMIC_QR);
        when(service.prepare(any(), any(), any(), any(), any(), any())).thenReturn(new BusinessOperationService.Prepared(op, null));
        controller.qr(new QrCodeRequest("Main shop", null, null, null, null, null), who);
        ArgumentCaptor<Map<String, String>> params = ArgumentCaptor.forClass(Map.class);
        verify(service).prepare(any(), any(), any(), params.capture(), any(), any());
        assertThat(params.getValue()).isEmpty();
    }
}
