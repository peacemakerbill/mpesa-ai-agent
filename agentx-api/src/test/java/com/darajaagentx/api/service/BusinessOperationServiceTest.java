package com.darajaagentx.api.service;

import com.darajaagentx.api.config.BusinessOpsProperties;
import com.darajaagentx.api.config.DarajaProperties;
import com.darajaagentx.api.dto.BusinessAccountRequest;
import com.darajaagentx.api.dto.BusinessAccountView;
import com.darajaagentx.api.dto.BusinessOperationView;
import com.darajaagentx.api.exception.ActionNotFoundException;
import com.darajaagentx.api.exception.DarajaOutcomeUnknownException;
import com.darajaagentx.api.exception.DarajaRefusedException;
import com.darajaagentx.api.exception.InvalidActionRequestException;
import com.darajaagentx.api.exception.InvalidActionStateException;
import com.darajaagentx.api.exception.TooManyRequestsException;
import com.darajaagentx.api.model.BusinessAccount;
import com.darajaagentx.api.model.BusinessOperation;
import com.darajaagentx.api.model.BusinessOperationStatus;
import com.darajaagentx.api.model.DarajaEnvironment;
import com.darajaagentx.api.repository.BusinessAccountRepository;
import com.darajaagentx.api.repository.BusinessOperationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What happens between a chat request and Safaricom: who may approve, what goes out, what comes back. In memory stores, a fake
 * Daraja and a fake chat, so nothing leaves the machine.
 */
class BusinessOperationServiceTest {

    static final String OWNER = "254712345678";
    static final String OTHER = "254722000111";
    static final String KEY = "AbCdEfGhIjKlMnOpQrStUvWxYz123456";
    static final String SECRET = "s3cr3tS3cr3tS3cr3t";
    static final String PASS = "Safaricom999!*!";
    static final String PASSKEY = "bfb279f9aa9bdbcf158e97dd71a467cd2e0c893059b10f78e6b72ada1ed2c919";

    /** A clock the test can move forward. */
    static class TestClock extends Clock {
        Instant now = Instant.parse("2026-11-03T08:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }
    }

    /** Remembers every call and answers with whatever the test sets. */
    static class FakeDaraja implements DarajaApi {
        final List<String> paths = new ArrayList<>();
        final List<Map<String, Object>> bodies = new ArrayList<>();
        final List<String> credentialsUsed = new ArrayList<>();
        Map<String, Object> answer = Map.of("ResponseCode", "0", "ResponseDescription", "Accept the service request successfully.", "ConversationID", "AG_1");
        RuntimeException failure;

        @Override
        public Map<String, Object> post(DarajaEnvironment environment, String consumerKey, String consumerSecret, String path, Map<String, Object> body) {
            paths.add(path);
            bodies.add(body);
            credentialsUsed.add(environment + ":" + consumerKey + ":" + consumerSecret);
            if (failure != null) {
                throw failure;
            }
            return answer;
        }

        Map<String, Object> lastBody() {
            return bodies.get(bodies.size() - 1);
        }

        String lastPath() {
            return paths.get(paths.size() - 1);
        }
    }

    static class FakeChat implements ChatNotifier {
        final List<String> texts = new ArrayList<>();

        @Override
        public void text(String phone, String text) {
            texts.add(text);
        }

        @Override
        public void image(String phone, String pngBase64, String caption) {
            texts.add(caption);
        }

        String last() {
            return texts.isEmpty() ? "" : texts.get(texts.size() - 1);
        }
    }

    private final Map<UUID, BusinessAccount> accountRows = new LinkedHashMap<>();
    private final Map<UUID, BusinessOperation> operationRows = new LinkedHashMap<>();
    private final TestClock clock = new TestClock();
    private final FakeDaraja daraja = new FakeDaraja();
    private final FakeChat chat = new FakeChat();
    private BusinessAccountService accounts;
    private BusinessOperationService service;
    private BusinessAccountView shop;
    private BusinessAccountView counter;

