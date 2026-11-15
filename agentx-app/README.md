<div align="center">

<img src="../docs/assets/banner-agentx-app.svg" alt="" width="100%"/>

<br/>

<img src="../docs/assets/badges-agentx-app.svg" alt="Flutter 3.22 or later, Dart 3.4 or later, Android, iOS and macOS, Linux and Windows desktop, ten looks." width="880"/>

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
2. Asks the phone to confirm the owner: fingerprint, face, or the phone lock. The operating system does the check. Beside it the customer can tap **Approve with OTP** (on the chat message, the request screen and the business request screen): a 6 digit code is sent by text message to their own number and typed into a small sheet. It helps on a phone with no lock.
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

Web is not supported, because the code uses `dart:io`. To try the app in Chrome anyway, run `flutter run -d chrome --web-port 3000` and allow `http://localhost:3000` in agentx-api's `CORS_ALLOWED_ORIGINS` (see `console/README.md`). Expect parts of it to fail there. The console is the tool for testing in a browser.

## Create the project and run it

The platform folders (`android/`, `ios/`, `linux/`, `macos/`, `windows/`) are made by Flutter and are committed with the project. If a checkout does not have them, create them once:

1. In Android Studio choose File, New, New Flutter Project. Name it `agentx_app`, organization `ke.co.darajaagentx`, Kotlin and Swift, and tick Android, iOS, Linux, macOS and Windows. (From a terminal, `./setup.sh ke.co.darajaagentx` does the same.)
2. Keep `lib/`, `test/`, `docs/`, `pubspec.yaml` and `analysis_options.yaml` from this repository, and delete the `test/widget_test.dart` that Flutter adds.
3. Apply the settings for each platform from [`docs/PLATFORM_SETUP.md`](docs/PLATFORM_SETUP.md).
4. Run:

```bash
flutter pub get
flutter analyze && flutter test
flutter run
```

With no setting, the app looks for a server on your own computer: `http://10.0.2.2:8084` on the Android emulator and `http://localhost:8084` everywhere else. For any other server, pass `API_BASE_URL`:

| Device | `API_BASE_URL` |
|---|---|
| Android emulator | Nothing needed, the default is `http://10.0.2.2:8084` |
| Linux, macOS, Windows, iOS simulator | Nothing needed, the default is `http://localhost:8084` |
| Real phone on the same Wi-Fi | `http://<your computer's IP>:8084` |
| Release build | `https://<your public host>`, required. A release build without it shows a message and nothing else. |

The addresses for staging and production are kept in `env/*.json` and used by `tool/app.sh`, so they are typed once. See [`env/README.md`](env/README.md).

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
| `lib/main.dart` | Start up and deep links (`https://host/a/ID` and `agentx://a/ID`) |
| `lib/app_state.dart` | Signed in or not, hand-off settings, pending link, the contact and chat services |
| `lib/api/api_client.dart` | agentx-api calls. The phone number is sent when asking for a code, after that the server reads it from the token |
| `lib/chat/` | `ChatController` (reads new messages every 1.5 seconds while open, sends text, buttons, files, contacts, locations) and `ChatDevice` (camera, microphone, files, location, sound) |
| `lib/contacts/` | Contact search: this phone, Google (People API), Microsoft (Graph), vCard and CSV files |
| `lib/models/handoff.dart` | Builds the dial code and the steps. Only digits, `*` and `#` are ever dialled |
| `lib/models/mpesa_sms.dart`, `sms_match.dart` | Picks apart M-Pesa messages and finds the one for a request (patterns unverified) |
| `lib/models/deep_link.dart` | Accepts only the two link shapes with a valid id |
| `lib/services/` | Secure storage, biometric approval, launcher, SMS reader (Android) |
| `lib/screens/` | Sign in, sign up, chat, contact finder, request list, request detail, settings, profile, email addresses, business accounts (list, form, CSV import), business operations, files and storage, My OneApp |
| `lib/chat/mentions.dart`, `media_cache.dart`, `one_app.dart` | `@` mentions, the phone file cache with its size limit, and My OneApp |
| `lib/models/business_account.dart`, `business_operation.dart` | Business accounts, CSV import result, and operations prepared from the chat |
| `lib/models/profile.dart`, `lib/profile/` | The profile, saved email addresses, sign up steps, and the profile picture widget |
| `lib/widgets/` | Chat bubbles and shared pieces: panel, status tag, callout, amount, progress timeline, appearance sheet, `brand.dart` (the logo, loader and lockup), and `auth_shell.dart`, the frame shared by the pages before sign in |
| `lib/theme/app_theme.dart` | The ten looks and the `ThemeData` built from them |
| `test/` | Hand-off, link parsing, models, API client, chat, contact parsers, sources and finder. Tests import `package:agentx_app/...`, so the project name must stay `agentx_app` |

## Platform differences

