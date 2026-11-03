<!-- PLACE THIS FILE AT: mpesa-ai-agent/FEATURES.md
     Replace the existing file. The images it uses are in mpesa-ai-agent/docs/assets/. You can delete this comment. -->

<div align="center">

<img src="docs/assets/banner-features.svg" alt="" width="100%"/>

<br/>

<img src="docs/assets/badges-features.svg" alt="Spring Boot 4.1.2, Flutter on Android and iOS, Claude with tool use, replies in English and Swahili, ten looks, the PIN is never seen by the system." width="880"/>

<br/><br/>

What the system does today. For setup, see the [project overview](README.md). For the design limits, see its "Known gaps" section.

<img src="docs/assets/divider.svg" alt="" width="100%"/>

</div>

> [!WARNING]
> **Status: a reference build.** The code was written and then migrated to Spring Boot 4.1.2. agent-service has been seen starting on Boot 4. Nothing has been through a full end to end test. Everything below describes the code as written. Expect to fix build errors, and verify the M-Pesa hand-off values and SMS wording on real phones before relying on any of it.

## In one paragraph

Customers chat with an AI assistant, in the app or on the web, in English or Swahili, by text, voice note, photo, video or file. The assistant prepares requests for their **own** M-Pesa: send money, Paybill, Till, Pochi la Biashara, airtime and Ratiba. The customer approves each one on their phone with fingerprint, face or phone lock, then finishes inside M-Pesa by typing the PIN there. The system holds no customer M-Pesa keys and never sees or stores the PIN.

<div align="center">
<img src="docs/assets/flow.svg" alt="Request flow: ask, prepare, approve, hand off, record" width="100%"/>
</div>

<div align="center">
<img src="docs/assets/divider.svg" alt="" width="100%"/>
</div>

## For customers

### What they can ask for

<div align="center">
<img src="docs/assets/capabilities.svg" alt="What the assistant can prepare: send money, Paybill, Till, Pochi la Biashara, airtime, Ratiba, voice and photos, contact search" width="880"/>
</div>

| Request | Details the assistant collects |
|---|---|
| Send money | A phone number, or a contact name from their phone, and the amount |
| Pay a Paybill | Paybill number, account number, amount |
| Pay a Till | Till number, amount |
| Pay Pochi la Biashara | The owner's phone number, amount |
| Buy airtime | Amount, and a number if it is not their own |
| Set up a Ratiba | What it pays, how often (daily, weekly, monthly), start date, optional end date |
| See their requests | A short list with status and M-Pesa code |
| Cancel a waiting request | By saying so, or with the Cancel button under the link |
| Record an M-Pesa code | Type it or send the M-Pesa message or a screenshot |
| Talk to a person | The assistant hands over to the team |

### How they talk to it

- **Text, voice notes** (optional speech to text), **photos** (for example a Paybill sign, invoice or M-Pesa message), **PDFs, Word, Excel, CSV and text files**, shared **contacts** and locations. Everything comes through the same attach menu as in a messaging app: photo, camera, video, record a video, document, contact, location, and a microphone button for voice notes.
- Replies in the language they write in. Short, plain formatting with bold, italics and tappable links.
- A tap-to-choose **menu** after "hi", "menu", "habari", "msaada" and similar greetings.
- Optional **spoken replies** to voice notes (off by default).
- Videos are kept in the chat and the assistant says plainly that it cannot open them. Unreadable files get a hint on what would help.
- **Finding a contact:** when the customer says "send 500 to Mama", the assistant shows a **Find in contacts** button. The customer searches this phone, Google, Microsoft or Outlook, or a vCard or CSV file, and shares the one person they choose.

### The approval flow

