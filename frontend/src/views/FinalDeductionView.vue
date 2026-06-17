<template>
  <main class="deduction-page">
    <CaseFileTabMenu :episode-id="episodeId" active="deduction" />

    <section v-if="loading" class="panel">최종 추리 세션을 여는 중입니다.</section>

    <section v-else class="panel">
      <p class="eyebrow">FINAL DEDUCTION</p>
      <h1>{{ startData.finalQuestion || '최종 추리를 시작할 수 없습니다.' }}</h1>
      <p class="notice">{{ startData.message || message }}</p>

      <div class="counter-row">
        <div class="counter">질문 {{ startData.currentQuestionCount || 0 }}/{{ startData.maxQuestionCount || 20 }}</div>
        <div class="counter muted">남은 질문 {{ remainingQuestions }}</div>
      </div>

      <section class="rules">
        <strong>바다거북 스프형 질문 규칙</strong>
        <p>AI가 아닌 규칙 기반 응답입니다. 관련 있음/없음, 부분적으로 맞음, 질문이 모호함, 단서 부족, 정답 직접 노출 불가 범위로만 답합니다.</p>
        <p>정답이나 실제 최종 장소를 직접 묻는 질문에는 답하지 않습니다.</p>
      </section>

      <section class="clues">
        <strong>사용 가능한 단서</strong>
        <span v-for="clue in startData.collectedClues" :key="clue">{{ clue }}</span>
        <em v-if="!startData.collectedClues?.length">수집한 단서가 없습니다. 질문 응답이 제한될 수 있습니다.</em>
      </section>

      <section v-if="!startData.sessionId" class="blocked">
        <strong>최종 추리 조건 미충족</strong>
        <p>실제 최종 장소에 도착해야 최종 추리 채팅과 정답 제출을 시작할 수 있습니다.</p>
        <p>이 장소에서는 최종 추리를 시작할 수 없습니다. 장소 미션 진행 상태를 다시 확인해 주세요.</p>
        <div>
          <button type="button" @click="router.push({ name: 'EpisodeMap', params: { episodeId } })">지도로 돌아가기</button>
          <button type="button" class="secondary" @click="router.push({ name: 'EpisodeCaseFile', params: { episodeId } })">미션 파일 확인</button>
        </div>
      </section>

      <template v-else>
        <section class="history">
          <article v-for="item in questions" :key="item.id || `${item.userQuestion}-${item.aiAnswerText}`" class="qa">
            <p class="q">Q. {{ item.userQuestion }}</p>
            <p class="a">{{ item.aiAnswerText }}</p>
            <span>{{ answerTypeLabel(item.aiAnswerType) }}</span>
          </article>
          <p v-if="!questions.length" class="empty-history">아직 질문 기록이 없습니다. 수집한 단서를 바탕으로 예/아니오 질문을 입력해 보세요.</p>
        </section>

        <form class="ask" @submit.prevent="ask">
          <input v-model.trim="question" :disabled="remainingQuestions <= 0" placeholder="예: 용의자가 사건과 관련 있나?" />
          <button type="submit" :disabled="remainingQuestions <= 0 || !question">질문</button>
        </form>

        <p v-if="remainingQuestions <= 0" class="limit-message">질문 횟수를 모두 사용했습니다. 단서 보드와 질문 기록을 보고 최종 정답을 제출하세요.</p>

        <FinalAnswerSubmitBox @submit="submitFinalAnswer" />
      </template>

      <p v-if="message" class="message" :class="{ success: finalCorrect === true, error: finalCorrect === false }">{{ message }}</p>
    </section>
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { episodeApi } from '@/api/episodeApi';
import FinalAnswerSubmitBox from '@/components/episode/FinalAnswerSubmitBox.vue';
import CaseFileTabMenu from '@/components/episode/CaseFileTabMenu.vue';

const route = useRoute();
const router = useRouter();
const episodeId = route.params.episodeId;
const loading = ref(true);
const startData = ref({ collectedClues: [] });
const questions = ref([]);
const question = ref('');
const message = ref('');
const finalCorrect = ref(null);

const remainingQuestions = computed(() => Math.max(0, (startData.value.maxQuestionCount || 20) - (startData.value.currentQuestionCount || 0)));

onMounted(async () => {
  try {
    startData.value = await episodeApi.startDeduction(episodeId);
    if (startData.value.sessionId) {
      questions.value = await episodeApi.getDeductionQuestions(startData.value.sessionId);
    }
  } catch (error) {
    message.value = error.userMessage || '최종 추리를 시작할 수 없습니다.';
  } finally {
    loading.value = false;
  }
});

async function ask() {
  if (!question.value || !startData.value.sessionId || remainingQuestions.value <= 0) return;
  try {
    const askedQuestion = question.value;
    const answer = await episodeApi.askDeduction(startData.value.sessionId, askedQuestion);
    questions.value.push({ userQuestion: askedQuestion, aiAnswerText: answer.answerText, aiAnswerType: answer.answerType });
    startData.value.currentQuestionCount = answer.questionCount;
    question.value = '';
    message.value = '';
    finalCorrect.value = null;
  } catch (error) {
    message.value = error.userMessage || '질문을 제출할 수 없습니다.';
    finalCorrect.value = false;
  }
}

