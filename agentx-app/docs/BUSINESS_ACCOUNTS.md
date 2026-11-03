<div align="center">

<img src="../../docs/assets/banner-business.svg" alt="" width="100%"/>

<br/>

<img src="../../docs/assets/badges-business.svg" alt="Secrets encrypted at rest, Paybill and Buy Goods shortcodes, branches under one head, CSV upload up to 500 accounts, money out needs approval." width="880"/>

<br/><br/>

Save a business shortcode and its credentials once, then use it from the chat.

<sub><a href="../README.md">Back to the Daraja Agent-X app</a> &nbsp;|&nbsp; <a href="../../README.md">Project overview</a></sub>

<img src="../../docs/assets/divider.svg" alt="" width="100%"/>

</div>

# Business accounts

For businesses that use Daraja: STK Push, STK Push query, Dynamic QR, C2B, pull transactions, B2C, B2B, Pochi la Biashara, account balance,
transaction status and reversals. The customer saves a shortcode and its credentials once under **Settings, Business accounts**.
The security credential is never typed. agentx-api makes it from the initiator password with Safaricom's public certificate.

## What is saved

| Field | Needed for | Stored |
|---|---|---|
| Environment (sandbox or production) | everything | plain |
| Shortcode type (Paybill or Buy Goods) and shortcode | everything | plain |
| Consumer key and secret | every Daraja call | encrypted, only the last 4 characters of the key are shown |
| STK passkey | STK Push, STK Push query | encrypted |
| Initiator name | B2C, B2B, Pochi, balance, status, reversal | plain |
| Initiator password | the same list | encrypted |

Secrets are encrypted with AES-256-GCM on the server. The key is `AGENTX_VAULT_KEY` (`openssl rand -base64 32`). Each value is bound to its owner and
field, so a copied value does not open under another owner. The phone never gets a secret back, only whether it is saved. Leave a secret box empty
when editing to keep the saved one.

## Head offices, tills, store numbers and child shortcodes

A **head office** holds a shortcode and, once it has gone live on the Daraja portal, its credentials. The credentials are optional on a head office,
so a head office whose stores each went live on their own can be saved with none. Under a head office there are **children**, and a child is one of two things.

**1. A till or branch that shares the head office's shortcode.** It uses the head office's shortcode, environment, type and credentials, and adds only
what is different:

- Buy Goods: the till number (5 to 7 digits). The shortcode is the head office number.
- Paybill: the account reference (letters and numbers, up to 12).

**2. A store number or child shortcode with its own number.** It went live on its own, so it has its own shortcode (5 to 7 digits):

| Head office is | A child shortcode has |
|---|---|
| Buy Goods | its own store number **and the till number linked to it** |
| Paybill | its own shortcode and **no till number** (an account reference is optional) |

STK Push and the other calls use the child's own shortcode as the business shortcode, and for Buy Goods the linked till as the receiving party.

**Which credentials a child shortcode uses.** Each child shortcode chooses:

- **The head office's.** The key and secret, and the initiator, come from the head office. The passkey is never taken from the head office, because a
  passkey belongs to one shortcode, so STK Push needs the child's own passkey.
- **Its own.** Send what the shortcode has: key and secret, passkey, initiator name and password. Anything you leave empty (the key and secret, or the
  initiator) comes from the head office. The account list shows, for each group, whether it is its own, the head office's, or missing.

A till that shares the head office's shortcode always uses the head office's credentials. If it has credentials of its own, give it its own shortcode.

Rules: one head per environment and shortcode, and a child shortcode must be a number no other account has. A child is always in its head office's
environment and has its head office's type. Changing a head's environment or type changes its children, and changing its shortcode changes the children
that share it (a child with its own shortcode keeps it). Removing a head removes its children. Choosing the head office's credentials on a child
removes the credentials it had saved.

## Uploading a CSV file

Settings, Business accounts, Upload CSV. The file is checked first and nothing is saved until the check is clean. Then **Save** sends it again.
If any row has a problem, nothing at all is saved.

- Up to 500 accounts and 1 MB. UTF-8. The first line is the header.
- Columns can be in any order and can be left out. Commas, semicolons or tabs separate them.
- A row for an account that is already saved updates it. An empty `consumer_secret` keeps the saved one.
- Values that start with `PASTE_` are refused, so the example file cannot be uploaded by mistake.

| Column | Head row | Branch row |
|---|---|---|
| `label` | a name | a name |
| `environment` | sandbox or production | empty, unless the head exists in both |
| `shortcode_type` | paybill or buy_goods | empty |
| `shortcode` | 5 to 7 digits | empty to share the head's, or its own number for a store number |
| `till_number` | empty | Buy Goods: 5 to 7 digits (the till linked to a store number) |
| `account_reference` | empty | Paybill: up to 12 letters or digits (optional for a child shortcode) |
| `parent_shortcode` | empty | the head's shortcode |
| `consumer_key`, `consumer_secret` | if it went live | only for a store number with its own |
| `initiator_name`, `initiator_password` | for B2C, B2B, balance | only for a store number with its own |
| `passkey` | for STK Push | only for a store number with its own |