    private BusinessAccountRepository accountStore() {
        return (BusinessAccountRepository) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{BusinessAccountRepository.class}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "save" -> {
                    BusinessAccount a = (BusinessAccount) args[0];
                    a.prepareForMongo(Instant.now());
                    accountRows.put(a.getId(), a);
                    return a;
                }
                case "deleteAll" -> {
                    for (Object a : (Iterable<?>) args[0]) {
                        accountRows.remove(((BusinessAccount) a).getId());
                    }
                    return null;
                }
                case "findByOwnerPhoneOrderByCreatedAtAsc" -> {
                    return accountRows.values().stream().filter(a -> a.getOwnerPhone().equals(args[0])).toList();
                }
                case "findByIdAndOwnerPhone" -> {
                    return Optional.ofNullable(accountRows.get((UUID) args[0])).filter(a -> a.getOwnerPhone().equals(args[1]));
                }
                case "findByOwnerPhoneAndParentId" -> {
                    return accountRows.values().stream().filter(a -> a.getOwnerPhone().equals(args[0]) && args[1].equals(a.getParentId())).toList();
                }
                default -> throw new UnsupportedOperationException(method.getName());
            }
        });
    }

    private BusinessOperationRepository operationStore() {
        return (BusinessOperationRepository) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{BusinessOperationRepository.class}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "save" -> {
                    BusinessOperation o = (BusinessOperation) args[0];
                    o.prepareForMongo(Instant.now());
                    operationRows.put(o.getId(), o);
                    return o;
                }
                case "findById" -> {
                    return Optional.ofNullable(operationRows.get((UUID) args[0]));
                }
                case "findByIdAndOwnerPhone" -> {
                    return Optional.ofNullable(operationRows.get((UUID) args[0])).filter(o -> o.getOwnerPhone().equals(args[1]));
                }
                case "findByOwnerPhoneAndIdempotencyKey" -> {
                    return operationRows.values().stream().filter(o -> o.getOwnerPhone().equals(args[0]) && o.getIdempotencyKey().equals(args[1])).findFirst();
                }
                case "findTop30ByOwnerPhoneOrderByCreatedAtDesc" -> {
                    List<BusinessOperation> mine = new ArrayList<>(operationRows.values().stream().filter(o -> o.getOwnerPhone().equals(args[0])).toList());
                    Collections.reverse(mine);
                    return mine.size() > 30 ? mine.subList(0, 30) : mine;
                }
                case "findByOwnerPhoneAndStatus" -> {
                    return operationRows.values().stream().filter(o -> o.getOwnerPhone().equals(args[0]) && o.getStatus() == args[1]).toList();
                }
                default -> throw new UnsupportedOperationException(method.getName());
            }
        });
    }

    private BusinessOperationService serviceWith(BusinessOpsProperties props, DarajaApi api) {
        return new BusinessOperationService(operationStore(), accounts, api, chat, props, "https://api.example.com/", new SlidingWindowLimiter(clock), clock);
    }

    @BeforeEach
    void setUp() throws Exception {
        SecretVault vault = new SecretVault(Base64.getEncoder().encodeToString(new byte[32]));
        Path certs = Path.of(getClass().getResource("/daraja-certs").toURI());
        SecurityCredentialGenerator generator = new SecurityCredentialGenerator(new DarajaProperties("a", "b", certs.toString()));
        accounts = new BusinessAccountService(accountStore(), vault, generator, (env, key, secret) -> new TokenProbe.Outcome(TokenProbe.Kind.ACCEPTED, 200), clock);
        service = serviceWith(new BusinessOpsProperties(150000, Duration.ofMinutes(30), false, 3, 2), daraja);
        shop = accounts.create(OWNER, new BusinessAccountRequest("Main shop", "sandbox", "buy_goods", "174379", "", null, null, null, KEY, SECRET, "testapi", PASS, PASSKEY, null));
        counter = accounts.create(OWNER, new BusinessAccountRequest("Counter two", "sandbox", "buy_goods", "174379", "9876543", null, shop.id(), null, null, null, null, null, null, null));
        accounts.create(OWNER, new BusinessAccountRequest("Bare", "sandbox", "paybill", "600111", "", null, null, null, KEY, SECRET, null, null, null, null));
    }

    private static Map<String, String> params(String... keyValues) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    private BusinessOperationService.Prepared prepare(String account, String type, Map<String, String> p, String key) {
        return service.prepare(OWNER, account, type, p, "TEXT", key);
    }

    private static Map<String, Object> initiatorResult(Object code, String desc, String receipt, Map<String, String> values) {
        List<Map<String, Object>> pairs = new ArrayList<>();
        values.forEach((k, v) -> pairs.add(Map.of("Key", k, "Value", v)));
        return Map.of("Result", Map.of("ResultCode", code, "ResultDesc", desc, "TransactionID", receipt, "ResultParameters", Map.of("ResultParameter", pairs)));
    }

    // ---------------------------------------------------------------- choosing the account and checking the request

    @Test
    void anOwnerWithNoAccountIsToldWhereToAddOne() {
        assertThatThrownBy(() -> service.prepare("254799000000", null, "BALANCE", params(), "TEXT", null))
                .isInstanceOf(InvalidActionRequestException.class).hasMessageContaining("no business accounts");
    }

    @Test
    void severalAccountsMeansTheNameIsNeeded() {
        assertThatThrownBy(() -> prepare(null, "BALANCE", params(), null))
                .hasMessageContaining("Which business account").hasMessageContaining("Main shop");
        assertThatThrownBy(() -> prepare("nope", "BALANCE", params(), null)).hasMessageContaining("No business account matches");
    }

    @Test
    void anAccountCanBeNamedByLabelTillOrAtMention() {
        assertThat(prepare("@Main shop", "BALANCE", params(), "a").operation().getAccountId()).isEqualTo(shop.id());
        assertThat(prepare("9876543", "BALANCE", params(), "b").operation().getAccountId()).isEqualTo(counter.id());
        assertThat(prepare("counter", "BALANCE", params(), "c").operation().getAccountId()).isEqualTo(counter.id());
    }

    @Test
    void anAccountThatLacksWhatIsNeededSaysSo() {
        assertThatThrownBy(() -> prepare("Bare", "BALANCE", params(), null)).hasMessageContaining("cannot do").hasMessageContaining("Settings");
        assertThatThrownBy(() -> prepare("Bare", "STK_PUSH", params("amount", "10", "phone", "0712345678"), null)).hasMessageContaining("cannot do");
    }

    @Test
    void badAmountsAndDetailsAreRefusedBeforeAnythingIsSaved() {
        assertThatThrownBy(() -> prepare("Counter two", "STK_PUSH", params("phone", "0712345678"), null)).isInstanceOf(InvalidActionRequestException.class);
        assertThatThrownBy(() -> prepare("Counter two", "STK_PUSH", params("amount", "10.5", "phone", "0712345678"), null)).isInstanceOf(InvalidActionRequestException.class);
        assertThatThrownBy(() -> prepare("Counter two", "STK_PUSH", params("amount", "0", "phone", "0712345678"), null)).isInstanceOf(InvalidActionRequestException.class);
        assertThatThrownBy(() -> prepare("Counter two", "STK_PUSH", params("amount", "150001", "phone", "0712345678"), null)).hasMessageContaining("150,000");
        assertThatThrownBy(() -> prepare("Counter two", "STK_PUSH", params("amount", "10", "phone", "0712345678", "reference", "no spaces!"), null)).isInstanceOf(InvalidActionRequestException.class);
        assertThatThrownBy(() -> prepare("Main shop", "B2C", params("amount", "10", "phone", "0700111222", "command", "Steal"), null)).isInstanceOf(InvalidActionRequestException.class);
        assertThatThrownBy(() -> prepare("Main shop", "B2B", params("amount", "10", "receiver", "12"), null)).isInstanceOf(InvalidActionRequestException.class);
        assertThatThrownBy(() -> prepare("Main shop", "B2B", params("amount", "10", "receiver", "888880", "target_type", "paybill"), null)).hasMessageContaining("account number");
        assertThatThrownBy(() -> prepare("Main shop", "REVERSAL", params("amount", "10", "receipt", "xx"), null)).isInstanceOf(InvalidActionRequestException.class);
        assertThatThrownBy(() -> prepare("Main shop", "LAUNDER", params(), null)).hasMessageContaining("STK_PUSH");
        assertThat(operationRows).isEmpty();
        assertThat(daraja.paths).isEmpty();
    }

    // ---------------------------------------------------------------- reads and collections go out at once

    @Test
    void aBalanceCheckGoesOutAtOnceWithTheOwnersOwnKeys() {
        BusinessOperationService.Prepared p = prepare("Main shop", "BALANCE", params(), "bal");
        assertThat(p.operation().getStatus()).isEqualTo(BusinessOperationStatus.SUBMITTED);
        assertThat(daraja.lastPath()).isEqualTo("/mpesa/accountbalance/v1/query");
        assertThat(daraja.credentialsUsed.get(0)).isEqualTo("SANDBOX:" + KEY + ":" + SECRET);
        assertThat(daraja.lastBody().get("Initiator")).isEqualTo("testapi");
        assertThat((String) daraja.lastBody().get("SecurityCredential")).hasSizeGreaterThan(100);
        assertThat(daraja.lastBody().toString()).doesNotContain(PASS);
        String result = String.valueOf(daraja.lastBody().get("ResultURL"));
        assertThat(result).startsWith("https://api.example.com/daraja/business/" + p.operation().getId() + "/").endsWith("/result");
        assertThat(result.substring("https://".length())).doesNotContain("//");
    }

    @Test
    void theSameKeyNeverSendsTwice() {
        BusinessOperationService.Prepared first = prepare("Main shop", "BALANCE", params(), "same");
        BusinessOperationService.Prepared second = prepare("Main shop", "BALANCE", params(), "same");
        assertThat(second.operation().getId()).isEqualTo(first.operation().getId());
        assertThat(daraja.paths).hasSize(1);
    }

    @Test
    void theBalanceResultIsReadableAndAnAnswerIsAcceptedOnlyOnce() {
        BusinessOperation op = prepare("Main shop", "BALANCE", params(), "bal").operation();
        Map<String, Object> result = initiatorResult(0, "ok", "QGH7ABC123",
                params("AccountBalance", "Working Account|KES|700000.00|700000.00|0.00|0.00&Utility Account|KES|228037.00|228037.00|0.00|0.00"));
        service.onCallback(op.getId(), "wrong-token", "result", result);
        assertThat(op.getStatus()).isEqualTo(BusinessOperationStatus.SUBMITTED);

        service.onCallback(op.getId(), op.getCallbackToken(), "result", result);
        assertThat(op.getStatus()).isEqualTo(BusinessOperationStatus.SUCCEEDED);
        assertThat(chat.last()).contains("Working Account: KES 700,000.00").contains("Utility Account: KES 228,037.00");

        int told = chat.texts.size();
        service.onCallback(op.getId(), op.getCallbackToken(), "result", result);
        assertThat(chat.texts).hasSize(told);
    }

    @Test
    void anStkPushUsesTheHeadShortcodeAndTheBranchTill() {
        BusinessOperation op = prepare("9876543", "STK_PUSH", params("amount", "1,200", "phone", "0712 345 678", "reference", "INV42"), "stk").operation();
        assertThat(op.getStatus()).isEqualTo(BusinessOperationStatus.SUBMITTED);
        assertThat(daraja.lastPath()).isEqualTo("/mpesa/stkpush/v1/processrequest");
        Map<String, Object> body = daraja.lastBody();
        assertThat(body.get("BusinessShortCode")).isEqualTo("174379");
        assertThat(body.get("PartyB")).isEqualTo("9876543");
        assertThat(body.get("TransactionType")).isEqualTo("CustomerBuyGoodsOnline");
        assertThat(String.valueOf(body.get("Amount"))).isEqualTo("1200");
        assertThat(body.get("PhoneNumber")).isEqualTo("254712345678");
        assertThat(String.valueOf(body.get("CallBackURL"))).endsWith("/callback");
        assertThat(new String(Base64.getDecoder().decode(String.valueOf(body.get("Password"))))).startsWith("174379" + PASSKEY);
    }

    @Test
    void anStkPushIsSettledByTheCustomersAnswer() {
        daraja.answer = Map.of("ResponseCode", "0", "CheckoutRequestID", "ws_CO_1");
        BusinessOperation paid = prepare("Counter two", "STK_PUSH", params("amount", "5", "phone", "0712345678"), "p1").operation();
        assertThat(paid.getCheckoutRequestId()).isEqualTo("ws_CO_1");
        service.onCallback(paid.getId(), paid.getCallbackToken(), "callback", Map.of("Body", Map.of("stkCallback", Map.of("ResultCode", 0, "ResultDesc", "ok",
                "CallbackMetadata", Map.of("Item", List.of(Map.of("Name", "Amount", "Value", 5), Map.of("Name", "MpesaReceiptNumber", "Value", "QGH7ABC123")))))));
        assertThat(paid.getStatus()).isEqualTo(BusinessOperationStatus.SUCCEEDED);
        assertThat(paid.getReceipt()).isEqualTo("QGH7ABC123");
        assertThat(chat.last()).contains("QGH7ABC123");

        BusinessOperation cancelled = prepare("Counter two", "STK_PUSH", params("amount", "5", "phone", "0712345678"), "p2").operation();
        service.onCallback(cancelled.getId(), cancelled.getCallbackToken(), "callback", Map.of("Body", Map.of("stkCallback", Map.of("ResultCode", 1032, "ResultDesc", "Request cancelled by user"))));
        assertThat(cancelled.getStatus()).isEqualTo(BusinessOperationStatus.FAILED);
        assertThat(chat.last()).contains("Request cancelled by user");
    }

    @Test
    void stkPushesAreLimitedPerHour() {
        for (int i = 0; i < 3; i++) {
            prepare("Counter two", "STK_PUSH", params("amount", "5", "phone", "0712345678"), "r" + i);
        }
        assertThatThrownBy(() -> prepare("Counter two", "STK_PUSH", params("amount", "5", "phone", "0712345678"), "r3")).isInstanceOf(TooManyRequestsException.class);
        clock.advance(Duration.ofMinutes(61));
        assertThat(prepare("Counter two", "STK_PUSH", params("amount", "5", "phone", "0712345678"), "r4").operation().getStatus()).isEqualTo(BusinessOperationStatus.SUBMITTED);
    }

    @Test
    void aRefusedRequestFailsAndSaysWhy() {
        daraja.answer = Map.of("ResponseCode", "1", "ResponseDescription", "Invalid Access Token");
        BusinessOperation op = prepare("Counter two", "STK_PUSH", params("amount", "5", "phone", "0712345678"), "x").operation();
        assertThat(op.getStatus()).isEqualTo(BusinessOperationStatus.FAILED);
        assertThat(op.getFailure()).contains("Invalid Access Token");
    }

    @Test
    void checkingAnStkPushAlsoSettlesIt() {
        daraja.answer = Map.of("ResponseCode", "0", "CheckoutRequestID", "ws_CO_2");
        BusinessOperation push = prepare("Counter two", "STK_PUSH", params("amount", "5", "phone", "0712345678"), "push").operation();
        daraja.answer = Map.of("ResponseCode", "0", "ResultCode", "0", "ResultDesc", "The service request is processed successfully.");
        BusinessOperation query = prepare("Counter two", "STK_QUERY", params("operation", push.getId().toString().substring(0, 8)), "q").operation();
        assertThat(query.getStatus()).isEqualTo(BusinessOperationStatus.SUCCEEDED);
        assertThat(daraja.lastPath()).isEqualTo("/mpesa/stkpushquery/v1/query");
        assertThat(push.getStatus()).isEqualTo(BusinessOperationStatus.SUCCEEDED);
        assertThatThrownBy(() -> prepare("Counter two", "STK_QUERY", params(), null)).isInstanceOf(InvalidActionRequestException.class);

        daraja.failure = new DarajaRefusedException("The transaction is being processed");
        BusinessOperation still = prepare("Counter two", "STK_QUERY", params("checkout_request_id", "ws_CO_55"), "q2").operation();
        assertThat(still.getStatus()).isEqualTo(BusinessOperationStatus.SUCCEEDED);
        assertThat(still.getResultText()).contains("not finished");
    }

    @Test
    void aQrCodeIsReturnedOnceAndNotKept() {
        daraja.answer = Map.of("ResponseCode", "00", "QRCode", "BASE64PNG");
        BusinessOperationService.Prepared p = prepare("Main shop", "DYNAMIC_QR", params("amount", "300", "reference", "TBL5"), "qr");
        assertThat(p.qrCode()).isEqualTo("BASE64PNG");
        assertThat(p.operation().getStatus()).isEqualTo(BusinessOperationStatus.SUCCEEDED);
        assertThat(String.valueOf(p.operation().getResultText())).doesNotContain("BASE64PNG");
        assertThat(daraja.lastPath()).isEqualTo("/mpesa/qrcode/v1/generate");

        daraja.answer = Map.of("ResponseCode", "400", "ResponseDescription", "Bad request");
        assertThat(prepare("Main shop", "DYNAMIC_QR", params("amount", "300"), "qr2").operation().getStatus()).isEqualTo(BusinessOperationStatus.FAILED);
    }

    @Test
    void pulledTransactionsAreListedAndCode1000IsSuccess() {
        daraja.answer = Map.of("ResponseCode", "1000", "ResponseMessage", "Success", "Response", List.of(
                Map.of("transactionId", "AAA1", "amount", "100", "trxDate", "2026-11-01", "billreference", "R1"), Map.of("transactionId", "AAA2", "amount", "50", "trxDate", "2026-11-02")));
        assertThatThrownBy(() -> prepare("Main shop", "PULL", params("start_date", "2026-11-02", "end_date", "2026-11-01"), null)).hasMessageContaining("after the start");
        assertThatThrownBy(() -> prepare("Main shop", "PULL", params("start_date", "yesterday", "end_date", "2026-11-01"), null)).isInstanceOf(InvalidActionRequestException.class);
        BusinessOperation op = prepare("Main shop", "PULL", params("start_date", "2026-11-01", "end_date", "2026-11-02"), "pull").operation();
        assertThat(op.getStatus()).isEqualTo(BusinessOperationStatus.SUCCEEDED);
        assertThat(op.getResultText()).contains("AAA1").contains("2 transactions");
    }

    // ---------------------------------------------------------------- money out waits for the owner

    @Test
    void moneyOutWaitsForApprovalAndIsSentOnce() {
        daraja.answer = Map.of("ResponseCode", "0", "ConversationID", "AG_1");
        BusinessOperation op = service.prepare(OWNER, "Main shop", "B2C", params("amount", "500", "phone", "0700111222", "remarks", "Salary"), "voice", "b2c").operation();
        assertThat(op.getStatus()).isEqualTo(BusinessOperationStatus.PENDING_APPROVAL);
        assertThat(op.getSource()).isEqualTo("VOICE");
        assertThat(daraja.paths).isEmpty();
        BusinessOperationView view = BusinessOperationView.of(op, service.approvalRequired(op), null);
        assertThat(view.needsApproval()).isTrue();
        assertThat(view.toString()).doesNotContain(op.getCallbackToken());

        assertThatThrownBy(() -> service.approve(OTHER, op.getId(), "BIOMETRIC")).isInstanceOf(ActionNotFoundException.class);
        BusinessOperation sent = service.approve(OWNER, op.getId(), "BIOMETRIC");
        assertThat(sent.getStatus()).isEqualTo(BusinessOperationStatus.SUBMITTED);
        assertThat(sent.getApprovalMethod()).isEqualTo("BIOMETRIC");
        assertThat(daraja.lastPath()).isEqualTo("/mpesa/b2c/v3/paymentrequest");
        assertThat(daraja.lastBody().get("CommandID")).isEqualTo("BusinessPayment");
        assertThat(daraja.lastBody().get("PartyB")).isEqualTo("254700111222");
        assertThat(daraja.lastBody().get("PartyA")).isEqualTo("174379");
        assertThat(daraja.lastBody()).containsKey("Occassion");

        assertThatThrownBy(() -> service.approve(OWNER, op.getId(), "BIOMETRIC")).isInstanceOf(InvalidActionStateException.class);
        assertThat(daraja.paths).hasSize(1);

        service.onCallback(op.getId(), op.getCallbackToken(), "result", initiatorResult(0, "ok", "QGH9XYZ789", params("ReceiverPartyPublicName", "254700111222 - JOHN DOE")));
        assertThat(op.getStatus()).isEqualTo(BusinessOperationStatus.SUCCEEDED);
        assertThat(chat.last()).contains("JOHN DOE").contains("QGH9XYZ789");
    }

    @Test
    void aDeclinedRequestSendsNothingAndCannotBeApprovedLater() {
        BusinessOperation op = prepare("Main shop", "B2POCHI", params("amount", "200", "phone", "0700111222"), "pochi").operation();
        assertThat(service.decline(OWNER, op.getId()).getStatus()).isEqualTo(BusinessOperationStatus.DECLINED);
        assertThat(chat.last()).contains("nothing was sent");
        assertThatThrownBy(() -> service.approve(OWNER, op.getId(), "BIOMETRIC")).isInstanceOf(InvalidActionStateException.class);
        assertThat(daraja.paths).isEmpty();
    }

    @Test
    void aRequestNobodyApprovedExpires() {
        BusinessOperation op = prepare("Main shop", "B2C", params("amount", "10", "phone", "0700111222"), "old").operation();
        clock.advance(Duration.ofMinutes(31));
        assertThatThrownBy(() -> service.approve(OWNER, op.getId(), "BIOMETRIC")).isInstanceOf(InvalidActionStateException.class);
        assertThat(op.getStatus()).isEqualTo(BusinessOperationStatus.EXPIRED);
        assertThat(daraja.paths).isEmpty();
    }

    @Test
    void onlyAFewRequestsMayWaitForApprovalAtOnce() {
        prepare("Main shop", "B2C", params("amount", "10", "phone", "0700111222"), "o1");
        prepare("Main shop", "B2C", params("amount", "10", "phone", "0700111222"), "o2");
        assertThatThrownBy(() -> prepare("Main shop", "B2C", params("amount", "10", "phone", "0700111222"), "o3")).hasMessageContaining("waiting for your approval");
    }

    @Test
    void collectionsCanBeSetToWaitForApprovalToo() {
        BusinessOperationService strict = serviceWith(new BusinessOpsProperties(150000, Duration.ofMinutes(30), true, 20, 10), daraja);
        assertThat(strict.prepare(OWNER, "Counter two", "STK_PUSH", params("amount", "5", "phone", "0712345678"), "TEXT", "s").operation().getStatus())
                .isEqualTo(BusinessOperationStatus.PENDING_APPROVAL);
    }

    // ---------------------------------------------------------------- when Safaricom does not answer clearly

    @Test
    void aTimeoutIsNeverRetriedAndTheOwnerIsToldToCheckTheStatement() {
        daraja.failure = new DarajaOutcomeUnknownException("Safaricom did not answer in time.");
        BusinessOperation op = prepare("Main shop", "BALANCE", params(), "unk").operation();
        assertThat(op.getStatus()).isEqualTo(BusinessOperationStatus.UNKNOWN);
        assertThat(op.getFailure()).contains("M-Pesa statement");
        int calls = daraja.paths.size();
        prepare("Main shop", "BALANCE", params(), "unk");
        assertThat(daraja.paths).hasSize(calls);
    }

    @Test
    void aRefusalFailsCleanly() {
        daraja.failure = new DarajaRefusedException("Invalid Access Token");
        assertThat(prepare("Main shop", "BALANCE", params(), "ref").operation().getStatus()).isEqualTo(BusinessOperationStatus.FAILED);
    }

    @Test
    void aTimeoutCallbackAndAFailureResultAreRecorded() {
        daraja.answer = Map.of("ResponseCode", "0");
        BusinessOperation status = prepare("Main shop", "STATUS", params("receipt", "qgh7abc123"), "st").operation();
        assertThat(status.getParams().get("receipt")).isEqualTo("QGH7ABC123");
        service.onCallback(status.getId(), status.getCallbackToken(), "timeout", Map.of());
        assertThat(status.getStatus()).isEqualTo(BusinessOperationStatus.UNKNOWN);

        BusinessOperation bal = prepare("Main shop", "BALANCE", params(), "bal").operation();
        service.onCallback(bal.getId(), bal.getCallbackToken(), "result", Map.of("Result", Map.of("ResultCode", 2001, "ResultDesc", "The initiator information is invalid.")));
        assertThat(bal.getStatus()).isEqualTo(BusinessOperationStatus.FAILED);
        assertThat(bal.getResultText()).contains("2001");
    }

    @Test
    void anAnswerThatArrivesWhileTheRequestIsStillGoingOutIsNotLost() throws Exception {
        BusinessOperationService[] holder = new BusinessOperationService[1];
        Thread[] early = new Thread[1];
        DarajaApi racing = (env, key, secret, path, body) -> {
            String[] parts = String.valueOf(body.get("ResultURL")).split("/");
            UUID id = UUID.fromString(parts[parts.length - 3]);
            String token = parts[parts.length - 2];
            early[0] = new Thread(() -> holder[0].onCallback(id, token, "result", initiatorResult(0, "ok", "RACE123456", params("AccountBalance", "Working Account|KES|5.00|5.00|0|0"))));
            early[0].start();
            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return Map.of("ResponseCode", "0", "ConversationID", "AG_R");
        };
        holder[0] = serviceWith(new BusinessOpsProperties(150000, Duration.ofMinutes(30), false, 20, 10), racing);
        BusinessOperation op = holder[0].prepare(OWNER, "Main shop", "BALANCE", params(), "TEXT", "race").operation();
        early[0].join();
        assertThat(op.getStatus()).isEqualTo(BusinessOperationStatus.SUCCEEDED);
    }

    @Test
    void ownersSeeOnlyTheirOwnRequests() {
        BusinessOperation op = prepare("Main shop", "BALANCE", params(), "mine").operation();
        assertThat(service.list(OTHER)).isEmpty();
        assertThat(service.list(OWNER)).contains(op);
        assertThatThrownBy(() -> service.get(OTHER, op.getId())).isInstanceOf(ActionNotFoundException.class);
    }

    // ---------------------------------------------------------------- the request bodies themselves

    @Test
    void theOtherRequestBodiesCarryDarajasOwnSpellings() throws Exception {
        ResolvedCredentials rc = accounts.resolve(OWNER, counter.id());
        assertThat(rc.partyB()).isEqualTo("9876543");
        DarajaRequests.Call b2b = DarajaRequests.b2b(rc, "CRED", "BusinessPayBill", "888880", "ACC1", 100, "Rent", "https://r", "https://t");
        assertThat(b2b.path()).isEqualTo("/mpesa/b2b/v1/paymentrequest");
        assertThat(b2b.body().get("CommandID")).isEqualTo("BusinessPayBill");
        DarajaRequests.Call reversal = DarajaRequests.reversal(rc, "CRED", "QGH7ABC123", 100, "Oops", "https://r", "https://t");
        assertThat(reversal.path()).isEqualTo("/mpesa/reversal/v1/request");
        assertThat(reversal.body()).containsKey("RecieverIdentifierType");
        DarajaRequests.Call pochi = DarajaRequests.b2pochi(rc, "CRED", "conv", "254700111222", 10, "x", "", "https://r", "https://t");
        assertThat(pochi.path()).isEqualTo("/mpesa/b2pochi/v1/paymentrequest");
        assertThat(pochi.body().get("CommandID")).isEqualTo("BusinessPayToPochi");
    }

    @Test
    void balanceTextHandlesOddInput() {
        assertThat(BusinessOperationService.balanceText("Working|KES|n/a|n/a")).contains("n/a");
        assertThat(BusinessOperationService.balanceText("")).isEqualTo("Safaricom did not return the balance.");
    }
}
