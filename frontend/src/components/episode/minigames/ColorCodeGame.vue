<template>
  <div class="color-code">
    <p>사건 카드 순서에 맞는 색을 {{ targetLength }}개 선택하세요.</p>
    <div class="color-slots"><span v-for="(_, index) in targetLength" :key="index" :style="{ background: guess[index] || 'transparent' }"></span></div>
    <div class="color-palette"><button v-for="color in palette" :key="color" type="button" :style="{ background: color }" @click="push(color)"></button></div>
    <button type="button" class="ghost" @click="reset">초기화</button>
  </div>
</template>
<script setup>
import { computed, ref, watch } from 'vue';
const props = defineProps({ config: { type: Object, default: () => ({}) } });
const emit = defineEmits(['solved-change', 'proof-change']);
const palette = computed(() => props.config.palette || ['#ef4444', '#f59e0b', '#10b981', '#3b82f6']);
const solution = computed(() => props.config.solution || palette.value.slice(0, Number(props.config.codeLength || 4)));
const targetLength = computed(() => solution.value.length);
const guess = ref([]);
watch(guess, () => {
  emit('proof-change', `MG|COLOR_CODE|${guess.value.join(',')}`);
  emit('solved-change', guess.value.join('|') === solution.value.join('|'));
}, { deep: true });
watch(targetLength, reset, { immediate: true });
function push(color) { if (guess.value.length < targetLength.value) guess.value = [...guess.value, color]; }
function reset() { guess.value = []; emit('solved-change', false); }
</script>
<style scoped>
.color-code { display: grid; gap: 8px; }
.color-slots, .color-palette { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; }
.color-palette { grid-template-columns: repeat(5, 1fr); }
.color-slots span { min-height: 34px; border: 1px solid rgba(255,255,255,.2); border-radius: 10px; }
button { min-height: 40px; border: 2px solid rgba(255,255,255,.55); border-radius: 10px; color: #fff; font-weight: 900; }
.ghost { border: 1px solid rgba(148,163,184,.28); background: rgba(15,23,42,.7); color: #cbd5e1; }
</style>
