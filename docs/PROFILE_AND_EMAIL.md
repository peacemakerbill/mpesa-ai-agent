<!-- PLACE THIS FILE AT: mpesa-ai-agent/docs/PROFILE_AND_EMAIL.md
     The images it uses are in mpesa-ai-agent/docs/assets/. You can delete this comment. -->

<div align="center">

<img src="assets/banner-profile.svg" alt="" width="100%"/>

<br/>

<img src="assets/badges-profile.svg" alt="Sign up with an SMS code, email verified with its own code, profile picture on local or network storage, reports as PDF, CSV or Excel, mail through a log or SMTP transport, the owner is told when a report goes to others." width="880"/>

<br/><br/>

Customers can create an account, keep a profile, and have reports and alerts sent to their email.

<sub><a href="../README.md">Back to the project overview</a> &nbsp;|&nbsp; <a href="../APPLY.md">Apply this update</a> &nbsp;|&nbsp; <a href="../agent-service/HOOKS4.md">Agent edits</a></sub>

<img src="assets/divider.svg" alt="" width="100%"/>

</div>

# Profile and email

This update adds five things.

1. **Sign up.** A new customer gives a phone number and a name. The phone gets the same 6 digit SMS code that sign in uses. Once the code is right the account exists and the app is signed in.
2. **A profile.** First, middle and last name, email, address, city, county, country, a saved location and a profile picture. Everything can be changed later in the app, in the console, over the API, or by asking the assistant.
3. **Email verification.** An email address only counts once the customer has typed the code that was sent to it.
4. **Automatic emails.** A welcome email, a notice when the account is signed in on a device, and a notice when a payment or business request finishes.
5. **Reports by email.** Say it in the chat: "send this report to my email as a PDF", "email this as Excel to finance@acme.co.ke", or "send this to everyone on my list as CSV". The assistant picks the endpoints, builds the file and sends it.

The profile picture is kept wherever the backend is configured to keep files: a folder on the server, or a shared network folder. One setting switches between the two.

## Sign up

Sign up reuses the phone code. Nothing new has to be learned by the customer, and there is no password.

| Step | Request | What happens |
|---|---|---|
| 1 | `POST /api/auth/signup/request` | The details are saved as a pending profile and a 6 digit code goes to the phone by SMS. Phone, first name and last name are required. Middle name and email are optional. Nothing about the person is trusted yet. |
| 2 | `POST /api/auth/signup/verify` | The code is checked. The profile becomes active and the answer is the same token as signing in, for this device. The app then starts as if the customer had just signed in. |

Good to know:

- A phone number that already has an account cannot sign up again. It signs in.
- Both calls are rate limited per address and per phone number.
- The pending profile expires after `agentx.profile.signup-ttl` (15 minutes). A new request replaces it.
- `agentx.profile.signup-enabled: false` closes sign up. People with an account still sign in.
- If an email was given at sign up, a second code is emailed to it right after the phone code is accepted. It is used only once the customer types that code (see below). If that email cannot be sent, the account is still created and the app offers "Verify your email" again from the profile.
- People who already had an account before this update get an empty profile the first time they open Profile. Nothing is lost and nobody has to sign up again.

## Verify an email address

The email is verified with a separate 6 digit code that goes to the email itself. That proves the customer can read it.

| Step | Request | What happens |
|---|---|---|
| 1 | `POST /api/profile/email/request-code` with `{ "email": "..." }` | A code is emailed. The address is held as the pending email. The verified address, if any, stays in use. |
| 2 | `POST /api/profile/email/verify` with `{ "code": "123456" }` | The code is checked. The pending address becomes the verified email. |
| | `DELETE /api/profile/email` | Removes the email from the profile. |

The code works once, expires after `agentx.mail.code-ttl` (10 minutes), allows `code-max-attempts` wrong tries (5) and no more than `codes-per-hour` new codes (5). The first time an email is verified, a welcome email goes to it.

Where the code goes: in the app and the console the customer types it. In the chat the assistant has `request_email_verification` and `confirm_email_code`. The code is emailed, and the assistant never sees it, so it cannot be repeated back or guessed on the customer's behalf.

