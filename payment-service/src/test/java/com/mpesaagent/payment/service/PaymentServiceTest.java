package com.mpesaagent.payment.service;

import com.mpesaagent.payment.config.LimitsProperties;
import com.mpesaagent.payment.dto.CreatePaymentRequest;
import com.mpesaagent.payment.dto.StkPushResponse;
import com.mpesaagent.payment.exception.IdempotencyConflictException;
import com.mpesaagent.payment.exception.InvalidPaymentRequestException;
import com.mpesaagent.payment.exception.LimitExceededException;
import com.mpesaagent.payment.exception.MpesaApiException;
import com.mpesaagent.payment.exception.MpesaCommunicationException;
import com.mpesaagent.payment.exception.PaymentNotFoundException;
import com.mpesaagent.payment.model.Payment;
import com.mpesaagent.payment.model.PaymentStatus;
import com.mpesaagent.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String PHONE = "254712345678";

    @Mock PaymentRepository repository;
    @Mock PaymentStateService state;
    @Mock MpesaClient mpesa;

    private PaymentService service;

    @BeforeEach
    void setUp() {
        service = new PaymentService(repository, state, mpesa, new LimitsProperties(1000, 2000));
    }

    private static CreatePaymentRequest request(String key, String phone, int amount) {
        return new CreatePaymentRequest(key, phone, amount, "ORD-1");
    }

    private static Payment payment(PaymentStatus status) {
        Payment p = new Payment("k1", PHONE, 500, "ORD-1");
        ReflectionTestUtils.setField(p, "id", ID);
        p.setStatus(status);
        return p;
    }

    private void stubSave() {
        when(repository.saveAndFlush(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            ReflectionTestUtils.setField(p, "id", ID);
            return p;
        });
    }

    @Test
    void rejectsAmountAboveTransactionLimit() {
        assertThatThrownBy(() -> service.initiate(request("k1", "0712345678", 1001)))
                .isInstanceOf(LimitExceededException.class);
        verifyNoInteractions(mpesa, state);
    }

    @Test
    void rejectsPaymentThatWouldExceedDailyLimit() {
        when(repository.sumAmountSince(eq(PHONE), anyCollection(), any(Instant.class))).thenReturn(1500L);

        assertThatThrownBy(() -> service.initiate(request("k1", "0712345678", 600)))
                .isInstanceOf(LimitExceededException.class);
        verifyNoInteractions(mpesa, state);
    }

    @Test
    void rejectsBadPhoneBeforeAnythingElseHappens() {
        assertThatThrownBy(() -> service.initiate(request("k1", "12345", 100)))
                .isInstanceOf(InvalidPaymentRequestException.class);
        verifyNoInteractions(repository, mpesa, state);
    }

    @Test
    void normalizesPhoneAndPushesStk() {
        stubSave();
        when(mpesa.stkPush(PHONE, 500, "ORD-1"))
                .thenReturn(new StkPushResponse("m1", "c1", "0", "Success", "Success"));
        Payment pushed = payment(PaymentStatus.PUSHED);
        when(state.markPushed(ID, "m1", "c1")).thenReturn(pushed);

        Payment result = service.initiate(request("k1", "0712345678", 500));

        assertThat(result).isSameAs(pushed);
        verify(mpesa).stkPush(PHONE, 500, "ORD-1");
    }

    @Test
    void replayOfSameRequestReturnsExistingPaymentWithoutPushingAgain() {
        Payment existing = payment(PaymentStatus.PUSHED);
        when(repository.findByIdempotencyKey("k1")).thenReturn(Optional.of(existing));

        Payment result = service.initiate(request("k1", "0712345678", 500));

        assertThat(result).isSameAs(existing);
        verifyNoInteractions(mpesa, state);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void reusingKeyForDifferentAmountIsAConflict() {
        when(repository.findByIdempotencyKey("k1")).thenReturn(Optional.of(payment(PaymentStatus.PUSHED)));

        assertThatThrownBy(() -> service.initiate(request("k1", "0712345678", 700)))
                .isInstanceOf(IdempotencyConflictException.class);
        verifyNoInteractions(mpesa, state);
    }

    @Test
    void racingRequestWithSameKeyReturnsTheWinner() {
        Payment winner = payment(PaymentStatus.PENDING);
        when(repository.findByIdempotencyKey("k1")).thenReturn(Optional.empty(), Optional.of(winner));
        when(repository.saveAndFlush(any(Payment.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        Payment result = service.initiate(request("k1", "0712345678", 500));

        assertThat(result).isSameAs(winner);
        verifyNoInteractions(mpesa, state);
    }

    @Test
    void darajaRefusalMarksPaymentFailed() {
        stubSave();
        when(mpesa.stkPush(anyString(), anyInt(), anyString()))
                .thenReturn(new StkPushResponse(null, null, "1", "Rejected", null));
        Payment failed = payment(PaymentStatus.FAILED);
        when(state.markPushFailed(ID, "1", "Rejected")).thenReturn(failed);

        assertThat(service.initiate(request("k1", "0712345678", 500))).isSameAs(failed);
    }

    @Test
    void apiErrorMarksPaymentFailed() {
        stubSave();
        when(mpesa.stkPush(anyString(), anyInt(), anyString()))
                .thenThrow(new MpesaApiException("400.002.02", "Bad Request - Invalid PhoneNumber"));
        Payment failed = payment(PaymentStatus.FAILED);
        when(state.markPushFailed(ID, "400.002.02", "Bad Request - Invalid PhoneNumber")).thenReturn(failed);

        assertThat(service.initiate(request("k1", "0712345678", 500))).isSameAs(failed);
    }

    @Test
    void timeoutTalkingToDarajaKeepsPaymentPendingAndNeverMarksItFailed() {
        stubSave();
        when(mpesa.stkPush(anyString(), anyInt(), anyString()))
                .thenThrow(new MpesaCommunicationException("timeout", null));
        Payment pending = payment(PaymentStatus.PENDING);
        when(state.markUncertain(eq(ID), anyString())).thenReturn(pending);

        assertThat(service.initiate(request("k1", "0712345678", 500))).isSameAs(pending);
        verify(state, never()).markPushFailed(any(), any(), any());
    }

    @Test
    void getThrowsWhenPaymentDoesNotExist() {
        when(repository.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(ID)).isInstanceOf(PaymentNotFoundException.class);
    }
}
