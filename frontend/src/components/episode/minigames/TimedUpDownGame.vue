<template>
  <div class="up-down-game">
    <p>{{ min }}부터 {{ max }} 사이의 숫자를 제한 시간 안에 맞히세요.</p>
    <div class="status">
      <strong>남은 시간 {{ remainingSeconds }}초</strong>
      <span>범위 {{ low }} ~ {{ high }}</span>
    </div>
    <div class="display">{{ draft || '숫자 입력' }}</div>
    <div v-if="lastHint" class="hint" :class="{ success: lastHint === '정답', fail: lastHint === '시간 초과' }">{{ lastHint }}</div>
    <div class="keypad">
      <button v-for="number in numbers" :key="number" type="button" :disabled="finished" @click="append(number)">{{ number }}</button>
      <button type="button" :disabled="finished" @click="backspace">←</button>
      <button type="button" :disabled="finished" @click="append(0)">0</button>
      <button type="button" :disabled="finished || !draft" @click="check">확인</button>
    </div>
    <button type="button" class="ghost" @click="reset">초기화</button>
  </div>
</template>
<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue';

const props = defineProps({ config: { type: Object, default: () => ({}) } });
const emit = defineEmits(['solved-change', 'proof-change', 'auto-submit']);

const min = computed(() => Number(props.config.min || 1));
const max = computed(() => Number(props.config.max || 100));
const solution = computed(() => Number(props.config.solution || 50));
const durationSeconds = computed(() => Math.min(20, Math.max(8, Number(props.config.durationSeconds || 15))));
const numbers = [1, 2, 3, 4, 5, 6, 7, 8, 9];
const draft = ref('');
const low = ref(min.value);
const high = ref(max.value);
const lastHint = ref('');
const started = ref(false);
const finished = ref(false);
const remainingMs = ref(durationSeconds.value * 1000);
let timer = null;
const remainingSeconds = computed(() => Math.max(0, Math.ceil(remainingMs.value / 1000)));

watch([min, max, solution, durationSeconds], reset, { immediate: true });

function append(number) {
  if (finished.value || draft.value.length >= String(max.value).length) return;
  if (!started.value) start();
  draft.value += String(number);
}

function backspace() {
  draft.value = draft.value.slice(0, -1);
}

function check() {
  const value = Number(draft.value);
  if (!Number.isFinite(value) || finished.value) return;
  if (value < solution.value) {
    lastHint.value = 'UP';
    low.value = Math.max(low.value, value + 1);
  } else if (value > solution.value) {
    lastHint.value = 'DOWN';
    high.value = Math.min(high.value, value - 1);
  } else {
    lastHint.value = '정답';
    finished.value = true;
    clearInterval(timer);
    const proof = `MG|UP_DOWN_TIMER|${value}`;
    emit('proof-change', proof);
    emit('solved-change', true);
    emit('auto-submit', proof);
    return;
  }
  emit('proof-change', `MG|UP_DOWN_TIMER|${value}`);
  emit('solved-change', false);
  draft.value = '';
}

function start() {
  started.value = true;
  const endsAt = Date.now() + remainingMs.value;
  clearInterval(timer);
  timer = window.setInterval(() => {
    remainingMs.value = Math.max(0, endsAt - Date.now());
    if (remainingMs.value <= 0) {
      finished.value = true;
      lastHint.value = '시간 초과';
      clearInterval(timer);
      emit('proof-change', '');
      emit('solved-change', false);
    }
  }, 100);
}

function reset() {
  clearInterval(timer);
  draft.value = '';
  low.value = min.value;
  high.value = max.value;
  lastHint.value = '';
  started.value = false;
  finished.value = false;
  remainingMs.value = durationSeconds.value * 1000;
  emit('proof-change', '');
  emit('solved-change', false);
}

onBeforeUnmount(() => clearInterval(timer));
</script>
<style scoped>
.up-down-game { display: grid; gap: 10px; text-align: center; }
.status { display: flex; justify-content: space-between; gap: 8px; color: #fde68a; font-weight: 900; }
.status span { color: #bfdbfe; }
.display { min-height: 46px; display: grid; place-items: center; border-radius: 12px; background: rgba(15,23,42,.82); color: #fff; font-size: 1.35rem; font-weight: 1000; }
.hint { border-radius: 999px; padding: 7px 10px; background: rgba(14,116,144,.28); color: #a5f3fc; font-weight: 1000; }
.hint.success { background: rgba(22,101,52,.32); color: #bbf7d0; }
.hint.fail { background: rgba(127,29,29,.32); color: #fecaca; }
.keypad { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
button { min-height: 46px; border: 0; border-radius: 12px; background: #ea580c; color: #fff; font-weight: 900; padding: 8px; }
button:disabled { opacity: .45; }
.ghost { border: 1px solid rgba(148,163,184,.28); background: rgba(15,23,42,.7); color: #cbd5e1; }
@media (max-width: 430px) { .status { display: grid; } }
</style>