| | Android | iOS | Linux, macOS, Windows |
|---|---|---|---|
| Dialler with the code filled in | Customer presses call | Same, with a system confirmation | No dialler. Use the copyable steps |
| Open the M-Pesa app | Possible by package name (not verified) | Only with a URL scheme (not verified) | Not possible |
| Matching the M-Pesa SMS | On request, with permission | Not possible. Send the message in the chat | Not possible. Send the message in the chat |
| Approval | Fingerprint, face or phone lock, or OTP | Face ID, Touch ID or passcode, or OTP | OTP only. The phone check is not available |

## Things to know before relying on it

> [!WARNING]
> The code was written without a Flutter SDK. The first `flutter analyze` on a real install found real errors, which were fixed, so expect a few more the first time you run a platform that has not been run yet. The desktop builds have no guard yet for plugins that do not exist there (approval, contacts, sign-in), so those buttons can throw.

- **USSD codes and M-Pesa app identifiers are not hard-coded.** They come from the server (`/api/config`) and start empty, so the app shows guided steps until you set verified values.
- **Contacts** are searched on the phone. The Google and Microsoft sign-ins give the app a short-lived token that stays in memory, and nothing from the address book goes to your servers. Only a contact the customer chooses to share is sent, in the chat. You need your own OAuth client ids, see [`docs/CONTACTS.md`](docs/CONTACTS.md).
- **Voice notes** are recorded as AAC in an `.m4a` file (`audio/mp4`). Check that your speech to text service accepts it.
- **Sign-in codes** arrive by SMS only when the server has `CODE_DELIVERY=sms`. On development and staging servers (`CODE_SHOW_IN_APP`) the server also returns the code, and the app shows it under the code box, then fills it in and signs in by itself a moment later. A production server never does. On Android the app also reads the code from the text message and signs in by itself, with no SMS permission (Google's SMS Retriever, see [`docs/SMS.md`](docs/SMS.md)). On iPhone the keyboard offers the code from the message and the sixth digit signs in.
- **Device lock is not M-Pesa authentication.** It protects our approval step. M-Pesa still asks for its own PIN.
- **Terms.** Check Safaricom's terms and any app store rules about a third party app that hands over to M-Pesa before you publish.

## Business accounts, files and My OneApp

See `docs/BUSINESS_ACCOUNTS.md`, `docs/FILES_AND_MY_ONEAPP.md` and `docs/ANDROID_LINKS.md`.

On **Business accounts**, the QR icon (and **QR code** on each account) makes a Dynamic QR code a customer scans to pay, for the saved account or for any Till, Paybill or phone number, and shows the Lipa na Bonga steps for a Till. The same can be asked in the chat. See "QR codes" in `docs/BUSINESS_ACCOUNTS.md`.

## The logo

`lib/widgets/brand.dart` draws the logo with a painter, so it is sharp at every size and needs no image or package. `BrandMark` takes a `motion`: `still` (default), `intro` (draws once, then a coin crosses the bridge) or `loop`. `BrandLoader` replaces a page-sized progress circle, and `BrandLockup` is the mark with the name. With the phone's "remove animations" setting on, every motion shows a still logo. The launcher icons were made from the same drawing, see [`../docs/BRAND.md`](../docs/BRAND.md).

## Sign up and profile

**Chats.** The Chats button in the chat's top bar lists every chat, pinned first. Start a new one, open an old one, rename, pin or delete it (the three dots, or swipe a row to the left), and search once there are more than six. Opening an old chat brings back its messages and what the assistant remembers. See [`../docs/CHATS.md`](../docs/CHATS.md).

**Selecting.** Both lists have a Select mode (the three dots, or press and hold a row). Tick several chats and delete them together, or tick several finished requests and clear them together. Delete all chats and Clear finished requests are in the same menu. Clearing a request only hides it from this list, the record stays on the server. Open requests cannot be cleared. Deleting chats is for good, goes one by one under the hood (the limit is 60 changes a minute), and stops at the first chat that cannot be deleted, for example while the assistant is still replying.

The sign in screen has **Create an account**, in the same look as sign in. Three steps with a progress bar: your name (shown live as you type), your phone and an optional email, then the SMS code in six boxes that send themselves when the sixth digit is in, with a countdown before a new code can be asked for. **Settings, Profile** edits names, email (verified with its own code), address, location and the profile picture, and switches the sign in and payment emails on or off. **Email addresses** keeps the list reports can be sent to (add, paste or upload a CSV or text file). The picture and the location use the camera, gallery and location permissions the chat already asks for. See [`../docs/PROFILE_AND_EMAIL.md`](../docs/PROFILE_AND_EMAIL.md).

## More reading

| Document | What is in it |
|---|---|
| [`docs/PLATFORM_SETUP.md`](docs/PLATFORM_SETUP.md) | Settings for each platform, signing, app links |
| [`docs/CONTACTS.md`](docs/CONTACTS.md) | Contact search and the Google and Microsoft sign-ins |
| [`docs/SMS.md`](docs/SMS.md) | The M-Pesa message check |

<div align="center">
<sub>M-Pesa is a registered mark of Safaricom. This project is not affiliated with or endorsed by Safaricom.</sub>
</div>
