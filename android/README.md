# JoinQueue Notifier (Android)

Personal sideload app. It opens the Feather campaign **inside the app** and clicks **Join Queue** with JavaScript every ~10 seconds (±3 seconds). Most clicks fail; it keeps retrying and only fires a **phone notification when join succeeds**.

## Download APK

https://github.com/ocheema/firstcursorproject/raw/cursor/joinqueue-android-e788/android/dist/JoinQueueNotifier-debug.apk

Uninstall any older version first, then install.

## How to use

1. Open **JoinQueue Notifier**
2. Log into Feather in the built-in browser (same screen)
3. Wait until you can see the **Join Queue** button
4. Tap **Start**
5. Leave this app open on that page (screen stays on)

The status line at the top shows each attempt. A red error under Join Queue on the page means the click landed but join failed — that is expected; it will retry. You only get a notification when join actually succeeds.

## Notes

- No Accessibility permission needed anymore
- No Gmail / email
- Personal sideload only
