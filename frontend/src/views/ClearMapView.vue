<template>
  <main class="tab-page">
    <header class="hero">
      <button type="button" class="back" @click="router.push({ name: 'EpisodeList' })">미션 파일 목록</button>
      <p>CLEAR MAP</p>
      <h1>클리어 맵</h1>
      <span>클리어한 방탈출 기록을 도장 카드로 확인합니다.</span>
    </header>

    <section v-if="loading" class="state">클리어 기록을 불러오는 중입니다.</section>
    <section v-else-if="error" class="state error">{{ error }}</section>
    <section v-else-if="!clearedEpisodes.length" class="state">아직 클리어한 미션이 없습니다.</section>
    <section v-else class="clear-grid">
      <article v-for="episode in clearedEpisodes" :key="episode.id" class="clear-card" @click="router.push({ name: 'EpisodeClearReport', params: { episodeId: episode.id } })">
        <div class="stamp">
          <span>MISSION</span>
          <strong>COMPLETE</strong>
        </div>
        <small>CASE {{ String(episode.id).padStart(2, '0') }}</small>
        <h2>{{ episode.title }}</h2>
        <p>{{ episode.subtitle }}</p>
        <div class="meta">
          <span>{{ episode.era }}</span>
          <span>{{ episode.genre }}</span>
          <span>{{ episode.difficulty }}</span>
        </div>
      </article>
    </section>

    <MainBottomNav />
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import MainBottomNav from '@/components/MainBottomNav.vue';
import { episodeApi } from '@/api/episodeApi';

const router = useRouter();
const episodes = ref([]);
const loading = ref(true);
const error = ref('');
const clearedEpisodes = computed(() => episodes.value.filter((episode) => episode.cleared || episode.progressStatus === 'CLEARED'));

onMounted(loadEpisodes);

async function loadEpisodes() {
  loading.value = true;
  error.value = '';
  try {
    const response = await episodeApi.listEpisodes({ limit: 100, offset: 0 });
    episodes.value = Array.isArray(response) ? response : (response.items || []);
  } catch (err) {
    error.value = err.userMessage || '클리어 기록을 불러오지 못했습니다.';
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.tab-page { min-height: 100vh; box-sizing: border-box; padding: 24px 16px 126px; background: radial-gradient(circle at 15% 10%, rgba(180,83,9,.22), transparent 32%), linear-gradient(160deg, #17110b, #111827 58%, #050505); color: #f8fafc; font-family: Georgia, 'Noto Sans KR', serif; }
.hero, .clear-grid, .state { width: min(100%, 880px); box-sizing: border-box; margin-left: auto; margin-right: auto; }
.hero { margin-bottom: 18px; padding: 22px; border: 1px solid rgba(245,158,11,.24); border-radius: 20px; background: rgba(15,23,42,.58); }
.back { min-height: 38px; border: 1px solid rgba(148,163,184,.28); border-radius: 999px; background: transparent; color: #cbd5e1; padding: 0 14px; }
.hero p { margin: 18px 0 8px; color: #f59e0b; font-weight: 900; letter-spacing: .16em; font-size: .78rem; }
h1 { margin: 0; font-size: clamp(2rem, 9vw, 3.5rem); line-height: 1; }
.hero span { display: block; margin-top: 10px; color: #cbd5e1; }
.clear-grid { display: grid; grid-template-columns: repeat(2, minmax(0,1fr)); gap: 14px; }
.clear-card { position: relative; min-height: 210px; padding: 20px; border-radius: 18px; border: 1px solid rgba(248,250,252,.13); background: linear-gradient(135deg, rgba(255,247,237,.09), rgba(15,23,42,.78)); box-shadow: 0 20px 52px rgba(0,0,0,.22); cursor: pointer; overflow: hidden; }
.stamp { position: absolute; right: 16px; bottom: 16px; width: 132px; height: 56px; display: grid; place-items: center; border: 4px solid rgba(220,38,38,.9); border-radius: 4px; color: rgba(239,68,68,.96); background: repeating-linear-gradient(105deg, rgba(220,38,38,.18) 0 1px, transparent 1px 9px); text-align: center; transform: rotate(-4deg); opacity: .9; }
.stamp span, .stamp strong { display: block; line-height: .9; font-weight: 1000; letter-spacing: .06em; }
.stamp span { font-size: 1.16rem; }
.stamp strong { font-size: 1rem; }
.clear-card small { color: #fca5a5; font-weight: 900; letter-spacing: .1em; }
h2 { margin: 14px 120px 8px 0; font-size: 1.28rem; line-height: 1.28; }
p { margin: 0 120px 0 0; color: #cbd5e1; line-height: 1.55; }
.meta { display: flex; flex-wrap: wrap; gap: 7px; margin: 14px 120px 0 0; }
.meta span { border: 1px solid rgba(245,158,11,.28); border-radius: 999px; padding: 5px 9px; color: #fde68a; font-size: .78rem; }
.state { padding: 16px; border: 1px dashed rgba(148,163,184,.34); border-radius: 16px; color: #cbd5e1; text-align: center; }
.state.error { color: #fecaca; background: rgba(127,29,29,.18); }
@media (max-width: 560px) {
  .clear-grid { grid-template-columns: 1fr; }
  h2, p, .meta { margin-right: 0; }
  .stamp { position: static; margin-top: 16px; }
}
</style>
