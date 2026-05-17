<template>
  <div class="detail-page">
    <header class="detail-header">
      <button class="ghost-btn" type="button" @click="goBack">← 목록</button>
      <div>
        <p class="eyebrow">OPERATION DETAIL</p>
        <h1>{{ region?.name || '작전 정보 로딩 중' }}</h1>
        <p>{{ region?.description || '작전 데이터를 불러오고 있습니다.' }}</p>
      </div>
      <button class="primary-btn" type="button" @click="startMission">작전 시작</button>
    </header>

    <main class="detail-layout">
      <section class="overview-panel">
        <div class="metric-grid">
          <div>
            <span>평균 평점</span>
            <strong>{{ formatRating(reviewState.averageRating) }}</strong>
          </div>
          <div>
            <span>리뷰</span>
            <strong>{{ reviewState.reviewCount }}개</strong>
          </div>
          <div>
            <span>미션</span>
            <strong>{{ missions.length }}개</strong>
          </div>
        </div>

        <div class="mission-brief">
          <h2>진입 전 확인</h2>
          <p>리뷰와 문의를 확인한 뒤 작전을 시작할 수 있습니다. 리뷰 작성은 최종 미션을 클리어한 사용자에게만 열립니다.</p>
          <button class="primary-btn wide" type="button" @click="startMission">브리핑으로 이동</button>
          <button v-if="finalMissionId" class="ghost-btn wide" type="button" @click="openClearReport">클리어 기록 보기</button>
        </div>
      </section>

      <section class="review-panel">
        <div class="panel-head">
          <div>
            <p class="eyebrow">REVIEWS</p>
            <h2>평점과 후기</h2>
          </div>
          <div class="review-controls">
            <select v-model="reviewSort" @change="fetchReviews">
              <option value="latest">최신순</option>
              <option value="rating_desc">평점 높은순</option>
              <option value="rating_asc">평점 낮은순</option>
              <option value="clear_time">클리어 빠른순</option>
            </select>
            <select v-model="reviewRatingFilter">
              <option value="all">전체</option>
              <option value="5">5점</option>
              <option value="4">4점 이상</option>
              <option value="3">3점 이상</option>
            </select>
          </div>
        </div>

        <form class="review-form" @submit.prevent="submitReview">
          <label>
            <span>평점</span>
            <select v-model.number="reviewForm.rating" :disabled="!reviewState.canReview">
              <option v-for="rating in [5, 4, 3, 2, 1]" :key="rating" :value="rating">{{ rating }}점</option>
            </select>
          </label>
          <label>
            <span>리뷰</span>
            <textarea v-model.trim="reviewForm.content" rows="3" :disabled="!reviewState.canReview" placeholder="클리어 후 느낀 난이도와 이동 동선을 남겨주세요."></textarea>
          </label>
          <button class="primary-btn" type="submit" :disabled="!reviewState.canReview || isReviewSaving">
            {{ reviewState.myReviewId ? '내 리뷰 수정' : '리뷰 등록' }}
          </button>
          <p v-if="!reviewState.canReview" class="muted">최종 미션을 클리어한 뒤 리뷰를 작성할 수 있습니다.</p>
        </form>

        <div v-if="filteredReviews.length === 0" class="empty-box">조건에 맞는 리뷰가 없습니다.</div>
        <article v-for="review in filteredReviews" :key="review.id" class="review-item">
          <div class="item-head">
            <strong>{{ review.authorNickname || '요원' }}</strong>
            <span>★ {{ review.rating }} · {{ formatElapsed(review.clearElapsedSeconds) }}</span>
          </div>
          <p>{{ review.content }}</p>
          <div v-if="review.mine" class="item-actions">
            <button type="button" class="ghost-btn small" @click="editReview(review)">수정</button>
            <button type="button" class="danger-btn small" @click="deleteReview(review.id)">삭제</button>
          </div>
        </article>
      </section>

      <section class="community-panel">
        <div class="panel-head">
          <div>
            <p class="eyebrow">COMMUNITY</p>
            <h2>문의와 답변</h2>
          </div>
        </div>

        <form class="question-form" @submit.prevent="submitQuestion">
          <input v-model.trim="questionForm.title" type="text" placeholder="문의 제목" />
          <textarea v-model.trim="questionForm.content" rows="3" placeholder="작전 동선, 힌트, 오류 등에 대해 질문하세요."></textarea>
          <button class="primary-btn" type="submit" :disabled="isQuestionSaving">문의 등록</button>
        </form>

        <div v-if="questions.length === 0" class="empty-box">아직 문의가 없습니다.</div>
        <article v-for="question in questions" :key="question.id" class="question-item">
          <div class="item-head">
            <strong>{{ question.title }}</strong>
            <span>{{ question.authorNickname || '요원' }}</span>
          </div>
          <p>{{ question.content }}</p>
          <div v-if="question.mine" class="item-actions">
            <button type="button" class="danger-btn small" @click="deleteQuestion(question.id)">문의 삭제</button>
          </div>

          <div class="answer-list">
            <div v-for="answer in question.answers || []" :key="answer.id" class="answer-item">
              <strong>{{ answer.authorNickname || '요원' }}</strong>
              <p>{{ answer.content }}</p>
              <button v-if="answer.mine" type="button" class="danger-btn small" @click="deleteAnswer(question.id, answer.id)">삭제</button>
            </div>
          </div>

          <form class="answer-form" @submit.prevent="submitAnswer(question)">
            <input v-model.trim="answerDrafts[question.id]" type="text" placeholder="답변 작성" />
            <button class="ghost-btn small" type="submit">답변</button>
          </form>
        </article>
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import apiClient from '@/api/axiosInstance';
import { useSessionStore } from '@/stores/sessionStore';

