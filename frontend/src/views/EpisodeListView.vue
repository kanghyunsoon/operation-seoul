<template>
  <main class="episode-page">
    <header class="hero">
      <div>
        <p>CASE FILES</p>
        <h1 class="project-title">OPERATION: KOREA</h1>
        <span>{{ sessionStore.currentUser?.nickname || '요원' }}님, {{ selectedRegionLabel }} 미션 파일을 선택하세요.</span>
      </div>
      <div class="header-actions">
        <button type="button" class="admin-link primary" @click="router.push({ name: 'RegionMap' })">지역 다시 선택</button>
        <button type="button" class="admin-link danger" @click="logout">로그아웃</button>
        <template v-if="sessionStore.isAdmin">
          <button type="button" class="admin-link primary" @click="router.push({ name: 'AdminEpisodes' })">미션 파일 생성/관리</button>
          <button type="button" class="admin-link" @click="router.push({ name: 'AdminUsers' })">회원 관리</button>
          <button type="button" class="admin-link" @click="router.push({ name: 'AdminReviews' })">리뷰 관리</button>
        </template>
      </div>
    </header>

    <p v-if="message" class="toast" :class="messageType">{{ message }}</p>
    <section v-if="areaCode" class="region-filter">
      <strong>{{ selectedRegionLabel }}</strong>
      <span>선택 권역의 공개 미션 파일만 표시합니다.</span>
      <button type="button" @click="showAllEpisodes">전체 보기</button>
    </section>
    <section v-if="loading" class="state">미션 파일을 불러오는 중입니다.</section>
    <section v-else-if="error" class="state error">{{ error }}</section>
    <section v-else-if="!episodes.length" class="state">
      이 권역에 공개된 미션 파일이 아직 없습니다. 다른 지역을 선택하거나 전체 사건을 확인하세요.
    </section>
    <section v-else class="episode-list">
      <article v-for="episode in episodes" :key="episode.id" class="case-card" @click="openEpisode(episode.id)">
        <div v-if="episode.cleared || episode.progressStatus === 'CLEARED'" class="clear-stamp">
          <strong>COMPLETE</strong>
        </div>
        <div class="card-actions">
          <button
            type="button"
            class="icon-btn favorite-btn"
            :class="{ active: episode.favorited }"
            :disabled="favoriteBusyId === episode.id"
            :aria-label="episode.favorited ? '관심 에피소드에서 제거' : '관심 에피소드에 추가'"
            @click.stop="toggleFavorite(episode)"
          >
            {{ episode.favorited ? '♥' : '♡' }}
          </button>
          <button
            type="button"
            class="icon-btn ranking-btn"
            aria-label="미션 랭킹 보기"
            @click.stop="openRanking(episode)"
          >
            🏆
          </button>
        </div>
        <div class="stamp">CASE {{ String(episode.id).padStart(2, '0') }}</div>
        <h2>{{ episode.title }}</h2>
        <p>{{ episode.subtitle }}</p>
        <div class="meta">
          <span>{{ episode.era }}</span>
          <span>{{ episode.genre }}</span>
          <span>{{ episode.difficulty }}</span>
        </div>
        <button type="button" class="open-case-btn">미션 파일 열기</button>
      </article>
    </section>
    <nav v-if="!loading && !error && episodes.length" class="pagination" aria-label="에피소드 페이지">
      <button type="button" :disabled="pageOffset <= 0" aria-label="첫 페이지" @click="loadPage(0)">«</button>
      <button type="button" :disabled="pageOffset <= 0" aria-label="이전 페이지" @click="loadPage(pageOffset - pageSize)">‹</button>
      <button
        v-for="page in visiblePages"
        :key="`episode-page-${page}`"
        type="button"
        class="page-number"
        :class="{ active: page === currentPage }"
        @click="loadPage((page - 1) * pageSize)"
      >
        {{ page }}
      </button>
      <button type="button" :disabled="!hasMoreEpisodes" aria-label="다음 페이지" @click="loadPage(pageOffset + pageSize)">›</button>
      <button type="button" :disabled="!canJumpLastPage" aria-label="마지막 페이지" @click="loadPage((totalPages - 1) * pageSize)">»</button>
    </nav>
    <MainBottomNav />
    <aside class="ai-float">
      <button
        type="button"
        class="ai-bubble"
        aria-label="AI 플레이 분석 열기"
        @click="openAnalysisModal"
      >
        <span aria-hidden="true">AI</span>
      </button>
    </aside>
    <AiAnalysisModal
      v-if="analysisModalOpen"
      :analysis="analysis"
      :loading="analysisLoading"
      :error="analysisError"
      @close="analysisModalOpen = false"
    />
  </main>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import MainBottomNav from '@/components/MainBottomNav.vue';
