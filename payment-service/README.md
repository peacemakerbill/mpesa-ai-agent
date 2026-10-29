# payment-service

The cashier of the chat + M-Pesa agent. It is the only service that talks to Safaricom Daraja.
The AI agent never moves money: it asks this service for a payment after the customer taps Confirm,
and the final result ("paid" or "failed") only ever comes from Safaricom's callback.

Spring Boot 3.4, Java 21, PostgreSQL, Flyway, RabbitMQ. Layered MVC packages.

## Package layout

```
com.mpesaagent.payment
  PaymentServiceApplication
  controller/   InternalPaymentController, MpesaCallbackController      (HTTP in and out)
  service/      PaymentService, PaymentStateService, MpesaClient        (business rules, Daraja calls)
  repository/   PaymentRepository                                       (database access)
  model/        Payment, PaymentStatus                                  (entity and states)
  dto/          CreatePaymentRequest, PaymentResponse, Stk* payloads    (request and response shapes)
  exception/    domain exceptions and GlobalExceptionHandler
  config/       typed properties, InternalKeyFilter, RestClient, RabbitMQ, scheduling
  event/        PaymentCompletedEvent, PaymentEventPublisher            (RabbitMQ notifications)
  scheduler/    PaymentReconciler                                       (recovers lost callbacks)
  util/         PhoneNumberUtil
```

Who does what:

* PaymentService starts a payment: validates, checks limits, handles idempotency, calls Daraja.
* PaymentStateService is the only code that changes a payment's status, each change in its own short
  transaction with a row lock.
* MpesaClient only speaks HTTP to Daraja (token caching, STK Push, STK query).
* MpesaCallbackController receives Safaricom's result and hands it to PaymentStateService.
* PaymentReconciler asks Daraja about payments whose callback never arrived.

## Payment lifecycle

```
PENDING    saved locally, Daraja has not confirmed yet
PUSHED     Daraja accepted, the customer has the PIN prompt
SUCCESS    Safaricom confirmed (final)
FAILED     rejected, for example insufficient funds or wrong PIN (final)
CANCELLED  customer dismissed the prompt, code 1032 (final)
TIMEOUT    no answer in time. Not final, a late success callback still wins.
```

A network timeout while pushing leaves the payment in PENDING, never FAILED, because the customer may
already have a prompt. Retrying the same idempotency key returns the same payment and never pushes again.

## Configuration

Copy `.env.example` to `.env` and fill it in. Everything is read from environment variables.

| Variable | Meaning |
|---|---|
| INTERNAL_KEY | Shared secret sent as the X-Internal-Key header by agent-service and companion-api |
| MPESA_BASE_URL | Sandbox https://sandbox.safaricom.co.ke, production https://api.safaricom.co.ke |
| MPESA_CONSUMER_KEY, MPESA_CONSUMER_SECRET | Daraja app credentials |
| MPESA_SHORTCODE | Sandbox 174379, production your Paybill or Till |
| MPESA_PASSKEY | STK Push passkey for that shortcode |
| MPESA_TRANSACTION_TYPE | CustomerPayBillOnline for Paybill, CustomerBuyGoodsOnline for Till |
| PUBLIC_BASE_URL | Public HTTPS address Safaricom can reach. Include any gateway path prefix |
| MPESA_CALLBACK_SECRET | Random string placed in the callback URL path |
| MPESA_CALLBACK_ALLOWED_IPS | Optional comma separated Safaricom IPs |
| PAYMENT_LIMIT_PER_TX, PAYMENT_LIMIT_PER_DAY | Limits in whole KES (defaults 70000 and 150000) |
| DB_URL, DB_USER, DB_PASSWORD | PostgreSQL |
| RABBITMQ_HOST, RABBITMQ_PORT, RABBITMQ_USER, RABBITMQ_PASSWORD | RabbitMQ |

The callback URL Safaricom is given is `PUBLIC_BASE_URL/api/callbacks/stk/MPESA_CALLBACK_SECRET`.
The path deliberately avoids words like "mpesa" and "safaricom", which Daraja can reject in callback URLs.

## Run it

