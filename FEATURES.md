# Features

What the system does today. For setup, see `README.md`. For the design limits, see the "Known gaps" section there.

> **Status:** written but not compiled, run or tested end to end. Everything below describes the code as written. Expect to fix
> build errors and to verify the M-Pesa hand-off values and SMS wording on real phones before relying on any of it.

## In one paragraph

Customers chat with an AI assistant, in the app or on the web, in English or Swahili, by text, voice note, photo, video or file. The assistant
prepares requests for their **own** M-Pesa: send money, Paybill, Till, Pochi la Biashara, airtime and Ratiba. The customer
approves each one on their phone with fingerprint, face or phone lock, then finishes inside M-Pesa by typing the PIN there.
The system holds no customer M-Pesa keys and never sees or stores the PIN.

## For customers

### What they can ask for
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
* Text, **voice notes** (optional speech to text), **photos** (for example a Paybill sign, invoice or M-Pesa message),
  **PDFs, Word, Excel, CSV and text files**, shared **contacts** and locations. Everything comes through the same attach menu as in a messaging app: photo, camera, video, record a video, document, contact, location, and a microphone button for voice notes.
* Replies in the language they write in. Short, plain formatting with bold, italics and tappable links.
* A tap-to-choose **menu** after "hi", "menu", "habari", "msaada" and similar greetings.
* Optional **spoken replies** to voice notes (off by default).
* Videos are kept in the chat and the assistant says plainly that it cannot open them. Unreadable files get a hint on what would help.
* **Finding a contact:** when the customer says "send 500 to Mama", the assistant shows a **Find in contacts** button. The customer searches this phone, Google, Microsoft/Outlook, or a vCard/CSV file, and shares the one person they choose.

