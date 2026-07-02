/** Talking to the two services: memes (same origin) and security (CORS, token issuer). */

export const SECURITY: string =
  import.meta.env.VITE_SECURITY_URL ?? 'http://localhost:8080';

export interface MemeRef {
  id: string;
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

export const listMemes = async (): Promise<MemeRef[]> =>
  (await fetch('/memes')).json();

export const hotMemes = async (): Promise<HotEntry[]> =>
  (await fetch('/memes/hot')).json();

export const listComments = async (memeId: string, token: string | null): Promise<MemeComment[]> =>
  (await fetch(`/memes/${memeId}/comments`, { headers: authHeader(token) })).json();

export const memeTally = async (memeId: string, token: string | null): Promise<VoteTally> =>
  (await fetch(`/memes/${memeId}/votes`, { headers: authHeader(token) })).json();
