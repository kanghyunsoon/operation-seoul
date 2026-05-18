<template>
  <div class="detail-page">
    <header class="detail-header">
      <button class="ghost-btn" type="button" @click="goBack">← 목록</button>
      <div>
        <p class="eyebrow">OPERATION DETAIL</p>
        <h1>{{ region?.name || '작전 정보 로딩 중' }}</h1>
        <p>작전 내용과 커뮤니티 기록을 분리해 확인합니다.</p>
      </div>
      <button class="primary-btn" type="button" @click="startMission">작전 시작</button>
    </header>

    <main class="detail-layout">
      <section class="content-panel board-panel">
        <div class="board-tabs" role="tablist" aria-label="작전 게시판">
          <button
            v-for="section in detailSections"
            :key="`tab-${section.key}`"
            type="button"
            :class="{ active: activeSection === section.key }"
            @click="setActiveSection(section.key)"
          >
            {{ section.title }}<span>{{ sectionCount(section.key) }}</span>
          </button>
        </div>

      <template v-if="activeSection === 'entry'">
        <div class="panel-head">
          <div>
            <p class="eyebrow">ENTRY CHECK</p>
            <h2>진입점 확인</h2>
          </div>
        </div>
        <article class="operation-content">
          <h3>작전 내용</h3>
          <p>{{ operationBriefText }}</p>
        </article>
        <div class="action-row">
          <button class="primary-btn" type="button" @click="startMission">작전 시작</button>
          <button class="ghost-btn" type="button" @click="activeSection = 'reviews'">평점과 후기 보기</button>
          <button class="ghost-btn" type="button" @click="activeSection = 'questions'">문의와 답변 보기</button>
        </div>
      </template>

      <template v-else-if="activeSection === 'reviews'">
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

        <div class="board-toolbar">
          <span>총 {{ searchedReviews.length }}개</span>
          <input
            v-model.trim="reviewSearch"
            type="search"
            placeholder="후기 내용 검색"
            @input="reviewPage = 1"
          />
        </div>

        <form class="review-form" @submit.prevent="submitReview">
          <label>
            <span>평점</span>
            <select v-model.number="reviewForm.rating" :disabled="!canWriteReview">
              <option v-for="rating in [5, 4, 3, 2, 1]" :key="rating" :value="rating">{{ rating }}점</option>
            </select>
          </label>
          <label>
            <span>리뷰</span>
            <textarea v-model.trim="reviewForm.content" rows="3" :disabled="!canWriteReview" placeholder="클리어 후 느낀 난이도와 이동 동선을 남겨주세요."></textarea>
          </label>
          <button class="primary-btn" type="submit" :disabled="!canWriteReview || isReviewSaving">
            {{ reviewState.myReviewId ? '내 리뷰 수정' : '리뷰 등록' }}
          </button>
          <p v-if="!canWriteReview" class="muted">최종 미션을 클리어한 뒤 리뷰를 작성할 수 있습니다.</p>
        </form>

        <div v-if="searchedReviews.length === 0" class="empty-box">조건에 맞는 리뷰가 없습니다.</div>
        <div v-else-if="!selectedReview" class="review-board" role="list">
          <div class="board-head">
            <span>분류</span>
            <span>제목</span>
            <span>작성자</span>
            <span>반응</span>
          </div>
          <div
            v-for="review in pagedReviews"
            :key="review.id"
            class="review-row"
            :class="{ selected: selectedReviewId === review.id }"
            tabindex="0"
            role="listitem"
            @click="openReviewDetail(review)"
            @keyup.enter="openReviewDetail(review)"
          >
            <span class="review-rating">★ {{ review.rating }}</span>
            <span class="review-title">{{ reviewSummaryText(review.content) }}</span>
            <span class="review-author">{{ review.authorNickname || '요원' }}</span>
            <span class="review-time review-reactions">
              <span>{{ formatElapsed(review.clearElapsedSeconds) }}</span>
              <button
                type="button"
                class="like-btn"
                :class="{ active: review.liked }"
                @click.stop="toggleReviewLike(review)"
              >
                ♥ {{ review.likeCount || 0 }}
              </button>
            </span>
          </div>
          <nav v-if="reviewTotalPages > 1" class="pagination" aria-label="후기 페이지">
            <button type="button" :disabled="reviewPage === 1" @click="reviewPage -= 1">‹</button>
            <button
              v-for="(page, index) in reviewPageButtons"
              :key="`review-${page}-${index}`"
              type="button"
              :class="{ active: reviewPage === page, dots: page === '...' }"
              :disabled="page === '...'"
              @click="reviewPage = page"
            >
              {{ page }}
            </button>
            <button type="button" :disabled="reviewPage === reviewTotalPages" @click="reviewPage += 1">›</button>
          </nav>
        </div>

        <aside v-else class="review-detail-panel">
          <button type="button" class="ghost-btn small" @click="closeReviewDetail">목록으로</button>
          <div class="item-head">
            <strong>{{ selectedReview.authorNickname || '요원' }}</strong>
            <span>★ {{ selectedReview.rating }} · {{ formatElapsed(selectedReview.clearElapsedSeconds) }}</span>
          </div>
          <p>{{ selectedReview.content }}</p>
          <div class="item-actions">
            <button
              type="button"
              class="ghost-btn small like-detail-btn"
              :class="{ active: selectedReview.liked }"
              @click="toggleReviewLike(selectedReview)"
            >
              ♥ 좋아요 {{ selectedReview.likeCount || 0 }}
            </button>
            <button v-if="selectedReview.mine" type="button" class="ghost-btn small" @click="editReview(selectedReview)">수정</button>
            <button v-if="selectedReview.mine" type="button" class="danger-btn small" @click="deleteReview(selectedReview.id)">삭제</button>
          </div>
        </aside>
      </template>

      <template v-else-if="activeSection === 'questions'">
        <div class="panel-head">
          <div>
            <p class="eyebrow">COMMUNITY</p>
            <h2>문의와 답변</h2>
          </div>
          <button
            v-if="!selectedQuestion"
            class="primary-btn"
            type="button"
            @click="toggleQuestionForm"
          >
            {{ showQuestionForm ? '작성 취소' : '문의 작성' }}
          </button>
        </div>

        <div class="board-toolbar">
          <span>총 {{ searchedQuestions.length }}개</span>
          <input
            v-model.trim="questionSearch"
            type="search"
            placeholder="문의 제목 또는 내용 검색"
            @input="questionPage = 1"
          />
        </div>

        <form v-if="showQuestionForm && !selectedQuestion" class="question-form" @submit.prevent="submitQuestion">
          <input v-model.trim="questionForm.title" type="text" placeholder="문의 제목" />
          <textarea v-model.trim="questionForm.content" rows="3" placeholder="작전 동선, 힌트, 오류 등에 대해 질문하세요."></textarea>
          <button class="primary-btn" type="submit" :disabled="isQuestionSaving">문의 등록</button>
        </form>

        <div v-if="searchedQuestions.length === 0" class="empty-box">조건에 맞는 문의가 없습니다.</div>
        <div v-else-if="!selectedQuestion" class="question-board" role="list">
          <div class="board-head question-head">
            <span>분류</span>
            <span>제목</span>
            <span>작성자</span>
            <span>반응</span>
          </div>
          <div
            v-for="question in pagedQuestions"
            :key="question.id"
            class="question-row"
            tabindex="0"
            role="listitem"
            @click="openQuestionDetail(question)"
            @keyup.enter="openQuestionDetail(question)"
          >
            <span class="board-category">문의</span>
            <span class="board-title">{{ question.title }}</span>
            <span class="board-author">{{ question.authorNickname || '요원' }}</span>
            <span class="board-meta question-reactions">
              <span>{{ (question.answers || []).length }}답변</span>
              <button
                type="button"
                class="like-btn"
                :class="{ active: question.liked }"
                @click.stop="toggleQuestionLike(question)"
              >
                ♥ {{ question.likeCount || 0 }}
              </button>
            </span>
          </div>
          <nav v-if="questionTotalPages > 1" class="pagination" aria-label="문의 페이지">
            <button type="button" :disabled="questionPage === 1" @click="questionPage -= 1">‹</button>
            <button
              v-for="(page, index) in questionPageButtons"
              :key="`question-${page}-${index}`"
              type="button"
              :class="{ active: questionPage === page, dots: page === '...' }"
              :disabled="page === '...'"
              @click="questionPage = page"
            >
              {{ page }}
            </button>
            <button type="button" :disabled="questionPage === questionTotalPages" @click="questionPage += 1">›</button>
          </nav>
        </div>
        <article v-else class="question-item">
          <button type="button" class="ghost-btn small" @click="closeQuestionDetail">목록으로</button>
          <div class="item-head">
            <strong>{{ selectedQuestion.title }}</strong>
            <span>{{ selectedQuestion.authorNickname || '요원' }}</span>
          </div>
          <p>{{ selectedQuestion.content }}</p>
          <div class="item-actions">
            <button
              type="button"
              class="ghost-btn small like-detail-btn"
              :class="{ active: selectedQuestion.liked }"
              @click="toggleQuestionLike(selectedQuestion)"
            >
              ♥ 좋아요 {{ selectedQuestion.likeCount || 0 }}
            </button>
            <button v-if="selectedQuestion.mine" type="button" class="danger-btn small" @click="deleteQuestion(selectedQuestion.id)">문의 삭제</button>
          </div>

          <div class="answer-list">
            <div v-for="answer in selectedQuestion.answers || []" :key="answer.id" class="answer-item">
              <strong>{{ answer.authorNickname || '요원' }}</strong>
              <p>{{ answer.content }}</p>
              <button v-if="answer.mine" type="button" class="danger-btn small" @click="deleteAnswer(selectedQuestion.id, answer.id)">삭제</button>
            </div>
          </div>

          <form class="answer-form" @submit.prevent="submitAnswer(selectedQuestion)">
            <input v-model.trim="answerDrafts[selectedQuestion.id]" type="text" placeholder="답변 작성" />
            <button class="ghost-btn small" type="submit">답변</button>
          </form>
        </article>
      </template>

      <template v-else>
        <div class="panel-head">
          <div>
            <p class="eyebrow">CLEAR RECORD</p>
            <h2>클리어 기록 보기</h2>
          </div>
        </div>
        <article v-if="hasClearedFinalMission" class="clear-record-box success">
          <strong>클리어한 사건입니다.</strong>
          <p>최종 작전 기록과 수집한 단서 해설을 확인할 수 있습니다.</p>
          <button class="primary-btn" type="button" @click="openClearReport">클리어 기록 불러오기</button>
        </article>
        <article v-else class="clear-record-box">
          <strong>클리어하지 못한 사건입니다.</strong>
          <p>최종 미션을 클리어하면 점수, 소요 시간, 단서 해설이 이곳에서 열립니다.</p>
          <button class="primary-btn" type="button" @click="startMission">작전 시작</button>
        </article>
      </template>
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
const selectedReviewId = ref(null);
const selectedQuestionId = ref(null);
const activeSection = ref('entry');
const showQuestionForm = ref(false);
const reviewSearch = ref('');
const questionSearch = ref('');
const reviewPage = ref(1);
const questionPage = ref(1);
const boardPageSize = 10;

