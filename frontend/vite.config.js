import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// The built files are placed in Spring Boot's static directory so the backend
// continues to serve the page at exactly the same root URL.
export default defineConfig({
  plugins: [vue()],
  base: './',
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8081',
    },
  },
  build: {
    outDir: '../src/main/resources/static',
    emptyOutDir: true,
  },
})
