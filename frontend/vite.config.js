import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

export default defineConfig({
  plugins: [
    vue(),
    vueDevTools(),
  ],
  resolve: {
    alias: {
      // src 하위 파일을 @/ 형태로 import하기 위한 경로 별칭입니다.
      '@': fileURLToPath(new URL('./src', import.meta.url))
    },
  },
})
