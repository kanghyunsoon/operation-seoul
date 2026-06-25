<template>
  <main class="recommend-page">
    <header class="hero">
      <div>
        <p>AI CURATION</p>
        <h1>맞춤 추천</h1>
        <span>관심과 클리어 기록을 바탕으로 AI가 다음 사건 파일을 추천합니다.</span>
      </div>
      <div class="hero-actions">
        <button type="button" @click="loadRecommendations">다시 분석</button>
      </div>
    </header>

    <p v-if="message" class="toast error">{{ message }}</p>
    <section v-if="loading" class="state">추천 정보를 분석하는 중입니다.</section>
    <section v-else-if="!recommendations.length" class="state">추천 가능한 공개 사건 파일이 없습니다.</section>
    <section v-else class="recommend-list">
      <article
        v-for="item in recommendations"
        :key="item.episodeId"
        class="recommend-card"
        :class="{ cleared: item.cleared }"
      >
        <div class="score-box">
          <strong>{{ item.score }}</strong>
          <span>추천 점수</span>
        </div>
        <div class="card-body">
          <div class="badges">
            <span v-if="item.favorited">관심</span>
            <span v-if="item.cleared">클리어</span>
            <span>{{ item.difficulty }}</span>
          </div>
          <h2>{{ item.title }}</h2>
          <p class="subtitle">{{ item.subtitle }}</p>
          <p class="reason">{{ item.reason }}</p>
          <div class="meta">
            <span>{{ item.era }}</span>
            <span>{{ item.genre }}</span>
            <span>{{ item.estimatedTime }}</span>
            <span>{{ item.estimatedDistance }}</span>
          </div>
          <div class="actions">
            <button type="button" @click="openEpisode(item)">상세 보기</button>
          </div>
        </div>
      </article>
    </section>
  </main>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { recommendationApi } from '@/api/recommendationApi';

const router = useRouter();
const recommendations = ref([]);
const loading = ref(true);
const message = ref('');

onMounted(loadRecommendations);

async function loadRecommendations() {
  loading.value = true;
  message.value = '';
  try {
    recommendations.value = await recommendationApi.getEpisodeRecommendations({ limit: 8 });
  } catch (err) {
    message.value = err.userMessage || '추천을 불러오지 못했습니다.';
  } finally {
    loading.value = false;
  }
}

function openEpisode(item) {
  router.push({ name: 'EpisodeDetail', params: { episodeId: item.episodeId } });
}
</script>

<style scoped>
.recommend-page { min-height: 100vh; box-sizing: border-box; padding: 26px 16px 72px; background: radial-gradient(circle at 12% 0%, rgba(14,165,233,.22), transparent 30%), linear-gradient(145deg, #08111f, #111827 58%, #030712); color: #f8fafc; font-family: 'Noto Serif KR', Georgia, serif; }
.hero, .recommend-list, .state, .toast { width: min(100%, 980px); box-sizing: border-box; margin-left: auto; margin-right: auto; }
.hero { display: flex; justify-content: space-between; align-items: end; gap: 16px; margin-bottom: 16px; padding: 22px; border: 1px solid rgba(56,189,248,.24); border-radius: 24px; background: rgba(15,23,42,.68); }
.hero p { margin: 0 0 8px; color: #7dd3fc; font-size: .74rem; font-weight: 1000; letter-spacing: .16em; }
h1 { margin: 0; font-size: clamp(2.3rem, 9vw, 4.8rem); line-height: .94; }
.hero span { display: block; margin-top: 10px; color: #cbd5e1; }
.hero-actions, .actions { display: flex; flex-wrap: wrap; gap: 8px; }
button { min-height: 40px; border: 0; border-radius: 12px; background: #0369a1; color: #fff; font: inherit; font-weight: 900; padding: 0 14px; }
.ghost { background: #334155; }
.recommend-list { display: grid; gap: 14px; }
.recommend-card { display: grid; grid-template-columns: 108px 1fr; gap: 14px; padding: 16px; border: 1px solid rgba(148,163,184,.18); border-radius: 24px; background: linear-gradient(135deg, rgba(14,165,233,.13), rgba(2,6,23,.74)); box-shadow: 0 24px 64px rgba(0,0,0,.22); }
.recommend-card.cleared { opacity: .78; }
.score-box { display: grid; place-content: center; min-height: 108px; border-radius: 22px; background: #075985; color: #e0f2fe; text-align: center; }
.score-box strong { font-size: 2.3rem; line-height: 1; }
.score-box span { margin-top: 8px; font-size: .78rem; font-weight: 900; }
.badges, .meta { display: flex; flex-wrap: wrap; gap: 7px; }
.badges span { border-radius: 999px; padding: 5px 9px; background: rgba(125,211,252,.15); color: #bae6fd; font-size: .78rem; font-weight: 900; }
h2 { margin: 12px 0 6px; color: #fff7ed; font-size: 1.5rem; }
.subtitle { margin: 0; color: #cbd5e1; }
.reason { margin: 12px 0; padding: 12px; border-left: 4px solid #38bdf8; border-radius: 12px; background: rgba(2,6,23,.42); color: #e0f2fe; line-height: 1.55; }
.meta { margin-bottom: 12px; }
.meta span { border: 1px solid rgba(125,211,252,.22); border-radius: 999px; padding: 5px 9px; color: #bae6fd; font-size: .78rem; }
.state, .toast { padding: 16px; border: 1px dashed rgba(148,163,184,.34); border-radius: 16px; color: #cbd5e1; text-align: center; }
.toast { margin-bottom: 12px; border-style: solid; background: rgba(127,29,29,.24); color: #fecaca; }
@media (max-width: 640px) {
  .hero, .recommend-card { display: grid; grid-template-columns: 1fr; }
  .hero-actions, .actions { display: grid; grid-template-columns: 1fr; }
  .score-box { min-height: 80px; }
}
</style>
