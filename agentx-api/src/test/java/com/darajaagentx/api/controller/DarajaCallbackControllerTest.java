package com.darajaagentx.api.controller;

import com.darajaagentx.api.service.BusinessOperationService;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** The short addresses Safaricom is given, and the older long ones that must keep working for requests already sent. */
class DarajaCallbackControllerTest {

    private final BusinessOperationService service = mock(BusinessOperationService.class);
    private final DarajaCallbackController controller = new DarajaCallbackController(service);
    private final UUID id = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private static List<String> pathsOf(String method) throws NoSuchMethodException {
        Method m = Arrays.stream(DarajaCallbackController.class.getDeclaredMethods()).filter(x -> x.getName().equals(method)).findFirst()
                .orElseThrow(NoSuchMethodException::new);
        return Arrays.asList(m.getAnnotation(PostMapping.class).value());
    }

    @Test
    void eachKindOfAnswerHasAShortAddressAndKeepsItsOldOne() throws Exception {
        assertThat(pathsOf("stk")).containsExactlyInAnyOrder("/stk-callback/{id}/{token}", "/daraja/business/{id}/{token}/callback");
        assertThat(pathsOf("result")).containsExactlyInAnyOrder("/results/{id}/{token}", "/daraja/business/{id}/{token}/result");
        assertThat(pathsOf("timeout")).containsExactlyInAnyOrder("/timeout/{id}/{token}", "/daraja/business/{id}/{token}/timeout");
    }

    @Test
    void everyAnswerIsPassedOnWithItsKindAndAlwaysAccepted() {
        Map<String, Object> body = Map.of("Body", Map.of());
        assertThat(controller.stk(id, "tok", body)).containsEntry("ResultCode", 0);
        assertThat(controller.result(id, "tok", body)).containsEntry("ResultCode", 0);
        assertThat(controller.timeout(id, "tok", null)).containsEntry("ResultCode", 0);
        verify(service).onCallback(id, "tok", "callback", body);
        verify(service).onCallback(id, "tok", "result", body);
        verify(service).onCallback(id, "tok", "timeout", null);
    }

    @Test
    void aFailureWhileProcessingStillAnswersAccepted() {
        org.mockito.Mockito.doThrow(new IllegalStateException("boom")).when(service).onCallback(id, "bad", "result", null);
        assertThat(controller.result(id, "bad", null)).containsEntry("ResultDesc", "Accepted");
    }
}
