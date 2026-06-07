<template>
  <main class="community-page">
    <header class="hero">
      <button type="button" class="ghost" @click="router.push({ name: 'RegionMap' })">권역 지도로</button>
      <p>REGION COMMUNITY</p>
      <h1>{{ regionName }} 커뮤니티</h1>
      <span>클리어 리뷰와 현장 질문을 한 곳에서 확인합니다.</span>
      <div class="hero-actions">
        <button type="button" @click="router.push({ name: 'EpisodeList', query: { areaCode } })">이 권역 사건 보기</button>
        <button type="button" @click="reload">새로고침</button>
      </div>
    </header>

    <p v-if="message" class="toast" :class="messageType">{{ message }}</p>

    <section class="layout">
      <article class="panel review-panel">
        <div class="panel-head">
          <div>
            <p>REVIEWS</p>
            <h2>클리어 리뷰</h2>
          </div>
          <span>{{ reviewState.reviewCount || 0 }}개 · 평균 {{ averageRating }}</span>
        </div>

        <form class="stack-form" @submit.prevent="submitReview">
          <label>별점
            <select v-model.number="reviewForm.rating">
              <option v-for="score in [5,4,3,2,1]" :key="score" :value="score">{{ score }}점</option>
            </select>
          </label>
          <label>리뷰
            <textarea v-model.trim="reviewForm.content" rows="4" placeholder="권역 동선, 난이도, 분위기를 남겨 주세요."></textarea>
          </label>
          <button type="submit">{{ reviewState.myReviewId ? '내 리뷰 수정' : '리뷰 등록' }}</button>
          <small v-if="!reviewState.canReview">클리어 기록이 있어야 리뷰를 작성할 수 있습니다.</small>
        </form>

        <div class="feed">
          <article v-for="review in reviewState.reviews || []" :key="review.id" class="feed-card">
            <div class="feed-head">
              <strong>{{ review.authorNickname || '요원' }}</strong>
              <span>별점 {{ review.rating }} · 좋아요 {{ review.likeCount || 0 }}</span>
            </div>
            <p>{{ review.content }}</p>
            <div class="actions">
              <button type="button" @click="toggleReviewLike(review)">좋아요</button>
              <button v-if="review.mine" type="button" class="danger" @click="deleteReview(review)">삭제</button>
            </div>
          </article>
          <p v-if="!(reviewState.reviews || []).length" class="empty">아직 리뷰가 없습니다.</p>
        </div>
      </article>

      <article class="panel">
        <div class="panel-head">
          <div>
            <p>Q&A</p>
            <h2>현장 질문</h2>
          </div>
          <span>{{ questions.length }}개</span>
        </div>

        <form class="stack-form" @submit.prevent="submitQuestion">
          <label>제목
            <input v-model.trim="questionForm.title" type="text" placeholder="예: 이 권역은 야간 이동이 괜찮나요?" />
          </label>
          <label>내용
            <textarea v-model.trim="questionForm.content" rows="3" placeholder="스포일러 없이 질문을 남겨 주세요."></textarea>
          </label>
          <button type="submit">질문 등록</button>
        </form>

        <div class="feed">
          <article v-for="question in questions" :key="question.id" class="feed-card question-card">
            <div class="feed-head">
              <strong>{{ question.title }}</strong>
              <span>{{ question.authorNickname || '요원' }} · 좋아요 {{ question.likeCount || 0 }}</span>
            </div>
            <p>{{ question.content }}</p>
            <div class="actions">
              <button type="button" @click="toggleQuestionLike(question)">좋아요</button>
              <button v-if="question.mine" type="button" class="danger" @click="deleteQuestion(question)">삭제</button>
            </div>

            <div class="answers">
              <p v-for="answer in question.answers || []" :key="answer.id">
                <b>{{ answer.authorNickname || '요원' }}</b> {{ answer.content }}
                <button v-if="answer.mine" type="button" @click="deleteAnswer(question, answer)">삭제</button>
              </p>
              <form class="answer-form" @submit.prevent="submitAnswer(question)">
                <input v-model.trim="answerDrafts[question.id]" type="text" placeholder="답변 입력" />
                <button type="submit">답변</button>
              </form>
            </div>
          </article>
          <p v-if="!questions.length" class="empty">아직 질문이 없습니다.</p>
        </div>
      </article>
    </section>
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { regionCommunityApi } from '@/api/regionCommunityApi';
import { regionAreas, regionLabel } from '@/constants/regionAreas';

