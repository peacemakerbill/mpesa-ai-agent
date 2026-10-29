package com.mpesaagent.agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    private static SimpleClientHttpRequestFactory factory(Duration connect, Duration read) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(connect);
        f.setReadTimeout(read);
        return f;
    }

    @Bean
    public RestClient paymentRestClient(ServiceProperties.Payment props, ServiceProperties.Internal internal) {
        // Starting an STK Push talks to Safaricom, so allow a little longer than a normal call
        return RestClient.builder()
                .baseUrl(props.baseUrl())
                .defaultHeader(InternalKeyFilter.HEADER, internal.key())
                .requestFactory(factory(Duration.ofSeconds(3), Duration.ofSeconds(30)))
                .build();
    }

    @Bean
    public RestClient companionRestClient(ServiceProperties.Companion props, ServiceProperties.Internal internal) {
        return RestClient.builder()
                .baseUrl(props.baseUrl())
                .defaultHeader(InternalKeyFilter.HEADER, internal.key())
                .requestFactory(factory(Duration.ofSeconds(3), Duration.ofSeconds(15)))
                .build();
    }

    @Bean
    public RestClient anthropicRestClient(AnthropicProperties props) {
        return RestClient.builder()
                .baseUrl(props.baseUrl())
                .defaultHeader("x-api-key", props.apiKey())
                .defaultHeader("anthropic-version", props.version())
                .requestFactory(factory(Duration.ofSeconds(5), Duration.ofSeconds(120)))
                .build();
    }

    @Bean
    public RestClient sttRestClient(SpeechProperties props) {
        return RestClient.builder()
                .baseUrl(props.baseUrl())
                .defaultHeader("Authorization", "Bearer " + props.apiKey())
                .requestFactory(factory(Duration.ofSeconds(5), Duration.ofSeconds(120)))
                .build();
    }

    @Bean
    public RestClient ttsRestClient(TtsProperties props) {
        return RestClient.builder()
                .baseUrl(props.baseUrl())
                .defaultHeader("Authorization", "Bearer " + props.apiKey())
                .requestFactory(factory(Duration.ofSeconds(5), Duration.ofSeconds(60)))
                .build();
    }
}
