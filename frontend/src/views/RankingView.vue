<template>
  <main class="ranking-page">
    <header class="hero">
      <button type="button" class="back-btn" @click="goBack">← 뒤로가기</button>
      <p>LEADERBOARD</p>
      <h1>{{ pageTitle }}</h1>
      <span>{{ pageDescription }}</span>
      <div class="hero-actions">
        <button type="button" @click="loadRankings">새로고침</button>
        <button v-if="isEpisodeRanking" type="button" class="ghost-btn" @click="showTotalRanking">전체 랭킹</button>
      </div>
    </header>

    <p v-if="message" class="toast error">{{ message }}</p>

    <section class="board-shell">
      <article class="panel player-board">
        <div class="panel-head">
          <div>
            <p>{{ isEpisodeRanking ? 'MISSION RANKING' : 'PLAYER TOTAL' }}</p>
            <h2>{{ boardTitle }}</h2>
          </div>
          <span>{{ rankings.length }}명</span>
        </div>
        <div v-if="loading" class="empty">랭킹을 불러오는 중입니다.</div>
        <div v-else-if="!rankings.length" class="empty">{{ emptyMessage }}</div>
        <ol v-else class="ranking-list player-list">
          <li
            v-for="entry in visibleRankings"
            :key="rankingKey(entry)"
            :class="{ mine: entry.userId === sessionStore.userId }"
          >
            <b>{{ entry.rankNo }}</b>
            <img :src="entry.profileImageUrl || defaultProfile" alt="" />
            <div>
              <strong>{{ entry.nickname || '요원' }}</strong>
              <span>{{ entrySubLabel(entry) }}</span>
            </div>
            <em>{{ entryScore(entry) }}점</em>
            <small>오답 {{ entry.wrongAnswerCount || 0 }} · 질문 {{ entry.deductionQuestionCount || 0 }} · 제출 {{ entry.finalGuessCount || 0 }}</small>
          </li>
        </ol>
        <nav v-if="!loading && totalPages > 1" class="pagination" aria-label="랭킹 페이지">
          <button type="button" :disabled="currentPage <= 1" aria-label="첫 페이지" @click="goPage(1)">«</button>
          <button type="button" :disabled="currentPage <= 1" aria-label="이전 페이지" @click="goPage(currentPage - 1)">‹</button>
          <button
            v-for="page in visiblePages"
            :key="`ranking-page-${page}`"
            type="button"
            class="page-number"
            :class="{ active: page === currentPage }"
            @click="goPage(page)"
          >
            {{ page }}
          </button>
          <button type="button" :disabled="currentPage >= totalPages" aria-label="다음 페이지" @click="goPage(currentPage + 1)">›</button>
          <button type="button" :disabled="currentPage >= totalPages" aria-label="마지막 페이지" @click="goPage(totalPages)">»</button>
        </nav>
      </article>
    </section>
  </main>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { rankingApi } from '@/api/rankingApi';
import { useSessionStore } from '@/stores/sessionStore';

const route = useRoute();
const router = useRouter();
const sessionStore = useSessionStore();
const rankings = ref([]);
const loading = ref(true);
const message = ref('');
const pageSize = 6;
const currentPage = ref(1);
const defaultProfile = 'data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 width=%2264%22 height=%2264%22 viewBox=%220 0 64 64%22%3E%3Crect width=%2264%22 height=%2264%22 rx=%2218%22 fill=%22%231e293b%22/%3E%3Ccircle cx=%2232%22 cy=%2225%22 r=%2211%22 fill=%22%23fde68a%22/%3E%3Cpath d=%22M14 56c3-12 12-18 18-18s15 6 18 18%22 fill=%22%23fde68a%22/%3E%3C/svg%3E';

const episodeId = computed(() => route.query.episodeId || null);
const episodeTitle = computed(() => String(route.query.title || '').trim());
const isEpisodeRanking = computed(() => Boolean(episodeId.value));
const pageTitle = computed(() => (isEpisodeRanking.value ? '미션 랭킹' : '랭킹'));
const boardTitle = computed(() => (isEpisodeRanking.value ? episodeTitle.value || '선택한 미션 랭킹' : '플레이어 전체 랭킹'));
const pageDescription = computed(() => (
  isEpisodeRanking.value
    ? '선택한 미션을 클리어한 플레이어의 점수 순위를 확인합니다.'
    : '전체 누적 점수를 기준으로 플레이어 순위를 확인합니다.'
));
const emptyMessage = computed(() => (
  isEpisodeRanking.value ? '아직 이 미션의 클리어 기록이 없습니다.' : '아직 합산 랭킹 기록이 없습니다.'
));
const totalPages = computed(() => Math.max(1, Math.ceil(rankings.value.length / pageSize)));
const visibleRankings = computed(() => {
  const start = (currentPage.value - 1) * pageSize;
  return rankings.value.slice(start, start + pageSize);
});
const visiblePages = computed(() => {
  const total = totalPages.value;
  const start = Math.max(1, Math.min(currentPage.value - 2, total - 4));
  const end = Math.min(total, start + 4);
  return Array.from({ length: end - start + 1 }, (_, index) => start + index);
});

onMounted(loadRankings);

watch(() => route.query.episodeId, () => {
  currentPage.value = 1;
  loadRankings();
});

