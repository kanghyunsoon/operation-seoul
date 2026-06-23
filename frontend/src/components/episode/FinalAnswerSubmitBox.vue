<template>
  <section class="answer-box">
    <h3>최종 정답 제출</h3>
    <p>범인, 흉기, 동기, 방법이 모두 들어간 문장으로 제출하세요. 오답은 클리어타임에 5분이 추가됩니다.</p>
    <form @submit.prevent="submit">
      <input v-model.trim="answer" placeholder="예: 이몽룡이 망치로 은폐를 위해 교살했다." />
      <button type="submit">제출</button>
    </form>
  </section>
</template>

<script setup>
import { ref } from 'vue';

const emit = defineEmits(['submit']);
const answer = ref('');
const submit = () => {
  if (!answer.value) return;
  emit('submit', answer.value);
  answer.value = '';
};
</script>

<style scoped>
.answer-box { padding: 14px; border: 1px solid rgba(248,113,113,.34); border-radius: 16px; background: rgba(127,29,29,.2); }
h3 { margin: 0 0 6px; color: #fecaca; }
p { margin: 0 0 10px; color: #cbd5e1; font-size: .84rem; line-height: 1.5; }
form { display: grid; grid-template-columns: minmax(0,1fr) auto; gap: 8px; }
input { border: 1px solid rgba(148,163,184,.28); border-radius: 10px; background: rgba(2,6,23,.72); color: #fff; padding: 12px; }
button { border: 0; border-radius: 10px; background: #dc2626; color: #fff; font-weight: 900; padding: 0 14px; }
@media (max-width: 370px) { form { grid-template-columns: 1fr; } button { min-height: 42px; } }
</style>