1. The assistant prepares the request and sends a link, plus a Cancel button, in the chat.
2. The link opens the Daraja Agent-X app. It shows the request in plain words with warnings.
3. The customer approves with fingerprint, face or phone lock (the phone's own check).
4. The app opens the dialler (code typed in, customer presses call) or the M-Pesa app, and shows numbered steps with copy buttons.
5. The customer types the M-Pesa PIN inside M-Pesa.
6. On Android, **Check my M-Pesa message** finds the confirmation SMS and matches it. Or they tap "I completed it".
7. The customer is told in the chat what was recorded.

<div align="center">
<img src="docs/assets/divider.svg" alt="" width="100%"/>
</div>

## The Daraja Agent-X app

Flutter, for Android, iOS, macOS, Linux and Windows. More in [`agentx-app/README.md`](agentx-app/README.md).

- **Sign in** with the phone number and a 6 digit code sent by SMS. Sign out at any time.
- **Chat tab** with the assistant: message bubbles, buttons and menus the assistant sends, photos, videos, documents, voice notes (record and play), shared locations and contacts, typing indicator, and a **Requests tab** beside it.
- Request list and request detail, with warnings (details read from a voice note, photo or file are flagged).
- Approval with biometrics or device lock. Decline with one tap.
- Hand-off to M-Pesa: dialler, optional M-Pesa app launch, guided steps, copy buttons. Values come from the server, so they can be corrected without a new release.
- **Contact search** across this phone (with permission, or its own chooser without one), Google contacts, Outlook contacts, a vCard or CSV file, or a typed number. Searching happens on the phone. See [`agentx-app/docs/CONTACTS.md`](agentx-app/docs/CONTACTS.md).
- **M-Pesa message check (Android only):** reads only messages from the sender `MPESA`, only on request, matches on the phone, and sends the code, kind, amount and payee, never the text. See [`agentx-app/docs/SMS.md`](agentx-app/docs/SMS.md).
- Deep links from `https://host/a/ID` and `agentx://a/ID`.
- iPhone: no message reading is possible. Customers send the M-Pesa message in the chat instead.

### Ten looks, saved on the phone

Picked from the palette button in the app bar. The browser console offers the same ten. Seven are everyday looks: Daylight, Graphite, Pwani (coastal teal), Mono, Matatu (street yellow, black and red), Kahawa (espresso brown with teal) and Blueprint (drafting navy with a grid). Three use M-Pesa colours: Savanna (green header), Lipa (white and red) and Receipt (paper slip, typewriter type).

<div align="center">
<img src="docs/assets/themes.svg" alt="The ten looks" width="100%"/>
</div>

<div align="center">
<img src="docs/assets/divider.svg" alt="" width="100%"/>
</div>

## How results are recorded

| Level | Meaning |
|---|---|
| `NONE` | Nothing reported yet |
| `REPORTED` | The customer said it was done, maybe with a code |
| `SMS_AMOUNT` | A message on their phone matched the amount, kind and time. It did not show who received it |
| `SMS_MATCHED` | As above, and the recipient number or the Paybill account matched too |

> [!IMPORTANT]
> None of these is a Safaricom confirmation. A message can also correct a request wrongly marked "not done", and one M-Pesa code can back only one request.

Request states: `CREATED`, `APPROVED`, `HANDED_OFF`, `DONE_REPORTED`, `NOT_DONE`, `DECLINED`, `CANCELLED`, `EXPIRED`.

## Safety and privacy built in

<div align="center">
<img src="docs/assets/safety.svg" alt="Safety built in: no PIN, phone lock approval, whitelist checks, read-back, scam warnings, one phone one chat, rate limits, 30 minute expiry" width="880"/>
</div>

<table>
<tr>
<td width="50%" valign="top">

**Money and the PIN**

- **No PIN, no keys.** The assistant refuses to take a PIN. The app and servers never see it.
- **The model never moves money.** It only calls prepare tools. The phone number on a request is the signed-in customer's, read from their token.
- **Whitelist validation** per request type: Paybill and Till numbers are 5 to 7 digits, Kenyan numbers are normalised, amounts are limited (airtime minimum KES 5), unknown fields are rejected, a request to your own number is refused.
- **Honest wording:** results are described as "reported by you" or "matches the message on your phone", never "confirmed by Safaricom".

</td>
<td width="50%" valign="top">

**What the model reads**

- **Read-back rule:** details taken from a voice note, photo or file are read back by the assistant and flagged again in the app.
- **Prompt-injection defence:** text inside images, documents, transcripts and forwarded messages is treated as data, never as instructions.
- **Scam warnings** for prizes, loans, jobs, "reverse my money" and "release fee" stories.

</td>
</tr>
<tr>
<td width="50%" valign="top">

**People and sessions**

- **Isolation:** every request belongs to one phone number. Another customer's link cannot open it.
- **Chat isolation:** the chat and its files belong to the number in the token. Nobody can read another number's chat or download its files.
- **Sign-in codes:** sent by SMS, valid 10 minutes, 5 tries, 3 codes an hour, stored hashed, never shown to the model.
- **Sessions:** signed tokens tied to a device, revocable by signing out.
- **Contacts stay on the phone.** The server only sees the one contact the customer chooses to share. Google and Microsoft tokens stay in memory on the phone.

</td>
<td width="50%" valign="top">

**Limits and plumbing**

- **Limits:** max open requests per customer, request expiry (30 minutes before approval), rate limiting per customer, duplicate message handling, per-customer locking so messages are handled in order.
- **Internal calls** between services carry a shared internal key. The sign-in code mode (`CODE_DELIVERY`) defaults to log only.
- **Failing well:** database and broker problems time out quickly and produce one clear answer, not a hang. See below.

</td>
</tr>
</table>

<div align="center">
<img src="docs/assets/divider.svg" alt="" width="100%"/>
</div>

## Behind the scenes

<div align="center">
<img src="docs/assets/architecture.svg" alt="Architecture" width="100%"/>
</div>

<br/>

| Service | Port | What it does |
|---|---|---|
| [agent-service](agent-service/README.md) | 8082 | Claude model with tool use, the chat (messages and files) and conversation memory, role handling, menus, media understanding, contact lookup |
| [agentx-api](agentx-api/README.md) | 8084 | Signs customers in by SMS code, relays the chat, stores requests, serves the app, matches message evidence, publishes events, serves app-link files |
| [agentx_app](agentx-app/README.md) | none | The Flutter app above |
| [payment-service](payment-service/README.md) | 8083 | **Optional** Daraja module for a business, off by default |

**Infrastructure.** A database you choose with `DATABASE_TYPE` (MongoDB, PostgreSQL or MySQL, with separate `payments` and `agentx` databases), Redis and RabbitMQ. payment-service defaults to MongoDB when run directly, and the compose file starts PostgreSQL unless you say otherwise. See [`docs/DATABASES.md`](docs/DATABASES.md). All Java services use the same layered packages: controller, service, repository, model, dto, exception, config.

**Engineering features.** Flyway migrations for the SQL databases, idempotency keys, row locking on state changes, events sent only after commit, Docker Compose for everything, per-service `.env.example` files read through `spring.config.import`, and unit and integration tests for the Java services and Dart tests for the app.

### Failing well

| Situation | What happens |
|---|---|
| Database not up yet when a SQL service starts | Flyway retries the connection 6 times, 5 seconds apart, then stops with one clear message |
| Database or Mongo unreachable at run time | Hikari times out after 5 seconds, Mongo after `serverSelectionTimeoutMS=5000`, so requests do not hang |
| Database, query timeout or RabbitMQ failure inside a payment call | payment-service answers **503 Service Unavailable** with a plain body |
| The model API answers 408, 429, 5xx or 529 | agent-service tries up to 3 times before giving up |

The 503 handler is in payment-service today. agent-service and agentx-api still need the same treatment.

<div align="center">
<img src="docs/assets/divider.svg" alt="" width="100%"/>
</div>

## Business accounts, chat transactions and chat files

- **Business accounts** (Settings): Paybill or Buy Goods shortcode, till numbers, consumer key and secret, STK passkey, initiator name and password, encrypted on the server. Security credential made by the server. Several branches or tills under one shortcode, and store numbers or child shortcodes with their own number (a Buy Goods store number has its linked till, a Paybill child has none), each using the head office's credentials or its own, whichever it has from Daraja. CSV upload with a full check first. See [`agentx-app/docs/BUSINESS_ACCOUNTS.md`](agentx-app/docs/BUSINESS_ACCOUNTS.md).
- **Transactions in chat:** STK Push, STK Push query, Dynamic QR, balance, pull transactions, B2C, Pochi la Biashara, B2B. Money out needs approval in the app. Requests are sent once.
- **`@` mentions and contact picker** in the chat box.
- **Chat file storage:** Redis (default, 24 hours), a folder or network share, or S3-compatible storage. See [`agent-service/STORAGE.md`](agent-service/STORAGE.md).
- **Phone file cache** with a size limit, and a My OneApp screen. See [`agentx-app/docs/FILES_AND_MY_ONEAPP.md`](agentx-app/docs/FILES_AND_MY_ONEAPP.md).

## Sign up, profiles and email

- **Sign up** in the app, the console or over the API: phone, first and last name (middle name and email optional), then the same 6 digit SMS code that sign in uses. No password. A new customer is signed in as soon as the code is right. People who already had an account get an empty profile on first use.
- **Profile:** first, middle and last name, email, address, city, county, country, a saved location and a profile picture, all editable in the app, in the console, over the API, or by asking the assistant.
- **Profile picture** is checked, shrunk to 512 pixels and cleaned of EXIF data, then kept where the backend is set to keep files: a local folder, or a mounted network share. One setting, `agentx.storage.type: local` or `network`.
- **Email address** counts only after a 6 digit code sent to it has been typed. The code never reaches the assistant.
- **Automatic emails:** a welcome, a notice when the account signs in on a device, and a notice when a payment or business request finishes. Customers can switch the sign in and payment notices off.
- **Reports by email:** say "send this report to my email as a PDF", "email this as Excel to a@x.com" or "send it to everyone on my list as CSV". PDF, CSV and Excel, from the table in the chat or from business requests, business accounts and payments. With no address named it goes to the verified profile email. The owner is told when a report goes to someone else.
- **Saved addresses:** add, paste or upload (CSV or text) a list of addresses to send reports to, and name them ("my accountant").
- **Many chats:** a customer can start a new chat, go back to an old one (its messages and the assistant's memory come back), rename it, pin it and delete it. In the app under the Chats button and in the console's Chats panel. See [`docs/CHATS.md`](docs/CHATS.md).
- **Mail settings** in `application.yml` under `agentx.mail`: `log` (writes emails to the log, the default) or `smtp`, sender, limits, allowed domains. See [`docs/PROFILE_AND_EMAIL.md`](docs/PROFILE_AND_EMAIL.md).

## Optional business payments (Daraja)

Switched on with `docker compose --profile business up` and `AGENT_BUSINESS_PAYMENTS=true`. Off by default. See [`payment-service/README.md`](payment-service/README.md).

- **STK Push** to your own Paybill or Till after the customer taps Pay, with the result coming only from Safaricom's callback, a reconciler for lost callbacks, and payment status and history.
- **C2B** direct Paybill and Till payments (validation and confirmation callbacks).
- **Receipts** as PDF, and **QR codes** to pay.
- **Refunds**, **payouts** (withdrawals, disbursements) and **reversals** through B2C, all needing a second person to approve.
- **Transaction lookup** and **account balance** for staff and admins.
- **Roles:** `CUSTOMER`, `STAFF`, `APPROVER`, `ADMIN`, decided from a staff list and the signed-in number. The model only sees the tools its role allows, and payment-service checks again.
- **Limits:** per payment and per day, configurable.

## Try it without a real frontend

<table>
<tr>
<td width="50%" valign="top">

**[`postman/`](postman/README.md)**

A collection and an environment for every endpoint, with tests and scripts that carry values from one call to the next. 137 requests, and a walkthrough you can run with the collection runner.

</td>
<td width="50%" valign="top">

**[`console/console.html`](console/README.md)**

One HTML file, run with `python3 -m http.server`, that simulates the frontend. It needs `CORS_ALLOWED_ORIGINS` set on the services, which is off by default.

</td>
</tr>
</table>

The console has:

- a journey view that draws one request as a trace across customer, agent, Daraja Agent-X API, phone and M-Pesa,
- a real chat against the real agent: sign in with a code, attach menu, file pickers, voice notes, camera and video recording, drag and drop, location, and contact search across Google, Microsoft, a phone picker and vCard or CSV,
- a phone app simulator, a Business tab (accounts, tills and branches, CSV upload, check with Safaricom, approve or decline the requests the assistant prepares) and an explorer for every endpoint,
- a command palette (Ctrl+K), and the same ten themes as the app, with an upload slot for your own logo.

## Not included

> [!NOTE]
> These are left out on purpose or not built yet.

- Moving or holding money. Only the customer can, inside M-Pesa.
- Verified payment status from Safaricom for personal wallets. See "Known gaps" in the [project overview](README.md).
- Reading M-Pesa messages on iPhone, or in the background on Android.
- "Money received" detection for personal wallets, and recipient-side confirmation.
- A web-only approval page without the app, push notifications (the chat is read by polling while the app is open), an admin dashboard, a support inbox, saved recipients and bill presets, and Swahili text in the app screens.
- Any production setup: an SMS sender registration, app store releases, privacy registration and notices, monitoring and backups.

<div align="center">
<img src="docs/assets/divider.svg" alt="" width="100%"/>
<sub>M-Pesa is a registered mark of Safaricom. This project is not affiliated with or endorsed by Safaricom.</sub>
</div>
