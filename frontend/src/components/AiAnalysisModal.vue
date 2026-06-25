<template>
  <div class="analysis-backdrop" @click.self="$emit('close')">
    <section class="analysis-modal" role="dialog" aria-modal="true" aria-label="AI 플레이 분석">
      <button type="button" class="modal-close" aria-label="닫기" @click="$emit('close')">×</button>
      <p class="eyebrow">AI PLAY ANALYSIS</p>
      <h2>AI 플레이 분석</h2>

      <div v-if="loading" class="modal-state">분석 결과를 불러오는 중입니다.</div>
      <div v-else-if="error && !analysis" class="modal-state error-state">{{ error }}</div>
      <div v-else-if="!analysis" class="modal-state">아직 분석할 플레이 기록이 없습니다.</div>
      <div v-else class="analysis-content">
        <p v-if="error" class="notice">{{ error }}</p>
        <strong class="type-title">당신은 {{ analysis.playerType }} 유형입니다.</strong>
        <p class="summary">{{ analysis.summary }}</p>

        <div class="text-grid">
          <article>
            <h3>강점</h3>
            <p>{{ analysis.strength }}</p>
          </article>
          <article>
            <h3>보완점</h3>
            <p>{{ analysis.weakness }}</p>
          </article>
          <article>
            <h3>추천</h3>
            <p>{{ analysis.recommendation }}</p>
          </article>
        </div>

        <section class="mbti-section">
          <h3>플레이 MBTI</h3>
          <article v-for="item in analysis.playMbti || []" :key="item.dimension" class="mbti-row">
            <strong>{{ item.dimension }}</strong>
            <div class="mbti-labels">
              <span>{{ item.leftLabel }} {{ item.leftPercent }}%</span>
              <span>{{ item.rightPercent }}% {{ item.rightLabel }}</span>
            </div>
            <div class="bar" aria-hidden="true">
              <span :style="{ width: `${item.leftPercent}%` }"></span>
            </div>
          </article>
        </section>
      </div>
    </section>
  </div>
</template>

<script setup>
defineProps({
  analysis: { type: Object, default: null },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' }
});

defineEmits(['close']);
</script>

<style scoped>
.analysis-backdrop {
  position: fixed;
  inset: 0;
  z-index: 80;
  display: grid;
  place-items: center;
  padding: 18px;
  background: rgba(2,6,23,.72);
  backdrop-filter: blur(8px);
}
.analysis-modal {
  position: relative;
  width: min(560px, 100%);
  max-height: min(760px, calc(100dvh - 36px));
  overflow: auto;
  box-sizing: border-box;
  padding: 24px;
  border: 1px solid rgba(125,211,252,.28);
  border-radius: 12px;
  background: linear-gradient(160deg, rgba(15,23,42,.98), rgba(30,41,59,.98));
  color: #f8fafc;
  box-shadow: 0 28px 80px rgba(0,0,0,.45);
}
.modal-close {
  position: absolute;
  top: 14px;
  right: 14px;
  width: 34px;
  min-height: 34px;
  padding: 0;
  border: 1px solid rgba(148,163,184,.35);
  border-radius: 50%;
  background: rgba(15,23,42,.82);
  color: #e2e8f0;
  font-size: 1.35rem;
  line-height: 1;
}
.eyebrow {
  margin: 0 0 8px;
  color: #7dd3fc;
  font-size: .72rem;
  font-weight: 1000;
  letter-spacing: .14em;
}
h2 { margin: 0 42px 18px 0; font-size: 1.65rem; }
.modal-state {
  padding: 34px 12px;
  border: 1px dashed rgba(148,163,184,.28);
  border-radius: 10px;
  color: #cbd5e1;
  text-align: center;
}
.error-state {
  border-color: rgba(248,113,113,.38);
  color: #fecaca;
  background: rgba(127,29,29,.18);
}
.notice {
  margin: 0 0 12px;
  padding: 10px 12px;
  border-radius: 8px;
  background: rgba(120,53,15,.28);
  color: #fde68a;
}
.type-title {
  display: block;
  color: #fef3c7;
  font-size: 1.25rem;
}
.summary {
  margin: 10px 0 16px;
  color: #cbd5e1;
  line-height: 1.55;
}
.text-grid {
  display: grid;
  gap: 10px;
}
.text-grid article {
  padding: 12px;
  border: 1px solid rgba(148,163,184,.16);
  border-radius: 8px;
  background: rgba(2,6,23,.32);
}
h3 {
  margin: 0 0 7px;
  color: #bae6fd;
  font-size: .98rem;
}
.text-grid p {
  margin: 0;
  color: #e2e8f0;
  line-height: 1.45;
}
.mbti-section {
  margin-top: 18px;
}
.mbti-row {
  display: grid;
  gap: 8px;
  margin-top: 12px;
}
.mbti-row > strong {
  color: #fff7ed;
}
.mbti-labels {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  color: #cbd5e1;
  font-size: .86rem;
  font-weight: 800;
}
.bar {
  height: 12px;
  overflow: hidden;
  border-radius: 999px;
  background: rgba(148,163,184,.3);
}
.bar span {
  display: block;
  height: 100%;
  min-width: 2px;
  border-radius: inherit;
  background: linear-gradient(90deg, #38bdf8, #facc15);
}
@media (max-width: 560px) {
  .analysis-backdrop { padding: 10px; align-items: end; }
  .analysis-modal { max-height: calc(100dvh - 20px); padding: 20px 16px; }
  h2 { font-size: 1.35rem; }
  .mbti-labels { font-size: .78rem; }
}
</style>
