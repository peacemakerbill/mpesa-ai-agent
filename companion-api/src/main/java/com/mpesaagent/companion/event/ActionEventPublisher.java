package com.mpesaagent.companion.event;

import com.mpesaagent.companion.config.RabbitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Sends events only after the database change is committed. A broker outage is logged, never shown to the app. */
@Component
public class ActionEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ActionEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public ActionEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAction(ActionEvent event) {
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.WALLET_EXCHANGE, RabbitConfig.ACTION_KEY, event);
            log.info("Published wallet action {} {}", event.actionId(), event.status());
        } catch (AmqpException e) {
            log.error("Could not publish wallet action {} ({}). The customer was not notified in chat.", event.actionId(), event.status(), e);
        }
    }
}
