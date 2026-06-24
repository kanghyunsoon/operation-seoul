<template>
  <section class="review-panel">
    <header>
      <div>
        <p>EPISODE REVIEWS</p>
        <h2>{{ title }}</h2>
      </div>
      <div class="review-summary">
        <strong>{{ reviewState.reviewCount || 0 }}개 리뷰</strong>
        <span>평균 별점 {{ averageRating }}점</span>
        <span>평균 난이도 {{ averageDifficultyRating }}점</span>
      </div>
    </header>

    <form v-if="canWriteReview" class="review-form" @submit.prevent="submitReview">
      <div class="rating-row">
        <label>별점</label>
        <div class="star-control" role="radiogroup" aria-label="별점">
          <button
            v-for="value in [1,2,3,4,5]"
            :key="`rating-${value}`"
            type="button"
            :class="{ filled: value <= form.rating }"
            :aria-label="`${value}점`"
            @click="form.rating = value"
          >
            {{ value <= form.rating ? '★' : '☆' }}
          </button>
        </div>

        <label>체감 난이도</label>
        <div class="star-control" role="radiogroup" aria-label="체감 난이도">
          <button
            v-for="value in [1,2,3,4,5]"
            :key="`difficulty-${value}`"
            type="button"
            :class="{ filled: value <= form.difficultyRating }"
            :aria-label="`${value}점`"
            @click="form.difficultyRating = value"
          >
            {{ value <= form.difficultyRating ? '★' : '☆' }}
          </button>
        </div>
      </div>

      <label class="spoiler-toggle">
        <input v-model="form.spoiler" type="checkbox" />
        <span></span>
        <strong>가려지는 리뷰로 등록</strong>
      </label>

      <textarea v-model.trim="form.content" rows="4" placeholder="현장 난이도, 동선, 사건 몰입감을 중심으로 리뷰를 남겨 주세요."></textarea>
      <button type="submit">{{ editingReviewId ? '리뷰 수정' : '리뷰 등록' }}</button>
      <button v-if="editingReviewId" type="button" class="ghost" @click="cancelEdit">수정 취소</button>
    </form>

    <p v-else-if="showComposer" class="review-note">
      {{ reviewState.message || '클리어한 에피소드에만 리뷰를 작성할 수 있습니다.' }}
    </p>
    <p v-if="message" class="message" :class="messageType">{{ message }}</p>

    <div class="review-list">
      <article v-for="review in reviewState.reviews || []" :key="review.id" class="review-card" :class="{ hidden: review.status === 'HIDDEN' }">
        <div class="review-head">
          <strong>{{ review.authorNickname || '플레이어' }}</strong>
          <span>
            <b v-for="value in [1,2,3,4,5]" :key="`review-${review.id}-${value}`">{{ value <= review.rating ? '★' : '☆' }}</b>
            · 난이도 {{ review.difficultyRating }}
          </span>
        </div>
        <button v-if="review.spoiler && !openedSpoilers.includes(review.id)" type="button" class="spoiler-cover" @click="openedSpoilers.push(review.id)">
          가려진 리뷰 보기
        </button>
        <p v-else>{{ review.content }}</p>
        <div v-if="showComposer && review.mine" class="review-actions">
          <button type="button" @click="editReview(review)">수정</button>
          <button type="button" class="danger" @click="deleteReview(review.id)">삭제</button>
        </div>

        <div class="comment-box">
          <button type="button" class="comment-toggle" @click="toggleComments(review.id)">
            댓글 {{ (review.comments || []).length }}개
          </button>
          <div v-if="openedComments.includes(review.id)" class="comments">
            <article v-for="comment in review.comments || []" :key="comment.id" class="comment-card">
              <div>
                <strong>{{ comment.authorNickname || '플레이어' }}</strong>
                <button
                  v-if="editingCommentId !== comment.id && comment.spoiler && !openedCommentSpoilers.includes(comment.id)"
                  type="button"
                  class="spoiler-cover comment-spoiler"
                  @click="openedCommentSpoilers.push(comment.id)"
                >
                  가려진 댓글 보기
                </button>
                <p v-else-if="editingCommentId !== comment.id">{{ comment.content }}</p>
                <form v-else class="comment-form" @submit.prevent="submitCommentEdit(comment.id)">
                  <input v-model.trim="commentEditContent" type="text" placeholder="댓글을 입력하세요." />
                  <label class="comment-spoiler-toggle">
                    <input v-model="commentEditSpoiler" type="checkbox" />
                    가림
                  </label>
                  <button type="submit">저장</button>
                  <button type="button" class="ghost" @click="cancelCommentEdit">취소</button>
                </form>
              </div>
              <div v-if="comment.mine" class="comment-actions">
                <button type="button" @click="startCommentEdit(comment)">수정</button>
                <button type="button" class="danger" @click="deleteComment(comment.id)">삭제</button>
              </div>
            </article>
            <p v-if="!(review.comments || []).length" class="empty">아직 댓글이 없습니다.</p>
            <form class="comment-form" @submit.prevent="submitComment(review.id)">
              <input v-model.trim="commentForms[review.id].content" type="text" placeholder="댓글을 입력하세요." />
              <label class="comment-spoiler-toggle">
                <input v-model="commentForms[review.id].spoiler" type="checkbox" />
                가림
              </label>
              <button type="submit">댓글 등록</button>
            </form>
          </div>
        </div>
      </article>
      <p v-if="!(reviewState.reviews || []).length" class="empty">아직 등록된 리뷰가 없습니다.</p>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { reviewApi } from '@/api/reviewApi';