const route = useRoute();
const router = useRouter();
const sessionStore = useSessionStore();
const regionId = route.params.regionId;

const region = ref(null);
const missions = ref([]);
const reviewSort = ref('latest');
const reviewRatingFilter = ref('all');
const reviewState = ref({ reviews: [], averageRating: 0, reviewCount: 0, canReview: false, myReviewId: null });
const reviewForm = ref({ rating: 5, content: '' });
const questions = ref([]);
const questionForm = ref({ title: '', content: '' });
const answerDrafts = ref({});
const isReviewSaving = ref(false);
const isQuestionSaving = ref(false);

const finalMissionId = computed(() => missions.value.find(mission => mission.missionType === 'FINAL' || mission.isFinal === true || mission.final === true)?.id || null);
const filteredReviews = computed(() => {
  if (reviewRatingFilter.value === 'all') return reviewState.value.reviews;
  const minimum = Number(reviewRatingFilter.value);
  return reviewState.value.reviews.filter(review => Number(review.rating || 0) >= minimum);
});

onMounted(async () => {
  await Promise.all([fetchRegion(), fetchMissions(), fetchReviews(), fetchQuestions()]);
});

const fetchRegion = async () => {
  const response = await apiClient.get(`/v1/regions/${regionId}`);
  region.value = response.data;
};

const fetchMissions = async () => {
  const response = await apiClient.get(`/v1/regions/${regionId}/missions`, {
    params: { userId: sessionStore.userId || 1 }
  });
  missions.value = response.data || [];
};

const fetchReviews = async () => {
  const response = await apiClient.get(`/v1/regions/${regionId}/reviews`, {
    params: { sort: reviewSort.value }
  });
  reviewState.value = response.data;
  const mine = (response.data.reviews || []).find(review => review.mine);
  if (mine) {
    reviewForm.value = { rating: mine.rating, content: mine.content };
  }
};

const fetchQuestions = async () => {
  const response = await apiClient.get(`/v1/regions/${regionId}/questions`);
  questions.value = response.data || [];
};

