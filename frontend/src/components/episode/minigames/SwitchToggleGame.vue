<template>
  <div class="switch-bank">
    <p>진짜 단서만 켜고 위장 단서는 끄세요.</p>
    <button v-for="(label, index) in labels" :key="`${index}-${label}`" type="button" :class="{ on: states[index] }" @click="toggle(index)"><span></span>{{ label }}</button>
  </div>
</template>
<script setup>
import { computed, ref, watch } from 'vue';
const props = defineProps({ config: { type: Object, default: () => ({}) } });
const emit = defineEmits(['solved-change', 'proof-change']);
const labels = computed(() => props.config.switches || ['단서', '증거', '위장 단서', '동선 단서']);
const targetStates = computed(() => props.config.targetStates || labels.value.map((_, index) => index < 2));
const states = ref([]);
watch(labels, reset, { immediate: true });
watch(states, () => {
  emit('proof-change', `MG|SWITCH_TOGGLE|${states.value.map(Boolean).map(value => value ? '1' : '0').join(',')}`);
  emit('solved-change', states.value.join('|') === targetStates.value.join('|'));
}, { deep: true });
function reset() { states.value = labels.value.map(() => false); emit('solved-change', false); }
function toggle(index) { const next = [...states.value]; next[index] = !next[index]; states.value = next; }
</script>
<style scoped>
.switch-bank { display: grid; gap: 8px; }
button { display: flex; align-items: center; gap: 8px; justify-content: flex-start; min-height: 42px; border: 0; border-radius: 10px; background: rgba(15,23,42,.92); color: #fff; font-weight: 900; padding: 8px 12px; }
button span { width: 28px; height: 16px; border-radius: 999px; background: #475569; }
button.on span { background: #22c55e; }
</style>
