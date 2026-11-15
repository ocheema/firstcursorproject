# JoinQueue Notifier

Clicks **Join Queue** on a Feather campaign page every ~10 seconds (±3 seconds) and notifies you when a join actually succeeds.

## Android (runs fully on your phone)

See [`android/README.md`](android/README.md).

**Download APK:**  
https://github.com/ocheema/firstcursorproject/raw/cursor/joinqueue-android-e788/android/dist/JoinQueueNotifier-debug.apk

1. Sideload the APK
2. Allow restricted settings → enable Accessibility
3. Open the campaign in Chrome and tap **Start clicker**
4. Keep Chrome on the page — many clicks may fail; the app verifies success after each try and only notifies when join really works

## Desktop (Node + Playwright)

Requires Node.js 18+ on a computer where you can log into Feather.

```bash
npm install
cp .env.example .env   # set GMAIL_USER, GMAIL_APP_PASSWORD
npm start
```