async function loadRankings() {
  loading.value = true;
  message.value = '';
  try {
    rankings.value = isEpisodeRanking.value
      ? await rankingApi.getRankings({ episodeId: episodeId.value, limit: 100 })
      : await rankingApi.getPlayerRankings({ limit: 100 });
    currentPage.value = Math.min(currentPage.value, totalPages.value);
  } catch (error) {
    message.value = error.userMessage || '랭킹을 불러오지 못했습니다.';
  } finally {
    loading.value = false;
  }
}

function entryScore(entry) {
  return isEpisodeRanking.value ? entry.score || 0 : entry.totalScore || 0;
}

function entrySubLabel(entry) {
  return isEpisodeRanking.value
    ? entry.episodeTitle || episodeTitle.value || '미션 클리어'
    : `${entry.clearCount || 0}개 미션 클리어`;
}

function rankingKey(entry) {
  return isEpisodeRanking.value
    ? `mission-${entry.episodeId}-${entry.userId}-${entry.clearedAt || entry.rankNo}`
    : `player-${entry.userId}`;
}

function goPage(page) {
  currentPage.value = Math.min(Math.max(1, page), totalPages.value);
}

function goBack() {
  if (window.history.length > 1) {
    router.back();
    return;
  }
  router.push({ name: 'EpisodeList' });
}

function showTotalRanking() {
  router.push({ name: 'Ranking' });
}
</script>

<style scoped>
.ranking-page { min-height: 100vh; box-sizing: border-box; padding: 20px 16px 92px; background: radial-gradient(circle at 80% 4%, rgba(250,204,21,.22), transparent 30%), linear-gradient(150deg, #1c1917, #111827 58%, #030712); color: #f8fafc; font-family: 'Noto Sans KR', Georgia, serif; }
.hero, .board-shell, .toast { width: min(100%, 1120px); margin-left: auto; margin-right: auto; }
.hero { margin-bottom: 14px; padding: 18px 22px; border: 1px solid rgba(251,191,36,.24); border-radius: 20px; background: rgba(15,23,42,.72); }
.back-btn { margin-bottom: 14px; border: 1px solid rgba(148,163,184,.34); background: rgba(15,23,42,.8); color: #cbd5e1; }
.hero p, .panel-head p { margin: 0 0 8px; color: #facc15; font-size: .74rem; font-weight: 900; letter-spacing: .16em; }
h1 { margin: 0; font-size: clamp(2.1rem, 7vw, 4.2rem); line-height: .94; }
.hero span { display: block; margin-top: 10px; color: #cbd5e1; }
.hero-actions { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 16px; }
button { min-height: 40px; border: 0; border-radius: 12px; background: #b45309; color: #fff; font: inherit; font-weight: 900; padding: 0 14px; cursor: pointer; }
.ghost-btn { border: 1px solid rgba(148,163,184,.34); background: rgba(15,23,42,.82); color: #e2e8f0; }
.board-shell { display: grid; gap: 16px; }
.panel { padding: 16px; border: 1px solid rgba(148,163,184,.18); border-radius: 20px; background: rgba(15,23,42,.72); box-shadow: 0 28px 70px rgba(0,0,0,.25); }
.panel-head { display: flex; justify-content: space-between; align-items: end; gap: 12px; }
.panel-head h2 { margin: 0; font-size: 1.45rem; }
.panel-head span { color: #fde68a; font-weight: 900; }
.ranking-list { display: grid; gap: 8px; margin: 14px 0 0; padding: 0; list-style: none; }
.ranking-list li { display: grid; grid-template-columns: 42px 48px 1fr auto; gap: 12px; align-items: center; padding: 10px 12px; border: 1px solid rgba(148,163,184,.16); border-radius: 16px; background: rgba(2,6,23,.4); }
.ranking-list li.mine { border-color: rgba(16,185,129,.75); background: linear-gradient(135deg, rgba(16,185,129,.22), rgba(2,6,23,.56)); box-shadow: 0 0 0 2px rgba(16,185,129,.16), 0 18px 44px rgba(0,0,0,.24); }
.ranking-list li.mine b { background: #059669; }
.ranking-list b { display: grid; place-items: center; width: 42px; height: 42px; border-radius: 14px; background: #92400e; color: #fffbeb; }
.ranking-list img { width: 48px; height: 48px; border-radius: 16px; object-fit: cover; background: #334155; }
.ranking-list strong { display: block; color: #fff7ed; }
.ranking-list span, .ranking-list small, .empty { color: #94a3b8; }
.ranking-list em { color: #fde68a; font-style: normal; font-weight: 1000; font-size: 1.05rem; }
.ranking-list small { grid-column: 3 / 5; }
.pagination { margin: 14px auto 0; display: flex; align-items: center; justify-content: center; gap: 7px; }
.pagination button { min-width: 32px; width: 32px; min-height: 32px; padding: 0; border-radius: 4px; border: 1px solid rgba(148,163,184,.34); background: rgba(15,23,42,.8); color: #cbd5e1; }
.pagination .page-number.active { border-color: #10b981; background: #059669; color: #fff; }
.pagination button:disabled { opacity: .42; cursor: not-allowed; }
.toast { margin-bottom: 12px; padding: 12px 14px; border-radius: 14px; background: rgba(127,29,29,.24); color: #fecaca; }
.empty { padding: 18px; text-align: center; }
@media (max-width: 860px) {
  .board-shell { grid-template-columns: 1fr; }
}
@media (max-width: 560px) {
  .hero-actions { display: grid; }
  .ranking-list li { grid-template-columns: 36px 44px 1fr; }
  .ranking-list em, .ranking-list small { grid-column: 3; }
}
</style>
