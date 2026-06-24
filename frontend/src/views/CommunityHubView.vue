<template>
  <main class="community-board-page">
    <header class="hero">
      <div>
        <p>COMMUNITY BOARD</p>
        <h1>커뮤니티</h1>
        <span>권역별 글을 한 게시판에서 모아보고 검색합니다.</span>
      </div>
      <button type="button" @click="reload">새로고침</button>
    </header>

    <p v-if="message" class="toast" :class="messageType">{{ message }}</p>

    <section class="toolbar">
      <label>
        <span>검색</span>
        <input v-model.trim="keyword" type="search" placeholder="제목, 내용, 작성자 검색" />
      </label>
      <label>
        <span>유형</span>
        <select v-model="typeFilter">
          <option value="all">전체</option>
          <option value="question">질문</option>
          <option value="review">리뷰</option>
        </select>
      </label>
      <label>
        <span>지역</span>
        <select v-model="regionFilter">
          <option value="all">전체 권역</option>
          <option v-for="area in regionAreas" :key="area.code" :value="area.code">{{ area.label }}</option>
        </select>
      </label>
      <label>
        <span>정렬</span>
        <select v-model="sortMode">
          <option value="latest">최신순</option>
          <option value="popular">좋아요순</option>
        </select>
      </label>
    </section>

    <section class="board-layout">
      <aside class="compose-panel">
        <div class="panel-head">
          <div>
            <p>NEW QUESTION</p>
            <h2>질문 작성</h2>
          </div>
        </div>
        <form class="compose-form" @submit.prevent="submitQuestion">
          <label>
            <span>권역</span>
            <select v-model="questionForm.regionId">
              <option v-for="area in regionAreas" :key="area.regionId" :value="area.regionId">{{ area.label }}</option>
            </select>
          </label>
          <label>
            <span>제목</span>
            <input v-model.trim="questionForm.title" type="text" maxlength="120" placeholder="스포일러 없이 질문 제목을 입력하세요." />
          </label>
          <label>
            <span>내용</span>
            <textarea v-model.trim="questionForm.content" rows="5" maxlength="1000" placeholder="동선, 난이도, 현장 상황 등을 질문하세요."></textarea>
          </label>
          <button type="submit">질문 등록</button>
        </form>
      </aside>

      <section class="feed-panel">
        <div class="panel-head">
          <div>
            <p>BOARD FEED</p>
            <h2>게시글</h2>
          </div>
          <span>{{ filteredPosts.length }}개</span>
        </div>

        <div v-if="loading" class="state">커뮤니티 글을 불러오는 중입니다.</div>
        <div v-else-if="!filteredPosts.length" class="state">조건에 맞는 게시글이 없습니다.</div>
        <article v-for="post in visiblePosts" v-else :key="post.key" class="post-card" :class="post.type">
          <div class="post-top">
            <span class="type-badge">{{ post.type === 'review' ? '리뷰' : '질문' }}</span>
            <span class="region-badge">{{ post.regionLabel }}</span>
            <small>좋아요 {{ post.likeCount || 0 }}</small>
          </div>
          <h3>{{ post.title }}</h3>
          <p>{{ post.content }}</p>
          <div class="post-meta">
            <span>{{ post.authorNickname || '요원' }}</span>
            <span v-if="post.type === 'review'">별점 {{ post.rating || 0 }}</span>
            <span v-if="post.answerCount != null">답변 {{ post.answerCount }}</span>
          </div>
          <div class="post-actions">
            <button type="button" @click="openRegionBoard(post)">상세 게시판</button>
            <button type="button" class="ghost" @click="toggleLike(post)">좋아요</button>
          </div>
        </article>

        <nav v-if="!loading && totalPages > 1" class="pagination" aria-label="커뮤니티 페이지">
          <button type="button" :disabled="currentPage <= 1" @click="goPage(1)">«</button>
          <button type="button" :disabled="currentPage <= 1" @click="goPage(currentPage - 1)">‹</button>
          <button
            v-for="page in visiblePages"
            :key="`community-page-${page}`"
            type="button"
            class="page-number"
            :class="{ active: page === currentPage }"
            @click="goPage(page)"
          >
            {{ page }}
          </button>
          <button type="button" :disabled="currentPage >= totalPages" @click="goPage(currentPage + 1)">›</button>
          <button type="button" :disabled="currentPage >= totalPages" @click="goPage(totalPages)">»</button>
        </nav>
      </section>
    </section>

    <MainBottomNav />
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import MainBottomNav from '@/components/MainBottomNav.vue';
import { regionCommunityApi } from '@/api/regionCommunityApi';
import { regionAreas } from '@/constants/regionAreas.js';

