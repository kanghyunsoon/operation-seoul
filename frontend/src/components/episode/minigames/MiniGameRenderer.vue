<template>
  <div class="mini-game">
    <p class="story-hook">{{ missionDescription }}</p>
    <component
      v-if="activeComponent"
      :is="activeComponent"
      :config="config"
      :submit-value="submitValue"
      :basis="basis"
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
const missionDescription = computed(() => props.interaction?.missionDescription || props.interaction?.prompt || '아래 미션을 해결하여 단서를 얻으세요.');
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
  latestProof.value = String(value || '');
  emit('proof-change', latestProof.value);
}

function handleAutoSubmit(value) {
  const proof = String(value || latestProof.value || '');
  if (!proof) return;
  latestProof.value = proof;
  emit('proof-change', proof);
  emit('auto-submit', proof);
}
</script>
<style scoped>
.mini-game { display: grid; gap: 12px; margin-top: 12px; padding: 12px; border: 1px solid rgba(148,163,184,.18); border-radius: 18px; background: linear-gradient(135deg, rgba(15,23,42,.92), rgba(8,47,73,.5)); }
.story-hook { margin: 0; color: #fcd34d; font-weight: 900; }
.fallback { margin: 0; color: #cbd5e1; }
.device-status { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 9px 10px; border-radius: 12px; background: rgba(15,23,42,.8); color: #cbd5e1; font-size: .78rem; font-weight: 900; }
.device-status span { color: #fbbf24; }
</style>
