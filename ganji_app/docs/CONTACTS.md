<!-- PLACE THIS FILE AT: mpesa-ai-agent/ganji_app/docs/CONTACTS.md
     Replace the existing file. The images it uses are in mpesa-ai-agent/docs/assets/. You can delete this comment. -->

<div align="center">

<img src="../../docs/assets/banner-contacts.svg" alt="" width="100%"/>

<br/>

<img src="../../docs/assets/badges-contacts.svg" alt="Flutter 3.22 or later, Google People API, Microsoft Graph, sign-in with PKCE and no secret, the address book stays on the phone." width="880"/>

<br/><br/>

The customer can look up a person from five places.

<sub><a href="../README.md">Back to the M-Ganji app</a> &nbsp;|&nbsp; <a href="../../README.md">Project overview</a></sub>

<img src="../../docs/assets/divider.svg" alt="" width="100%"/>

</div>

The lookup happens in the chat (when the assistant asks "who is Mama?"), in the **Share a contact** attachment, and whenever a request needs a contact.

> [!IMPORTANT]
> All searching happens on the phone. Nothing from an address book is sent to our servers. The only thing that is sent is the one contact the customer chooses to share in the chat.

## The five sources

| Source | What is used | Needs |
|---|---|---|
| **This phone** | `flutter_contacts` reads the address book, which includes accounts the phone syncs (Google, Outlook, Samsung and so on) | `READ_CONTACTS` on Android, `NSContactsUsageDescription` on iOS. If the customer refuses, "Use the phone's own chooser" still works with no permission |
| **Google** | People API: `people:searchContacts` and `otherContacts:search` (people the customer has emailed) | A Google OAuth client, below |
| **Microsoft** | Graph `/me/contacts`, read in pages and searched on the phone | A Microsoft app registration, below |
| **A file** | A `.vcf` (vCard 2.1, 3.0 or 4.0) or `.csv` export from any address book, CRM or phone | Nothing |
| **Type it** | A name and number typed by hand | Nothing |

Numbers are shown the Kenyan way (`0712 345 678`) whatever the source wrote (`+254 712 345 678`, `254712345678`, `712345678`).

<div align="center">
<img src="../../docs/assets/divider.svg" alt="" width="100%"/>
</div>

## Google

<details open>
<summary><b>Set up the OAuth client</b></summary>

<br/>

1. In Google Cloud Console create a project, then enable **People API**.
2. On the OAuth consent screen, add the scopes `https://www.googleapis.com/auth/contacts.readonly` and `https://www.googleapis.com/auth/contacts.other.readonly`. Both are *sensitive scopes*. While the app is in "Testing", only the test users you add can sign in. To let any customer sign in, submit the app for Google's verification.
3. Under Credentials, create an **OAuth client ID**:

| Platform | What to enter |
|---|---|
| Android | Type Android, with the app's package name and the SHA-1 of the signing key (`keytool -list -v -keystore your.jks`). Add one for the debug key too |
| iOS | Type iOS with the bundle id. Put the client id in `Info.plist` as `GIDClientID`, and the reversed client id as a URL scheme |

4. No client id is placed in the Dart code. `google_sign_in` 6.x finds the Android client from the package name and key.

</details>

The app asks for the access token when the customer first searches in Google, keeps it in memory, and forgets it if Google answers 401.

## Microsoft (Outlook and Microsoft 365)

<details open>
<summary><b>Register the app in Azure</b></summary>

<br/>

1. In the Azure portal go to App registrations, then New registration. Supported account types: personal Microsoft accounts and work or school accounts.
2. Under Authentication, add a **Mobile and desktop** platform with the redirect URI `mganjiauth://callback`.
3. Under API permissions, add Microsoft Graph, delegated: `Contacts.Read`.
4. Copy the **Application (client) ID** and build the app with it:

```bash
flutter run \
  --dart-define=MS_CLIENT_ID=00000000-0000-0000-0000-000000000000 \
  --dart-define=API_BASE_URL=https://ganji.example.com
```

</details>

With no `MS_CLIENT_ID`, the Microsoft chip is hidden. The sign-in is a public client with PKCE, so no secret is in the app. Android needs `appAuthRedirectScheme: 'mganjiauth'` in `build.gradle`, and iOS needs the URL scheme. Both are in [`PLATFORM_SETUP.md`](PLATFORM_SETUP.md).

## The browser console

`console/console.html` has the same finder (Settings, "Contact search"). Paste a Google Web client id (authorised JavaScript origin is the page's origin) and a Microsoft client id (SPA redirect URI is the page's address) there. The vCard and CSV import, and the browser's own Contact Picker on Chrome for Android, need no setup. See [`console/README.md`](../../console/README.md).

<div align="center">
<img src="../../docs/assets/divider.svg" alt="" width="100%"/>
</div>

## Not verified

> [!WARNING]
> None of the Google or Microsoft sign-ins could be run where this was written, because they need your client ids and a real account. The parsing and the screens were tested against mock responses only. Check on a real phone with a real account before relying on it.

<div align="center">
<img src="../../docs/assets/divider.svg" alt="" width="100%"/>
<sub>M-Pesa is a registered mark of Safaricom. This project is not affiliated with or endorsed by Safaricom.</sub>
</div>
