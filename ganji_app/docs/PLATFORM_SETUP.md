<!-- PLACE THIS FILE AT: mpesa-ai-agent/ganji_app/docs/PLATFORM_SETUP.md
     Replace the existing file. You can delete this comment. -->

<div align="center">
<img src="../../docs/assets/banner-platform.svg" alt="" width="100%"/>
<br/>
<img src="../../docs/assets/badges-platform.svg" alt="Android manifest and Gradle, iOS and macOS Info.plist and Xcode, Linux and Windows packages, Flutter 3.22 or later, minSdk 23, app links served by the M-Ganji API." width="880"/>
</div>

# Platform setup

Flutter creates the platform folders. This page lists what to change in each one. Nothing here was run on a device by the person who wrote it, so check every step on a real phone.

Two names are used throughout:

- `YOUR_HOST` is the public host of ganji-api, the value of `GANJI_PUBLIC_BASE_URL`.
- `ke.co.ganjiai.ganji_app` is the application id when the project is created with organization `ke.co.ganjiai` and name `ganji_app`. Put the same value in `GANJI_ANDROID_PACKAGE` on ganji-api.

## 0. Create the project

In Android Studio: File, New, New Flutter Project, then:

| Field | Value |
|---|---|
| Project name | `ganji_app` (the tests import `package:ganji_app/...`) |
| Organization | `ke.co.ganjiai` |
| Project type | Application |
| Android language | Kotlin |
| iOS language | Swift |
| Platforms | Android, iOS, Linux, macOS, Windows. Leave Web off. |

Then bring in the code and check it:

```bash
cd <your project folder>
rm -rf lib test
cp -r <repository>/ganji_app/lib <repository>/ganji_app/test <repository>/ganji_app/docs .
cp <repository>/ganji_app/pubspec.yaml <repository>/ganji_app/analysis_options.yaml .
rm -f test/widget_test.dart
flutter pub get && flutter analyze && flutter test
```

## 1. Android

### `android/app/src/main/kotlin/ke/co/ganjiai/ganji_app/MainActivity.kt`
`local_auth` needs a fragment activity:

```kotlin
package ke.co.ganjiai.ganji_app

import io.flutter.embedding.android.FlutterFragmentActivity

class MainActivity : FlutterFragmentActivity()
```

### `android/app/src/main/AndroidManifest.xml`
Inside `<manifest>`, before `<application>`:

```xml
<uses-permission android:name="android.permission.INTERNET"/>
<uses-permission android:name="android.permission.USE_BIOMETRIC"/>
<!-- Chat: voice notes, photos and video from the camera, and "share my location" -->
<uses-permission android:name="android.permission.RECORD_AUDIO"/>
<uses-permission android:name="android.permission.CAMERA"/>
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION"/>
<!-- Contact search on this phone. The phone's own contact chooser works without it. -->
<uses-permission android:name="android.permission.READ_CONTACTS"/>
<!-- Optional, for "Check my M-Pesa message". Restricted by Google Play, see SMS.md -->
<uses-permission android:name="android.permission.READ_SMS"/>
<queries>
    <intent><action android:name="android.intent.action.VIEW"/><data android:scheme="https"/></intent>
    <intent><action android:name="android.intent.action.DIAL"/><data android:scheme="tel"/></intent>
    <!-- Replace with the real M-Pesa package from HANDOFF_MPESA_ANDROID_PACKAGE -->
    <package android:name="REPLACE.WITH.MPESA.PACKAGE"/>
</queries>
```

Inside the main `<activity>`, keep `android:launchMode="singleTop"` (so a link opens the running app) and add next to the existing intent filter:

