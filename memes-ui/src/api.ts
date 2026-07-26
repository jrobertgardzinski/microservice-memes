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

/** A meme id with its vote score — how the score reads answer on the wire. */
export interface ScoreEntry {
  memeId: string;
  score: number;
}

export interface MemeComment {
  id: string;
  /** masked in the public listing (a***@example.com) — display it, never compare it to an identity */
  author: string;
  /** null for a reader when a moderator has hidden the comment (the author still sees their own) */
  text: string | null;
  /** null when the backend has no tally to report — render "n/a", never a made-up 0 */
  score: number | null;
  myVote: VoteDirection | null;
  /** a moderator's soft flag: the comment shows as a tombstone rather than being deleted */
  hidden?: boolean;
  /** the server's word that the signed-in viewer wrote this — the ONLY basis for "your comment"
   *  affordances, since the masked author no longer equals anyone's e-mail (absent on older APIs) */
  own?: boolean;
}

export type VoteDirection = 'UP' | 'DOWN';

/** A target's score plus the caller's own current vote (null = not voted). */
export interface VoteTally {
  /** the JSON field is nullable on the wire (the backend builds it with map.put) — a null must
   *  surface as "n/a" in the UI, not silently coerce into a numeric-looking 0 */
  score: number | null;
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

/** The wall is served one page at a time; the server caps the size, so this is what a page is. */
export const GALLERY_PAGE_SIZE = 50;

/** How many memes one score call asks about; the server refuses more than a page of the wall, so a
 *  longer list (a well-stocked favourites collection) goes out as several calls. */
export const SCORE_BATCH_SIZE = GALLERY_PAGE_SIZE;

export const listMemes = async (tag?: string, page = 0): Promise<MemeRef[]> => {
  const query = new URLSearchParams({ page: String(page), size: String(GALLERY_PAGE_SIZE) });
  if (tag) query.set('tag', tag);
  return (await fetch(`/memes?${query}`)).json();
};

/** A meme's public metadata. The uploader comes back MASKED (a***@example.com) — display it,
 *  never compare it to an identity; `own` is the server's answer to "is this mine?", computed
 *  from the token this call carries (false for a signed-out visitor, who may still read). */
export const memeMeta = async (
  memeId: string, token: string | null,
): Promise<{ id: string; author: string; own?: boolean; nsfw?: boolean }> =>
  (await fetch(`/memes/${memeId}/meta`, { headers: authHeader(token) })).json();

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

/**
 * The scores of EXACTLY these memes, keyed by id — the read behind the numbers on the tiles.
 *
 * A meme missing from the returned map has an UNKNOWN score: the service had nothing to say about
 * that id (a favourite that outlived its meme), or a batch never got through. It is NOT a meme with
 * zero votes — an existing meme nobody voted on comes back with a real 0. That is why this answers
 * with a Map: `get` returns `undefined`, so a caller has to decide what "no tally" looks like
 * instead of indexing into an object and writing `?? 0`.
 *
 * The wall used to take its numbers from `/memes/hot`. That list is a RANKING capped at the hottest
 * hundred, so a lookup for anything below the cap found nothing — and `?? 0` printed "▲ 0" under
 * memes that had votes. Today's gallery is smaller than the cap, which is the only reason nobody
 * had seen it yet.
 */
export const memeScores = async (memeIds: string[]): Promise<Map<string, number>> => {
  const scores = new Map<string, number>();
  for (let from = 0; from < memeIds.length; from += SCORE_BATCH_SIZE) {
    const batch = memeIds.slice(from, from + SCORE_BATCH_SIZE);
    const r = await fetch(`/memes/scores?ids=${batch.map(encodeURIComponent).join(',')}`);
    // a batch that did not answer leaves those ids out of the map, which reads as "unknown" —
    // the one thing we must not do here is fill the gap with zeros
    if (!r.ok) continue;
    (await r.json() as ScoreEntry[]).forEach((entry) => scores.set(entry.memeId, entry.score));
  }
  return scores;
};

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

/** Hide or reveal a comment — a moderator-only soft touch (a tombstone, not a deletion). */
export const setCommentHidden = async (
  memeId: string, commentId: string, hidden: boolean, token: string | null,
): Promise<boolean> =>
  (await fetch(`${COMMENTS}/memes/${memeId}/comments/${commentId}/hidden`, {
    method: 'PUT',
    headers: { ...jsonHeaders, ...authHeader(token) },
    body: JSON.stringify({ hidden }),
  })).ok;

/** The purge-policy dial, admin-only: what happens to a leaver's memes unless their wizard says otherwise. */
export interface PurgePolicy {
  axis: string;
  effective: string;
  source: 'DB' | 'ENV';
  envDefault: string;
}

export const getPurgePolicy = async (token: string | null): Promise<PurgePolicy | null> => {
  const r = await fetch('/admin/purge-policy', { headers: authHeader(token) });
  return r.ok ? r.json() : null;
};

export const setPurgePolicy = async (
  rule: string, token: string | null,
): Promise<{ ok: boolean; detail?: string }> => {
  const r = await fetch('/admin/purge-policy', {
    method: 'PUT',
    headers: { ...jsonHeaders, ...authHeader(token) },
    body: JSON.stringify({ memes: rule }),
  });
  const body = await r.json().catch(() => ({}));
  return r.ok ? { ok: true } : { ok: false, detail: body.detail ?? body.status ?? String(r.status) };
};

export const clearPurgePolicy = async (token: string | null): Promise<boolean> =>
  (await fetch('/admin/purge-policy', { method: 'DELETE', headers: authHeader(token) })).ok;

/** Favourites live in microservice-user-collections: opaque refs, hydrated by THIS gallery. */
export const COLLECTIONS: string =
  import.meta.env.VITE_COLLECTIONS_URL ?? 'http://localhost:8092';

export interface FavouriteRef { itemType: string; itemId: string; }

/** The caller's saved refs, newest first. Throws on anything but 200 — callers degrade quietly. */
export const listFavourites = async (token: string): Promise<FavouriteRef[]> => {
  const r = await fetch(`${COLLECTIONS}/collections/favourites/items`, { headers: authHeader(token) });
  if (!r.ok) throw new Error(String(r.status));
  return r.json();
};

export const saveFavourite = async (memeId: string, token: string): Promise<boolean> =>
  (await fetch(`${COLLECTIONS}/collections/favourites/items/meme/${encodeURIComponent(memeId)}`,
    { method: 'PUT', headers: authHeader(token) })).ok;

export const removeFavourite = async (memeId: string, token: string): Promise<boolean> =>
  (await fetch(`${COLLECTIONS}/collections/favourites/items/meme/${encodeURIComponent(memeId)}`,
    { method: 'DELETE', headers: authHeader(token) })).ok;
