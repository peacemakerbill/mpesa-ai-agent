package com.mpesaagent.companion.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class RabbitConfig {

    public static final String WALLET_EXCHANGE = "wallet.events";
    public static final String ACTION_KEY = "wallet.action";

    @Bean
    TopicExchange walletExchange() {
        return new TopicExchange(WALLET_EXCHANGE, true, false);
    }

    /**
     * Spring Boot 4 uses Jackson 3, so the AMQP converter is the Jackson 3 one. It shares the application's JsonMapper, which
     * writes dates as ISO text, the format the agent service already reads.
     */
    @Bean
    MessageConverter jsonMessageConverter(JsonMapper jsonMapper) {
        return new JacksonJsonMessageConverter(jsonMapper);
    }
}