<template>
  <div class="mini-game">
    <component
      v-if="activeComponent"
      :is="activeComponent"
      :config="config"
      :submit-value="submitValue"
      :basis="basis"
      :retry-variant="retryVariant"
      @solved-change="handleSolved"
      @proof-change="handleProof"
      @auto-submit="handleAutoSubmit"
    />
    <p v-if="!activeComponent" class="fallback">{{ basis || '현장 단서' }}를 확인하고 제출하세요.</p>
    <div class="device-status">
      <span>{{ autoSubmit ? '자동 판정' : '제출 대기' }}</span>
      <strong>{{ statusText }}</strong>
    </div>
  </div>
</template>
<script setup>
import { computed, ref, watch } from 'vue';
import NumberLockGame from './NumberLockGame.vue';
import WordComposeGame from './WordComposeGame.vue';
import MemoryCardGame from './MemoryCardGame.vue';
import PatternLockGame from './PatternLockGame.vue';
import RapidTapGame from './RapidTapGame.vue';
import DirectionSequenceGame from './DirectionSequenceGame.vue';
import TimedUpDownGame from './TimedUpDownGame.vue';
import NumberBaseballGame from './NumberBaseballGame.vue';
import NumberSequenceTapGame from './NumberSequenceTapGame.vue';
import ColorStroopMiniGame from './ColorStroopMiniGame.vue';
import LeftRightSortMiniGame from './LeftRightSortMiniGame.vue';

const props = defineProps({ interaction: { type: Object, default: () => ({}) } });
const emit = defineEmits(['solved-change', 'proof-change', 'auto-submit']);
const registry = {
  NUMBER_LOCK: NumberLockGame,
  WORD_COMPOSE: WordComposeGame,
  MEMORY_CARD: MemoryCardGame,
  PATTERN_LOCK: PatternLockGame,
  RAPID_TAP: RapidTapGame,
  DIRECTION_SEQUENCE: DirectionSequenceGame,
  UP_DOWN_TIMER: TimedUpDownGame,
  NUMBER_BASEBALL: NumberBaseballGame,
  NUMBER_SEQUENCE_TAP: NumberSequenceTapGame,
  COLOR_STROOP: ColorStroopMiniGame,
  LEFT_RIGHT_SORT: LeftRightSortMiniGame
};
const type = computed(() => String(props.interaction?.type || '').toUpperCase());
const activeComponent = computed(() => registry[type.value] || null);
const config = computed(() => props.interaction?.config || {});
const submitValue = computed(() => String(props.interaction?.localSolution || props.interaction?.basis || '').trim());
const basis = computed(() => props.interaction?.basis || '');
const retryVariant = computed(() => Number(props.interaction?.retryVariant || 0));
const autoSubmit = computed(() => ['UP_DOWN_TIMER', 'NUMBER_BASEBALL', 'NUMBER_SEQUENCE_TAP', 'COLOR_STROOP', 'LEFT_RIGHT_SORT'].includes(type.value));
const statusText = computed(() => {
  if (autoSubmit.value) return latestProof.value ? '성공 결과를 서버에 자동 제출했습니다.' : '성공 또는 실패 시 화면에서 바로 안내됩니다.';
  return latestProof.value ? '입력값이 기록되었습니다. 제출하면 서버가 판정합니다.' : '장치를 조작한 뒤 결과를 제출하세요.';
});
const latestProof = ref('');

watch(type, () => {
  latestProof.value = '';
});

function handleSolved(value) {
  emit('solved-change', Boolean(value));
}

function handleProof(value) {
  latestProof.value = withRetryVariant(String(value || ''));
  emit('proof-change', latestProof.value);
}

function handleAutoSubmit(value) {
  const proof = withRetryVariant(String(value || latestProof.value || ''));
  if (!proof) return;
  latestProof.value = proof;
  emit('proof-change', proof);
  emit('auto-submit', proof);
}

function withRetryVariant(proof) {
  if (!proof || retryVariant.value <= 0) return proof;
  const parts = proof.split('|');
  if (parts.length < 3 || parts[0] !== 'MG') return proof;
  if (parts[2]?.startsWith('R')) return proof;
  return `MG|${parts[1]}|R${retryVariant.value}|${parts.slice(2).join('|')}`;
}
</script>
<style scoped>
.mini-game { display: grid; gap: 12px; margin-top: 12px; padding: 14px; border: 1px solid rgba(125,211,252,.24); border-radius: 18px; background: linear-gradient(135deg, rgba(15,23,42,.96), rgba(8,47,73,.64)); color: #f8fafc; }
.mini-game :deep(p) { color: #e2e8f0; font-weight: 700; line-height: 1.55; }
.fallback { margin: 0; color: #cbd5e1; }
.device-status { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 10px 12px; border: 1px solid rgba(148,163,184,.2); border-radius: 12px; background: rgba(2,6,23,.58); color: #e2e8f0; font-size: .8rem; font-weight: 900; }
.device-status span { color: #fcd34d; }
.device-status strong { color: #f8fafc; }
.mini-game :deep(button:not(.pattern-node):not(.memory-card):not(.color-choice):not(.tap-button):not(.ghost):not(.start)) {
  border: 2px solid #477399 !important;
  background: #24476a !important;
  color: #f8fafc !important;
  box-shadow: 0 8px 18px rgba(2,6,23,.24), inset 0 0 0 1px rgba(255,255,255,.08) !important;
  text-shadow: none;
}
.mini-game :deep(button:not(.pattern-node):not(:disabled):hover) {
  filter: brightness(1.1);
}
.mini-game :deep(button:not(.pattern-node).selected),
.mini-game :deep(button:not(.pattern-node).active),
.mini-game :deep(button:not(.pattern-node).correct) {
  border-color: #7dd3fc !important;
  background: #0369a1 !important;
  color: #fff !important;
}
.mini-game :deep(button:not(.pattern-node).flash) {
  border-color: #fde68a !important;
  background: #ca8a04 !important;
  color: #fff !important;
  box-shadow: 0 0 0 4px rgba(253,230,138,.22), 0 14px 30px rgba(202,138,4,.3) !important;
}
.mini-game :deep(button.ghost) {
  border: 1px solid #64748b !important;
  background: #26364c !important;
  color: #f8fafc !important;
}
.mini-game :deep(button.start) {
  border: 1px solid #7dd3fc !important;
  background: #0284c7 !important;
  color: #fff !important;
}
.mini-game :deep(button:disabled) {
  opacity: .55;
  cursor: not-allowed;
  filter: grayscale(.15);
}
</style>
