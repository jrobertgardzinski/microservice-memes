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
  /** A scenario-unique account, registered and verified through the service (not the UI). */
  async provisionAccount() {
    const email = `gallery-${Date.now()}-${counter++}@example.com`;
    const password = 'StrongPassword1!';
    await this.post(`${SECURITY}/register`, { email, password });
    const mailbox = await fetch(`${SECURITY}/test/mailbox/verification-token?email=${encodeURIComponent(email)}`);
    const { token } = await mailbox.json();
    await this.post(`${SECURITY}/verify-email`, { token });
    this.account = { email, password };
    return this.account;
  }

  /** Signs the account in over HTTP and returns the access token (for API-side seeding). */
  async apiToken() {
    const r = await this.post(`${SECURITY}/authenticate`, this.account);
    return (await r.json()).accessToken;
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
