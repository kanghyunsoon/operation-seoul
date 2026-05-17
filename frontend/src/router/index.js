import { createRouter, createWebHistory } from 'vue-router';
import IntroView from '@/views/IntroView.vue';
import HomeView from '@/views/HomeView.vue';
import RegionDetailView from '@/views/RegionDetailView.vue';
import BriefingView from '@/views/BriefingView.vue';
import MapView from '@/views/MapView.vue';
import AiChatView from '@/views/AiChatView.vue';
import ClearView from '@/views/ClearView.vue';

import { useSessionStore } from '@/stores/sessionStore.js';

// 서비스의 전체 화면 흐름입니다.
// Intro -> Home -> Briefing -> Map -> Chat -> Clear 순서가 기본 게임 루프입니다.
const routes = [
  {
    path: '/',
    redirect: '/intro'
  },
  {
    path: '/intro',
    name: 'Intro',
    component: IntroView,
    meta: { requiresAuth: false }
  },
  {
    path: '/home',
    name: 'Home',
    component: HomeView,
    meta: { requiresAuth: true } // 로그인 필수
  },
  {
    path: '/regions/:regionId',
    name: 'RegionDetail',
    component: RegionDetailView,
    meta: { requiresAuth: true }
  },
  {
    path: '/briefing',
    name: 'Briefing',
    component: BriefingView,
    meta: { requiresAuth: true }, // 로그인 필수
    props: route => ({ missionId: route.query.missionId })
  },
  {
    path: '/map',
    name: 'Map',
    component: MapView,
    meta: { requiresAuth: true }
  },
  {
    path: '/chat/:sessionId',
    name: 'Chat',
    component: AiChatView,
    meta: { requiresAuth: true }
  },
  {
    path: '/clear/:missionId',
    name: 'Clear',
    component: ClearView,
    meta: { requiresAuth: true }
  }
];



const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes
});

// 로그인 필수 화면은 sessionStore의 토큰 존재 여부로 막습니다.
// 이미 로그인한 사용자가 Intro로 돌아오면 Home으로 돌려보내 UX 흐름을 단순화합니다.
router.beforeEach((to, from) => {
  const sessionStore = useSessionStore();

  if (to.meta.requiresAuth && !sessionStore.isLoggedIn) {
    alert('로그인이 필요한 서비스입니다.');
    return { name: 'Intro' };
  } else if (to.name === 'Intro' && sessionStore.isLoggedIn) {
    return { name: 'Home' };
  }

  return true;
});

export default router;
