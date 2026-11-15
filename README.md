# JoinQueue Notifier

Clicks **Join Queue** on a Feather campaign page every ~10 seconds (±3 seconds) and notifies you only when a join actually succeeds.

## Android (runs fully on your phone)

In-app browser + JavaScript clicker (no Accessibility permission).

**Download APK:**  
https://github.com/ocheema/firstcursorproject/raw/cursor/joinqueue-android-e788/android/dist/JoinQueueNotifier-debug.apk

1. Install the APK  
2. Open the app and log into Feather in the built-in page  
3. Tap **Start** and leave the app open  
4. You get a notification only when join succeeds

Details: [`android/README.md`](android/README.md)

## Desktop (Node + Playwright)

```bash
npm install
cp .env.example .env
npm start
```
