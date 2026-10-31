<!-- PLACE THIS FILE AT: mpesa-ai-agent/companion-app/README.md
     Replace the existing file if there is one. The SVG images it uses go in mpesa-ai-agent/docs/assets/. You can delete this comment. -->

<div align="center">

<img src="../docs/assets/banner-companion-app.svg" alt="" width="100%"/>

<br/>

<img alt="Flutter" src="https://img.shields.io/badge/Flutter-3.22+-14231C?style=flat-square&logo=flutter&logoColor=4FD1C5"/> <img alt="Dart" src="https://img.shields.io/badge/Dart-3.4+-14231C?style=flat-square&logo=dart&logoColor=6EA8FF"/> <img alt="Android" src="https://img.shields.io/badge/Android-supported-14231C?style=flat-square&logo=android&logoColor=2BD576"/> <img alt="iOS" src="https://img.shields.io/badge/iOS-supported-14231C?style=flat-square&logo=apple&logoColor=E8F4EC"/> <img alt="Looks" src="https://img.shields.io/badge/Looks-10-087F36?style=flat-square"/>

<br/><br/>

The customer's side. Chat with the assistant, approve with the phone's own lock, and hand over to M-Pesa.

<sub><a href="../README.md">Back to the project overview</a></sub>

<img src="../docs/assets/divider.svg" alt="" width="100%"/>

</div>

## What it does

<div align="center">
<img src="../docs/assets/flow.svg" alt="Request flow" width="100%"/>
</div>

<br/>

The customer signs in with a code sent by SMS, then talks to the assistant by text, voice note, photo, document, video, shared contact or shared location. When the assistant prepares a request, this app does three things:

1. Shows the request in plain words with warnings. Numbers read from a voice note or a photo are flagged.
2. Asks the phone to confirm the owner: fingerprint, face, or the phone lock. The operating system does the check.
3. Hands over to M-Pesa: opens the dialler with the code typed in, or shows copyable steps. **The M-Pesa PIN is typed only inside M-Pesa.** This app, the assistant and the servers never see it or store it.

On Android the customer can then tap **Check my M-Pesa message**. The app finds the matching confirmation SMS (only from sender `MPESA`, only on request) and sends the server the code, amount and payee, never the text. The customer can also tap "I completed it", with the M-Pesa code if they like. A match is recorded as *matched with the message on the customer's phone*, otherwise as *reported by the customer*. Nothing here can confirm with Safaricom that money moved. See [`docs/SMS.md`](docs/SMS.md).

## Run it

```bash
./setup.sh ke.co.yourcompany            # creates android/ and ios/, runs flutter pub get
# apply docs/PLATFORM_SETUP.md
flutter test
flutter run --dart-define=API_BASE_URL=https://companion.example.com
```

On an Android emulator the host machine is `http://10.0.2.2:8084`, which is the default. Plain `http` needs `android:usesCleartextTraffic="true"` in a debug manifest and an ATS exception on iOS. Use https for anything real.

## Ten looks

Choose from the palette button in the app bar. The choice is saved on the phone. The browser console uses the same ten with the same colours.

<div align="center">
<img src="../docs/assets/themes.svg" alt="The ten looks" width="100%"/>
</div>

<br/>

No screen hard-codes a colour. Everything reads the palette through `context.tk`, so another look is one more `Palette` and one more `Tokens` case in `lib/theme/app_theme.dart`. A test checks that text on every look meets 4.5 to 1 contrast. Fonts are the phone's own: Mono asks for Georgia and falls back to the system serif, Receipt asks for Courier and falls back to the system monospace, so no font files or extra packages are needed. The theme needs Flutter 3.22 or later (Dart 3.4).

## Layout

| Path | Purpose |
|---|---|
| `lib/main.dart` | Start up and deep links (`https://host/a/ID` and `mpesaagent://a/ID`) |
| `lib/app_state.dart` | Signed in or not, hand-off settings, pending link, the contact and chat services |
| `lib/api/api_client.dart` | companion-api calls. The phone number is sent when asking for a code, after that the server reads it from the token |
| `lib/chat/` | `ChatController` (reads new messages every 1.5 seconds while open, sends text, buttons, files, contacts, locations) and `ChatDevice` (camera, microphone, files, location, sound) |
| `lib/contacts/` | Contact search: this phone, Google (People API), Microsoft (Graph), vCard and CSV files |
| `lib/models/handoff.dart` | Builds the dial code and the steps. Only digits, `*` and `#` are ever dialled |
| `lib/models/mpesa_sms.dart`, `sms_match.dart` | Picks apart M-Pesa messages and finds the one for a request (patterns unverified) |
| `lib/models/deep_link.dart` | Accepts only the two link shapes with a valid id |
| `lib/services/` | Secure storage, biometric approval, launcher, SMS reader (Android) |
| `lib/screens/` | Sign in, chat, contact finder, request list, request detail |
| `lib/widgets/` | Chat bubbles and shared pieces: panel, status tag, callout, amount, progress timeline, appearance sheet |
| `lib/theme/app_theme.dart` | The ten looks and the `ThemeData` built from them |
| `test/` | Hand-off, link parsing, models, API client, chat, contact parsers, sources and finder |

## Platform differences

| | Android | iOS |
|---|---|---|
| Dialler with the code filled in | Customer presses call | Same, with a system confirmation |
| Open the M-Pesa app | Possible by package name (not verified) | Only with a URL scheme (not verified) |
| Matching the M-Pesa SMS | On request, with permission | Not possible. Send the message in the chat |
| Approval | Fingerprint, face or phone lock | Face ID, Touch ID or passcode |

## Things to know before relying on it

> [!WARNING]
> The app was written without a Flutter SDK in the writing environment. Package versions in `pubspec.yaml` are from memory. Expect a few compile errors on the first `flutter analyze`.

- **USSD codes and M-Pesa app identifiers are not hard-coded.** They come from the server (`/api/config`) and start empty, so the app shows guided steps until you set verified values.
- **Contacts** are searched on the phone. The Google and Microsoft sign-ins give the app a short-lived token that stays in memory, and nothing from the address book goes to your servers. Only a contact the customer chooses to share is sent, in the chat. You need your own OAuth client ids, see [`docs/CONTACTS.md`](docs/CONTACTS.md).
- **Voice notes** are recorded as AAC in an `.m4a` file (`audio/mp4`). Check that your speech to text service accepts it.
- **Sign-in codes** arrive by SMS only when the server has `CODE_DELIVERY=sms`. In `dev` mode the server returns the code to the app, for testing only.
- **Device lock is not M-Pesa authentication.** It protects our approval step. M-Pesa still asks for its own PIN.
- **Terms.** Check Safaricom's terms and any app store rules about a third party app that hands over to M-Pesa before you publish.

## More reading

| Document | What is in it |
|---|---|
| [`docs/PLATFORM_SETUP.md`](docs/PLATFORM_SETUP.md) | Android and iOS settings, signing, app links |
| [`docs/CONTACTS.md`](docs/CONTACTS.md) | Contact search and the Google and Microsoft sign-ins |
| [`docs/SMS.md`](docs/SMS.md) | The M-Pesa message check |

<div align="center">
<sub>M-Pesa is a registered mark of Safaricom. This project is not affiliated with or endorsed by Safaricom.</sub>
</div>
