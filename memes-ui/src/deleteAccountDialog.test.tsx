import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import DeleteAccountDialog from './DeleteAccountDialog';

/**
 * What the wizard says when the step-up did not succeed — and whether it lets the person try again.
 *
 * Every non-2xx used to read "Wrong password.", so a throttled attempt or a 500 accused the person
 * of mistyping a password that was right; and a request that never arrived threw straight past
 * `setBusy(false)`, leaving every button disabled with no message at all — the only way out was
 * reloading the page (P18 poz. 40). The 429 case is not hypothetical any more: step-up gained a
 * rate limit in P18 poz. 5, so "wait a moment" is now a routine answer that must not be dressed
 * up as a wrong password.
 */
describe('the deletion wizard when the step-up fails', () => {
  const original = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = original;
    vi.restoreAllMocks();
  });

  const open = () =>
    render(<DeleteAccountDialog token="t" onDeleted={() => {}} onClose={() => {}} />);

  const typePasswordAndSubmit = () => {
    fireEvent.change(screen.getByLabelText('your password'), { target: { value: 'right-one' } });
    fireEvent.click(screen.getByRole('button', { name: 'Delete my account' }));
  };

  it('tells a throttled person to wait instead of blaming their password', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(new Response('{}', { status: 429 }));
    open();

    typePasswordAndSubmit();

    await waitFor(() =>
      expect(screen.getByText('Too many attempts — wait a moment and try again.')).toBeTruthy());
    expect(screen.queryByText('Wrong password.')).toBeNull();
  });

  it('names a server fault as a server fault', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(new Response('{}', { status: 500 }));
    open();

    typePasswordAndSubmit();

    await waitFor(() =>
      expect(screen.getByText('Security answered 500. Please try again.')).toBeTruthy());
    expect(screen.queryByText('Wrong password.')).toBeNull();
  });

  it('stays usable when the request never arrives', async () => {
    globalThis.fetch = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'));
    open();

    typePasswordAndSubmit();

    await waitFor(() => expect(screen.getByText(/Could not reach the security service/)).toBeTruthy());
    // the button must be clickable again: a network blip cannot cost the person their wizard
    expect(screen.getByRole('button', { name: 'Delete my account' })).not.toBeDisabled();
  });
});

/**
 * What the wizard puts ON THE WIRE for each option (P18 poz. 18).
 *
 * The preselected option used to send `{}`. An empty choice map does not mean "the wizard's
 * default" to anybody downstream: identity's `PurgeChoices` documents it as "whatever each content
 * service's deployment default is", so the orchestrator leaves the `policy` field out of the fact
 * and the admin's runtime override decides the fate of the memes instead — silently overruling a
 * radio button labelled "delete my memes", while three javadocs promise the leaver's wish wins over
 * everything. A choice only outranks the override if it is actually stated, so every option states
 * one now.
 */
describe('the policy the wizard sends', () => {
  const original = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = original;
    vi.restoreAllMocks();
  });

  /** Answers ELEVATED to the step-up and 202 to the deletion, recording every call. */
  const stubSecurity = () => {
    const calls: Array<{ url: string; body: unknown }> = [];
    globalThis.fetch = vi.fn((input: unknown, init?: RequestInit) => {
      const url = String(input);
      calls.push({ url, body: init?.body === undefined ? undefined : JSON.parse(String(init.body)) });
      return Promise.resolve(url.endsWith('/account/delete')
        ? new Response('{}', { status: 202 })
        : new Response(JSON.stringify({ status: 'ELEVATED' }), { status: 200 }));
    }) as unknown as typeof globalThis.fetch;
    return calls;
  };

  const deleteCall = (calls: Array<{ url: string; body: unknown }>) =>
    calls.find((call) => call.url.endsWith('/account/delete'));

  const runWizard = async (pick?: string) => {
    const calls = stubSecurity();
    const deleted = vi.fn();
    render(<DeleteAccountDialog token="t" onDeleted={deleted} onClose={() => {}} />);
    if (pick) {
      fireEvent.click(screen.getByRole('radio', { name: new RegExp(pick) }));
    }
    fireEvent.change(screen.getByLabelText('your password'), { target: { value: 'right-one' } });
    fireEvent.click(screen.getByRole('button', { name: 'Delete my account' }));
    await waitFor(() => expect(deleted).toHaveBeenCalled());
    return calls;
  };

  it('spells out the recommended option instead of sending no preference at all', async () => {
    const calls = await runWizard();

    expect(deleteCall(calls)?.body).toEqual({ purge: { memes: 'DELETE', comments: 'ANONYMIZE_AUTHOR' } });
  });

  it('still spells out "burn it all"', async () => {
    expect(deleteCall(await runWizard('Burn it all'))?.body)
      .toEqual({ purge: { memes: 'DELETE', comments: 'DELETE' } });
  });

  it('still spells out the popularity rule, with its threshold', async () => {
    expect(deleteCall(await runWizard('Keep what the community liked'))?.body)
      .toEqual({ purge: { memes: 'KEEP_POPULAR_ANONYMIZED:100', comments: 'KEEP_POPULAR_ANONYMIZED:100' } });
  });
});
