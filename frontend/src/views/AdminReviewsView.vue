<template>
  <main class="admin-review-page">
    <header class="admin-hero">
      <div>
        <p>ADMIN CONSOLE</p>
        <h1>리뷰 관리</h1>
        <span>에피소드 리뷰를 숨김, 복구, 삭제 처리합니다.</span>
      </div>
      <div class="hero-actions">
        <button type="button" @click="router.push({ name: 'AdminEpisodes' })">에피소드 관리</button>
        <button type="button" @click="router.push({ name: 'EpisodeList' })">사건 목록</button>
      </div>
    </header>

    <section class="filters">
      <label>
        상태
        <select v-model="filters.status" @change="loadReviews">
          <option value="ALL">전체</option>
          <option value="VISIBLE">표시</option>
          <option value="HIDDEN">숨김</option>
          <option value="DELETED">삭제</option>
        </select>
      </label>
      <label>
        검색
        <input v-model.trim="filters.keyword" type="search" placeholder="작성자, 이메일, 에피소드" @keyup.enter="loadReviews" />
      </label>
      <button type="button" @click="loadReviews">검색</button>
    </section>

    <p v-if="message" class="message" :class="messageType">{{ message }}</p>

    <section v-if="loading" class="empty">리뷰를 불러오는 중입니다.</section>
    <section v-else-if="reviews.length === 0" class="empty">조건에 맞는 리뷰가 없습니다.</section>
    <section v-else class="review-grid">
      <article v-for="review in reviews" :key="review.id" class="review-card" :class="review.status.toLowerCase()">
        <div class="card-head">
          <div>
            <strong>{{ review.authorNickname || '요원' }}</strong>
            <span>{{ review.episodeTitle }}</span>
          </div>
          <em>{{ review.status }}</em>
        </div>
        <div class="ratings">
          <span>별점 {{ review.rating }}</span>
          <span>난이도 {{ review.difficultyRating }}</span>
          <span v-if="review.spoiler">스포일러</span>
        </div>
        <p>{{ review.content }}</p>
        <div class="actions">
          <button v-if="review.status === 'VISIBLE'" type="button" @click="hideReview(review)">숨김</button>
          <button v-if="review.status === 'HIDDEN'" type="button" @click="restoreReview(review)">복구</button>
          <button v-if="review.status !== 'DELETED'" type="button" class="danger" @click="deleteReview(review)">삭제</button>
        </div>
      </article>
    </section>
  </main>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { adminReviewApi } from '@/api/adminReviewApi';

const router = useRouter();
const reviews = ref([]);
const loading = ref(true);
const message = ref('');
const messageType = ref('success');
const filters = reactive({ status: 'ALL', keyword: '' });

onMounted(loadReviews);

async function loadReviews() {
  loading.value = true;
  try {
    reviews.value = await adminReviewApi.getReviews({ status: filters.status, keyword: filters.keyword });
  } catch (error) {
    setMessage(error.userMessage || '관리자 리뷰 목록을 불러올 수 없습니다.', 'error');
  } finally {
    loading.value = false;
  }
}

async function hideReview(review) {
  if (!confirm('이 리뷰를 숨김 처리할까요?')) return;
  try {
    await adminReviewApi.hideReview(review.id);
    setMessage('리뷰가 숨김 처리되었습니다.');
    await loadReviews();
  } catch (error) {
    setMessage(error.userMessage || '리뷰를 숨김 처리할 수 없습니다.', 'error');
  }
}

async function restoreReview(review) {
  if (!confirm('이 리뷰를 복구할까요?')) return;
  try {
    await adminReviewApi.restoreReview(review.id);
    setMessage('리뷰가 복구되었습니다.');
    await loadReviews();
  } catch (error) {
    setMessage(error.userMessage || '리뷰를 복구할 수 없습니다.', 'error');
  }
}

