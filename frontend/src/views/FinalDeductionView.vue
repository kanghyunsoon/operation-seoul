<template>
  <main class="deduction-page">
    <CaseFileTabMenu :episode-id="episodeId" active="deduction" />

    <section v-if="loading" class="panel">최종 추리 세션을 여는 중입니다.</section>

    <section v-else class="panel">
      <div class="elapsed-timer" aria-live="polite">
        <span>PLAY TIME</span>
        <strong>{{ totalElapsedLabel }}</strong>
      </div>
      <button type="button" class="case-file-fab" @click="clueDialogOpen = true" aria-label="미션 파일 열기">
        <span>📁</span>
        <strong>MISSION FILE</strong>
        <em>단서</em>
      </button>

      <p class="eyebrow">FINAL DEDUCTION</p>
      <h1>{{ finalDeductionTitle }}</h1>
      <p class="notice">{{ startData.message || message }}</p>

      <div class="counter-row">
        <div class="counter time-counter">진행 시간 {{ totalElapsedLabel }}</div>
        <div class="counter">질문 {{ startData.currentQuestionCount || 0 }}/{{ isAdminQuestionMode ? '∞' : (startData.maxQuestionCount || 20) }}</div>
        <div class="counter">가설 {{ startData.currentHypothesisCount || 0 }}/{{ startData.maxHypothesisCount || 2 }}</div>
        <div class="counter muted">남은 질문 {{ isAdminQuestionMode ? '관리자 무제한' : remainingQuestions }}</div>
        <div class="counter penalty">패널티 {{ penaltyMinutes }}분</div>
      </div>

      <section class="rules">
        <strong>바다거북스프 추리 규칙</strong>
        <p>일반 질문은 AI가 사건 맥락과 수집 단서를 읽고 예/아니오/부분적/판정 불가 범위로 답합니다.</p>
        <p>정답 키워드나 용의자를 직접 특정하는 질문은 차단됩니다. 가설 검증은 맞은 개수만 알려줍니다.</p>
        <p>질문은 +1분, 가설 검증은 +5분, 최종 오답은 +5분이 클리어타임에 추가됩니다.</p>
      </section>

      <section v-if="!startData.sessionId" class="blocked">
        <strong>최종 추리 조건 미충족</strong>
        <p>조사 미션 8개 완료 후 자동 공개된 최종 정답 입력 장소에 도착해야 최종 추리를 시작할 수 있습니다.</p>
        <div>
          <button type="button" @click="router.push({ name: 'EpisodeMap', params: { episodeId }, query: preservedQuery })">지도로 돌아가기</button>
          <button type="button" class="secondary" @click="router.push({ name: 'EpisodeCaseFile', params: { episodeId }, query: preservedQuery })">사건 파일 확인</button>
        </div>
      </section>

      <template v-else>
        <section ref="historyRef" class="history">
          <div class="history-head">
            <div>
              <strong>채팅 내역</strong>
              <p>질문과 AI 답변 기록을 보면서 최종 가설을 좁혀가세요.</p>
            </div>
            <button type="button" class="ghost" @click="scrollHistoryToBottom">최신 보기</button>
          </div>
          <article v-for="item in questions" :key="item.id || item.localId || `${item.userQuestion}-${item.aiAnswerText}`" class="qa">
            <p class="q">Q. {{ item.userQuestion }}</p>
            <p class="a">
              {{ item.aiAnswerText }}<span v-if="item.typing" class="typing-cursor">▌</span>
            </p>
            <span>{{ item.typing ? '답변 작성 중' : answerTypeLabel(item.aiAnswerType) }}</span>
          </article>
          <p v-if="!questions.length" class="empty-history">아직 질문 기록이 없습니다. 단서를 바탕으로 예/아니오 질문을 입력해 보세요.</p>
        </section>

        <section class="actions-grid">
          <form class="ask" @submit.prevent="ask">
            <label>
              최종 추리 질문
              <input v-model.trim="question" :disabled="asking || !canAskQuestion" placeholder="예: 피해자의 알리바이와 관련 있나요?" />
            </label>
            <span class="penalty-anchor">
              <button type="submit" :disabled="asking || !canAskQuestion || !question">{{ asking ? '응답 중' : '질문' }}</button>
              <b v-for="item in penaltyEffects.question" :key="item.id" class="penalty-float">+{{ item.minutes }}</b>
            </span>
          </form>

          <section class="hypothesis-box">
            <div class="hypothesis-head">
              <h3>키워드 확인</h3>
              <span>{{ isAdminHypothesisMode ? '관리자 무제한' : `남은 횟수 ${remainingHypotheses}회` }}</span>
            </div>
            <p>범인, 흉기, 동기, 사인을 한 문장으로 적으면 4개 중 몇 개가 맞았는지만 알려줍니다.</p>
            <form @submit.prevent="verifyHypothesis">
              <input v-model.trim="hypothesis" :disabled="!canVerifyHypothesis" placeholder="예: 이몽룡이 망치로 은폐를 위해 교살했다." />
              <span class="penalty-anchor">
                <button type="submit" :disabled="!canVerifyHypothesis || !hypothesis">검증</button>
                <b v-for="item in penaltyEffects.hypothesis" :key="item.id" class="penalty-float">+{{ item.minutes }}</b>
              </span>
            </form>
            <p v-if="hypothesisResult" class="hypothesis-result">{{ hypothesisResult }}</p>
            <p v-if="!isAdminHypothesisMode && remainingHypotheses <= 0" class="limit-message">가설 검증 횟수를 모두 사용했습니다.</p>
          </section>

          <div class="final-box penalty-anchor wide-anchor">
            <FinalAnswerSubmitBox @submit="submitFinalAnswer" />
            <b v-for="item in penaltyEffects.final" :key="item.id" class="penalty-float final-float">+{{ item.minutes }}</b>
          </div>
        </section>

        <p v-if="!isAdminQuestionMode && remainingQuestions <= 0" class="limit-message">질문 횟수를 모두 사용했습니다. 단서 보드와 질문 기록을 보고 가설 검증 또는 최종 제출을 진행하세요.</p>
      </template>

      <p v-if="message" class="message" :class="{ success: finalCorrect === true, error: finalCorrect === false }">{{ message }}</p>
    </section>

    <div v-if="clueDialogOpen" class="clue-modal-backdrop" @click.self="clueDialogOpen = false">
      <section class="clue-modal" role="dialog" aria-modal="true" aria-label="획득 단서와 용의자">
        <header class="modal-head">
          <div>
            <p class="eyebrow">CASE DOSSIER</p>
            <h2>획득 단서와 용의자</h2>
          </div>
          <button type="button" class="modal-close" @click="clueDialogOpen = false">닫기</button>
        </header>

        <div class="modal-grid">
          <section class="modal-panel">
            <h3>획득 단서</h3>
            <div v-if="popupClues.length" class="clue-list">
              <p v-for="clue in popupClues" :key="clue">{{ clue }}</p>
            </div>
            <p v-else class="empty-modal">수집한 단서가 없습니다. 질문 응답이 제한될 수 있습니다.</p>
          </section>

          <section class="modal-panel">
            <h3>용의자 정보</h3>
            <div v-if="popupSuspects.length" class="suspect-list">
              <article v-for="suspect in popupSuspects" :key="suspect.suspectId || `${suspect.displayName}-${suspect.alias}`" class="suspect-mini" :class="{ locked: !suspect.unlocked }">
                <div class="suspect-title">
                  <strong>{{ suspect.displayName || '이름 미확인 인물' }}</strong>
                  <span>{{ suspect.alias || '직업 미확인' }}</span>
                </div>
                <p>{{ suspect.relationToVictim || suspect.shortDescription || '사건과 연결된 인물입니다.' }}</p>
                <dl>
                  <dt>의심 포인트</dt><dd>{{ suspect.suspiciousPoint || '의심 포인트 미확인' }}</dd>
                  <dt>알리바이</dt><dd>{{ suspect.alibiSummary || '알리바이 미확인' }}</dd>
                </dl>
              </article>
            </div>
            <p v-else class="empty-modal">용의자 정보를 불러오는 중이거나 아직 공개된 용의자가 없습니다.</p>
          </section>
        </div>
      </section>
    </div>
  </main>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { episodeApi } from '@/api/episodeApi';
