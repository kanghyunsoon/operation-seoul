<template>
  <div class="number-lock" aria-label="숫자 맞추기">
    <div v-for="(_, index) in digitCount" :key="`digit-${index}`" class="digit-wheel">
      <button type="button" @click="bump(index, 1)">▲</button>
      <strong>{{ guess[index] || '0' }}</strong>
      <button type="button" @click="bump(index, -1)">▼</button>
    </div>
  </div>
</template>
<script setup>
import { computed, ref, watch } from 'vue';
const props = defineProps({ config: { type: Object, default: () => ({}) }, submitValue: { type: String, default: '' } });
const emit = defineEmits(['solved-change', 'proof-change']);
const digitCount = computed(() => Number(props.config.digits || props.submitValue.replace(/\D/g, '').length || 4));
const target = computed(() => props.config.solutionDigits || props.submitValue.replace(/\D/g, '').slice(0, digitCount.value));
const guess = ref([]);
watch([digitCount, () => props.config.initial], reset, { immediate: true });
watch(guess, () => {
  const value = guess.value.join('');
  emit('proof-change', `MG|NUMBER_LOCK|${value}`);
  emit('solved-change', value === target.value);
}, { deep: true });
function reset() { guess.value = String(props.config.initial || '0'.repeat(digitCount.value)).slice(0, digitCount.value).padEnd(digitCount.value, '0').split(''); }
function bump(index, delta) { const next = [...guess.value]; next[index] = String((Number(next[index] || 0) + delta + 10) % 10); guess.value = next; }
</script>
<style scoped>
.number-lock { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 8px; }
.digit-wheel { display: grid; gap: 6px; justify-items: center; }
.digit-wheel strong { width: 100%; display: grid; place-items: center; min-height: 58px; border: 1px solid rgba(255,255,255,.16); border-radius: 14px; background: #020617; color: #fff; font-size: 2rem; }
button { border: 0; border-radius: 10px; background: #ea580c; color: #fff; font-weight: 900; padding: 8px 12px; }
@media (max-width:370px){ .number-lock { grid-template-columns: 1fr; } }
</style>
