<template>
  <main class="episode-page">
    <header class="hero">
      <div>
        <p>CASE FILES</p>
        <h1>Operation Korea</h1>
        <span>{{ sessionStore.currentUser?.nickname || '요원' }}님, 조사할 사건파일을 선택하세요.</span>
      </div>
      <div v-if="sessionStore.isAdmin" class="admin-actions">
        <button type="button" class="admin-link primary" @click="router.push({ name: 'AdminEpisodes' })">사건파일 생성/관리</button>
        <button type="button" class="admin-link" @click="router.push({ name: 'AdminUsers' })">회원 관리</button>
        <button type="button" class="admin-link" @click="router.push({ name: 'AdminReviews' })">리뷰 관리</button>
      </div>
    </header>

    <section v-if="loading" class="state">사건파일을 불러오는 중입니다.</section>
    <section v-else-if="error" class="state error">{{ error }}</section>
    <section v-else class="episode-list">
      <article v-for="episode in episodes" :key="episode.id" class="case-card" @click="openEpisode(episode.id)">
        <div class="stamp">CASE {{ String(episode.id).padStart(2, '0') }}</div>
        <h2>{{ episode.title }}</h2>
        <p>{{ episode.subtitle }}</p>
        <div class="meta">
          <span>{{ episode.era }}</span>
          <span>{{ episode.genre }}</span>
          <span>{{ episode.difficulty }}</span>
        </div>
        <button type="button">사건파일 열기</button>
      </article>
    </section>
  </main>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { useSessionStore } from '@/stores/sessionStore';
import { episodeApi } from '@/api/episodeApi';

const router = useRouter();
const sessionStore = useSessionStore();
const episodes = ref([]);
const loading = ref(true);
const error = ref('');

onMounted(async () => {
  try {
    episodes.value = await episodeApi.listEpisodes();
  } catch (err) {
    error.value = err.userMessage || '에피소드 목록을 불러오지 못했습니다.';
  } finally {
    loading.value = false;
  }
});

const openEpisode = (episodeId) => router.push({ name: 'EpisodeDetail', params: { episodeId } });
</script>

<style scoped>
.episode-page { min-height: 100vh; box-sizing: border-box; padding: 28px 16px 60px; background: radial-gradient(circle at 15% 10%, rgba(180,83,9,.25), transparent 32%), linear-gradient(160deg, #17110b, #111827 58%, #050505); color: #f8fafc; font-family: Georgia, 'Noto Sans KR', serif; }
.hero { width: min(100%, 880px); margin: 0 auto 24px; padding: 22px; border: 1px solid rgba(245,158,11,.26); border-radius: 20px; background: rgba(15,23,42,.54); display: flex; align-items: flex-end; justify-content: space-between; gap: 14px; }
.hero p { margin: 0 0 8px; color: #f59e0b; font-size: .78rem; font-weight: 900; letter-spacing: .16em; }
h1 { margin: 0; font-size: clamp(2rem, 9vw, 4rem); line-height: 1; }
.hero span { display: block; margin-top: 12px; color: #cbd5e1; }
.episode-list { width: min(100%, 880px); margin: 0 auto; display: grid; gap: 16px; }
.case-card { position: relative; overflow: hidden; padding: 24px; border: 1px solid rgba(248,250,252,.13); border-radius: 20px; background: linear-gradient(135deg, rgba(255,247,237,.1), rgba(15,23,42,.75)); box-shadow: 0 24px 60px rgba(0,0,0,.22); cursor: pointer; }
.case-card:before { content: ''; position: absolute; inset: 0 auto 0 0; width: 7px; background: #b45309; }
.stamp { display: inline-block; transform: rotate(-2deg); border: 2px solid rgba(248,113,113,.75); padding: 5px 9px; color: #fca5a5; font-weight: 900; font-size: .72rem; }
h2 { margin: 16px 0 8px; font-size: 1.45rem; }
.case-card p { margin: 0; color: #cbd5e1; line-height: 1.55; }
.meta { display: flex; flex-wrap: wrap; gap: 7px; margin: 18px 0; }
.meta span { border: 1px solid rgba(245,158,11,.28); border-radius: 999px; padding: 5px 9px; color: #fde68a; font-size: .78rem; }
button { min-height: 42px; border: 0; border-radius: 12px; background: #b45309; color: white; font: inherit; font-weight: 900; padding: 0 15px; }
.admin-actions { display: flex; flex-wrap: wrap; gap: 8px; }
.admin-link { flex: 0 0 auto; background: #334155; }
.admin-link.primary { background: #b45309; }
.state { width: min(100%, 880px); margin: 0 auto; padding: 24px; border: 1px dashed rgba(148,163,184,.35); border-radius: 16px; color: #cbd5e1; text-align: center; }
.state.error { color: #fecaca; }
@media (max-width: 560px) {
  .hero { flex-direction: column; align-items: stretch; }
  .admin-actions { display: grid; grid-template-columns: 1fr; }
}
</style>
