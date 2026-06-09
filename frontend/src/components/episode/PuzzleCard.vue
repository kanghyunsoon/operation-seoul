<template>
  <section v-if="puzzle" class="puzzle-card">
    <div class="puzzle-head">
      <span>{{ puzzleTypeLabel(puzzle.puzzleType) }}</span>
      <strong>{{ puzzle.difficulty || 'NORMAL' }}</strong>
      <button type="button" class="close-btn" aria-label="퍼즐 닫기" @click="$emit('close')">닫기</button>
    </div>

    <h3>현장 퍼즐</h3>
    <p class="guide">{{ puzzleGuide }}</p>
    <p class="question">{{ puzzle.questionText || '이 장소의 퍼즐 질문이 아직 없습니다. 관리자 화면에서 질문을 보강하세요.' }}</p>

    <details class="hint-box">
      <summary>힌트 보기</summary>
      <ol v-if="safeHints.length">
        <li v-for="(hint, index) in safeHints" :key="`${index}-${hint}`">{{ normalizeHint(hint, index) }}</li>
      </ol>
      <p v-else>등록된 힌트가 없습니다. 관리자 화면에서 3단계 힌트를 추가하세요.</p>
    </details>

    <form @submit.prevent="submit">
      <input v-model.trim="answer" :placeholder="`정답 형식: ${puzzle.answerFormat || 'TEXT'}`" autocomplete="off" />
      <button type="submit">정답 제출</button>
    </form>

    <p v-if="message" class="message" :class="{ success: correct === true, error: correct === false }">{{ message }}</p>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue';

const props = defineProps({
  puzzle: { type: Object, default: null },
  message: { type: String, default: '' },
  correct: { type: Boolean, default: null }
});

const emit = defineEmits(['submit', 'close']);
const answer = ref('');

const safeHints = computed(() => {
  const hints = (props.puzzle?.hints || []).filter(Boolean);
  return hints.length ? hints : ['현장 메모와 사건자료 제목을 먼저 비교하세요.', '장소명 글자 추출이 아니라 단서의 의미를 보세요.', '정답은 사건파일에 붙일 짧은 단어입니다.'];
});
const puzzleGuide = computed(() => {
  const type = String(props.puzzle?.puzzleType || '').toUpperCase();
  if (type === 'NUMBER_LOCK') return '관리자가 입력한 현장 숫자가 있을 때만 사용하는 숫자 암호입니다.';
  if (type === 'PATTERN') return '그림이 아니라 단서 카드와 장소 분위기의 반복 패턴을 비교하세요.';
  if (type === 'STORY_COMBINATION') return '현재 장소의 문제 문장과 사건파일의 관련 카드를 대조해 핵심 단어를 입력합니다.';
  if (type === 'INITIAL_SOUND') return '장소명 초성이 아니라 사건 메모 안의 키워드를 기준으로 풉니다.';
  return '현장에서 확인 가능한 단서와 사건파일 자료를 연결해 풉니다.';
});

watch(() => props.puzzle?.puzzleId, () => { answer.value = ''; });

function submit() {
  if (answer.value) emit('submit', answer.value);
}

function puzzleTypeLabel(type) {
  return {
    OBSERVATION: '관찰형',
    NUMBER_LOCK: '숫자 암호',
    INITIAL_SOUND: '초성/언어유희',
    PATTERN: '패턴 추론',
    STORY_COMBINATION: '스토리 조합'
  }[type] || '퍼즐';
}

function normalizeHint(hint, index) {
  const text = String(hint || '').trim();
  if (!text || /^(answer|destination|story)-clue-\d+$/i.test(text) || isEnglishSentence(text) || isRouteDependentHint(text)) {
    return fallbackHint(index);
  }
  return text;
}

function fallbackHint(index) {
  return ['문제 문장에 나온 현장 근거를 먼저 확인하세요.', '정답은 이 장소에서 확인한 단어 또는 숫자여야 합니다.', '다른 장소의 진행 순서가 아니라 현재 퍼즐의 단서만 기준으로 보세요.'][index % 3];
}

function isEnglishSentence(text) {
  const alphabetCount = (text.match(/[A-Za-z]/g) || []).length;
  return alphabetCount >= 3 && !/[가-힣]/.test(text);
}

function isRouteDependentHint(text) {
  const compact = String(text || '').replace(/\s+/g, '');
  return compact.includes('가장최근') || compact.includes('최근보상') || compact.includes('이전증거') || compact.includes('이전사건자료');
}
</script>

<style scoped>
.puzzle-card { position: fixed; left: 50%; bottom: 232px; z-index: 28; width: min(calc(100% - 24px), 410px); max-height: 44vh; overflow: auto; transform: translateX(-50%); box-sizing: border-box; padding: 16px; border: 1px solid rgba(251,146,60,.3); border-radius: 18px; background: rgba(2,6,23,.97); color: #e2e8f0; box-shadow: 0 18px 50px rgba(0,0,0,.35); }
.puzzle-head { display: flex; justify-content: space-between; gap: 8px; align-items: center; color: #fb923c; font-size: .74rem; font-weight: 900; }
h3 { margin: 8px 0; color: #fff; }
.guide { margin: 8px 0; padding: 9px 10px; border-radius: 10px; background: rgba(14,116,144,.2); color: #a5f3fc; font-size: .82rem; line-height: 1.45; }
.question { line-height: 1.6; padding: 12px; border-radius: 12px; background: rgba(15,23,42,.85); color: #f8fafc; }
.hint-box { margin-top: 10px; }
summary { cursor: pointer; color: #fcd34d; font-weight: 800; }
li { margin-top: 6px; color: #cbd5e1; line-height: 1.45; }
form { display: grid; grid-template-columns: minmax(0,1fr) auto; gap: 8px; margin-top: 12px; }
input { min-width: 0; border: 1px solid rgba(148,163,184,.28); border-radius: 10px; background: rgba(15,23,42,.9); color: #fff; padding: 11px; }
button { border: 0; border-radius: 10px; background: #ea580c; color: #fff; font-weight: 900; padding: 0 12px; }
.close-btn { min-height: 30px; margin-left: auto; border: 1px solid rgba(251,146,60,.36); background: rgba(15,23,42,.88); color: #fed7aa; font-size: .74rem; }
.message { margin: 10px 0 0; padding: 9px; border-radius: 10px; font-size: .84rem; }
.success { background: rgba(22,101,52,.22); color: #bbf7d0; }
.error { background: rgba(127,29,29,.22); color: #fecaca; }
@media (min-width: 900px) {
  .puzzle-card { left: 24px; bottom: 24px; transform: none; width: 420px; max-height: calc(100vh - 120px); }
}
@media (max-width: 370px) { form { grid-template-columns: 1fr; } button { min-height: 42px; } }
</style>
