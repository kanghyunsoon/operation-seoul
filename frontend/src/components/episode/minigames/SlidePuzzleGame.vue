<template>
  <div class="slide-puzzle">
    <p>흩어진 사건 조각을 순서대로 맞추세요.</p>
    <div class="slide-grid"><button v-for="(tile, index) in tiles" :key="`${index}-${tile}`" type="button" @click="move(index)">{{ tile }}</button></div>
  </div>
</template>
<script setup>
import { computed, ref, watch } from 'vue';
const props = defineProps({ config: { type: Object, default: () => ({}) }, submitValue: { type: String, default: '' } });
const emit = defineEmits(['solved-change', 'proof-change']);
const target = computed(() => props.config.tiles || [...(props.submitValue || '단서완성')].slice(0, 4));
const initialTiles = computed(() => props.config.initialTiles || [...target.value].reverse());
const tiles = ref([]);
watch(target, reset, { immediate: true });
watch(tiles, () => {
  emit('proof-change', `MG|SLIDE_PUZZLE|${tiles.value.join('')}`);
  emit('solved-change', tiles.value.join('') === target.value.join(''));
}, { deep: true });
function reset() { tiles.value = [...initialTiles.value]; emit('solved-change', false); }
function move(index) { if (index <= 0) return; const next = [...tiles.value]; [next[index - 1], next[index]] = [next[index], next[index - 1]]; tiles.value = next; }
</script>
<style scoped>
.slide-puzzle { display: grid; gap: 8px; }
.slide-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 8px; }
button { min-height: 58px; border: 0; border-radius: 12px; background: linear-gradient(135deg, #92400e, #0f172a); color: #fff; font-weight: 1000; padding: 8px; }
</style>
