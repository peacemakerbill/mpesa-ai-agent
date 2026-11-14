# Build settings for each server

One small file for each server the app can be built for. They hold the settings that `flutter run` and `flutter build` would otherwise need typed on the command line with `--dart-define`.

| File | For |
|---|---|
| `staging.json` | The test server, for example the free Render copy |
| `production.json` | The live server. Release builds for the stores |
| `local.json` | Your own computer or phone setup. Optional, never committed |

```json
{
  "API_BASE_URL": "https://agentx-api-free-bgp.onrender.com",
  "MS_CLIENT_ID": ""
}
```

| Key | Meaning |
|---|---|
| `API_BASE_URL` | The server's address, starting with `https://` and with no slash at the end. Required for a release build. |
| `MS_CLIENT_ID` | The Microsoft app registration id for Outlook contacts. Empty hides that source. See `docs/CONTACTS.md`. |

These are not secrets. They end up inside the built app, and `staging.json` and `production.json` are committed. Never put a key or password in them.

## Using them

Do not type the address again. Use the script in `tool/`:

```
tool/app.sh staging run                      run on the connected device against staging
tool/app.sh staging run -d linux             anything after run goes to flutter
tool/app.sh production build appbundle       a store build
tool/app.sh staging build apk
tool/app.sh staging show                     print the settings
tool/app.sh staging set API_BASE_URL https://agentx-api-free-bgp.onrender.com
```

`set` changes one value and leaves the rest of the file as it was. It is the way to update the file when the server's address changes, and it asks you to commit the file afterwards.

The script refuses to build while a value still holds a placeholder (`YOUR_HOST`, `YOURINITIALS`), and refuses a `production` build whose address does not start with `https://`.

For `local.json`, copy `staging.json`, change the address (for example `http://192.168.1.20:8084` for a real phone on your Wi-Fi) and use `tool/app.sh local run`. On your own computer, `flutter run` alone needs no file at all: the app picks `localhost`, or `10.0.2.2` on the Android emulator.

Without the script, the same thing is `flutter run --dart-define-from-file=env/staging.json`. Android Studio and VS Code take that flag in their run arguments.
