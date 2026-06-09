<template>
  <div class="memory-grid">
    <button v-for="card in deck" :key="card.id" type="button" :class="{ open: card.open || card.matched }" @click="flip(card)"><span>{{ card.open || card.matched ? card.label : '?' }}</span></button>
  </div>
</template>
<script setup>
import { ref, watch } from 'vue';
const props = defineProps({ config: { type: Object, default: () => ({}) }, submitValue: { type: String, default: '' }, basis: { type: String, default: '' } });
const emit = defineEmits(['solved-change', 'proof-change']);
const deck = ref([]); const opened = ref([]);
watch(() => [props.config.cards, props.submitValue, props.basis], reset, { immediate: true, deep: true });
function reset() { const cards = props.config.cards || [props.submitValue, props.basis, '봉인', '문서'].filter(Boolean); deck.value = [...cards, ...cards].map((label, index) => ({ id: `${index}-${label}`, label, open: false, matched: false })); opened.value = []; emit('solved-change', false); }
function flip(card) { if (card.open || card.matched || opened.value.length >= 2) return; card.open = true; opened.value = [...opened.value, card]; if (opened.value.length === 2) { const [a,b]=opened.value; if (a.label === b.label) { a.matched = b.matched = true; opened.value = []; const solved = deck.value.every(c => c.matched); if (solved) emit('proof-change', 'MG|MEMORY_CARD|MATCHED'); emit('solved-change', solved); } else { window.setTimeout(() => { a.open = false; b.open = false; opened.value = []; }, 520); } } }
</script>
<style scoped>
.memory-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
button { min-height: 54px; border: 0; border-radius: 10px; background: #ea580c; color: #fff; font-weight: 900; padding: 8px; }
button.open { background: #0e7490; }
</style>
