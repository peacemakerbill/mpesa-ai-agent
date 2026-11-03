package com.darajaagentx.api.service;

import com.darajaagentx.api.util.Phones;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Puts a message into a customer's chat through agent-service. Used when Safaricom's answer arrives later, for example
 * the result of a payout. If the chat cannot be reached the message is only logged: the result is still saved and
 * shows in the app.
 */
@Component
public class AgentNotifier implements ChatNotifier {

    private static final Logger log = LoggerFactory.getLogger(AgentNotifier.class);

    private final RestClient agent;

    public AgentNotifier(@Qualifier("agentRestClient") RestClient agent) {
        this.agent = agent;
    }

    @Override
    public void text(String phone, String text) {
        send(phone, Map.of("text", text));
    }

    @Override
    public void image(String phone, String pngBase64, String caption) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("text", caption);
        body.put("imageBase64", pngBase64);
        send(phone, body);
    }

    private void send(String phone, Map<String, Object> body) {
        try {
            agent.post().uri("/internal/notify/{phone}", phone)
                    .contentType(MediaType.APPLICATION_JSON).body(body)
                    .retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("Could not put a message in the chat of {}: {}", Phones.mask(phone), e.getMessage());
        }
    }
}
