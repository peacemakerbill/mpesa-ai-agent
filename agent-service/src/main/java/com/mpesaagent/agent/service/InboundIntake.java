package com.mpesaagent.agent.service;

import com.mpesaagent.agent.chat.ChatLog;
import com.mpesaagent.agent.chat.ChatMessage;
import com.mpesaagent.agent.dto.InboundMessage;
import com.mpesaagent.agent.store.DedupeStore;
import com.mpesaagent.agent.util.Phones;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Takes in one customer message: ignores repeats, writes it into the customer's chat, and hands it to our own
 * threads. The caller gets an answer straight away because the model and M-Pesa can take a while.
 */
@Component
public class InboundIntake {

    public enum Result { ACCEPTED, DUPLICATE, BUSY }

    private static final Logger log = LoggerFactory.getLogger(InboundIntake.class);

    private final DedupeStore dedupe;
    private final MessageProcessor processor;
    private final ChatLog chatLog;
    private final Executor executor;

    public InboundIntake(DedupeStore dedupe, MessageProcessor processor, ChatLog chatLog,
                         @Qualifier("inboundExecutor") Executor executor) {
        this.dedupe = dedupe;
        this.processor = processor;
        this.chatLog = chatLog;
        this.executor = executor;
    }

    public Result accept(InboundMessage message) {
        if (!dedupe.firstTime(message.messageId())) {
            log.info("Ignoring duplicate message {}", message.messageId());
            return Result.DUPLICATE;
        }
        chatLog.append(Phones.normalize(message.from()), ChatMessage.fromInbound(message));
        try {
            executor.execute(() -> processor.process(message));
        } catch (RejectedExecutionException e) {
            // Too busy: the app can send it again, so let the same id through next time
            dedupe.forget(message.messageId());
            log.error("Inbound queue is full, rejecting message {}", message.messageId());
            return Result.BUSY;
        }
        return Result.ACCEPTED;
    }
}
