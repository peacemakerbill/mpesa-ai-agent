<!-- PLACE THIS FILE AT: mpesa-ai-agent/payment-service/README.md
     Replace the existing file if there is one. The SVG images it uses go in mpesa-ai-agent/docs/assets/. You can delete this comment. -->

<div align="center">

<img src="../docs/assets/banner-payment.svg" alt="" width="100%"/>

<br/>

<img alt="Spring%20Boot" src="https://img.shields.io/badge/Spring%20Boot-4.1.2-087F36?style=flat-square&logo=springboot&logoColor=white"/> <img alt="Java" src="https://img.shields.io/badge/Java-21-14231C?style=flat-square&logo=openjdk&logoColor=2BD576"/> <img alt="Maven" src="https://img.shields.io/badge/Maven-3.9+-14231C?style=flat-square&logo=apachemaven&logoColor=F2B544"/> <img alt="MongoDB" src="https://img.shields.io/badge/MongoDB-default-14231C?style=flat-square&logo=mongodb&logoColor=2BD576"/> <img alt="PostgreSQL" src="https://img.shields.io/badge/PostgreSQL-supported-14231C?style=flat-square&logo=postgresql&logoColor=6EA8FF"/> <img alt="MySQL" src="https://img.shields.io/badge/MySQL-supported-14231C?style=flat-square&logo=mysql&logoColor=F2B544"/> <img alt="Flyway" src="https://img.shields.io/badge/Flyway-12-14231C?style=flat-square&logo=flyway&logoColor=FF5468"/> <img alt="RabbitMQ" src="https://img.shields.io/badge/RabbitMQ-events-14231C?style=flat-square&logo=rabbitmq&logoColor=FF7A45"/> <img alt="Daraja" src="https://img.shields.io/badge/Daraja-optional-087F36?style=flat-square"/>

<br/><br/>

The cashier, for a business. It is the only service that talks to Safaricom Daraja, and the final result comes only from Safaricom.

<sub><a href="../README.md">Back to the project overview</a></sub>

<img src="../docs/assets/divider.svg" alt="" width="100%"/>

</div>

> [!NOTE]
> This module is **optional** and off by default. Personal wallet requests do not need it. Turn it on with `docker compose --profile business up --build` and `AGENT_BUSINESS_PAYMENTS=true` in agent-service.

## What it does

The AI agent never moves money. It asks this service for a payment after the customer taps Confirm, and the final answer ("paid" or "failed") only ever comes from Safaricom's callback.

| Job | Detail |
|---|---|
| STK Push | To your own Paybill or Till, after the customer taps Pay |
| Callbacks | Receives Safaricom's result at a secret URL, with an optional IP allowlist |
| Reconcile | Asks Daraja about payments whose callback never arrived |
| Money out | Refunds, payouts and reversals through B2C, each needing a second person to approve |
| Lookups | Transaction status and account balance for staff and admins |
| Receipts and QR | PDF receipts and QR codes to pay |
| Roles | `CUSTOMER`, `STAFF`, `APPROVER`, `ADMIN`. The model only sees the tools its role allows, and this service checks again |

## Package layout

```text
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

| Class | Does |
|---|---|
| `PaymentService` | Starts a payment: validates, checks limits, handles idempotency, calls Daraja |
| `PaymentStateService` | The only code that changes a payment's status, each change in its own short transaction with a row lock |
| `MpesaClient` | Only speaks HTTP to Daraja: token caching, STK Push, STK query |
| `MpesaCallbackController` | Receives Safaricom's result and hands it to `PaymentStateService` |
| `PaymentReconciler` | Asks Daraja about payments whose callback never arrived |

## Payment lifecycle

| Status | Meaning | Final |
|---|---|---|
| `PENDING` | Saved locally, Daraja has not confirmed yet | No |
| `PUSHED` | Daraja accepted, the customer has the PIN prompt | No |
| `SUCCESS` | Safaricom confirmed | Yes |
| `FAILED` | Rejected, for example insufficient funds or wrong PIN | Yes |
| `CANCELLED` | The customer dismissed the prompt, code 1032 | Yes |
| `TIMEOUT` | No answer in time. A late success callback still wins | No |

A network timeout while pushing leaves the payment in `PENDING`, never `FAILED`, because the customer may already have a prompt. Retrying the same idempotency key returns the same payment and never pushes again.

## Run it

```bash
docker compose up -d rabbitmq redis          # plus a database
cd payment-service
cp .env.example .env                         # then edit it
mvn spring-boot:run
```

Expose port 8083 with ngrok or a Cloudflare Tunnel and put that HTTPS address in `PUBLIC_BASE_URL`. Run everything in Docker with `docker compose up --build` from the project root. Run the tests with `mvn clean verify`. They use in memory H2 with the real Flyway migration and mock Daraja and RabbitMQ, so they need no network.

The default database when run directly is **MongoDB**. Set `DATABASE_TYPE=postgresql` or `mysql` to use a SQL database, which is the better choice before real money because it has multi-record transactions.

## Configuration

| Variable | Meaning |
|---|---|
| `INTERNAL_KEY` | Shared secret sent as `X-Internal-Key` by agent-service and companion-api |
| `MPESA_BASE_URL` | Sandbox `https://sandbox.safaricom.co.ke`, production `https://api.safaricom.co.ke` |
| `MPESA_CONSUMER_KEY`, `MPESA_CONSUMER_SECRET` | Daraja app credentials |
| `MPESA_SHORTCODE` | Sandbox 174379, production your Paybill or Till |
| `MPESA_PASSKEY` | STK Push passkey for that shortcode |
| `MPESA_TRANSACTION_TYPE` | `CustomerPayBillOnline` for Paybill, `CustomerBuyGoodsOnline` for Till |
| `PUBLIC_BASE_URL` | Public HTTPS address Safaricom can reach. Include any gateway path prefix |
| `MPESA_CALLBACK_SECRET` | Random string placed in the callback URL path |
| `MPESA_CALLBACK_ALLOWED_IPS` | Optional comma separated Safaricom IPs |
| `PAYMENT_LIMIT_PER_TX`, `PAYMENT_LIMIT_PER_DAY` | Limits in whole KES (defaults 70000 and 150000) |
| `DATABASE_TYPE` | `mongodb` (default), `postgresql` or `mysql` |
| `MONGODB_URI` | Keep `serverSelectionTimeoutMS=5000` in it |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | For the SQL profiles |
| `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD` | RabbitMQ |

