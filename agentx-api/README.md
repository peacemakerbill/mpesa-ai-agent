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
  storage/      where files are kept: profile pictures and chat files (folder, network share, database or Cloudinary)
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

Locally the code is shown on screen by default (`CODE_SHOW_IN_APP=auto`), so you can sign in from the console without an SMS account. Run the tests with `mvn clean verify`.

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
| `POST /api/actions/{id}/approve` | Approve after the phone check (`method` BIOMETRIC or DEVICE_CREDENTIAL), or with a text message code (`method` OTP and `code`) |
| `POST /api/actions/{id}/approval-code` | Send a 6 digit approval code by text message to the signed in number. Valid 5 minutes for this request only, 30 seconds between codes, 6 per hour, 5 wrong tries per code. Table `approval_codes` (migration V8) |
| `POST /api/actions/{id}/handoff` | Record that the dialler or M-Pesa was opened |
| `POST /api/actions/{id}/report` | Customer says it is done, or not done |
| `POST /api/actions/{id}/sms-evidence` | Send the parsed message fields for matching |
| `POST /api/actions/{id}/decline` | Decline |
| `DELETE /api/actions/{id}` | Clear one finished request from the customer's list (hidden, not deleted). Open ones answer 409 |
| `POST /api/actions/clear` | Clear several finished requests, body `{"ids":[...]}` (up to 100). Open ones are skipped. Answers `{cleared, skipped}` |
| `DELETE /api/actions` | Clear every finished request (newest 200 per call). Answers `{cleared, skipped}` |
| `GET`, `POST`, `DELETE /api/chat/messages` | Read, send and clear the chat |
| `GET`, `POST`, `DELETE /api/chat/sessions`, `POST /api/chat/sessions/{id}/open`, `PUT .../title`, `PUT .../pin`, `DELETE /api/chat/sessions/{id}` | The list of chats: start a new one, open an old one, rename, pin, delete. See [`docs/CHATS.md`](../docs/CHATS.md) |
| `POST /api/chat/media`, `GET /api/chat/media/{id}` | Upload and download chat files |
| `GET`, `POST /api/business-accounts` | List the customer's business accounts, and save one. Secrets are encrypted and only a clue (the last 4 characters) comes back, with a `credentials` block that says which credentials are saved and whether the vault key can still open them |
| `GET`, `PUT`, `DELETE /api/business-accounts/{id}` | Read, change and remove one account. A secret left out of a change keeps the saved value |
| `POST /api/business-accounts/{id}/verify` | Ask Safaricom for a token with the saved consumer key and secret, and check the certificate for the initiator password |
| `GET /api/business-accounts/csv-template`, `POST /api/business-accounts/import` | The CSV template, and upload up to 500 accounts |
| `GET /api/business-operations`, `GET /api/business-operations/{id}` | List and read business requests. Each carries a `timeline` of what happened, step by step, with the time of each |
| `POST /api/business-operations/qr` | Make a Dynamic QR code to pay a saved account, or any Till, Paybill or phone number. See "QR codes and Lipa na Bonga" below |
| `POST /api/business-operations/{id}/approval-code`, `.../approve`, `.../decline` | Approve (fingerprint, phone lock or text message code) or decline a business request that is waiting |
| `GET /a/{id}` | Landing page that opens the app. The chat no longer shows this address, it is for links opened from a browser or another app |
| `GET /.well-known/assetlinks.json` and `apple-app-site-association` | App link files |

**Called by Safaricom** (no session token, the address carries a one time token for that request)

| Method and path | Purpose |
|---|---|
| `POST /stk-callback/{id}/{token}` | The answer to an STK Push |
| `POST /results/{id}/{token}`, `POST /timeout/{id}/{token}` | The result, and the timeout, of B2C, B2B, balance, transaction status, reversal and pull transactions |
| `GET /daraja/ping` | Answers `{"status":"ok","service":"agentx-api"}`, so you can check that this address leads to this service from the internet. It shows nothing private |

The older routes `/daraja/business/{id}/{token}/callback`, `/result` and `/timeout` are still served for requests that were sent before the short paths. The addresses are chosen by `AGENTX_STK_CALLBACK_URL`, `AGENTX_RESULT_URL` and `AGENTX_TIMEOUT_URL`, see [`docs/STK_CALLBACK.md`](../docs/STK_CALLBACK.md).

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