```xml
<!-- The https link the assistant sends in the chat. autoVerify opens the app without a chooser. -->
<intent-filter android:autoVerify="true">
    <action android:name="android.intent.action.VIEW"/>
    <category android:name="android.intent.category.DEFAULT"/>
    <category android:name="android.intent.category.BROWSABLE"/>
    <data android:scheme="https" android:host="YOUR_HOST" android:pathPrefix="/a/"/>
</intent-filter>
<!-- Fallback used by the "Open the app" button on the web page. -->
<intent-filter>
    <action android:name="android.intent.action.VIEW"/>
    <category android:name="android.intent.category.DEFAULT"/>
    <category android:name="android.intent.category.BROWSABLE"/>
    <data android:scheme="mganji" android:host="a"/>
</intent-filter>
```

### `android/app/build.gradle.kts` (or `build.gradle` in older templates)
Inside `defaultConfig`. Microsoft contact sign-in returns to the app through AppAuth, and the build fails without the placeholder:

```kotlin
minSdk = 23
manifestPlaceholders["appAuthRedirectScheme"] = "mganjiauth"
```

Groovy form: `minSdkVersion 23` and `manifestPlaceholders += [appAuthRedirectScheme: 'mganjiauth']`. Google sign-in needs no manifest change, see [`CONTACTS.md`](CONTACTS.md).

### `android/app/src/debug/AndroidManifest.xml`
To reach a plain `http://` server on your computer while developing, add this to the `<application>` tag. Never put it in the release manifest:

```xml
<application android:usesCleartextTraffic="true"/>
```

### App links verification
ganji-api serves `/.well-known/assetlinks.json` once `GANJI_ANDROID_PACKAGE` and `GANJI_ANDROID_SHA256` are set. Get the SHA-256 of your signing certificate with `keytool -list -v -keystore your.jks`, or from Play Console, App signing. After installing, check with `adb shell pm get-app-links ke.co.ganjiai.ganji_app`.

### Release build
Set a signing config and keep `minSdk` at 23 or higher. Then:

```bash
flutter build appbundle --dart-define=API_BASE_URL=https://YOUR_HOST
```

## 2. iOS

You need a Mac with Xcode.

### `ios/Runner/Info.plist`
Add:

```xml
<key>NSFaceIDUsageDescription</key>
<string>Confirm it is really you before a request is sent to M-Pesa.</string>
<key>NSContactsUsageDescription</key>
<string>Pick the person you want to pay. Your contacts stay on this phone.</string>
<key>NSMicrophoneUsageDescription</key>
<string>Record a voice note for M-Ganji.</string>
<key>NSCameraUsageDescription</key>
<string>Take a photo or video to send to M-Ganji.</string>
<key>NSPhotoLibraryUsageDescription</key>
<string>Choose a photo or video to send to M-Ganji.</string>
<key>NSLocationWhenInUseUsageDescription</key>
<string>Share your location with M-Ganji when you choose to.</string>
<key>LSApplicationQueriesSchemes</key>
<array>
    <string>tel</string>
    <string>https</string>
    <!-- Add the M-Pesa app scheme from HANDOFF_MPESA_IOS_URL, for example mpesa -->
</array>
<key>CFBundleURLTypes</key>
<array>
    <dict>
        <key>CFBundleURLName</key><string>mganji</string>
        <key>CFBundleURLSchemes</key><array><string>mganji</string></array>
    </dict>
    <!-- Returns from the Microsoft sign-in used for Outlook contacts -->
    <dict>
        <key>CFBundleURLName</key><string>mganjiauth</string>
        <key>CFBundleURLSchemes</key><array><string>mganjiauth</string></array>
    </dict>
</array>
```

### Podfile
Set the minimum iOS version at the top of `ios/Podfile`:

```ruby
platform :ios, '13.0'
```

### Universal links
Open `ios/Runner.xcworkspace`. Under Runner, Signing and Capabilities, pick your team and add **Associated Domains** with `applinks:YOUR_HOST`. ganji-api serves `/.well-known/apple-app-site-association` once `GANJI_IOS_APP_ID` (TEAMID.bundle.id) is set. Check the bundle id in Xcode, under Runner, General, because Flutter may write the underscore as a hyphen.

