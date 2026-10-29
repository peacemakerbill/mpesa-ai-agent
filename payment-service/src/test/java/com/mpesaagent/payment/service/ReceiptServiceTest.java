package com.mpesaagent.payment.service;

import com.mpesaagent.payment.config.BusinessProperties;
import com.mpesaagent.payment.exception.InvalidPaymentRequestException;
import com.mpesaagent.payment.model.Payment;
import com.mpesaagent.payment.model.PaymentStatus;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReceiptServiceTest {

    private final ReceiptService service = new ReceiptService(new BusinessProperties("Café Bora", "0700000000"));

    private Payment payment(PaymentStatus status) {
        Payment p = new Payment("k", "254712345678", 1500, "ORD-77");
        ReflectionTestUtils.setField(p, "id", UUID.randomUUID());
        p.setStatus(status);
        p.setMpesaReceipt("QGH7ABC123");
        return p;
    }

    @Test
    void buildsAReadablePdfWithTheKeyDetails() throws Exception {
        byte[] pdf = service.receipt(payment(PaymentStatus.SUCCESS));

        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("QGH7ABC123").contains("KES 1,500").contains("ORD-77").contains("Caf? Bora");
            assertThat(text).doesNotContain("254712345678");   // phone is masked
        }
    }

    @Test
    void onlySuccessfulPaymentsGetAReceipt() {
        assertThatThrownBy(() -> service.receipt(payment(PaymentStatus.FAILED)))
                .isInstanceOf(InvalidPaymentRequestException.class);
    }
}
