import { Given, Then, When } from '@cucumber/cucumber';
import { expect } from 'playwright/test';
import { SECURITY, UI } from '../support/world.mjs';

// --- the doors into the gallery ---------------------------------------------------------------

Given('an account exists that never followed its verification link', async function () {
  await this.provisionAccount({ verify: false });
});

Given('the account carries a mailed sign-in code factor', async function () {
  await this.enrolCodeFactor();
});

Given('the account has recovery codes', async function () {
  this.recoveryCodes = await this.generateRecoveryCodes();
});

When('they create an account from the panel', async function () {
  this.account = {
    email: `signup-${Date.now()}-${Math.floor(Math.random() * 1e6)}@example.com`,
    password: 'StrongPassword1!',
  };
  await this.page.getByRole('tab', { name: 'Create account' }).click();
  await this.page.getByLabel('e-mail').fill(this.account.email);
  await this.page.getByLabel('password', { exact: true }).fill(this.account.password);
  await this.page.getByRole('button', { name: 'Create account' }).click();
});

Then('the panel says to check the mail', async function () {
  await expect(this.page.getByText(/check the mail we sent/i)).toBeVisible();
});

Then('the panel says the address is not verified yet', async function () {
  await expect(this.page.getByText(/not verified yet/i)).toBeVisible();
});

When('they follow the link from that mail', async function () {
  // the browser cannot read e-mail: the test-environment mailbox hands over what was "sent",
  // and the visitor arrives on exactly the link the mail carries
  const token = await this.verificationToken(this.account.email);
  await this.page.goto(`${UI}/?verify=${encodeURIComponent(token)}`);
});

Then('the panel says the address is verified', async function () {
  await expect(this.page.getByText(/verified/i)).toBeVisible();
});

Then('signing in with the new account works', async function () {
  await this.signIn();
  await expect(this.page.getByText(`signed in as ${this.account.email}`)).toBeVisible();
});

When('they sign in with that account', async function () {
  await this.signIn();
});

When('they sign in with the wrong password', async function () {
  await this.signIn({ password: 'NotThePassword9!' });
});

Then('the panel refuses them', async function () {
  await expect(this.page.getByRole('alert')).toBeVisible();
});

Then('the panel does not show them as signed in', async function () {
  await expect(this.page.getByText(`signed in as ${this.account.email}`)).toHaveCount(0);
});

Then('the panel asks for the sign-in code', async function () {
  await expect(this.page.getByText(/we e-mailed a sign-in code/i)).toBeVisible();
});

When('they type the code from their mail', async function () {
  await this.submitSignInCode(await this.signInCode(this.account.email));
});

When('they type a recovery code instead', async function () {
  await this.submitSignInCode(this.recoveryCodes[0]);
});

// --- the way out --------------------------------------------------------------------------

Given('signs in with that account through the code step', async function () {
  await this.signIn();
  await expect(this.page.getByText(/we e-mailed a sign-in code/i)).toBeVisible();
  await this.submitSignInCode(await this.signInCode(this.account.email));
  await expect(this.page.getByText(`signed in as ${this.account.email}`)).toBeVisible();
});

When('they open the danger zone', async function () {
  await this.page.getByRole('button', { name: 'delete account…' }).click();
  await expect(this.page.getByRole('heading', { name: 'Delete your account' })).toBeVisible();
});

When('confirm the deletion with their password', async function () {
  await this.page.getByLabel('your password').fill(this.account.password);
  await this.page.getByRole('button', { name: 'Delete my account' }).click();
});

When('confirm the deletion with the wrong password', async function () {
  await this.page.getByLabel('your password').fill('NotThePassword9!');
  await this.page.getByRole('button', { name: 'Delete my account' }).click();
});

When('keep their account instead', async function () {
  await this.page.getByRole('button', { name: 'Keep my account' }).click();
});

Then('the dialog asks for the sign-in code', async function () {
  await expect(this.page.getByLabel('sign-in code')).toBeVisible();
});

When('they confirm the deletion with the code from their mail', async function () {
  await this.page.getByLabel('sign-in code').fill(await this.signInCode(this.account.email));
  await this.page.getByRole('button', { name: 'Confirm & delete' }).click();
});

Then('the panel says the deletion started', async function () {
  await expect(this.page.getByText(/Account deletion started/i)).toBeVisible();
});

Then('the dialog complains about the password', async function () {
  await expect(this.page.getByText('Wrong password.')).toBeVisible();
});

Then('signing in with that account is refused', async function () {
  const r = await this.post(`${SECURITY}/authenticate`, this.account);
  expect(r.status, 'a deleted account cannot come back through the front door').not.toBe(200);
});

Then('signing in with that account still works', async function () {
  const r = await this.post(`${SECURITY}/authenticate`, this.account);
  expect([200, 202]).toContain(r.status);
});