### Build
```bash
flutter build ipa --dart-define=API_BASE_URL=https://YOUR_HOST
```

## 3. macOS

You need a Mac. The macOS app runs in a sandbox, so it needs permission for each thing it does. In both `macos/Runner/DebugProfile.entitlements` and `macos/Runner/Release.entitlements` add:

```xml
<key>com.apple.security.network.client</key><true/>
<key>com.apple.security.device.audio-input</key><true/>
<key>com.apple.security.personal-information.addressbook</key><true/>
<key>com.apple.security.files.user-selected.read-only</key><true/>
```

Without `network.client` the app cannot reach the internet. Copy the usage strings from the iOS `Info.plist` section (Contacts, Microphone, Camera, Location) into `macos/Runner/Info.plist`. If secure storage fails to save the sign-in token, turn on the Keychain Sharing capability in Xcode. The entitlements and the keychain step were not verified.

## 4. Linux

No files to edit. Install the build packages:

```bash
sudo apt install clang cmake ninja-build pkg-config libgtk-3-dev liblzma-dev libsecret-1-dev libjsoncpp-dev \
  libgstreamer1.0-dev libgstreamer-plugins-base1.0-dev gstreamer1.0-plugins-good gstreamer1.0-libav
flutter run -d linux --dart-define=API_BASE_URL=http://localhost:8084
```

`libsecret` is for the secure token store, and the GStreamer packages are for voice note playback.

## 5. Windows

Build on a Windows PC with Visual Studio 2022 and the "Desktop development with C++" workload. Turn on Developer Mode in Windows Settings, because Flutter plugins need symlinks. Then:

```bash
flutter run -d windows --dart-define=API_BASE_URL=http://localhost:8084
```

## 6. Run settings in Android Studio

Run, Edit Configurations, select `main.dart`, then fill in Additional run args:

```
--dart-define=API_BASE_URL=http://10.0.2.2:8084 --dart-define=MS_CLIENT_ID=<your Microsoft app id>
```

| Device | `API_BASE_URL` |
|---|---|
| Android emulator | `http://10.0.2.2:8084` |
| Linux, macOS or Windows | `http://localhost:8084` |
| Real phone on your Wi-Fi | `http://<your computer's IP>:8084` |
| Release | `https://YOUR_HOST` |

## What differs between the platforms

| | Android | iOS | Linux, macOS, Windows |
|---|---|---|---|
| Dial with the code filled in | Opens the dialler with the code typed in. The customer presses call. | Same. iOS may show a confirmation sheet first. | No dialler. The copyable steps are used. |
| Open the M-Pesa app | Possible if you know its package name (not verified) | Only if the app has a URL scheme you can open (not verified) | Not possible |
| Reading the M-Pesa SMS | On request, with permission, see [`SMS.md`](SMS.md) | Not possible. The customer sends it in the chat. | Not possible. The customer sends it in the chat. |
| Searching contacts | This phone (with permission, or its own chooser), Google, Microsoft, a .vcf or .csv file | Same, see [`CONTACTS.md`](CONTACTS.md) | Google, Microsoft and files. The phone address book is not available. |
| Approval | Fingerprint, face or phone lock | Face ID, Touch ID or passcode | Not available |

## Not done on purpose
- The app never reads SMS in the background and never dials automatically. The customer presses call and types the PIN in M-Pesa.
- Screenshots are not blocked. To block them on Android, set `WindowManager.LayoutParams.FLAG_SECURE` in `MainActivity`.
- There is no offline mode and no push notification yet. The chat is read every second or two while the app is open, so a reply that comes while the app is closed is seen the next time it opens.

## Not done yet
- The desktop builds have no guard for plugins that do not exist there. The approval step, the contact buttons and the sign-in buttons can throw on Linux, macOS and Windows until those calls are wrapped and the buttons are hidden.
