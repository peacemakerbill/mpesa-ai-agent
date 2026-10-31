package com.mganji.agent.service;

import com.mganji.agent.client.GanjiClient;
import com.mganji.agent.client.PaymentClient;
import com.mganji.agent.dto.OperationView;
import com.mganji.agent.dto.PaymentView;
import com.mganji.agent.exception.GanjiApiException;
import com.mganji.agent.exception.PaymentApiException;
import com.mganji.agent.model.Intent;
import com.mganji.agent.model.IntentType;
import com.mganji.agent.store.IntentStore;
import com.mganji.agent.util.Money;
import com.mganji.agent.util.Phones;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Acts on button taps. This is the only place where money starts moving or a request is submitted, and it only
 * acts on a stored intent that belongs to the person who tapped, once. A tap carries just an id: the amount and
 * recipient come from the server side intent, so they cannot be changed from the chat.
 */
@Component
public class ButtonHandler {

    private static final Logger log = LoggerFactory.getLogger(ButtonHandler.class);

    static final String EXPIRED =
            "That confirmation has expired or was already used. If you were making a payment, ask me for your payment status before trying again, so you are not charged twice.";

    private final IntentStore intents;
    private final PaymentClient payments;
    private final ReplySender sender;
    private final GanjiClient ganji;

    public ButtonHandler(IntentStore intents, PaymentClient payments, ReplySender sender, GanjiClient ganji) {
        this.intents = intents;
        this.payments = payments;
        this.sender = sender;
        this.ganji = ganji;
    }

    /** True for ids this handler understands. Other ids (menu choices, template buttons) are handled elsewhere. */
    public static boolean handles(String actionId) {
        return actionId != null && actionId.matches("^(PAY|OK|NO|APPROVE|REJECT|RCPT|WCAN):.+");
    }

    public void handle(String phone, String actionId) {
        int colon = actionId.indexOf(':');
        String action = actionId.substring(0, colon);
        String id = actionId.substring(colon + 1);
        try {
            switch (action) {
                case "PAY" -> pay(phone, id);
                case "OK" -> confirm(phone, id);
                case "NO" -> cancel(phone, id);
                case "APPROVE" -> decide(phone, id, true);
                case "REJECT" -> decide(phone, id, false);
                case "RCPT" -> receipt(phone, id);
                case "WCAN" -> cancelWalletRequest(phone, id);
                default -> sender.text(phone, EXPIRED);
            }
        } catch (PaymentApiException e) {
            log.warn("Button {} failed: {} {}", action, e.getStatus(), e.getMessage());
            sender.text(phone, failureMessage(e));
        } catch (GanjiApiException e) {
            log.warn("Button {} failed: {} {}", action, e.getStatus(), e.getMessage());
            sender.text(phone, ganjiFailure(e));
        }
    }

    // ------------------------------------------------------------ requests for the customer's own M-Pesa

    /** ganji-api checks that the request belongs to the number that tapped, so a forwarded button does nothing. */
    private void cancelWalletRequest(String phone, String requestId) {
        UUID id;
        try {
            id = UUID.fromString(requestId);
        } catch (IllegalArgumentException e) {
            sender.text(phone, EXPIRED);
            return;
        }
        var cancelled = ganji.cancel(id, phone);
        sender.text(phone, "Okay, I've cancelled it: " + cancelled.summary() + ". Nothing was sent.");
    }

    static String ganjiFailure(GanjiApiException e) {
        if (e.isNotFound()) {
            return "I couldn't find that request. It may have expired.";
        }
        if (e.isConflict()) {
            return "That request can't be cancelled any more: it was already approved and taken to M-Pesa, or it has finished. "
                    + "Please check your M-Pesa messages to see what happened.";
        }
        if (e.isUnavailable()) {
            return "I couldn't reach the app service just now. Please try again in a moment.";
        }
        return e.getMessage();
    }

    // ------------------------------------------------------------ payments

