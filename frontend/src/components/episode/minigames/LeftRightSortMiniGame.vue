<template>
  <div class="sort-game">
    <div class="hud">
      <span>남은 시간 {{ remainingSeconds }}초</span>
      <strong>정답 {{ correctCount }}/{{ passCorrectCount }}</strong>
      <span>오답 {{ wrongCount }}</span>
    </div>

    <div v-if="finished" class="result" :class="{ success: passed, fail: !passed }">
      {{ passed ? '성공했습니다. 결과를 제출했습니다.' : '틀렸습니다. 초기화 후 다시 시도하세요.' }}
    </div>

    <div v-else class="sort-board" aria-live="polite">
      <div class="destination destination-left" aria-label="왼쪽 목적지">
        <span>{{ emojiForSide('LEFT') }}</span>
      </div>

      <div class="queue-lane" aria-label="동물 대기열">
        <div
          v-for="item in visibleQueue"
          :key="`${currentIndex}-${item.offset}-${item.target.key}`"
          class="queue-item"
          :class="{ current: item.offset === 0 }"
        >
          {{ item.target.emoji }}
        </div>
      </div>

      <div class="destination destination-right" aria-label="오른쪽 목적지">
        <span>{{ emojiForSide('RIGHT') }}</span>
      </div>
    </div>

    <div class="side-buttons" aria-label="분류 방향 선택">
      <button type="button" :disabled="finished" @click="choose('LEFT')">←</button>
      <button type="button" :disabled="finished" @click="choose('RIGHT')">→</button>
    </div>

    <button v-if="finished" type="button" class="ghost" @click="reset">초기화</button>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue';

const props = defineProps({ config: { type: Object, default: () => ({}) } });
const emit = defineEmits(['solved-change', 'proof-change', 'auto-submit']);

const defaultTargets = [
  { key: 'CAT', label: '고양이', emoji: '🐱', correctSide: 'LEFT' },
  { key: 'DOG', label: '강아지', emoji: '🐶', correctSide: 'RIGHT' }
];

const targets = computed(() => normalizeTargets(props.config.targets));
const maxRounds = computed(() => Number(props.config.rounds || 30));
const durationSeconds = computed(() => Number(props.config.durationSeconds || 15));
const passCorrectCount = computed(() => Number(props.config.passCorrectCount || 20));
const currentIndex = ref(0);
const queue = ref([]);
const correctCount = ref(0);
const wrongCount = ref(0);
const finished = ref(false);
const remainingMs = ref(durationSeconds.value * 1000);
const startedAt = ref(Date.now());
let timer = null;

const remainingSeconds = computed(() => Math.max(0, Math.ceil(remainingMs.value / 1000)));
const passed = computed(() => correctCount.value >= passCorrectCount.value);
const currentTarget = computed(() => queue.value[currentIndex.value] || targets.value[0] || defaultTargets[0]);
const visibleQueue = computed(() => {
  return queue.value
    .slice(currentIndex.value, currentIndex.value + 5)
    .map((target, offset) => ({ target, offset }))
    .reverse();
});

watch(() => props.config, reset, { deep: true, immediate: true });

function choose(side) {
  if (finished.value) return;
  if (side === currentTarget.value.correctSide) correctCount.value += 1;
  else wrongCount.value += 1;

  currentIndex.value += 1;
  if (currentIndex.value >= maxRounds.value) finish();
}

function startTimer() {
  clearInterval(timer);
  const endsAt = Date.now() + remainingMs.value;
  timer = window.setInterval(() => {
    remainingMs.value = Math.max(0, endsAt - Date.now());
    if (remainingMs.value <= 0) finish();
  }, 80);
}

function finish() {
  if (finished.value) return;
  clearInterval(timer);
  finished.value = true;
  const proof = makeProof();
  emit('proof-change', proof);
  emit('solved-change', passed.value);
  emit('auto-submit', proof);
}

function makeProof() {
  return `MG|LEFT_RIGHT_SORT|${JSON.stringify({
    correctCount: correctCount.value,
    totalRounds: currentIndex.value,
    wrongCount: wrongCount.value,
    elapsedMillis: Date.now() - startedAt.value
  })}`;
}

function reset() {
  clearInterval(timer);
  currentIndex.value = 0;
  correctCount.value = 0;
  wrongCount.value = 0;
  finished.value = false;
  remainingMs.value = durationSeconds.value * 1000;
  startedAt.value = Date.now();
  queue.value = buildRandomQueue(maxRounds.value);
  emit('proof-change', '');
  emit('solved-change', false);
  startTimer();
}

function buildRandomQueue(size) {
  const list = targets.value.length ? targets.value : defaultTargets;
  const result = [];
  for (let index = 0; index < size; index += 1) {
    let next = list[Math.floor(Math.random() * list.length)];
    const previous = result[result.length - 1];
    const previousTwo = result[result.length - 2];
    if (previous && previousTwo && previous.key === previousTwo.key && next.key === previous.key) {
      next = list.find(item => item.key !== previous.key) || next;
    }
    result.push({ ...next });
  }
  return result;
}