const router = useRouter();
const loading = ref(true);
const posts = ref([]);
const keyword = ref('');
const typeFilter = ref('all');
const regionFilter = ref('all');
const sortMode = ref('latest');
const currentPage = ref(1);
const pageSize = 6;
const message = ref('');
const messageType = ref('success');
const questionForm = reactive({
  regionId: regionAreas[0]?.regionId || 1,
  title: '',
  content: ''
});

const filteredPosts = computed(() => {
  const normalizedKeyword = keyword.value.toLowerCase();
  return posts.value
    .filter((post) => typeFilter.value === 'all' || post.type === typeFilter.value)
    .filter((post) => regionFilter.value === 'all' || post.areaCode === regionFilter.value)
    .filter((post) => {
      if (!normalizedKeyword) return true;
      return [post.title, post.content, post.authorNickname, post.regionLabel]
        .some((value) => String(value || '').toLowerCase().includes(normalizedKeyword));
    })
    .sort((left, right) => {
      if (sortMode.value === 'popular') {
        return (right.likeCount || 0) - (left.likeCount || 0);
      }
      return new Date(right.createdAt || 0) - new Date(left.createdAt || 0);
    });
});
const totalPages = computed(() => Math.max(1, Math.ceil(filteredPosts.value.length / pageSize)));
const visiblePosts = computed(() => {
  const start = (currentPage.value - 1) * pageSize;
  return filteredPosts.value.slice(start, start + pageSize);
});
const visiblePages = computed(() => {
  const total = totalPages.value;
  const start = Math.max(1, Math.min(currentPage.value - 2, total - 4));
  const end = Math.min(total, start + 4);
  return Array.from({ length: end - start + 1 }, (_, index) => start + index);
});

watch([keyword, typeFilter, regionFilter, sortMode], () => {
  currentPage.value = 1;
});

onMounted(reload);

async function reload() {
  loading.value = true;
  message.value = '';
  try {
    const bundles = await Promise.all(regionAreas.map(loadAreaCommunity));
    posts.value = bundles.flat();
    currentPage.value = Math.min(currentPage.value, totalPages.value);
  } catch (error) {
    setMessage(error.userMessage || '커뮤니티 글을 불러오지 못했습니다.', 'error');
  } finally {
    loading.value = false;
  }
}

async function loadAreaCommunity(area) {
  const [reviewState, questions] = await Promise.all([
    regionCommunityApi.getReviews(area.regionId),
    regionCommunityApi.getQuestions(area.regionId)
  ]);
  const reviews = (reviewState.reviews || []).map((review) => ({
    key: `review-${area.regionId}-${review.id}`,
    id: review.id,
    type: 'review',
    regionId: area.regionId,
    areaCode: area.code,
    regionLabel: area.label,
    title: `${area.label} 클리어 리뷰`,
    content: review.content,
    authorNickname: review.authorNickname,
    likeCount: review.likeCount || 0,
    rating: review.rating,
    createdAt: review.createdAt
  }));
  const questionPosts = (questions || []).map((question) => ({
    key: `question-${area.regionId}-${question.id}`,
    id: question.id,
    type: 'question',
    regionId: area.regionId,
    areaCode: area.code,
    regionLabel: area.label,
    title: question.title,
    content: question.content,
    authorNickname: question.authorNickname,
    likeCount: question.likeCount || 0,
    answerCount: (question.answers || []).length,
    createdAt: question.createdAt
  }));
  return [...reviews, ...questionPosts];
}

async function submitQuestion() {
  if (!questionForm.title || !questionForm.content) {
    setMessage('질문 제목과 내용을 입력해 주세요.', 'error');
    return;
  }
  try {
    await regionCommunityApi.createQuestion(questionForm.regionId, {
      title: questionForm.title,
      content: questionForm.content
    });
    questionForm.title = '';
    questionForm.content = '';
    setMessage('질문을 등록했습니다.');
    await reload();
  } catch (error) {
    setMessage(error.userMessage || '질문을 등록하지 못했습니다.', 'error');
  }
}

async function toggleLike(post) {
  try {
    if (post.type === 'review') {
      await regionCommunityApi.toggleReviewLike(post.regionId, post.id);
    } else {
      await regionCommunityApi.toggleQuestionLike(post.regionId, post.id);
    }
    await reload();
  } catch (error) {
    setMessage(error.userMessage || '좋아요를 처리하지 못했습니다.', 'error');
  }
}

function openRegionBoard(post) {
  router.push({
    name: 'RegionCommunity',
    params: { regionId: post.regionId },
    query: { areaCode: post.areaCode }
  });
}

