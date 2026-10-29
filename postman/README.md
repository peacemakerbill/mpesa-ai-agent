# Postman collection

Files:
* `mpesa-agent.postman_collection.json`: 93 requests for every HTTP endpoint of the three services.
* `mpesa-agent-local.postman_environment.json`: addresses, the internal key, test phone numbers and secrets for a local run.

## Use it
1. In Postman choose Import and select both files, then pick the environment **mpesa-agent local**.
2. Set `internalKey` to the same value as `INTERNAL_KEY` in your `.env` files, and `callbackSecret` to your own value. Never put real production secrets in a file you share.
3. Start the services (`docker compose up --build`). For the walkthrough, start companion-api with `CODE_DELIVERY=dev`, so asking for a sign-in code returns it
   (`devCode`). Never use that on a server other people can reach: anyone could sign in as any number.
4. **No Daraja account needed for the wallet flow:** run folder `2. companion-api / A. Walkthrough` with the collection runner. Step 1 asks for a sign-in code,
   step 2 signs in a "phone", step 3 prepares a request, steps 6 to 8 approve, hand off and report. Then try `C2. Chat`.

## What is where
| Folder | Contents |
|---|---|
| 1. agent-service | Deliver a customer message straight to the agent (text, voice, image, document, contact, location, buttons, menu), and read or clear a customer's chat |
| 2. companion-api | Walkthrough, agent-side calls, app-side calls, the chat (read, send, upload, download), public pages |
| 3. payment-service | Optional Daraja module: payments, refunds and payouts with approvals, QR, staff roles, Daraja callbacks |

## Good to know
* Ids, tokens and codes are saved automatically by test scripts into collection variables, so requests chain.
* The agent needs an `ANTHROPIC_API_KEY` to answer. Without one, messages are accepted but there is no reply.
* Payment and operation requests need Daraja credentials and the optional module running.
* The collection was generated from the code and has not been run against the services.
