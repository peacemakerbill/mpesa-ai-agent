package com.mpesaagent.companion.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    /** The chat lives in agent-service. This service signs people in and passes their chat through. */
    @Bean
    public RestClient agentRestClient(AgentProperties props, InternalProperties internal) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(30));
        return RestClient.builder()
                .baseUrl(props.baseUrl())
                .defaultHeader(InternalKeyFilter.HEADER, internal.key())
                .requestFactory(factory)
                .build();
    }
}
