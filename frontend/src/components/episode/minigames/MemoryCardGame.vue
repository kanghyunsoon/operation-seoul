<template>
  <div class="memory-game">
    <div class="memory-status">
      <span>실수 {{ mistakes }}/{{ maxMistakes }}</span>
      <strong>{{ solved ? '모든 짝 확인' : '같은 단서 쌍을 모두 찾으세요' }}</strong>
    </div>
    <div class="memory-grid">
      <button
        v-for="card in deck"
        :key="card.id"
        type="button"
        class="memory-card"
        :class="{ open: card.open && !card.matched, matched: card.matched, failed }"
        :disabled="failed || solved"
        @click="flip(card)"
      >
        <span>{{ card.open || card.matched ? card.label : '?' }}</span>
      </button>
    </div>
    <button v-if="failed" type="button" class="retry" @click="reset">재도전</button>
  </div>
</template>
<script setup>
import { computed, ref, watch } from 'vue';
const props = defineProps({
  config: { type: Object, default: () => ({}) },
  submitValue: { type: String, default: '' },
  basis: { type: String, default: '' },
  retryVariant: { type: Number, default: 0 }
});
const emit = defineEmits(['solved-change', 'proof-change']);
const deck = ref([]);
const opened = ref([]);
const mistakes = ref(0);
const failed = ref(false);
const solved = ref(false);
const maxMistakes = computed(() => Number(props.config.maxMistakes || 5));
watch(() => [props.config.cards, props.submitValue, props.basis, props.retryVariant], reset, { immediate: true, deep: true });
function reset() {
  const cards = props.config.cards || [props.submitValue, props.basis, '봉인', '문서', '동선', '증거'].filter(Boolean);
  deck.value = shuffle(
    [...cards, ...cards].map((label, index) => ({ id: `${index}-${label}`, label, open: false, matched: false })),
    props.retryVariant
  );
  opened.value = [];
  mistakes.value = 0;
  failed.value = false;
  solved.value = false;
  emit('proof-change', '');
  emit('solved-change', false);
}
function flip(card) {
  if (failed.value || solved.value || card.open || card.matched || opened.value.length >= 2) return;
  card.open = true;
  opened.value = [...opened.value, card];
  if (opened.value.length !== 2) return;
  const [a, b] = opened.value;
  if (a.label === b.label) {
    a.matched = b.matched = true;
    opened.value = [];
    solved.value = deck.value.every(c => c.matched);
    if (solved.value) emit('proof-change', 'MG|MEMORY_CARD|MATCHED');
    emit('solved-change', solved.value);
    return;
  }
  mistakes.value += 1;
  if (mistakes.value >= maxMistakes.value) {
    failed.value = true;
    emit('proof-change', '');
    emit('solved-change', false);
  }
  window.setTimeout(() => {
    a.open = false;
    b.open = false;
    opened.value = [];
  }, 520);
}
function shuffle(items, retryVariant) {
  const arranged = [...items].sort((a, b) => String(a.id).localeCompare(String(b.id)));
  if (arranged.length < 2) return arranged;
  const offset = Math.floorMod
    ? Math.floorMod(retryVariant, arranged.length)
    : ((retryVariant % arranged.length) + arranged.length) % arranged.length;
  const rotated = [...arranged.slice(offset), ...arranged.slice(0, offset)];
  return rotated.map((_, index) => rotated[(index * 5) % rotated.length]);
}
</script>
<style scoped>
.memory-game { display: grid; gap: 10px; }
.memory-status { display: flex; justify-content: space-between; gap: 8px; color: #fde68a; font-weight: 900; font-size: .85rem; }
.memory-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; }
.memory-card { min-height: 58px; border: 2px solid #4b6685 !important; border-radius: 12px; background: #21344d !important; color: #f8fafc !important; font-weight: 1000; padding: 8px; box-shadow: inset 0 0 0 1px rgba(255,255,255,.05) !important; }
.memory-card.open { border-color: #38bdf8 !important; background: #075985 !important; color: #fff !important; box-shadow: 0 0 0 3px rgba(56,189,248,.16) !important; }
.memory-card.matched { border-color: #4ade80 !important; background: #166534 !important; color: #f0fdf4 !important; box-shadow: 0 0 0 3px rgba(74,222,128,.18) !important; }
button.failed { opacity: .55; }
.retry { min-height: 46px; border-color: #64748b !important; border-radius: 12px; background: #334155 !important; color: #fff !important; font-weight: 900; }
@media (max-width: 430px) { .memory-grid { grid-template-columns: repeat(3, 1fr); } }
</style>
