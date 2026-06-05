<template>
  <main class="report-page">
    <section v-if="loading" class="report shell">클리어 리포트를 불러오는 중입니다.</section>
    <section v-else-if="errorMessage" class="report shell">
      <p class="eyebrow">CLEAR REPORT</p>
      <h1>리포트를 열 수 없습니다</h1>
      <p>{{ errorMessage }}</p>
      <div class="actions">
        <button type="button" @click="router.push({ name: 'EpisodeMap', params: { episodeId } })">지도로 돌아가기</button>
        <button type="button" class="ghost" @click="router.push({ name: 'EpisodeList' })">사건 목록</button>
      </div>
    </section>

    <section v-else-if="report" class="report">
      <p class="stamp">CASE CLEARED</p>
      <p class="eyebrow">클리어 리포트</p>
      <h1>{{ report.title }}</h1>
      <p class="question">{{ report.finalQuestion }}</p>

      <div class="score-card">
        <div>
          <span>최종 점수</span>
          <strong>{{ report.score || 0 }}점</strong>
        </div>
        <div>
          <span>소요 시간</span>
          <strong>{{ formatElapsed(report.elapsedSeconds) }}</strong>
        </div>
        <div>
          <span>최종 장소</span>
          <strong>{{ report.finalArrivedSpotName || '기록 없음' }}</strong>
        </div>
      </div>

      <div class="metric-grid">
        <article><strong>{{ report.completedSpotCount || 0 }}/{{ report.totalSpotCount || 0 }}</strong><span>조사 완료 장소</span></article>
        <article><strong>{{ report.answerClueCount || 0 }}</strong><span>정답 힌트</span></article>
        <article><strong>{{ report.destinationClueCount || 0 }}</strong><span>목적지 힌트</span></article>
        <article><strong>{{ report.deductionQuestionCount || 0 }}</strong><span>최종 추리 질문</span></article>
        <article><strong>{{ report.wrongAnswerCount || 0 }}</strong><span>오답</span></article>
        <article><strong>{{ report.unlockedEvidenceIds?.length || 0 }}</strong><span>해금 자료</span></article>
      </div>

      <article class="paper-block">
        <h2>진실 파일</h2>
        <p>{{ report.finalTruthSummary || '진실 요약이 아직 등록되지 않았습니다.' }}</p>
      </article>

      <article class="paper-block">
        <h2>실제 역사 해설</h2>
        <p>{{ report.actualHistorySummary || '역사 해설이 아직 등록되지 않았습니다.' }}</p>
      </article>

      <article class="paper-block">
        <h2>수집한 단서</h2>
        <div class="clue-section">
          <h3>정답 힌트</h3>
          <div class="chips"><span v-for="clue in report.answerClues || []" :key="`a-${clue}`">{{ clue }}</span><em v-if="!(report.answerClues || []).length">없음</em></div>
        </div>
        <div class="clue-section">
          <h3>목적지 힌트</h3>
          <div class="chips"><span v-for="clue in report.destinationClues || []" :key="`d-${clue}`">{{ clue }}</span><em v-if="!(report.destinationClues || []).length">없음</em></div>
        </div>
        <div class="clue-section">
          <h3>스토리 단서</h3>
          <div class="chips"><span v-for="clue in report.storyClues || []" :key="`s-${clue}`">{{ clue }}</span><em v-if="!(report.storyClues || []).length">없음</em></div>
        </div>
      </article>

      <article class="paper-block subdued">
        <h2>조사 기록</h2>
        <p>시작: {{ formatDate(report.startedAt) }}</p>
        <p>클리어: {{ formatDate(report.clearedAt) }}</p>
        <p>힌트 사용 {{ report.hintUsedCount || 0 }}회 · 최종 정답 제출 {{ report.finalGuessCount || 0 }}회</p>
      </article>

      <EpisodeReviewPanel v-if="report.canReview" :episode-id="episodeId" />
      <article v-else class="paper-block subdued">
        <h2>리뷰</h2>
        <p>CLEARED 상태에서만 리뷰를 작성할 수 있습니다. 이미 작성한 리뷰가 있다면 목록에서 수정할 수 있습니다.</p>
      </article>

      <div class="actions">
        <button type="button" @click="router.push({ name: 'EpisodeCaseFile', params: { episodeId } })">사건파일 보기</button>
        <button type="button" class="ghost" @click="router.push({ name: 'EpisodeList' })">사건 목록으로</button>
      </div>
    </section>
  </main>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { episodeApi } from '@/api/episodeApi';