function normalizeTargets(raw) {
  const list = Array.isArray(raw) && raw.length ? raw : defaultTargets;
  return list.map((target, index) => {
    const fallback = defaultTargets[index % defaultTargets.length];
    const key = String(target.key || fallback.key).toUpperCase();
    return {
      key,
      label: String(target.label || fallback.label),
      emoji: String(target.emoji || emojiForKey(key)),
      correctSide: String(target.correctSide || fallback.correctSide).toUpperCase()
    };
  });
}

function emojiForKey(key) {
  return String(key).toUpperCase() === 'DOG' ? '🐶' : '🐱';
}

function emojiForSide(side) {
  const target = targets.value.find(item => item.correctSide === side);
  return target ? (target.emoji || emojiForKey(target.key)) : '';
}

onBeforeUnmount(() => clearInterval(timer));
</script>

<style scoped>
.sort-game {
  display: grid;
  gap: 14px;
  text-align: center;
}

.hud {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 8px;
  color: #dbeafe;
  font-weight: 900;
}

.hud span:first-child { text-align: left; }
.hud span:last-child { text-align: right; }
.hud strong { color: #fde68a; }

.sort-board {
  position: relative;
  display: grid;
  grid-template-columns: 76px minmax(0, 1fr) 76px;
  align-items: center;
  gap: 12px;
  min-height: 230px;
  padding: 16px 12px;
  overflow: hidden;
  border-radius: 24px;
  background: linear-gradient(180deg, rgba(15, 23, 42, .64), rgba(15, 23, 42, .34));
}

.sort-board::before {
  content: '';
  position: absolute;
  inset: 24px 86px;
  border-radius: 999px;
  background: radial-gradient(circle, rgba(251, 191, 36, .24), transparent 56%);
  pointer-events: none;
}

.destination {
  position: relative;
  z-index: 2;
  min-height: 86px;
  display: grid;
  place-items: center;
  border: 2px dashed rgba(148, 163, 184, .34);
  border-radius: 22px;
  background: rgba(2, 6, 23, .4);
}

.destination span {
  font-size: 2.4rem;
  line-height: 1;
  filter: drop-shadow(0 8px 12px rgba(0, 0, 0, .35));
}

.queue-lane {
  position: relative;
  z-index: 1;
  min-height: 210px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  gap: 0;
  padding: 10px 0;
}

.queue-item {
  width: 58px;
  height: 50px;
  display: grid;
  place-items: center;
  margin-bottom: -8px;
  border: 2px solid rgba(255, 255, 255, .08);
  border-radius: 22px;
  background: rgba(15, 23, 42, .76);
  font-size: 2.15rem;
  line-height: 1;
  opacity: .72;
  transform: scale(.82);
  box-shadow: 0 10px 18px rgba(0, 0, 0, .28);
}

.queue-item.current {
  width: 112px;
  height: 96px;
  margin-top: 4px;
  margin-bottom: 0;
  border-color: rgba(251, 191, 36, .35);
  background: radial-gradient(circle, rgba(251, 191, 36, .18), rgba(15, 23, 42, .9));
  font-size: 5.2rem;
  opacity: 1;
  transform: scale(1);
  box-shadow: 0 18px 28px rgba(0, 0, 0, .4);
}

.side-buttons {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

button {
  min-height: 60px;
  border: 0;
  border-radius: 18px;
  background: #f97316;
  color: #fff;
  font-size: 1.45rem;
  font-weight: 1000;
  padding: 10px;
}

button:disabled { opacity: .5; }

.result {
  border-radius: 16px;
  padding: 14px;
  font-weight: 1000;
}

.result.success { background: rgba(22, 101, 52, .32); color: #bbf7d0; }
.result.fail { background: rgba(127, 29, 29, .32); color: #fecaca; }

.ghost {
  min-height: 48px;
  border: 1px solid rgba(148, 163, 184, .28);
  background: rgba(15, 23, 42, .7);
  color: #cbd5e1;
}

@media (max-width: 430px) {
  .hud {
    grid-template-columns: 1fr;
    text-align: left;
  }

  .hud span:last-child { text-align: left; }

  .sort-board {
    grid-template-columns: 58px minmax(0, 1fr) 58px;
    gap: 8px;
    min-height: 208px;
    padding: 12px 8px;
  }

  .sort-board::before { inset: 24px 60px; }
  .destination { min-height: 76px; border-radius: 18px; }
  .destination span { font-size: 2rem; }
  .queue-lane { min-height: 190px; }
  .queue-item { width: 48px; height: 42px; font-size: 1.8rem; }
  .queue-item.current { width: 94px; height: 84px; font-size: 4.5rem; }
  button { min-height: 56px; }
}
</style>