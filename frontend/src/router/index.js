import { createRouter, createWebHistory } from 'vue-router';
// 우리가 만든 뷰 파일들 가져오기
import BriefingView from '../views/BriefingView.vue';
import MapView from '../views/MapView.vue';
import AiChatView from '../views/AiChatView.vue';

const routes = [
  {
    path: '/',
    name: 'Briefing',
    component: BriefingView
  },
  {
    path: '/map',
    name: 'Map',
    component: MapView
  },
  {
    path: '/chat/:sessionId', // 세션 ID를 파라미터로 받는 채팅창
    name: 'Chat',
    component: AiChatView
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

export default router;