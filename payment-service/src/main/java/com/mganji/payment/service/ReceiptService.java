package com.mganji.payment.service;

import com.mganji.payment.config.BusinessProperties;
import com.mganji.payment.exception.InvalidPaymentRequestException;
import com.mganji.payment.model.Payment;
import com.mganji.payment.model.PaymentStatus;
import com.mganji.payment.util.PhoneNumberUtil;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/** Builds a simple one page PDF receipt for a successful payment. */
@Service
public class ReceiptService {

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm").withZone(ZoneId.of("Africa/Nairobi"));

    private final BusinessProperties business;

    public ReceiptService(BusinessProperties business) {
        this.business = business;
    }

    public byte[] receipt(Payment payment) {
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new InvalidPaymentRequestException("A receipt is only available for successful payments");
        }
        Map<String, String> rows = new LinkedHashMap<>();
        rows.put("M-Pesa receipt", payment.getMpesaReceipt() == null ? "Pending confirmation" : payment.getMpesaReceipt());
        rows.put("Amount paid", String.format("KES %,d", payment.getAmount()));
        java.time.Instant when = payment.getUpdatedAt() != null ? payment.getUpdatedAt()
                : payment.getCreatedAt() != null ? payment.getCreatedAt() : java.time.Instant.now();
        rows.put("Date", DATE.format(when));
        rows.put("Paid from", PhoneNumberUtil.mask(payment.getPhone()));
        rows.put("Reference", payment.getAccountReference() == null ? "" : payment.getAccountReference());
        rows.put("Status", "Successful");
        rows.put("Payment ID", String.valueOf(payment.getId()));

        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A5);
            document.addPage(page);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                float left = 40;
                float right = page.getMediaBox().getWidth() - 40;
                float y = page.getMediaBox().getHeight() - 60;

                y = text(cs, bold, 18, left, y, safe(business.name()));
                y = text(cs, regular, 11, left, y - 6, "PAYMENT RECEIPT");
                y -= 10;
                cs.moveTo(left, y);
                cs.lineTo(right, y);
                cs.stroke();
                y -= 26;

                for (Map.Entry<String, String> row : rows.entrySet()) {
                    text(cs, regular, 10, left, y, row.getKey());
                    y = text(cs, bold, 10, left + 120, y, safe(row.getValue()));
                    y -= 8;
                }

                y -= 14;
                text(cs, regular, 10, left, y, "Thank you for your payment.");
                if (business.supportPhone() != null && !business.supportPhone().isBlank()) {
                    text(cs, regular, 9, left, y - 16, "Questions? Contact " + safe(business.supportPhone()));
                }
            }
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not build the receipt", e);
        }
    }

    /** Draws one line and returns the y position of the next line. */
    private static float text(PDPageContentStream cs, PDType1Font font, float size, float x, float y, String value)
            throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(value);
        cs.endText();
        return y - size - 4;
    }

    /** The built in PDF fonts only cover basic Latin, so anything else becomes a question mark. */
    private static String safe(String value) {
        return value == null ? "" : value.replaceAll("[^\\x20-\\x7E]", "?");
    }
}