function goPage(page) {
  currentPage.value = Math.min(Math.max(1, page), totalPages.value);
}

function setMessage(text, type = 'success') {
  message.value = text;
  messageType.value = type;
}
</script>

<style scoped>
.community-board-page { min-height: 100vh; box-sizing: border-box; padding: 24px 16px 126px; background: radial-gradient(circle at 18% 0%, rgba(14,116,144,.22), transparent 34%), linear-gradient(160deg, #0f172a, #111827 60%, #050505); color: #f8fafc; font-family: Georgia, 'Noto Sans KR', serif; }
.hero, .toolbar, .board-layout, .toast { width: min(100%, 1080px); box-sizing: border-box; margin-left: auto; margin-right: auto; }
.hero { display: flex; justify-content: space-between; align-items: end; gap: 16px; margin-bottom: 14px; padding: 22px; border: 1px solid rgba(125,211,252,.22); border-radius: 20px; background: rgba(15,23,42,.68); }
.hero p, .panel-head p { margin: 0 0 8px; color: #67e8f9; font-size: .74rem; font-weight: 900; letter-spacing: .15em; }
h1 { margin: 0; font-size: clamp(2.2rem, 7vw, 4.5rem); line-height: .95; }
.hero span { color: #cbd5e1; }
button { min-height: 40px; border: 0; border-radius: 12px; background: #0e7490; color: #fff; font: inherit; font-weight: 900; padding: 0 14px; }
.ghost { background: #334155; }
.toolbar { display: grid; grid-template-columns: minmax(240px, 1fr) repeat(3, minmax(120px, 160px)); gap: 10px; margin-bottom: 14px; padding: 14px; border: 1px solid rgba(148,163,184,.16); border-radius: 16px; background: rgba(15,23,42,.58); }
label { display: grid; gap: 6px; color: #cbd5e1; font-size: .82rem; font-weight: 900; }
input, select, textarea { width: 100%; box-sizing: border-box; border: 1px solid rgba(148,163,184,.24); border-radius: 12px; background: rgba(2,6,23,.48); color: #f8fafc; font: inherit; padding: 10px 12px; }
.board-layout { display: grid; grid-template-columns: 320px minmax(0, 1fr); gap: 14px; }
.compose-panel, .feed-panel { padding: 18px; border: 1px solid rgba(148,163,184,.18); border-radius: 20px; background: rgba(15,23,42,.68); box-shadow: 0 22px 58px rgba(0,0,0,.2); }
.panel-head { display: flex; justify-content: space-between; align-items: end; gap: 12px; margin-bottom: 12px; }
.panel-head h2 { margin: 0; }
.panel-head span { color: #fde68a; font-weight: 900; }
.compose-form { display: grid; gap: 12px; }
.post-card { padding: 14px; border: 1px solid rgba(148,163,184,.16); border-radius: 16px; background: rgba(2,6,23,.42); }
.post-card + .post-card { margin-top: 10px; }
.post-card.review { border-left: 5px solid #f59e0b; }
.post-card.question { border-left: 5px solid #06b6d4; }
.post-top, .post-meta, .post-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.type-badge, .region-badge { border-radius: 999px; padding: 4px 8px; font-size: .72rem; font-weight: 1000; }
.type-badge { background: rgba(14,116,144,.36); color: #a5f3fc; }
.region-badge { background: rgba(245,158,11,.18); color: #fde68a; }
.post-top small, .post-meta { color: #94a3b8; }
.post-card h3 { margin: 10px 0 6px; color: #fff7ed; }
.post-card p { margin: 0 0 12px; color: #cbd5e1; line-height: 1.55; white-space: pre-wrap; }
.state, .toast { padding: 16px; border: 1px dashed rgba(148,163,184,.34); border-radius: 16px; color: #cbd5e1; text-align: center; }
.toast { margin-bottom: 12px; border-style: solid; background: rgba(22,101,52,.18); color: #bbf7d0; }
.toast.error { color: #fecaca; background: rgba(127,29,29,.18); }
.pagination { margin: 14px auto 0; display: flex; align-items: center; justify-content: center; gap: 7px; }
.pagination button { min-width: 32px; width: 32px; min-height: 32px; padding: 0; border-radius: 4px; border: 1px solid rgba(148,163,184,.34); background: rgba(15,23,42,.8); color: #cbd5e1; }
.pagination .page-number.active { border-color: #10b981; background: #059669; color: #fff; }
.pagination button:disabled { opacity: .42; cursor: not-allowed; }
@media (max-width: 860px) {
  .hero, .board-layout, .toolbar { grid-template-columns: 1fr; display: grid; }
}
</style>