## QR codes and Lipa na Bonga

`POST /api/business-operations/qr` (device token) makes a Daraja Dynamic QR code, a picture a customer scans in the My Safaricom or M-PESA app to pay. Body: `account` (a saved business account, whose consumer key and secret sign the call), `amount` (whole shillings), optional `reference`, `merchantName`, and `payTo` of `account` (default, the saved account's own Till or Paybill), `till`, `paybill` or `phone` with `payToNumber`. A Paybill code needs the account number to pay into. The answer is the usual operation view with `qrCode` holding a base64 PNG. The picture is not stored, so make another when you need it. At most 60 codes per hour per customer. The chat assistant does the same through the `DYNAMIC_QR` request type.

Lipa na Bonga has no merchant or Daraja API. It is a customer-side USSD flow (*126#, Till number, amount, Service PIN), 1 Bonga Point worth KES 0.20. The assistant and the app only show those steps for a Buy Goods QR code. They never charge Bonga Points and never ask for a Service PIN.

## Configuration

| Variable | Meaning |
|---|---|
| `INTERNAL_KEY` | Shared secret for calls between services |
| `AGENTX_TOKEN_SECRET` | 32 or more random characters. Signs sessions |
| `AGENTX_PUBLIC_BASE_URL` | Public HTTPS address, used in links and the link files |
| `AGENTX_STK_CALLBACK_URL` | Optional. Public HTTPS address Safaricom posts STK Push answers to. Blank uses `AGENTX_PUBLIC_BASE_URL` |
| `AGENTX_RESULT_URL`, `AGENTX_TIMEOUT_URL` | Optional. Where Safaricom posts the final result, and the queue timeout notice, of B2C, B2B, Pochi, balance, status and reversal requests. An address, or a template with `{id}` and `{token}`. Blank uses `AGENTX_PUBLIC_BASE_URL`. See `docs/STK_CALLBACK.md` |
| `ZROK_ENABLE_TOKEN` or `NGROK_AUTHTOKEN`, and `DEV_TUNNEL`, `DEV_TUNNEL_PROVIDER`, `DEV_TUNNEL_INSTALL` | Development only. When no address is set for STK answers, a zrok2 or ngrok tunnel is opened for you on Linux, macOS or Windows. See `docs/DEV_TUNNEL.md` |
| `APP_ENV` | `development` (default), `staging` (or `uat`) or `production`. Production refuses to start with development only settings, and outside production a business account on Safaricom's production Daraja is refused. See [`docs/ENVIRONMENTS.md`](../docs/ENVIRONMENTS.md) |
| `AGENTX_ALLOW_LIVE_DARAJA` | `false` (default). `true` lets a copy that is not production call live Daraja |
| `CODE_DELIVERY` | `log` (default) or `sms` |
| `CODE_SHOW_IN_APP` | `auto` (default: the app also shows the code in development and staging, never in production), `true` or `false` |
| `SMS_USERNAME`, `SMS_API_KEY`, `SMS_SENDER_ID` | Africa's Talking, for `sms` |
| `DATABASE_TYPE` | `mongodb`, `postgresql` or `mysql`. See [`docs/DATABASES.md`](../docs/DATABASES.md) |
| `MONGODB_URI` | Add `serverSelectionTimeoutMS=5000` so a missing database fails fast |
| `HANDOFF_USSD_MAIN`, `HANDOFF_USSD_PAY_TILL`, `HANDOFF_MPESA_ANDROID_PACKAGE`, `HANDOFF_MPESA_IOS_URL` | Hand-off values. **Not verified, set them yourself** |
| Android package and signing fingerprint, iOS app id | So `/a/ID` links open the app directly |
| `MAIL_TRANSPORT` | `log` (default, nothing is sent) or `smtp` |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_STARTTLS`, `MAIL_SSL` | The mail server, for `smtp` |
| `MAIL_FROM`, `MAIL_FROM_NAME` | The sender. Replies to a report go to the customer who sent it (their verified profile email). `MAIL_REPLY_TO` is only a fallback for system emails |
| `MAIL_NOTIFY_WELCOME`, `MAIL_NOTIFY_SIGN_IN`, `MAIL_NOTIFY_PAYMENTS` | Which automatic emails exist |
| `MAIL_REPORT_MAX_RECIPIENTS`, `MAIL_REPORT_PER_HOUR`, `MAIL_REPORT_MAX_ROWS`, `MAIL_REPORT_MAX_BYTES`, `MAIL_REPORT_ALLOW_EXTERNAL`, `MAIL_REPORT_ALLOWED_DOMAINS` | Limits on emailed reports |
| `STORAGE_TYPE` | `local` (default), `network`, `database` (the database this service uses, no disk needed) or `cloudinary`: where profile pictures and chat files are kept |
| `STORAGE_LOCAL_PATH`, `STORAGE_NETWORK_PATH`, `STORAGE_NETWORK_SUBFOLDER`, `STORAGE_NETWORK_CREATE`, `STORAGE_MAX_PICTURE_BYTES` | Folder, mounted share, sub folder, create it if missing, picture size limit |
| `STORAGE_DB_MAX_FILE_BYTES`, `STORAGE_PURGE_INTERVAL` | Biggest file kept in the database (15 MB, MongoDB stops at 16 MB), how often expired database files are removed |
| `CLOUDINARY_URL`, `STORAGE_CLOUDINARY_CLOUD_NAME`, `STORAGE_CLOUDINARY_API_KEY`, `STORAGE_CLOUDINARY_API_SECRET`, `STORAGE_CLOUDINARY_FOLDER` | The Cloudinary account when `STORAGE_TYPE=cloudinary`. Give the address from the dashboard, or the three values |
| `SIGNUP_ENABLED` | `true` (default). `false` closes sign up |
| `AGENTX_BUSINESS_MAX_AMOUNT`, `AGENTX_BUSINESS_APPROVAL_TTL`, `AGENTX_BUSINESS_STK_PER_HOUR`, `AGENTX_BUSINESS_APPROVE_COLLECTIONS` | Limits on requests made on business accounts |
| `AGENTX_RECONCILE_ENABLED`, `AGENTX_RECONCILE_INTERVAL`, `AGENTX_RECONCILE_MIN_AGE`, `AGENTX_RECONCILE_GIVE_UP_AFTER` | The job that asks Safaricom about an STK Push whose callback never arrived (defaults: on, every 1 minute, after 2 minutes, give up after 15 minutes) |
| `CORS_ALLOWED_ORIGINS` | For the browser console only |

> [!CAUTION]
> While `CODE_SHOW_IN_APP` is on, the sign-in code is returned in the response, so anyone who knows a phone number could sign in as it. Production never returns it and refuses to start with `CODE_SHOW_IN_APP=true`. Set it to `false` once `CODE_DELIVERY=sms` works. The old `CODE_DELIVERY=dev` still works and means the same as `log` with the code shown.

A code that is shown in the app is not written to the log. The Android app can read the sign-in text by itself: it sends an optional `appHash` (11 characters) with `POST /api/auth/request-code` and `POST /api/auth/signup/request`, and the server ends the text with it, which is what Google's SMS Retriever needs. A value of the wrong shape is ignored.

## Failing well

The profile ymls for PostgreSQL, MySQL and MongoDB carry the resilience settings: Flyway retries the connection 6 times, 5 seconds apart, Hikari gives up after 5 seconds, and the Mongo URI sets `serverSelectionTimeoutMS=5000`. A database that is still starting delays boot instead of crashing it, and one that is missing produces one clear failure.

> [!NOTE]
> This service does not answer 503 for database and broker outages yet. Add that to `GlobalExceptionHandler`.

## Known gaps

- No transactional outbox. If RabbitMQ is down when a customer reports, the acknowledgement in the chat can be lost.
- Request expiry is checked when a request is read, not by a timer.
- The nested record properties for hand-off settings were not exercised on Boot 4. Check the startup log on first run.

<div align="center">
<sub>M-Pesa is a registered mark of Safaricom. This project is not affiliated with or endorsed by Safaricom.</sub>
</div>