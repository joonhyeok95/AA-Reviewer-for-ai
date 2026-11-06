import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

/**
 * 화면은 항상 Gateway(9909) 경유로 접속한다. (http://localhost:9909/)
 *  - Gateway 라우트: /cm/** → API(9091), 그 외 /** → 이 개발 서버(5173)
 *  - 화면과 API 가 같은 Origin 이라 SESSION / XSRF-TOKEN 쿠키를 그대로 쓴다. (CORS·프록시 불필요)
 *  - 5173 에 직접 접속하면 API 호출(/cm)과 로그인 redirect 가 동작하지 않는다.
 */
/** 운영(nginx/default.conf)과 같은 CSP - npm run preview 로 빌드 결과를 같은 조건에서 확인 */
const CSP = "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
  + "img-src 'self' data: https:; font-src 'self' data:; connect-src 'self'; frame-src https:; "
  + "frame-ancestors 'self'; base-uri 'self'; form-action 'self'"

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    host: '127.0.0.1',
    port: 5173,
    strictPort: true,
  },
  preview: {
    host: '127.0.0.1',
    port: 5173,
    strictPort: true,
    headers: { 'Content-Security-Policy': CSP },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    chunkSizeWarningLimit: 1200,
  },
  test: {
    environment: 'jsdom',
    include: ['src/**/*.spec.ts'],
  },
})