The callback URL Safaricom is given is `PUBLIC_BASE_URL/api/callbacks/stk/MPESA_CALLBACK_SECRET`. The path deliberately avoids words like "mpesa" and "safaricom", which Daraja can reject in callback URLs. The service reads `.env` through `spring.config.import: optional:file:.env[.properties]`, and real environment variables win.

## Endpoints

**Internal** (require `X-Internal-Key`)

| Method and path | Purpose |
|---|---|
| `POST /internal/payments` | Start a payment. Safe to retry with the same `idempotencyKey` |
| `GET /internal/payments/{id}` | Read a payment |
| `GET /internal/payments/latest?phone=0712345678` | Newest payment for a customer |

**Public, called by Safaricom**

| Method and path | Purpose |
|---|---|
| `POST /api/callbacks/stk/{secret}` | STK Push result. A wrong secret gets 403 |

Health: `GET /actuator/health`. Refunds, payouts, approvals, QR and staff roles are in the `3. payment-service` folder of the Postman collection.

<details>
<summary><b>Try a sandbox payment</b></summary>

<br/>

```bash
curl -X POST http://localhost:8083/internal/payments \
  -H "X-Internal-Key: $INTERNAL_KEY" \
  -H "Content-Type: application/json" \
  -d '{"idempotencyKey":"demo-1","phone":"0708374149","amount":1,"accountReference":"DEMO-1"}'
```

Simulate Safaricom's success callback locally, using the `checkoutRequestId` from the answer above:

```bash
curl -X POST http://localhost:8083/api/callbacks/stk/$MPESA_CALLBACK_SECRET \
  -H "Content-Type: application/json" \
  -d '{"Body":{"stkCallback":{"MerchantRequestID":"x","CheckoutRequestID":"ws_CO_REPLACE_ME","ResultCode":0,"ResultDesc":"The service request is processed successfully.","CallbackMetadata":{"Item":[{"Name":"Amount","Value":1.00},{"Name":"MpesaReceiptNumber","Value":"TEST123ABC"},{"Name":"PhoneNumber","Value":254708374149}]}}}}'
```

</details>

**Error answers:** 400 bad input, 401 missing internal key, 404 unknown payment, 409 idempotency key reused for a different payment, 422 limit exceeded, **503** when the database, a query or RabbitMQ is unavailable.

## Failing well

| Situation | What happens |
|---|---|
| Database not up yet | Flyway retries 6 times, 5 seconds apart, then stops with one clear message |
| Database or Mongo unreachable at run time | Hikari times out after 5 seconds, Mongo after `serverSelectionTimeoutMS=5000` |
| Data access, query timeout or AMQP failure inside a request | `GlobalExceptionHandler` answers 503 with a plain body |

## Events for the agent-service

When a payment reaches `SUCCESS`, `FAILED`, `CANCELLED` or `TIMEOUT`, a JSON message is published after the database commit to exchange `payments.events` with routing key `payment.done`:

```json
{ "paymentId": "", "phone": "", "status": "", "amount": 0, "accountReference": "", "receipt": "", "resultCode": "", "reason": "", "occurredAt": "" }
```

agent-service binds its own queue to that exchange and replies to the customer in the chat.

## Safety rules enforced here

- Amounts and limits are checked in code. The AI cannot raise them.
- STK Push amounts are whole shillings and the account reference is at most 12 characters (Daraja limits).
- Idempotency: a unique key per request, replays return the original payment, and a key reused for a different amount or phone is rejected with 409.
- A payment becomes `SUCCESS` only from a Safaricom callback or an STK query answer.
- Duplicate and late callbacks are ignored once a payment is final. Row locks stop races.
- The callback URL carries a secret, with an optional IP allowlist.
- Result codes are stored as text because Safaricom sometimes sends alphanumeric codes.
- Phone numbers are masked in logs. Secrets are never logged.

## Known gaps before real money

> [!WARNING]
> Read these before raising limits or using production credentials.

- The daily limit check and the insert are not atomic. Two simultaneous requests for one phone could slightly exceed it. Add a per phone advisory lock first.
- Events are published after commit but not stored in an outbox. If RabbitMQ is down at that moment the customer is not notified (it is logged).
- The STK query API does not return the M-Pesa receipt, so a payment recovered by the reconciler has no receipt number until you reconcile against the statement.
- A callback that arrives before the push response is saved cannot be matched. Stuck `PENDING` payments are logged as warnings every minute for a day.
- There is no daily statement reconciliation job yet, and no C2B Paybill handling beyond the validation and confirmation callbacks.

<div align="center">
<sub>M-Pesa is a registered mark of Safaricom. This project is not affiliated with or endorsed by Safaricom.</sub>
</div>
