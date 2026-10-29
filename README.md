# Chat assistant for your own M-Pesa

Customers chat in the app or on the web (text, voice notes, photos, videos, PDFs, Word and Excel files, shared contacts and locations). An AI assistant understands what
they want, such as "send 500 to Mama", "pay Paybill 888880 account 1234" or "buy 50 airtime", and prepares it.
The customer then approves on their own phone and finishes inside M-Pesa, using the system that already works.

## The idea in one picture

```
 Chat (in the app)              Approve on the phone                         M-Pesa
 ----------------              --------------------                         ------
 "send 500 to Mama"     1.  request link arrives in the chat
 assistant prepares     2.  app shows: Send KES 500 to Mama
 the request            3.  fingerprint / face / phone lock (approval)
                        4.  app opens the dialler or M-Pesa,       5. customer types the
                            steps ready                                M-Pesa PIN here, only here
                        6.  Android: "Check my M-Pesa message" finds the confirmation SMS
                            on the phone and matches it. Or tap "I completed it".
 assistant records it:
 "matches the message on your phone" or "reported by you"
```

What this design deliberately does **not** do:

* It holds **no M-Pesa API keys for customers** and cannot move anyone's money. Only the customer can, inside M-Pesa.
* It never sees, asks for, or stores the M-Pesa PIN. The assistant is told to refuse it if someone sends it.
* It cannot get a confirmation from Safaricom. On Android the app can match the M-Pesa confirmation SMS on the customer's own
  phone (on request, sender MPESA only, only the parsed code, amount and payee are sent). Otherwise the status is "reported by
  the customer". Either way it is a record, not proof. A forwarded message or screenshot is kept with the request.

See `FEATURES.md` for the full list of what the system does today, `postman/` for a Postman collection of every endpoint, and `console/` for a single HTML page that works like a frontend for all of them (`cd console && python3 -m http.server 8000`).

## Status: read this first

Nothing here has been **compiled, run or tested end to end**. The sandbox that wrote it could not download Maven
packages and has no Flutter SDK. The Java was checked for syntax only. Expect compile errors and a few failing tests
on the first build. Do this before anything else:

```
cd payment-service && mvn clean verify     # repeat in agent-service, companion-api
cd companion-app && ./setup.sh your.reverse.domain && flutter analyze && flutter test
```

Then try it with the console, the `dev` sign-in mode and small amounts.

## The parts

| Part | Port | Job | Needed? |
|---|---|---|---|
| agent-service | 8082 | Talks to the AI model, keeps the chat (messages and files), prepares requests with tools | Yes |
| companion-api | 8084 | Signs customers in with an SMS code, relays the chat to the agent, stores prepared requests, serves the app, publishes "customer reported" events | Yes |
| companion-app | n/a | Flutter app for Android and iOS: chat, search contacts, approve, hand over to M-Pesa, report | Yes |
| payment-service | 8083 | Daraja for a **business** (STK Push to your own shortcode, refunds, payouts, staff roles) | **Optional**, off by default |

Infrastructure: PostgreSQL (two databases, `payments` and `companion`), Redis (conversation state), RabbitMQ
(events). The Java services share the same layered packages: controller, service, repository, model, dto, exception, config.

### How a request is kept safe

* The assistant never moves money. It only calls "prepare" tools. The phone number on a request is the verified
  signed-in customer's number from their token, never something the model typed.
* Details are checked by a whitelist per type (Paybill and Till numbers are 5 to 7 digits, Kenyan numbers are
  normalised, amounts are limited, unknown fields are rejected, a request to your own number is refused).
* Details read from a voice note, photo or file are flagged in the app, and the assistant reads them back before preparing.
* A request belongs to one phone number. Another customer's link cannot open it.
* Links carry only a random id. Requests expire after 30 minutes if not approved.
* Signing in needs a 6 digit code sent to the customer's phone by SMS (valid 10 minutes, 5 tries, 3 codes an hour).
  The model never sees the code. The chat belongs to the number in the token: nobody can read or write another number's chat.
* Contacts: for "send 500 to Mama" the assistant asks for the person by name. The app (or the console) searches this phone, Google,
  Microsoft or a vCard/CSV file and sends back only the one contact the customer chooses. Your servers never see the address book.
  See `companion-app/docs/CONTACTS.md`.
