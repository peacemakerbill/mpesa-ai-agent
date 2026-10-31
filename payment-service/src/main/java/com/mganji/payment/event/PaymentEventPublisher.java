package com.mganji.payment.event;

import com.mganji.payment.config.RabbitConfig;
import com.mganji.payment.util.PhoneNumberUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends events only after the database transaction has committed, so the agent never hears about
 * a result that was rolled back. A broker outage is logged and never fails an M-Pesa callback.
 */
@Component
public class PaymentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public PaymentEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        publish(RabbitConfig.PAYMENT_DONE_KEY, event,
                "payment " + event.paymentId() + " " + event.status() + " " + PhoneNumberUtil.mask(event.phone()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOperationCompleted(OperationCompletedEvent event) {
        publish(RabbitConfig.OPERATION_DONE_KEY, event, "operation " + event.operationId() + " " + event.status());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onApprovalRequested(ApprovalRequestedEvent event) {
        publish(RabbitConfig.OPERATION_APPROVAL_KEY, event, "approval needed for operation " + event.operationId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onC2bReceived(C2bReceivedEvent event) {
        publish(RabbitConfig.C2B_RECEIVED_KEY, event, "C2B payment " + event.transId());
    }

    private void publish(String routingKey, Object event, String description) {
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.PAYMENTS_EXCHANGE, routingKey, event);
            log.info("Published event: {}", description);
        } catch (AmqpException e) {
            log.error("Could not publish event ({}). Nobody was notified.", description, e);
        }
    }
}
