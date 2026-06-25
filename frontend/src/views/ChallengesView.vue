<template>
  <main class="challenge-ranking-page">
    <header class="hero">
      <div>
        <p>CHALLENGE / RANKING</p>
        <h1>챌린지/랭킹</h1>
        <span>목표 달성 현황과 누적 점수 랭킹을 확인합니다.</span>
      </div>
    </header>

    <section class="top-tabs">
      <button type="button" :class="{ active: activeTab === 'challenge' }" @click="activeTab = 'challenge'">챌린지</button>
      <button type="button" :class="{ active: activeTab === 'ranking' }" @click="activeTab = 'ranking'">랭킹</button>
    </section>

    <p v-if="message" class="toast error">{{ message }}</p>

    <section v-if="activeTab === 'challenge'" class="content-shell">
      <div class="section-head">
        <h2>다음 챌린지</h2>
        <button type="button" @click="loadAll">새로고침</button>
      </div>
      <section v-if="loading" class="state">챌린지를 불러오는 중입니다.</section>
      <template v-else>
        <div class="challenge-grid">
          <article v-for="goal in summary.activeGoals || []" :key="goal.type" class="goal-card" :class="{ completed: goal.completed }">
            <span>{{ goal.description }}</span>
            <h3>{{ goal.title }}</h3>
            <div class="progress-track">
              <i :style="{ width: progressPercent(goal) + '%' }"></i>
            </div>
            <small>{{ goal.currentValue }} / {{ goal.targetValue }}</small>
          </article>
        </div>

        <div class="section-head lower">
          <h2>달성한 챌린지</h2>
          <span>{{ summary.completedCount || 0 }}개</span>
        </div>
        <section v-if="summary.completedGoals?.length" class="completed-list">
          <article v-for="goal in summary.completedGoals" :key="`${goal.type}-${goal.targetValue}`">
            <strong>{{ goal.title }}</strong>
            <span>{{ goal.description }}</span>
          </article>
        </section>
        <section v-else class="state">아직 달성한 챌린지가 없습니다.</section>
      </template>
    </section>

    <section v-else class="content-shell">
      <div class="section-head">
        <h2>누적 점수 랭킹</h2>
        <button type="button" @click="loadRankings">새로고침</button>
      </div>
      <section v-if="rankingLoading" class="state">랭킹을 불러오는 중입니다.</section>
      <section v-else-if="!rankings.length" class="state">아직 랭킹 기록이 없습니다.</section>
      <template v-else>
        <ol class="ranking-list">
          <li v-for="entry in visibleRankings" :key="entry.userId" :class="{ mine: entry.userId === sessionStore.userId }">
            <b>{{ entry.rankNo }}</b>
            <img :src="entry.profileImageUrl || defaultProfile" alt="" />
            <div>
              <strong>{{ entry.nickname || '유저' }}</strong>
              <span>{{ entry.clearCount || 0 }}개 미션 클리어 · 챌린지 {{ entry.achievedChallengeCount || 0 }}개</span>
            </div>
            <em>{{ entry.totalScore || 0 }}점</em>
          </li>
        </ol>
        <nav v-if="rankingTotalPages > 1" class="pager">
          <button type="button" :disabled="rankingPage <= 1" @click="rankingPage -= 1">‹</button>
          <button
            v-for="page in rankingPages"
            :key="page"
            type="button"
            :class="{ active: page === rankingPage }"
            @click="rankingPage = page"
          >
            {{ page }}
          </button>
          <button type="button" :disabled="rankingPage >= rankingTotalPages" @click="rankingPage += 1">›</button>
        </nav>
      </template>
    </section>

    <MainBottomNav />
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import MainBottomNav from '@/components/MainBottomNav.vue';
import { challengeApi } from '@/api/challengeApi';
import { rankingApi } from '@/api/rankingApi';
import { useSessionStore } from '@/stores/sessionStore';

const sessionStore = useSessionStore();
const activeTab = ref('challenge');
const summary = ref({ activeGoals: [], completedGoals: [], completedCount: 0 });
const rankings = ref([]);
const loading = ref(true);
const rankingLoading = ref(true);
const message = ref('');
const rankingPage = ref(1);
const rankingPageSize = 10;
const defaultProfile = 'data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 width=%2264%22 height=%2264%22 viewBox=%220 0 64 64%22%3E%3Crect width=%2264%22 height=%2264%22 rx=%2218%22 fill=%22%231e293b%22/%3E%3Ccircle cx=%2232%22 cy=%2225%22 r=%2211%22 fill=%22%23fde68a%22/%3E%3Cpath d=%22M14 56c3-12 12-18 18-18s15 6 18 18%22 fill=%22%23fde68a%22/%3E%3C/svg%3E';

const rankingTotalPages = computed(() => Math.max(1, Math.ceil(rankings.value.length / rankingPageSize)));
const visibleRankings = computed(() => rankings.value.slice((rankingPage.value - 1) * rankingPageSize, rankingPage.value * rankingPageSize));
const rankingPages = computed(() => Array.from({ length: rankingTotalPages.value }, (_, index) => index + 1));

onMounted(loadAll);

async function loadAll() {
  await Promise.all([loadSummary(), loadRankings()]);
}

async function loadSummary() {
  loading.value = true;
  message.value = '';
  try {
    summary.value = await challengeApi.getSummary();
  } catch (error) {
    message.value = error.userMessage || '챌린지를 불러오지 못했습니다.';
  } finally {
    loading.value = false;
  }
}

