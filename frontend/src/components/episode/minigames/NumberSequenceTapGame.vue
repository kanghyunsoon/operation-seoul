<template>
  <div class="number-tap-game">
    <p>1부터 9까지 순서대로 누르되, 조건을 정확히 지키세요.</p>
    <div class="timer">남은 시간 {{ remainingSeconds }}초</div>
    <div class="rules">
      <span v-for="number in skipNumbers" :key="`skip-${number}`">{{ number }} 건너뛰기</span>
      <span v-for="number in doubleNumbers" :key="`double-${number}`">{{ number }} 두 번 누르기</span>
    </div>
    <div v-if="failed" class="fail-mark">X<br><small>틀렸습니다</small></div>
    <div v-else class="pressed-line">입력: {{ pressed.join(' ') || '아직 없음' }}</div>
    <div class="num-grid">
      <button v-for="number in buttons" :key="number" type="button" :disabled="failed || completed" @click="push(number)">{{ number }}</button>
    </div>
    <button v-if="failed || completed || remainingMs <= 0" type="button" class="ghost" @click="reset">초기화</button>
  </div>
</template>
<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue';

const props = defineProps({ config: { type: Object, default: () => ({}) } });
const emit = defineEmits(['solved-change', 'proof-change', 'auto-submit']);

const baseSequence = computed(() => props.config.sequence || [1, 2, 3, 4, 5, 6, 7, 8, 9]);
const skipNumbers = computed(() => normalizeRuleList(props.config.skipNumbers || props.config.skipNumber, [3]));
const doubleNumbers = computed(() => normalizeRuleList(props.config.doubleNumbers || props.config.doubleNumber, [7]));
const buttons = computed(() => props.config.buttons || [1, 2, 3, 4, 5, 6, 7, 8, 9]);
const durationSeconds = computed(() => Number(props.config.durationSeconds || 7));
const expected = computed(() => baseSequence.value.flatMap((number) => {
  const value = Number(number);
  if (skipNumbers.value.includes(value)) return [];
  if (doubleNumbers.value.includes(value)) return [value, value];
  return [value];
}));
const pressed = ref([]);
const failed = ref(false);
const completed = ref(false);
const started = ref(false);
const remainingMs = ref(durationSeconds.value * 1000);
let timer = null;
const remainingSeconds = computed(() => Math.max(0, Math.ceil(remainingMs.value / 1000)));

watch([baseSequence, skipNumbers, doubleNumbers], reset, { immediate: true });
watch(pressed, () => {
  const proof = pressed.value.join(',');
  emit('proof-change', `MG|NUMBER_SEQUENCE_TAP|${proof}`);
  emit('solved-change', proof === expected.value.join(','));
}, { deep: true });

function push(number) {
  if (failed.value || completed.value) return;
  if (!started.value) start();
  const value = Number(number);
  const expectedValue = expected.value[pressed.value.length];
  if (value !== expectedValue) {
    fail();
    return;
  }
  pressed.value = [...pressed.value, value];
  if (pressed.value.length === expected.value.length) {
    completed.value = true;
    clearInterval(timer);
    const proof = `MG|NUMBER_SEQUENCE_TAP|${pressed.value.join(',')}`;
    emit('proof-change', proof);
    emit('solved-change', true);
    emit('auto-submit', proof);
  }
}

function start() {
  started.value = true;
  const endsAt = Date.now() + remainingMs.value;
  clearInterval(timer);
  timer = window.setInterval(() => {
    remainingMs.value = Math.max(0, endsAt - Date.now());
    if (remainingMs.value <= 0) fail();
  }, 250);
}

function fail() {
  failed.value = true;
  clearInterval(timer);
  emit('proof-change', '');
  emit('solved-change', false);
}

function reset() {
  clearInterval(timer);
  pressed.value = [];
  failed.value = false;
  completed.value = false;
  started.value = false;
  remainingMs.value = durationSeconds.value * 1000;
  emit('proof-change', '');
  emit('solved-change', false);
}

function normalizeRuleList(value, fallback) {
  const source = Array.isArray(value) ? value : [value];
  const list = source.map(Number).filter(number => Number.isFinite(number));
  return list.length ? list.slice(0, 2) : fallback;
}

onBeforeUnmount(() => clearInterval(timer));
</script>
<style scoped>
.number-tap-game { display: grid; gap: 10px; text-align: center; }
.timer { color: #fde68a; font-weight: 1000; }
.rules { display: flex; flex-wrap: wrap; justify-content: center; gap: 8px; }
.rules span, .pressed-line { border-radius: 10px; padding: 8px 10px; background: rgba(15,23,42,.72); color: #fde68a; font-weight: 900; }
.pressed-line { color: #bfdbfe; }
.fail-mark { padding: 12px; border-radius: 16px; background: rgba(127,29,29,.32); color: #fecaca; font-size: 2.2rem; font-weight: 1000; line-height: 1; }
.fail-mark small { font-size: .9rem; }
.num-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
button { min-height: 48px; border: 0; border-radius: 12px; background: #ea580c; color: #fff; font-size: 1rem; font-weight: 900; padding: 8px; }
button:disabled { opacity: .45; }
.ghost { border: 1px solid rgba(148,163,184,.28); background: rgba(15,23,42,.7); color: #cbd5e1; }
</style>
