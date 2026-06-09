<template>
  <div class="rapid-tap">
    <p>{{ label }} 흔적을 제한 시간 안에 모두 눌러 봉인을 해제하세요.</p>
    <strong>{{ count }} / {{ target }}</strong>
    <button type="button" @click="count++">탭</button>
  </div>
</template>
<script setup>
import { computed, ref, watch } from 'vue';
const props = defineProps({ config: { type: Object, default: () => ({}) }, basis: { type: String, default: '' } });
const emit = defineEmits(['solved-change', 'proof-change']);
const target = computed(() => Number(props.config.target || 7));
const label = computed(() => props.config.label || props.basis || '단서');
const count = ref(0);
watch(count, () => {
  emit('proof-change', `MG|RAPID_TAP|${count.value}`);
  emit('solved-change', count.value >= target.value);
});
watch(target, () => { count.value = 0; emit('solved-change', false); }, { immediate: true });
</script>
<style scoped>
.rapid-tap { display: grid; gap: 8px; text-align: center; }
strong { font-size: 1.6rem; color: #fcd34d; }
button { min-height: 86px; border: 0; border-radius: 999px; background: #dc2626; color: #fff; font-size: 1.3rem; font-weight: 900; }
</style>
