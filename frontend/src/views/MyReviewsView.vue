<template>
  <main class="reviews-page">
    <header class="hero">
      <button type="button" class="back" @click="router.push({ name: 'MyPage' })">뒤로가기</button>
      <p>MY REVIEWS</p>
      <h1>내가 쓴 리뷰</h1>
      <span>내가 작성한 미션 리뷰를 모아봅니다.</span>
    </header>

    <p v-if="message" class="toast error">{{ message }}</p>
    <section class="review-panel">
      <div class="panel-head">
        <div>
          <p>REVIEWS</p>
          <h2>리뷰 목록</h2>
        </div>
        <button type="button" @click="loadMyReviews">새로고침</button>
      </div>

      <p v-if="loading" class="state">리뷰를 불러오는 중입니다.</p>
      <p v-else-if="!myReviews.length" class="state">아직 작성한 리뷰가 없습니다.</p>
      <article v-for="review in myReviews" :key="review.id" class="review-card">
        <div>
          <strong>{{ review.episodeTitle || review.title || '미션 리뷰' }}</strong>
          <span>별점 {{ review.rating || 0 }} · 난이도 {{ review.difficultyRating || '-' }}</span>
        </div>
        <p>{{ review.content }}</p>
        <button
          v-if="review.episodeId"
          type="button"
          @click="router.push({ name: 'EpisodeDetail', params: { episodeId: review.episodeId } })"
        >
          사건 파일 보기
        </button>
      </article>
    </section>

    <MainBottomNav />
  </main>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import MainBottomNav from '@/components/MainBottomNav.vue';
import { reviewApi } from '@/api/reviewApi';

const router = useRouter();
const myReviews = ref([]);
const loading = ref(true);
const message = ref('');

onMounted(loadMyReviews);

async function loadMyReviews() {
  loading.value = true;
  message.value = '';
  try {
    myReviews.value = await reviewApi.getMyReviews();
  } catch (err) {
    message.value = err.userMessage || '내 리뷰 목록을 불러오지 못했습니다.';
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.reviews-page { min-height: 100vh; box-sizing: border-box; padding: 24px 16px 126px; background: radial-gradient(circle at 80% 0%, rgba(245,158,11,.2), transparent 34%), linear-gradient(160deg, #0f172a, #111827 60%, #050505); color: #f8fafc; font-family: Georgia, 'Noto Sans KR', serif; }
.hero, .review-panel, .toast { width: min(100%, 880px); box-sizing: border-box; margin-left: auto; margin-right: auto; }
.hero { margin-bottom: 18px; padding: 22px; border: 1px solid rgba(245,158,11,.24); border-radius: 20px; background: rgba(15,23,42,.58); }
.back { min-height: 38px; border: 1px solid rgba(148,163,184,.28); border-radius: 999px; background: transparent; color: #cbd5e1; padding: 0 14px; }
.hero p, .panel-head p { margin: 18px 0 8px; color: #f59e0b; font-weight: 900; letter-spacing: .16em; font-size: .78rem; }
.panel-head p { margin-top: 0; color: #67e8f9; }
h1 { margin: 0; font-size: clamp(2rem, 9vw, 3.5rem); line-height: 1; }
.hero span { display: block; margin-top: 10px; color: #cbd5e1; }
.review-panel { padding: 18px; border: 1px solid rgba(125,211,252,.2); border-radius: 20px; background: rgba(15,23,42,.62); box-shadow: 0 20px 52px rgba(0,0,0,.18); }
.panel-head { display: flex; justify-content: space-between; align-items: end; gap: 12px; margin-bottom: 12px; }
.panel-head h2 { margin: 0; }
button { min-height: 40px; border: 0; border-radius: 12px; background: #334155; color: #fff; font: inherit; font-weight: 900; padding: 0 14px; }
.review-card { padding: 16px 0; border-top: 1px solid rgba(148,163,184,.14); }
.review-card:first-of-type { border-top: 0; }
.review-card strong { display: block; color: #fde68a; font-size: 1.05rem; }
.review-card span { display: block; margin-top: 5px; color: #94a3b8; font-size: .86rem; }
.review-card p { color: #cbd5e1; line-height: 1.55; white-space: pre-wrap; }
.review-card button { background: #b45309; }
.state, .toast { padding: 16px; border: 1px dashed rgba(148,163,184,.34); border-radius: 16px; color: #cbd5e1; text-align: center; }
.toast { margin-bottom: 12px; border-style: solid; background: rgba(127,29,29,.24); color: #fecaca; }
@media (max-width: 560px) {
  .panel-head { display: block; }
  .panel-head button { width: 100%; margin-top: 10px; }
}
</style>
