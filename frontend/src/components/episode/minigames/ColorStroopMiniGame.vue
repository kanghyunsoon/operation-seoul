<template>
  <div class="stroop-game">
    <div class="hud">
      <span>라운드 {{ currentRound + 1 }}/{{ totalRounds }}</span>
      <strong>남은 시간 {{ remainingSeconds }}초</strong>
      <span>정답 {{ correctCount }}/{{ passCorrectCount }}</span>
    </div>

    <div v-if="finished" class="result" :class="{ success: passed, fail: !passed }">
      {{ passed ? '성공입니다. 결과를 제출했습니다.' : '실패했습니다. 초기화 후 다시 시도하세요.' }}
    </div>

    <div v-else-if="preparing" class="prompt-card prepare-card">
      <p>실제 글자 색깔만 고르세요.</p>
      <strong class="prepare-count">{{ prepareSeconds }}</strong>
      <small>곧 시작합니다</small>
    </div>

    <div v-else class="prompt-card">
      <p>글자의 의미가 아니라 실제 글자 색깔을 고르세요.</p>
      <strong class="stroop-word" :style="{ color: currentItem.textColorHex }">{{ currentItem.text }}</strong>
      <small>이번 라운드 {{ roundRemainingSeconds }}초</small>
    </div>

    <div class="color-buttons">
      <button
        v-for="color in colors"
        :key="color.key"
        type="button"
        class="color-choice"
        :disabled="finished || preparing"
        :style="{ '--choice-color': color.hex, '--choice-text': readableTextColor(color.hex) }"
        @click="choose(color.key)"
      >
        <span class="swatch" :style="{ backgroundColor: color.hex }"></span>
        {{ color.label }}
      </button>
    </div>

    <button v-if="finished" type="button" class="ghost" @click="reset">초기화</button>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue';

const props = defineProps({ config: { type: Object, default: () => ({}) } });
const emit = defineEmits(['solved-change', 'proof-change', 'auto-submit']);

const defaultColors = [
  { key: 'RED', label: '빨강', hex: '#ef4444' },
  { key: 'BLUE', label: '파랑', hex: '#3b82f6' },
  { key: 'GREEN', label: '초록', hex: '#22c55e' },
  { key: 'YELLOW', label: '노랑', hex: '#eab308' }
];

const colors = computed(() => normalizeColors(props.config.colors));
const totalRounds = computed(() => Number(props.config.rounds || props.config.items?.length || 10));
const passCorrectCount = computed(() => Number(props.config.passCorrectCount || 8));
const perRoundMs = computed(() => Number(props.config.perRoundSeconds || 2) * 1000);
const items = computed(() => normalizeItems(props.config.items, colors.value, totalRounds.value));
const currentRound = ref(0);
const correctCount = ref(0);
const wrongCount = ref(0);
const finished = ref(false);
const preparing = ref(true);
const prepareMs = ref(3000);
const roundRemainingMs = ref(perRoundMs.value);
const startedAt = ref(Date.now());
let timer = null;

const currentItem = computed(() => items.value[Math.min(currentRound.value, items.value.length - 1)] || items.value[0]);
const passed = computed(() => correctCount.value >= passCorrectCount.value);
const remainingSeconds = computed(() => Math.max(0, Math.ceil(((totalRounds.value - currentRound.value - 1) * perRoundMs.value + roundRemainingMs.value) / 1000)));
const roundRemainingSeconds = computed(() => Math.max(0, Math.ceil(roundRemainingMs.value / 1000)));
const prepareSeconds = computed(() => Math.max(1, Math.ceil(prepareMs.value / 1000)));

watch(() => props.config, reset, { deep: true, immediate: true });

function choose(colorKey) {
  if (finished.value || preparing.value) return;
  if (colorKey === currentItem.value.textColorKey) correctCount.value += 1;
  else wrongCount.value += 1;
  nextRound();
}

function nextRound() {
  if (currentRound.value + 1 >= totalRounds.value) {
    finish();
    return;
  }
  currentRound.value += 1;
  startRoundTimer();
}

function startRoundTimer() {
  clearInterval(timer);
  roundRemainingMs.value = perRoundMs.value;
  const endsAt = Date.now() + perRoundMs.value;
  timer = window.setInterval(() => {
    roundRemainingMs.value = Math.max(0, endsAt - Date.now());
    if (roundRemainingMs.value <= 0) {
      wrongCount.value += 1;
      nextRound();
    }
  }, 250);
}

function finish() {
  clearInterval(timer);
  finished.value = true;
  const proof = makeProof();
  emit('proof-change', proof);
  emit('solved-change', passed.value);
  emit('auto-submit', proof);
}

function makeProof() {
  return `MG|COLOR_STROOP|${JSON.stringify({
    correctCount: correctCount.value,
    totalRounds: totalRounds.value,
    wrongCount: wrongCount.value,
    elapsedMillis: Date.now() - startedAt.value
  })}`;
}

