<template>
  <div class="shadow-find">
    <p>사건 조명 아래에서 {{ targetLabel }}의 그림자를 찾으세요.</p>
    <div class="shadow-row">
      <button v-for="(item, index) in shadows" :key="`${index}-${item}`" type="button" :class="{ picked: picked === index }" @click="pick(index)"><span :style="shapeStyle(index)"></span>{{ item }}</button>
    </div>
  </div>
</template>
<script setup>
import { computed, ref, watch } from 'vue';
const props = defineProps({ config: { type: Object, default: () => ({}) }, basis: { type: String, default: '' } });
const emit = defineEmits(['solved-change', 'proof-change']);
const targetIndex = computed(() => Number(props.config.targetIndex || 1));
const targetLabel = computed(() => props.config.label || props.basis || '단서');
const shadows = computed(() => props.config.shadows || ['봉투', targetLabel.value, '렌즈', '문서']);
const picked = ref(null);
watch(picked, () => {
  emit('proof-change', `MG|SHADOW_FIND|${picked.value}`);
  emit('solved-change', picked.value === targetIndex.value);
});
watch(targetIndex, () => { picked.value = null; emit('solved-change', false); }, { immediate: true });
function pick(index) { picked.value = index; }
function shapeStyle(index) { return { transform: `skewX(${[-18, 0, 18, -8][index % 4]}deg) rotate(${index * 12}deg)` }; }
</script>
<style scoped>
.shadow-find { display: grid; gap: 8px; }
.shadow-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; }
button { display: grid; gap: 6px; place-items: center; min-height: 82px; border: 1px solid rgba(255,255,255,.15); border-radius: 12px; background: #111827; color: #fff; font-weight: 900; }
button.picked { outline: 2px solid #fcd34d; }
span { width: 38px; height: 26px; border-radius: 50%; background: rgba(0,0,0,.75); box-shadow: 18px 10px 18px rgba(0,0,0,.45); }
</style>