async function loadRankings() {
  rankingLoading.value = true;
  try {
    rankings.value = await rankingApi.getPlayerRankings({ limit: 100 });
    rankingPage.value = Math.min(rankingPage.value, rankingTotalPages.value);
  } catch (error) {
    message.value = error.userMessage || '랭킹을 불러오지 못했습니다.';
  } finally {
    rankingLoading.value = false;
  }
}

function progressPercent(goal) {
  const target = Math.max(1, goal.targetValue || 1);
  const current = Math.max(0, goal.currentValue || 0);
  return Math.min(100, Math.round((current / target) * 100));
}
</script>

<style scoped>
.challenge-ranking-page { min-height: 100vh; box-sizing: border-box; padding: 24px 16px 126px; background: radial-gradient(circle at 80% 0%, rgba(248,113,113,.22), transparent 32%), linear-gradient(150deg, #1f1111, #111827 58%, #030712); color: #f8fafc; font-family: 'Noto Sans KR', system-ui, sans-serif; }
.hero, .top-tabs, .content-shell, .toast { width: min(100%, 980px); box-sizing: border-box; margin-left: auto; margin-right: auto; }
.hero { margin-bottom: 14px; padding: 22px; border: 1px solid rgba(248,113,113,.24); border-radius: 22px; background: rgba(15,23,42,.68); }
.hero p { margin: 0 0 8px; color: #fca5a5; font-size: .74rem; font-weight: 1000; letter-spacing: .16em; }
h1 { margin: 0; font-size: clamp(2.2rem, 8vw, 4.4rem); line-height: .96; }
.hero span { display: block; margin-top: 10px; color: #cbd5e1; }
.top-tabs { display: grid; grid-template-columns: repeat(2, 1fr); gap: 8px; margin-bottom: 14px; padding: 8px; border-radius: 16px; background: rgba(15,23,42,.72); }
button { min-height: 40px; border: 0; border-radius: 12px; background: #334155; color: #fff; font: inherit; font-weight: 900; padding: 0 14px; cursor: pointer; }
.top-tabs button.active, .section-head button { background: #b91c1c; }
.content-shell { padding: 18px; border: 1px solid rgba(148,163,184,.18); border-radius: 22px; background: rgba(15,23,42,.7); box-shadow: 0 24px 64px rgba(0,0,0,.24); }
.section-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 14px; }
.section-head.lower { margin-top: 22px; }
.section-head h2 { margin: 0; }
.section-head span { color: #fde68a; font-weight: 900; }
.challenge-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.goal-card, .completed-list article, .ranking-list li { border: 1px solid rgba(148,163,184,.16); border-radius: 16px; background: rgba(2,6,23,.42); }
.goal-card { display: grid; gap: 10px; padding: 16px; }
.goal-card.completed {
  border-color: rgba(103,190,217,.72);
  background: linear-gradient(135deg, rgba(103,190,217,.2), rgba(2,6,23,.46));
}
.goal-card span { color: #fecaca; font-weight: 900; }
.goal-card h3 { margin: 0; color: #fff7ed; }
.progress-track { overflow: hidden; height: 10px; border-radius: 999px; background: rgba(148,163,184,.18); }
.progress-track i { display: block; height: 100%; border-radius: inherit; background: linear-gradient(90deg, #f87171, #facc15); }
small, .state { color: #94a3b8; }
.completed-list { display: grid; gap: 8px; }
.completed-list article {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border-color: rgba(103,190,217,.72);
  background: linear-gradient(135deg, rgba(103,190,217,.2), rgba(2,6,23,.46));
}
.completed-list strong { color: #dff7ff; }
.completed-list span { color: #67bed9; font-weight: 900; }
.ranking-list { display: grid; gap: 8px; margin: 0; padding: 0; list-style: none; }
.ranking-list li { display: grid; grid-template-columns: 42px 48px 1fr auto; gap: 12px; align-items: center; padding: 10px 12px; }
.ranking-list li.mine { border-color: rgba(16,185,129,.75); background: linear-gradient(135deg, rgba(16,185,129,.22), rgba(2,6,23,.56)); }
.ranking-list b { display: grid; place-items: center; width: 42px; height: 42px; border-radius: 14px; background: #92400e; color: #fffbeb; }
.ranking-list img { width: 48px; height: 48px; border-radius: 16px; object-fit: cover; background: #334155; }
.ranking-list strong { display: block; color: #fff7ed; }
.ranking-list span { color: #94a3b8; }
.ranking-list em { color: #fde68a; font-style: normal; font-weight: 1000; }
.pager { display: flex; justify-content: center; gap: 8px; margin-top: 14px; flex-wrap: wrap; }
.pager button { min-width: 38px; padding: 0 10px; border-radius: 999px; }
.pager button.active { background: #059669; }
.pager button:disabled { opacity: .45; cursor: default; }
.state, .toast { padding: 16px; border: 1px dashed rgba(148,163,184,.34); border-radius: 16px; text-align: center; }
.toast { margin-bottom: 12px; border-style: solid; color: #fecaca; background: rgba(127,29,29,.18); }
@media (max-width: 760px) {
  .challenge-grid { grid-template-columns: 1fr; }
  .ranking-list li { grid-template-columns: 36px 44px 1fr; }
  .ranking-list em { grid-column: 3; }
}
</style>
