// src/router/index.js 예시
import { createRouter, createWebHistory } from 'vue-router'
import MapView from '../views/MapView.vue'
import AiChatView from '../views/AiChatView.vue' // 임포트

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'Map',
      component: MapView
    },
    {
      // :sessionId 를 통해 몇 번 세션인지 파라미터로 받습니다
      path: '/chat/:sessionId',
      name: 'AiChat',
      component: AiChatView
    }
  ]
})

export default router