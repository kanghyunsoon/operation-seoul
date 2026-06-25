<template>
  <div class="game-nav-shell">
    <button type="button" class="game-home" @click="router.push({ name: 'EpisodeList', query: preservedQuery })">홈</button>
    <nav class="case-tabs" aria-label="플레이 화면 전환">
      <button type="button" :class="{ active: active === 'case' }" @click="go('EpisodeCaseFile')">사건 파일</button>
      <button type="button" :class="{ active: active === 'map' }" @click="go('EpisodeMap')">지도</button>
      <button type="button" :class="{ active: active === 'deduction' }" @click="go('FinalDeduction')">최종추리</button>
    </nav>
  </div>
</template>

<script setup>
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';

const props = defineProps({
  episodeId: { type: [String, Number], required: true },
  active: { type: String, required: true }
});

const router = useRouter();
const route = useRoute();
const preservedQuery = computed(() => route.query.areaCode ? { areaCode: route.query.areaCode } : {});
const go = (name) => router.push({ name, params: { episodeId: props.episodeId }, query: preservedQuery.value });
</script>

<style scoped>
.game-nav-shell { width: 100%; }
.game-home { position: fixed; top: 14px; left: 14px; z-index: 60; min-width: 54px; min-height: 40px; padding: 0 14px; border: 1px solid rgba(250,204,21,.42); border-radius: 12px; background: rgba(120,53,15,.9); color: #fef3c7; font: inherit; font-weight: 900; box-shadow: 0 12px 26px rgba(0,0,0,.32); }
.case-tabs { width: min(100%, 430px); margin: 52px auto 12px; display: grid; grid-template-columns: repeat(3, 1fr); gap: 6px; padding: 6px; border: 1px solid rgba(148,163,184,.22); border-radius: 16px; background: rgba(15,23,42,.72); }
.case-tabs button { min-height: 40px; border: 0; border-radius: 12px; background: transparent; color: #cbd5e1; font: inherit; font-weight: 900; }
.case-tabs button.active { background: #b45309; color: #fff7ed; box-shadow: 0 8px 18px rgba(180,83,9,.25); }
@media (max-width: 370px) { .case-tabs button, .game-home { font-size: .84rem; } }
</style>
