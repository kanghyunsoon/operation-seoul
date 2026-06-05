<template>
  <main class="detail-page">
    <button class="back" type="button" @click="router.push({ name: 'EpisodeList' })">목록으로 돌아가기</button>
    <section v-if="loading" class="panel">사건파일을 불러오는 중입니다.</section>
    <section v-else-if="error" class="panel error">{{ error }}</section>
    <section v-else-if="episode" class="panel">
      <p class="eyebrow">{{ episode.era }} · {{ episode.genre }}</p>
      <h1>{{ episode.title }}</h1>
      <h2>{{ episode.subtitle }}</h2>
      <p class="synopsis">{{ episode.fictionSynopsis }}</p>
      <div class="info-grid">
        <span>난이도 <strong>{{ episode.difficulty }}</strong></span>
        <span>예상 시간 <strong>{{ episode.estimatedTime }}</strong></span>
        <span>예상 거리 <strong>{{ episode.estimatedDistance }}</strong></span>
        <span>정답 유형 <strong>{{ episode.finalAnswerType }}</strong></span>
      </div>
      <button class="primary" type="button" @click="start">브리핑으로 이동</button>
      <p v-if="message" class="message">{{ message }}</p>
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
const loading = ref(true);
const error = ref('');
const message = ref('');

onMounted(async () => {
  try {
    episode.value = await episodeApi.getEpisode(episodeId);
  } catch (err) {
    error.value = err.userMessage || '사건파일을 불러오지 못했습니다.';
  } finally {
    loading.value = false;
  }
});

const start = async () => {
  try {
    const started = await episodeApi.startEpisode(episodeId);
    message.value = '에피소드를 시작했습니다.';
    router.push({ name: 'EpisodeBriefing', params: { episodeId: started.id } });
  } catch (err) {
    message.value = err.userMessage || '에피소드를 시작하지 못했습니다.';
  }
};
</script>

<style scoped>
.detail-page { min-height: 100vh; box-sizing: border-box; padding: 22px 16px; background: #111827; color: #f8fafc; font-family: Georgia, 'Noto Sans KR', serif; }
.back { margin: 0 auto 12px; display: block; width: min(100%, 720px); min-height: 38px; border: 1px solid rgba(148,163,184,.28); border-radius: 999px; background: transparent; color: #cbd5e1; }
.panel { width: min(100%, 720px); box-sizing: border-box; margin: 0 auto; padding: 24px; border-radius: 22px; background: linear-gradient(155deg, rgba(120,53,15,.28), rgba(15,23,42,.9)); border: 1px solid rgba(245,158,11,.22); }
.panel.error { color: #fecaca; }
.eyebrow { color: #f59e0b; font-weight: 900; letter-spacing: .08em; }
h1 { margin: 0; font-size: clamp(1.8rem, 9vw, 3.4rem); line-height: 1.05; }
h2 { color: #fde68a; font-size: 1.05rem; }
.synopsis { color: #dbeafe; line-height: 1.75; white-space: pre-line; }
.info-grid { display: grid; gap: 8px; margin: 20px 0; }
.info-grid span { display: flex; justify-content: space-between; gap: 10px; padding: 12px; border-radius: 12px; background: rgba(2,6,23,.34); color: #94a3b8; }
strong { color: #fff; }
.primary { width: 100%; min-height: 48px; border: 0; border-radius: 14px; background: #b45309; color: #fff; font-weight: 900; font: inherit; }
.message { color: #bbf7d0; }
</style>
