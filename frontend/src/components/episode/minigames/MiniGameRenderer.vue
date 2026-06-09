<template>
  <div class="mini-game">
    <p v-if="interaction.storyHook" class="story-hook">{{ interaction.storyHook }}</p>
    <component
      v-if="activeComponent"
      :is="activeComponent"
      :config="config"
      :submit-value="submitValue"
      :basis="basis"
      @solved-change="handleSolved"
      @proof-change="handleProof"
    />
    <p v-if="!activeComponent" class="fallback">{{ basis || '현장 단서' }}를 확인한 뒤 정답을 제출하세요.</p>
    <div class="device-status" :class="{ solved }">
      <span>{{ solved ? '해제 완료' : '장치 대기 중' }}</span>
      <strong>{{ solved ? '서버 제출 가능' : statusText }}</strong>
    </div>
  </div>
</template>
<script setup>
import { computed, ref, watch } from 'vue';
import NumberLockGame from './NumberLockGame.vue';
import WordComposeGame from './WordComposeGame.vue';
import ColorCodeGame from './ColorCodeGame.vue';
import MemoryCardGame from './MemoryCardGame.vue';
import PatternLockGame from './PatternLockGame.vue';
import SwitchToggleGame from './SwitchToggleGame.vue';
import RapidTapGame from './RapidTapGame.vue';
import DirectionSequenceGame from './DirectionSequenceGame.vue';
import ShadowFindGame from './ShadowFindGame.vue';
import SlidePuzzleGame from './SlidePuzzleGame.vue';

const props = defineProps({ interaction: { type: Object, default: () => ({}) } });
const emit = defineEmits(['solved-change', 'proof-change']);
const registry = {
  NUMBER_LOCK: NumberLockGame,
  WORD_COMPOSE: WordComposeGame,
  COLOR_CODE: ColorCodeGame,
  MEMORY_CARD: MemoryCardGame,
  PATTERN_LOCK: PatternLockGame,
  SWITCH_TOGGLE: SwitchToggleGame,
  RAPID_TAP: RapidTapGame,
  DIRECTION_SEQUENCE: DirectionSequenceGame,
  SHADOW_FIND: ShadowFindGame,
  SLIDE_PUZZLE: SlidePuzzleGame
};
const type = computed(() => String(props.interaction?.type || '').toUpperCase());
const activeComponent = computed(() => registry[type.value] || null);
const config = computed(() => props.interaction?.config || {});
const submitValue = computed(() => String(props.interaction?.localSolution || props.interaction?.basis || '').trim());
const basis = computed(() => props.interaction?.basis || '');
const solved = ref(false);
const latestProof = ref('');
const statusText = computed(() => latestProof.value ? '입력값 확인 중' : '단서를 조작해 장치를 해제하세요');

watch(type, () => {
  solved.value = false;
  latestProof.value = '';
});

function handleSolved(value) {
  solved.value = Boolean(value);
  emit('solved-change', solved.value);
}

function handleProof(value) {
  latestProof.value = String(value || '');
  emit('proof-change', latestProof.value);
}
</script>
<style scoped>
.mini-game { display: grid; gap: 12px; margin-top: 12px; padding: 12px; border: 1px solid rgba(148,163,184,.18); border-radius: 18px; background: linear-gradient(135deg, rgba(15,23,42,.92), rgba(8,47,73,.5)); }
.story-hook { margin: 0; color: #fcd34d; font-weight: 900; }
.fallback { margin: 0; color: #cbd5e1; }
.device-status { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 9px 10px; border-radius: 12px; background: rgba(15,23,42,.8); color: #cbd5e1; font-size: .78rem; font-weight: 900; }
.device-status span { color: #fbbf24; }
.device-status.solved { background: rgba(22,101,52,.26); color: #bbf7d0; }
.device-status.solved span { color: #86efac; }
</style>
