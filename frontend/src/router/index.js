import { createRouter, createWebHistory } from 'vue-router';
// 우리가 만든 뷰 파일들 가져오기
import IntroView from '@/views/IntroView.vue';
import BriefingView from '@/views/BriefingView.vue';
import MapView from '@/views/MapView.vue';
import AiChatView from '@/views/AiChatView.vue';

// 세션 스토어 가져오기 (확장자 .js 명시)
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
    meta: { requiresAuth: false } // 인증 필요 없음
  },
  {
    path: '/briefing',
    name: 'Briefing',
    component: BriefingView,
    meta: { requiresAuth: true } // 로그인 필요
  },
  {
    path: '/map',
    name: 'Map',
    component: MapView,
    meta: { requiresAuth: true } // 로그인 필요
  },
  {
    path: '/chat/:sessionId',
    name: 'Chat',
    component: AiChatView,
    meta: { requiresAuth: true } // 로그인 필요
  }
];

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes
});

// 전역 네비게이션 가드 설정
router.beforeEach((to, from, next) => {
  const sessionStore = useSessionStore();

  // 이동하려는 라우트가 인증을 요구하는지 확인
  if (to.meta.requiresAuth && !sessionStore.isLoggedIn) {
    // 인증이 필요한데 로그인이 안 되어 있다면 Intro(로그인) 화면으로 튕겨냄
    alert('로그인이 필요한 서비스입니다. 요원 인증을 진행해주세요.');
    next({ name: 'Intro' });
  }else if (to.name === 'Intro' && sessionStore.isLoggedIn) {
  // 이미 로그인된 상태면 브리핑 화면으로 강제 이동
  next({ name: 'Briefing' });
} else {
    // 그 외의 경우는 정상적으로 통과
    next();
  }
});

export default router;