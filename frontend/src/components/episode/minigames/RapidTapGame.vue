<template>
  <div class="rapid-tap">
    <p>{{ label }} 신호를 정해진 횟수만큼 정확히 누르세요.</p>
    <div class="tap-board">
      <strong>{{ count }} / {{ target }}</strong>
      <span>남은 시간 {{ remainingSeconds }}초</span>
    </div>
    <button type="button" :disabled="finished" @click="tap">{{ started ? '탭' : '시작' }}</button>
    <p class="rule">기준 속도는 초당 6회입니다. 목표 횟수를 넘기거나 부족하면 제출 시 실패합니다.</p>
  </div>
</template>
<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue';
const props = defineProps({ config: { type: Object, default: () => ({}) }, basis: { type: String, default: '' } });
const emit = defineEmits(['solved-change', 'proof-change']);
const target = computed(() => Number(props.config.target || 23));
const durationSeconds = computed(() => Math.min(10, Math.max(1, Number(props.config.durationSeconds || 5))));
const label = computed(() => props.config.label || props.basis || '단서');
const count = ref(0);
const started = ref(false);
const finished = ref(false);
const remainingMs = ref(durationSeconds.value * 1000);
let timer = null;

const remainingSeconds = computed(() => Math.max(0, Math.ceil(remainingMs.value / 1000)));

watch([target, durationSeconds], reset, { immediate: true });
watch(count, emitState);

function tap() {
  if (finished.value) return;
  if (!started.value) start();
  count.value += 1;
}

function start() {
  started.value = true;
  remainingMs.value = durationSeconds.value * 1000;
  const endsAt = Date.now() + remainingMs.value;
  clearInterval(timer);
  timer = setInterval(() => {
    remainingMs.value = Math.max(0, endsAt - Date.now());
    if (remainingMs.value <= 0) {
      finished.value = true;
      clearInterval(timer);
      emitState();
    }
  }, 100);
}

function reset() {
  clearInterval(timer);
  count.value = 0;
  started.value = false;
  finished.value = false;
  remainingMs.value = durationSeconds.value * 1000;
  emitState();
}

function emitState() {
  emit('proof-change', `MG|RAPID_TAP|${count.value}`);
  emit('solved-change', count.value === target.value && started.value);
}

onBeforeUnmount(() => clearInterval(timer));
</script>
<style scoped>
.rapid-tap { display: grid; gap: 10px; text-align: center; }
.tap-board { display: grid; gap: 4px; padding: 12px; border-radius: 16px; background: rgba(15,23,42,.78); }
strong { font-size: 1.8rem; color: #fcd34d; }
span { color: #bae6fd; font-weight: 900; }
button { min-height: 86px; border: 0; border-radius: 999px; background: #dc2626; color: #fff; font-size: 1.3rem; font-weight: 900; }
button:disabled { opacity: .5; background: #475569; }
.rule { margin: 0; color: #cbd5e1; font-size: .82rem; line-height: 1.45; }
</style>
