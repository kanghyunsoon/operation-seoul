<template>
  <main class="episode-page">
    <header class="hero">
      <div>
        <p>CASE FILES</p>
        <h1>Operation Korea</h1>
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
          <span>MISSION</span>
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
        <button type="button">미션 파일 열기</button>
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
    <aside class="ai-float" :class="{ open: aiAssistantOpen }">
      <div v-if="aiAssistantOpen" class="ai-panel" role="dialog" aria-label="AI 도우미">
        <div class="ai-panel-head">
          <strong>AI 도우미</strong>
          <button type="button" class="ai-close" aria-label="AI 도우미 닫기" @click="aiAssistantOpen = false">×</button>
        </div>
        <button type="button" class="ai-panel-action primary" @click="openAiRoute('Recommendations')">
          <span>AI 추천</span>
          <small>다음 사건 파일을 추천받기</small>
        </button>
        <button type="button" class="ai-panel-action" @click="openAiRoute('Coaching')">
          <span>AI 코칭</span>
          <small>내 플레이 성향 분석 보기</small>
        </button>
      </div>
      <button
        type="button"
        class="ai-bubble"
        :aria-expanded="aiAssistantOpen"
        aria-label="AI 도우미 열기"
        @click="aiAssistantOpen = !aiAssistantOpen"
      >
        <span>AI</span>
      </button>
    </aside>
  </main>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import MainBottomNav from '@/components/MainBottomNav.vue';
import { useSessionStore } from '@/stores/sessionStore';
import { episodeApi } from '@/api/episodeApi';
import { favoriteApi } from '@/api/favoriteApi';
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
const aiAssistantOpen = ref(false);
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
    return Array.from({ length: currentPage.value + (hasMoreEpisodes.value ? 1 : 0) }, (_, index) => index + 1);
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
    totalEpisodeCount.value = Number.isFinite(page.totalCount) ? page.totalCount : null;
    window.scrollTo({ top: 0, behavior: 'smooth' });
  } catch (err) {
    error.value = err.userMessage || '에피소드 목록을 불러오지 못했습니다.';
  } finally {
    loading.value = false;
  }
}

const openEpisode = (episodeId) => router.push({ name: 'EpisodeDetail', params: { episodeId } });
const openRanking = (episode) => router.push({ name: 'Ranking', query: { episodeId: episode.id, title: episode.title } });

function logout() {
  sessionStore.logout();
  router.push({ name: 'Intro' });
}

function showAllEpisodes() {
  pageOffset.value = 0;
  router.push({ name: 'EpisodeList' });
}

