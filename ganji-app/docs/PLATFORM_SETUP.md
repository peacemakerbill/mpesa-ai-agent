<!-- PLACE THIS FILE AT: mpesa-ai-agent/ganji-app/docs/PLATFORM_SETUP.md
     Replace the existing file. The images it uses are in mpesa-ai-agent/docs/assets/. You can delete this comment. -->

<div align="center">

<img src="../../docs/assets/banner-platform.svg" alt="" width="100%"/>

<br/>

<img src="../../docs/assets/badges-platform.svg" alt="Android manifest and Gradle, iOS Info.plist and Xcode, Flutter 3.22 or later, minSdk 23, app links served by the M-Ganji API." width="880"/>

<br/><br/>

`./setup.sh your.reverse.domain` creates the `android/` and `ios/` folders. Then apply the changes below.

<sub><a href="../README.md">Back to the M-Ganji app</a> &nbsp;|&nbsp; <a href="../../README.md">Project overview</a></sub>

<img src="../../docs/assets/divider.svg" alt="" width="100%"/>

</div>

> [!WARNING]
> Nothing here was built or run in the environment that wrote it. The package versions in `pubspec.yaml` were written from memory. Run `flutter pub get`, then fix any API that changed. Treat each step as something to check on a real phone.

Replace `ganji.example.com` below with the host in `GANJI_PUBLIC_BASE_URL`.

## Android

### 1. Use a fragment activity

In `android/app/src/main/kotlin/.../MainActivity.kt`, because `local_auth` needs a fragment activity:

```kotlin
import io.flutter.embedding.android.FlutterFragmentActivity

class MainActivity : FlutterFragmentActivity()
```

### 2. Edit the manifest

In `android/app/src/main/AndroidManifest.xml`, inside `<manifest>` and before `<application>`, add the permissions and the queries.

| Permission | Why the app asks for it |
|---|---|
| `INTERNET` | Talk to ganji-api |
| `USE_BIOMETRIC` | Fingerprint, face or phone lock before a request is approved |
| `RECORD_AUDIO` | Voice notes in the chat |
| `CAMERA` | Photos and video from the camera |
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | "Share my location" in the chat |
| `READ_CONTACTS` | Contact search on this phone. The phone's own contact chooser works without it |
| `READ_SMS` | Optional, for "Check my M-Pesa message". Restricted by Google Play, see [`SMS.md`](SMS.md) |

```xml
<uses-permission android:name="android.permission.INTERNET"/>
<uses-permission android:name="android.permission.USE_BIOMETRIC"/>
<uses-permission android:name="android.permission.RECORD_AUDIO"/>
<uses-permission android:name="android.permission.CAMERA"/>
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION"/>
<uses-permission android:name="android.permission.READ_CONTACTS"/>
<uses-permission android:name="android.permission.READ_SMS"/>
<queries>
    <intent><action android:name="android.intent.action.VIEW"/><data android:scheme="https"/></intent>
    <intent><action android:name="android.intent.action.DIAL"/><data android:scheme="tel"/></intent>
    <package android:name="REPLACE.WITH.MPESA.PACKAGE"/>
</queries>
```

Replace `REPLACE.WITH.MPESA.PACKAGE` with the real M-Pesa package, the same value as `HANDOFF_MPESA_ANDROID_PACKAGE`.

Inside the main `<activity>`, next to the existing intent filter, add two more:

| Filter | What it does |
|---|---|
| `https` with `autoVerify` | The https link the assistant sends in the chat. `autoVerify` makes it open the app without a chooser |
| `mganji://a` | Fallback used by the "Open the app" button on the web page |

```xml
<intent-filter android:autoVerify="true">
    <action android:name="android.intent.action.VIEW"/>
    <category android:name="android.intent.category.DEFAULT"/>
    <category android:name="android.intent.category.BROWSABLE"/>
    <data android:scheme="https" android:host="ganji.example.com" android:pathPrefix="/a/"/>
</intent-filter>
<intent-filter>
    <action android:name="android.intent.action.VIEW"/>
    <category android:name="android.intent.category.DEFAULT"/>
    <category android:name="android.intent.category.BROWSABLE"/>
    <data android:scheme="mganji" android:host="a"/>
</intent-filter>
```

Also set `android:launchMode="singleTop"` on the activity, so a link opens the running app instead of a second copy.

### 3. Add the Microsoft sign-in redirect

Microsoft contact sign-in returns to the app through AppAuth. In `android/app/build.gradle`, inside `defaultConfig`, add:

```groovy
manifestPlaceholders += [appAuthRedirectScheme: 'mganjiauth']
```

Google sign-in needs no manifest change. See [`CONTACTS.md`](CONTACTS.md).

### 4. Verify app links

