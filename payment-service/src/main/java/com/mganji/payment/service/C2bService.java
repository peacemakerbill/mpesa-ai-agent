package com.mganji.payment.service;

import com.mganji.payment.dto.C2bPayload;
import com.mganji.payment.dto.DarajaAsyncResponse;
import com.mganji.payment.event.C2bReceivedEvent;
import com.mganji.payment.model.C2bTransaction;
import com.mganji.payment.repository.C2bTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Handles customers who pay our Paybill or Till directly, outside the chat. */
@Service
public class C2bService {

    private static final Logger log = LoggerFactory.getLogger(C2bService.class);

    private final C2bTransactionRepository repository;
    private final ApplicationEventPublisher events;
    private final MpesaOperationsClient client;

    public C2bService(C2bTransactionRepository repository, ApplicationEventPublisher events, MpesaOperationsClient client) {
        this.repository = repository;
        this.events = events;
        this.client = client;
    }

    /** One time setup per shortcode. Run again whenever PUBLIC_BASE_URL or the callback secret changes. */
    public DarajaAsyncResponse registerUrls() {
        return client.registerC2bUrls();
    }

    /**
     * Stores a confirmed payment once. Safaricom can send the same confirmation twice, so the transaction id
     * is unique.
     *
     * @return false when this transaction was already recorded
     */
    @Transactional
    public boolean recordConfirmation(C2bPayload payload) {
        if (repository.existsByTransId(payload.transId())) {
            log.info("Ignoring duplicate C2B confirmation {}", payload.transId());
            return false;
        }
        C2bTransaction saved = repository.save(new C2bTransaction(
                payload.transId(), payload.transactionType(), payload.transTime(), payload.amount(),
                payload.businessShortCode(), payload.billRefNumber(), payload.msisdn(),
                payload.payerName(), payload.orgAccountBalance()));
        log.info("C2B payment {} of {} recorded for account {}", saved.getTransId(), saved.getAmount(), saved.getBillRefNumber());
        events.publishEvent(C2bReceivedEvent.from(saved));
        return true;
    }
}
