<!-- PLACE THIS FILE AT: mpesa-ai-agent/postman/README.md
     Replace the existing file if there is one. The SVG images it uses go in mpesa-ai-agent/docs/assets/. You can delete this comment. -->

<div align="center">

<img src="../docs/assets/banner-postman.svg" alt="" width="100%"/>

<br/>

<img alt="Postman" src="https://img.shields.io/badge/Postman-93%20requests-14231C?style=flat-square&logo=postman&logoColor=FF7A45"/> <img alt="Environment" src="https://img.shields.io/badge/Environment-local-14231C?style=flat-square&logo=postman&logoColor=FF7A45"/> <img alt="Auto%20chaining" src="https://img.shields.io/badge/Auto%20chaining-tests-087F36?style=flat-square"/>

<br/><br/>

A collection and an environment for every HTTP endpoint of the services, with a walkthrough you can run.

<sub><a href="../README.md">Back to the project overview</a></sub>

<img src="../docs/assets/divider.svg" alt="" width="100%"/>

</div>

## Files

| File | What it is |
|---|---|
| `mpesa-agent.postman_collection.json` | 93 requests for every HTTP endpoint of the three services |
| `mpesa-agent-local.postman_environment.json` | Addresses, the internal key, test phone numbers and secrets for a local run |

## Use it

1. In Postman choose **Import**, select both files, then pick the environment **mpesa-agent local**.
2. Set `internalKey` to the same value as `INTERNAL_KEY` in your `.env` files, and `callbackSecret` to your own value. Never put real production secrets in a file you share.
3. Start the services with `docker compose up --build`. For the walkthrough, start companion-api with `CODE_DELIVERY=dev` so asking for a sign-in code returns it (`devCode`).
4. **No Daraja account needed for the wallet flow.** Run the folder `2. companion-api / A. Walkthrough` with the collection runner, then try `C2. Chat`.

> [!CAUTION]
> Never use `CODE_DELIVERY=dev` on a server other people can reach. Anyone could sign in as any number.

## The walkthrough

| Step | What it does |
|---|---|
| 1 | Ask for a sign-in code |
| 2 | Sign in a "phone" |
| 3 | Prepare a request |
| 4 and 5 | Read the request back and check its state |
| 6 to 8 | Approve, hand off and report |

## What is where

| Folder | Contents |
|---|---|
| 1. agent-service | Deliver a customer message straight to the agent (text, voice, image, document, contact, location, buttons, menu), and read or clear a customer's chat |
| 2. companion-api | Walkthrough, agent-side calls, app-side calls, the chat (read, send, upload, download), public pages |
| 3. payment-service | Optional Daraja module: payments, refunds and payouts with approvals, QR, staff roles, Daraja callbacks |

## Good to know

- Ids, tokens and codes are saved automatically by test scripts into collection variables, so requests chain from one call to the next.
- The agent needs an `ANTHROPIC_API_KEY` to answer. Without one, messages are accepted but there is no reply.
- Payment and operation requests need Daraja credentials and the optional module running.
- The collection was generated from the code and has not been run against the services.
- Addresses and ports in the environment match the defaults: agent-service 8082, payment-service 8083, companion-api 8084.

<div align="center">
<sub>M-Pesa is a registered mark of Safaricom. This project is not affiliated with or endorsed by Safaricom.</sub>
</div>
