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
          <span>최종 조사 지점</span>
          <strong>{{ report.finalArrivedSpotName || '기록 없음' }}</strong>
        </div>
      </div>

      <div class="metric-grid">
        <article><strong>{{ report.completedSpotCount || 0 }}/{{ report.totalSpotCount || 0 }}</strong><span>조사 완료 장소</span></article>
        <article><strong>{{ report.answerClueCount || 0 }}</strong><span>수집한 추리 단서</span></article>
        <article><strong>4</strong><span>도출한 사건 진실</span></article>
        <article><strong>{{ report.deductionQuestionCount || 0 }}</strong><span>최종 추리 질문</span></article>
        <article><strong>{{ report.wrongAnswerCount || 0 }}</strong><span>오답</span></article>
        <article><strong>{{ report.unlockedEvidenceIds?.length || 0 }}</strong><span>해금 자료</span></article>
      </div>

      <article class="paper-block fact-block">
        <p class="mode-label">FACT MODE</p>
        <h2>1. 모티브 공개</h2>
        <p>{{ motifDisclosure }}</p>
      </article>

      <article class="paper-block fact-block">
        <h2>2. 실제 사건 해설</h2>
        <p v-for="paragraph in historyExplanationParagraphs" :key="paragraph">{{ paragraph }}</p>
      </article>

      <article class="paper-block fact-block">
        <h2>3. 픽션과 역사의 매칭 (디브리핑)</h2>
        <p v-for="line in fictionHistoryMappingLines" :key="line">{{ line }}</p>
      </article>

      <article class="paper-block">
        <h2>수집한 단서</h2>
        <div v-for="slot in deductionSlots" :key="slot.id" class="clue-section">
          <h3>{{ slot.label }} 추리</h3>
          <div class="chips"><span v-for="clue in slot.clues" :key="`${slot.id}-${clue}`">{{ clue }}</span><em v-if="!slot.clues.length">분류된 단서 없음</em></div>
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
        <p>클리어 상태에서만 리뷰를 작성할 수 있습니다. 이미 작성한 리뷰가 있다면 목록에서 수정할 수 있습니다.</p>
      </article>

      <div class="actions">
        <button type="button" @click="router.push({ name: 'Coaching' })">코칭 보기</button>
        <button type="button" @click="router.push({ name: 'EpisodeCaseFile', params: { episodeId } })">미션 파일 보기</button>
        <button type="button" class="ghost" @click="router.push({ name: 'EpisodeList' })">사건 목록으로</button>
      </div>
    </section>
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { episodeApi } from '@/api/episodeApi';
import EpisodeReviewPanel from '@/components/episode/EpisodeReviewPanel.vue';

const route = useRoute();
const router = useRouter();
const episodeId = route.params.episodeId;
const report = ref(null);
const loading = ref(true);
const errorMessage = ref('');

const motifSection = computed(() => sectionText(report.value?.actualHistorySummary, '1. 모티브 공개', '2. 실제 사건 해설'));
const historySection = computed(() => sectionText(report.value?.actualHistorySummary, '2. 실제 사건 해설'));
const mappingSection = computed(() => sectionText(report.value?.finalTruthSummary, '3. 픽션과 역사의 매칭 (디브리핑)'));
const deductionSlots = computed(() => {
  const fallback = uniqueClues(report.value?.answerClues);
  return [
    reportSlot('CULPRIT', '범인', report.value?.culpritClues, fallback, 0),
    reportSlot('WEAPON', '흉기', report.value?.weaponClues, fallback, 1),
    reportSlot('MOTIVE', '동기', report.value?.motiveClues, fallback, 2),
    reportSlot('METHOD', '방법', report.value?.methodClues, fallback, 3)
  ];
});

function reportSlot(id, label, explicit, fallback, offset) {
  const clues = uniqueClues(explicit);
  return { id, label, clues: clues.length ? clues : fallback.filter((_, index) => index % 4 === offset) };
}

function uniqueClues(values = []) {
  return [...new Set((values || []).map((value) => String(value || '').trim()).filter(Boolean))];
}
const motifDisclosure = computed(() => {
  if (motifSection.value) return motifSection.value;
  const finalPlace = report.value?.finalArrivedSpotName || '최종 장소';
  return `이 임무는 실제 [${finalPlace}]에서 있었던 [역사적 사건/인물]을 모티브로 제작되었습니다.`;
});
const historyExplanationParagraphs = computed(() => {
  const paragraphs = splitBlocks(historySection.value || report.value?.actualHistorySummary)
    .filter((paragraph) => !paragraph.includes('이 임무는 실제'));
  return paragraphs.length ? paragraphs : ['역사 해설이 아직 등록되지 않았습니다.'];
});
const fictionHistoryMappingLines = computed(() => {
  const lines = splitBlocks(mappingSection.value || report.value?.finalTruthSummary)
    .flatMap((paragraph) => paragraph.split('\n'))
    .map((line) => line.trim())
    .filter(Boolean);
  return lines.length ? lines : ['픽션과 실제 역사의 매칭 해설이 아직 등록되지 않았습니다.'];
});

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

function splitBlocks(value) {
  return String(value || '')
    .split(/\n{2,}/)
    .map((block) => block.trim())
    .filter(Boolean);
}

function sectionText(value, startTitle, nextTitle) {
  const text = String(value || '');
  const start = text.indexOf(startTitle);
  if (start < 0) return '';
  const bodyStart = start + startTitle.length;
  const bodyEnd = nextTitle ? text.indexOf(nextTitle, bodyStart) : -1;
  return text.slice(bodyStart, bodyEnd >= 0 ? bodyEnd : undefined).trim();
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
.fact-block { border-color: rgba(146, 64, 14, .26); background: rgba(255, 251, 235, .76); }
.mode-label { margin: 0 0 6px; color: #9a3412; font-size: .72rem; font-weight: 1000; letter-spacing: .14em; }
.clue-section { margin-top: 12px; }
.clue-section h3 { margin: 0 0 7px; font-size: .92rem; color: #78350f; }
.chips { display: flex; flex-wrap: wrap; gap: 7px; }
.chips span { border-radius: 999px; background: #24180d; color: #fde68a; padding: 7px 10px; font-size: .82rem; font-weight: 900; }
.chips em { color: #78716c; font-style: normal; }
.actions { display: grid; grid-template-columns: 1fr; gap: 9px; margin-top: 18px; }
button { min-height: 48px; border: 0; border-radius: 13px; background: #24180d; color: #fff; font-weight: 900; }
button.ghost { border: 1px solid rgba(36,24,13,.24); background: transparent; color: #24180d; }
@media (min-width: 640px) { .score-card { grid-template-columns: repeat(3, 1fr); } .score-card div { display: grid; } .metric-grid { grid-template-columns: repeat(3, 1fr); } .actions { grid-template-columns: 1fr 1fr 1fr; } }
@media (max-width: 370px) { .report { padding: 18px; } .metric-grid { grid-template-columns: 1fr; } }
</style>