## The profile

`GET /api/profile` returns the profile. `PUT /api/profile` changes it. A field that is left out stays as it is.

| Field | Notes |
|---|---|
| `firstName`, `lastName` | Required. They cannot be cleared. |
| `middleName` | Optional. An empty text clears it. |
| `address`, `city`, `county`, `country` | Optional. An empty text clears the field. |
| `latitude`, `longitude` | The saved location. `clearLocation: true` removes it. In the app it comes from the phone's location, with the customer's permission. |
| `notifySignIn`, `notifyPayments` | Each customer can switch the sign in and payment emails off. |
| email | Changed only with the code check above, never through `PUT /api/profile`. |
| phone | Not editable. It is the account. |

### Profile picture

| Request | What it does |
|---|---|
| `PUT /api/profile/picture` | The picture is the request body, sent as `image/png` or `image/jpeg`. Up to `agentx.storage.max-picture-bytes` (3 MB). |
| `GET /api/profile/picture` | Returns the picture. Only the signed-in customer can read their own. |
| `DELETE /api/profile/picture` | Removes it. |

The server does not store the upload as it came. It reads the picture, checks that it really is a PNG or JPEG, shrinks it to at most 512 pixels on the longest side, and writes a fresh image. That removes EXIF data such as the place a photo was taken, and rejects files that only pretend to be pictures.

## Where files are kept: local or network

One setting chooses the storage. Pictures are the first thing to use it.

```yaml
agentx:
  storage:
    type: local            # local or network
    local:
      path: ./data/files
    network:
      path: /mnt/agentx    # a shared folder already mounted on this server
      create-if-missing: false
      subfolder: agentx-api
    max-picture-bytes: 3145728
```

| `type` | Where the files go | Use it for |
|---|---|---|
| `local` | The folder in `local.path` on this server. | One server, development, a mounted disk. |
| `network` | A shared folder in `network.path` that is mounted on this server: NFS, SMB or CIFS, a NAS. Files go in `subfolder` so several services can share one share. | Several servers, or pictures that must outlive a redeploy. |

With `network`, the share must already be mounted. If the folder is missing the service **refuses to start** rather than quietly writing to its own disk. Set `create-if-missing: true` only if you want the folder created.

The environment variables are `STORAGE_TYPE`, `STORAGE_LOCAL_PATH`, `STORAGE_NETWORK_PATH`, `STORAGE_NETWORK_CREATE`, `STORAGE_NETWORK_SUBFOLDER` and `STORAGE_MAX_PICTURE_BYTES`.

`network` here means a mounted shared folder. It is not an object store. The chat files in `agent-service` have their own setting, `chat.media.store`, with an S3 option (see `agent-service/STORAGE.md`). Pictures do not use S3 in this update.

## Email settings

Email goes out through one place, the mail outbox. It records every message, sent or not, in a table the customer can see in Profile under emails sent.

```yaml
agentx:
  mail:
    transport: log         # log or smtp
    from: no-reply@example.com
    from-name: Daraja Agent-X
    reply-to:
    smtp:
      host: smtp.example.com
      port: 587
      username: no-reply@example.com
      password: change-me
      start-tls: true
      ssl: false
    notifications:
      welcome: true
      sign-in: true
      payments: true
    reports:
      max-recipients: 10
      per-hour: 10
      max-rows: 5000
      max-attachment-bytes: 8388608
      allow-external: true
      allowed-domains:
```