const route = useRoute();
const router = useRouter();

const regionId = computed(() => Number(route.params.regionId));
const areaCode = computed(() => route.query.areaCode || regionAreas.find((area) => area.regionId === regionId.value)?.code || 'seoul');
const regionName = computed(() => regionLabel(areaCode.value || regionId.value));
const averageRating = computed(() => Number(reviewState.value.averageRating || 0).toFixed(1));

const reviewState = ref({ reviews: [], reviewCount: 0, averageRating: 0, canReview: false, myReviewId: null });
const questions = ref([]);
const answerDrafts = reactive({});
const message = ref('');
const messageType = ref('success');
const reviewForm = reactive({ rating: 5, content: '' });
const questionForm = reactive({ title: '', content: '' });

onMounted(reload);

async function reload() {
  await Promise.all([loadReviews(), loadQuestions()]);
}

async function loadReviews() {
  try {
    reviewState.value = await regionCommunityApi.getReviews(regionId.value);
    const mine = (reviewState.value.reviews || []).find((review) => review.mine);
    if (mine) {
      reviewForm.rating = mine.rating || 5;
      reviewForm.content = mine.content || '';
    }
  } catch (error) {
    setMessage(error.userMessage || '권역 리뷰를 불러오지 못했습니다.', 'error');
  }
}

async function loadQuestions() {
  try {
    questions.value = await regionCommunityApi.getQuestions(regionId.value);
  } catch (error) {
    setMessage(error.userMessage || '권역 질문을 불러오지 못했습니다.', 'error');
  }
}

async function submitReview() {
  if (!reviewForm.content || reviewForm.content.length < 5) {
    setMessage('리뷰는 5자 이상 입력해 주세요.', 'error');
    return;
  }
  try {
    if (reviewState.value.myReviewId) {
      await regionCommunityApi.updateReview(regionId.value, reviewState.value.myReviewId, reviewForm);
    } else {
      await regionCommunityApi.createReview(regionId.value, reviewForm);
    }
    setMessage('리뷰가 저장되었습니다.');
    await loadReviews();
  } catch (error) {
    setMessage(error.userMessage || '리뷰를 저장하지 못했습니다.', 'error');
  }
}

async function deleteReview(review) {
  if (!confirm('이 리뷰를 삭제할까요?')) return;
  try {
    await regionCommunityApi.deleteReview(regionId.value, review.id);
    reviewForm.content = '';
    setMessage('리뷰가 삭제되었습니다.');
    await loadReviews();
  } catch (error) {
    setMessage(error.userMessage || '리뷰를 삭제하지 못했습니다.', 'error');
  }
}

async function toggleReviewLike(review) {
  try {
    await regionCommunityApi.toggleReviewLike(regionId.value, review.id);
    await loadReviews();
  } catch (error) {
    setMessage(error.userMessage || '좋아요를 처리하지 못했습니다.', 'error');
  }
}

async function submitQuestion() {
  if (!questionForm.title || !questionForm.content) {
    setMessage('질문 제목과 내용을 입력해 주세요.', 'error');
    return;
  }
  try {
    await regionCommunityApi.createQuestion(regionId.value, questionForm);
    questionForm.title = '';
    questionForm.content = '';
    setMessage('질문이 등록되었습니다.');
    await loadQuestions();
  } catch (error) {
    setMessage(error.userMessage || '질문을 등록하지 못했습니다.', 'error');
  }
}

async function deleteQuestion(question) {
  if (!confirm('이 질문을 삭제할까요?')) return;
  try {
    await regionCommunityApi.deleteQuestion(regionId.value, question.id);
    setMessage('질문이 삭제되었습니다.');
    await loadQuestions();
  } catch (error) {
    setMessage(error.userMessage || '질문을 삭제하지 못했습니다.', 'error');
  }
}

async function toggleQuestionLike(question) {
  try {
    await regionCommunityApi.toggleQuestionLike(regionId.value, question.id);
    await loadQuestions();
  } catch (error) {
    setMessage(error.userMessage || '좋아요를 처리하지 못했습니다.', 'error');
  }
}

async function submitAnswer(question) {
  const content = answerDrafts[question.id];
  if (!content) return;
  try {
    await regionCommunityApi.createAnswer(regionId.value, question.id, { content });
    answerDrafts[question.id] = '';
    await loadQuestions();
  } catch (error) {
    setMessage(error.userMessage || '답변을 등록하지 못했습니다.', 'error');
  }
}

