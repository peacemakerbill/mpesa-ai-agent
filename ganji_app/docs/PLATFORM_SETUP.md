# Platform setup

`./setup.sh your.reverse.domain` creates the `android/` and `ios/` folders. Then apply the changes below.
Nothing here was built or run in the sandbox that wrote it. The package versions in `pubspec.yaml` were written from memory: run `flutter pub get`, then fix any API that changed. Treat each step as something to check on a real phone.

Replace `ganji.example.com` with the host in `GANJI_PUBLIC_BASE_URL`.

## Android

### 1. `android/app/src/main/kotlin/.../MainActivity.kt`
`local_auth` needs a fragment activity:

```kotlin
import io.flutter.embedding.android.FlutterFragmentActivity

class MainActivity : FlutterFragmentActivity()
```

### 2. `android/app/src/main/AndroidManifest.xml`
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

Inside the main `<activity>`, next to the existing intent filter:

```xml
<!-- The https link the assistant sends in the chat. autoVerify makes it open the app without a chooser. -->
<intent-filter android:autoVerify="true">
    <action android:name="android.intent.action.VIEW"/>
    <category android:name="android.intent.category.DEFAULT"/>
    <category android:name="android.intent.category.BROWSABLE"/>
    <data android:scheme="https" android:host="ganji.example.com" android:pathPrefix="/a/"/>
</intent-filter>
<!-- Fallback used by the "Open the app" button on the web page. -->
<intent-filter>
    <action android:name="android.intent.action.VIEW"/>
    <category android:name="android.intent.category.DEFAULT"/>
    <category android:name="android.intent.category.BROWSABLE"/>
    <data android:scheme="mganji" android:host="a"/>
</intent-filter>
```

Microsoft contact sign-in returns to the app through AppAuth. In `android/app/build.gradle`, inside `defaultConfig`, add
`manifestPlaceholders += [appAuthRedirectScheme: 'mganjiauth']`. Google sign-in needs no manifest change, see `CONTACTS.md`.

Set `android:launchMode="singleTop"` on the activity so a link opens the running app instead of a second copy.

### 3. App links verification
ganji-api serves `/.well-known/assetlinks.json` once `GANJI_ANDROID_PACKAGE` and `GANJI_ANDROID_SHA256` are set.
Get the SHA-256 of your signing certificate with `keytool -list -v -keystore your.jks` (or from Play Console, App signing).
Check it with `adb shell pm get-app-links your.package` after install.

### 4. Release build
Set a real `applicationId`, a signing config, and `minSdkVersion 23` or higher (`local_auth` needs it). Build with:

```
flutter build appbundle --dart-define=API_BASE_URL=https://ganji.example.com
```

## iOS

### 1. `ios/Runner/Info.plist`
Add:

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

### 2. Universal links
In Xcode, Runner target, Signing and Capabilities, add **Associated Domains** with `applinks:ganji.example.com`.
ganji-api serves `/.well-known/apple-app-site-association` once `GANJI_IOS_APP_ID` (TEAMID.bundle.id) is set.

### 3. Build
```
flutter build ipa --dart-define=API_BASE_URL=https://ganji.example.com
```

## What differs between the two phones

| | Android | iOS |
|---|---|---|
| Dial with the code filled in | Opens the dialler with the code typed in. The customer presses call. | Same, `tel:` links open the dialler. iOS may show a confirmation sheet first. |
| Open the M-Pesa app | Possible if you know its package name (not verified here). | Only if the app has a URL scheme you can open (not verified here). |
| Reading the M-Pesa SMS | On request, with permission, see `SMS.md`. Otherwise the customer sends it in the chat. | Not possible. The customer sends it in the chat. |
| Searching contacts | This phone (with permission, or its own chooser), Google, Microsoft, a .vcf or .csv file. | Same. See `CONTACTS.md`. |
| Biometrics | Fingerprint, face or phone lock through the system prompt. | Face ID, Touch ID or passcode. |

## Not done on purpose
- The app never reads SMS in the background and never dials automatically. The customer presses call and types the PIN in M-Pesa.
- Screenshots are not blocked. To block them on Android, set `WindowManager.LayoutParams.FLAG_SECURE` in `MainActivity`.
- There is no offline mode and no push notification yet. The chat is read every second or two while the app is open, so a reply that comes while the app is closed is seen the next time it opens.
