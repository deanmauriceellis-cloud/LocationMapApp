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
    // S205: HMR client opens its WebSocket to whatever host the browser
    // loaded the page from (window.location.host) — works for localhost
    // dev AND LAN access at http://10.0.0.229:4302/. The earlier explicit
    // `host: 'localhost'` blocked LAN access because the LAN browser tried
    // to dial its own localhost. clientPort kept so HMR survives any future
    // reverse-proxy that maps the page port ≠ the WS port.
    hmr: {
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
