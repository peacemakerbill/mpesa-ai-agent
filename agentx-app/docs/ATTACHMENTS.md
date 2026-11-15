# Camera, video, files, location and file size

How the attach menu in the chat works on each platform, what it needs, and how photos and videos are made smaller before they are sent.

Nothing here was run on a device by the person who wrote it, so check each platform you ship. The one place that cannot be checked without a Flutter install is the build itself: run `flutter pub get && flutter analyze && flutter test` first.

## What opens on each platform

| Attach menu entry | Android and iOS | Web, Linux, Windows, macOS |
|---|---|---|
| Photo | The phone's photo library | The system file dialog, pictures only |
| Camera | The phone's own camera app | The in-app camera screen (`lib/chat/camera_capture_screen.dart`) |
| Video | The phone's video library | The system file dialog, videos only |
| Record video | The phone's own camera app, up to 2 minutes | The in-app camera screen, up to 2 minutes |
| Document | The phone's file chooser (`file_picker`) | The system file dialog (`file_selector`) |
| Location | GPS (`geolocator`) | The browser's location prompt, GeoClue on Linux, the system location service on Windows and macOS. If it fails, a box asks for the place to be typed or pasted. |
| Microphone button | AAC in an `.m4a` file | The best format the device can record, see "Voice notes" below |

The in-app camera is needed because the system camera picker does not exist on the web or on Linux. On a computer it uses `camera` with `camera_desktop` (GStreamer and V4L2 on Linux, AVFoundation on macOS, Media Foundation on Windows). In a browser it uses the browser's own camera API. The same screen records video: press the red button to start, press it again to stop, and it stops by itself at 2 minutes.

## What each platform needs

| Platform | Needs |
|---|---|
| Android | Nothing new. `CAMERA`, `RECORD_AUDIO` and location permissions are already in the manifest. |
| iOS | `NSCameraUsageDescription`, `NSMicrophoneUsageDescription`, `NSPhotoLibraryUsageDescription` and `NSLocationWhenInUseUsageDescription` in `Info.plist`. Without the camera one the app closes the moment the camera opens. See `PLATFORM_SETUP.md`. |
| Web | The page must be served over `https://`, or from `localhost`. A browser refuses the camera, microphone and location on any other address. Allow each one from the lock icon next to the address. |
| Linux | `sudo apt install libgstreamer1.0-dev libgstreamer-plugins-base1.0-dev gstreamer1.0-plugins-good` to build with the camera, `pulseaudio-utils ffmpeg` for voice notes, and the `geoclue-2.0` service for location. The user must be in the `video` group to open a camera on some systems. |
| macOS | The camera, microphone and location entitlements, and the usage strings in `Info.plist`. See `PLATFORM_SETUP.md`. |
| Windows | Windows Settings, Privacy and security, Camera: "Let desktop apps access your camera" must be on. Same for the microphone and location. |

## Making files smaller

Photos and videos are made smaller on the device before they are uploaded, so a 12 MB phone photo does not hit the server's 5 MB limit for pictures and a minute of video does not use a month of data. The choice is in **Settings, Photo and video size**, and is kept on the device.

| Level | Photos | Videos |
|---|---|---|
| Original | Sent as they are | Sent as they are |
| Balanced (default) | Longest side 1920 pixels, JPEG quality 82 | Medium quality |
| Small | Longest side 1280 pixels, JPEG quality 68 | Low quality |

What happens exactly:

- A photo is decoded, turned upright using its camera rotation, scaled down and saved again as a JPEG. A picture with transparency stays a PNG. A GIF is never touched, so it keeps its animation. Photos under 200 KB are sent as they are.
- The new file is used only when it is smaller than the original. If the picture cannot be read (a HEIC file on a platform that has no decoder, for one) or anything goes wrong, the original is sent.
- Saving a photo again removes the information a camera stores inside it, such as the place it was taken.
- On Android and iOS a video is compressed after it was recorded or chosen (`video_compress`). On the web and on computers there is no video compressor, so the in-app camera records at the bitrate of the chosen level instead (about 2.5 Mbit/s for Balanced, 1 Mbit/s for Small), and a video chosen from disk is sent as it is.
- Documents and voice notes are not compressed. A file larger than 30 MB after all this is refused with a message.
- While a file is being prepared the chat shows a line above the message box. A long video can take a minute.

The server limits are separate: `AGENT_MAX_IMAGE_BYTES` (5 MB by default) for pictures the assistant reads, and the file store limit in `agentx-api` (15 MB when files are kept in the database). Compression is what keeps ordinary phone photos under them.

## Voice notes

The recorder picks the first format the device supports: AAC (`audio/mp4`), then Opus (`audio/webm` in Chrome, `audio/ogg` in Firefox and on computers), then WAV. The server's speech to text client accepts all of them. Chrome and Firefox cannot record AAC, so the web usually sends Opus. Safari records AAC.

## Location

On a phone the position comes from the GPS and is read once, when the button is pressed. On the web the browser asks for permission and uses Wi-Fi and IP based positioning, so it can be several hundred metres off on a computer. When the position cannot be read at all, the chat asks for the place instead: type `-1.286389, 36.817223`, or paste a Google Maps link. The same box is used by **Settings, Profile, Use my location**.

## When something does not open

| What you see | Why, and what to do |
|---|---|
| "The app is not allowed to use the camera" | Allow it. On a phone in Settings, in a browser from the lock icon next to the address, on macOS in System Settings, Privacy and Security, Camera. |
| "A browser only opens the camera on a secure page" | Open the app at an `https://` address, or at `localhost`. A plain `http://` address on your network will not work. |
| "No camera was found on this device" | Plug one in. On Linux check `ls /dev/video*`, and that your user is in the `video` group. |
| "Another app is using the camera" | Close the other app (a video call, for example) and press Try again. |
| The camera screen is black on Linux | The GStreamer plugins are missing. Install `gstreamer1.0-plugins-good` and start the app again. |
| Voice note fails on Linux | Install `pulseaudio-utils` and `ffmpeg`. |
| Contact file picker does not open on Linux | The vCard and CSV picker in contact search uses `file_picker`, which calls `zenity` or `kdialog` on Linux. Install `zenity`. The attach menu does not use it. |
| Location says "could not be read" on Linux | Install and start GeoClue (`geoclue-2.0`), or type the place. |