| Setting | Env variable | Default | Meaning |
|---|---|---|---|
| `transport` | `MAIL_TRANSPORT` | `log` | `log` writes each email to the service log and sends nothing, so a developer can see the words and the code. `smtp` sends through the server below. |
| `from`, `from-name` | `MAIL_FROM`, `MAIL_FROM_NAME` | `no-reply@localhost`, `Daraja Agent-X` | Most mail servers only accept a sender address they own, usually the same as the username. |
| `reply-to` | `MAIL_REPLY_TO` | blank | Only a fallback for the system's own emails. Replies are decided per customer: a report a customer emails to other people carries that customer's verified profile email as its reply address, so an answer goes to them and not to the sender address. |
| `smtp.host`, `port` | `MAIL_HOST`, `MAIL_PORT` | blank, `587` | Use 587 with `start-tls: true`, or 465 with `ssl: true`. |
| `smtp.username`, `password` | `MAIL_USERNAME`, `MAIL_PASSWORD` | blank | Keep the password in `.env`, never in a committed file. Quotes around it are dropped. For Gmail, or a password written as four groups of four letters, spaces are dropped too, so a Gmail app password can be pasted exactly as Google shows it. A warning in the log says when a Gmail password does not have 16 letters. |
| `smtp.start-tls`, `ssl` | `MAIL_STARTTLS`, `MAIL_SSL` | `true`, `false` | |
| `smtp.connect-timeout`, `timeout` | none | 10 s, 30 s | |
| `notifications.welcome`, `sign-in`, `payments` | `MAIL_NOTIFY_WELCOME`, `MAIL_NOTIFY_SIGN_IN`, `MAIL_NOTIFY_PAYMENTS` | all `true` | Which automatic emails exist at all. Customers can also turn off sign in and payment notices for themselves. |
| `reports.max-recipients` | `MAIL_REPORT_MAX_RECIPIENTS` | 10 | Most addresses on one report. |
| `reports.per-hour` | `MAIL_REPORT_PER_HOUR` | 10 | Most reports one customer can send in an hour. |
| `reports.max-rows` | `MAIL_REPORT_MAX_ROWS` | 5000 | Rows in one report. |
| `reports.max-attachment-bytes` | `MAIL_REPORT_MAX_BYTES` | 8 MB | Size of the file. A bigger report is refused with a message to narrow it. |
| `reports.allow-external` | `MAIL_REPORT_ALLOW_EXTERNAL` | `true` | `false` means a report can only go to the customer's own verified email or to addresses on their saved list. |
| `reports.allowed-domains` | `MAIL_REPORT_ALLOWED_DOMAINS` | blank | Comma separated. When set, reports can only go to these domains. The customer's own verified email always works. |
| `code-ttl`, `code-max-attempts`, `codes-per-hour` | none | 10 min, 5, 5 | Email verification codes. |
| `sign-in-alert-gap` | `MAIL_SIGN_IN_ALERT_GAP` | 30 min | The least time between two "new sign in" emails to the same customer. |

Start with `transport: log`. Everything works and nothing leaves the server. Switch to `smtp` when the host, username and password are in `.env`.

### The automatic emails

| When | Who gets it | Notes |
|---|---|---|
| First time an email is verified | That address | A welcome. |
| A device signs in | The verified email | Says which device and when (Nairobi time). Sent at most once per `sign-in-alert-gap`, and only if the customer has not switched it off. |
| A payment or a business request finishes, or fails | The verified email | Title, summary, result and the M-Pesa code when there is one. Off for customers who switched it off. |
| A report is sent to anyone other than the owner | The owner's verified email | A short notice listing who got it, so a customer notices if someone else asked on their behalf. |
| An email address needs verifying | That address | The 6 digit code. |

Sign in emails need one small edit to `AuthController` in your repository, which `agentx-api/tools/apply_profile_hooks.py` makes for you (see APPLY.md). Payment emails cover payments made from the app (the same event that puts the news in the chat) and business requests made from the chat. Payments made inside `payment-service` itself are not part of this update.

## Reports by email

A report is a table turned into a file and attached to an email. Three file types: **PDF**, **CSV** and **Excel (xlsx)**. People can say "excel", "spreadsheet", "xls" and so on. With no format named the report is a PDF.

### Where the rows come from

| `source` | Rows |
|---|---|
| `BUSINESS_REQUESTS` | The customer's business requests, with optional `status`, `type`, `account`, `from`, `until` and `limit` filters. |
| `BUSINESS_ACCOUNTS` | The saved business accounts. Secrets are never included. |
| `PAYMENTS` | The customer's recorded payment requests. |
| `TABLE` | Rows the caller supplies in `columns` and `rows`. This is what lets the customer say "send **this** report", meaning the table the assistant just showed. |