```
cd mpesa-agent
docker compose up -d postgres rabbitmq redis
cd payment-service
cp .env.example .env        # then edit it
set -a; source .env; set +a
mvn spring-boot:run
```

Expose port 8083 with ngrok or Cloudflare Tunnel, and put that HTTPS address in `PUBLIC_BASE_URL`.

Run everything in Docker instead: `docker compose up --build` from the `mpesa-agent` folder.

Run the tests: `mvn clean verify`. They use an in memory H2 database with the real Flyway migration and
mock Daraja and RabbitMQ, so they need no network and no running infrastructure.

## Endpoints

Internal, require the `X-Internal-Key` header:

| Method and path | Purpose |
|---|---|
| POST /internal/payments | Start a payment. Safe to retry with the same idempotencyKey |
| GET /internal/payments/{id} | Read a payment |
| GET /internal/payments/latest?phone=0712345678 | Newest payment for a customer |

Public, called by Safaricom:

| Method and path | Purpose |
|---|---|
| POST /api/callbacks/stk/{secret} | STK Push result. Wrong secret gets 403 |

Health: `GET /actuator/health`.

Try a sandbox payment:

```
curl -X POST http://localhost:8083/internal/payments \
  -H "X-Internal-Key: $INTERNAL_KEY" \
  -H "Content-Type: application/json" \
  -d '{"idempotencyKey":"demo-1","phone":"0708374149","amount":1,"accountReference":"DEMO-1"}'
```

Simulate Safaricom's success callback locally (use the checkoutRequestId from the response above):

```
curl -X POST http://localhost:8083/api/callbacks/stk/$MPESA_CALLBACK_SECRET \
  -H "Content-Type: application/json" \
  -d '{"Body":{"stkCallback":{"MerchantRequestID":"x","CheckoutRequestID":"ws_CO_REPLACE_ME","ResultCode":0,"ResultDesc":"The service request is processed successfully.","CallbackMetadata":{"Item":[{"Name":"Amount","Value":1.00},{"Name":"MpesaReceiptNumber","Value":"TEST123ABC"},{"Name":"PhoneNumber","Value":254708374149}]}}}}'
```

Error answers: 400 bad input, 401 missing internal key, 404 unknown payment, 409 idempotency key reused for a
different payment, 422 limit exceeded.

## Events for the agent-service

When a payment reaches SUCCESS, FAILED, CANCELLED or TIMEOUT, a JSON message is published after the database
commit to exchange `payments.events` with routing key `payment.done`:

```
{ paymentId, phone, status, amount, accountReference, receipt, resultCode, reason, occurredAt }
```

The agent-service binds its own queue to that exchange and replies to the customer in the chat.

## Safety rules enforced here

* Amounts and limits are checked in code. The AI cannot raise them.
* STK Push amounts are whole shillings and the account reference is at most 12 characters (Daraja limits).
* Idempotency: unique key per request, replays return the original payment, a key reused for a different
  amount or phone is rejected with 409.
* A payment becomes SUCCESS only from a Safaricom callback or an STK query answer.
* Duplicate and late callbacks are ignored once a payment is final. Row locks stop races.
* The callback URL carries a secret, with an optional IP allowlist.
* Result codes are stored as text because Safaricom sometimes sends alphanumeric codes.
* Phone numbers are masked in logs. Secrets are never logged.

## Known gaps before real money

* The daily limit check and the insert are not atomic. Two simultaneous requests for one phone could slightly
  exceed it. Add a per phone advisory lock before raising limits.
* Events are published after commit but not stored in an outbox. If RabbitMQ is down at that moment the
  customer is not notified (it is logged). Add an outbox table if that matters.
* The STK query API does not return the M-Pesa receipt, so a payment recovered by the reconciler has no
  receipt number until you reconcile against the statement.
* A callback that arrives before the push response is saved cannot be matched. This needs the customer to
  enter a PIN within milliseconds, but the reconciler would not see it either (the row is still PENDING).
  Stuck PENDING payments are logged as warnings every minute for a day.
* There is no daily statement reconciliation job yet, and no B2C refunds or C2B Paybill handling.