import AiAnalysisModal from '@/components/AiAnalysisModal.vue';
import { useSessionStore } from '@/stores/sessionStore';
import { episodeApi } from '@/api/episodeApi';
import { favoriteApi } from '@/api/favoriteApi';
import { playerAnalysisApi } from '@/api/playerAnalysisApi';
import { regionLabel } from '@/constants/regionAreas.js';

const router = useRouter();
const route = useRoute();
const sessionStore = useSessionStore();
const episodes = ref([]);
const loading = ref(true);
const error = ref('');
const message = ref('');
const messageType = ref('success');
const favoriteBusyId = ref(null);
const analysisModalOpen = ref(false);
const analysisLoading = ref(false);
const analysisError = ref('');
const analysis = ref(null);
const pageSize = 6;
const pageOffset = ref(0);
const hasMoreEpisodes = ref(false);
const totalEpisodeCount = ref(null);
const areaCode = computed(() => route.query.areaCode || '');
const selectedRegionLabel = computed(() => areaCode.value ? regionLabel(areaCode.value) : '전체 권역');
const currentPage = computed(() => Math.floor(pageOffset.value / pageSize) + 1);
const totalPages = computed(() => totalEpisodeCount.value == null ? null : Math.max(1, Math.ceil(totalEpisodeCount.value / pageSize)));
const canJumpLastPage = computed(() => totalPages.value != null && currentPage.value < totalPages.value);
const visiblePages = computed(() => {
  if (totalPages.value == null) {
    const knownLastPage = currentPage.value + (hasMoreEpisodes.value ? 1 : 0);
    const start = Math.max(1, knownLastPage - 9);
    return Array.from({ length: knownLastPage - start + 1 }, (_, index) => start + index);
  }
  const total = totalPages.value;
  const start = Math.max(1, Math.min(currentPage.value - 4, total - 9));
  const end = Math.min(total, start + 9);
  return Array.from({ length: end - start + 1 }, (_, index) => start + index);
});

onMounted(loadEpisodes);
watch(() => route.query.areaCode, () => loadPage(0));

async function loadEpisodes() {
  return loadPage(pageOffset.value);
}

async function loadPage(offset = 0) {
  loading.value = true;
  error.value = '';
  pageOffset.value = Math.max(0, offset);
  try {
    const response = await episodeApi.listEpisodes({
      ...(areaCode.value ? { areaCode: areaCode.value } : {}),
      limit: pageSize,
      offset: pageOffset.value
    });
    const page = Array.isArray(response)
      ? {
        items: response.slice(pageOffset.value, pageOffset.value + pageSize),
        hasMore: pageOffset.value + pageSize < response.length,
        totalCount: response.length
      }
      : response;
    const pageItems = Array.isArray(page.items) ? page.items : [];
    episodes.value = pageItems.slice(0, pageSize);
    hasMoreEpisodes.value = Boolean(page.hasMore || pageItems.length > pageSize);
    const parsedTotalCount = Number(page.totalCount);
    totalEpisodeCount.value = Number.isFinite(parsedTotalCount) ? parsedTotalCount : null;
    window.scrollTo({ top: 0, behavior: 'smooth' });
  } catch (err) {
    error.value = err.userMessage || '에피소드 목록을 불러오지 못했습니다.';
  } finally {
    loading.value = false;
  }
}

const openEpisode = (episodeId) => router.push({
  name: 'EpisodeDetail',
  params: { episodeId },
  query: areaCode.value ? { areaCode: areaCode.value } : {}
});
const openRanking = (episode) => router.push({ name: 'Ranking', query: { episodeId: episode.id, title: episode.title } });

function logout() {
  sessionStore.logout();
  router.push({ name: 'Intro' });
}

function showAllEpisodes() {
  pageOffset.value = 0;
  router.push({ name: 'EpisodeList' });
}

async function toggleFavorite(episode) {
  favoriteBusyId.value = episode.id;
  try {
    if (episode.favorited) {
      await favoriteApi.removeFavorite(episode.id);
      episode.favorited = false;
      setMessage('관심 에피소드에서 제거했습니다.');
    } else {
      await favoriteApi.addFavorite(episode.id);
      episode.favorited = true;
      setMessage('관심 에피소드에 추가했습니다.');
    }
  } catch (err) {
    setMessage(err.userMessage || '관심 에피소드 상태를 변경하지 못했습니다.', 'error');
  } finally {
    favoriteBusyId.value = null;
  }
}

function setMessage(text, type = 'success') {
  message.value = text;
  messageType.value = type;
}

async function openAnalysisModal() {
  analysisModalOpen.value = true;
  analysisLoading.value = true;
  analysisError.value = '';
  analysis.value = null;
  try {
    analysis.value = await playerAnalysisApi.getLatest(sessionStore.userId);
  } catch (err) {
    analysisError.value = err.userMessage || 'AI 분석 결과를 불러오지 못했습니다.';
  } finally {
    analysisLoading.value = false;
  }
}
</script>