### The approval flow
1. The assistant prepares the request and sends a link, plus a Cancel button, in the chat.
2. The link opens the companion app. It shows the request in plain words with warnings.
3. The customer approves with fingerprint, face or phone lock (the phone's own check).
4. The app opens the dialler (code typed in, customer presses call) or the M-Pesa app, and shows numbered steps with copy buttons.
5. The customer types the M-Pesa PIN inside M-Pesa.
6. On Android, **Check my M-Pesa message** finds the confirmation SMS and matches it. Or they tap "I completed it".
7. The customer is told in the chat what was recorded.

## The companion app (Flutter, Android and iOS)
* **Sign in** with the phone number and a 6 digit code sent by SMS. Sign out at any time.
* **Chat tab** with the assistant: message bubbles, buttons and menus the assistant sends, photos, videos, documents, voice notes (record and play), shared locations and contacts, typing indicator, and a **Requests tab** beside it.
* Request list and request detail, with warnings (details read from a voice note, photo or file are flagged).
* Approval with biometrics or device lock. Decline with one tap.
* Hand-off to M-Pesa: dialler, optional M-Pesa app launch, guided steps, copy buttons. Values come from the server, so they can be corrected without a new release.
* **Contact search** across this phone (with permission, or its own chooser without one), Google contacts, Outlook contacts, a vCard or CSV file, or a typed number. Searching happens on the phone. See `companion-app/docs/CONTACTS.md`.
* **M-Pesa message check (Android only):** reads only messages from the sender `MPESA`, only on request, matches on the phone, sends the code, kind, amount and payee, never the text. See `companion-app/docs/SMS.md`.
* Deep links from `https://host/a/ID` and `mpesaagent://a/ID`.
* iPhone: no message reading is possible. Customers send the M-Pesa message in the chat instead.
* **Ten looks, saved on the phone:** Daylight, Graphite, Pwani (coastal teal), Mono, Matatu (street yellow, black and red), Kahawa (espresso brown with teal) and Blueprint (drafting navy with a grid), plus three in M-Pesa colours: Savanna (green header), Lipa (white and red) and Receipt (paper slip, typewriter type). Picked from the palette button in the app bar. The browser console offers the same ten.

## How results are recorded

| Level | Meaning |
|---|---|
| NONE | Nothing reported yet |
| REPORTED | The customer said it was done, maybe with a code |
| SMS_AMOUNT | A message on their phone matched the amount, kind and time. It did not show who received it. |
| SMS_MATCHED | As above, and the recipient number or the Paybill account matched too |

None of these is a Safaricom confirmation. A message can also correct a request wrongly marked "not done", and one M-Pesa
code can back only one request. Request states: CREATED, APPROVED, HANDED_OFF, DONE_REPORTED, NOT_DONE, DECLINED, CANCELLED, EXPIRED.

## Safety and privacy built in
* **No PIN, no keys.** The assistant refuses to take a PIN. The app and servers never see it.
* **The model never moves money.** It only calls prepare tools. The phone number on a request is the signed-in customer's, read from their token.
* **Whitelist validation** per request type: Paybill and Till numbers are 5 to 7 digits, Kenyan numbers are normalised, amounts are limited (airtime minimum KES 5), unknown fields are rejected, a request to your own number is refused.
* **Read-back rule:** details taken from a voice note, photo or file are read back by the assistant and flagged again in the app.
* **Prompt-injection defence:** text inside images, documents, transcripts and forwarded messages is treated as data, never as instructions.
* **Scam warnings** for prizes, loans, jobs, "reverse my money" and "release fee" stories.
* **Isolation:** every request belongs to one phone number. Another customer's link cannot open it.
* **Sign-in codes:** sent by SMS, valid 10 minutes, 5 tries, 3 codes an hour, stored hashed, never shown to the model.
* **Chat isolation:** the chat and its files belong to the number in the token. Nobody can read another number's chat or download its files.
* **Sessions:** signed tokens tied to a device, revocable by signing out.
* **Limits:** max open requests per customer, request expiry (30 minutes before approval), rate limiting per customer, duplicate message handling, per-customer locking so messages are handled in order.
* **Contacts stay on the phone.** The server only sees the one contact the customer chooses to share. Google and Microsoft tokens stay in memory on the phone.
* **Internal calls** between services carry a shared internal key. The sign-in code mode (`CODE_DELIVERY`) defaults to log only.
* **Honest wording:** results are described as "reported by you" or "matches the message on your phone", never "confirmed by Safaricom".

## Behind the scenes

| Service | Port | What it does |
|---|---|---|
| agent-service | 8082 | Claude model with tool use, the chat (messages and files) and conversation memory, role handling, menus, media understanding, contact lookup |
| companion-api | 8084 | Signs customers in by SMS code, relays the chat, stores requests, serves the app, matches message evidence, publishes events, serves app-link files |
| companion-app | n/a | The Flutter app above |
| payment-service | 8083 | **Optional** Daraja module for a business, off by default |

Infrastructure: a database you choose in the yml (PostgreSQL by default, or MySQL, or MongoDB, see `docs/DATABASES.md`) with separate `payments` and `companion` databases, Redis, RabbitMQ. All Java services use the same layered packages: controller, service, repository, model, dto, exception, config.

Other engineering features: Flyway migrations for the SQL databases, idempotency keys, row locking on state changes, events sent only after commit, Docker Compose for everything, per-service `.env.example`, and unit and integration tests for the Java services and Dart tests for the app.

## Optional business payments (Daraja)
Switched on with `docker compose --profile business up` and `AGENT_BUSINESS_PAYMENTS=true`. Off by default.

* **STK Push** to your own Paybill or Till after the customer taps Pay, with the result coming only from Safaricom's callback, a reconciler for lost callbacks, and payment status and history.
* **C2B** direct Paybill and Till payments (validation and confirmation callbacks).
* **Receipts** as PDF, **QR codes** to pay.
* **Refunds**, **payouts** (withdrawals, disbursements) and **reversals** through B2C, all needing a second person to approve.
* **Transaction lookup** and **account balance** for staff and admins.
* **Roles:** CUSTOMER, STAFF, APPROVER, ADMIN, decided from a staff list and the signed-in number. The model only sees the tools its role allows, and payment-service checks again.
* **Limits:** per payment and per day, configurable.

## Try it without a real frontend
* **`postman/`** has a collection and an environment for every endpoint, with tests and scripts that carry values from one call to the next.
* **`console/console.html`** is one HTML file, run with `python3 -m http.server`, that simulates the frontend: a journey view that draws one request as a trace across customer, agent, companion API, phone and M-Pesa, a real chat against the real agent (sign in with a code, attach menu, file pickers, voice notes, camera and video recording, drag and drop, location, contact search across Google, Microsoft, a phone picker and vCard/CSV), a phone app simulator, an explorer for every endpoint, a command palette (Ctrl+K), and the same ten themes as the app, with an upload slot for your own logo. It needs `CORS_ALLOWED_ORIGINS` set on the services, which is off by default. See `console/README.md`.

## Not included
* Moving or holding money. Only the customer can, inside M-Pesa.
* Verified payment status from Safaricom for personal wallets. See `README.md`, "Known gaps".
* Reading M-Pesa messages on iPhone, or in the background on Android.
* "Money received" detection for personal wallets, and recipient-side confirmation.
* A web-only approval page without the app, push notifications (the chat is read by polling while the app is open), an admin dashboard, a support inbox, saved recipients and bill presets, Swahili text in the app screens.
* Any production setup: an SMS sender registration, app store releases, privacy registration and notices, monitoring and backups.
