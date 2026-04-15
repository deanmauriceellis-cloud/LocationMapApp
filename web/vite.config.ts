import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    host: '0.0.0.0',
    port: 4302,
    // Explicit HMR websocket config — when the server binds to 0.0.0.0
    // Vite leaves the browser to guess how to reach the WS, which
    // fails in Firefox ("can't establish connection to ws://localhost:4302").
    // Pin it to the same host/port the browser uses to load the page.
    hmr: {
      host: 'localhost',
      protocol: 'ws',
      clientPort: 4302,
    },
    proxy: {
      // SalemIntelligence (admin editorial-AI bridge). Listed before the
      // generic /api rule so Vite's longest-match routes /api/intel/*
      // here. Gets us same-origin requests → no CORS preflight needed.
      // SI by default doesn't send Access-Control-Allow-Origin, which
      // broke the admin tool when served from http://10.0.0.229:4302.
      '/api/intel': {
        target: 'http://localhost:8089',
        changeOrigin: true,
      },
      // cache-proxy — LMA POI R/W + aux data services.
      '/api': {
        target: 'http://10.0.0.229:4300',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
    },
  },
})
