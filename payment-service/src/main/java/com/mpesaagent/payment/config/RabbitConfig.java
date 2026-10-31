package com.mpesaagent.payment.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class RabbitConfig {

    public static final String PAYMENTS_EXCHANGE = "payments.events";
    public static final String PAYMENT_DONE_KEY = "payment.done";
    public static final String OPERATION_DONE_KEY = "operation.done";
    public static final String OPERATION_APPROVAL_KEY = "operation.approval";
    public static final String C2B_RECEIVED_KEY = "c2b.received";

    @Bean
    TopicExchange paymentsExchange() {
        return new TopicExchange(PAYMENTS_EXCHANGE, true, false);
    }

    /** Spring's JsonMapper writes dates as ISO text, which is friendlier for other services. */
    @Bean
    MessageConverter jsonMessageConverter(JsonMapper jsonMapper) {
        return new JacksonJsonMessageConverter(jsonMapper);
    }
}