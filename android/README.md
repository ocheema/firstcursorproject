# JoinQueue Notifier (Android)

Personal sideload app that runs fully on your phone:

1. Uses an **Accessibility Service** to tap **Join Queue** in Chrome
2. Retries every **~10 seconds (±3 seconds)**
3. Sends a **Gmail** email + local notification when it joins

## Install the APK (no Android Studio needed)

1. On your phone, allow installs from unknown sources / this browser
2. Download `JoinQueueNotifier-debug.apk` from this PR / agent artifacts
3. Open the APK and install **JoinQueue Notifier**

## First-time setup on the phone

1. Open **JoinQueue Notifier**
2. Enter:
   - Campaign URL (pre-filled)
   - Gmail address
   - Gmail [App Password](https://myaccount.google.com/apppasswords)
   - Notify email
3. Tap **Save settings**
4. Tap **Enable Accessibility access** → turn on **JoinQueue Notifier**
5. Tap **Open campaign in browser** and log into Feather in Chrome
6. Return to the app → **Start clicker**
7. Keep Chrome on the campaign page in the foreground

When it joins, you get a phone notification and an email. The clicker then stops.

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
- Do not force-stop the app while it is running
- Some phones may pause background work; leave Chrome visible while waiting