import { caseFileApi } from '@/api/caseFileApi';
import { useTypingBuffer } from '@/composables/useTypingBuffer';
import FinalAnswerSubmitBox from '@/components/episode/FinalAnswerSubmitBox.vue';
import CaseFileTabMenu from '@/components/episode/CaseFileTabMenu.vue';

const route = useRoute();
const router = useRouter();
const episodeId = route.params.episodeId;
const preservedQuery = computed(() => route.query.areaCode ? { areaCode: route.query.areaCode } : {});
const loading = ref(true);
const asking = ref(false);
const clueDialogOpen = ref(false);
const caseFile = ref(null);
const historyRef = ref(null);
const startData = ref({ collectedClues: [], maxHypothesisCount: 2, currentHypothesisCount: 0, clearTimePenaltySeconds: 0 });
const activeElapsedSeconds = ref(0);
const questions = ref([]);
const question = ref('');
const hypothesis = ref('');
const hypothesisResult = ref('');
const message = ref('');
const finalCorrect = ref(null);
const typingTarget = ref(null);
const typingBuffer = useTypingBuffer(18);
const penaltyEffects = ref({ question: [], hypothesis: [], final: [] });
let elapsedTimer = null;
let elapsedSaveTimer = null;

const remainingQuestions = computed(() => Math.max(0, (startData.value.maxQuestionCount || 20) - (startData.value.currentQuestionCount || 0)));
const remainingHypotheses = computed(() => Math.max(0, (startData.value.maxHypothesisCount || 2) - (startData.value.currentHypothesisCount || 0)));
const isAdminQuestionMode = computed(() => (startData.value.maxQuestionCount || 0) >= 999);
const isAdminHypothesisMode = computed(() => (startData.value.maxHypothesisCount || 0) >= 999);
const canAskQuestion = computed(() => Boolean(startData.value.sessionId) && (isAdminQuestionMode.value || remainingQuestions.value > 0));
const canVerifyHypothesis = computed(() => Boolean(startData.value.sessionId) && (isAdminHypothesisMode.value || remainingHypotheses.value > 0));
const penaltyMinutes = computed(() => Math.floor((startData.value.clearTimePenaltySeconds || 0) / 60));
const totalElapsedSeconds = computed(() => activeElapsedSeconds.value + (startData.value.clearTimePenaltySeconds || 0));
const totalElapsedLabel = computed(() => formatElapsed(totalElapsedSeconds.value));
const finalDeductionTitle = computed(() => {
  if (!startData.value.sessionId) {
    return startData.value.finalQuestion || '최종 추리를 시작할 수 없습니다.';
  }
  return 'AI 문답 추리를 통해 범인, 흉기, 동기, 사인을 하나씩 밝혀내세요.';
});

