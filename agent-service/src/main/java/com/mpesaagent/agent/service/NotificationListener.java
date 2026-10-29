package com.mpesaagent.agent.service;

import com.mpesaagent.agent.chat.ChatChannel.Button;
import com.mpesaagent.agent.config.AgentProperties;
import com.mpesaagent.agent.config.RabbitConfig;
import com.mpesaagent.agent.dto.ApprovalRequestedEvent;
import com.mpesaagent.agent.dto.C2bReceivedEvent;
import com.mpesaagent.agent.dto.OperationDoneEvent;
import com.mpesaagent.agent.dto.PaymentDoneEvent;
import com.mpesaagent.agent.dto.WalletActionEvent;
import com.mpesaagent.agent.model.IntentType;
import com.mpesaagent.agent.store.DedupeStore;
import com.mpesaagent.agent.store.IntentStore;
import com.mpesaagent.agent.util.Phones;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Tells people what happened after the fact: a payment finished, a refund was decided, an approver is needed.
 * RabbitMQ can deliver a message twice, so each notification is guarded by a once-only key. If sending fails the
 * key is released and the error is rethrown, which makes the broker retry (configured in application.yml).
 */
@Component
public class NotificationListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationListener.class);

    private final ReplySender sender;
    private final RoleService roles;
    private final IntentStore intents;
    private final DedupeStore dedupe;
    private final AgentProperties props;

    public NotificationListener(ReplySender sender, RoleService roles, IntentStore intents, DedupeStore dedupe, AgentProperties props) {
        this.sender = sender;
        this.roles = roles;
        this.intents = intents;
        this.dedupe = dedupe;
        this.props = props;
    }

    @RabbitListener(queues = RabbitConfig.PAYMENT_DONE_QUEUE)
    public void onPaymentDone(PaymentDoneEvent event) {
        String text = Notifications.payment(event);
        if (text == null || event.phone() == null) {
            return;
        }
        once("payment:" + event.paymentId() + ":" + event.status(), () -> {
            List<Button> buttons = "SUCCESS".equals(event.status())
                    ? List.of(new Button("RCPT:" + event.paymentId(), "Get receipt")) : List.of();
            sender.notifyWithButtons(event.phone(), text, null, buttons);
        });
    }

    @RabbitListener(queues = RabbitConfig.OPERATION_DONE_QUEUE)
    public void onOperationDone(OperationDoneEvent event) {
        if (event.requestedBy() == null) {
            return;
        }
        boolean staff = roles.identify(event.requestedBy()).isStaff();
        String text = Notifications.operation(event, staff);
        if (text == null) {
            return;
        }
        once("operation:" + event.operationId() + ":" + event.status(), () -> {
            sender.notifyText(event.requestedBy(), text);
            // A refund that could not be paid out needs a human, so the team is told as well
            if ("REFUND".equals(event.type()) && ("FAILED".equals(event.status()) || "TIMEOUT".equals(event.status()))) {
                alertTeam("Refund " + event.operationId() + " for " + Phones.mask(event.requestedBy())
                        + " did not complete (" + event.status() + "). " + (event.reason() == null ? "" : event.reason()));
            }
        });
    }

    @RabbitListener(queues = RabbitConfig.OPERATION_APPROVAL_QUEUE)
    public void onApprovalRequested(ApprovalRequestedEvent event) {
        if (event.approverPhones() == null || event.approverPhones().isEmpty()) {
            log.warn("Operation {} needs approval but no approvers are configured", event.operationId());
            return;
        }
        String text = Notifications.approval(event);
        String requester = Phones.normalize(event.requestedBy());
        for (String raw : event.approverPhones()) {
            String approver = Phones.normalize(raw);
            if (approver.equals(requester)) {
                continue;   // payment-service would refuse a self approval anyway
            }
            try {
                once("approval:" + event.operationId() + ":" + approver, () -> {
                    var intent = intents.create(IntentType.APPROVAL, approver,
                            Map.of("operationId", event.operationId().toString()), props.approvalIntentTtl());
                    sender.notifyWithButtons(approver, text, null,
                            List.of(new Button("APPROVE:" + intent.id(), "Approve"), new Button("REJECT:" + intent.id(), "Reject")));
                });
            } catch (RuntimeException e) {
                // One unreachable approver must not stop the others from being told
                log.error("Could not notify approver {}: {}", Phones.mask(approver), e.getMessage());
            }
        }
    }

    @RabbitListener(queues = RabbitConfig.C2B_RECEIVED_QUEUE)
    public void onC2bReceived(C2bReceivedEvent event) {
        // The customer's own number is only visible when they typed it as the account number
        String phone = Phones.normalize(event.billRefNumber());
        if (!Phones.isKenyanMobile(phone) || event.transId() == null) {
            return;
        }
        once("c2b:" + event.transId(), () -> sender.notifyText(phone, Notifications.c2b(event)));
    }

    @RabbitListener(queues = RabbitConfig.WALLET_ACTION_QUEUE)
    public void onWalletAction(WalletActionEvent event) {
        String text = Notifications.walletAction(event);
        String phone = Phones.normalize(event.phone());
        if (text == null || !Phones.isKenyanMobile(phone)) {
            return;
        }
        once("wallet:" + event.actionId() + ":" + event.status() + ":" + event.verification(),
                () -> sender.notifyText(phone, text));
    }

    private void alertTeam(String message) {
        for (String phone : props.escalationPhones()) {
            try {
                sender.notifyText(Phones.normalize(phone), message);
            } catch (RuntimeException e) {
                log.error("Could not alert {}: {}", Phones.mask(Phones.normalize(phone)), e.getMessage());
            }
        }
    }

    private void once(String key, Runnable action) {
        String guard = "notify:" + key;
        if (!dedupe.firstTime(guard)) {
            log.info("Notification {} was already sent", key);
            return;
        }
        try {
            action.run();
        } catch (RuntimeException e) {
            dedupe.forget(guard);
            throw e;
        }
    }
}
