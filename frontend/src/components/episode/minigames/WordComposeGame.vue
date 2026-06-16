<template>
  <div class="word-compose">
    <div class="answer-tray">{{ composed || '조각을 눌러 단어를 만드세요' }}</div>
    <div class="tile-grid">
      <button v-for="(tile, index) in tiles" :key="`${index}-${tile}`" type="button" :disabled="used.includes(index)" @click="pick(tile, index)">{{ tile }}</button>
    </div>
    <button type="button" class="ghost" @click="reset">다시 조합</button>
  </div>
</template>
<script setup>
import { computed, ref, watch } from 'vue';
const props = defineProps({ config: { type: Object, default: () => ({}) }, submitValue: { type: String, default: '' } });
const emit = defineEmits(['solved-change', 'proof-change']);
const tiles = computed(() => props.config.tiles || [...props.submitValue]);
const used = ref([]);
const composed = ref('');
watch(() => props.submitValue, reset, { immediate: true });
watch(composed, () => {
  emit('proof-change', `MG|WORD_COMPOSE|${composed.value}`);
  emit('solved-change', normalize(composed.value) === normalize(props.submitValue));
});
function pick(tile, index) { if (used.value.includes(index)) return; used.value = [...used.value, index]; composed.value += tile; }
function reset() { used.value = []; composed.value = ''; emit('solved-change', false); }
function normalize(value) { return String(value || '').replace(/\s+/g, '').toLowerCase(); }
</script>
<style scoped>
.word-compose { display: grid; gap: 8px; }
.answer-tray { min-height: 44px; display: grid; place-items: center; border: 1px dashed rgba(125,211,252,.42); border-radius: 12px; color: #e0f2fe; font-weight: 900; }
.tile-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; }
button { border: 0; border-radius: 10px; background: #ea580c; color: #fff; font-weight: 900; padding: 8px 12px; }
button:disabled { opacity: .32; }
.ghost { border: 1px solid rgba(148,163,184,.28); background: rgba(15,23,42,.7); color: #cbd5e1; }
</style>
