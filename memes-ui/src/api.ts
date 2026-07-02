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

export const listComments = async (memeId: string): Promise<MemeComment[]> =>
  (await fetch(`/memes/${memeId}/comments`)).json();