const detailSections = [
  { key: 'entry', title: '진입점 확인', description: '작전 내용과 시작' },
  { key: 'reviews', title: '평점과 후기', description: '클리어 요원 리뷰' },
  { key: 'questions', title: '문의와 답변', description: '질문과 답변 게시판' },
  { key: 'clear', title: '클리어 기록 보기', description: '완료한 사건 기록' }
];

const operationBriefText = computed(() => {
  const description = compactDisplayText(region.value?.description, 900);
  if (description) {
    return description;
  }
  const title = compactDisplayText(region.value?.name, 80) || '선택한 작전';
  return `${title}의 작전 내용이 아직 복호화되지 않았습니다. 브리핑 화면에서 현재 등록된 미션 단서와 현장 정보를 확인하세요.`;
});
const finalMission = computed(() => missions.value.find(mission => mission.missionType === 'FINAL' || mission.isFinal === true || mission.final === true) || null);
const finalMissionId = computed(() => finalMission.value?.id || null);
const hasClearedFinalMission = computed(() => finalMission.value?.sessionStatus === 'CLEARED');
const canWriteReview = computed(() => reviewState.value.canReview === true || hasClearedFinalMission.value);
const filteredReviews = computed(() => {
  if (reviewRatingFilter.value === 'all') return reviewState.value.reviews;
  const minimum = Number(reviewRatingFilter.value);
  return reviewState.value.reviews.filter(review => Number(review.rating || 0) >= minimum);
});
const searchedReviews = computed(() => {
  const keyword = normalizeSearch(reviewSearch.value);
  if (!keyword) return filteredReviews.value;
  return filteredReviews.value.filter(review =>
    normalizeSearch(review.content).includes(keyword)
      || normalizeSearch(review.authorNickname).includes(keyword)
  );
});
const reviewTotalPages = computed(() => Math.max(1, Math.ceil(searchedReviews.value.length / boardPageSize)));
const pagedReviews = computed(() => paginateItems(searchedReviews.value, reviewPage.value));
const reviewPageButtons = computed(() => buildPageButtons(reviewPage.value, reviewTotalPages.value));
const selectedReview = computed(() => searchedReviews.value.find(review => review.id === selectedReviewId.value) || null);
const selectedQuestion = computed(() => questions.value.find(question => question.id === selectedQuestionId.value) || null);
const searchedQuestions = computed(() => {
  const keyword = normalizeSearch(questionSearch.value);
  if (!keyword) return questions.value;
  return questions.value.filter(question =>
    normalizeSearch(question.title).includes(keyword)
      || normalizeSearch(question.content).includes(keyword)
      || normalizeSearch(question.authorNickname).includes(keyword)
  );
});
const questionTotalPages = computed(() => Math.max(1, Math.ceil(searchedQuestions.value.length / boardPageSize)));
const pagedQuestions = computed(() => paginateItems(searchedQuestions.value, questionPage.value));
const questionPageButtons = computed(() => buildPageButtons(questionPage.value, questionTotalPages.value));

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
    params: { sort: reviewSort.value, userId: sessionStore.userId || 1 }
  });
  reviewState.value = response.data;
  const mine = (response.data.reviews || []).find(review => review.mine);
  if (mine) {
    reviewForm.value = { rating: mine.rating, content: mine.content };
  }
  if (selectedReviewId.value && !(response.data.reviews || []).some(review => review.id === selectedReviewId.value)) {
    selectedReviewId.value = null;
  }
  reviewPage.value = Math.min(reviewPage.value, reviewTotalPages.value);
};

