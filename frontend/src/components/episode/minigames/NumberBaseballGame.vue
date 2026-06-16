<template>
  <div class="baseball-game">
    <p>서로 다른 {{ digitCount }}자리 숫자를 추리하세요. 위치까지 맞으면 S, 숫자만 맞으면 B입니다.</p>
    <div class="display">{{ guess || `${digitCount}자리 입력` }}</div>
    <div v-if="status" class="result" :class="{ success: solved, fail: failed }">{{ status }}</div>
    <div class="keypad">
      <button v-for="number in buttons" :key="number" type="button" :disabled="used(number) || locked" @click="append(number)">{{ number }}</button>
      <button type="button" :disabled="locked" @click="backspace">←</button>
      <button type="button" :disabled="used(0) || locked" @click="append(0)">0</button>
      <button type="button" :disabled="!isValidGuess || locked" @click="check">기록</button>
    </div>
    <ol class="history">
      <li v-for="(item, index) in history" :key="`${index}-${item.value}`">
        <span>{{ item.value }}</span>
        <strong>{{ item.strikes }}S {{ item.balls }}B {{ item.outs }}O</strong>
      </li>
    </ol>
    <button type="button" class="ghost" @click="reset">초기화</button>
  </div>
</template>
<script setup>
import { computed, ref, watch } from 'vue';

const props = defineProps({ config: { type: Object, default: () => ({}) } });
const emit = defineEmits(['solved-change', 'proof-change', 'auto-submit']);

const solution = computed(() => String(props.config.solution || '427'));
const digitCount = computed(() => Number(props.config.digits || solution.value.length || 3));
const buttons = computed(() => props.config.buttons || [1, 2, 3, 4, 5, 6, 7, 8, 9]);
const guess = ref('');
const history = ref([]);
const locked = ref(false);
const solved = ref(false);
const failed = ref(false);
const status = ref('');
const isValidGuess = computed(() => guess.value.length === digitCount.value && new Set(guess.value.split('')).size === guess.value.length);

watch(solution, reset, { immediate: true });

function append(number) {
  if (locked.value || guess.value.length >= digitCount.value || used(number)) return;
  guess.value += String(number);
}

function backspace() {
  guess.value = guess.value.slice(0, -1);
}

function used(number) {
  return guess.value.includes(String(number));
}

function check() {
  if (!isValidGuess.value) return;
  const value = String(guess.value);
  const result = score(value, solution.value);
  history.value = [{ value, ...result }, ...history.value].slice(0, Number(props.config.maxAttempts || 8));
  const proof = `MG|NUMBER_BASEBALL|${value}`;
  emit('proof-change', proof);
  emit('solved-change', value === solution.value);
  if (value === solution.value) {
    locked.value = true;
    solved.value = true;
    status.value = '정답입니다. 미션 성공을 서버에 제출했습니다.';
    emit('auto-submit', proof);
  } else if (history.value.length >= Number(props.config.maxAttempts || 8)) {
    locked.value = true;
    failed.value = true;
    status.value = '틀렸습니다. 초기화 후 다시 시도하세요.';
  }
  guess.value = '';
}

function score(value, target) {
  let strikes = 0;
  let balls = 0;
  value.split('').forEach((digit, index) => {
    if (target[index] === digit) strikes += 1;
    else if (target.includes(digit)) balls += 1;
  });
  return { strikes, balls, outs: digitCount.value - strikes - balls };
}

function reset() {
  guess.value = '';
  history.value = [];
  locked.value = false;
  solved.value = false;
  failed.value = false;
  status.value = '';
  emit('proof-change', '');
  emit('solved-change', false);
}
</script>
<style scoped>
.baseball-game { display: grid; gap: 10px; text-align: center; }
.display { min-height: 46px; display: grid; place-items: center; border-radius: 12px; background: rgba(15,23,42,.82); color: #fff; font-size: 1.35rem; font-weight: 1000; letter-spacing: .18em; }
.result { border-radius: 12px; padding: 9px 10px; font-weight: 1000; }
.result.success { background: rgba(22,101,52,.32); color: #bbf7d0; }
.result.fail { background: rgba(127,29,29,.32); color: #fecaca; }
.keypad { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
button { min-height: 46px; border: 0; border-radius: 12px; background: #ea580c; color: #fff; font-weight: 900; padding: 8px; }
button:disabled { opacity: .42; }
.history { display: grid; gap: 6px; margin: 0; padding-left: 0; color: #cbd5e1; list-style: none; }
.history li { display: flex; justify-content: space-between; gap: 8px; border-radius: 10px; padding: 8px 10px; background: rgba(15,23,42,.64); }
.history span { color: #f8fafc; font-weight: 900; letter-spacing: .12em; }
.history strong { color: #fde68a; }
.ghost { border: 1px solid rgba(148,163,184,.28); background: rgba(15,23,42,.7); color: #cbd5e1; }
</style>
