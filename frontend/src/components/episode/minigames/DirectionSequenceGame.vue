<template>
  <div class="direction-sequence">
    <p>메모의 화살표 순서대로 방향키를 누르세요.</p>
    <div class="sequence">{{ labels.join(' ') }}</div>
    <div class="pad">
      <button type="button" @click="push('UP')">↑</button>
      <button type="button" @click="push('LEFT')">←</button>
      <button type="button" @click="push('RIGHT')">→</button>
      <button type="button" @click="push('DOWN')">↓</button>
    </div>
    <button type="button" class="ghost" @click="reset">초기화</button>
  </div>
</template>
<script setup>
import { computed, ref, watch } from 'vue';
const props = defineProps({ config: { type: Object, default: () => ({}) } });
const emit = defineEmits(['solved-change', 'proof-change']);
const target = computed(() => props.config.sequence || ['UP','RIGHT','DOWN','LEFT']);
const guess = ref([]);
const labels = computed(() => target.value.map(v => ({ UP:'↑', RIGHT:'→', DOWN:'↓', LEFT:'←' }[v] || v)));
watch(guess, () => {
  emit('proof-change', `MG|DIRECTION_SEQUENCE|${guess.value.join(',')}`);
  emit('solved-change', guess.value.join(',') === target.value.join(','));
}, { deep: true });
watch(target, reset, { immediate: true });
function push(value) { if (guess.value.length < target.value.length) guess.value = [...guess.value, value]; }
function reset() { guess.value = []; emit('solved-change', false); }
</script>
<style scoped>
.direction-sequence { display: grid; gap: 8px; text-align: center; }
.sequence { color: #fcd34d; font-size: 1.35rem; font-weight: 1000; letter-spacing: .18em; }
.pad { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
.pad button:first-child { grid-column: 2; }
button { min-height: 44px; border: 0; border-radius: 10px; background: #ea580c; color: #fff; font-weight: 900; padding: 8px; }
.ghost { border: 1px solid rgba(148,163,184,.28); background: rgba(15,23,42,.7); color: #cbd5e1; }
</style>
