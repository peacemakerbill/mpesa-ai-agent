package com.mpesaagent.agent.client;

import com.mpesaagent.agent.dto.OperationView;
import com.mpesaagent.agent.dto.PaymentView;
import com.mpesaagent.agent.dto.QrView;
import com.mpesaagent.agent.dto.StaffView;
import com.mpesaagent.agent.exception.PaymentApiException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Everything the agent asks payment-service to do. Errors become PaymentApiException with a message that is
 * safe to show to a person. Who is allowed to do what is decided by payment-service, not here.
 */
@Component
public class PaymentClient {

    private final RestClient http;
    private final ObjectMapper mapper;

    public PaymentClient(RestClient paymentRestClient, ObjectMapper mapper) {
        this.http = paymentRestClient;
        this.mapper = mapper;
    }

    // ------------------------------------------------------------ payments

    public PaymentView createPayment(String idempotencyKey, String phone, int amount, String reference) {
        return call(() -> http.post().uri("/internal/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("idempotencyKey", idempotencyKey, "phone", phone, "amount", amount, "accountReference", reference))
                .retrieve().onStatus(s -> s.isError(), this::fail).body(PaymentView.class));
    }

    /** With a phone, the payment must belong to it. Pass null only for staff lookups. */
    public PaymentView getPayment(UUID id, String ownerPhone) {
        return call(() -> http.get().uri(b -> {
                    var u = b.path("/internal/payments/{id}");
                    if (ownerPhone != null) {
                        u.queryParam("phone", ownerPhone);
                    }
                    return u.build(id);
                })
                .retrieve().onStatus(s -> s.isError(), this::fail).body(PaymentView.class));
    }

    public Optional<PaymentView> latest(String phone) {
        try {
            return Optional.ofNullable(call(() -> http.get().uri("/internal/payments/latest?phone={p}", phone)
                    .retrieve().onStatus(s -> s.isError(), this::fail).body(PaymentView.class)));
        } catch (PaymentApiException e) {
            if (e.isNotFound()) {
                return Optional.empty();
            }
            throw e;
        }
    }

    public List<PaymentView> list(String phone, int limit) {
        List<PaymentView> result = call(() -> http.get().uri("/internal/payments?phone={p}&limit={l}", phone, limit)
                .retrieve().onStatus(s -> s.isError(), this::fail)
                .body(new ParameterizedTypeReference<List<PaymentView>>() {
                }));
        return result == null ? List.of() : result;
    }

    public byte[] receipt(UUID paymentId, String phone) {
        return call(() -> http.get().uri("/internal/payments/{id}/receipt?phone={p}", paymentId, phone)
                .accept(MediaType.APPLICATION_PDF)
                .retrieve().onStatus(s -> s.isError(), this::fail).body(byte[].class));
    }

    // ------------------------------------------------------------ operations

    public OperationView createOperation(Map<String, Object> body) {
        return call(() -> http.post().uri("/internal/operations")
                .contentType(MediaType.APPLICATION_JSON).body(body)
                .retrieve().onStatus(s -> s.isError(), this::fail).body(OperationView.class));
    }

    public OperationView getOperation(UUID id, String actorPhone) {
        return call(() -> http.get().uri("/internal/operations/{id}?actorPhone={a}", id, actorPhone)
                .retrieve().onStatus(s -> s.isError(), this::fail).body(OperationView.class));
    }

    public List<OperationView> myOperations(String actorPhone) {
        List<OperationView> result = call(() -> http.get().uri("/internal/operations?actorPhone={a}", actorPhone)
                .retrieve().onStatus(s -> s.isError(), this::fail)
                .body(new ParameterizedTypeReference<List<OperationView>>() {
                }));
        return result == null ? List.of() : result;
    }

    public List<OperationView> pendingApprovals(String actorPhone) {
        List<OperationView> result = call(() -> http.get().uri("/internal/operations/pending?actorPhone={a}", actorPhone)
                .retrieve().onStatus(s -> s.isError(), this::fail)
                .body(new ParameterizedTypeReference<List<OperationView>>() {
                }));
        return result == null ? List.of() : result;
    }

    public OperationView approve(UUID id, String actorPhone) {
        return call(() -> http.post().uri("/internal/operations/{id}/approve", id)
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("actorPhone", actorPhone))
                .retrieve().onStatus(s -> s.isError(), this::fail).body(OperationView.class));
    }

    public OperationView reject(UUID id, String actorPhone, String reason) {
        Map<String, Object> body = reason == null || reason.isBlank()
                ? Map.of("actorPhone", actorPhone) : Map.of("actorPhone", actorPhone, "reason", reason);
        return call(() -> http.post().uri("/internal/operations/{id}/reject", id)
                .contentType(MediaType.APPLICATION_JSON).body(body)
                .retrieve().onStatus(s -> s.isError(), this::fail).body(OperationView.class));
    }

    // ------------------------------------------------------------ other

    public StaffView staff(String phone) {
        return call(() -> http.get().uri("/internal/staff/{p}", phone)
                .retrieve().onStatus(s -> s.isError(), this::fail).body(StaffView.class));
    }

    public String qr(int amount, String reference) {
        QrView view = call(() -> http.post().uri("/internal/qr")
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("amount", amount, "accountReference", reference))
                .retrieve().onStatus(s -> s.isError(), this::fail).body(QrView.class));
        if (view == null || view.qrCodeBase64() == null || view.qrCodeBase64().isBlank()) {
            throw new PaymentApiException(502, "M-Pesa did not return a QR code");
        }
        return view.qrCodeBase64();
    }

    // ------------------------------------------------------------ plumbing

    private <T> T call(Supplier<T> action) {
        try {
            return action.get();
        } catch (PaymentApiException e) {
            throw e;
        } catch (RestClientException e) {
            throw new PaymentApiException(0, "The payment service is not reachable right now");
        }
    }

    private void fail(org.springframework.http.HttpRequest request,
                      org.springframework.http.client.ClientHttpResponse response) throws IOException {
        int status = response.getStatusCode().value();
        String message = null;
        try {
            JsonNode body = mapper.readTree(response.getBody().readAllBytes());
            message = body.path("message").asString(null);
        } catch (IOException | JacksonException ignored) {
            // not JSON, use the default below
        }
        if (message == null || message.isBlank()) {
            message = status >= 500 ? "The payment service had a problem" : "The request was not accepted";
        }
        throw new PaymentApiException(status, message);
    }
}