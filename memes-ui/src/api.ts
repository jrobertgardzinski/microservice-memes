/** Talking to the two services: memes (same origin) and security (CORS, token issuer). */

export const SECURITY: string =
  import.meta.env.VITE_SECURITY_URL ?? 'http://localhost:8080';

/** Comments live in their own microservice. */
export const COMMENTS: string =
  import.meta.env.VITE_COMMENTS_URL ?? 'http://localhost:8085';

export interface MemeRef {
  id: string;
  /** the moderators' judgement — the gallery blurs flagged memes until deliberately revealed */
  nsfw?: boolean;
}

export interface HotEntry {
  memeId: string;
  score: number;
}

export interface MemeComment {
  id: string;
  author: string;
  text: string;
  score: number;
  myVote: VoteDirection | null;
}

export type VoteDirection = 'UP' | 'DOWN';

/** A target's score plus the caller's own current vote (null = not voted). */
export interface VoteTally {
  score: number;
  myVote: VoteDirection | null;
}

/** A user-facing outcome message; `items` carries policy violations from a 422. */
export interface Notice {
  tone: 'success' | 'warning';
  text: string;
  items?: string[];
}

export const jsonHeaders = { 'Content-Type': 'application/json' };

export const authHeader = (token: string | null): Record<string, string> =>
  token ? { Authorization: `Bearer ${token}` } : {};

export const listMemes = async (tag?: string): Promise<MemeRef[]> =>
  (await fetch(tag ? `/memes?tag=${encodeURIComponent(tag)}` : '/memes')).json();

/** A meme's public metadata: who uploaded it. */
export const memeMeta = async (memeId: string): Promise<{ id: string; author: string; nsfw?: boolean }> =>
  (await fetch(`/memes/${memeId}/meta`)).json();

/** Flag or unflag a meme NSFW — a moderator-only call; the backend is the authority. */
export const setMemeNsfw = async (memeId: string, nsfw: boolean, token: string | null): Promise<boolean> =>
  (await fetch(`/memes/${memeId}/nsfw`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', ...authHeader(token) },
    body: JSON.stringify({ nsfw }),
  })).ok;

/** The tags an uploader has put on a meme (sorted). */
export const memeTags = async (memeId: string): Promise<string[]> =>
  (await fetch(`/memes/${memeId}/tags`)).json();

/** Replace a meme's whole tag set (author only). Returns the accepted tags, or a status on refusal. */
export const setMemeTags = async (
  memeId: string, tags: string[], token: string | null,
): Promise<{ ok: boolean; status?: string; tags?: string[] }> => {
  const r = await fetch(`/memes/${memeId}/tags`, {
    method: 'POST',
    headers: { ...jsonHeaders, ...authHeader(token) },
    body: JSON.stringify({ tags }),
  });
  const body = await r.json().catch(() => ({}));
  return r.ok ? { ok: true, tags: body.tags } : { ok: false, status: body.status ?? String(r.status) };
};

export const hotMemes = async (): Promise<HotEntry[]> =>
  (await fetch('/memes/hot')).json();

export const listComments = async (memeId: string, token: string | null): Promise<MemeComment[]> =>
  (await fetch(`${COMMENTS}/memes/${memeId}/comments`, { headers: authHeader(token) })).json();

export const memeTally = async (memeId: string, token: string | null): Promise<VoteTally> =>
  (await fetch(`/memes/${memeId}/votes`, { headers: authHeader(token) })).json();

/** Delete a meme — the author (of their own) or a moderator (of anyone's). Server-authorised. */
export const deleteMeme = async (memeId: string, token: string | null): Promise<boolean> =>
  (await fetch(`/memes/${memeId}`, { method: 'DELETE', headers: authHeader(token) })).ok;

/** Delete a comment — its author, or a moderator. Server-authorised. */
export const deleteComment = async (
  memeId: string, commentId: string, token: string | null,
): Promise<boolean> =>
  (await fetch(`${COMMENTS}/memes/${memeId}/comments/${commentId}`,
    { method: 'DELETE', headers: authHeader(token) })).ok;