const fetchQuestions = async () => {
  const response = await apiClient.get(`/v1/regions/${regionId}/questions`, {
    params: { userId: sessionStore.userId || 1 }
  });
  questions.value = response.data || [];
  if (selectedQuestionId.value && !questions.value.some(question => question.id === selectedQuestionId.value)) {
    selectedQuestionId.value = null;
  }
};

const submitReview = async () => {
  if (!reviewForm.value.content) return;
  isReviewSaving.value = true;
  try {
    if (reviewState.value.myReviewId) {
      await apiClient.put(`/v1/regions/${regionId}/reviews/${reviewState.value.myReviewId}`, reviewForm.value, {
        params: { userId: sessionStore.userId || 1 }
      });
    } else {
      await apiClient.post(`/v1/regions/${regionId}/reviews`, reviewForm.value, {
        params: { userId: sessionStore.userId || 1 }
      });
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

const openReviewDetail = (review) => {
  selectedReviewId.value = review.id;
};

const closeReviewDetail = () => {
  selectedReviewId.value = null;
};

const openQuestionDetail = (question) => {
  selectedQuestionId.value = question.id;
};

const closeQuestionDetail = () => {
  selectedQuestionId.value = null;
};

const setActiveSection = (key) => {
  activeSection.value = key;
  selectedReviewId.value = null;
  selectedQuestionId.value = null;
  showQuestionForm.value = false;
  reviewPage.value = 1;
  questionPage.value = 1;
};

const toggleQuestionForm = () => {
  showQuestionForm.value = !showQuestionForm.value;
};

const reviewSummaryText = (content) => {
  const text = String(content || '').replace(/\s+/g, ' ').trim();
  if (!text) return '내용 없는 리뷰';
  return text.length > 42 ? `${text.slice(0, 42)}...` : text;
};

const deleteReview = async (reviewId) => {
  if (!confirm('리뷰를 삭제할까요?')) return;
  try {
    await apiClient.delete(`/v1/regions/${regionId}/reviews/${reviewId}`, {
      params: { userId: sessionStore.userId || 1 }
    });
    reviewState.value = {
      ...reviewState.value,
      reviews: reviewState.value.reviews.filter(review => review.id !== reviewId),
      reviewCount: Math.max(0, Number(reviewState.value.reviewCount || 0) - 1),
      myReviewId: reviewState.value.myReviewId === reviewId ? null : reviewState.value.myReviewId
    };
    reviewForm.value = { rating: 5, content: '' };
    if (selectedReviewId.value === reviewId) {
      selectedReviewId.value = null;
    }
    await fetchReviews();
  } catch (error) {
    alert(error.userMessage || '리뷰 삭제에 실패했습니다.');
  }
};

const toggleReviewLike = async (review) => {
  const response = await apiClient.post(`/v1/regions/${regionId}/reviews/${review.id}/like`, null, {
    params: { userId: sessionStore.userId || 1 }
  });
  const updated = response.data;
  const index = reviewState.value.reviews.findIndex(item => item.id === updated.id);
  if (index !== -1) {
    reviewState.value.reviews[index] = updated;
  }
};

const submitQuestion = async () => {
  if (!questionForm.value.title || !questionForm.value.content) return;
  isQuestionSaving.value = true;
  try {
    await apiClient.post(`/v1/regions/${regionId}/questions`, questionForm.value, {
      params: { userId: sessionStore.userId || 1 }
    });
    questionForm.value = { title: '', content: '' };
    showQuestionForm.value = false;
    await fetchQuestions();
  } catch (error) {
    alert(error.userMessage || '문의 등록에 실패했습니다.');
  } finally {
    isQuestionSaving.value = false;
  }
};

const deleteQuestion = async (questionId) => {
  if (!confirm('문의글을 삭제할까요?')) return;
  try {
    await apiClient.delete(`/v1/regions/${regionId}/questions/${questionId}`, {
      params: { userId: sessionStore.userId || 1 }
    });
    questions.value = questions.value.filter(question => question.id !== questionId);
    if (selectedQuestionId.value === questionId) {
      selectedQuestionId.value = null;
    }
    await fetchQuestions();
  } catch (error) {
    alert(error.userMessage || '문의글 삭제에 실패했습니다.');
  }
};

const submitAnswer = async (question) => {
  const content = answerDrafts.value[question.id];
  if (!content) return;
  await apiClient.post(`/v1/regions/${regionId}/questions/${question.id}/answers`, { content }, {
    params: { userId: sessionStore.userId || 1 }
  });
  answerDrafts.value[question.id] = '';
  await fetchQuestions();
};

const toggleQuestionLike = async (question) => {
  const response = await apiClient.post(`/v1/regions/${regionId}/questions/${question.id}/like`, null, {
    params: { userId: sessionStore.userId || 1 }
  });
  const updated = response.data;
  const index = questions.value.findIndex(item => item.id === updated.id);
  if (index !== -1) {
    questions.value[index] = updated;
  }
};

const deleteAnswer = async (questionId, answerId) => {
  try {
    await apiClient.delete(`/v1/regions/${regionId}/questions/${questionId}/answers/${answerId}`, {
      params: { userId: sessionStore.userId || 1 }
    });
    const question = questions.value.find(item => item.id === questionId);
    if (question) {
      question.answers = (question.answers || []).filter(answer => answer.id !== answerId);
    }
    await fetchQuestions();
  } catch (error) {
    alert(error.userMessage || '답변 삭제에 실패했습니다.');
  }
};

const startMission = () => {
  router.push({ name: 'Briefing', query: { regionId } });
};

const openClearReport = () => {
  if (!finalMissionId.value || !hasClearedFinalMission.value) {
    activeSection.value = 'clear';
    return;
  }
  router.push({ name: 'Clear', params: { missionId: finalMissionId.value }, query: { regionId } });
};

const sectionCount = (key) => {
  if (key === 'reviews') return reviewState.value.reviewCount || 0;
  if (key === 'questions') return questions.value.length;
  if (key === 'clear') return hasClearedFinalMission.value ? 1 : 0;
  return missions.value.length || 0;
};

const normalizeSearch = (value) => {
  return String(value || '').replace(/\s+/g, '').toLowerCase();
};

const compactDisplayText = (value, maxLength) => {
  const normalized = String(value || '')
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/<[^>]*>/g, ' ')
    .replace(/[ \t]+/g, ' ')
    .replace(/\n{3,}/g, '\n\n')
    .trim();
  if (!normalized) return '';
  return normalized.length > maxLength ? `${normalized.slice(0, maxLength).trim()}...` : normalized;
};

const paginateItems = (items, page) => {
  const currentPage = Math.min(Math.max(1, page), Math.max(1, Math.ceil(items.length / boardPageSize)));
  const start = (currentPage - 1) * boardPageSize;
  return items.slice(start, start + boardPageSize);
};

const buildPageButtons = (currentPage, totalPages) => {
  if (totalPages <= 7) {
    return Array.from({ length: totalPages }, (_, index) => index + 1);
  }
  const pages = new Set([1, totalPages]);
  for (let page = currentPage - 2; page <= currentPage + 2; page += 1) {
    if (page >= 1 && page <= totalPages) {
      pages.add(page);
    }
  }
  const sorted = Array.from(pages).sort((a, b) => a - b);
  return sorted.flatMap((page, index) => {
    if (index === 0) return [page];
    const previous = sorted[index - 1];
    return page - previous > 1 ? ['...', page] : [page];
  });
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
.detail-layout { width: min(1120px, 100%); margin: 0 auto; display: block; }
.content-panel { border: 1px solid rgba(148, 163, 184, 0.18); border-radius: 8px; background: rgba(15, 23, 42, 0.64); padding: 18px; }
.content-panel { min-width: 0; }
.board-panel { padding-top: 0; overflow: hidden; }
.board-tabs { display: flex; flex-wrap: wrap; gap: 0; margin: 0 -18px 18px; padding: 0 18px; border-bottom: 1px solid rgba(148, 163, 184, 0.24); background: rgba(2, 6, 23, 0.22); }
.board-tabs button { min-height: 45px; border: 0; border-bottom: 2px solid transparent; background: transparent; color: #94a3b8; cursor: pointer; font: inherit; font-size: 0.84rem; font-weight: 900; padding: 0 14px; }
.board-tabs button.active { border-bottom-color: #38bdf8; color: #f8fafc; }
.board-tabs span { margin-left: 4px; color: #67e8f9; font-size: 0.78rem; }
.mission-brief h2, .panel-head h2 { margin: 0; color: #fff; font-size: 1.18rem; }
.operation-content { padding: 18px; border: 1px solid rgba(103, 232, 249, 0.18); border-radius: 8px; background: rgba(2, 6, 23, 0.28); }
.operation-content h3 { margin: 0 0 12px; color: #67e8f9; font-size: 0.98rem; }
.operation-content p { margin: 0; color: #e2e8f0; font-size: 1.02rem; line-height: 1.85; white-space: pre-line; }
.action-row { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 14px; }
.clear-record-box { display: grid; gap: 10px; padding: 20px; border: 1px dashed rgba(148, 163, 184, 0.3); border-radius: 8px; background: rgba(2, 6, 23, 0.28); }
.clear-record-box.success { border-style: solid; border-color: rgba(245, 158, 11, 0.38); background: rgba(120, 53, 15, 0.18); }
.clear-record-box strong { color: #f8fafc; font-size: 1.04rem; }
.clear-record-box p { margin: 0; color: #94a3b8; line-height: 1.6; }
.panel-head { display: flex; justify-content: space-between; gap: 12px; align-items: end; margin-bottom: 14px; }
.review-controls { display: flex; gap: 8px; }
.board-toolbar { display: grid; grid-template-columns: auto minmax(220px, 340px); gap: 12px; align-items: center; margin-bottom: 12px; }
.board-toolbar span { color: #94a3b8; font-size: 0.8rem; font-weight: 800; }
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
.review-board { display: grid; gap: 8px; }
.board-head { display: grid; grid-template-columns: 72px minmax(0, 1fr) 110px 170px; gap: 10px; padding: 0 11px 7px; color: #64748b; font-size: 0.72rem; font-weight: 900; border-bottom: 1px solid rgba(148, 163, 184, 0.18); }
.question-head { grid-template-columns: 78px minmax(0, 1fr) 110px 150px; }
.review-row { display: grid; grid-template-columns: 72px minmax(0, 1fr) 110px 170px; gap: 10px; align-items: center; width: 100%; min-height: 44px; border: 1px solid rgba(148, 163, 184, 0.16); border-radius: 6px; background: rgba(2, 6, 23, 0.34); color: #e2e8f0; cursor: pointer; font: inherit; padding: 9px 11px; text-align: left; }
.review-row:hover, .review-row.selected { border-color: rgba(103, 232, 249, 0.5); background: rgba(8, 47, 73, 0.42); }
.review-rating { color: #fcd34d; font-weight: 900; }
.review-title, .review-author, .review-time { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.review-title { color: #f8fafc; font-weight: 800; }
.review-author, .review-time { color: #94a3b8; font-size: 0.82rem; }
.review-reactions { display: flex; gap: 8px; align-items: center; overflow: visible; }
.question-board { display: grid; gap: 8px; }
.question-row { display: grid; grid-template-columns: 78px minmax(0, 1fr) 110px 150px; gap: 10px; align-items: center; width: 100%; min-height: 46px; border: 1px solid rgba(148, 163, 184, 0.16); border-radius: 6px; background: rgba(2, 6, 23, 0.34); color: #e2e8f0; cursor: pointer; font: inherit; padding: 9px 11px; text-align: left; }
.question-row:hover { border-color: rgba(103, 232, 249, 0.5); background: rgba(8, 47, 73, 0.42); }
.board-category { color: #38bdf8; font-size: 0.78rem; font-weight: 900; }
.board-title, .board-author, .board-meta { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.board-title { color: #f8fafc; font-weight: 800; }
.board-author, .board-meta { color: #94a3b8; font-size: 0.82rem; }
.question-reactions { display: flex; gap: 8px; align-items: center; overflow: visible; }
.like-btn { flex: 0 0 auto; border: 1px solid rgba(148, 163, 184, 0.28); border-radius: 999px; background: rgba(2, 6, 23, 0.46); color: #94a3b8; cursor: pointer; font: inherit; font-size: 0.76rem; font-weight: 900; padding: 4px 8px; }
.like-btn.active, .like-detail-btn.active { border-color: rgba(244, 114, 182, 0.68); background: rgba(157, 23, 77, 0.22); color: #f9a8d4; }
.like-btn:hover, .like-detail-btn:hover { border-color: rgba(244, 114, 182, 0.8); color: #fbcfe8; }
.pagination { display: flex; justify-content: center; gap: 6px; margin: 18px 0 4px; }
.pagination button { min-width: 34px; height: 34px; border: 1px solid rgba(148, 163, 184, 0.28); border-radius: 4px; background: rgba(2, 6, 23, 0.5); color: #cbd5e1; cursor: pointer; font: inherit; font-size: 0.82rem; font-weight: 900; }
.pagination button.active { border-color: #38bdf8; background: #0ea5e9; color: #f8fafc; }
.pagination button:disabled { opacity: 0.35; cursor: not-allowed; }
.review-detail-panel, .question-item { padding: 14px; border: 1px solid rgba(148, 163, 184, 0.16); border-radius: 8px; background: rgba(2, 6, 23, 0.34); margin-top: 10px; }
.review-detail-panel > .ghost-btn { margin-bottom: 12px; }
.item-head { display: flex; justify-content: space-between; gap: 10px; margin-bottom: 8px; }
.item-head strong { min-width: 0; overflow: hidden; color: #f8fafc; text-overflow: ellipsis; white-space: nowrap; }
.item-head span { flex: 0 0 auto; color: #fcd34d; font-size: 0.82rem; font-weight: 800; }
.review-detail-panel p, .question-item p { margin: 0; color: #cbd5e1; line-height: 1.6; }
.item-actions { display: flex; gap: 8px; justify-content: flex-end; margin-top: 10px; }
.answer-list { display: grid; gap: 8px; margin: 12px 0; }
.answer-item { padding: 10px; border-left: 2px solid rgba(103, 232, 249, 0.48); background: rgba(8, 47, 73, 0.24); }
.answer-item strong { display: block; margin-bottom: 4px; color: #67e8f9; font-size: 0.82rem; }
.answer-form { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 8px; }
@media (max-width: 840px) {
  .detail-header { grid-template-columns: 1fr; }
  .review-form { grid-template-columns: 1fr; }
  .board-toolbar { grid-template-columns: 1fr; }
  .review-row { grid-template-columns: 56px minmax(0, 1fr); }
  .question-row { grid-template-columns: 62px minmax(0, 1fr); }
  .board-head { display: none; }
  .review-author, .review-time { display: none; }
  .board-author, .board-meta { display: none; }
  .panel-head { align-items: stretch; flex-direction: column; }
  .review-controls { flex-direction: column; }
}
</style>
