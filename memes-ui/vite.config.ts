import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    // UI dev server against a running stack: API calls go to the real memes service
    // the e2e harness points this at its own memes instance; a human gets the stack's default
    proxy: { '/memes': process.env.MEMES_URL ?? 'http://localhost:8083' },
  },
});
