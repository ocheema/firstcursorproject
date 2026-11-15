# JoinQueue Notifier

Local Node.js + Playwright app that repeatedly clicks **Join Queue** on a Feather campaign page every ~10 seconds (±3 seconds), then emails you when it successfully joins.

## Prerequisites

- Node.js 18+
- A Gmail account with [App Passwords](https://myaccount.google.com/apppasswords) enabled (2-Step Verification required)
- Access to the Feather campaign URL while logged into OpenAI

## Setup

1. Install dependencies (also installs Chromium for Playwright):

   ```bash
   npm install
   ```

2. Copy env template and fill in values:

   ```bash
   cp .env.example .env
   ```

   | Variable | Required | Description |
   |---|---|---|
   | `GMAIL_USER` | yes | Gmail address that sends the mail |
   | `GMAIL_APP_PASSWORD` | yes | Gmail App Password (spaces OK) |
   | `NOTIFY_EMAIL` | no | Recipient (default: `omer.cheema@insead.edu`) |
   | `CAMPAIGN_URL` | no | Override campaign URL |

3. Start the clicker:

   ```bash
   npm start
   ```

## First run / login

A visible Chromium window opens with a persistent profile in `.pw-feather-profile/`.

- If you are not logged into Feather, complete SSO in that window.
- The script waits up to 10 minutes for the campaign page / Join Queue control.
- Later runs reuse the saved session, so you usually will not need to log in again.

Leave the process running until you get the **Joined Feather queue** email, then it exits.

## Behavior

- Clicks Join Queue / JoinQueue when visible
- Waits a random **7–13 seconds** between attempts
- Treats success as: Join Queue control gone/disabled, or page text matching joined / in queue
- Sends one Gmail notification, then stops
- Refreshes the page every 5 attempts to avoid a stale UI

## Notes

- Run this on **your machine** (not in Cloud Agents) so your OpenAI login session is available.
- Do not commit `.env` or `.pw-feather-profile/` — both are gitignored.