<style scoped>
.episode-page { height: 100dvh; box-sizing: border-box; display: flex; flex-direction: column; gap: 14px; padding: 14px 16px calc(110px + env(safe-area-inset-bottom)); overflow: hidden; background: transparent; color: #f3f6fa; font-family: 'Noto Sans KR', system-ui, sans-serif; }
.hero { flex: 0 0 auto; width: min(100%, 980px); box-sizing: border-box; margin: 0 auto; padding: 16px 18px; border: 1px solid rgba(36,50,71,.94); border-radius: 10px; background: rgba(12,23,38,.88); display: flex; align-items: flex-end; justify-content: space-between; gap: 14px; }
.hero p { margin: 0 0 5px; color: #9f8a62; font-size: .68rem; font-weight: 900; letter-spacing: .16em; }
h1 { margin: 0; font-size: clamp(1.7rem, 5.2vw, 2.8rem); line-height: .95; }
.hero span { display: block; margin-top: 7px; color: #cbd5e1; font-size: .9rem; }
.episode-list { flex: 1 1 auto; min-height: 0; width: min(100%, 980px); margin: 0 auto; display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); grid-template-rows: repeat(3, minmax(0, 1fr)); gap: 12px; }
.case-card { position: relative; overflow: hidden; min-height: 0; padding: 20px 24px 18px; border: 1px solid rgba(36,50,71,.94); border-radius: 8px; background: linear-gradient(145deg, rgba(12,23,38,.94), rgba(8,17,30,.94)); box-shadow: 0 10px 24px rgba(0,0,0,.22); cursor: pointer; }
.case-card:before { content: ''; position: absolute; inset: 0 auto 0 0; width: 2px; background: #6f552b; opacity: .42; }
.clear-stamp {
  position: absolute;
  right: 16px;
  bottom: 12px;
  z-index: 1;
  width: 132px;
  height: 54px;
  display: grid;
  place-items: center;
  border: 3px solid rgba(220,38,38,.9);
  border-radius: 4px;
  color: rgba(239,68,68,.96);
  background:
    repeating-linear-gradient(105deg, rgba(220,38,38,.18) 0 1px, transparent 1px 9px),
    rgba(127,29,29,.04);
  box-shadow: inset 0 0 0 2px rgba(239,68,68,.28);
  font-family: 'Noto Sans KR', system-ui, sans-serif;
  text-align: center;
  transform: rotate(-4deg);
  pointer-events: none;
  opacity: .9;
}
.clear-stamp::before {
  content: '';
  position: absolute;
  inset: 5px;
  border: 1px solid rgba(239,68,68,.58);
}
.clear-stamp::after {
  content: '';
  position: absolute;
  inset: 0;
  background: radial-gradient(circle at 20% 35%, transparent 0 2px, rgba(15,23,42,.18) 2px 4px, transparent 4px 18px);
  opacity: .42;
  mix-blend-mode: multiply;
}
.clear-stamp span,
.clear-stamp strong {
  position: relative;
  z-index: 1;
  display: block;
  line-height: 1;
  font-weight: 1000;
  letter-spacing: .04em;
}
.clear-stamp span { font-size: .78rem; }
.clear-stamp strong { font-size: 1.08rem; }
.card-actions { position: absolute; top: 10px; right: 10px; z-index: 2; display: flex; gap: 6px; }
.icon-btn { width: 36px; min-height: 36px; border: 1px solid rgba(36,50,71,.94); border-radius: 8px; background: rgba(8,17,30,.9); color: #a7b2c3; font-size: 1rem; line-height: 1; padding: 0; }
.favorite-btn.active { background: rgba(184,135,59,.14); color: #d1bd8f; border-color: rgba(184,135,59,.5); }
.favorite-btn:disabled { opacity: .6; cursor: wait; }
.ranking-btn { background: rgba(8,17,30,.9); }
.stamp { display: inline-block; transform: none; border: 1px solid rgba(143,106,50,.44); padding: 4px 8px; color: #b9a476; background: rgba(12,23,38,.72); font-weight: 900; font-size: .78rem; letter-spacing: .08em; }
h2 { margin: 12px 0 8px; font-size: 1.38rem; padding-right: 74px; line-height: 1.22; word-break: keep-all; }
.case-card p { margin: 0; color: #c8d1df; font-size: 1rem; line-height: 1.42; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.meta { display: flex; flex-wrap: wrap; gap: 7px; margin: 14px 0 14px; }
.meta span { border: 1px solid rgba(143,106,50,.38); border-radius: 999px; padding: 5px 10px; color: #b9a476; background: rgba(12,23,38,.72); font-size: .84rem; font-weight: 800; }
button { min-height: 40px; border: 1px solid rgba(36,50,71,.94); border-radius: 8px; background: rgba(8,17,30,.9); color: #f3f6fa; font: inherit; font-weight: 900; padding: 0 14px; }
.open-case-btn {
  background: #b8873b;
  border-color: rgba(184,135,59,.68);
  color: #050a12;
}
.header-actions { display: flex; flex-wrap: wrap; gap: 8px; }
.admin-link { flex: 0 0 auto; background: #334155; }
.admin-link.primary { background: #b8873b; color: #050a12; border-color: rgba(184,135,59,.68); }
.admin-link.danger { background: rgba(110,47,52,.18); color: #f2c7ca; border-color: #9a4a50; }
.state, .toast { flex: 0 0 auto; width: min(100%, 1040px); box-sizing: border-box; margin: 0 auto; padding: 10px 14px; border: 1px dashed rgba(148,163,184,.35); border-radius: 12px; color: #cbd5e1; text-align: center; }
.toast { border-style: solid; background: rgba(12,23,38,.72); color: #a7b2c3; }
.toast.error, .state.error { color: #fecaca; background: rgba(127,29,29,.18); }
.region-filter { flex: 0 0 auto; width: min(100%, 1040px); box-sizing: border-box; margin: 0 auto; padding: 9px 12px; border: 1px solid rgba(36,50,71,.94); border-radius: 12px; background: rgba(12,23,38,.72); display: flex; align-items: center; gap: 10px; color: #cbd5e1; font-size: .9rem; }
.region-filter strong { color: #b9a476; }
.region-filter button { margin-left: auto; min-height: 36px; background: rgba(8,17,30,.9); }
.pagination { flex: 0 0 auto; width: min(100%, 980px); margin: 2px auto 8px; display: flex; flex-wrap: wrap; align-items: center; justify-content: center; gap: 7px; }
.pagination button { min-width: 32px; width: 32px; min-height: 32px; padding: 0; border-radius: 4px; border: 1px solid rgba(148,163,184,.34); background: rgba(15,23,42,.8); color: #cbd5e1; }
.pagination .page-number.active { border-color: rgba(184,135,59,.5); background: rgba(184,135,59,.16); color: #f3f6fa; }
.pagination button:disabled { opacity: .42; cursor: not-allowed; }
:deep(.main-bottom-nav) {
  bottom: max(18px, calc(env(safe-area-inset-bottom) + 18px));
  width: min(720px, calc(100vw - 32px));
}
.ai-float {
  position: fixed;
  right: max(48px, calc(env(safe-area-inset-right) + 48px));
  bottom: max(118px, calc(env(safe-area-inset-bottom) + 118px));
  z-index: 40;
}
.ai-bubble {
  width: 58px;
  min-height: 58px;
  border-radius: 50%;
  padding: 0;
  border: 1px solid rgba(184,135,59,.5);
  background: #b8873b;
  color: #050a12;
  box-shadow: 0 18px 42px rgba(0,0,0,.34);
  font-size: 1rem;
  letter-spacing: 0;
}
.ai-bubble span {
  display: grid;
  place-items: center;
  width: 100%;
  height: 100%;
  font-weight: 1000;
}
@media (max-width: 560px) {
  .episode-page { gap: 10px; padding: 12px 12px calc(102px + env(safe-area-inset-bottom)); }
  .hero { flex-direction: column; align-items: stretch; }
  .header-actions { display: grid; grid-template-columns: 1fr; }
  .episode-list { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
  .case-card { min-height: 112px; padding: 10px; }
  .case-card p, .meta { display: none; }
  .case-card button:not(.icon-btn) { min-height: 30px; font-size: .76rem; padding: 0 8px; }
  h2 { margin: 8px 0; font-size: .88rem; padding-right: 32px; }
  .stamp { font-size: .54rem; padding: 2px 5px; }
  .icon-btn { width: 30px; min-height: 30px; font-size: .9rem; }
  .clear-stamp { right: 10px; bottom: 9px; width: 104px; height: 42px; }
  .clear-stamp span { font-size: .68rem; }
  .clear-stamp strong { font-size: .78rem; }
  .region-filter { display: grid; }
  .region-filter button { margin-left: 0; }
  .pagination {
    margin-bottom: 8px;
    gap: 8px;
  }
  :deep(.main-bottom-nav) {
    bottom: max(14px, calc(env(safe-area-inset-bottom) + 14px));
    width: calc(100vw - 24px);
  }
  .ai-float {
    right: max(24px, calc(env(safe-area-inset-right) + 24px));
    bottom: max(112px, calc(env(safe-area-inset-bottom) + 112px));
  }
  .ai-bubble { width: 54px; min-height: 54px; font-size: 1.45rem; }
}
</style>
