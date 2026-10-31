package com.mganji.payment.service;

import com.mganji.payment.config.MpesaProperties;
import com.mganji.payment.dto.StkPushResponse;
import com.mganji.payment.dto.StkQueryResponse;
import com.mganji.payment.exception.MpesaApiException;
import com.mganji.payment.exception.MpesaCommunicationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thin client for the Daraja APIs we need: OAuth, STK Push and STK Push query.
 * It only talks HTTP. Business rules and state live in the other services.
 */
@Service
public class MpesaClient {

    private static final Logger log = LoggerFactory.getLogger(MpesaClient.class);
    private static final ZoneId NAIROBI = ZoneId.of("Africa/Nairobi");
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final RestClient http;
    private final MpesaProperties props;
    private final ObjectMapper objectMapper;

    private String cachedToken;
    private Instant tokenExpiresAt = Instant.EPOCH;

    public MpesaClient(RestClient mpesaRestClient, MpesaProperties props, ObjectMapper objectMapper) {
        this.http = mpesaRestClient;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    /**
     * Asks Daraja to show the M-Pesa PIN prompt on the customer's phone.
     *
     * @throws MpesaApiException           Daraja refused the request, nothing was sent to the customer
     * @throws MpesaCommunicationException the outcome is unknown (timeout or network error)
     */
    public StkPushResponse stkPush(String phone, int amount, String accountReference) {
        String token = accessToken();
        String timestamp = LocalDateTime.now(NAIROBI).format(TIMESTAMP);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("BusinessShortCode", props.shortcode());
        body.put("Password", password(timestamp));
        body.put("Timestamp", timestamp);
        body.put("TransactionType", props.transactionType());
        body.put("Amount", amount);
        body.put("PartyA", phone);
        body.put("PartyB", props.shortcode());
        body.put("PhoneNumber", phone);
        body.put("CallBackURL", props.stkCallbackUrl());
        body.put("AccountReference", accountReference);
        body.put("TransactionDesc", "Payment");   // Daraja allows at most 13 characters

        try {
            StkPushResponse response = http.post()
                    .uri("/mpesa/stkpush/v1/processrequest")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(StkPushResponse.class);
            if (response == null) {
                throw new MpesaCommunicationException("Empty response from M-Pesa", null);
            }
            return response;
        } catch (RestClientResponseException e) {
            throw apiError("STK push", e);
        } catch (ResourceAccessException e) {
            throw new MpesaCommunicationException("Could not reach M-Pesa for STK push", e);
        } catch (RestClientException e) {
            throw new MpesaCommunicationException("Unreadable response from M-Pesa for STK push", e);
        }
    }

    /**
     * Asks Daraja for the outcome of an earlier STK push. While the customer is still deciding, Daraja
     * answers with an error ("being processed"), which surfaces as {@link MpesaApiException}.
     */
    public StkQueryResponse stkQuery(String checkoutRequestId) {
        String token = accessToken();
        String timestamp = LocalDateTime.now(NAIROBI).format(TIMESTAMP);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("BusinessShortCode", props.shortcode());
        body.put("Password", password(timestamp));
        body.put("Timestamp", timestamp);
        body.put("CheckoutRequestID", checkoutRequestId);

        try {
            StkQueryResponse response = http.post()
                    .uri("/mpesa/stkpushquery/v1/query")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(StkQueryResponse.class);
            if (response == null) {
                throw new MpesaCommunicationException("Empty response from M-Pesa", null);
            }
            return response;
        } catch (RestClientResponseException e) {
            throw apiError("STK query", e);
        } catch (RestClientException e) {
            throw new MpesaCommunicationException("Could not query M-Pesa", e);
        }
    }

    /**
     * Authenticated JSON POST to any Daraja endpoint, with the same error handling as STK push.
     * Used by MpesaOperationsClient for B2C, balance, status, reversal, C2B and QR.
     *
     * @throws MpesaApiException           Daraja answered with an error
     * @throws MpesaCommunicationException the outcome is unknown (timeout or network error)
     */
    public <T> T postJson(String operation, String uri, Map<String, Object> body, Class<T> responseType) {
        String token = accessToken();
        try {
            T response = http.post()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(responseType);
            if (response == null) {
                throw new MpesaCommunicationException("Empty response from M-Pesa for " + operation, null);
            }
            return response;
        } catch (RestClientResponseException e) {
            throw apiError(operation, e);
        } catch (ResourceAccessException e) {
            throw new MpesaCommunicationException("Could not reach M-Pesa for " + operation, e);
        } catch (RestClientException e) {
            throw new MpesaCommunicationException("Unreadable response from M-Pesa for " + operation, e);
        }
    }

    private String password(String timestamp) {
        String raw = props.shortcode() + props.passkey() + timestamp;
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** OAuth tokens last about an hour, so we cache one and refresh a minute before it expires. */
    private synchronized String accessToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt)) {
            return cachedToken;
        }
        String credentials = props.consumerKey() + ":" + props.consumerSecret();
        String basic = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        try {
            JsonNode body = http.get()
                    .uri("/oauth/v1/generate?grant_type=client_credentials")
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null || !body.hasNonNull("access_token")) {
                throw new MpesaApiException("AUTH", "M-Pesa returned no access token");
            }
            long expiresIn = body.path("expires_in").asLong(3599);
            cachedToken = body.get("access_token").asString();
            tokenExpiresAt = Instant.now().plusSeconds(Math.max(expiresIn - 60, 30));
            log.debug("Obtained a new M-Pesa access token");
            return cachedToken;
        } catch (RestClientException e) {
            // Nothing was sent to the customer yet, so callers can treat this as a clean failure.
            log.error("M-Pesa authentication failed: {}", e.getMessage());
            throw new MpesaApiException("AUTH", "Could not authenticate with M-Pesa");
        }
    }

    private synchronized void invalidateToken() {
        cachedToken = null;
        tokenExpiresAt = Instant.EPOCH;
    }

    private MpesaApiException apiError(String operation, RestClientResponseException e) {
        if (e.getStatusCode().value() == 401) {
            invalidateToken();
        }
        String code = String.valueOf(e.getStatusCode().value());
        String message = "M-Pesa rejected the request";
        try {
            JsonNode node = objectMapper.readTree(e.getResponseBodyAsString());
            if (node.hasNonNull("errorCode")) {
                code = node.get("errorCode").asString();
            }
            if (node.hasNonNull("errorMessage")) {
                message = node.get("errorMessage").asString();
            }
        } catch (Exception ignored) {
            // body was not JSON, keep the defaults
        }
        log.warn("{} failed: http={} code={} message={}", operation, e.getStatusCode().value(), code, message);
        return new MpesaApiException(code, message);
    }
}