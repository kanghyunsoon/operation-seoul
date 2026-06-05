<template>
  <section v-if="puzzle" class="puzzle-card">
    <div class="puzzle-head">
      <span>{{ puzzleTypeLabel(puzzle.puzzleType) }}</span>
      <strong>{{ puzzle.difficulty || 'NORMAL' }}</strong>
    </div>

    <h3>현장 퍼즐</h3>
    <p>{{ puzzle.questionText }}</p>

    <details>
      <summary>힌트 보기</summary>
      <ol>
        <li v-for="(hint, index) in puzzle.hints" :key="`${index}-${hint}`">{{ hint }}</li>
      </ol>
    </details>

    <form @submit.prevent="submit">
      <input v-model.trim="answer" :placeholder="`정답 형식: ${puzzle.answerFormat || 'TEXT'}`" autocomplete="off" />
      <button type="submit">정답 제출</button>
    </form>

    <p v-if="message" class="message" :class="{ success: correct === true, error: correct === false }">{{ message }}</p>
  </section>
</template>

<script setup>
import { ref, watch } from 'vue';

const props = defineProps({
  puzzle: { type: Object, default: null },
  message: { type: String, default: '' },
  correct: { type: Boolean, default: null }
});

const emit = defineEmits(['submit']);
const answer = ref('');

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
  }[type] || type;
}
</script>

<style scoped>
.puzzle-card { position: fixed; left: 50%; bottom: 232px; z-index: 28; width: min(calc(100% - 24px), 410px); max-height: 44vh; overflow: auto; transform: translateX(-50%); box-sizing: border-box; padding: 16px; border: 1px solid rgba(251,146,60,.3); border-radius: 18px; background: rgba(2,6,23,.96); color: #e2e8f0; box-shadow: 0 18px 50px rgba(0,0,0,.35); }
.puzzle-head { display: flex; justify-content: space-between; color: #fb923c; font-size: .74rem; font-weight: 900; }
h3 { margin: 8px 0; color: #fff; }
p { line-height: 1.55; }
summary { cursor: pointer; color: #fcd34d; font-weight: 800; }
li { margin-top: 6px; color: #cbd5e1; }
form { display: grid; grid-template-columns: minmax(0,1fr) auto; gap: 8px; margin-top: 12px; }
input { min-width: 0; border: 1px solid rgba(148,163,184,.28); border-radius: 10px; background: rgba(15,23,42,.9); color: #fff; padding: 11px; }
button { border: 0; border-radius: 10px; background: #ea580c; color: #fff; font-weight: 900; padding: 0 12px; }
.message { margin: 10px 0 0; padding: 9px; border-radius: 10px; font-size: .84rem; }
.success { background: rgba(22,101,52,.22); color: #bbf7d0; }
.error { background: rgba(127,29,29,.22); color: #fecaca; }
@media (max-width: 370px) { form { grid-template-columns: 1fr; } button { min-height: 42px; } }
</style>