A `TABLE` is limited to 2000 rows, 30 columns and 300 characters per cell when it comes from the assistant. The server limit `reports.max-rows` applies on top.

### Who it goes to

`to` holds the addresses. Besides real addresses it understands:

- the word `me`, meaning the customer's own verified profile email
- names of people on the customer's saved list

`toMe: true` adds the customer's own email. `toSaved: true` adds everyone on the saved list. If the caller names nobody, the report goes to the customer's own email.

If the customer has no verified email and asks for "my email", the assistant says so and offers to verify one. It does not guess.

### The saved list

Customers can keep a list of addresses to send reports to (up to `agentx.profile.max-recipients`, 200). They can:

- add one address with a name
- paste many, separated by commas, spaces, semicolons or new lines
- upload a `.csv` or `.txt` file of addresses (an `email` column and an optional `name` column, or one address per line)
- remove one

The answer to a paste or an upload says how many were added, how many were skipped as duplicates and which were not valid addresses. Upload takes CSV and text files only. An Excel list is not read: save it as CSV first.

### Asking in the chat

The assistant has these tools: `get_my_profile`, `update_my_profile`, `request_email_verification`, `confirm_email_code`, `list_email_recipients`, `save_email_recipients`, `remove_email_recipient` and `send_report_email`. The customer's phone always comes from the signed in session, never from the model.

| The customer says | What the assistant does |
|---|---|
| "Send this report to my email as a PDF." | Takes the table it just showed, calls `send_report_email` with `format: pdf` and nothing else. The report goes to the verified profile email. |
| "Email my pending payouts for this month as Excel." | Uses the `BUSINESS_REQUESTS` source with the status and dates, sends xlsx to the profile email. |
| "Send my business accounts as CSV to a@x.com and b@y.com." | Uses `BUSINESS_ACCOUNTS` with `to` set to both addresses. The owner is told it went to others. |
| "Here are the emails to send reports to" with a pasted list or an attached file | `save_email_recipients`, then reports "to everyone on my list" use `toSaved`. |
| "Change my first name to Wanjiru and my city to Nakuru." | `update_my_profile`. |
| "Verify my email wanjiru@example.com." | `request_email_verification`, then the customer reads the code from the email and says it, and the assistant calls `confirm_email_code`. |

The assistant tells the customer what it did, and only says "sent" when the server says the email was delivered. If the server answers that nothing was delivered, the tool returns an error and the assistant says so.

The tools are wired in with the edits in `agent-service/HOOKS4.md`. The new classes are `ProfileTools`, `ProfileApi` and `AgentXProfileClient`, in the same style as the business account tools.

### Over the API

`POST /api/email/report` takes the same request, which is how the app, the console and the assistant all share one path:

```json
{
  "source": "BUSINESS_REQUESTS",
  "title": "Payouts in October",
  "format": "xlsx",
  "toMe": true,
  "to": ["finance@acme.co.ke"],
  "message": "October payouts, as asked.",
  "status": "COMPLETED",
  "from": "2026-10-01",
  "until": "2026-10-31"
}
```

The answer lists who it was delivered to, the file name, the number of rows and `delivered`. `GET /api/email/log` lists emails sent for the customer, with status SENT, FAILED or LOGGED (when `transport` is `log`).

### Safety on report emails

- Everything runs as the signed in customer. A customer can only report on their own data.
- Business secrets (consumer secrets, initiator passwords, passkeys) are never in a report.
- Cells that begin with `=`, `+`, `-` or `@` are written so that Excel does not run them as formulas.
- Reports are limited by count per hour, recipients, rows and size, and by `allow-external` and `allowed-domains` if you set them.
- When a report goes to someone other than the owner, the owner gets a notice listing the addresses.

## All the endpoints

Customer calls use the `Authorization: Bearer` token. Internal calls (from `agent-service`) use `X-Internal-Key` and always carry the phone.

