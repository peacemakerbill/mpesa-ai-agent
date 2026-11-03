<!-- PLACE THIS FILE AT: mpesa-ai-agent/agentx-api/README.md
     Replace the existing file if there is one. The SVG images it uses go in mpesa-ai-agent/docs/assets/. You can delete this comment. -->

<div align="center">

<img src="../docs/assets/banner-agentx-api.svg" alt="" width="100%"/>

<br/>

<img src="../docs/assets/badges-agentx-api.svg" alt="Spring Boot 4.1.2, Java 21, Maven 3.9 or later, MongoDB, PostgreSQL or MySQL, Flyway 12, RabbitMQ, port 8084." width="880"/>

<br/><br/>

The front door. It signs customers in, relays the chat to the agent, stores prepared requests and serves the app.

<sub><a href="../README.md">Back to the project overview</a></sub>

<img src="../docs/assets/divider.svg" alt="" width="100%"/>

</div>

## What it does

| Job | Detail |
|---|---|
| Sign in | A 6 digit code sent by SMS (valid 10 minutes, 5 tries, 3 codes an hour, stored hashed). The result is a signed session token tied to a device |
| Sign up and profile | Sign up with the same SMS code. A profile with names, email, address, location and a picture. An email is verified with its own code |
| Email | Welcome, sign in and payment emails, and reports (PDF, CSV, Excel) the customer asks for in the chat or the app. Sent by SMTP, or written to the log. See [`docs/PROFILE_AND_EMAIL.md`](../docs/PROFILE_AND_EMAIL.md) |
| Relay the chat | The app talks to this service. This service talks to agent-service. Nobody can read another number's chat or files |
| Store requests | Every request belongs to one phone number and moves through `CREATED`, `APPROVED`, `HANDED_OFF`, then `DONE_REPORTED` or `NOT_DONE` |
| Match evidence | Compares the code, kind, amount and payee from an M-Pesa message on the customer's phone with the request |
| Serve the app | Landing page for `/a/{id}`, the app's hand-off settings, and the Android and iOS link files |
| Publish events | Sends "customer reported" events to RabbitMQ |

> [!IMPORTANT]
> Only `/a/*`, `/api/*` and `/.well-known/*` should be public. Block `/internal/*` at your proxy.

## Package layout

```text
com.darajaagentx.api
  AgentXApiApplication
  controller/   AuthController, SignupController, ProfileController, EmailController,
                AppActionController, ChatController, InternalActionController,
                InternalProfileController, LandingController, WellKnownController
  service/      sign-in, sign up, profile, email, reports, requests, evidence matching, chat relay
  mail/         mail outbox, log and SMTP transports, email templates
  report/       CSV, Excel and PDF writers
  storage/      where profile pictures are kept (local or network)
  repository/   database access, for the database you choose
  model/  dto/  entities and request and response shapes
  config/       typed properties, token filter, internal key filter, CORS, RabbitMQ
  exception/    GlobalExceptionHandler
```

## Run it

```bash
docker compose up -d redis rabbitmq        # plus a database
cd agentx-api
cp .env.example .env                       # then edit it
mvn spring-boot:run
```

Start with `CODE_DELIVERY=dev` to sign in from the console without an SMS account. Run the tests with `mvn clean verify`.

## Endpoints

**Public, for the app** (session token required except sign-in)

| Method and path | Purpose |
|---|---|
| `POST /api/auth/request-code` | Ask for a sign-in code |
| `POST /api/auth/verify` | Trade the code for a session token |
| `POST /api/auth/signup/request`, `POST /api/auth/signup/verify` | Sign up: save the details and send the SMS code, then check it (no token needed) |
| `GET`, `PUT /api/profile` | Read and change the profile |
| `POST /api/profile/email/request-code`, `POST /api/profile/email/verify`, `DELETE /api/profile/email` | Verify or remove the email |
| `PUT`, `GET`, `DELETE /api/profile/picture` | Upload, read and remove the profile picture |
| `GET`, `POST /api/email/recipients`, `DELETE /api/email/recipients/{id}` | Saved addresses |
| `POST /api/email/recipients/paste`, `POST /api/email/recipients/import` | Add many addresses from pasted text or a CSV or text file |
| `POST /api/email/report`, `GET /api/email/log` | Email a report, and the emails sent |
| `DELETE /api/devices/me` | Sign this phone out |
| `GET /api/config` | Hand-off settings (dial codes, M-Pesa app ids) |
| `GET /api/actions`, `GET /api/actions/{id}` | List and read requests |
| `POST /api/actions/{id}/approve` | Approve after the phone check |
| `POST /api/actions/{id}/handoff` | Record that the dialler or M-Pesa was opened |
| `POST /api/actions/{id}/report` | Customer says it is done, or not done |
| `POST /api/actions/{id}/sms-evidence` | Send the parsed message fields for matching |
| `POST /api/actions/{id}/decline` | Decline |
| `GET`, `POST`, `DELETE /api/chat/messages` | Read, send and clear the chat |
| `POST /api/chat/media`, `GET /api/chat/media/{id}` | Upload and download chat files |
| `GET /a/{id}` | Landing page that opens the app |
| `GET /.well-known/assetlinks.json` and `apple-app-site-association` | App link files |