    private void pay(String phone, String id) {
        Optional<Intent> taken = intents.take(id, phone, IntentType.PAYMENT);
        if (taken.isEmpty()) {
            sender.text(phone, EXPIRED);
            return;
        }
        Intent intent = taken.get();
        int amount = Integer.parseInt(intent.get("amount"));
        String reference = intent.get("reference");

        // The intent id is the idempotency key: even if this code runs twice, the customer is charged once
        PaymentView payment;
        try {
            payment = payments.createPayment("wa-" + intent.id(), phone, amount, reference);
        } catch (PaymentApiException e) {
            if (!e.isUnavailable()) {
                throw e;   // a clear refusal (limit, invalid number): safe to say nothing was charged
            }
            // A timeout does not tell us whether the prompt went out, so never claim that nothing happened
            log.error("Could not confirm payment creation for intent {}: {}", intent.id(), e.getMessage());
            sender.text(phone, "I could not confirm whether the M-Pesa prompt was sent. Please check your phone. "
                    + "Don't pay again yet: ask me for your payment status in a minute and I'll tell you exactly what happened.");
            return;
        }
        String status = payment.status() == null ? "" : payment.status();
        switch (status) {
            case "PUSHED", "SUCCESS" -> sender.text(phone,
                    "I've sent an M-Pesa prompt for *" + Money.kes(amount) + "* to " + Phones.mask(phone)
                            + ".\n\nPlease enter your M-Pesa PIN on your phone to complete the payment. "
                            + "I'll confirm here as soon as it goes through.");
            case "FAILED", "CANCELLED" -> sender.text(phone,
                    "M-Pesa did not accept the request, so you have not been charged."
                            + (payment.resultDesc() == null ? "" : "\n\nReason: " + payment.resultDesc())
                            + "\n\nWould you like to try again?");
            default -> sender.text(phone,
                    "I could not confirm with M-Pesa whether the prompt was sent. Please check your phone. "
                            + "Don't pay again yet: ask me for your payment status in a minute and I'll tell you exactly what happened.");
        }
    }

    // ------------------------------------------------------------ requests that need a second person

    private void confirm(String phone, String id) {
        Optional<Intent> taken = intents.take(id, phone, IntentType.REFUND, IntentType.PAYOUT, IntentType.REVERSAL);
        if (taken.isEmpty()) {
            sender.text(phone, EXPIRED);
            return;
        }
        Intent intent = taken.get();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("idempotencyKey", "wa-" + intent.id());
        body.put("actorPhone", phone);
        body.put("reason", intent.get("reason"));

        switch (intent.type()) {
            case REFUND -> {
                body.put("type", "REFUND");
                body.put("relatedPaymentId", intent.get("paymentId"));
                body.put("amount", Integer.parseInt(intent.get("amount")));
                OperationView op = payments.createOperation(body);
                sender.text(phone, "Thank you. Your refund request for *" + Money.kes(Integer.parseInt(intent.get("amount")))
                        + "* has been submitted for review (reference " + shortId(op) + ").\n\nI'll message you here as soon as a decision is made.");
            }
            case PAYOUT -> {
                body.put("type", intent.get("type"));
                body.put("recipientPhone", intent.get("recipient"));
                body.put("amount", Integer.parseInt(intent.get("amount")));
                OperationView op = payments.createOperation(body);
                sender.text(phone, "Payout request submitted (reference " + shortId(op) + "). "
                        + "It is waiting for another approver, and I'll message you when it is decided.");
            }
            case REVERSAL -> {
                body.put("type", "REVERSAL");
                body.put("targetReceipt", intent.get("receipt"));
                body.put("amount", Integer.parseInt(intent.get("amount")));
                OperationView op = payments.createOperation(body);
                sender.text(phone, "Reversal request submitted (reference " + shortId(op) + "). It is waiting for an approver.");
            }
            default -> sender.text(phone, EXPIRED);
        }
    }

    private void decide(String phone, String id, boolean approve) {
        Optional<Intent> taken = intents.take(id, phone, IntentType.APPROVAL);
        if (taken.isEmpty()) {
            sender.text(phone, EXPIRED);
            return;
        }
        UUID operationId = UUID.fromString(taken.get().get("operationId"));
        if (approve) {
            payments.approve(operationId, phone);
            sender.text(phone, "Approved. The request has been sent to M-Pesa. I'll confirm here when it completes.");
        } else {
            payments.reject(operationId, phone, "Rejected by approver in the chat");
            sender.text(phone, "Rejected. The person who asked for it will be told.");
        }
    }

    private void cancel(String phone, String id) {
        boolean cancelled = intents.discard(id, phone);
        sender.text(phone, cancelled
                ? "Okay, I've cancelled that. Nothing was charged or sent."
                : "That request had already expired or been used.");
    }

    private void receipt(String phone, String paymentId) {
        UUID id;
        try {
            id = UUID.fromString(paymentId);
        } catch (IllegalArgumentException e) {
            sender.text(phone, "I couldn't find that receipt. Ask me for \"my receipt\" and I'll send it.");
            return;
        }
        byte[] pdf = payments.receipt(id, phone);   // payment-service checks that the payment belongs to this phone
        sender.document(phone, pdf, "application/pdf", "receipt.pdf", "Your payment receipt");
    }

    // ------------------------------------------------------------ text

    private static String shortId(OperationView op) {
        return op == null || op.id() == null ? "" : op.id().toString().substring(0, 8);
    }

    static String failureMessage(PaymentApiException e) {
        if (e.isForbidden()) {
            return "You do not have permission to do that.";
        }
        if (e.isUnavailable()) {
            return "I couldn't reach the payment system just now. Before trying again, please ask me for your payment status or your requests, so nothing is done twice.";
        }
        if (e.isNotFound()) {
            return "I couldn't find that. It may have been removed.";
        }
        return e.getMessage() + " Nothing was charged.";
    }
}