On a branch row, filling any credential column means "its own credentials". The columns it leaves empty come from the head office, except the passkey.

Example:

```
label,environment,shortcode_type,shortcode,till_number,account_reference,parent_shortcode,consumer_key,consumer_secret,initiator_name,initiator_password,passkey
Main shop,sandbox,buy_goods,174379,,,,KEY,SECRET,testapi,PASSWORD,PASSKEY
Till 1,sandbox,buy_goods,,123456,,174379,,,,,
Till 2,sandbox,buy_goods,,654321,,174379,,,,,
School fees,production,paybill,400200,,,,KEY,SECRET,,,
Nairobi branch,production,paybill,,,NAIROBI,400200,,,,,
Mombasa store,,,600111,543210,,174379,STOREKEY,STORESECRET,,,STOREPASSKEY
Kisumu campus,,,400300,,,400200,KEY,SECRET,,,PASSKEY
```

The app can copy or open the same example, and it comes from the server (`GET /api/business-accounts/csv-template`), so the two never disagree.
Treat a filled-in file as a secret. Delete it after uploading.

## Checking

**Check with Safaricom** on an account asks Daraja for an access token with the saved key and secret, and tests that the certificate can lock the
initiator password. It does not move money. A child is checked with its own key and secret when it has them, otherwise through its head office.

## Server setup

1. Set `AGENTX_VAULT_KEY`. Without it the feature stays off and the screens say so.
2. Put Safaricom's public certificates in `agentx-api/src/main/resources/certs/sandbox` and `certs/production`, or point `DARAJA_CERTS_DIR` at a folder with the same two subfolders. See the README in each folder. The certificates are public, not secrets.

## Using the accounts from the chat

Once an account is saved, ask in the chat in your own words. Type `@` to pick a business account or a contact.

- `@Main shop what is the balance`
- `@Main shop STK push 500 to 0712345678, reference INV42`
- `@Counter two make a QR for 300`
- `pull transactions on @Main shop from 1 November to 2 November`
- `check the STK push I just sent`
- `send 5000 from @Main shop to 0700111222` (a payout, needs your approval)

**The @ picker.** Your business accounts are listed first, best match first. The last row, *Choose a contact*, opens the contact finder (this phone,
Google, Microsoft, a vCard or CSV file, or typing a number). An account stays in the message as `@Name`. A contact is added the same way as from the
attach menu, as a shared contact, and its name replaces the `@`. The assistant never sees your address book, only the person you pick.

**What waits for you and what does not.**

| Request | What happens |
|---|---|
| Balance, transaction status, pull transactions, Dynamic QR, STK Push query | Sent at once. Balance and status answer in the chat a few seconds later. |
| STK Push | Sent at once by default. Set `AGENTX_BUSINESS_APPROVE_COLLECTIONS=true` to make it wait for approval too. At most 20 an hour (`AGENTX_BUSINESS_STK_PER_HOUR`). |
| B2C, Pochi la Biashara, B2B, reversal | Never sent until you approve. The chat sends a link. You open it, check the details and approve with fingerprint, face or phone lock. The link works for 30 minutes (`AGENTX_BUSINESS_APPROVAL_TTL`). |

Every request is capped at `AGENTX_BUSINESS_MAX_AMOUNT` (150,000 by default). A request is sent to Safaricom at most once. If Safaricom does not answer
clearly, the request is marked *Not sure* and the chat tells you to check your M-Pesa statement. It is never tried again by itself.

**Where to see them.** Settings, Business accounts, then the history button at the top. A waiting request shows there with an Approve button, so
you do not need the chat link.

**What the assistant can and cannot see.** It sees each account's name, till, shortcode and what it can do. It never sees a key, secret, passkey,
password or security credential. It cannot approve anything. Details that came from a voice note, a photo or a document are marked, and the approval
screen is where you check them.

## Server setup for the chat

1. Safaricom must be able to reach agentx-api to send results. Set `AGENTX_PUBLIC_BASE_URL` to the public HTTPS address. Sandbox callbacks do not work against `localhost`.
2. agentx-api and agent-service need the same `INTERNAL_KEY`. agentx-api puts results into the chat through agent-service (`/internal/notify/{phone}`), and
   agent-service reads accounts and prepares requests through agentx-api (`/internal/business`).
3. On Android, the approval link needs a second path in the manifest next to the existing `/a/` one. See `APPLY.md`.

## Not yet

Linking a personal M-Pesa account is not offered: no public way to do it was found (see `FILES_AND_MY_ONEAPP.md`). B2B and Pull Transactions requests were written from memory of
the Daraja documentation and have not been tried against Safaricom.
