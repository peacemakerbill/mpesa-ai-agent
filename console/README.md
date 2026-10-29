# API console

One HTML file that acts like a frontend for the whole system. No build step, no packages, no internet needed.

## Run it

```bash
cd console
python3 -m http.server 8000
```

Open http://localhost:8000/console.html. Use `localhost`, not an IP address: browsers only allow the microphone, camera and location on `localhost` or HTTPS.

## Let the page call your services

The browser blocks one origin calling another unless the service allows it. Each Spring service has an opt-in setting. Start every service you want to try with:

```
CORS_ALLOWED_ORIGINS=http://localhost:8000
```

Leave it empty in production. A wildcard `*` is ignored on purpose. The status lights at the top turn green when a service is reachable, and amber when it is running but this page is not allowed to call it.

Open Settings (the sliders icon, top right) to set the service addresses, the internal key, the callback secret, the customer phone numbers and the optional contact search client ids. They are kept in this browser only.

## What is on each tab

| Tab | What it does |
|---|---|
| Journey | Runs a whole request from chat to message check and draws it as a trace across five parties: customer, agent, companion API, phone app and M-Pesa. Solid lines are real calls, dashed lines are steps the console can only pretend (a fingerprint, the PIN, the M-Pesa message). Press any solid line to read its exact request and answer. You can make the M-Pesa message show a different number or account to see it refused. It needs no AI, because it uses the agent-side endpoints of the companion API. |
| Chat | The real chat with the real agent. Sign in with a phone number and the 6 digit code (companion-api must run with `CODE_DELIVERY=dev` for the console to receive the code, otherwise read it from the companion-api log or your SMS). Then talk to the assistant: the plus button opens the attach menu with photo (file picker), camera, video (file picker) or record a video, audio file, document, contact and location. The microphone button records a voice note (timer, live level, cancel or send). You can also drag a file onto the chat or paste one. Buttons and menus the assistant sends can be tapped, and **Find in contacts** opens the contact finder. The chat is read from the companion API every 1.5 seconds. |
| Phone | The companion app on a phone: sign in with a code, list, approve with a fingerprint, open M-Pesa, report, and check an M-Pesa message. Messages from M-Pesa are simulated and kept in this browser. |
| Explorer | Every endpoint (78 of them) with an editable path, query and body, a send button, and the answer as body, headers, request and curl. Saved values such as the request id and the phone token are filled in for you. |

The dock at the bottom lists every call made, with its full request and answer. Press Ctrl+K (or Cmd+K) for the command palette: jump to a view, run the journey, switch theme or open any of the 78 endpoints by name.

Ten themes, picked from the menu at the top right and shared with the Flutter app. Everyday: Daylight (light, cobalt), Graphite (dark, amber), Pwani (coastal teal), Mono (black and white, serif headings), Matatu (street yellow, black rules, red), Kahawa (espresso brown, teal) and Blueprint (drafting navy with a grid). In M-Pesa colours: Savanna (bright green header, red marks), Lipa (white with a red lead) and Receipt (a paper slip in typewriter type).

### Logo

The M-Pesa logo is a registered mark and is not included. To brand the console, open Settings, choose your own logo under "Your logo" and it appears in the header and as the assistant's picture in Chat. It is resized and kept in this browser only.

### Contact search

The finder in Chat (attach, Contact, or the assistant's **Find in contacts** button) and in the Phone tab searches:

* **Google contacts**, including people you have emailed. Needs a Google OAuth **Web** client id in Settings. In Google Cloud, enable People API, add the page's origin (for example `http://localhost:8000`) as an authorised JavaScript origin, and add the scopes `contacts.readonly` and `contacts.other.readonly`.
* **Outlook and Microsoft 365 contacts.** Needs an Azure app registration with a **single-page application** redirect URI equal to this page's address, and the delegated permission `Contacts.Read`. Paste its Application (client) ID in Settings. The sign-in opens in a popup and uses PKCE, so no secret is stored.
* **This device's contacts**, with the browser's Contact Picker. Only Chrome on Android offers it; elsewhere the button explains that.
* **A .vcf or .csv file** exported from any address book. Nothing to set up.
* **Typing the number.**

Contacts are searched in the browser. Only the contact you choose is sent, and only as a chat message. Tokens stay in memory. See `companion-app/docs/CONTACTS.md`.

### Files and voice notes in Chat

Files you attach are uploaded to the companion API (`POST /api/chat/media`) and then named in the message you send, exactly as the app does.
Chrome and Edge record voice notes as WebM and Safari as MP4. Whether your transcription step accepts WebM or MP4 is worth checking.

## Be careful

* This page holds the internal key and secrets in the browser. Use it on your own machine with test values, never with production keys.
* Do not turn on `CORS_ALLOWED_ORIGINS` or `CODE_DELIVERY=dev` on a server that other people can reach. With `dev`, anyone can sign in as any phone number.
* A matched message here is the simulator matching a message it made itself. It shows how the checks behave, not that money moved.
* Calls to the payments service and the Daraja callbacks only work with that service running and, for real payments, with Daraja credentials.
