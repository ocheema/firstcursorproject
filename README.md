# JoinQueue Notifier

Clicks **Join Queue** on a Feather campaign page every ~10 seconds (±3 seconds) and emails you when it joins.

## Android (runs fully on your phone)

See [`android/README.md`](android/README.md).

1. Sideload `JoinQueueNotifier-debug.apk`
2. Enable Accessibility for the app
3. Enter Gmail App Password settings
4. Open the campaign in Chrome and tap **Start clicker**

## Desktop (Node + Playwright)

Requires Node.js 18+ on a computer where you can log into Feather.

```bash
npm install
cp .env.example .env   # set GMAIL_USER, GMAIL_APP_PASSWORD
npm start
```

Details: complete SSO in the Chromium window on first run; leave it running until the success email arrives.
