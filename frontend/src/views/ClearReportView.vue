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
        <div><span>최종 점수</span><strong>{{ report.score || 0 }}점</strong></div>
        <div><span>소요 시간</span><strong>{{ formatElapsed(report.elapsedSeconds) }}</strong></div>
        <div><span>최종 조사 지점</span><strong>{{ report.finalArrivedSpotName || '기록 없음' }}</strong></div>
      </div>

      <div class="metric-grid">
        <article><strong>{{ report.completedSpotCount || 0 }}/{{ report.totalSpotCount - 1 || 0 }}</strong><span>조사 완료 장소</span></article>
        <article><strong>{{ report.answerClueCount || 0 }}</strong><span>수집한 추리 단서</span></article>
        <article><strong>4</strong><span>도출한 사건 진실</span></article>
        <article><strong>{{ report.deductionQuestionCount || 0 }}</strong><span>최종 추리 질문</span></article>
        <article><strong>{{ report.wrongAnswerCount || 0 }}</strong><span>오답</span></article>
        <article><strong> {{ report.finalGuessCount || 0 }}</strong><span>최종 정답 제출</span></article>
      </div>

      <article class="paper-block fact-block">
        <p class="mode-label">FACT MODE</p>
        <h2>1. 모티브 공개</h2>
        <p>{{ motifDisclosure }}</p>
      </article>

      <article class="paper-block fact-block">
        <h2>2. 실제 장소 해설</h2>
        <div class="history-explanation">
          <p v-for="paragraph in historyExplanationParagraphs" :key="paragraph">{{ paragraph }}</p>
        </div>
      </article>

      <article class="paper-block">
        <h2>수집한 단서</h2>
        <div v-for="slot in deductionSlots" :key="slot.id" class="clue-section">
          <h3>{{ slot.label }} 추리</h3>
          <div class="clue-list">
            <p v-for="clue in slot.clues" :key="`${slot.id}-${clue}`">{{ clue }}</p>
            <em v-if="!slot.clues.length">분류된 단서 없음</em>
          </div>
        </div>
        <div class="clue-section">

        </div>
      </article>

      <article class="paper-block subdued">
        <h2>조사 기록</h2>
        <p>시작: {{ formatDate(report.startedAt) }}</p>
        <p>클리어: {{ formatDate(report.clearedAt) }}</p>

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
const deductionSlots = computed(() => {
  const fallback = uniqueClues(report.value?.answerClues);
  return [
    reportSlot('CULPRIT', '범인', report.value?.culpritClues, fallback, 0),
    reportSlot('WEAPON', '흉기', report.value?.weaponClues, fallback, 1),
    reportSlot('MOTIVE', '동기', report.value?.motiveClues, fallback, 2),
    reportSlot('METHOD', '사인', report.value?.methodClues, fallback, 3)
  ];
});

const motifDisclosure = computed(() => {
  if (motifSection.value) return motifSection.value;
  const finalPlace = report.value?.finalArrivedSpotName || '최종 장소';
  return `이 임무는 [${finalPlace}]의 실제 장소성과 지역적 분위기를 배경 모티브로 구성되었습니다.`;
});

const historyExplanationParagraphs = computed(() => {
  const paragraphs = splitReadableParagraphs(historySection.value || report.value?.actualHistorySummary)
    .filter((paragraph) => !paragraph.includes('이 임무의 실제'));
  return paragraphs.length ? paragraphs : ['실제 장소 해설이 아직 등록되지 않았습니다.'];
});

onMounted(loadReport);

function reportSlot(id, label, explicit, fallback, offset) {
  const clues = uniqueClues(explicit);
  return { id, label, clues: clues.length ? clues : fallback.filter((_, index) => index % 4 === offset) };
}

function uniqueClues(values = []) {
  return [...new Set((values || []).map((value) => String(value || '').trim()).filter(Boolean))];
}

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

