<template>
  <nav class="case-tabs" aria-label="플레이 화면 전환">
    <button type="button" class="home" @click="router.push({ name: 'EpisodeList' })">홈</button>
    <button type="button" :class="{ active: active === 'map' }" @click="go('EpisodeMap')">지도</button>
    <button type="button" :class="{ active: active === 'case' }" @click="go('EpisodeCaseFile')">사건파일</button>
    <button type="button" :class="{ active: active === 'deduction' }" @click="go('FinalDeduction')">최종추리</button>
  </nav>
</template>

<script setup>
import { useRouter } from 'vue-router';

const props = defineProps({
  episodeId: { type: [String, Number], required: true },
  active: { type: String, required: true }
});

const router = useRouter();
const go = (name) => router.push({ name, params: { episodeId: props.episodeId } });
</script>

<style scoped>
.case-tabs { width: min(100%, 430px); margin: 0 auto 12px; display: grid; grid-template-columns: .82fr repeat(3, 1fr); gap: 6px; padding: 6px; border: 1px solid rgba(148,163,184,.22); border-radius: 16px; background: rgba(15,23,42,.72); }
button { min-height: 40px; border: 0; border-radius: 12px; background: transparent; color: #cbd5e1; font: inherit; font-weight: 900; }
button.home { color: #fde68a; }
button.active { background: #b45309; color: #fff7ed; box-shadow: 0 8px 18px rgba(180,83,9,.25); }
@media (max-width: 370px) { button { font-size: .84rem; } }
</style>