const props = defineProps({
  episodeId: { type: [String, Number], required: true },
  title: { type: String, default: '클리어 리뷰' },
  showComposer: { type: Boolean, default: true }
});

const reviewState = ref({ reviews: [], reviewCount: 0, canReview: false, message: '' });
const form = ref({ rating: 5, difficultyRating: 3, content: '', spoiler: false });
const editingReviewId = ref(null);
const message = ref('');
const messageType = ref('success');
const openedSpoilers = ref([]);
const openedCommentSpoilers = ref([]);
const openedComments = ref([]);
const commentForms = reactive({});
const editingCommentId = ref(null);
const commentEditContent = ref('');
const commentEditSpoiler = ref(false);

const averageRating = computed(() => Number(reviewState.value.averageRating || 0).toFixed(1));
const averageDifficultyRating = computed(() => Number(reviewState.value.averageDifficultyRating || 0).toFixed(1));
const canWriteReview = computed(() => props.showComposer && (reviewState.value.canReview || editingReviewId.value));

onMounted(loadReviews);
defineExpose({ loadReviews });

async function loadReviews() {
  try {
    reviewState.value = await reviewApi.getEpisodeReviews(props.episodeId);
    (reviewState.value.reviews || []).forEach((review) => {
      if (!commentForms[review.id]) {
        commentForms[review.id] = { content: '', spoiler: false };
      }
    });
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
      setMessage('리뷰 내용은 5자 이상 입력해 주세요.', 'error');
      return;
    }
    if (editingReviewId.value) {
      await reviewApi.updateReview(editingReviewId.value, form.value);
      setMessage('리뷰가 수정되었습니다.');
    } else {
      await reviewApi.createEpisodeReview(props.episodeId, form.value);
      setMessage('리뷰가 등록되었습니다.');
    }
    cancelEdit();
    await loadReviews();
  } catch (error) {
    setMessage(error.userMessage || '리뷰를 등록할 수 없습니다.', 'error');
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
    setMessage('리뷰가 삭제되었습니다.');
    await loadReviews();
  } catch (error) {
    setMessage(error.userMessage || '리뷰를 삭제할 수 없습니다.', 'error');
  }
}

function toggleComments(reviewId) {
  if (openedComments.value.includes(reviewId)) {
    openedComments.value = openedComments.value.filter((id) => id !== reviewId);
  } else {
    openedComments.value.push(reviewId);
  }
}

async function submitComment(reviewId) {
  if (!commentForms[reviewId]) {
    commentForms[reviewId] = { content: '', spoiler: false };
  }
  const content = String(commentForms[reviewId].content || '').trim();
  if (content.length < 2) {
    setMessage('댓글 내용은 2자 이상 입력해 주세요.', 'error');
    return;
  }
  try {
    await reviewApi.createReviewComment(reviewId, { content, spoiler: commentForms[reviewId].spoiler === true });
    commentForms[reviewId] = { content: '', spoiler: false };
    setMessage('댓글이 등록되었습니다.');
    await loadReviews();
  } catch (error) {
    setMessage(error.userMessage || '댓글을 등록할 수 없습니다.', 'error');
  }
}

function startCommentEdit(comment) {
  editingCommentId.value = comment.id;
  commentEditContent.value = comment.content || '';
  commentEditSpoiler.value = comment.spoiler === true;
}

function cancelCommentEdit() {
  editingCommentId.value = null;
  commentEditContent.value = '';
  commentEditSpoiler.value = false;
}

