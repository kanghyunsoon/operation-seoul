import { createRouter, createWebHistory } from 'vue-router';
import IntroView from '@/views/IntroView.vue';
import RegionMapView from '@/views/RegionMapView.vue';
import RegionCommunityView from '@/views/RegionCommunityView.vue';
import RankingView from '@/views/RankingView.vue';
import ChallengesView from '@/views/ChallengesView.vue';
import RecommendationsView from '@/views/RecommendationsView.vue';
import CoachingView from '@/views/CoachingView.vue';
import PlansView from '@/views/PlansView.vue';
import GroupsView from '@/views/GroupsView.vue';
import EpisodeListView from '@/views/EpisodeListView.vue';
import EpisodeDetailView from '@/views/EpisodeDetailView.vue';
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
  { path: '/regions', name: 'RegionMap', component: RegionMapView, meta: { requiresAuth: true } },
  { path: '/regions/:regionId/community', name: 'RegionCommunity', component: RegionCommunityView, meta: { requiresAuth: true } },
  { path: '/rankings', name: 'Ranking', component: RankingView, meta: { requiresAuth: true } },
  { path: '/challenges', name: 'Challenges', component: ChallengesView, meta: { requiresAuth: true } },
  { path: '/recommendations', name: 'Recommendations', component: RecommendationsView, meta: { requiresAuth: true } },
  { path: '/coaching', name: 'Coaching', component: CoachingView, meta: { requiresAuth: true } },
  { path: '/plans', name: 'Plans', component: PlansView, meta: { requiresAuth: true } },
  { path: '/groups', name: 'Groups', component: GroupsView, meta: { requiresAuth: true } },
  { path: '/episodes', name: 'EpisodeList', component: EpisodeListView, meta: { requiresAuth: true } },
  { path: '/episodes/:episodeId', name: 'EpisodeDetail', component: EpisodeDetailView, meta: { requiresAuth: true } },
  { path: '/episodes/:episodeId/briefing', redirect: (to) => ({ name: 'EpisodeCaseFile', params: { episodeId: to.params.episodeId } }) },
  { path: '/episodes/:episodeId/map', name: 'EpisodeMap', component: EpisodeMapView, meta: { requiresAuth: true } },
  { path: '/episodes/:episodeId/case-file', name: 'EpisodeCaseFile', component: EpisodeCaseFileView, meta: { requiresAuth: true } },
  { path: '/episodes/:episodeId/deduction', name: 'FinalDeduction', component: FinalDeductionView, meta: { requiresAuth: true } },
  { path: '/episodes/:episodeId/clear-report', name: 'EpisodeClearReport', component: ClearReportView, meta: { requiresAuth: true } },
  { path: '/me', name: 'MyPage', component: MyPageView, meta: { requiresAuth: true } },
  { path: '/admin/users', name: 'AdminUsers', component: AdminUsersView, meta: { requiresAuth: true, requiresAdmin: true } },
  { path: '/admin/reviews', name: 'AdminReviews', component: AdminReviewsView, meta: { requiresAuth: true, requiresAdmin: true } },
  { path: '/admin/episodes', name: 'AdminEpisodes', component: AdminEpisodesView, meta: { requiresAuth: true, requiresAdmin: true } },
  { path: '/home', redirect: '/regions' },
  { path: '/regions/:regionId', redirect: (to) => ({ name: 'EpisodeList', query: { areaCode: to.params.regionId } }) },
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
    return { name: sessionStore.isAdmin ? 'AdminEpisodes' : 'RegionMap' };
  }
  return true;
});

export default router;