* Text found inside photos, documents and voice transcripts is treated as data, never as instructions.

### Request states

CREATED, then APPROVED (after the phone check), HANDED_OFF (dialler or M-Pesa opened), then DONE_REPORTED or NOT_DONE.
A request can also be DECLINED, CANCELLED or EXPIRED. Separately, each request carries a `verification` level: NONE, REPORTED
(customer said so), SMS_AMOUNT (a message on their phone matched amount, kind and time) or SMS_MATCHED (and the recipient or Paybill
account too). A message can also correct a mistaken NOT_DONE. Cancel buttons appear in the chat next to each link.

## What the assistant can prepare

| Customer says | Needs |
|---|---|
| Send money to a number or a contact | Number or contact name, amount |
| Pay a Paybill | Paybill number, account, amount |
| Pay a Till | Till number, amount |
| Pay Pochi la Biashara | Owner's number, amount |
| Buy airtime | Amount, and a number if not their own |
| Set up a Ratiba | What it pays, how often (daily, weekly, monthly), start date, optional end date |
| Show my requests, cancel one, record an M-Pesa code | Nothing |

## Setup

### 1. Prerequisites

Java 21, Maven 3.9+, Docker, Flutter. An Anthropic API key and a public HTTPS address for companion-api (a tunnel such as Cloudflare Tunnel
is fine for testing). For real sign-in codes, an Africa's Talking account (SMS).

### 2. Configure

Copy each `.env.example` to `.env` in its own folder. Use the **same** `INTERNAL_KEY` everywhere. Key values:

* agent-service: `ANTHROPIC_API_KEY`, business name, escalation phones, `COMPANION_PUBLIC_BASE_URL`. Voice is optional.
* companion-api: `COMPANION_TOKEN_SECRET` (32 or more random characters), `COMPANION_PUBLIC_BASE_URL`,
  `CODE_DELIVERY` (below), and the hand-off values below.

`CODE_DELIVERY` decides how the sign-in code reaches the customer: `log` writes it to the companion-api log (default, for a developer),
`dev` also returns it to the app and console so a test can sign in alone (**never on a system other people can reach**: anyone could sign in
as any number), and `sms` sends a text message through Africa's Talking (`SMS_USERNAME`, `SMS_API_KEY`, `SMS_SENDER_ID`).

### 3. Run

```
docker compose up --build
```

This starts PostgreSQL (creating the `companion` database through `docker/postgres-init`), Redis, RabbitMQ, the
agent and companion-api. If PostgreSQL already has a data volume from an earlier run, the init script is
skipped, so create the database once by hand: `docker compose exec postgres createdb -U app companion`.

### Choosing the database

PostgreSQL is the default. MySQL and MongoDB work too: change `spring.profiles.active` in `application.yml`
(or set `DATABASE_TYPE`) and start the matching container with `docker compose --profile mysql up` or
`docker compose --profile mongodb up`. MongoDB has no multi-record transactions, so prefer a SQL database for
`payment-service`. Details, trade-offs and what was tested are in `docs/DATABASES.md`.

### 4. Build and publish the app

See `companion-app/README.md` and `companion-app/docs/PLATFORM_SETUP.md`. Build with
`--dart-define=API_BASE_URL=https://your-companion-domain`. Put the Android package, signing certificate fingerprint
and iOS app id into the companion-api `.env` so `https://your-companion-domain/a/ID` links open the app directly.

### 5. Hand-off values (verify these)

The app can dial a code with the details already typed in, or open the M-Pesa app. **The values for that are not
hard coded because they were not verified.** They live in the companion-api `.env`:

```
HANDOFF_USSD_MAIN=*334#
HANDOFF_USSD_PAY_TILL=        # for example a code with {till} and {amount} placeholders
HANDOFF_MPESA_ANDROID_PACKAGE=
HANDOFF_MPESA_IOS_URL=
```

Placeholders are `{amount}`, `{phone}` (local format like 0712345678), `{paybill}`, `{account}`, `{till}`. The app only
ever dials digits, `*` and `#`, so a Paybill with a letter in the account number falls back to guided steps. Leave a
template empty and the app dials the main M-Pesa menu and shows numbered steps with copy buttons. Confirm the real
menu paths and any app identifiers with the M-Pesa team, and test on a real phone with small amounts.

