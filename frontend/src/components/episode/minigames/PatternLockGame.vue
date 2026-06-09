<template>
  <div class="pattern-lock">
    <p>빛나는 순서를 기억해 노드를 누르세요.</p>
    <div class="pattern-grid"><button v-for="node in 9" :key="node" type="button" :class="{ selected: guess.includes(node - 1) }" @click="push(node - 1)">{{ node }}</button></div>
    <button type="button" class="ghost" @click="reset">초기화</button>
  </div>
</template>
<script setup>
import { computed, ref, watch } from 'vue';
const props = defineProps({ config: { type: Object, default: () => ({}) } });
const emit = defineEmits(['solved-change', 'proof-change']);
const nodes = computed(() => props.config.nodes || [0,1,4,7]);
const guess = ref([]);
watch(guess, () => {
  emit('proof-change', `MG|PATTERN_LOCK|${guess.value.join(',')}`);
  emit('solved-change', guess.value.join(',') === nodes.value.join(','));
}, { deep: true });
watch(nodes, reset, { immediate: true });
function push(node) { if (guess.value.length < nodes.value.length) guess.value = [...guess.value, node]; }
function reset() { guess.value = []; emit('solved-change', false); }
</script>
<style scoped>
.pattern-lock { display: grid; gap: 8px; }
.pattern-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
button { min-height: 44px; border: 0; border-radius: 10px; background: #ea580c; color: #fff; font-weight: 900; padding: 8px; }
button.selected { background: #0891b2; }
.ghost { border: 1px solid rgba(148,163,184,.28); background: rgba(15,23,42,.7); color: #cbd5e1; }
</style>
