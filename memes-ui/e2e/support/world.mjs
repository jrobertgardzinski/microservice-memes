import { AfterAll, BeforeAll, Before, After, setWorldConstructor, setDefaultTimeout } from '@cucumber/cucumber';
import { chromium } from 'playwright';
import zlib from 'node:zlib';

// the harness (run-e2e.sh) starts these; ports avoid the docker stack's 8080/8083/8085
export const SECURITY = process.env.SECURITY_URL ?? 'http://localhost:8180';
export const MEMES = process.env.MEMES_URL ?? 'http://localhost:8183';
export const UI = process.env.UI_URL ?? 'http://localhost:4300';

setDefaultTimeout(15_000);

let browser;
let counter = 0;

/**
 * A 1x1 PNG with a UNIQUE pixel colour per call. The meme service deduplicates identical bytes
 * (MemeContentIndex), so a shared fixture PNG would silently collapse every scenario's "upload"
 * into the first one — with the first uploader as author. Chunk CRCs are real (zlib.crc32).
 */
export function uniquePng() {
  const chunk = (type, data) => {
    const body = Buffer.concat([Buffer.from(type, 'ascii'), data]);
    const out = Buffer.alloc(8 + data.length + 4);
    out.writeUInt32BE(data.length, 0);
    body.copy(out, 4);
    out.writeUInt32BE(zlib.crc32(body), 8 + data.length);
    return out;
  };
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(1, 0);   // width
  ihdr.writeUInt32BE(1, 4);   // height
  ihdr[8] = 8;                // bit depth
  ihdr[9] = 6;                // RGBA
  const pixel = Buffer.from([0, ...Buffer.from([counter++ & 255, Math.floor(Math.random() * 256),
      Math.floor(Math.random() * 256), 255])]);   // filter byte + unique RGBA
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', zlib.deflateSync(pixel)),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

BeforeAll(async () => {
  browser = await chromium.launch();
});

AfterAll(async () => {
  await browser?.close();
});

/**
 * One browser context per scenario (fresh localStorage — the gallery keeps its token there) and
 * API helpers that provision the world server-side: accounts through security's test-env mailbox,
 * memes through the real upload endpoint. The UI is only driven for what the scenario is ABOUT.
 */
class GalleryWorld {
  /** A scenario-unique account, registered and verified through the service (not the UI).
   *  `verify: false` leaves it standing at the door with the mail unread — the state a
   *  scenario needs when the POINT is what an unverified account is told. */
  async provisionAccount({ verify = true } = {}) {
    const email = `gallery-${Date.now()}-${counter++}@example.com`;
    const password = 'StrongPassword1!';
    await this.post(`${SECURITY}/register`, { email, password });
    this.account = { email, password };
    if (verify) {
      await this.post(`${SECURITY}/verify-email`, { token: await this.verificationToken(email) });
    }
    return this.account;
  }

  /** What the verification mail carried (the test environment's captured mailbox — a browser
   *  cannot read e-mail, and this is the only backdoor these scenarios use). */
  async verificationToken(email) {
    const r = await fetch(`${SECURITY}/test/mailbox/verification-token?email=${encodeURIComponent(email)}`);
    if (!r.ok) throw new Error(`no verification mail for ${email}`);
    return (await r.json()).token;
  }

  /** The sign-in code the same mailbox captured (the second factor, and the step-up's own step). */
  async signInCode(email) {
    for (let i = 0; i < 20; i++) {           // the code is mailed as the request is served
      const r = await fetch(`${SECURITY}/test/mailbox/signin-code?email=${encodeURIComponent(email)}`);
      if (r.ok) return (await r.json()).code;
      await new Promise((done) => setTimeout(done, 100));
    }
    throw new Error(`no sign-in code for ${email}`);
  }

  /** Gives the account a mailed-code second factor, over the API: the scenarios that USE the
   *  factor are about the panel's code step, not about the enrolment screen. */
  async enrolCodeFactor() {
    const token = await this.apiToken();
    const auth = { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` };
    const start = await fetch(`${SECURITY}/account/factors/EMAIL_CODE/enroll/start`,
      { method: 'POST', headers: auth, body: '{}' });
    if (!start.ok) throw new Error(`factor enrolment refused: ${start.status}`);
    const code = await this.signInCode(this.account.email);
    const confirm = await fetch(`${SECURITY}/account/factors/EMAIL_CODE/enroll/confirm`,
      { method: 'POST', headers: auth, body: JSON.stringify({ code }) });
    if (!confirm.ok) throw new Error(`factor confirmation refused: ${confirm.status}`);
  }

  /** The one-time codes shown exactly once when generated — kept for the scenario that spends one. */
  async generateRecoveryCodes() {
    const token = await this.apiToken();
    const r = await fetch(`${SECURITY}/account/recovery-codes`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    });
    if (!r.ok) throw new Error(`recovery codes refused: ${r.status}`);
    return (await r.json()).codes;
  }

  /** Fills the panel and presses Sign in — no assertion, because half the scenarios are about
   *  what happens when it does NOT simply work. */
  async signIn({ email = this.account.email, password = this.account.password } = {}) {
    await this.page.getByLabel('e-mail').fill(email);
    await this.page.getByLabel('password', { exact: true }).fill(password);
    await this.page.getByRole('button', { name: 'Sign in', exact: true }).click();
  }

  /** The panel's second step: the mailed code — or a recovery code, which the chain accepts
   *  in its place. */
  async submitSignInCode(code) {
    await this.page.getByLabel('sign-in code').fill(code);
    await this.page.getByRole('button', { name: 'Sign in', exact: true }).click();
  }

  /** Signs the account in over HTTP and returns the access token (for API-side seeding).
   *  An account that already carries a factor answers 202 with a ticket — the seeding path
   *  finishes that chain with the mailed code, exactly as the panel would. */
  async apiToken() {
    const r = await this.post(`${SECURITY}/authenticate`, this.account);
    const body = await r.json();
    if (body.accessToken) return body.accessToken;
    if (!body.mfaTicket) throw new Error(`no token and no ticket for ${this.account.email}`);
    const code = await this.signInCode(this.account.email);
    const done = await this.post(`${SECURITY}/authenticate/factor`,
      { mfaTicket: body.mfaTicket, proof: code });
    return (await done.json()).accessToken;
  }

  /** Uploads a 1x1 PNG through the real endpoint; remembers the meme id. */
  async seedMeme() {
    const uploader = await this.provisionAccount();   // its own throwaway author
    const token = await this.apiToken();
    const form = new FormData();
    form.append('file', new Blob([uniquePng()], { type: 'image/png' }), 'pixel.png');
    const r = await fetch(`${MEMES}/memes`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}` },
      body: form,
    });
    if (r.status !== 201) throw new Error(`seed upload failed: ${r.status}`);
    this.memeId = (await r.json()).id;
    this.account = undefined;                          // the scenario signs in as its OWN account
    return this.memeId;
  }

  async openGallery() {
    this.context = await browser.newContext();
    this.page = await this.context.newPage();
    await this.page.goto(UI);
  }

  post(url, body) {
    return fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
  }
}

setWorldConstructor(GalleryWorld);

Before(function () {
  this.memeId = undefined;
  this.account = undefined;
});

After(async function () {
  await this.context?.close();
});