| Method and path | Purpose |
|---|---|
| `POST /api/auth/signup/request` | Start sign up, send the SMS code |
| `POST /api/auth/signup/verify` | Finish sign up, returns the token |
| `GET /api/profile` | Read the profile |
| `PUT /api/profile` | Change names, address, location, notice switches |
| `POST /api/profile/email/request-code` | Email a verification code |
| `POST /api/profile/email/verify` | Confirm the code |
| `DELETE /api/profile/email` | Remove the email |
| `PUT /api/profile/picture` | Upload the picture |
| `GET /api/profile/picture` | Read the picture |
| `DELETE /api/profile/picture` | Remove the picture |
| `GET /api/email/recipients` | The saved list |
| `POST /api/email/recipients` | Add one |
| `DELETE /api/email/recipients/{id}` | Remove one |
| `POST /api/email/recipients/paste` | Add many from pasted text |
| `POST /api/email/recipients/import` | Add many from a CSV or text file |
| `POST /api/email/report` | Email a report |
| `GET /api/email/log` | Emails sent for this customer |
| `GET /internal/profile`, `PUT /internal/profile` | Profile for the assistant |
| `POST /internal/profile/email/request-code`, `POST /internal/profile/email/verify` | Email check for the assistant |
| `GET`, `POST`, `DELETE /internal/email/recipients`, `POST /internal/email/recipients/paste` | The saved list for the assistant |
| `POST /internal/email/report` | Report for the assistant |

The Postman collection has them as folders **C5. Sign up and profile**, **C6. Email addresses and reports** and **B2. Agent side: profile and email**. The console has a **Profile** tab that does all of it in a browser.

## Where it lives

| Part | New files |
|---|---|
| `agentx-api` | `SignupController`, `ProfileController`, `EmailController`, `InternalProfileController`, services for sign up, profile, email codes, recipients and reports, the `mail` package (outbox, log and SMTP transports, templates), the `report` package (CSV, XLSX and PDF writers), the file store for pictures, `V5__profiles_and_email` for PostgreSQL and MySQL, and the MongoDB collections for the same data |
| `agent-service` | `ProfileTools`, `ProfileApi`, `AgentXProfileClient`, `HOOKS4.md` |
| `agentx-app` | sign up screen, profile screen, email addresses screen, profile picture widget, the profile model, and the matching `ApiClient` calls |
| `console`, `postman` | the Profile tab and the three Postman folders |

The database change is Flyway migration **V5**. On MongoDB the collections are created when first used. Nothing in an existing table is changed.

## What was and was not checked

Nothing in this update was built with Maven or Flutter, because neither was available where it was written. What was checked:

- the Java code compiles against small stand-ins for Spring and Jackson, and 85 + 38 tests of the backend logic and 19 tests of the assistant tools pass in that setup
- the Dart code was checked for syntax, for imports that exist and for names that exist, and its tests were written but not run
- the console was driven in a headless browser against a stand-in server
- the Postman collection is valid JSON with the expected folders

Please run these on your own machine before relying on it:

- `mvn test` in `agentx-api` and `agent-service`, and `flutter test` in `agentx-app`
- sending a real email with `transport: smtp` to a mailbox you own
- sign up and sign in on a phone

## Things to know

- **No idempotency on report emails.** If the model repeats a tool call, a report can be sent twice. Reports are capped per hour, and the owner is notified, but a repeat is possible.
- **The PDF uses a built in font.** It handles Latin text. Names with characters outside Latin-1 may print as a placeholder, so use CSV or Excel for those.
- **Network storage is a mounted folder.** Pictures do not go to S3 in this update.
- **Attached files in the chat.** The assistant can only read a file a customer attaches if your agent-service passes its text to the model. Pasted addresses always work.
- **The sign in edit.** `AuthController` needs the one line from `apply_profile_hooks.py`. If the script cannot find its place in your version of the file it prints the exact lines to add.
- **Payment emails from `payment-service`.** Not included. Emails cover payments made from the app and business requests made from the chat, which `agentx-api` records.