const popupClues = computed(() => {
  const summary = caseFile.value?.clueSummary || {};
  return uniqueValues([
    ...(startData.value.collectedClues || []),
    ...(summary.culpritClues || []),
    ...(summary.weaponClues || []),
    ...(summary.motiveClues || []),
    ...(summary.methodClues || []),
    ...(summary.answerClues || []),
    ...(summary.destinationClues || []),
    ...(summary.storyClues || []),
    ...(caseFile.value?.evidences || [])
      .filter((evidence) => evidence.unlocked)
      .flatMap((evidence) => [evidence.title, evidence.textSummary])
  ]).filter((clue) => !isGenericEvidenceTitle(clue));
});
const popupSuspects = computed(() => (caseFile.value?.suspects || []).filter((suspect) => suspect.displayName || suspect.alias));

watch(typingBuffer.displayedText, (value) => {
  if (!typingTarget.value) return;
  typingTarget.value.aiAnswerText = value;
  scrollHistoryToBottom();
});

watch(typingBuffer.isFinished, (finished) => {
  if (!finished || !typingTarget.value) return;
  typingTarget.value.typing = false;
  typingTarget.value = null;
  asking.value = false;
  scrollHistoryToBottom();
});

onMounted(async () => {
  loadCaseFileForPopup();
  try {
    startData.value = await episodeApi.startDeduction(episodeId);
    syncElapsedFromStartData();
    startElapsedTimer();
    window.addEventListener('visibilitychange', handleElapsedVisibility);
    window.addEventListener('pagehide', handleElapsedPageHide);
    if (startData.value.sessionId) {
      questions.value = await episodeApi.getDeductionQuestions(startData.value.sessionId);
      scrollHistoryToBottom();
    }
  } catch (error) {
    message.value = error.userMessage || '최종 추리를 시작할 수 없습니다.';
  } finally {
    loading.value = false;
  }
});