async function deleteReview(review) {
  if (!confirm('이 리뷰를 삭제 처리할까요?')) return;
  try {
    await adminReviewApi.deleteReview(review.id);
    setMessage('리뷰가 삭제 처리되었습니다.');
    await loadReviews();
  } catch (error) {
    setMessage(error.userMessage || '리뷰를 삭제할 수 없습니다.', 'error');
  }
}

function setMessage(text, type = 'success') {
  message.value = text;
  messageType.value = type;
}
</script>

<style scoped>
.admin-review-page { min-height: 100vh; box-sizing: border-box; padding: 24px 16px 70px; background: linear-gradient(155deg, #0f172a, #111827 56%, #0b0b0b); color: #f8fafc; font-family: 'Noto Sans KR', Georgia, serif; }
.admin-hero { width: min(100%, 980px); margin: 0 auto 16px; display: flex; align-items: flex-end; justify-content: space-between; gap: 12px; padding: 20px; border: 1px solid rgba(148,163,184,.2); border-radius: 20px; background: rgba(15,23,42,.72); }
.admin-hero p { margin: 0 0 6px; color: #f59e0b; font-size: .75rem; font-weight: 900; letter-spacing: .14em; }
.admin-hero h1 { margin: 0; font-size: clamp(1.8rem, 8vw, 3.2rem); }
.admin-hero span { display: block; margin-top: 8px; color: #cbd5e1; }
button { min-height: 42px; border: 0; border-radius: 12px; background: #b45309; color: white; font: inherit; font-weight: 900; padding: 0 14px; }
.filters { width: min(100%, 980px); margin: 0 auto 14px; display: grid; grid-template-columns: 130px 1fr auto; gap: 10px; padding: 14px; border: 1px solid rgba(148,163,184,.18); border-radius: 16px; background: rgba(248,250,252,.06); }
.filters label { display: grid; gap: 6px; color: #cbd5e1; font-size: .82rem; font-weight: 800; }
select, input { min-height: 42px; box-sizing: border-box; width: 100%; border: 1px solid rgba(148,163,184,.3); border-radius: 12px; background: rgba(15,23,42,.8); color: #f8fafc; padding: 0 12px; font: inherit; }
.message { width: min(100%, 980px); margin: 0 auto 14px; padding: 12px 14px; border-radius: 14px; background: rgba(22,163,74,.14); color: #bbf7d0; }
.message.error { background: rgba(220,38,38,.16); color: #fecaca; }
.empty { width: min(100%, 980px); margin: 0 auto; padding: 22px; border: 1px dashed rgba(148,163,184,.28); border-radius: 16px; color: #cbd5e1; text-align: center; }
.review-grid { width: min(100%, 980px); margin: 0 auto; display: grid; gap: 12px; }
.review-card { padding: 18px; border: 1px solid rgba(248,250,252,.12); border-radius: 18px; background: rgba(15,23,42,.7); box-shadow: 0 18px 40px rgba(0,0,0,.18); }
.review-card.hidden { opacity: .76; border-color: rgba(245,158,11,.34); }
.review-card.deleted { opacity: .58; border-color: rgba(248,113,113,.28); }
.card-head { display: flex; justify-content: space-between; gap: 10px; }
.card-head strong { display: block; font-size: 1.05rem; }
.card-head span { display: block; margin-top: 4px; color: #cbd5e1; font-size: .9rem; }
em { font-style: normal; color: #fde68a; font-weight: 900; }
.ratings { display: flex; flex-wrap: wrap; gap: 7px; margin: 12px 0; }
.ratings span { border: 1px solid rgba(245,158,11,.25); border-radius: 999px; padding: 5px 9px; color: #fde68a; font-size: .78rem; }
.review-card p { color: #e5e7eb; line-height: 1.6; white-space: pre-wrap; }
.actions { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 14px; }
.actions .danger { background: #b91c1c; }
@media (max-width: 560px) {
  .admin-hero { align-items: stretch; flex-direction: column; }
  .filters { grid-template-columns: 1fr; }
}
</style>