function openAiRoute(name) {
  aiAssistantOpen.value = false;
  router.push({ name });
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
</script>

<style scoped>
.episode-page { min-height: 100vh; box-sizing: border-box; padding: 28px 16px 126px; background: radial-gradient(circle at 15% 10%, rgba(180,83,9,.25), transparent 32%), linear-gradient(160deg, #17110b, #111827 58%, #050505); color: #f8fafc; font-family: Georgia, 'Noto Sans KR', serif; }
.hero { width: min(100%, 880px); margin: 0 auto 24px; padding: 22px; border: 1px solid rgba(245,158,11,.26); border-radius: 20px; background: rgba(15,23,42,.54); display: flex; align-items: flex-end; justify-content: space-between; gap: 14px; }
.hero p { margin: 0 0 8px; color: #f59e0b; font-size: .78rem; font-weight: 900; letter-spacing: .16em; }
h1 { margin: 0; font-size: clamp(2rem, 9vw, 4rem); line-height: 1; }
.hero span { display: block; margin-top: 12px; color: #cbd5e1; }
.episode-list { width: min(100%, 880px); margin: 0 auto; display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
.case-card { position: relative; overflow: hidden; min-height: 210px; padding: 20px; border: 1px solid rgba(248,250,252,.13); border-radius: 18px; background: linear-gradient(135deg, rgba(255,247,237,.1), rgba(15,23,42,.75)); box-shadow: 0 20px 46px rgba(0,0,0,.2); cursor: pointer; }
.case-card:before { content: ''; position: absolute; inset: 0 auto 0 0; width: 7px; background: #b45309; }
.clear-stamp {
  position: absolute;
  right: 18px;
  bottom: 18px;
  z-index: 1;
  width: 138px;
  height: 58px;
  display: grid;
  place-items: center;
  border: 4px solid rgba(220,38,38,.9);
  border-radius: 4px;
  color: rgba(239,68,68,.96);
  background:
    repeating-linear-gradient(105deg, rgba(220,38,38,.18) 0 1px, transparent 1px 9px),
    rgba(127,29,29,.04);
  box-shadow: inset 0 0 0 2px rgba(239,68,68,.28);
  font-family: Georgia, 'Times New Roman', serif;
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
  line-height: .9;
  font-weight: 1000;
  letter-spacing: .06em;
}
.clear-stamp span { font-size: 1.24rem; }
.clear-stamp strong { font-size: 1.08rem; }
.card-actions { position: absolute; top: 14px; right: 14px; z-index: 2; display: flex; gap: 7px; }
.icon-btn { width: 40px; min-height: 40px; border: 1px solid rgba(251,191,36,.35); border-radius: 999px; background: rgba(15,23,42,.78); color: #fde68a; font-size: 1.15rem; line-height: 1; padding: 0; }
.favorite-btn.active { background: #b45309; color: #fff7ed; border-color: #f59e0b; }
.favorite-btn:disabled { opacity: .6; cursor: wait; }
.ranking-btn { background: rgba(120,53,15,.88); }
.stamp { display: inline-block; transform: rotate(-2deg); border: 2px solid rgba(248,113,113,.75); padding: 4px 8px; color: #fca5a5; font-weight: 900; font-size: .68rem; }
h2 { margin: 14px 0 7px; font-size: 1.28rem; padding-right: 42px; line-height: 1.28; }
.case-card p { margin: 0; color: #cbd5e1; line-height: 1.55; }
.meta { display: flex; flex-wrap: wrap; gap: 6px; margin: 15px 0 16px; }
.meta span { border: 1px solid rgba(245,158,11,.28); border-radius: 999px; padding: 4px 8px; color: #fde68a; font-size: .72rem; }
button { min-height: 40px; border: 0; border-radius: 12px; background: #b45309; color: white; font: inherit; font-weight: 900; padding: 0 14px; }
.header-actions { display: flex; flex-wrap: wrap; gap: 8px; }
.admin-link { flex: 0 0 auto; background: #334155; }
.admin-link.primary { background: #b45309; }
.admin-link.danger { background: #7f1d1d; }
.state, .toast { width: min(100%, 880px); box-sizing: border-box; margin: 0 auto 14px; padding: 14px 18px; border: 1px dashed rgba(148,163,184,.35); border-radius: 16px; color: #cbd5e1; text-align: center; }
.toast { border-style: solid; background: rgba(22,101,52,.18); color: #bbf7d0; }
.toast.error, .state.error { color: #fecaca; background: rgba(127,29,29,.18); }
.region-filter { width: min(100%, 880px); box-sizing: border-box; margin: 0 auto 14px; padding: 12px 14px; border: 1px solid rgba(125,211,252,.24); border-radius: 16px; background: rgba(8,47,73,.28); display: flex; align-items: center; gap: 10px; color: #cbd5e1; }
.region-filter strong { color: #a5f3fc; }
.region-filter button { margin-left: auto; min-height: 36px; background: #0369a1; }
.pagination { width: min(100%, 880px); margin: 18px auto 0; display: flex; align-items: center; justify-content: center; gap: 5px; }
.pagination button { min-width: 36px; width: 36px; min-height: 36px; padding: 0; border-radius: 4px; border: 1px solid rgba(148,163,184,.34); background: rgba(15,23,42,.8); color: #cbd5e1; }
.pagination .page-number.active { border-color: #10b981; background: #059669; color: #fff; }
.pagination button:disabled { opacity: .42; cursor: not-allowed; }
.ai-float {
  position: fixed;
  right: max(18px, env(safe-area-inset-right));
  bottom: max(102px, calc(env(safe-area-inset-bottom) + 102px));
  z-index: 40;
  display: grid;
  justify-items: end;
  gap: 10px;
}
.ai-bubble {
  width: 64px;
  min-height: 64px;
  border-radius: 50%;
  padding: 0;
  border: 1px solid rgba(251,191,36,.58);
  background: linear-gradient(145deg, #f59e0b, #b45309);
  color: #fff7ed;
  box-shadow: 0 18px 42px rgba(0,0,0,.38), 0 0 0 6px rgba(245,158,11,.14);
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
.ai-panel {
  width: min(310px, calc(100vw - 32px));
  padding: 12px;
  border: 1px solid rgba(245,158,11,.3);
  border-radius: 8px;
  background: rgba(15,23,42,.96);
  box-shadow: 0 24px 70px rgba(0,0,0,.45);
  color: #f8fafc;
}
.ai-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
}
.ai-panel-head strong { font-size: .98rem; }
.ai-close {
  width: 30px;
  min-height: 30px;
  border-radius: 50%;
  padding: 0;
  background: rgba(51,65,85,.9);
  color: #e2e8f0;
  font-size: 1.15rem;
}
.ai-panel-action {
  width: 100%;
  min-height: auto;
  display: grid;
  gap: 3px;
  justify-items: start;
  padding: 12px;
  border-radius: 8px;
  background: rgba(51,65,85,.86);
  color: #e2e8f0;
  text-align: left;
}
.ai-panel-action + .ai-panel-action { margin-top: 8px; }
.ai-panel-action.primary { background: #b45309; color: #fff7ed; }
.ai-panel-action span { font-weight: 1000; }
.ai-panel-action small { color: rgba(226,232,240,.78); font-size: .78rem; line-height: 1.35; }
.ai-panel-action.primary small { color: rgba(255,247,237,.82); }
@media (max-width: 560px) {
  .hero { flex-direction: column; align-items: stretch; }
  .header-actions { display: grid; grid-template-columns: 1fr; }
  .episode-list { grid-template-columns: 1fr; }
  .case-card { min-height: 0; padding: 20px; }
  .clear-stamp { right: 16px; bottom: 16px; width: 124px; height: 52px; }
  .clear-stamp span { font-size: 1.08rem; }
  .clear-stamp strong { font-size: .96rem; }
  .region-filter { display: grid; }
  .region-filter button { margin-left: 0; }
  .pagination { gap: 8px; }
  .ai-float { right: 14px; bottom: max(96px, calc(env(safe-area-inset-bottom) + 96px)); }
  .ai-bubble { width: 58px; min-height: 58px; }
}
</style>
