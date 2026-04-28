import { createRouter, createWebHistory } from 'vue-router';
// 1. 뷰 파일들 가져오기
import IntroView from '@/views/IntroView.vue';
import HomeView from '@/views/HomeView.vue';
import BriefingView from '@/views/BriefingView.vue';
import MapView from '@/views/MapView.vue';
import AiChatView from '@/views/AiChatView.vue';

// 2. 세션 스토어 가져오기 (⚠️ 폴더명 stores 확인 완료)
import { useSessionStore } from '@/stores/sessionStore.js';

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
    path: '/briefing',
    name: 'Briefing',
    component: BriefingView,
    meta: { requiresAuth: true }, // 로그인 필수
    props: route => ({ missionId: route.query.missionId }) // 쿼리 파라미터를 컴포넌트 소품(props)으로 전달
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
  }
];



const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes
});

// src/router/index.js 내부 하단

router.beforeEach((to, from) => {
  const sessionStore = useSessionStore();

  if (to.meta.requiresAuth && !sessionStore.isLoggedIn) {
    alert('로그인이 필요한 서비스입니다.');
    return { name: 'Intro' }; // 👈 next() 대신 return 사용
  } else if (to.name === 'Intro' && sessionStore.isLoggedIn) {
    // 로그인 상태에서 인트로 접근 시 무조건 'Home'으로!
    return { name: 'Home' }; // 👈 next() 대신 return 사용
  }

  return true; // 👈 그 외의 경우는 정상적인 이동 허용
});

export default router;