import EpisodeReviewPanel from '@/components/episode/EpisodeReviewPanel.vue';

const route = useRoute();
const router = useRouter();
const episodeId = route.params.episodeId;
const report = ref(null);
const loading = ref(true);
const errorMessage = ref('');

onMounted(loadReport);

async function loadReport() {
  loading.value = true;
  errorMessage.value = '';
  try {
    report.value = await episodeApi.getClearReport(episodeId);
  } catch (error) {
    errorMessage.value = error.userMessage || '클리어한 에피소드의 리포트만 확인할 수 있습니다.';
  } finally {
    loading.value = false;
  }
}

function formatElapsed(seconds) {
  if (seconds == null) return '기록 없음';
  const total = Number(seconds);
  const hours = Math.floor(total / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  if (hours > 0) return `${hours}시간 ${minutes}분`;
  return `${minutes}분`;
}

function formatDate(value) {
  if (!value) return '기록 없음';
  return new Date(value).toLocaleString('ko-KR', { hour12: false });
}
</script>

<style scoped>
.report-page { min-height: 100vh; box-sizing: border-box; padding: 18px 14px 28px; background: radial-gradient(circle at 20% 0%, rgba(180, 83, 9, .18), transparent 34%), #f8f1df; color: #24180d; font-family: Georgia, 'Noto Sans KR', serif; }
.report { width: min(100%, 760px); box-sizing: border-box; margin: 0 auto; padding: 22px; border: 2px solid rgba(36,24,13,.18); border-radius: 22px; background: rgba(255,255,255,.62); box-shadow: 0 22px 55px rgba(36,24,13,.13); }
.shell { min-height: 260px; display: grid; align-content: center; gap: 12px; }
.stamp { display: inline-block; transform: rotate(-3deg); border: 3px solid #b91c1c; color: #b91c1c; padding: 6px 10px; font-weight: 1000; }
.eyebrow { margin: 12px 0 0; color: #9a3412; font-size: .76rem; font-weight: 1000; letter-spacing: .14em; }
h1 { margin: 8px 0; font-size: clamp(1.8rem, 8vw, 3.4rem); line-height: 1.05; }
.question { margin: 0 0 18px; color: #57534e; line-height: 1.65; }
.score-card { display: grid; grid-template-columns: 1fr; gap: 10px; margin: 18px 0; }
.score-card div, .metric-grid article, .paper-block { border: 1px solid rgba(36,24,13,.16); border-radius: 16px; background: rgba(255,255,255,.68); }
.score-card div { padding: 14px; display: flex; justify-content: space-between; gap: 12px; align-items: center; }
.score-card span, .metric-grid span { color: #78716c; font-size: .82rem; font-weight: 800; }
.score-card strong { color: #92400e; font-size: 1.15rem; }
.metric-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 9px; margin: 16px 0; }
.metric-grid article { padding: 12px; display: grid; gap: 4px; }
.metric-grid strong { font-size: 1.25rem; }
.paper-block { margin-top: 14px; padding: 16px; }
.paper-block h2 { margin: 0 0 10px; }
.paper-block p { margin: 6px 0; line-height: 1.75; }
.paper-block.subdued { background: rgba(250, 247, 239, .72); }
.clue-section { margin-top: 12px; }
.clue-section h3 { margin: 0 0 7px; font-size: .92rem; color: #78350f; }
.chips { display: flex; flex-wrap: wrap; gap: 7px; }
.chips span { border-radius: 999px; background: #24180d; color: #fde68a; padding: 7px 10px; font-size: .82rem; font-weight: 900; }
.chips em { color: #78716c; font-style: normal; }
.actions { display: grid; grid-template-columns: 1fr; gap: 9px; margin-top: 18px; }
button { min-height: 48px; border: 0; border-radius: 13px; background: #24180d; color: #fff; font-weight: 900; }
button.ghost { border: 1px solid rgba(36,24,13,.24); background: transparent; color: #24180d; }
@media (min-width: 640px) { .score-card { grid-template-columns: repeat(3, 1fr); } .score-card div { display: grid; } .metric-grid { grid-template-columns: repeat(3, 1fr); } .actions { grid-template-columns: 1fr 1fr; } }
@media (max-width: 370px) { .report { padding: 18px; } .metric-grid { grid-template-columns: 1fr; } }
</style>
