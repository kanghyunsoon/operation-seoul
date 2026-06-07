import { createRouter, createWebHistory } from 'vue-router';
import IntroView from '@/views/IntroView.vue';
import EpisodeListView from '@/views/EpisodeListView.vue';
import EpisodeDetailView from '@/views/EpisodeDetailView.vue';
import EpisodeBriefingView from '@/views/EpisodeBriefingView.vue';
import EpisodeMapView from '@/views/EpisodeMapView.vue';
import EpisodeCaseFileView from '@/views/EpisodeCaseFileView.vue';
import FinalDeductionView from '@/views/FinalDeductionView.vue';
import ClearReportView from '@/views/ClearReportView.vue';
import MyPageView from '@/views/MyPageView.vue';
import AdminReviewsView from '@/views/AdminReviewsView.vue';
import AdminUsersView from '@/views/AdminUsersView.vue';
import AdminEpisodesView from '@/views/AdminEpisodesView.vue';
import { useSessionStore } from '@/stores/sessionStore.js';

const routes = [
  { path: '/', redirect: '/intro' },
  { path: '/intro', name: 'Intro', component: IntroView, meta: { requiresAuth: false } },
  { path: '/episodes', name: 'EpisodeList', component: EpisodeListView, meta: { requiresAuth: true } },
  { path: '/episodes/:episodeId', name: 'EpisodeDetail', component: EpisodeDetailView, meta: { requiresAuth: true } },
  { path: '/episodes/:episodeId/briefing', name: 'EpisodeBriefing', component: EpisodeBriefingView, meta: { requiresAuth: true } },
  { path: '/episodes/:episodeId/map', name: 'EpisodeMap', component: EpisodeMapView, meta: { requiresAuth: true } },
  { path: '/episodes/:episodeId/case-file', name: 'EpisodeCaseFile', component: EpisodeCaseFileView, meta: { requiresAuth: true } },
  { path: '/episodes/:episodeId/deduction', name: 'FinalDeduction', component: FinalDeductionView, meta: { requiresAuth: true } },
  { path: '/episodes/:episodeId/clear-report', name: 'EpisodeClearReport', component: ClearReportView, meta: { requiresAuth: true } },
  { path: '/me', name: 'MyPage', component: MyPageView, meta: { requiresAuth: true } },
  { path: '/admin/users', name: 'AdminUsers', component: AdminUsersView, meta: { requiresAuth: true, requiresAdmin: true } },
  { path: '/admin/reviews', name: 'AdminReviews', component: AdminReviewsView, meta: { requiresAuth: true, requiresAdmin: true } },
  { path: '/admin/episodes', name: 'AdminEpisodes', component: AdminEpisodesView, meta: { requiresAuth: true, requiresAdmin: true } },
  { path: '/home', redirect: '/episodes' },
  { path: '/regions/:regionId', redirect: '/episodes' },
  { path: '/briefing', redirect: '/episodes' },
  { path: '/map', redirect: '/episodes' },
  { path: '/chat/:sessionId', redirect: '/episodes' },
  { path: '/clear/:missionId', redirect: '/episodes' }
];

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes
});

router.beforeEach(async (to) => {
  const sessionStore = useSessionStore();
  if (to.meta.requiresAuth) {
    await sessionStore.ensureInitialized();
    if (!sessionStore.isLoggedIn) {
      return { name: 'Intro' };
    }
    if (to.meta.requiresAdmin && !sessionStore.isAdmin) {
      return { name: 'EpisodeList' };
    }
  }
  if (to.name === 'Intro' && sessionStore.isLoggedIn) {
    return { name: sessionStore.isAdmin ? 'AdminEpisodes' : 'EpisodeList' };
  }
  return true;
});

export default router;
