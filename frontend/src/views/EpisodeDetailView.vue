<template>
  <main class="detail-page">
    <button class="back" type="button" @click="goEpisodeList">목록으로 돌아가기</button>
    <section v-if="loading" class="panel">미션 파일을 불러오는 중입니다.</section>
    <section v-else-if="error" class="panel error">{{ error }}</section>
    <section v-else-if="episode" class="panel">
      <div class="top-row">
        <p class="eyebrow">{{ episode.era }} · {{ episode.genre }}</p>
        <div class="top-actions">
          <button
            type="button"
            class="favorite-btn"
            :class="{ active: episode.favorited }"
            :disabled="favoriteBusy"
            @click="toggleFavorite"
          >
            {{ episode.favorited ? '♥ 관심 해제' : '♡ 관심 추가' }}
          </button>
          <button type="button" class="ranking-btn" @click="openRanking">🏆 랭킹</button>
        </div>
      </div>
      <h1>{{ episode.title }}</h1>
      <h2>{{ episode.subtitle }}</h2>
      <p class="synopsis">{{ episode.missionDescription || episode.fictionSynopsis }}</p>
      <div class="info-grid">
        <span>난이도 <strong>{{ episode.difficulty }}</strong></span>
        <span>예상 시간 <strong>{{ episode.estimatedTime }}</strong></span>
        <span>예상 거리 <strong>{{ episode.estimatedDistance }}</strong></span>
        <span>정답 유형 <strong>{{ episode.finalAnswerType }}</strong></span>
      </div>
      <div class="actions">
        <button class="primary" type="button" @click="openPrimaryAction">
          {{ cleared ? '클리어 리포트 열기' : '미션 파일 열기' }}
        </button>
        <button class="secondary" type="button" @click="showReviews">해당 미션 리뷰 보기</button>
      </div>
      <p v-if="message" class="message" :class="messageType">{{ message }}</p>
      <EpisodeReviewPanel
        v-if="reviewsOpen"
        id="episode-reviews"
        :episode-id="episodeId"
        title="미션 리뷰"
      />
    </section>
  </main>
</template>

<script setup>
import { computed, nextTick, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { episodeApi } from '@/api/episodeApi';
import { favoriteApi } from '@/api/favoriteApi';
import EpisodeReviewPanel from '@/components/episode/EpisodeReviewPanel.vue';

const route = useRoute();
const router = useRouter();
const episodeId = route.params.episodeId;
const episode = ref(null);
const loading = ref(true);
const error = ref('');
const message = ref('');
const messageType = ref('success');
const favoriteBusy = ref(false);
const reviewsOpen = ref(false);

const cleared = computed(() => episode.value?.progressStatus === 'CLEARED');
const listQuery = computed(() => route.query.areaCode ? { areaCode: route.query.areaCode } : {});

onMounted(async () => {
  try {
    episode.value = await episodeApi.getEpisode(episodeId);
  } catch (err) {
    error.value = err.userMessage || '미션 파일을 불러오지 못했습니다.';
  } finally {
    loading.value = false;
  }
});

async function openPrimaryAction() {
  if (cleared.value) {
    router.push({ name: 'EpisodeClearReport', params: { episodeId }, query: listQuery.value });
    return;
  }
  await start();
}

const start = async () => {
  try {
    const started = await episodeApi.startEpisode(episodeId);
    setMessage('에피소드를 시작했습니다.');
    router.push({ name: 'EpisodeMissionBriefing', params: { episodeId: started.id }, query: listQuery.value });
  } catch (err) {
    setMessage(err.userMessage || '에피소드를 시작하지 못했습니다.', 'error');
  }
};

async function toggleFavorite() {
  if (!episode.value) return;
  favoriteBusy.value = true;
  try {
    if (episode.value.favorited) {
      await favoriteApi.removeFavorite(episode.value.id);
      episode.value.favorited = false;
      setMessage('관심 에피소드에서 제거했습니다.');
    } else {
      await favoriteApi.addFavorite(episode.value.id);
      episode.value.favorited = true;
      setMessage('관심 에피소드에 추가했습니다.');
    }
  } catch (err) {
    setMessage(err.userMessage || '관심 에피소드 상태를 변경하지 못했습니다.', 'error');
  } finally {
    favoriteBusy.value = false;
  }
}

async function showReviews() {
  reviewsOpen.value = true;
  await nextTick();
  document.getElementById('episode-reviews')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

function openRanking() {
  router.push({ name: 'Ranking', query: { episodeId, title: episode.value?.title || '' } });
}

function goEpisodeList() {
  router.push({ name: 'EpisodeList', query: listQuery.value });
}

function setMessage(text, type = 'success') {
  message.value = text;
  messageType.value = type;
}
</script>

<style scoped>
.detail-page { min-height: 100vh; box-sizing: border-box; padding: 22px 16px; background: #111827; color: #f8fafc; font-family: Georgia, 'Noto Sans KR', serif; }
.back { margin: 0 auto 12px; display: block; width: min(100%, 720px); min-height: 38px; border: 1px solid rgba(148,163,184,.28); border-radius: 999px; background: transparent; color: #cbd5e1; }
.panel { width: min(100%, 720px); box-sizing: border-box; margin: 0 auto; padding: 24px; border-radius: 22px; background: linear-gradient(155deg, rgba(120,53,15,.28), rgba(15,23,42,.9)); border: 1px solid rgba(245,158,11,.22); }
.panel.error { color: #fecaca; }
.top-row { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.eyebrow { margin: 0 0 14px; color: #f59e0b; font-weight: 900; letter-spacing: .08em; }
h1 { margin: 0; font-size: clamp(1.8rem, 9vw, 3.4rem); line-height: 1.05; }
h2 { color: #fde68a; font-size: 1.05rem; }
.synopsis { color: #dbeafe; line-height: 1.75; white-space: pre-line; }
.info-grid { display: grid; gap: 8px; margin: 20px 0; }
.info-grid span { display: flex; justify-content: space-between; gap: 10px; padding: 12px; border-radius: 12px; background: rgba(2,6,23,.34); color: #94a3b8; }
strong { color: #fff; }
.actions { display: grid; gap: 10px; }
.primary, .secondary, .favorite-btn, .ranking-btn { width: 100%; min-height: 48px; border: 0; border-radius: 14px; color: #fff; font-weight: 900; font: inherit; }
.primary { background: #b45309; }
.secondary { background: #334155; }
.top-actions { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 8px; }
.favorite-btn { width: auto; min-height: 40px; padding: 0 14px; border: 1px solid rgba(251,191,36,.35); background: rgba(15,23,42,.7); color: #fde68a; white-space: nowrap; }
.favorite-btn.active { background: #b45309; color: #fff7ed; }
.ranking-btn { width: auto; min-height: 40px; padding: 0 14px; border: 1px solid rgba(251,191,36,.35); background: rgba(120,53,15,.82); color: #fde68a; white-space: nowrap; }
.message { border-radius: 12px; padding: 12px; background: rgba(22,101,52,.18); color: #bbf7d0; }
.message.error { background: rgba(127,29,29,.22); color: #fecaca; }
@media (max-width: 560px) {
  .panel { padding: 20px; }
  .top-row { display: grid; }
  .top-actions { display: grid; }
  .favorite-btn, .ranking-btn { width: 100%; }
}
</style>
