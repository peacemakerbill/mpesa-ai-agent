<div align="center">

<img src="../../docs/assets/banner-files.svg" alt="" width="100%"/>

<br/>

<img src="../../docs/assets/badges-files.svg" alt="Flutter 3.22 or later, file cache limit with a 200 MB default, sign out clears the cache, no M-Pesa account link by design." width="880"/>

<br/><br/>

How the phone keeps files, and what the My OneApp screen does.

<sub><a href="../README.md">Back to the Daraja Agent-X app</a> &nbsp;|&nbsp; <a href="../../README.md">Project overview</a></sub>

<img src="../../docs/assets/divider.svg" alt="" width="100%"/>

</div>

# Files on the phone, and My OneApp

## Files and storage (Settings)

- Files opened in the chat are kept in the app's private folder, so they open at once and without data the next time.
- The limit is Off, 50 MB, 200 MB (default), 500 MB or 1 GB. When it is passed, the file opened longest ago goes first.
- Everything is removed on sign out. A session that expires on its own does not run this: add
  `await (await MediaCache.shared()).clear();` to `AppState.signOut()` (and `sessionExpired()` if it does not call it) to cover that too.
- The server copy is separate: see `agent-service/STORAGE.md`. `ApiClient.deleteChatFile(id)` removes one file from the server; no screen calls it yet.

## My OneApp and M-Pesa (Settings)

The screen explains how a payment from the person's own M-Pesa works and offers two shortcuts: open My OneApp, and open the dialer on `*334#`.
It has **no** "connect my M-Pesa account" button, on purpose. What was found:

- Safaricom merged the M-PESA app and MySafaricom into **My OneApp** in April 2026. The old M-PESA store listing was converted into it.
- Third parties can add services through **Mini Apps** inside My OneApp. Mini apps are built with web technologies in a sandbox, and go through
  a submission and approval process. Public pages say nothing about how a mini app identifies the user or what account data it can reach.
- Daraja 3.0 did not change the authentication model, and nothing public describes letting an outside app act on a customer's personal M-Pesa account.

So today the chat prepares a request, the person approves it here, and the PIN is entered in M-Pesa. The realistic route to a deeper link is a
Mini App for Daraja Agent-X, which is a decision and an application to Safaricom, not something code in this repo can do.

### Check on a device before release

`oneAppAndroidPackages` in `lib/chat/one_app.dart` holds one package id (`com.safaricom.mpesa.lifestyle`) that is **not confirmed** for My OneApp.
Run `adb shell pm list packages | grep -i safaricom` on a phone that has My OneApp and put the right id first. If no id opens the app, the button falls
back to a Play Store search, so a wrong id does not break it. If you want the check to work without a store round trip on Android 11 and newer,
add to `android/app/src/main/AndroidManifest.xml`, outside `<application>`:

```xml
<queries>
    <package android:name="com.safaricom.mpesa.lifestyle"/>
</queries>
```

Sources: [Techtrends Kenya on My OneApp](https://techtrendske.co.ke/2026/04/01/safaricom-one-app-merges-m-pesa-adds-mini-apps/),
[TechCabal on the migration](https://techcabal.com/2026/04/07/safaricom-migrates-m-pesa-users-to-my-oneapp-in-platform-consolidation-push/),
[Safaricom's Mini Apps page](https://developer.safaricom.co.ke/miniapps),
[what Daraja 3.0 changed](https://dev.to/ronnyabuto/what-daraja-30-actually-changed-for-developers-and-what-it-did-not-3ek4),
[My OneApp on the App Store](https://apps.apple.com/us/app/my-oneapp/id1496784664).
