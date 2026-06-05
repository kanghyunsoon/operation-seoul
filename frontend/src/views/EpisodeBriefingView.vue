<template>
  <main class="briefing-page">
    <section v-if="episode" class="briefing-card">
      <p class="label">CASE BRIEFING</p>
      <h1>{{ episode.title }}</h1>
      <p class="synopsis">{{ episode.fictionSynopsis }}</p>
      <div class="mission-rule">
        <strong>조사 방식</strong>
        <span>전체 장소는 처음부터 지도에 표시됩니다. 최종 후보 중 실제 최종 장소는 서버 내부에서만 판정되며, 사건파일에서 단서와 자료를 계속 대조해야 합니다.</span>
      </div>
      <div class="mission-rule">
        <strong>최종 질문</strong>
        <span>{{ episode.finalQuestion }}</span>
      </div>
      <div class="actions">
        <button type="button" @click="router.push({ name: 'EpisodeMap', params: { episodeId } })">지도 진입</button>
        <button type="button" class="secondary" @click="router.push({ name: 'EpisodeCaseFile', params: { episodeId } })">사건파일 열기</button>
      </div>
    </section>
  </main>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { episodeApi } from '@/api/episodeApi';

const route = useRoute();
const router = useRouter();
const episodeId = route.params.episodeId;
const episode = ref(null);

onMounted(async () => { episode.value = await episodeApi.getEpisode(episodeId); });
</script>

<style scoped>
.briefing-page { min-height: 100vh; display: grid; place-items: center; box-sizing: border-box; padding: 20px 16px; background: radial-gradient(circle at top, rgba(127,29,29,.25), transparent 38%), #020617; color: #f8fafc; font-family: Georgia, 'Noto Sans KR', serif; }
.briefing-card { width: min(100%, 700px); padding: 26px; border: 1px solid rgba(248,113,113,.28); border-radius: 24px; background: rgba(15,23,42,.84); box-shadow: 0 30px 80px rgba(0,0,0,.36); }
.label { color: #fca5a5; font-weight: 900; letter-spacing: .16em; font-size: .76rem; } h1 { margin: 0 0 16px; font-size: clamp(1.9rem, 9vw, 3.7rem); line-height: 1; } .synopsis { color: #dbeafe; line-height: 1.8; }
.mission-rule { display: grid; gap: 6px; margin-top: 12px; padding: 14px; border-left: 3px solid #f97316; background: rgba(30,41,59,.52); } strong { color: #fed7aa; } span { color: #cbd5e1; line-height: 1.55; }
.actions { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; margin-top: 22px; } button { min-height: 50px; border: 0; border-radius: 14px; background: #dc2626; color: #fff; font: inherit; font-weight: 900; } .secondary { background: #334155; }
@media (max-width: 390px) { .actions { grid-template-columns: 1fr; } }
</style>