const submitReview = async () => {
  if (!reviewForm.value.content) return;
  isReviewSaving.value = true;
  try {
    if (reviewState.value.myReviewId) {
      await apiClient.put(`/v1/regions/${regionId}/reviews/${reviewState.value.myReviewId}`, reviewForm.value);
    } else {
      await apiClient.post(`/v1/regions/${regionId}/reviews`, reviewForm.value);
    }
    await fetchReviews();
  } catch (error) {
    alert(error.userMessage || '리뷰 저장에 실패했습니다.');
  } finally {
    isReviewSaving.value = false;
  }
};

const editReview = (review) => {
  reviewForm.value = { rating: review.rating, content: review.content };
};

const deleteReview = async (reviewId) => {
  if (!confirm('리뷰를 삭제할까요?')) return;
  await apiClient.delete(`/v1/regions/${regionId}/reviews/${reviewId}`);
  reviewForm.value = { rating: 5, content: '' };
  await fetchReviews();
};

const submitQuestion = async () => {
  if (!questionForm.value.title || !questionForm.value.content) return;
  isQuestionSaving.value = true;
  try {
    await apiClient.post(`/v1/regions/${regionId}/questions`, questionForm.value);
    questionForm.value = { title: '', content: '' };
    await fetchQuestions();
  } catch (error) {
    alert(error.userMessage || '문의 등록에 실패했습니다.');
  } finally {
    isQuestionSaving.value = false;
  }
};

const deleteQuestion = async (questionId) => {
  if (!confirm('문의글을 삭제할까요?')) return;
  await apiClient.delete(`/v1/regions/${regionId}/questions/${questionId}`);
  await fetchQuestions();
};

const submitAnswer = async (question) => {
  const content = answerDrafts.value[question.id];
  if (!content) return;
  await apiClient.post(`/v1/regions/${regionId}/questions/${question.id}/answers`, { content });
  answerDrafts.value[question.id] = '';
  await fetchQuestions();
};

const deleteAnswer = async (questionId, answerId) => {
  await apiClient.delete(`/v1/regions/${regionId}/questions/${questionId}/answers/${answerId}`);
  await fetchQuestions();
};

const startMission = () => {
  router.push({ name: 'Briefing', query: { regionId } });
};

const openClearReport = () => {
  router.push({ name: 'Clear', params: { missionId: finalMissionId.value }, query: { regionId } });
};

const goBack = () => {
  router.push({ name: 'Home', query: { area: route.query.area || 'seoul' } });
};

const formatRating = (value) => {
  const rating = Number(value || 0);
  return rating > 0 ? `${rating.toFixed(1)} / 5` : '-';
};

const formatElapsed = (seconds) => {
  if (!seconds) return '클리어 기록 없음';
  const minutes = Math.floor(Number(seconds) / 60);
  const remain = Number(seconds) % 60;
  return `${minutes}m ${String(remain).padStart(2, '0')}s`;
};
</script>