function splitReadableParagraphs(value) {
  const text = String(value || '')
    .replace(/\r\n?/g, '\n')
    .replace(/[ \t]+/g, ' ')
    .replace(/\s*([.!?。！？])\s*/g, '$1\n')
    .replace(/\n{2,}/g, '\n')
    .trim();
  const sentences = text
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean);
  const paragraphs = [];
  let current = '';
  for (const sentence of sentences) {
    if (!current) {
      current = sentence;
      continue;
    }
    if ((current + sentence).length < 92) {
      current = `${current} ${sentence}`;
    } else {
      paragraphs.push(current);
      current = sentence;
    }
  }
  if (current) paragraphs.push(current);
  return paragraphs;
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
.report-page { min-height: 100vh; box-sizing: border-box; padding: 22px 16px 42px; background: transparent; color: #f3f6fa; font-family: 'Noto Sans KR', system-ui, sans-serif; }
.report { width: min(100%, 1120px); box-sizing: border-box; margin: 0 auto; padding: clamp(20px, 3vw, 30px); border: 1px solid rgba(36,50,71,.94); border-radius: 12px; background: linear-gradient(145deg, rgba(12,23,38,.96), rgba(8,17,30,.96)); box-shadow: 0 12px 30px rgba(0,0,0,.35); }
.shell { min-height: 260px; display: grid; align-content: center; gap: 12px; }
.stamp { display: inline-block; width: fit-content; transform: rotate(-3deg); border: 2px solid rgba(154,74,80,.88); color: #f2c7ca; background: rgba(110,47,52,.18); padding: 6px 10px; font-weight: 1000; letter-spacing: .05em; }
.eyebrow { margin: 12px 0 0; color: #b9a476; font-size: .76rem; font-weight: 1000; letter-spacing: .14em; }
h1 { margin: 10px 0; color: #f3f6fa !important; font-size: clamp(2.2rem, 6vw, 4rem); line-height: 1.05; word-break: keep-all; }
.question { margin: 0 0 20px; color: #8f9caf !important; line-height: 1.7; font-size: 1.02rem; font-weight: 560; }
.score-card { display: grid; grid-template-columns: 1fr; gap: 10px; margin: 18px 0; }
.score-card div, .metric-grid article, .paper-block { border: 1px solid rgba(36,50,71,.94); border-radius: 12px; background: rgba(8,17,30,.72); }
.score-card div { padding: 16px; display: flex; justify-content: space-between; gap: 12px; align-items: center; }
.score-card span, .metric-grid span { color: #79869a !important; font-size: .86rem; font-weight: 800; }
.score-card strong { color: #b9a476 !important; font-size: 1.22rem; line-height: 1.35; }
.metric-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 9px; margin: 16px 0; }
.metric-grid article { padding: 12px; display: grid; gap: 4px; }
.metric-grid strong { color: #d4dbe6 !important; font-size: 1.3rem; line-height: 1.2; }
.paper-block { margin-top: 16px; padding: clamp(18px, 2.2vw, 24px); }
.paper-block h2 { margin: 0 0 12px; color: #e0e6ef !important; font-size: clamp(1.35rem, 2.4vw, 1.8rem); }
.paper-block p { margin: 8px 0; color: #8996a9 !important; line-height: 1.78; font-size: 1rem; font-weight: 470; }
.paper-block.subdued { background: rgba(8,17,30,.62); }
.fact-block { border-color: rgba(143,106,50,.34); background: rgba(12,23,38,.82); }
.mode-label { margin: 0 0 8px; color: #b9a476; font-size: .75rem; font-weight: 1000; letter-spacing: .14em; }
.history-explanation { display: grid; gap: 9px; margin-top: 10px; }
.history-explanation p { margin: 0; color: #8794a7 !important; line-height: 1.86; font-size: 1.02rem; font-weight: 460; word-break: keep-all; overflow-wrap: anywhere; }
.clue-section { margin-top: 16px; }
.clue-section h3 { margin: 0 0 9px; font-size: 1rem; color: #b9a476 !important; font-weight: 900; }
.clue-list { display: grid; gap: 8px; }
.clue-list p { margin: 0; padding: 8px 0 8px 13px; border-left: 2px solid rgba(143,106,50,.36); color: #8490a1 !important; background: transparent; line-height: 1.72; font-size: .98rem; font-weight: 470; word-break: keep-all; overflow-wrap: anywhere; }
.clue-list em { color: #697789 !important; font-style: normal; }
.actions { display: grid; grid-template-columns: 1fr; gap: 9px; margin-top: 18px; }
button { min-height: 46px; border: 1px solid rgba(36,50,71,.94); border-radius: 8px; background: rgba(8,17,30,.88); color: #f3f6fa; font-weight: 900; }
button.ghost { border: 1px solid rgba(36,50,71,.94); background: transparent; color: #a7b2c3; }
@media (min-width: 640px) { .score-card { grid-template-columns: repeat(3, 1fr); } .score-card div { display: grid; } .metric-grid { grid-template-columns: repeat(3, 1fr); } .actions { grid-template-columns: 1fr 1fr 1fr; } }
@media (max-width: 370px) { .report { padding: 18px; } .metric-grid { grid-template-columns: 1fr; } }
</style>



