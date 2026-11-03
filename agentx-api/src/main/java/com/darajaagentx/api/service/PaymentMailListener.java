package com.darajaagentx.api.service;

import com.darajaagentx.api.event.ActionEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Emails the customer when a payment made from the app is reported done. It listens to the same event that puts the news
 * in the chat, so ActionService needs no change.
 */
@Component
public class PaymentMailListener {

    private final CustomerMailer mailer;

    public PaymentMailListener(CustomerMailer mailer) {
        this.mailer = mailer;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAction(ActionEvent event) {
        if (!"DONE_REPORTED".equals(event.status())) {
            return;
        }
        mailer.paymentDone(event.phone(), "Payment", event.summary(), event.receipt());
    }
}