function reset() {
  clearInterval(timer);
  currentRound.value = 0;
  correctCount.value = 0;
  wrongCount.value = 0;
  finished.value = false;
  preparing.value = true;
  prepareMs.value = 3000;
  emit('proof-change', '');
  emit('solved-change', false);
  startPrepareTimer();
}

function startPrepareTimer() {
  clearInterval(timer);
  const endsAt = Date.now() + 3000;
  timer = window.setInterval(() => {
    prepareMs.value = Math.max(0, endsAt - Date.now());
    if (prepareMs.value <= 0) {
      clearInterval(timer);
      preparing.value = false;
      startedAt.value = Date.now();
      startRoundTimer();
    }
  }, 250);
}

function normalizeColors(raw) {
  const list = Array.isArray(raw) && raw.length ? raw : defaultColors;
  return list.map((color, index) => ({
    key: String(color.key || defaultColors[index % defaultColors.length].key),
    label: String(color.label || defaultColors[index % defaultColors.length].label),
    hex: String(color.hex || defaultColors[index % defaultColors.length].hex)
  })).slice(0, 4);
}

function normalizeItems(raw, colorList, rounds) {
  if (Array.isArray(raw) && raw.length) return raw.slice(0, rounds).map((item, index) => normalizeItem(item, index, colorList));
  return Array.from({ length: rounds }, (_, index) => {
    const textColor = colorList[(index + 1) % colorList.length];
    const text = colorList[index % colorList.length];
    return { text: text.label, textColorKey: textColor.key, textColorHex: textColor.hex };
  });
}

function normalizeItem(item, index, colorList) {
  const fallbackText = colorList[index % colorList.length];
  const fallbackColor = colorList[(index + 1) % colorList.length];
  const matchedColor = colorList.find(color => color.key === item.textColorKey) || fallbackColor;
  return {
    text: String(item.text || fallbackText.label),
    textColorKey: String(item.textColorKey || matchedColor.key),
    textColorHex: String(item.textColorHex || matchedColor.hex)
  };
}

function readableTextColor(hex) {
  const value = String(hex || '').replace('#', '');
  if (!/^[0-9a-f]{6}$/i.test(value)) return '#ffffff';
  const red = Number.parseInt(value.slice(0, 2), 16);
  const green = Number.parseInt(value.slice(2, 4), 16);
  const blue = Number.parseInt(value.slice(4, 6), 16);
  return ((red * 299 + green * 587 + blue * 114) / 1000) > 155 ? '#172033' : '#ffffff';
}

onBeforeUnmount(() => clearInterval(timer));
</script>

<style scoped>
.stroop-game { display: grid; gap: 12px; text-align: center; }
.hud { display: flex; justify-content: space-between; gap: 8px; color: #cbd5e1; font-weight: 900; }
.hud strong { color: #fde68a; }
.prompt-card { display: grid; gap: 8px; padding: 18px; border-radius: 18px; background: rgba(15,23,42,.82); }
.prompt-card p { margin: 0; color: #bfdbfe; font-weight: 900; }
.stroop-word { font-size: clamp(2.4rem, 12vw, 5rem); font-weight: 1000; letter-spacing: .08em; }
.prompt-card small { color: #fcd34d; font-weight: 900; }
.prepare-card { min-height: 170px; place-items: center; }
.prepare-count { color: #fcd34d; font-size: clamp(3rem, 16vw, 6rem); line-height: 1; font-weight: 1000; }
.color-buttons { display: grid; grid-template-columns: repeat(2, 1fr); gap: 8px; }
.color-choice { min-height: 52px; border: 2px solid color-mix(in srgb, var(--choice-color) 76%, #fff) !important; border-radius: 14px; background: var(--choice-color) !important; color: var(--choice-text) !important; font-weight: 1000; padding: 8px; box-shadow: inset 0 -3px 0 rgba(0,0,0,.16), 0 8px 18px color-mix(in srgb, var(--choice-color) 24%, transparent) !important; }
.color-choice:not(:disabled):hover { filter: brightness(1.08); }
.color-choice:focus-visible { outline: 3px solid #fff; outline-offset: 2px; }
.swatch { display: inline-block; width: 14px; height: 14px; margin-right: 7px; border: 2px solid currentColor; border-radius: 999px; background: transparent !important; vertical-align: -2px; }
.result { border-radius: 14px; padding: 10px; font-weight: 1000; }
.result.success { background: rgba(22,101,52,.32); color: #bbf7d0; }
.result.fail { background: rgba(127,29,29,.32); color: #fecaca; }
.ghost { min-height: 46px; border-color: rgba(148,163,184,.42) !important; border-radius: 12px; background: #26364c !important; color: #f8fafc !important; font-weight: 900; }
@media (max-width: 430px) { .hud { display: grid; } }
</style>
