# M-Ganji app (Flutter, Android and iOS)

Chat with M-Ganji, and approve what it prepares. The customer signs in with a code sent by SMS, then talks to the
assistant by text, voice note, photo, document, video, shared contact or shared location. When the assistant prepares a request
this app does three things:

1. Shows the request in plain words with warnings (numbers read from a voice note or a photo are flagged).
2. Asks the phone to confirm it is the owner: fingerprint, face, or the phone lock. The phone's operating system does the check.
3. Hands over to M-Pesa: opens the dialler with the code typed in, or shows copyable steps. **The M-Pesa PIN is typed only inside M-Pesa.**
   This app, the assistant and the servers never see it and never store it.

Afterwards, on Android, the customer can tap **Check my M-Pesa message**. The app finds the matching confirmation SMS on the phone
(only from sender MPESA, only on request) and sends the server the code, amount and payee, never the text. The customer can also tap
"I completed it", with the M-Pesa code if they like. A match is recorded as *matched with the message on the customer's phone*;
otherwise it is *reported by the customer*. Nothing in this system can confirm with Safaricom that money moved. See `docs/SMS.md`.

## Run it

```
./setup.sh ke.co.yourcompany            # creates android/ and ios/, runs flutter pub get
# apply docs/PLATFORM_SETUP.md
flutter test
flutter run --dart-define=API_BASE_URL=https://ganji.example.com
```

On an Android emulator the host machine is `http://10.0.2.2:8084` (the default). Plain `http` needs
`android:usesCleartextTraffic="true"` in a debug manifest and an ATS exception on iOS. Use https for anything real.

## Layout

| Path | Purpose |
|---|---|
| `lib/main.dart` | Start up, deep links (`https://host/a/ID` and `mganji://a/ID`) |
| `lib/app_state.dart` | Signed in or not, hand-off settings, pending link, the contact and chat services |
| `lib/api/api_client.dart` | ganji-api calls: sign-in code, chat, requests. The phone number is sent when asking for a code. After that the server reads it from the token. |
| `lib/chat/` | `ChatController` (reads new messages every 1.5 seconds while the chat is open, sends text, buttons, files, contacts, locations), `ChatDevice` (camera, microphone, files, location, sound) and its real implementation |
| `lib/contacts/` | Contact search: this phone, Google (People API), Microsoft (Graph), vCard and CSV files. Parsers, filter and the finder controller are plain Dart with tests. |
| `lib/models/handoff.dart` | Builds the dial code and the steps. Only digits, `*` and `#` are ever dialled. |
| `lib/models/mpesa_sms.dart`, `sms_match.dart` | Picks apart M-Pesa messages and finds the one for a request (patterns unverified) |
| `lib/models/deep_link.dart` | Accepts only the two link shapes with a valid id |
| `lib/services/` | Secure storage, biometric approval, launcher, SMS reader (Android) |
| `lib/screens/` | Sign in, chat, contact finder, request list, request detail |
| `lib/widgets/chat_bubbles.dart` | Message bubbles: text with bold and links, buttons, menus, photos, voice notes, files, locations, contacts, typing dots |
| `lib/theme/app_theme.dart` | The ten looks (Daylight, Graphite, Pwani, Mono, Matatu, Kahawa, Blueprint, Savanna, Lipa, Receipt): palettes, corner size, heading typeface, and the `ThemeData` built from them |
| `lib/widgets/ui.dart` | Shared pieces: panel, status tag, callout, amount, progress timeline, key and value list, appearance sheet |
| `lib/models/progress.dart` | Which step of the timeline a request is on, and the plain names and number formats |
| `test/` | Hand-off, link parsing, models, API client, chat controller and models, contact parsers, sources and finder |

## Looks

Ten themes, chosen from the palette button in the app bar and saved on the phone. Seven are everyday looks (Daylight, Graphite, Pwani, Mono, Matatu, Kahawa, Blueprint) and three borrow the colours of M-Pesa (Savanna, Lipa, Receipt). No screen hard-codes a colour: everything reads the palette
through `context.tk`, so another look is one more `Palette` and one more `Tokens` case in `lib/theme/app_theme.dart`.
The browser console (`console/console.html`) uses the same ten with the same colours. A test checks that text on every look meets 4.5 to 1 contrast.
Fonts are the phone's own. Mono asks for Georgia and falls back to the system serif, and Receipt asks for Courier and falls back to the system monospace, so no font files or extra packages are needed.
The theme needs Flutter 3.22 or later (Dart 3.4).

## Things to know before relying on it

- **Nothing here was compiled or run.** The sandbox that wrote it has no Flutter SDK. Package versions in `pubspec.yaml` are from memory.
  Expect to fix a few compile errors on the first `flutter analyze`.
- **USSD codes and the M-Pesa app identifiers are not hard-coded.** They come from the server (`/api/config`) and start empty,
  so the app shows guided steps until you set verified values. Confirm them with the M-Pesa team.
- **Contacts** are searched on the phone. The Google and Microsoft sign-ins give the app a short-lived token that stays in memory, and
  nothing from the address book goes to our servers. The only thing sent is a contact the customer chooses to share in the chat.
  Google and Microsoft need your own OAuth client ids, see `docs/CONTACTS.md`.
- **Voice notes** are recorded as AAC in an .m4a file (`audio/mp4`). Check that the speech to text service you use for the assistant accepts that format.
- **Sign-in codes** are sent by SMS only when the server has `CODE_DELIVERY=sms` and an SMS provider. In `dev` mode the server returns the code to the app, which is for testing only.
- **Device lock, not M-Pesa authentication.** The fingerprint or face check protects *our* approval step. M-Pesa still asks for its own PIN.
  Whether the OS used a biometric or the phone's PIN is a best guess, recorded for the audit trail only.
- **Terms**: check Safaricom's terms and any app store rules about a third-party app that hands over to M-Pesa before you publish.
