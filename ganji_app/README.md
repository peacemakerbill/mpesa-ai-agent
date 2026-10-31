<!-- PLACE THIS FILE AT: mpesa-ai-agent/ganji_app/README.md
     Replace the existing file. The SVG images it uses go in mpesa-ai-agent/docs/assets/. You can delete this comment. -->

<div align="center">

<img src="../docs/assets/banner-ganji-app.svg" alt="" width="100%"/>

<br/>

<img src="../docs/assets/badges-ganji-app.svg" alt="Flutter 3.22 or later, Dart 3.4 or later, Android, iOS and macOS, Linux and Windows desktop, ten looks." width="880"/>

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

## Platforms

The project is one Flutter codebase for five platforms. The phone is the real target, because the hand-off to M-Pesa uses the phone's dialler and the M-Pesa app. Desktop builds are for testing the chat and the screens.

| Platform | Build it on | Status |
|---|---|---|
| Android | Linux, macOS or Windows | The main target |
| iOS | A Mac with Xcode | Supported, needs an Apple developer account for a real phone |
| Linux | Linux | For testing. Approval, SMS and the dialler do not work |
| macOS | A Mac | For testing. Same limits as Linux |
| Windows | Windows with Visual Studio | For testing. Same limits as Linux |

Web is not supported, because the code uses `dart:io`.

## Create the project and run it

The platform folders (`android/`, `ios/`, `linux/`, `macos/`, `windows/`) are made by Flutter and are committed with the project. If a checkout does not have them, create them once:

1. In Android Studio choose File, New, New Flutter Project. Name it `ganji_app`, organization `ke.co.ganjiai`, Kotlin and Swift, and tick Android, iOS, Linux, macOS and Windows. (From a terminal, `./setup.sh ke.co.ganjiai` does the same.)
2. Keep `lib/`, `test/`, `docs/`, `pubspec.yaml` and `analysis_options.yaml` from this repository, and delete the `test/widget_test.dart` that Flutter adds.
3. Apply the settings for each platform from [`docs/PLATFORM_SETUP.md`](docs/PLATFORM_SETUP.md).
4. Run:

```bash
flutter pub get
flutter analyze && flutter test
flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8084
```

Pick the right `API_BASE_URL` for the device:

| Device | `API_BASE_URL` |
|---|---|
| Android emulator | `http://10.0.2.2:8084` (the default) |
| Linux, macOS or Windows | `http://localhost:8084` |
| Real phone on the same Wi-Fi | `http://<your computer's IP>:8084` |
| Release build | `https://<your public host>` |

Plain `http` needs `android:usesCleartextTraffic="true"` in the debug manifest and an App Transport Security exception on iOS. Use https for anything real. Microsoft contact sign-in also needs `--dart-define=MS_CLIENT_ID=<id>`, see [`docs/CONTACTS.md`](docs/CONTACTS.md).

On Linux, install the build packages first:

```bash
sudo apt install clang cmake ninja-build pkg-config libgtk-3-dev liblzma-dev libsecret-1-dev libjsoncpp-dev \
  libgstreamer1.0-dev libgstreamer-plugins-base1.0-dev gstreamer1.0-plugins-good gstreamer1.0-libav
```

## Ten looks

Choose from the palette button in the app bar. The choice is saved on the device. The browser console uses the same ten with the same colours.

<div align="center">
<img src="../docs/assets/themes.svg" alt="The ten looks" width="100%"/>
</div>

<br/>

No screen hard-codes a colour. Everything reads the palette through `context.tk`, so another look is one more `Palette` and one more `Tokens` case in `lib/theme/app_theme.dart`. A test checks that text on every look meets 4.5 to 1 contrast. Fonts are the device's own: Mono asks for Georgia and falls back to the system serif, Receipt asks for Courier and falls back to the system monospace, so no font files or extra packages are needed. The theme needs Flutter 3.22 or later (Dart 3.4).

## Layout