onUnmounted(() => {
  window.removeEventListener('visibilitychange', handleElapsedVisibility);
  window.removeEventListener('pagehide', handleElapsedPageHide);
  stopElapsedTimer();
});

async function loadCaseFileForPopup() {
  try {
    caseFile.value = await caseFileApi.getCaseFile(episodeId);
  } catch (error) {
    caseFile.value = null;
  }
}

async function ask() {
  if (!question.value || !startData.value.sessionId || !canAskQuestion.value || asking.value) return;
  asking.value = true;
  typingBuffer.reset();
  const askedQuestion = question.value;
  const placeholder = {
    localId: `local-${Date.now()}`,
    userQuestion: askedQuestion,
    aiAnswerText: '',
    aiAnswerType: 'UNKNOWN',
    typing: true
  };
  questions.value.push(placeholder);
  question.value = '';
  message.value = '';
  finalCorrect.value = null;
  scrollHistoryToBottom();

  try {
    const answer = await episodeApi.askDeduction(startData.value.sessionId, askedQuestion);
    placeholder.aiAnswerType = answer.answerType;
    startData.value.currentQuestionCount = answer.questionCount;
    startData.value.clearTimePenaltySeconds = answer.clearTimePenaltySeconds ?? startData.value.clearTimePenaltySeconds;
    persistElapsedTime();
    triggerPenalty('question', 1);
    typeAnswerInto(placeholder, answer.answerText || '판정 가능한 답변을 생성하지 못했습니다.');
  } catch (error) {
    finalCorrect.value = false;
    typeAnswerInto(placeholder, error.userMessage || '질문을 제출할 수 없습니다.');
  }
}

function typeAnswerInto(target, text) {
  typingTarget.value = target;
  typingBuffer.reset();
  typingBuffer.addChunk(text);
  typingBuffer.finishTyping();
}

async function verifyHypothesis() {
  if (!hypothesis.value || !startData.value.sessionId || !canVerifyHypothesis.value) return;
  try {
    const result = await episodeApi.verifyDeductionHypothesis(startData.value.sessionId, hypothesis.value);
    startData.value.currentHypothesisCount = result.hypothesisCount;
    startData.value.clearTimePenaltySeconds = result.clearTimePenaltySeconds ?? startData.value.clearTimePenaltySeconds;
    persistElapsedTime();
    hypothesisResult.value = result.message || `4개 정답 요소 중 ${result.matchedSlotCount || 0}개가 맞습니다.`;
    triggerPenalty('hypothesis', 5);
    hypothesis.value = '';
    message.value = '';
    finalCorrect.value = null;
  } catch (error) {
    message.value = error.userMessage || '가설을 검증할 수 없습니다.';
    finalCorrect.value = false;
  }
}

