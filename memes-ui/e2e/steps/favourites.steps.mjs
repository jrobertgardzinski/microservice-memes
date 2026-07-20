import { Then, When } from '@cucumber/cucumber';
import { expect } from 'playwright/test';
import { MEMES } from '../support/world.mjs';

When('they favourite the meme', async function () {
  await this.page.getByRole('button', { name: 'favourite', exact: true }).first().click();
  await expect(this.page.getByRole('button', { name: 'unfavourite' }).first()).toBeVisible();
});

When('they favourite their own meme', async function () {
  // the wall holds other scenarios' memes too — star exactly the one this account uploaded
  // (poll: the UI's upload fires and forgets, the server may still be writing)
  let mine;
  for (let attempt = 0; attempt < 20 && !mine; attempt++) {
    const memes = await (await fetch(`${MEMES}/memes`)).json();
    for (const meme of memes) {
      // a shared wall changes under us: a meme another scenario just purged answers 404 with an
      // empty body, and json() on that is a crash, not a miss
      const metaResponse = await fetch(`${MEMES}/memes/${meme.id}/meta`);
      if (!metaResponse.ok) continue;
      const meta = await metaResponse.json();
      if (meta.author === this.account.email) { mine = meme.id; break; }
    }
    if (!mine) await new Promise((r) => setTimeout(r, 250));
  }
  if (!mine) throw new Error('no meme by this account on the wall yet');
  await this.page.reload();   // make sure the fresh tile is rendered before starring it
  const tile = this.page.locator('.MuiCard-root', { has: this.page.locator(`img[src*="${mine}"]`) });
  await tile.getByRole('button', { name: 'favourite', exact: true }).click();
  await expect(tile.getByRole('button', { name: 'unfavourite' })).toBeVisible();
});

When('unfavourite the meme', async function () {
  await this.page.getByRole('button', { name: 'unfavourite' }).first().click();
});

When('switch to the favourites wall', async function () {
  await this.page.getByRole('button', { name: 'Favourites' }).click();
});

Then('the favourites wall is empty', async function () {
  await expect(this.page.getByText('No favourites yet')).toBeVisible();
});

// the ref outlives the meme on purpose: deletion happens server-side (the author's own DELETE),
// the gallery is none the wiser until it hydrates
When("the meme is deleted behind the gallery's back", async function () {
  // services persist across scenarios, so find THIS account's meme by authorship, not position
  const memes = await (await fetch(`${MEMES}/memes`)).json();
  const token = await this.apiToken();
  for (const meme of memes) {
    const meta = await (await fetch(`${MEMES}/memes/${meme.id}/meta`)).json();
    if (meta.author === this.account.email) {
      const r = await fetch(`${MEMES}/memes/${meme.id}`, {
        method: 'DELETE',
        headers: { Authorization: `Bearer ${token}` },
      });
      if (!r.ok) throw new Error(`the account's meme would not die: ${r.status}`);
      return;
    }
  }
  throw new Error('no meme by this account found to delete');
});

Then('the favourites wall shows an unavailable keepsake', async function () {
  await expect(this.page.getByText('unavailable')).toBeVisible();
});
