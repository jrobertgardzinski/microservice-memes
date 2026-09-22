import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import MemeDialog from './MemeDialog';

/**
 * The dialog while a write of its own is ON THE WIRE, and what it says when one comes back refused.
 *
 * serviceDown.test.tsx covers the service that is not there at all. These are the other half: the
 * server ANSWERS — 403, 404, a slow 201 — and the dialog used to be unable to tell those answers
 * apart, or to wait for them. A button that stays live through its own request is not a cosmetic
 * detail here: the comment POST is not idempotent, and a vote is a TOGGLE, so the second press is
 * not a repeat of the first but its opposite.
 */

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

/** Everything the dialog reads on mount, answered emptily; writes are routed by the caller. */
const dialogFetch = (write: (url: string, init?: RequestInit) => Response | Promise<Response> | undefined) =>
  vi.fn((input: unknown, init?: RequestInit) => {
    const url = String(input);
    const answered = write(url, init);
    if (answered !== undefined) return Promise.resolve(answered);
    if (url.includes('/comments')) return Promise.resolve(json([]));
    if (url.endsWith('/tags')) return Promise.resolve(json([]));
    if (url.endsWith('/votes')) return Promise.resolve(json({ score: 0, myVote: null }));
    if (url.endsWith('/meta')) return Promise.resolve(json({ id: 'm-1', author: 'a***@example.com' }));
    return Promise.resolve(json({}, 404));
  }) as unknown as typeof globalThis.fetch;

const open = (isModerator = false) =>
  render(
    <MemeDialog
      memeId="m-1" token="t" isModerator={isModerator}
      onVoted={() => {}} onNsfwChanged={() => {}} onRequireSignIn={() => {}}
      onTagClick={() => {}} onDeleted={() => {}} onClose={() => {}}
    />,
  );

describe('posting a comment', () => {
  const original = globalThis.fetch;
  afterEach(() => { globalThis.fetch = original; vi.restoreAllMocks(); });

  it('cannot be pressed a second time while the first POST is still out', async () => {
    let posts = 0;
    globalThis.fetch = dialogFetch((url, init) => {
      if (url.includes('/comments') && init?.method === 'POST') {
        posts += 1;
        return new Promise<Response>(() => {});   // the slow link, held open for the whole test
      }
      return undefined;
    });
    open();

    fireEvent.change(await screen.findByPlaceholderText('add a comment…'),
      { target: { value: 'the same thing twice' } });
    const post = screen.getByRole('button', { name: 'Post' });
    fireEvent.click(post);
    await waitFor(() => expect(posts).toBe(1));
    fireEvent.click(post);
    await new Promise((resolve) => { setTimeout(resolve, 0); });

    // the guard inside submitComment read a flag nothing ever set, so the button stayed live and
    // the impatient second press put the same text in the thread again
    expect(posts).toBe(1);
    expect(post).toBeDisabled();
  });
});

describe('a moderator flagging NSFW', () => {
  const original = globalThis.fetch;
  afterEach(() => { globalThis.fetch = original; vi.restoreAllMocks(); });

  const flagAnswering = (status: number) => {
    globalThis.fetch = dialogFetch((url, init) =>
      (url.endsWith('/nsfw') && init?.method === 'PUT' ? json({ status: 'NOPE' }, status) : undefined));
  };

  it('is told the meme is gone when it is gone, not that they are not a moderator', async () => {
    const said = vi.spyOn(window, 'alert').mockImplementation(() => {});
    flagAnswering(404);
    open(true);

    fireEvent.click(await screen.findByRole('button', { name: 'Flag NSFW' }));

    await waitFor(() => expect(said).toHaveBeenCalledWith('This meme is no longer here.'));
  });

  it('still hears the one thing 403 actually means', async () => {
    const said = vi.spyOn(window, 'alert').mockImplementation(() => {});
    flagAnswering(403);
    open(true);

    fireEvent.click(await screen.findByRole('button', { name: 'Flag NSFW' }));

    await waitFor(() => expect(said).toHaveBeenCalledWith('Only a moderator may flag NSFW.'));
  });

  it('hands the verdict back, so the wall behind the dialog can stop showing the old one', async () => {
    const changed = vi.fn();
    globalThis.fetch = dialogFetch((url, init) =>
      (url.endsWith('/nsfw') && init?.method === 'PUT' ? json({ id: 'm-1', nsfw: true }) : undefined));
    render(
      <MemeDialog
        memeId="m-1" token="t" isModerator
        onVoted={() => {}} onNsfwChanged={changed} onRequireSignIn={() => {}}
        onTagClick={() => {}} onDeleted={() => {}} onClose={() => {}}
      />,
    );

    fireEvent.click(await screen.findByRole('button', { name: 'Flag NSFW' }));

    await waitFor(() => expect(changed).toHaveBeenCalledWith(true));
  });
});

describe('the vote arrows', () => {
  const original = globalThis.fetch;
  afterEach(() => { globalThis.fetch = original; vi.restoreAllMocks(); });

  it('go dead while the vote they sent is still out', async () => {
    globalThis.fetch = dialogFetch((url, init) =>
      (url.endsWith('/votes') && init?.method === 'POST' ? new Promise<Response>(() => {}) : undefined));
    open();

    const up = await screen.findByRole('button', { name: 'vote up' });
    fireEvent.click(up);

    // a vote is a TOGGLE: a second click is the opposite instruction, and two of them racing let
    // whichever answered last decide what the arrow means
    await waitFor(() => expect(up).toBeDisabled());
    expect(screen.getByRole('button', { name: 'vote down' })).toBeDisabled();
  });
});
