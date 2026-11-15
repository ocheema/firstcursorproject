# JoinQueue Notifier (Android)

Personal sideload app that runs fully on your phone:

1. Uses an **Accessibility Service** to tap **Join Queue** in Chrome
2. Retries every **~10 seconds (±3 seconds)**
3. Shows a **phone notification** when it joins

## Download APK

On your phone, open this link and download the file:  
https://github.com/ocheema/firstcursorproject/raw/cursor/joinqueue-android-e788/android/dist/JoinQueueNotifier-debug.apk

Allow installs from unknown sources when prompted, then install **JoinQueue Notifier**.

## First-time setup

1. Open **JoinQueue Notifier**
2. If Accessibility is blocked (“controlled by restricted settings”):
   - **Settings → Apps → JoinQueue Notifier → ⋮ → Allow restricted settings**
3. Tap **Enable Accessibility access** → turn on **JoinQueue Notifier**
4. Tap **Open campaign in browser** and log into Feather in Chrome
5. Return to the app → **Start clicker**
6. Keep Chrome on the campaign page in the foreground

When it joins, you get a phone notification and the clicker stops.

## Build from source (optional)

```bash
cd android
# create local.properties with: sdk.dir=/path/to/Android/Sdk
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## Notes

- Personal use / sideload only — not Play Store packaged
- Accessibility permission is required so the app can tap Join Queue in Chrome
- Leave Chrome visible while waiting; some phones pause background automation