async function submitFinalAnswer(finalAnswer) {
  try {
    const result = await episodeApi.submitFinalAnswer(episodeId, startData.value.sessionId, finalAnswer);
    startData.value.clearTimePenaltySeconds = result.clearTimePenaltySeconds ?? startData.value.clearTimePenaltySeconds;
    await persistElapsedTime();
    message.value = result.message;
    finalCorrect.value = result.correct;
    if (!result.correct) {
      triggerPenalty('final', 5);
    }
    if (result.correct) {
      stopElapsedTimer(false);
      router.push({ name: 'EpisodeDebriefing', params: { episodeId }, query: preservedQuery.value });
    }
  } catch (error) {
    message.value = error.userMessage || '최종 정답을 제출할 수 없습니다.';
    finalCorrect.value = false;
  }
}

function triggerPenalty(type, minutes) {
  const id = `${type}-${Date.now()}-${Math.random().toString(16).slice(2)}`;
  penaltyEffects.value[type] = [...penaltyEffects.value[type], { id, minutes }];
  window.setTimeout(() => {
    penaltyEffects.value[type] = penaltyEffects.value[type].filter((item) => item.id !== id);
  }, 1650);
}

function elapsedStorageKey() {
  return `operation-korea:episode:${episodeId}:active-elapsed-seconds`;
}

function elapsedLastSeenStorageKey() {
  return `operation-korea:episode:${episodeId}:active-elapsed-last-seen`;
}

function syncElapsedFromStartData() {
  const serverElapsed = Number(startData.value?.activeElapsedSeconds || 0);
  const localElapsed = Number(window.localStorage.getItem(elapsedStorageKey()) || 0);
  const lastSeenAt = Number(window.localStorage.getItem(elapsedLastSeenStorageKey()) || 0);
  const offlineDelta = lastSeenAt > 0 ? Math.max(0, Math.floor((Date.now() - lastSeenAt) / 1000)) : 0;
  const localElapsedWithDelta = localElapsed > 0 ? localElapsed + offlineDelta : 0;
  activeElapsedSeconds.value = Math.max(Number(activeElapsedSeconds.value || 0), serverElapsed, localElapsedWithDelta);
  rememberElapsedTime();
}

function startElapsedTimer() {
  if (!startData.value.sessionId || elapsedTimer || document.hidden) return;
  elapsedTimer = window.setInterval(() => {
    activeElapsedSeconds.value += 1;
    rememberElapsedTime();
  }, 1000);
  elapsedSaveTimer = window.setInterval(() => {
    persistElapsedTime();
  }, 10000);
}

function handleElapsedVisibility() {
  if (document.hidden) {
    stopElapsedTimer();
  } else {
    syncElapsedFromStartData();
    startElapsedTimer();
  }
}

function handleElapsedPageHide() {
  stopElapsedTimer();
}

function stopElapsedTimer(shouldPersist = true) {
  clearInterval(elapsedTimer);
  clearInterval(elapsedSaveTimer);
  elapsedTimer = null;
  elapsedSaveTimer = null;
  rememberElapsedTime();
  if (shouldPersist) persistElapsedTime();
}

function rememberElapsedTime() {
  window.localStorage.setItem(elapsedStorageKey(), String(Math.max(0, Math.floor(Number(activeElapsedSeconds.value || 0)))));
  window.localStorage.setItem(elapsedLastSeenStorageKey(), String(Date.now()));
}

async function persistElapsedTime() {
  const elapsedSeconds = Math.max(0, Math.floor(Number(activeElapsedSeconds.value || 0)));
  try {
    const updated = await episodeApi.updateElapsedTime(episodeId, elapsedSeconds);
    const serverElapsed = Number(updated?.activeElapsedSeconds || 0);
    if (serverElapsed > activeElapsedSeconds.value) {
      activeElapsedSeconds.value = serverElapsed;
      rememberElapsedTime();
    }
  } catch {
    rememberElapsedTime();
  }
}

