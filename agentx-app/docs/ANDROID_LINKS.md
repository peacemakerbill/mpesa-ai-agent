<div align="center">

<img src="../../docs/assets/banner-links.svg" alt="" width="100%"/>

<br/>

<img src="../../docs/assets/badges-links.svg" alt="Android with autoVerify, web path /b/{id}, scheme agentx://b, edit AndroidManifest.xml." width="880"/>

<br/><br/>

One more path in the Android manifest so business requests open in the app.

<sub><a href="../README.md">Back to the Daraja Agent-X app</a> &nbsp;|&nbsp; <a href="../../README.md">Project overview</a></sub>

<img src="../../docs/assets/divider.svg" alt="" width="100%"/>

</div>

# App links for business requests

The chat sends `https://YOUR_HOST/b/{id}` when a request waits for approval. Add one more path to the intent filter you already have
for `/a/` in `android/app/src/main/AndroidManifest.xml`:

```xml
<intent-filter android:autoVerify="true">
    <action android:name="android.intent.action.VIEW"/>
    <category android:name="android.intent.category.DEFAULT"/>
    <category android:name="android.intent.category.BROWSABLE"/>
    <data android:scheme="https" android:host="YOUR_HOST" android:pathPrefix="/a/"/>
    <data android:scheme="https" android:host="YOUR_HOST" android:pathPrefix="/b/"/>
</intent-filter>
<intent-filter>
    <action android:name="android.intent.action.VIEW"/>
    <category android:name="android.intent.category.DEFAULT"/>
    <category android:name="android.intent.category.BROWSABLE"/>
    <data android:scheme="agentx" android:host="a"/>
    <data android:scheme="agentx" android:host="b"/>
</intent-filter>
```

iOS needs nothing new in the app: agentx-api now lists `/b/*` in its `apple-app-site-association` file.
