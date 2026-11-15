import { chromium } from "playwright";
import nodemailer from "nodemailer";
import dotenv from "dotenv";
import path from "node:path";
import { fileURLToPath } from "node:url";

dotenv.config();

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, "..");
const PROFILE_DIR = path.join(ROOT, ".pw-feather-profile");

const DEFAULT_URL =
  "https://feather.openai.com/campaigns/e27f8b7b-7b84-408b-801d-7437f012e1c1?tab=tasks&is_admin_view=false";

const CAMPAIGN_URL = process.env.CAMPAIGN_URL || DEFAULT_URL;
const NOTIFY_EMAIL = process.env.NOTIFY_EMAIL || "omer.cheema@insead.edu";
const GMAIL_USER = process.env.GMAIL_USER;
const GMAIL_APP_PASSWORD = process.env.GMAIL_APP_PASSWORD;

const JOIN_PATTERN = /join\s*queue/i;
const SUCCESS_PATTERN = /joined|in\s*queue|already\s*in\s*queue|queue\s*joined/i;

function requireEnv() {
  const missing = [];
  if (!GMAIL_USER) missing.push("GMAIL_USER");
  if (!GMAIL_APP_PASSWORD) missing.push("GMAIL_APP_PASSWORD");
  if (missing.length) {
    console.error(
      `Missing required env: ${missing.join(", ")}. Copy .env.example to .env and fill in values.`
    );
    process.exit(1);
  }
}

function randomDelayMs() {
  // 10 seconds ± 3 seconds → [7000, 13000]
  return 7000 + Math.floor(Math.random() * 6001);
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function log(msg) {
  console.log(`[${new Date().toISOString()}] ${msg}`);
}

async function sendSuccessEmail({ attempts, joinedAt }) {
  const transporter = nodemailer.createTransport({
    service: "gmail",
    auth: {
      user: GMAIL_USER,
      pass: GMAIL_APP_PASSWORD.replace(/\s+/g, ""),
    },
  });

  const info = await transporter.sendMail({
    from: GMAIL_USER,
    to: NOTIFY_EMAIL,
    subject: "Joined Feather queue",
    text: [
      "Successfully joined the Feather queue.",
      "",
      `Campaign: ${CAMPAIGN_URL}`,
      `Joined at: ${joinedAt}`,
      `Click attempts: ${attempts}`,
    ].join("\n"),
  });

  log(`Email sent to ${NOTIFY_EMAIL} (messageId=${info.messageId})`);
}

function joinQueueLocator(page) {
  return page
    .getByRole("button", { name: JOIN_PATTERN })
    .or(page.getByRole("link", { name: JOIN_PATTERN }))
    .or(page.locator("button, a, [role='button']").filter({ hasText: JOIN_PATTERN }));
}

async function hasJoinQueue(page) {
  const loc = joinQueueLocator(page).first();
  try {
    return await loc.isVisible({ timeout: 1500 });
  } catch {
    return false;
  }
}

async function looksJoined(page) {
  if (!(await hasJoinQueue(page))) {
    const bodyText = (await page.locator("body").innerText().catch(() => "")) || "";
    if (SUCCESS_PATTERN.test(bodyText)) return true;
    // Join control gone after a click is a strong success signal on this page
    return true;
  }

  const bodyText = (await page.locator("body").innerText().catch(() => "")) || "";
  return SUCCESS_PATTERN.test(bodyText) && !(await hasJoinQueue(page));
}

async function waitForLoginIfNeeded(page) {
  log(`Opening ${CAMPAIGN_URL}`);
  await page.goto(CAMPAIGN_URL, { waitUntil: "domcontentloaded", timeout: 120_000 });

  const deadline = Date.now() + 10 * 60 * 1000;
  while (Date.now() < deadline) {
    const url = page.url();
    const onAuth =
      /login|auth|accounts\.google|signin|sso|okta|openid/i.test(url) ||
      (await page.getByRole("button", { name: /sign\s*in|log\s*in/i }).count()) > 0;

    const ready =
      (await hasJoinQueue(page)) ||
      SUCCESS_PATTERN.test((await page.locator("body").innerText().catch(() => "")) || "");

    if (ready) {
      log("Campaign page ready");
      return;
    }

    if (onAuth || !url.includes("feather.openai.com")) {
      log("Waiting for you to finish login in the browser window…");
    } else {
      log("Waiting for Join Queue control (or joined state) to appear…");
    }

    await sleep(3000);
    if (!url.includes(CAMPAIGN_URL.split("?")[0])) {
      await page.goto(CAMPAIGN_URL, { waitUntil: "domcontentloaded" }).catch(() => {});
    }
  }

  throw new Error("Timed out waiting for login / campaign page (10 minutes)");
}

async function clickJoinQueue(page) {
  const loc = joinQueueLocator(page).first();
  await loc.waitFor({ state: "visible", timeout: 5000 });
  await loc.click({ timeout: 5000 });
}

async function main() {
  requireEnv();

  log(`Profile: ${PROFILE_DIR}`);
  log(`Notify: ${NOTIFY_EMAIL}`);

  const context = await chromium.launchPersistentContext(PROFILE_DIR, {
    headless: false,
    viewport: { width: 1280, height: 900 },
    args: ["--disable-blink-features=AutomationControlled"],
  });

  const page = context.pages()[0] || (await context.newPage());

  let attempts = 0;
  try {
    await waitForLoginIfNeeded(page);

    if (await looksJoined(page)) {
      log("Already joined — sending email and exiting");
      await sendSuccessEmail({ attempts: 0, joinedAt: new Date().toISOString() });
      return;
    }

    while (true) {
      attempts += 1;
      log(`Attempt ${attempts}: clicking Join Queue`);

      try {
        await clickJoinQueue(page);
      } catch (err) {
        log(`Click failed: ${err.message}`);
        if (await looksJoined(page)) {
          log("Looks joined after failed click");
          break;
        }
      }

      // Brief settle so the UI can update before we judge success
      await sleep(1500);

      if (await looksJoined(page)) {
        log("Join Queue success detected");
        break;
      }

      const delay = randomDelayMs();
      log(`Waiting ${(delay / 1000).toFixed(1)}s before next attempt`);
      await sleep(delay);

      // Refresh occasionally so a stale page does not miss an open queue
      if (attempts % 5 === 0) {
        log("Refreshing page");
        await page.reload({ waitUntil: "domcontentloaded" }).catch(() => {});
        await sleep(2000);
      }
    }

    const joinedAt = new Date().toISOString();
    await sendSuccessEmail({ attempts, joinedAt });
    log("Done");
  } finally {
    await context.close().catch(() => {});
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