function formatElapsed(seconds) {
  const total = Math.max(0, Math.floor(Number(seconds || 0)));
  const hours = Math.floor(total / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  const remainSeconds = total % 60;
  if (hours > 0) return `${hours}시간 ${String(minutes).padStart(2, '0')}분 ${String(remainSeconds).padStart(2, '0')}초`;
  return `${minutes}분 ${String(remainSeconds).padStart(2, '0')}초`;
}

function scrollHistoryToBottom() {
  nextTick(() => {
    const element = historyRef.value;
    if (element) {
      element.scrollTop = element.scrollHeight;
    }
  });
}

function uniqueValues(values) {
  const seen = new Set();
  return values
    .map((value) => String(value || '').trim())
    .filter(Boolean)
    .filter((value) => {
      const key = value.replace(/\s+/g, '').toLowerCase();
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    });
}

function isGenericEvidenceTitle(value) {
  return /^사건\s*자료\s*\d+$/i.test(String(value || '').trim());
}

function answerTypeLabel(type) {
  return ({
    YES: '예',
    NO: '아니오',
    PARTIAL: '부분적',
    UNKNOWN: '판정 불가',
    RELATED: '관련 있음',
    NOT_RELATED: '관련 없음',
    AMBIGUOUS: '질문 모호',
    INSUFFICIENT_CLUE: '단서 부족',
    REFUSED_DIRECT_REVEAL: '직접 정답 확인 차단'
  }[type] || type);
}
</script>

<style scoped>
.deduction-page { min-height: 100vh; box-sizing: border-box; padding: 14px 12px 48px; background: radial-gradient(circle at top, rgba(127,29,29,.24), transparent 34%), #020617; color: #f8fafc; font-family: 'Noto Sans KR', Georgia, serif; overflow-x: hidden; }
.panel { position: relative; width: min(100%, 1120px); box-sizing: border-box; margin: 0 auto; padding: 24px; border: 1px solid rgba(248,113,113,.24); border-radius: 24px; background: rgba(15,23,42,.86); box-shadow: 0 24px 70px rgba(0,0,0,.34); }
.elapsed-timer { position: absolute; top: 14px; right: 14px; z-index: 8; display: grid; gap: 2px; min-width: 126px; box-sizing: border-box; padding: 9px 11px; border: 1px solid rgba(103,232,249,.34); border-radius: 14px; background: rgba(2,6,23,.82); color: #e0f2fe; box-shadow: 0 12px 28px rgba(0,0,0,.28); text-align: right; pointer-events: none; }
.elapsed-timer span { color: #67e8f9; font-size: .66rem; font-weight: 1000; letter-spacing: .12em; }
.elapsed-timer strong { color: #fff; font-size: .96rem; font-weight: 1000; }
.eyebrow { margin: 0 0 8px; color: #fca5a5; font-size: .74rem; font-weight: 1000; letter-spacing: .14em; }
h1 { margin: 0 0 12px; font-size: clamp(1.45rem, 4vw, 2.7rem); line-height: 1.2; word-break: keep-all; overflow-wrap: anywhere; }
.notice { color: #fed7aa; line-height: 1.55; }
.counter-row { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 8px; margin: 12px 0; }
.counter { padding: 10px; border-radius: 12px; background: rgba(2,6,23,.36); color: #e2e8f0; font-weight: 900; text-align: center; }
.counter.muted { color: #cbd5e1; }
.counter.penalty { color: #fde68a; }
.counter.time-counter { display: none; }
.rules, .hypothesis-box { display: grid; gap: 6px; padding: 13px; border: 1px solid rgba(248,113,113,.22); border-radius: 16px; background: rgba(127,29,29,.14); }
.rules p, .hypothesis-box p { margin: 0; color: #fecaca; font-size: .84rem; line-height: 1.5; }
.blocked { display: grid; gap: 10px; margin-top: 14px; padding: 14px; border: 1px dashed rgba(248,113,113,.35); border-radius: 16px; background: rgba(127,29,29,.16); }
.blocked p { margin: 0; color: #fecaca; line-height: 1.55; }
.blocked div { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.history { display: grid; gap: 10px; max-height: 46vh; margin: 14px 0; padding: 14px; overflow: auto; border: 1px solid rgba(148,163,184,.2); border-radius: 18px; background: rgba(2,6,23,.34); scroll-behavior: smooth; }
.history-head { display: flex; justify-content: space-between; gap: 12px; align-items: center; position: sticky; top: -14px; z-index: 1; margin: -14px -14px 0; padding: 14px; background: linear-gradient(180deg, rgba(15,23,42,.96), rgba(15,23,42,.84)); border-bottom: 1px solid rgba(148,163,184,.16); }
.history-head p { margin: 4px 0 0; color: #94a3b8; font-size: .82rem; }
.qa { padding: 13px; border-radius: 14px; background: rgba(30,41,59,.68); }
.qa p { margin: 0 0 7px; line-height: 1.55; }
.q { color: #e0f2fe; font-weight: 800; }
.a { min-height: 1.55em; color: #fee2e2; white-space: pre-wrap; }
.qa span { color: #fca5a5; font-size: .75rem; font-weight: 900; }
.typing-cursor { display: inline-block; margin-left: 2px; color: #fde68a; animation: blink 1s step-end infinite; }
.empty-history { margin: 0; padding: 14px; border-radius: 14px; background: rgba(30,41,59,.42); color: #94a3b8; }
.actions-grid { display: grid; grid-template-columns: 1fr; gap: 14px; align-items: start; }
.ask { display: grid; grid-template-columns: 1fr 86px; gap: 8px; padding: 13px; border: 1px solid rgba(148,163,184,.18); border-radius: 16px; background: rgba(15,23,42,.56); }
.ask label { display: grid; gap: 7px; color: #fecaca; font-weight: 900; }
.hypothesis-box form { display: grid; grid-template-columns: 1fr 76px; gap: 8px; margin-top: 10px; }
.hypothesis-head { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
.hypothesis-head h3 { margin: 0; color: #fecaca; }
.hypothesis-head span { border-radius: 999px; padding: 5px 9px; background: rgba(245,158,11,.16); color: #fde68a; font-size: .76rem; font-weight: 900; }
.hypothesis-result { padding: 10px; border-radius: 12px; background: rgba(245,158,11,.16); color: #fde68a !important; font-weight: 900; }
.final-box { grid-column: 1 / -1; }
.penalty-anchor { position: relative; display: grid; align-items: end; }
.penalty-anchor button { width: 100%; }
.wide-anchor { display: block; }
.penalty-float { position: absolute; left: 50%; bottom: calc(100% + 6px); z-index: 4; transform: translateX(-50%); pointer-events: none; color: #fee2e2; font-size: 1.2rem; font-weight: 1000; text-shadow: 0 2px 0 #7f1d1d, 0 0 14px rgba(248,113,113,.84); animation: penalty-rise 1.65s cubic-bezier(.18,.9,.24,1) forwards; }
.final-float { top: -12px; bottom: auto; }
input { min-height: 44px; border: 1px solid rgba(148,163,184,.32); border-radius: 12px; background: rgba(2,6,23,.58); color: #fff; padding: 0 12px; font: inherit; }
button { min-height: 42px; border: 0; border-radius: 12px; background: #b91c1c; color: white; font: inherit; font-weight: 900; cursor: pointer; }
button:disabled { opacity: .45; cursor: not-allowed; }
.secondary, .ghost { background: #334155; }
.ghost { min-height: 34px; padding: 0 11px; font-size: .82rem; }
.limit-message { margin: 10px 0; padding: 10px; border-radius: 12px; background: rgba(120,53,15,.28); color: #fde68a; font-size: .84rem; }
.message { margin-top: 14px; padding: 12px; border-radius: 14px; background: rgba(30,64,175,.22); color: #bfdbfe; }
.message.success { background: rgba(22,163,74,.16); color: #bbf7d0; }
.message.error { background: rgba(220,38,38,.18); color: #fecaca; }
.case-file-fab { position: absolute; right: -74px; top: 50%; z-index: 6; display: grid; place-items: center; gap: 1px; width: 88px; height: 82px; border: 1px solid rgba(248,113,113,.34); border-radius: 20px; background: linear-gradient(145deg, rgba(30,41,59,.96), rgba(127,29,29,.72)); color: #fee2e2; box-shadow: 0 16px 38px rgba(0,0,0,.34), inset 0 1px 0 rgba(255,255,255,.08); transform: translateY(-50%); }
.case-file-fab span { font-size: 1.6rem; line-height: 1; filter: drop-shadow(0 2px 6px rgba(0,0,0,.35)); }
.case-file-fab strong { color: #fecaca; font-size: .62rem; letter-spacing: .08em; }
.case-file-fab em { color: #cbd5e1; font-size: .72rem; font-style: normal; font-weight: 900; }
.clue-modal-backdrop { position: fixed; inset: 0; z-index: 40; display: grid; place-items: center; padding: 24px; background: rgba(2,6,23,.78); backdrop-filter: blur(7px); }
.clue-modal { width: min(100%, 980px); max-height: min(82vh, 760px); overflow: auto; border: 1px solid rgba(245,158,11,.3); border-radius: 24px; background: #111827; box-shadow: 0 30px 90px rgba(0,0,0,.52); }
.modal-head { position: sticky; top: 0; z-index: 1; display: flex; justify-content: space-between; align-items: center; gap: 12px; padding: 18px; background: linear-gradient(180deg, rgba(17,24,39,.98), rgba(17,24,39,.9)); border-bottom: 1px solid rgba(245,158,11,.18); }
.modal-head h2 { margin: 0; font-size: 1.45rem; }
.modal-close { min-height: 36px; padding: 0 13px; background: #334155; }
.modal-grid { display: grid; grid-template-columns: minmax(0, .9fr) minmax(0, 1.1fr); gap: 14px; padding: 18px; }
.modal-panel { display: grid; align-content: start; gap: 12px; min-height: 240px; padding: 14px; border-radius: 18px; background: rgba(2,6,23,.42); border: 1px solid rgba(148,163,184,.16); }
.modal-panel h3 { margin: 0; color: #fde68a; }
.clue-list { display: grid; gap: 8px; }
.clue-list p { margin: 0; padding: 8px 0 8px 12px; border-left: 3px solid rgba(251,191,36,.5); color: #f3f4f6; background: transparent; font-size: .94rem; font-weight: 750; line-height: 1.62; word-break: keep-all; overflow-wrap: anywhere; }
.suspect-list { display: grid; gap: 10px; }
.suspect-mini { display: grid; gap: 8px; padding: 12px; border-radius: 14px; background: rgba(30,41,59,.66); }
.suspect-mini.locked { opacity: .72; }
.suspect-title { display: flex; justify-content: space-between; gap: 8px; align-items: center; }
.suspect-title strong { color: #fff; font-size: 1.02rem; }
.suspect-title span { color: #fde68a; font-size: .82rem; font-weight: 900; }
.suspect-mini p { margin: 0; color: #cbd5e1; line-height: 1.45; }
dl { display: grid; grid-template-columns: 76px minmax(0, 1fr); gap: 5px 8px; margin: 0; font-size: .82rem; }
dt { color: #fca5a5; font-weight: 900; }
dd { margin: 0; color: #e2e8f0; line-height: 1.42; }
.empty-modal { margin: 0; color: #94a3b8; line-height: 1.5; }
@keyframes blink { 50% { opacity: 0; } }
@keyframes penalty-rise { 0% { opacity: 0; transform: translate(-50%, 12px) scale(.8); } 12% { opacity: 1; transform: translate(-50%, 0) scale(1.08); } 62% { opacity: 1; transform: translate(-50%, -24px) scale(1); } 100% { opacity: 0; transform: translate(-50%, -54px) scale(.98); } }
@media (max-width: 900px) { .panel { width: min(100%, 620px); padding: 18px; } .counter-row, .actions-grid, .blocked div, .ask, .hypothesis-box form, .modal-grid { grid-template-columns: 1fr; } .history { max-height: 42vh; } .case-file-fab { position: static; width: 100%; height: 62px; grid-template-columns: auto auto auto; justify-content: center; gap: 8px; margin-bottom: 14px; transform: none; } }
</style>