ganji-api serves `/.well-known/assetlinks.json` once `GANJI_ANDROID_PACKAGE` and `GANJI_ANDROID_SHA256` are set. Get the SHA-256 of your signing certificate with `keytool -list -v -keystore your.jks`, or from Play Console, App signing. After installing, check it:

```bash
adb shell pm get-app-links your.package
```

### 5. Release build

Set a real `applicationId`, a signing config, and `minSdkVersion 23` or higher (`local_auth` needs it). Build with:

```bash
flutter build appbundle --dart-define=API_BASE_URL=https://ganji.example.com
```

<div align="center">
<img src="../../docs/assets/divider.svg" alt="" width="100%"/>
</div>

## iOS

### 1. Edit Info.plist

In `ios/Runner/Info.plist`, add the usage descriptions. iOS shows these strings in the permission prompts:

| Key | Text shown |
|---|---|
| `NSFaceIDUsageDescription` | Confirm it is really you before a request is sent to M-Pesa. |
| `NSContactsUsageDescription` | Pick the person you want to pay. Your contacts stay on this phone. |
| `NSMicrophoneUsageDescription` | Record a voice note for the assistant. |
| `NSCameraUsageDescription` | Take a photo or video to send to the assistant. |
| `NSPhotoLibraryUsageDescription` | Choose a photo or video to send to the assistant. |
| `NSLocationWhenInUseUsageDescription` | Share your location with the assistant when you choose to. |

```xml
<key>NSFaceIDUsageDescription</key>
<string>Confirm it is really you before a request is sent to M-Pesa.</string>
<key>NSContactsUsageDescription</key>
<string>Pick the person you want to pay. Your contacts stay on this phone.</string>
<key>NSMicrophoneUsageDescription</key>
<string>Record a voice note for the assistant.</string>
<key>NSCameraUsageDescription</key>
<string>Take a photo or video to send to the assistant.</string>
<key>NSPhotoLibraryUsageDescription</key>
<string>Choose a photo or video to send to the assistant.</string>
<key>NSLocationWhenInUseUsageDescription</key>
<string>Share your location with the assistant when you choose to.</string>
<key>LSApplicationQueriesSchemes</key>
<array>
    <string>tel</string>
    <string>https</string>
</array>
<key>CFBundleURLTypes</key>
<array>
    <dict>
        <key>CFBundleURLName</key><string>mganji</string>
        <key>CFBundleURLSchemes</key><array><string>mganji</string></array>
    </dict>
    <dict>
        <key>CFBundleURLName</key><string>mganjiauth</string>
        <key>CFBundleURLSchemes</key><array><string>mganjiauth</string></array>
    </dict>
</array>
```

Two more things to know about that block:

- Add the M-Pesa app scheme from `HANDOFF_MPESA_IOS_URL` (for example `mpesa`) as another `<string>` in `LSApplicationQueriesSchemes`.
- The `mganjiauth` entry is the return path from the Microsoft sign-in used for Outlook contacts.

### 2. Universal links

In Xcode, open the Runner target, then Signing and Capabilities, and add **Associated Domains** with `applinks:ganji.example.com`. ganji-api serves `/.well-known/apple-app-site-association` once `GANJI_IOS_APP_ID` (`TEAMID.bundle.id`) is set.

### 3. Build

```bash
flutter build ipa --dart-define=API_BASE_URL=https://ganji.example.com
```

<div align="center">
<img src="../../docs/assets/divider.svg" alt="" width="100%"/>
</div>

## What differs between the two phones

| | Android | iOS |
|---|---|---|
| Dial with the code filled in | Opens the dialler with the code typed in. The customer presses call | Same, `tel:` links open the dialler. iOS may show a confirmation sheet first |
| Open the M-Pesa app | Possible if you know its package name (not verified here) | Only if the app has a URL scheme you can open (not verified here) |
| Reading the M-Pesa SMS | On request, with permission, see [`SMS.md`](SMS.md). Otherwise the customer sends it in the chat | Not possible. The customer sends it in the chat |
| Searching contacts | This phone (with permission, or its own chooser), Google, Microsoft, a `.vcf` or `.csv` file | Same. See [`CONTACTS.md`](CONTACTS.md) |
| Biometrics | Fingerprint, face or phone lock through the system prompt | Face ID, Touch ID or passcode |

## Not done on purpose

- The app never reads SMS in the background and never dials automatically. The customer presses call and types the PIN in M-Pesa.
- Screenshots are not blocked. To block them on Android, set `WindowManager.LayoutParams.FLAG_SECURE` in `MainActivity`.
- There is no offline mode and no push notification yet. The chat is read about every 1.5 seconds while the app is open, so a reply that comes while the app is closed is seen the next time it opens.

<div align="center">
<img src="../../docs/assets/divider.svg" alt="" width="100%"/>
<sub>M-Pesa is a registered mark of Safaricom. This project is not affiliated with or endorsed by Safaricom.</sub>
</div>
