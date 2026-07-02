import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    // UI dev server against a running stack: API calls go to the real memes service
    proxy: { '/memes': 'http://localhost:8083' },
  },
});