<style scoped>
.detail-page { min-height: 100vh; box-sizing: border-box; padding: 34px 22px 56px; background: #0b0f19; color: #e2e8f0; font-family: 'Noto Sans KR', sans-serif; }
.detail-header { width: min(1120px, 100%); margin: 0 auto 24px; display: grid; grid-template-columns: auto minmax(0, 1fr) auto; gap: 18px; align-items: start; padding-bottom: 20px; border-bottom: 1px solid rgba(148, 163, 184, 0.18); }
.detail-header h1 { margin: 0 0 8px; color: #fff; font-size: clamp(1.5rem, 3vw, 2.3rem); line-height: 1.2; }
.detail-header p { margin: 0; color: #94a3b8; line-height: 1.6; }
.eyebrow { margin: 0 0 8px; color: #67e8f9; font-size: 0.72rem; font-weight: 900; letter-spacing: 0; }
.detail-layout { width: min(1120px, 100%); margin: 0 auto; display: grid; grid-template-columns: 0.72fr 1.28fr; gap: 18px; }
.overview-panel, .review-panel, .community-panel { border: 1px solid rgba(148, 163, 184, 0.18); border-radius: 8px; background: rgba(15, 23, 42, 0.64); padding: 18px; }
.overview-panel { align-self: start; }
.review-panel, .community-panel { min-width: 0; }
.community-panel { grid-column: 2; }
.metric-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; margin-bottom: 16px; }
.metric-grid div { min-width: 0; padding: 12px; border: 1px solid rgba(103, 232, 249, 0.18); border-radius: 6px; background: rgba(8, 47, 73, 0.34); }
.metric-grid span { display: block; margin-bottom: 6px; color: #94a3b8; font-size: 0.72rem; font-weight: 800; }
.metric-grid strong { color: #f8fafc; font-size: 1rem; }
.mission-brief h2, .panel-head h2 { margin: 0; color: #fff; font-size: 1.18rem; }
.mission-brief p { color: #94a3b8; line-height: 1.6; }
.panel-head { display: flex; justify-content: space-between; gap: 12px; align-items: end; margin-bottom: 14px; }
.review-controls { display: flex; gap: 8px; }
select, input, textarea { box-sizing: border-box; width: 100%; border: 1px solid rgba(148, 163, 184, 0.28); border-radius: 6px; background: rgba(2, 6, 23, 0.68); color: #f8fafc; font: inherit; padding: 9px 10px; }
textarea { resize: vertical; min-height: 84px; }
.review-form, .question-form { display: grid; gap: 10px; margin-bottom: 16px; }
.review-form { grid-template-columns: 120px minmax(0, 1fr) auto; align-items: end; }
.review-form label { display: grid; gap: 6px; }
.review-form span { color: #94a3b8; font-size: 0.74rem; font-weight: 800; }
.primary-btn, .ghost-btn, .danger-btn { border-radius: 6px; font: inherit; font-weight: 900; cursor: pointer; min-height: 38px; padding: 9px 13px; }
.primary-btn { border: 1px solid #06b6d4; background: #0891b2; color: #ecfeff; }
.primary-btn:disabled { opacity: 0.45; cursor: not-allowed; }
.ghost-btn { border: 1px solid rgba(148, 163, 184, 0.34); background: transparent; color: #cbd5e1; }
.danger-btn { border: 1px solid rgba(239, 68, 68, 0.48); background: rgba(127, 29, 29, 0.22); color: #fca5a5; }
.wide { width: 100%; margin-top: 8px; }
.small { min-height: 30px; padding: 6px 9px; font-size: 0.78rem; }
.muted, .empty-box { color: #94a3b8; font-size: 0.84rem; }
.empty-box { padding: 16px; border: 1px dashed rgba(148, 163, 184, 0.28); border-radius: 6px; text-align: center; }
.review-item, .question-item { padding: 14px; border: 1px solid rgba(148, 163, 184, 0.16); border-radius: 8px; background: rgba(2, 6, 23, 0.34); margin-top: 10px; }
.item-head { display: flex; justify-content: space-between; gap: 10px; margin-bottom: 8px; }
.item-head strong { min-width: 0; overflow: hidden; color: #f8fafc; text-overflow: ellipsis; white-space: nowrap; }
.item-head span { flex: 0 0 auto; color: #fcd34d; font-size: 0.82rem; font-weight: 800; }
.review-item p, .question-item p { margin: 0; color: #cbd5e1; line-height: 1.6; }
.item-actions { display: flex; gap: 8px; justify-content: flex-end; margin-top: 10px; }
.answer-list { display: grid; gap: 8px; margin: 12px 0; }
.answer-item { padding: 10px; border-left: 2px solid rgba(103, 232, 249, 0.48); background: rgba(8, 47, 73, 0.24); }
.answer-item strong { display: block; margin-bottom: 4px; color: #67e8f9; font-size: 0.82rem; }
.answer-form { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 8px; }
@media (max-width: 840px) {
  .detail-header, .detail-layout { grid-template-columns: 1fr; }
  .community-panel { grid-column: auto; }
  .review-form { grid-template-columns: 1fr; }
  .panel-head { align-items: stretch; flex-direction: column; }
  .review-controls { flex-direction: column; }
}
</style>