async function deleteAnswer(question, answer) {
  try {
    await regionCommunityApi.deleteAnswer(regionId.value, question.id, answer.id);
    await loadQuestions();
  } catch (error) {
    setMessage(error.userMessage || '답변을 삭제하지 못했습니다.', 'error');
  }
}

function setMessage(text, type = 'success') {
  message.value = text;
  messageType.value = type;
}
</script>

<style scoped>
.community-page { min-height: 100vh; box-sizing: border-box; padding: 24px 16px 70px; background: radial-gradient(circle at 12% 10%, rgba(34,197,94,.22), transparent 30%), linear-gradient(150deg, #082f49, #111827 58%, #030712); color: #f8fafc; font-family: 'Noto Sans KR', Georgia, serif; }
.hero, .layout, .toast { width: min(100%, 1180px); margin-left: auto; margin-right: auto; }
.hero { margin-bottom: 16px; padding: 22px; border: 1px solid rgba(125,211,252,.22); border-radius: 24px; background: rgba(15,23,42,.72); }
.ghost { background: rgba(15,23,42,.8); border: 1px solid rgba(148,163,184,.28); color: #cbd5e1; }
.hero p, .panel-head p { margin: 12px 0 8px; color: #67e8f9; font-size: .74rem; font-weight: 900; letter-spacing: .15em; }
h1 { margin: 0; font-size: clamp(2.2rem, 7vw, 4.8rem); line-height: .95; }
.hero span { display: block; margin-top: 10px; color: #cbd5e1; }
.hero-actions { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 16px; }
button { min-height: 40px; border: 0; border-radius: 12px; background: #0e7490; color: #fff; font: inherit; font-weight: 900; padding: 0 14px; cursor: pointer; }
.layout { display: grid; grid-template-columns: minmax(0, .9fr) minmax(0, 1.1fr); gap: 16px; }
.panel { padding: 18px; border: 1px solid rgba(148,163,184,.18); border-radius: 24px; background: rgba(15,23,42,.72); box-shadow: 0 28px 70px rgba(0,0,0,.25); }
.panel-head { display: flex; align-items: end; justify-content: space-between; gap: 12px; }
.panel-head h2 { margin: 0; font-size: 1.5rem; }
.panel-head span { color: #fde68a; font-weight: 900; }
.stack-form { display: grid; gap: 10px; margin: 16px 0; padding: 14px; border-radius: 18px; background: rgba(2,6,23,.36); }
label { display: grid; gap: 6px; color: #cbd5e1; font-weight: 800; }
input, textarea, select { width: 100%; box-sizing: border-box; border: 1px solid rgba(148,163,184,.24); border-radius: 12px; background: rgba(15,23,42,.9); color: #f8fafc; font: inherit; padding: 10px 12px; }
small { color: #fbbf24; }
.feed { display: grid; gap: 10px; }
.feed-card { padding: 14px; border: 1px solid rgba(148,163,184,.16); border-radius: 18px; background: rgba(2,6,23,.42); }
.feed-head { display: flex; justify-content: space-between; gap: 12px; color: #e0f2fe; }
.feed-head span { color: #94a3b8; font-size: .82rem; }
.feed-card p { color: #e5e7eb; line-height: 1.6; white-space: pre-wrap; }
.actions, .answer-form { display: flex; flex-wrap: wrap; gap: 8px; }
.danger { background: #7f1d1d; }
.answers { margin-top: 12px; padding-top: 12px; border-top: 1px solid rgba(148,163,184,.16); }
.answers p { margin: 0 0 8px; padding: 8px 10px; border-radius: 10px; background: rgba(15,23,42,.7); }
.answers b { color: #fde68a; }
.answer-form { display: grid; grid-template-columns: 1fr auto; }
.toast { margin-bottom: 12px; padding: 12px 14px; border-radius: 14px; background: rgba(22,101,52,.22); color: #bbf7d0; }
.toast.error { background: rgba(127,29,29,.24); color: #fecaca; }
.empty { color: #94a3b8; text-align: center; }
@media (max-width: 860px) {
  .layout { grid-template-columns: 1fr; }
  .panel-head, .feed-head { display: block; }
}
@media (max-width: 460px) {
  .hero-actions, .answer-form { grid-template-columns: 1fr; display: grid; }
}
</style>