| Path | Purpose |
|---|---|
| `lib/main.dart` | Start up and deep links (`https://host/a/ID` and `mganji://a/ID`) |
| `lib/app_state.dart` | Signed in or not, hand-off settings, pending link, the contact and chat services |
| `lib/api/api_client.dart` | ganji-api calls. The phone number is sent when asking for a code, after that the server reads it from the token |
| `lib/chat/` | `ChatController` (reads new messages every 1.5 seconds while open, sends text, buttons, files, contacts, locations) and `ChatDevice` (camera, microphone, files, location, sound) |
| `lib/contacts/` | Contact search: this phone, Google (People API), Microsoft (Graph), vCard and CSV files |
| `lib/models/handoff.dart` | Builds the dial code and the steps. Only digits, `*` and `#` are ever dialled |
| `lib/models/mpesa_sms.dart`, `sms_match.dart` | Picks apart M-Pesa messages and finds the one for a request (patterns unverified) |
| `lib/models/deep_link.dart` | Accepts only the two link shapes with a valid id |
| `lib/services/` | Secure storage, biometric approval, launcher, SMS reader (Android) |
| `lib/screens/` | Sign in, chat, contact finder, request list, request detail |
| `lib/widgets/` | Chat bubbles and shared pieces: panel, status tag, callout, amount, progress timeline, appearance sheet |
| `lib/theme/app_theme.dart` | The ten looks and the `ThemeData` built from them |
| `test/` | Hand-off, link parsing, models, API client, chat, contact parsers, sources and finder. Tests import `package:ganji_app/...`, so the project name must stay `ganji_app` |

## Platform differences

| | Android | iOS | Linux, macOS, Windows |
|---|---|---|---|
| Dialler with the code filled in | Customer presses call | Same, with a system confirmation | No dialler. Use the copyable steps |
| Open the M-Pesa app | Possible by package name (not verified) | Only with a URL scheme (not verified) | Not possible |
| Matching the M-Pesa SMS | On request, with permission | Not possible. Send the message in the chat | Not possible. Send the message in the chat |
| Approval | Fingerprint, face or phone lock | Face ID, Touch ID or passcode | Not available. The approval step is not guarded yet |

## Things to know before relying on it

> [!WARNING]
> The code was written without a Flutter SDK. The first `flutter analyze` on a real install found real errors, which were fixed, so expect a few more the first time you run a platform that has not been run yet. The desktop builds have no guard yet for plugins that do not exist there (approval, contacts, sign-in), so those buttons can throw.

- **USSD codes and M-Pesa app identifiers are not hard-coded.** They come from the server (`/api/config`) and start empty, so the app shows guided steps until you set verified values.
- **Contacts** are searched on the phone. The Google and Microsoft sign-ins give the app a short-lived token that stays in memory, and nothing from the address book goes to your servers. Only a contact the customer chooses to share is sent, in the chat. You need your own OAuth client ids, see [`docs/CONTACTS.md`](docs/CONTACTS.md).
- **Voice notes** are recorded as AAC in an `.m4a` file (`audio/mp4`). Check that your speech to text service accepts it.
- **Sign-in codes** arrive by SMS only when the server has `CODE_DELIVERY=sms`. In `dev` mode the server returns the code to the app, for testing only.
- **Device lock is not M-Pesa authentication.** It protects our approval step. M-Pesa still asks for its own PIN.
- **Terms.** Check Safaricom's terms and any app store rules about a third party app that hands over to M-Pesa before you publish.

## More reading

| Document | What is in it |
|---|---|
| [`docs/PLATFORM_SETUP.md`](docs/PLATFORM_SETUP.md) | Settings for each platform, signing, app links |
| [`docs/CONTACTS.md`](docs/CONTACTS.md) | Contact search and the Google and Microsoft sign-ins |
| [`docs/SMS.md`](docs/SMS.md) | The M-Pesa message check |

<div align="center">
<sub>M-Pesa is a registered mark of Safaricom. This project is not affiliated with or endorsed by Safaricom.</sub>
</div>