async function submitCommentEdit(commentId) {
  const content = commentEditContent.value.trim();
  if (content.length < 2) {
    setMessage('댓글 내용은 2자 이상 입력해 주세요.', 'error');
    return;
  }
  try {
    await reviewApi.updateReviewComment(commentId, { content, spoiler: commentEditSpoiler.value === true });
    cancelCommentEdit();
    setMessage('댓글이 수정되었습니다.');
    await loadReviews();
  } catch (error) {
    setMessage(error.userMessage || '댓글을 수정할 수 없습니다.', 'error');
  }
}

async function deleteComment(commentId) {
  if (!confirm('댓글을 삭제하시겠습니까?')) return;
  try {
    await reviewApi.deleteReviewComment(commentId);
    setMessage('댓글이 삭제되었습니다.');
    await loadReviews();
  } catch (error) {
    setMessage(error.userMessage || '댓글을 삭제할 수 없습니다.', 'error');
  }
}

function setMessage(text, type = 'success') {
  message.value = text;
  messageType.value = type;
}
</script>

<style scoped>
.review-panel { margin-top: 20px; padding: 18px; border: 1px solid rgba(36,24,13,.18); border-radius: 18px; background: rgba(255,255,255,.58); color: #24180d; }
header { display: flex; justify-content: space-between; gap: 12px; align-items: flex-start; }
header p { margin: 0; color: #9a3412; font-size: .72rem; font-weight: 900; letter-spacing: .12em; }
h2 { margin: 3px 0 0; }
.review-summary { display: grid; gap: 2px; color: #78350f; font-size: .8rem; text-align: right; }
.review-summary strong { color: #24180d; }
.review-form { display: grid; gap: 12px; margin-top: 14px; }
.rating-row { display: grid; grid-template-columns: auto 1fr; gap: 8px 12px; align-items: center; }
label { color: #57534e; font-size: .82rem; font-weight: 800; }
.star-control { display: flex; gap: 2px; align-items: center; }
.star-control button { width: 32px; min-height: 32px; padding: 0; border: 0; border-radius: 8px; background: transparent; color: #a8a29e; font-size: 1.35rem; line-height: 1; }
.star-control button.filled { color: #d97706; }
.spoiler-toggle { display: flex; gap: 9px; align-items: center; width: fit-content; cursor: pointer; }
.spoiler-toggle input { position: absolute; opacity: 0; pointer-events: none; }
.spoiler-toggle span { position: relative; width: 44px; height: 24px; border-radius: 999px; background: #a8a29e; transition: background .16s ease; }
.spoiler-toggle span::after { content: ''; position: absolute; top: 3px; left: 3px; width: 18px; height: 18px; border-radius: 999px; background: #fff; transition: transform .16s ease; }
.spoiler-toggle input:checked + span { background: #92400e; }
.spoiler-toggle input:checked + span::after { transform: translateX(20px); }
.spoiler-toggle strong { font-size: .86rem; color: #57534e; }
textarea, input { box-sizing: border-box; width: 100%; border: 1px solid rgba(36,24,13,.18); border-radius: 10px; background: rgba(255,255,255,.78); padding: 10px; font: inherit; }
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
.review-head b { font-weight: 900; color: #d97706; }
.review-card p { line-height: 1.6; }
.spoiler-cover { width: 100%; border: 1px dashed rgba(36,24,13,.3); background: transparent; color: #78350f; }
.review-actions, .comment-actions { display: flex; gap: 8px; justify-content: flex-end; }
.review-actions button, .comment-actions button { min-height: 32px; padding: 0 10px; }
.comment-box { margin-top: 12px; border-top: 1px solid rgba(36,24,13,.12); padding-top: 10px; }
.comment-toggle { min-height: 34px; padding: 0 10px; background: rgba(36,24,13,.08); color: #78350f; }
.comments { display: grid; gap: 8px; margin-top: 10px; }
.comment-card { display: grid; grid-template-columns: 1fr auto; gap: 8px; padding: 10px; border-radius: 12px; background: rgba(255,251,235,.72); }
.comment-card strong { color: #78350f; font-size: .82rem; }
.comment-card p { margin: 4px 0 0; }
.comment-form { display: grid; grid-template-columns: 1fr auto auto auto; gap: 8px; align-items: center; }
.comment-form button { min-height: 38px; padding: 0 12px; }
.comment-spoiler { margin-top: 6px; min-height: 34px; }
.comment-spoiler-toggle { display: flex; align-items: center; gap: 5px; white-space: nowrap; color: #78350f; }
.comment-spoiler-toggle input { width: auto; }
@media (max-width: 520px) {
  header, .review-head, .comment-card, .comment-form { display: grid; grid-template-columns: 1fr; }
  .review-summary { text-align: left; }
}
@media (max-width: 370px) {
  .rating-row { grid-template-columns: 1fr; }
}
</style>
