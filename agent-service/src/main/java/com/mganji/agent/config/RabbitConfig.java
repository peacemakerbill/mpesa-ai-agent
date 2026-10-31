package com.mganji.agent.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.DefaultJacksonJavaTypeMapper;
import org.springframework.amqp.support.converter.JacksonJavaTypeMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/** Declares our own queues and binds them to the exchange payment-service publishes to. */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "payments.events";
    public static final String PAYMENT_DONE_QUEUE = "agent.payment.done";
    public static final String OPERATION_DONE_QUEUE = "agent.operation.done";
    public static final String OPERATION_APPROVAL_QUEUE = "agent.operation.approval";
    public static final String C2B_RECEIVED_QUEUE = "agent.c2b.received";
    public static final String WALLET_EXCHANGE = "wallet.events";
    public static final String WALLET_ACTION_QUEUE = "agent.wallet.action";

    @Bean
    TopicExchange paymentsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    Queue paymentDoneQueue() {
        return new Queue(PAYMENT_DONE_QUEUE, true);
    }

    @Bean
    Queue operationDoneQueue() {
        return new Queue(OPERATION_DONE_QUEUE, true);
    }

    @Bean
    Queue operationApprovalQueue() {
        return new Queue(OPERATION_APPROVAL_QUEUE, true);
    }

    @Bean
    Queue c2bReceivedQueue() {
        return new Queue(C2B_RECEIVED_QUEUE, true);
    }

    @Bean
    Binding paymentDoneBinding(Queue paymentDoneQueue, TopicExchange paymentsExchange) {
        return BindingBuilder.bind(paymentDoneQueue).to(paymentsExchange).with("payment.done");
    }

    @Bean
    Binding operationDoneBinding(Queue operationDoneQueue, TopicExchange paymentsExchange) {
        return BindingBuilder.bind(operationDoneQueue).to(paymentsExchange).with("operation.done");
    }

    @Bean
    Binding operationApprovalBinding(Queue operationApprovalQueue, TopicExchange paymentsExchange) {
        return BindingBuilder.bind(operationApprovalQueue).to(paymentsExchange).with("operation.approval");
    }

    @Bean
    Binding c2bReceivedBinding(Queue c2bReceivedQueue, TopicExchange paymentsExchange) {
        return BindingBuilder.bind(c2bReceivedQueue).to(paymentsExchange).with("c2b.received");
    }

    @Bean
    TopicExchange walletExchange() {
        return new TopicExchange(WALLET_EXCHANGE, true, false);
    }

    @Bean
    Queue walletActionQueue() {
        return new Queue(WALLET_ACTION_QUEUE, true);
    }

    @Bean
    Binding walletActionBinding(Queue walletActionQueue, TopicExchange walletExchange) {
        return BindingBuilder.bind(walletActionQueue).to(walletExchange).with("wallet.action");
    }

    /**
     * payment-service and ganji-api write their own Java class names into each message. We do not have that class, so the
     * converter must use the type of the listener method's parameter instead (INFERRED).
     */
    @Bean
    MessageConverter jsonMessageConverter(JsonMapper jsonMapper) {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter(jsonMapper);
        DefaultJacksonJavaTypeMapper typeMapper = new DefaultJacksonJavaTypeMapper();
        typeMapper.setTypePrecedence(JacksonJavaTypeMapper.TypePrecedence.INFERRED);
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}