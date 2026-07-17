import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

const BACKEND_URL = process.env.VITE_BACKEND_URL || 'http://localhost:8081'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      '/codeanalyzer/server': {
        target: BACKEND_URL,
        changeOrigin: true
      }
    }
  }
})
