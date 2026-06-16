<template>
  <div class="direction-sequence">
    <p>시작 후 2초 동안 표시되는 방향을 기억해 순서대로 누르세요.</p>
    <button v-if="!started" type="button" class="start" @click="startPreview">시작</button>
    <div v-else class="sequence" :class="{ hidden: !previewing }">{{ previewing ? labels.join(' ') : '기억한 순서대로 입력' }}</div>
    <div class="progress">입력 {{ guess.length }}/{{ target.length }}</div>
    <div class="pad">
      <button type="button" :disabled="!started || previewing" @click="push('UP')">↑</button>
      <button type="button" :disabled="!started || previewing" @click="push('LEFT')">←</button>
      <button type="button" :disabled="!started || previewing" @click="push('RIGHT')">→</button>
      <button type="button" :disabled="!started || previewing" @click="push('DOWN')">↓</button>
    </div>
    <button type="button" class="ghost" @click="reset">초기화</button>
  </div>
</template>
<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue';

const props = defineProps({ config: { type: Object, default: () => ({}) } });
const emit = defineEmits(['solved-change', 'proof-change']);

const target = computed(() => props.config.sequence || ['UP','RIGHT','DOWN','LEFT']);
const guess = ref([]);
const started = ref(false);
const previewing = ref(false);
let timer = null;
const labels = computed(() => target.value.map(v => ({ UP:'↑', RIGHT:'→', DOWN:'↓', LEFT:'←' }[v] || v)));

watch(guess, () => {
  emit('proof-change', `MG|DIRECTION_SEQUENCE|${guess.value.join(',')}`);
  emit('solved-change', guess.value.join(',') === target.value.join(','));
}, { deep: true });
watch(target, reset, { immediate: true });

function startPreview() {
  started.value = true;
  previewing.value = true;
  clearTimeout(timer);
  timer = window.setTimeout(() => { previewing.value = false; }, 2000);
}

function push(value) {
  if (!started.value || previewing.value || guess.value.length >= target.value.length) return;
  guess.value = [...guess.value, value];
}

function reset() {
  clearTimeout(timer);
  guess.value = [];
  started.value = false;
  previewing.value = false;
  emit('proof-change', '');
  emit('solved-change', false);
}

onBeforeUnmount(() => clearTimeout(timer));
</script>
<style scoped>
.direction-sequence { display: grid; gap: 8px; text-align: center; }
.sequence { min-height: 46px; display: grid; place-items: center; border-radius: 12px; background: rgba(15,23,42,.82); color: #fcd34d; font-size: 1.35rem; font-weight: 1000; letter-spacing: .18em; }
.sequence.hidden { color: #bfdbfe; letter-spacing: 0; font-size: 1rem; }
.progress { color: #bfdbfe; font-weight: 900; }
.pad { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
.pad button:first-child { grid-column: 2; }
button { min-height: 44px; border: 0; border-radius: 10px; background: #ea580c; color: #fff; font-weight: 900; padding: 8px; }
button:disabled { opacity: .45; }
.start { background: #0891b2; }
.ghost { border: 1px solid rgba(148,163,184,.28); background: rgba(15,23,42,.7); color: #cbd5e1; }
</style>
