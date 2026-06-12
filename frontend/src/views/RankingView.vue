<template>
  <main class="ranking-page">
    <header class="hero">
      <button type="button" class="ghost" @click="router.push({ name: 'RegionMap' })">권역 선택</button>
      <p>LEADERBOARD</p>
      <h1>클리어 랭킹</h1>
      <span>점수, 오답 수, 추리 질문 수, 제출 횟수, 클리어 시간을 기준으로 정렬합니다.</span>
      <div class="hero-actions">
        <button type="button" @click="router.push({ name: 'EpisodeList' })">미션 파일 보기</button>
        <button type="button" @click="router.push({ name: 'Challenges' })">챌린지</button>
        <button type="button" @click="loadRankings">새로고침</button>
      </div>
    </header>

    <p v-if="message" class="toast error">{{ message }}</p>

    <section class="board-shell">
      <article class="panel main-board">
        <div class="panel-head">
          <div>
            <p>TOP AGENTS</p>
            <h2>전체 랭킹</h2>
          </div>
          <span>{{ rankings.length }}명</span>
        </div>
        <div v-if="loading" class="empty">랭킹을 불러오는 중입니다.</div>
        <div v-else-if="!rankings.length" class="empty">아직 클리어 기록이 없습니다.</div>
        <ol v-else class="ranking-list">
          <li v-for="entry in rankings" :key="`${entry.episodeId}-${entry.userId}-${entry.clearedAt}`">
            <b>{{ entry.rankNo }}</b>
            <img :src="entry.profileImageUrl || defaultProfile" alt="" />
            <div>
              <strong>{{ entry.nickname || '요원' }}</strong>
              <span>{{ entry.episodeTitle }}</span>
            </div>
            <em>{{ entry.score || 0 }}점</em>
            <small>오답 {{ entry.wrongAnswerCount || 0 }} · 질문 {{ entry.deductionQuestionCount || 0 }} · 제출 {{ entry.finalGuessCount || 0 }}</small>
          </li>
        </ol>
      </article>

      <article class="panel">
        <div class="panel-head">
          <div>
            <p>MY CLEARS</p>
            <h2>내 기록</h2>
          </div>
          <span>{{ myRankings.length }}개</span>
        </div>
        <div v-if="!myRankings.length" class="empty">아직 클리어한 미션 파일이 없습니다.</div>
        <div v-else class="my-list">
          <article v-for="entry in myRankings" :key="`mine-${entry.episodeId}-${entry.clearedAt}`">
            <strong>{{ entry.episodeTitle }}</strong>
            <span>{{ entry.score || 0 }}점</span>
            <small>{{ formatDate(entry.clearedAt) }}</small>
          </article>
        </div>
      </article>
    </section>
  </main>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { rankingApi } from '@/api/rankingApi';

const router = useRouter();
const rankings = ref([]);
const myRankings = ref([]);
const loading = ref(true);
const message = ref('');
const defaultProfile = 'data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 width=%2264%22 height=%2264%22 viewBox=%220 0 64 64%22%3E%3Crect width=%2264%22 height=%2264%22 rx=%2218%22 fill=%22%231e293b%22/%3E%3Ccircle cx=%2232%22 cy=%2225%22 r=%2211%22 fill=%22%23fde68a%22/%3E%3Cpath d=%22M14 56c3-12 12-18 18-18s15 6 18 18%22 fill=%22%23fde68a%22/%3E%3C/svg%3E';

onMounted(loadRankings);

async function loadRankings() {
  loading.value = true;
  message.value = '';
  try {
    const [rankingData, myData] = await Promise.all([
      rankingApi.getRankings({ limit: 50 }),
      rankingApi.getMyRankings()
    ]);
    rankings.value = rankingData;
    myRankings.value = myData;
  } catch (error) {
    message.value = error.userMessage || '랭킹을 불러오지 못했습니다.';
  } finally {
    loading.value = false;
  }
}

function formatDate(value) {
  if (!value) return '-';
  return new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
}
</script>

<style scoped>
.ranking-page { min-height: 100vh; box-sizing: border-box; padding: 24px 16px 70px; background: radial-gradient(circle at 80% 4%, rgba(250,204,21,.22), transparent 30%), linear-gradient(150deg, #1c1917, #111827 58%, #030712); color: #f8fafc; font-family: 'Noto Sans KR', Georgia, serif; }
.hero, .board-shell, .toast { width: min(100%, 1120px); margin-left: auto; margin-right: auto; }
.hero { margin-bottom: 16px; padding: 22px; border: 1px solid rgba(251,191,36,.24); border-radius: 24px; background: rgba(15,23,42,.72); }
.ghost { background: rgba(15,23,42,.8); border: 1px solid rgba(148,163,184,.28); color: #cbd5e1; }
.hero p, .panel-head p { margin: 12px 0 8px; color: #facc15; font-size: .74rem; font-weight: 900; letter-spacing: .16em; }
h1 { margin: 0; font-size: clamp(2.3rem, 8vw, 5rem); line-height: .94; }
.hero span { display: block; margin-top: 10px; color: #cbd5e1; }
.hero-actions { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 16px; }
button { min-height: 40px; border: 0; border-radius: 12px; background: #b45309; color: #fff; font: inherit; font-weight: 900; padding: 0 14px; cursor: pointer; }
.board-shell { display: grid; grid-template-columns: minmax(0, 1.25fr) minmax(300px, .75fr); gap: 16px; }
.panel { padding: 18px; border: 1px solid rgba(148,163,184,.18); border-radius: 24px; background: rgba(15,23,42,.72); box-shadow: 0 28px 70px rgba(0,0,0,.25); }
.panel-head { display: flex; justify-content: space-between; align-items: end; gap: 12px; }
.panel-head h2 { margin: 0; font-size: 1.45rem; }
.panel-head span { color: #fde68a; font-weight: 900; }
.ranking-list { display: grid; gap: 10px; margin: 16px 0 0; padding: 0; list-style: none; }
.ranking-list li { display: grid; grid-template-columns: 42px 48px 1fr auto; gap: 12px; align-items: center; padding: 12px; border: 1px solid rgba(148,163,184,.16); border-radius: 18px; background: rgba(2,6,23,.4); }
.ranking-list b { display: grid; place-items: center; width: 42px; height: 42px; border-radius: 14px; background: #92400e; color: #fffbeb; }
.ranking-list img { width: 48px; height: 48px; border-radius: 16px; object-fit: cover; background: #334155; }
.ranking-list strong, .my-list strong { display: block; color: #fff7ed; }
.ranking-list span, .ranking-list small, .my-list small, .empty { color: #94a3b8; }
.ranking-list em { color: #fde68a; font-style: normal; font-weight: 1000; font-size: 1.05rem; }
.ranking-list small { grid-column: 3 / 5; }
.my-list { display: grid; gap: 10px; margin-top: 16px; }
.my-list article { padding: 12px; border: 1px solid rgba(148,163,184,.16); border-radius: 16px; background: rgba(2,6,23,.4); }
.my-list span { display: block; margin: 6px 0; color: #fde68a; font-weight: 900; }
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