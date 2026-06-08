<template>
  <section class="review-panel">
    <header>
      <div>
        <p>EPISODE REVIEWS</p>
        <h2>클리어 리뷰</h2>
      </div>
      <span>{{ reviewState.reviewCount || 0 }}개 · 평균 {{ averageRating }}점</span>
    </header>

    <form v-if="reviewState.canReview || editingReviewId" class="review-form" @submit.prevent="submitReview">
      <div class="rating-row">
        <label>별점
          <select v-model.number="form.rating">
            <option v-for="value in [5,4,3,2,1]" :key="value" :value="value">{{ value }}점</option>
          </select>
        </label>
        <label>체감 난이도
          <select v-model.number="form.difficultyRating">
            <option v-for="value in [5,4,3,2,1]" :key="value" :value="value">{{ value }}점</option>
          </select>
        </label>
      </div>
      <label class="spoiler"><input v-model="form.spoiler" type="checkbox" /> 스포일러 포함</label>
      <textarea v-model.trim="form.content" rows="4" placeholder="현장 난이도, 동선, 사건 몰입감을 중심으로 리뷰를 남겨 주세요."></textarea>
      <button type="submit">{{ editingReviewId ? '리뷰 수정' : '리뷰 등록' }}</button>
      <button v-if="editingReviewId" type="button" class="ghost" @click="cancelEdit">수정 취소</button>
    </form>

    <p v-else class="review-note">{{ reviewState.message || '클리어한 에피소드에만 리뷰를 작성할 수 있습니다.' }}</p>
    <p v-if="message" class="message" :class="messageType">{{ message }}</p>

    <div class="review-list">
      <article v-for="review in reviewState.reviews || []" :key="review.id" class="review-card" :class="{ hidden: review.status === 'HIDDEN' }">
        <div class="review-head">
          <strong>{{ review.authorNickname || '플레이어' }}</strong>
          <span>별점 {{ review.rating }} · 난이도 {{ review.difficultyRating }}</span>
        </div>
        <button v-if="review.spoiler && !openedSpoilers.includes(review.id)" type="button" class="spoiler-cover" @click="openedSpoilers.push(review.id)">
          스포일러 리뷰 보기
        </button>
        <p v-else>{{ review.content }}</p>
        <div v-if="review.mine" class="review-actions">
          <button type="button" @click="editReview(review)">수정</button>
          <button type="button" class="danger" @click="deleteReview(review.id)">삭제</button>
        </div>
      </article>
      <p v-if="!(reviewState.reviews || []).length" class="empty">아직 등록된 리뷰가 없습니다.</p>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { reviewApi } from '@/api/reviewApi';

const props = defineProps({ episodeId: { type: [String, Number], required: true } });
const reviewState = ref({ reviews: [], reviewCount: 0, canReview: false, message: '' });
const form = ref({ rating: 5, difficultyRating: 3, content: '', spoiler: false });
const editingReviewId = ref(null);
const message = ref('');
const messageType = ref('success');
const openedSpoilers = ref([]);

const averageRating = computed(() => Number(reviewState.value.averageRating || 0).toFixed(1));

onMounted(loadReviews);
defineExpose({ loadReviews });

async function loadReviews() {
  try {
    reviewState.value = await reviewApi.getEpisodeReviews(props.episodeId);
    const mine = (reviewState.value.reviews || []).find((review) => review.mine);
    if (mine && !editingReviewId.value) {
      reviewState.value.canReview = false;
      reviewState.value.message = '이미 이 에피소드에 리뷰를 작성했습니다. 내 리뷰를 수정하거나 삭제할 수 있습니다.';
    }
  } catch (error) {
    messageType.value = 'error';
    message.value = error.userMessage || '리뷰를 불러올 수 없습니다.';
  }
}

async function submitReview() {
  try {
    if (!form.value.content || form.value.content.length < 5) {
      messageType.value = 'error';
      message.value = '리뷰 내용은 5자 이상 입력해 주세요.';
      return;
    }
    if (editingReviewId.value) {
      await reviewApi.updateReview(editingReviewId.value, form.value);
      message.value = '리뷰가 수정되었습니다.';
    } else {
      await reviewApi.createEpisodeReview(props.episodeId, form.value);
      message.value = '리뷰가 등록되었습니다.';
    }
    messageType.value = 'success';
    cancelEdit();
    await loadReviews();
  } catch (error) {
    messageType.value = 'error';
    message.value = error.userMessage || '리뷰를 등록할 수 없습니다.';
  }
}

function editReview(review) {
  editingReviewId.value = review.id;
  form.value = {
    rating: review.rating,
    difficultyRating: review.difficultyRating,
    content: review.content,
    spoiler: review.spoiler === true
  };
  message.value = '';
}

function cancelEdit() {
  editingReviewId.value = null;
  form.value = { rating: 5, difficultyRating: 3, content: '', spoiler: false };
}

async function deleteReview(reviewId) {
  if (!confirm('리뷰를 삭제하시겠습니까?')) return;
  try {
    await reviewApi.deleteReview(reviewId);
    messageType.value = 'success';
    message.value = '리뷰가 삭제되었습니다.';
    await loadReviews();
  } catch (error) {
    messageType.value = 'error';
    message.value = error.userMessage || '리뷰를 삭제할 수 없습니다.';
  }
}
</script>

<style scoped>
.review-panel { margin-top: 20px; padding: 18px; border: 1px solid rgba(36,24,13,.18); border-radius: 18px; background: rgba(255,255,255,.58); }
header { display: flex; justify-content: space-between; gap: 12px; align-items: flex-start; }
header p { margin: 0; color: #9a3412; font-size: .72rem; font-weight: 900; letter-spacing: .12em; }
h2 { margin: 3px 0 0; }
header span { color: #78350f; font-weight: 900; font-size: .86rem; text-align: right; }
.review-form { display: grid; gap: 10px; margin-top: 14px; }
.rating-row { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
label { display: grid; gap: 5px; color: #57534e; font-size: .82rem; font-weight: 800; }
.spoiler { display: flex; gap: 7px; align-items: center; }
select, textarea { box-sizing: border-box; width: 100%; border: 1px solid rgba(36,24,13,.18); border-radius: 10px; background: rgba(255,255,255,.78); padding: 10px; font: inherit; }
textarea { resize: vertical; }
button { min-height: 40px; border: 0; border-radius: 10px; background: #24180d; color: #fff; font-weight: 900; }
.ghost { border: 1px solid rgba(36,24,13,.25); background: transparent; color: #24180d; }
.danger { background: #b91c1c; }
.review-note, .empty { color: #78716c; line-height: 1.5; }
.message { padding: 10px; border-radius: 10px; }
.message.success { background: rgba(22,101,52,.16); color: #166534; }
.message.error { background: rgba(127,29,29,.15); color: #991b1b; }
.review-list { display: grid; gap: 10px; margin-top: 14px; }
.review-card { padding: 12px; border: 1px solid rgba(36,24,13,.14); border-radius: 14px; background: rgba(255,255,255,.66); }
.review-card.hidden { opacity: .7; }
.review-head { display: flex; justify-content: space-between; gap: 8px; color: #24180d; }
.review-head span { color: #92400e; font-size: .82rem; }
.review-card p { line-height: 1.6; }
.spoiler-cover { width: 100%; border: 1px dashed rgba(36,24,13,.3); background: transparent; color: #78350f; }
.review-actions { display: flex; gap: 8px; justify-content: flex-end; }
.review-actions button { min-height: 32px; padding: 0 10px; }
@media (max-width: 370px) { header, .review-head { display: grid; } .rating-row { grid-template-columns: 1fr; } }
</style>
