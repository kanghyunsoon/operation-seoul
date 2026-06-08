<template>
  <main class="coaching-page">
    <header class="hero">
      <div>
        <p>PLAY COACH</p>
        <h1>플레이 코칭</h1>
        <span>클리어 기록과 진행 데이터를 분석해 다음 플레이 전략을 제안합니다.</span>
      </div>
      <div class="hero-actions">
        <button type="button" @click="router.push({ name: 'Recommendations' })">추천 보기</button>
        <button type="button" @click="loadCoaching">다시 분석</button>
      </div>
    </header>

    <p v-if="message" class="toast error">{{ message }}</p>
    <section v-if="loading" class="state">코칭 리포트를 불러오는 중입니다.</section>
    <section v-else-if="summary" class="content-grid">
      <article class="summary-panel">
        <p>PROFILE</p>
        <h2>{{ summary.playStyle }}</h2>
        <div class="metrics">
          <span><strong>{{ summary.totalStarted || 0 }}</strong>시작</span>
          <span><strong>{{ summary.totalCleared || 0 }}</strong>클리어</span>
          <span><strong>{{ summary.averageScore || 0 }}</strong>평균 점수</span>
        </div>
        <ul>
          <li v-for="advice in summary.globalAdvice || []" :key="advice">{{ advice }}</li>
        </ul>
      </article>

      <section class="reports">
        <article v-for="report in summary.recentReports || []" :key="report.episodeId" class="report-card">
          <div class="grade">{{ report.grade }}</div>
          <div>
            <p>{{ statusLabel(report.status) }}</p>
            <h2>{{ report.episodeTitle }}</h2>
            <span>{{ report.summary }}</span>
          </div>
          <div class="mini-metrics">
            <span>점수 {{ report.score || 0 }}</span>
            <span>오답 {{ report.wrongAnswerCount || 0 }}</span>
            <span>질문 {{ report.deductionQuestionCount || 0 }}</span>
          </div>
          <div class="advice-grid">
            <div>
              <strong>강점</strong>
              <small v-for="item in report.strengths || []" :key="`s-${report.episodeId}-${item}`">{{ item }}</small>
            </div>
            <div>
              <strong>개선</strong>
              <small v-for="item in report.improvements || []" :key="`i-${report.episodeId}-${item}`">{{ item }}</small>
            </div>
          </div>
          <button type="button" @click="openEpisode(report)">사건 파일 보기</button>
        </article>
        <p v-if="!(summary.recentReports || []).length" class="empty">아직 분석할 플레이 기록이 없습니다.</p>
      </section>
    </section>
  </main>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { coachingApi } from '@/api/coachingApi';

const router = useRouter();
const summary = ref(null);
const loading = ref(true);
const message = ref('');

onMounted(loadCoaching);

async function loadCoaching() {
  loading.value = true;
  message.value = '';
  try {
    summary.value = await coachingApi.getMyCoaching();
  } catch (err) {
    message.value = err.userMessage || '코칭 리포트를 불러오지 못했습니다.';
  } finally {
    loading.value = false;
  }
}

function openEpisode(report) {
  router.push({ name: 'EpisodeDetail', params: { episodeId: report.episodeId } });
}

function statusLabel(status) {
  return { CLEARED: '클리어 완료', IN_PROGRESS: '진행 중', FINAL_READY: '최종 추리 가능', NOT_STARTED: '시작 전' }[status] || status;
}
</script>

<style scoped>
.coaching-page { min-height: 100vh; box-sizing: border-box; padding: 26px 16px 72px; background: radial-gradient(circle at 18% 0%, rgba(45,212,191,.2), transparent 32%), linear-gradient(145deg, #07140f, #111827 58%, #030712); color: #f8fafc; font-family: 'Noto Serif KR', Georgia, serif; }
.hero, .content-grid, .state, .toast { width: min(100%, 1080px); box-sizing: border-box; margin-left: auto; margin-right: auto; }
.hero { display: flex; justify-content: space-between; align-items: end; gap: 16px; margin-bottom: 16px; padding: 22px; border: 1px solid rgba(45,212,191,.24); border-radius: 24px; background: rgba(15,23,42,.68); }
.hero p, .summary-panel p, .report-card p { margin: 0 0 8px; color: #5eead4; font-size: .74rem; font-weight: 1000; letter-spacing: .16em; }
h1 { margin: 0; font-size: clamp(2.3rem, 9vw, 4.8rem); line-height: .94; }
.hero span { display: block; margin-top: 10px; color: #cbd5e1; }
.hero-actions { display: flex; flex-wrap: wrap; gap: 8px; }
button { min-height: 40px; border: 0; border-radius: 12px; background: #0f766e; color: #fff; font: inherit; font-weight: 900; padding: 0 14px; }
.content-grid { display: grid; grid-template-columns: 320px minmax(0, 1fr); gap: 14px; }
.summary-panel, .report-card { border: 1px solid rgba(148,163,184,.18); border-radius: 24px; background: rgba(15,23,42,.68); box-shadow: 0 24px 64px rgba(0,0,0,.22); }
.summary-panel { padding: 20px; align-self: start; }
.summary-panel h2 { margin: 0 0 14px; color: #ccfbf1; font-size: 2rem; }
.metrics { display: grid; gap: 8px; margin: 14px 0; }
.metrics span { display: flex; justify-content: space-between; padding: 12px; border-radius: 14px; background: rgba(2,6,23,.45); color: #cbd5e1; }
.metrics strong { color: #99f6e4; }
ul { padding-left: 18px; color: #dbeafe; line-height: 1.6; }
.reports { display: grid; gap: 12px; }
.report-card { display: grid; grid-template-columns: 70px 1fr; gap: 12px; padding: 16px; }
.grade { display: grid; place-items: center; width: 64px; height: 64px; border-radius: 20px; background: #0f766e; color: #ecfeff; font-size: 1.7rem; font-weight: 1000; }
.report-card h2 { margin: 0 0 8px; color: #fff7ed; }
.report-card span, .empty { color: #cbd5e1; line-height: 1.5; }
.mini-metrics { grid-column: 2; display: flex; flex-wrap: wrap; gap: 7px; }
.mini-metrics span { border: 1px solid rgba(94,234,212,.22); border-radius: 999px; padding: 5px 9px; color: #ccfbf1; font-size: .78rem; }
.advice-grid { grid-column: 1 / -1; display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.advice-grid div { padding: 12px; border-radius: 16px; background: rgba(2,6,23,.38); }
.advice-grid strong, .advice-grid small { display: block; }
.advice-grid strong { color: #99f6e4; margin-bottom: 8px; }
.advice-grid small { color: #cbd5e1; margin-top: 6px; line-height: 1.45; }
.report-card button { grid-column: 1 / -1; }
.state, .toast { padding: 16px; border: 1px dashed rgba(148,163,184,.34); border-radius: 16px; color: #cbd5e1; text-align: center; }
.toast { margin-bottom: 12px; border-style: solid; background: rgba(127,29,29,.24); color: #fecaca; }
@media (max-width: 840px) {
  .hero, .content-grid, .report-card, .advice-grid { display: grid; grid-template-columns: 1fr; }
  .hero-actions { display: grid; grid-template-columns: 1fr; }
  .mini-metrics { grid-column: auto; }
}
</style>
