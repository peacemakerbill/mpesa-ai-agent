package com.mganji.payment.controller;

import com.mganji.payment.dto.CreatePaymentRequest;
import com.mganji.payment.dto.PaymentResponse;
import com.mganji.payment.model.Payment;
import com.mganji.payment.service.PaymentService;
import com.mganji.payment.service.ReceiptService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Called only by our own services. Protected by InternalKeyFilter. */
@RestController
@RequestMapping("/internal/payments")
public class InternalPaymentController {

    private final PaymentService paymentService;
    private final ReceiptService receiptService;

    public InternalPaymentController(PaymentService paymentService, ReceiptService receiptService) {
        this.paymentService = paymentService;
        this.receiptService = receiptService;
    }

    /** Safe to retry with the same idempotencyKey: the same payment is returned and nobody is charged twice. */
    @PostMapping
    public PaymentResponse create(@Valid @RequestBody CreatePaymentRequest request) {
        return PaymentResponse.from(paymentService.initiate(request));
    }

    /**
     * When phone is given, the payment must belong to that phone (use this for customers).
     * Without it the lookup is unrestricted, which the agent only does for staff.
     */
    @GetMapping("/{id}")
    public PaymentResponse get(@PathVariable UUID id, @RequestParam(required = false) String phone) {
        Payment payment = phone == null ? paymentService.get(id) : paymentService.get(id, phone);
        return PaymentResponse.from(payment);
    }

    /** Most recent payment for a customer, used by the agent's check_payment_status tool. */
    @GetMapping("/latest")
    public PaymentResponse latest(@RequestParam String phone) {
        return PaymentResponse.from(paymentService.latestForPhone(phone));
    }

    /** A customer's recent payments, newest first. */
    @GetMapping
    public List<PaymentResponse> list(@RequestParam String phone, @RequestParam(defaultValue = "5") int limit) {
        return paymentService.list(phone, limit).stream().map(PaymentResponse::from).toList();
    }

    /** PDF receipt for a successful payment. The phone must own the payment. */
    @GetMapping("/{id}/receipt")
    public ResponseEntity<byte[]> receipt(@PathVariable UUID id, @RequestParam String phone) {
        Payment payment = paymentService.get(id, phone);
        byte[] pdf = receiptService.receipt(payment);
        String filename = "receipt-" + (payment.getMpesaReceipt() == null ? payment.getId() : payment.getMpesaReceipt()) + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(pdf);
    }
}
