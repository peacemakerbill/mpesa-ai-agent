package com.darajaagentx.api.service;

import com.darajaagentx.api.config.DarajaProperties;
import com.darajaagentx.api.model.DarajaEnvironment;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;

/** Calls Daraja's OAuth endpoint (/oauth/v1/generate) with the key and secret. The key and secret are never logged. */
@Component
public class DarajaTokenProbe implements TokenProbe {

    private final DarajaProperties props;

    public DarajaTokenProbe(DarajaProperties props) {
        this.props = props;
    }

    @Override
    public Outcome probe(DarajaEnvironment environment, String consumerKey, String consumerSecret) {
        String base = environment == DarajaEnvironment.PRODUCTION ? props.productionUrl() : props.sandboxUrl();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        try {
            String body = RestClient.builder().requestFactory(factory).baseUrl(base).build()
                    .get().uri("/oauth/v1/generate?grant_type=client_credentials")
                    .headers(h -> h.setBasicAuth(consumerKey, consumerSecret))
                    .retrieve().body(String.class);
            return body != null && body.contains("access_token") ? new Outcome(Kind.ACCEPTED, 200) : new Outcome(Kind.ERROR, 200);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            return new Outcome(status == 400 || status == 401 ? Kind.REFUSED : Kind.ERROR, status);
        } catch (RestClientException e) {
            return new Outcome(Kind.UNREACHABLE, 0);
        }
    }
}