async function submitFinalAnswer(finalAnswer) {
  try {
    const result = await episodeApi.submitFinalAnswer(episodeId, startData.value.sessionId, finalAnswer);
    message.value = result.message;
    finalCorrect.value = result.correct;
    if (result.correct) {
      router.push({ name: 'EpisodeClearReport', params: { episodeId } });
    }
  } catch (error) {
    message.value = error.userMessage || '최종 정답을 제출할 수 없습니다.';
    finalCorrect.value = false;
  }
}

function answerTypeLabel(type) {
  return ({
    YES: '예',
    NO: '아니오',
    RELATED: '관련 있음',
    NOT_RELATED: '관련 없음',
    PARTIAL: '부분적으로 맞음',
    AMBIGUOUS: '질문이 모호함',
    INSUFFICIENT_CLUE: '단서 부족',
    REFUSED_DIRECT_REVEAL: '정답 직접 노출 불가'
  }[type] || type);
}
</script>

<style scoped>
.deduction-page { min-height: 100vh; box-sizing: border-box; padding: 14px 12px 40px; background: radial-gradient(circle at top, rgba(127,29,29,.24), transparent 34%), #020617; color: #f8fafc; font-family: 'Noto Sans KR', Georgia, serif; }
.panel { width: min(100%, 430px); box-sizing: border-box; margin: 0 auto; padding: 18px; border: 1px solid rgba(248,113,113,.24); border-radius: 20px; background: rgba(15,23,42,.84); box-shadow: 0 24px 70px rgba(0,0,0,.34); }
.eyebrow { margin: 0 0 8px; color: #fca5a5; font-size: .74rem; font-weight: 1000; letter-spacing: .14em; }
h1 { margin: 0 0 12px; font-size: clamp(1.45rem, 7vw, 2.4rem); line-height: 1.15; }
.notice { color: #fed7aa; line-height: 1.55; }
.counter-row { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; margin: 10px 0; }
.counter { padding: 9px; border-radius: 12px; background: rgba(2,6,23,.36); color: #e2e8f0; font-weight: 900; text-align: center; }
.counter.muted { color: #cbd5e1; }
.rules { display: grid; gap: 6px; padding: 12px; border: 1px solid rgba(248,113,113,.22); border-radius: 16px; background: rgba(127,29,29,.14); }
.rules p { margin: 0; color: #fecaca; font-size: .82rem; line-height: 1.5; }
.clues { display: flex; flex-wrap: wrap; gap: 7px; margin: 14px 0; padding: 14px; border-radius: 16px; background: rgba(2,6,23,.34); }
.clues strong { width: 100%; }
.clues span { border-radius: 999px; padding: 5px 9px; background: rgba(245,158,11,.18); color: #fde68a; font-size: .78rem; font-weight: 900; }
.clues em { color: #94a3b8; font-style: normal; }
.blocked { display: grid; gap: 10px; padding: 14px; border: 1px dashed rgba(248,113,113,.35); border-radius: 16px; background: rgba(127,29,29,.16); }
.blocked p { margin: 0; color: #fecaca; line-height: 1.55; }
.blocked div { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.history { display: grid; gap: 10px; max-height: 38vh; overflow: auto; }
.qa { padding: 12px; border-radius: 14px; background: rgba(30,41,59,.64); }
.qa p { margin: 0 0 7px; line-height: 1.5; }
.q { color: #e0f2fe; }
.a { color: #fee2e2; }
.qa span { color: #fca5a5; font-size: .75rem; font-weight: 900; }
.empty-history { margin: 0; padding: 14px; border-radius: 14px; background: rgba(30,41,59,.42); color: #94a3b8; }
.ask { display: grid; grid-template-columns: 1fr 70px; gap: 8px; margin-top: 14px; }
input { min-height: 44px; border: 1px solid rgba(148,163,184,.32); border-radius: 12px; background: rgba(2,6,23,.58); color: #fff; padding: 0 12px; font: inherit; }
button { min-height: 42px; border: 0; border-radius: 12px; background: #b91c1c; color: white; font: inherit; font-weight: 900; }
button:disabled { opacity: .45; cursor: not-allowed; }
.secondary { background: #334155; }
.limit-message { margin: 10px 0; padding: 10px; border-radius: 12px; background: rgba(120,53,15,.28); color: #fde68a; font-size: .84rem; }
.message { padding: 12px; border-radius: 14px; background: rgba(30,64,175,.22); color: #bfdbfe; }
.message.success { background: rgba(22,163,74,.16); color: #bbf7d0; }
.message.error { background: rgba(220,38,38,.18); color: #fecaca; }
@media (max-width: 380px) { .blocked div, .counter-row, .ask { grid-template-columns: 1fr; } }
</style>
