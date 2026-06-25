<template>
  <div class="pattern-lock">
    <p>노드가 켜지는 순서를 기억한 뒤 같은 순서로 입력하세요.</p>
    <div class="progress">
      입력 {{ guess.length }}/{{ nodes.length }}
      <span v-if="previewing">순서 표시 중</span>
    </div>
    <div class="pattern-grid">
      <button
        v-for="node in 9"
        :key="node"
        type="button"
        :disabled="previewing"
        class="pattern-node"
        :class="{ selected: guess.includes(node - 1), flash: activeNode === node - 1 }"
        @click="push(node - 1)"
      >
        {{ node }}
      </button>
    </div>
    <button type="button" class="ghost" @click="reset">순서 다시 보기</button>
  </div>
</template>
<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue';
const props = defineProps({ config: { type: Object, default: () => ({}) } });
const emit = defineEmits(['solved-change', 'proof-change']);
const nodes = computed(() => props.config.nodes || [0, 1, 4, 7, 8, 5, 2]);
const guess = ref([]);
const previewing = ref(false);
const activeNode = ref(null);
let previewTimers = [];
let startTimer = null;

watch(nodes, reset, { immediate: true });
watch(guess, () => {
  emit('proof-change', `MG|PATTERN_LOCK|${guess.value.join(',')}`);
  emit('solved-change', guess.value.join(',') === nodes.value.join(','));
}, { deep: true });

function push(node) {
  if (previewing.value || guess.value.length >= nodes.value.length) return;
  guess.value = [...guess.value, node];
}

async function reset() {
  clearPreview();
  guess.value = [];
  activeNode.value = null;
  emit('proof-change', '');
  emit('solved-change', false);
  await nextTick();
  window.clearTimeout(startTimer);
  startTimer = window.setTimeout(showSequence, 180);
}

function showSequence() {
  clearPreview();
  previewing.value = true;
  const stepMs = 560;
  nodes.value.forEach((node, index) => {
    previewTimers.push(window.setTimeout(() => { activeNode.value = node; }, index * stepMs));
    previewTimers.push(window.setTimeout(() => { activeNode.value = null; }, index * stepMs + 360));
  });
  previewTimers.push(window.setTimeout(() => {
    activeNode.value = null;
    previewing.value = false;
  }, nodes.value.length * stepMs + 180));
}

function clearPreview() {
  window.clearTimeout(startTimer);
  startTimer = null;
  previewTimers.forEach(window.clearTimeout);
  previewTimers = [];
  previewing.value = false;
}

onBeforeUnmount(clearPreview);
</script>
<style scoped>
.pattern-lock { display: grid; gap: 10px; }
.progress { color: #bfdbfe; font-weight: 900; }
.progress span { color: #fde68a; margin-left: 8px; }
.pattern-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; padding: 10px; border-radius: 18px; background: rgba(248,250,252,.12); box-shadow: inset 0 0 0 1px rgba(255,255,255,.14); }
button { min-height: 48px; border: 0; border-radius: 12px; background: #334155; color: #fff; font-weight: 900; padding: 8px; transition: transform .12s ease, background .12s ease, box-shadow .12s ease, color .12s ease; }
button.pattern-node { min-height: 64px; border: 2px solid rgba(255,255,255,.58) !important; background: #f8fafc !important; color: #1e293b !important; box-shadow: 0 8px 22px rgba(255,255,255,.16), inset 0 0 0 1px rgba(184,135,59,.18) !important; font-size: 1.08rem; }
button.pattern-node:not(:disabled):hover { transform: translateY(-2px); background: #fff7ed !important; box-shadow: 0 12px 28px rgba(184,135,59,.26), inset 0 0 0 1px rgba(184,135,59,.28) !important; }
button.pattern-node.selected { border-color: #fbbf24 !important; background: #fde68a !important; color: #422006 !important; box-shadow: 0 0 0 4px rgba(251,191,36,.18), 0 12px 28px rgba(251,191,36,.28) !important; }
button.pattern-node.flash { transform: scale(1.1); border-color: #ffffff !important; background: #fff200 !important; color: #111827 !important; box-shadow: 0 0 0 6px rgba(255,242,0,.34), 0 0 30px rgba(255,242,0,.95), 0 16px 36px rgba(255,242,0,.42) !important; animation: node-flash .36s ease-in-out; }
button:disabled { cursor: wait; }
.ghost { border: 1px solid rgba(148,163,184,.28); background: rgba(15,23,42,.7); color: #cbd5e1; }
@keyframes node-flash {
  0% { filter: brightness(1); }
  35% { filter: brightness(1.45); }
  100% { filter: brightness(1); }
}
</style>