**Internal, for agent-service** (`X-Internal-Key` required)

| Method and path | Purpose |
|---|---|
| `POST /internal/actions` | Create a prepared request |
| `GET /internal/actions`, `GET /internal/actions/{id}` | List and read requests |
| `POST /internal/actions/{id}/cancel` | Cancel a waiting request |
| `POST /internal/actions/{id}/receipt` | Record an M-Pesa code against a request |
| `POST /internal/link-codes` | Create a link code |
| `GET`, `PUT /internal/profile` | The customer's profile, for the assistant |
| `POST /internal/profile/email/request-code`, `POST /internal/profile/email/verify` | Email check, for the assistant |
| `GET`, `POST`, `DELETE /internal/email/recipients`, `POST /internal/email/recipients/paste` | Saved addresses, for the assistant |
| `POST /internal/email/report` | Email a report, for the assistant |

## Configuration

| Variable | Meaning |
|---|---|
| `INTERNAL_KEY` | Shared secret for calls between services |
| `AGENTX_TOKEN_SECRET` | 32 or more random characters. Signs sessions |
| `AGENTX_PUBLIC_BASE_URL` | Public HTTPS address, used in links and the link files |
| `CODE_DELIVERY` | `log` (default), `dev` or `sms` |
| `SMS_USERNAME`, `SMS_API_KEY`, `SMS_SENDER_ID` | Africa's Talking, for `sms` |
| `DATABASE_TYPE` | `mongodb`, `postgresql` or `mysql`. See [`docs/DATABASES.md`](../docs/DATABASES.md) |
| `MONGODB_URI` | Add `serverSelectionTimeoutMS=5000` so a missing database fails fast |
| `HANDOFF_USSD_MAIN`, `HANDOFF_USSD_PAY_TILL`, `HANDOFF_MPESA_ANDROID_PACKAGE`, `HANDOFF_MPESA_IOS_URL` | Hand-off values. **Not verified, set them yourself** |
| Android package and signing fingerprint, iOS app id | So `/a/ID` links open the app directly |
| `MAIL_TRANSPORT` | `log` (default, nothing is sent) or `smtp` |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_STARTTLS`, `MAIL_SSL` | The mail server, for `smtp` |
| `MAIL_FROM`, `MAIL_FROM_NAME`, `MAIL_REPLY_TO` | The sender |
| `MAIL_NOTIFY_WELCOME`, `MAIL_NOTIFY_SIGN_IN`, `MAIL_NOTIFY_PAYMENTS` | Which automatic emails exist |
| `MAIL_REPORT_MAX_RECIPIENTS`, `MAIL_REPORT_PER_HOUR`, `MAIL_REPORT_MAX_ROWS`, `MAIL_REPORT_MAX_BYTES`, `MAIL_REPORT_ALLOW_EXTERNAL`, `MAIL_REPORT_ALLOWED_DOMAINS` | Limits on emailed reports |
| `STORAGE_TYPE` | `local` (default) or `network`: where profile pictures are kept |
| `STORAGE_LOCAL_PATH`, `STORAGE_NETWORK_PATH`, `STORAGE_NETWORK_SUBFOLDER`, `STORAGE_NETWORK_CREATE`, `STORAGE_MAX_PICTURE_BYTES` | Folder, mounted share, sub folder, create it if missing, picture size limit |
| `SIGNUP_ENABLED` | `true` (default). `false` closes sign up |
| `CORS_ALLOWED_ORIGINS` | For the browser console only |

> [!CAUTION]
> `CODE_DELIVERY=dev` returns the sign-in code in the response. Anyone could sign in as any number. Never use it on a server other people can reach.

## Failing well

The profile ymls for PostgreSQL, MySQL and MongoDB carry the resilience settings: Flyway retries the connection 6 times, 5 seconds apart, Hikari gives up after 5 seconds, and the Mongo URI sets `serverSelectionTimeoutMS=5000`. A database that is still starting delays boot instead of crashing it, and one that is missing produces one clear failure.

> [!NOTE]
> The 503 answers for database and broker outages that payment-service has are not in this service yet. Add them to `GlobalExceptionHandler` the same way.

## Known gaps

- No transactional outbox. If RabbitMQ is down when a customer reports, the acknowledgement in the chat can be lost.
- Request expiry is checked when a request is read, not by a timer.
- The nested record properties for hand-off settings were not exercised on Boot 4. Check the startup log on first run.

<div align="center">
<sub>M-Pesa is a registered mark of Safaricom. This project is not affiliated with or endorsed by Safaricom.</sub>
</div>
