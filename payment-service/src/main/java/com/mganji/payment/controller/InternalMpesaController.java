package com.mganji.payment.controller;

import com.mganji.payment.config.LimitsProperties;
import com.mganji.payment.dto.DarajaAsyncResponse;
import com.mganji.payment.dto.QrRequest;
import com.mganji.payment.dto.QrResponse;
import com.mganji.payment.exception.LimitExceededException;
import com.mganji.payment.service.C2bService;
import com.mganji.payment.service.MpesaOperationsClient;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Small Daraja helpers that are not tied to one person's request. */
@RestController
@RequestMapping("/internal")
public class InternalMpesaController {

    private final MpesaOperationsClient client;
    private final C2bService c2b;
    private final LimitsProperties limits;

    public InternalMpesaController(MpesaOperationsClient client, C2bService c2b, LimitsProperties limits) {
        this.client = client;
        this.c2b = c2b;
        this.limits = limits;
    }

    /** A dynamic M-Pesa QR code the customer can scan in the M-Pesa app. Returned as Base64 PNG. */
    @PostMapping("/qr")
    public QrResponse qr(@Valid @RequestBody QrRequest request) {
        if (request.amount() > limits.perTransaction()) {
            throw new LimitExceededException("Amount is above the per-transaction limit of KES " + limits.perTransaction());
        }
        return new QrResponse(client.generateQr(request.amount(), request.accountReference()));
    }

    /** Run once per shortcode to receive direct Paybill and Till payments. */
    @PostMapping("/c2b/register")
    public DarajaAsyncResponse registerC2b() {
        return c2b.registerUrls();
    }
}