## Voice, images and files

* **Voice notes** are recorded in the app or console (AAC in .m4a on phones, WebM or MP4 in browsers) and transcribed by a speech to text
  provider with an OpenAI compatible `/audio/transcriptions` endpoint. It is **off by default**. Check that your provider accepts those formats. Turning it on sends customers' audio to that provider. Choose a provider you are
  allowed to use, tell your customers, and check your obligations under the Kenya Data Protection Act. Numbers and
  names in transcripts can be wrong, so the assistant reads them back and the app flags them.
* **Spoken replies** (text to speech) are optional and off by default.
* **Images** (a photo of an M-Pesa message, an invoice, a Paybill sign) go to the model directly. **PDFs** go to the
  model. **Word, Excel, CSV and text** files are read on the server. Size limits are configurable.
* Videos and unsupported files are kept in the chat, and the assistant says plainly that it cannot open them.
* **Contacts and locations** shared in the chat reach the assistant as plain text (name and number, or coordinates).

## Optional: business payments (Daraja)

payment-service is the earlier part of this project: STK Push to **your own** shortcode, refunds, payouts and staff
approvals, using Daraja keys. It is **not needed** for personal wallet requests. To use it:

1. `docker compose --profile business up --build`
2. Set `AGENT_BUSINESS_PAYMENTS=true` in the agent-service `.env`.
3. Follow `payment-service/README.md` for Daraja setup, callbacks and the staff list.

With the switch off, the assistant is told nothing about these tools, the code refuses them, and every sender is a customer.

## Android and iOS differences

| | Android | iOS |
|---|---|---|
| Dialler with the code filled in | Opens the dialler, the customer presses call | Same, with a system confirmation |
| Open the M-Pesa app | Possible by package name (not verified) | Only if it has a URL scheme (not verified) |
| Reading the M-Pesa SMS to match the request | On request, with permission (`companion-app/docs/SMS.md`). Google Play restricts this permission. | Not possible. The customer sends the message in the chat. |
| Approval | Fingerprint, face or phone lock | Face ID, Touch ID or passcode |

## Known gaps and things to decide

* **Safaricom terms.** Check Safaricom's terms and the app store rules for a third party app that hands over to M-Pesa.
  The design avoids automating USSD, touching the PIN, and reading SMS in the background (SMS is read only on request, on Android), but get this confirmed.
* **No proof of payment.** Confirmation codes come from the customer, or from an SMS on the customer's own phone (Android). That SMS
  can be faked on a modified phone, so do not release goods or large value to a third party on it alone. If you need verified status, you need a
  business integration (Daraja) or Safaricom's own statement tools.
* No transactional outbox: if RabbitMQ is down when a customer reports, the acknowledgement in the chat can be lost.
* Request expiry is checked when a request is read, not by a timer. A request that was opened in M-Pesa never expires on its own.
* The chat is read by polling (about every 1.5 seconds while open). There is no push notification, so a reply that arrives while the app is closed is seen when it is opened.
* The sign-in code proves the customer holds the SIM only as well as SMS does. SIM swap is a risk for any SMS sign-in.
* The Google and Microsoft contact sign-ins need your own OAuth client ids and were not run against real accounts, see `companion-app/docs/CONTACTS.md`.
* The phone check protects our approval step only. It is recorded as "biometric" or "device lock" as a best guess,
  because the operating system does not say which was used.
* The M-Pesa SMS wording patterns in `companion-app/lib/models/mpesa_sms.dart` were written from memory. Test them with real messages.
* Spring `@ConfigurationProperties` with nested records (companion hand-off settings) has not been exercised; check the
  startup log on first run.
* No admin dashboard, offline mode, or Swahili text in the app screens yet (the assistant itself replies in the customer's language).

## Security notes

* Only the companion-api HTTPS paths (`/a/*`, `/api/*`, `/.well-known/*`) should be public.
  Keep agent-service (8082) private, and block `/internal/*` on companion-api at your proxy.
* Every call between services carries `X-Internal-Key`.
* Phone sessions are signed tokens (30 days by default) that can be revoked by signing the phone out.
* Never commit `.env` files or keys. There are no M-Pesa customer secrets to leak because none are held.
