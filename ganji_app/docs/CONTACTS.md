# Contact search

The customer can look up a person from five places, in the chat (when the assistant asks "who is Mama?"), in the
Share a contact attachment, and when a request needs a contact. All searching happens on the phone. Nothing from an address book is
sent to our servers. The only thing that is, is the one contact the customer chooses to share in the chat.

| Source | What is used | Needs |
|---|---|---|
| This phone | `flutter_contacts` reads the address book, which includes accounts the phone syncs (Google, Outlook, Samsung and so on). | `READ_CONTACTS` / `NSContactsUsageDescription`. If the customer refuses, "Use the phone's own chooser" still works with no permission. |
| Google | People API: `people:searchContacts` and `otherContacts:search` (people the customer has emailed). | A Google OAuth client, below. |
| Microsoft | Graph `/me/contacts`, read in pages and searched on the phone. | A Microsoft app registration, below. |
| A file | A `.vcf` (vCard 2.1, 3.0 or 4.0) or `.csv` export from any address book, CRM or phone. | Nothing. |
| Type it | A name and number typed by hand. | Nothing. |

Numbers are shown the Kenyan way (`0712 345 678`) whatever the source wrote (`+254 712 345 678`, `254712345678`, `712345678`).

## Google

1. In Google Cloud Console create a project, then enable **People API**.
2. OAuth consent screen: add the scopes `https://www.googleapis.com/auth/contacts.readonly` and
   `https://www.googleapis.com/auth/contacts.other.readonly`. Both are *sensitive scopes*. While the app is in "Testing" only the test users you add can sign in.
   To let any customer sign in, submit the app for Google's verification.
3. Credentials, create an **OAuth client ID**:
   * Android: type Android, with the app's package name and the SHA-1 of the signing key (`keytool -list -v -keystore your.jks`). Add one for the debug key too.
   * iOS: type iOS with the bundle id, then put the client id in `Info.plist` as `GIDClientID` and the reversed client id as a URL scheme.
4. No client id is placed in the Dart code. `google_sign_in` 6.x finds the Android client from the package name and key.

The app asks for the access token when the customer first searches in Google, keeps it in memory, and forgets it if Google answers 401.

## Microsoft (Outlook, Microsoft 365)

1. Azure portal, App registrations, New registration. Supported account types: personal Microsoft accounts and work or school accounts.
2. Authentication, add a **Mobile and desktop** platform with the redirect URI `mganjiauth://callback`.
3. API permissions, Microsoft Graph, delegated: `Contacts.Read`.
4. Copy the **Application (client) ID** and build the app with it:

```
flutter run --dart-define=MS_CLIENT_ID=00000000-0000-0000-0000-000000000000 --dart-define=API_BASE_URL=...
```

With no `MS_CLIENT_ID` the Microsoft chip is hidden. The sign-in is a public client with PKCE, so no secret is in the app.
Android needs `appAuthRedirectScheme: 'mganjiauth'` in `build.gradle` and iOS needs the URL scheme, both in `PLATFORM_SETUP.md`.

## The browser console

`console/console.html` has the same finder (Settings, "Contact search"). Paste a Google Web client id (authorised JavaScript origin = the page's origin) and/or
a Microsoft client id (SPA redirect URI = the page's address) there. The vCard and CSV import, and on Chrome for Android the browser's own
Contact Picker, need no setup.

## Not verified

None of the Google or Microsoft sign-ins could be run where this was written, because they need your client ids and a real account.
The parsing and the screens were tested against mock responses only. Check on a real phone with a real account before relying on it.
