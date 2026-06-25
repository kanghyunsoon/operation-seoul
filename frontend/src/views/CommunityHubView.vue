<template>
  <main class="community-page">
    <header class="hero">
      <div>
        <p>COMMUNITY BOARD</p>
        <h1>커뮤니티</h1>
        <span>지역권별 게시글과 공지사항을 확인합니다.</span>
      </div>
      <button type="button" @click="router.push({ name: 'CommunityPostWrite' })">게시글 작성</button>
    </header>

    <section class="board-shell">
      <nav class="region-tabs" aria-label="지역 선택">
        <button type="button" class="slide-btn" @click="scrollTabs(-1)">‹</button>
        <div ref="tabScroller" class="tab-scroller">
          <button
            v-for="tab in tabs"
            :key="tab.code"
            type="button"
            :class="{ active: selectedArea === tab.code }"
            @click="selectedArea = tab.code"
          >
            {{ tab.label }}
          </button>
        </div>
        <button type="button" class="slide-btn" @click="scrollTabs(1)">›</button>
      </nav>

      <div class="board-head">
        <h2>게시글</h2>
        <select v-model="sortMode" aria-label="정렬">
          <option value="latest">최신순</option>
          <option value="popular">추천순</option>
        </select>
      </div>

      <section class="search-row">
        <input v-model.trim="keyword" type="search" placeholder="검색어를 입력하세요" />
        <select v-model="searchMode" aria-label="검색 범위">
          <option value="all">글 + 댓글</option>
          <option value="title">제목만</option>
          <option value="author">글작성자</option>
          <option value="comment">댓글내용</option>
        </select>
      </section>

      <p v-if="message" class="toast" :class="messageType">{{ message }}</p>
      <section v-if="loading" class="state">게시글을 불러오는 중입니다.</section>
      <template v-else>
        <section v-if="noticePosts.length" class="notice-frame">
          <strong>공지사항</strong>
          <article v-for="post in noticePosts" :key="post.key" @click="openPost(post)">
            <span>공지</span>
            <b>{{ post.title }}</b>
            <small>{{ post.regionLabel }} · {{ post.authorNickname || '관리자' }}</small>
          </article>
        </section>

        <section v-if="visiblePosts.length" class="post-list">
          <article v-for="post in visiblePosts" :key="post.key" class="post-card" @click="openPost(post)">
            <div>
              <span class="region">{{ post.regionLabel }}</span>
              <h3>{{ post.title }}</h3>
            </div>
            <footer>
              <span>{{ post.authorNickname || '유저' }}</span>
              <span>추천 {{ post.likeCount || 0 }}</span>
              <span>댓글 {{ post.commentCount || 0 }}</span>
            </footer>
          </article>
        </section>
        <section v-else class="state">조건에 맞는 게시글이 없습니다.</section>

        <nav v-if="totalPages > 1" class="pager" aria-label="게시글 페이지">
          <button type="button" :disabled="currentPage <= 1" @click="goPage(currentPage - 1)">‹</button>
          <button
            v-for="page in visiblePages"
            :key="page"
            type="button"
            :class="{ active: page === currentPage }"
            @click="goPage(page)"
          >
            {{ page }}
          </button>
          <button type="button" :disabled="currentPage >= totalPages" @click="goPage(currentPage + 1)">›</button>
        </nav>
      </template>
    </section>

    <MainBottomNav />
  </main>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import MainBottomNav from '@/components/MainBottomNav.vue';
import { regionCommunityApi } from '@/api/regionCommunityApi';
import { regionAreas } from '@/constants/regionAreas';

const router = useRouter();
const loading = ref(true);
const posts = ref([]);
const selectedArea = ref('all');
const sortMode = ref('latest');
const searchMode = ref('all');
const keyword = ref('');
const currentPage = ref(1);
const pageSize = 10;
const message = ref('');
const messageType = ref('success');
const tabScroller = ref(null);

const tabs = computed(() => [{ code: 'all', label: '전체' }, ...regionAreas.map((area) => ({ code: area.code, label: area.label }))]);
const filteredPosts = computed(() => {
  const normalized = keyword.value.toLowerCase();
  return posts.value
    .filter((post) => selectedArea.value === 'all' || post.areaCode === selectedArea.value)
    .filter((post) => matchesSearch(post, normalized));
});
const noticePosts = computed(() => filteredPosts.value.filter((post) => post.notice).sort(latestSort));
const regularPosts = computed(() => filteredPosts.value.filter((post) => !post.notice).sort((left, right) => {
  if (sortMode.value === 'popular') return popularity(right) - popularity(left) || latestSort(left, right);
  return latestSort(left, right);
}));
const totalPages = computed(() => Math.max(1, Math.ceil(regularPosts.value.length / pageSize)));
const visiblePosts = computed(() => regularPosts.value.slice((currentPage.value - 1) * pageSize, currentPage.value * pageSize));
const visiblePages = computed(() => Array.from({ length: totalPages.value }, (_, index) => index + 1));

watch([selectedArea, sortMode, searchMode, keyword], () => {
  currentPage.value = 1;
});

onMounted(reload);

async function reload() {
  loading.value = true;
  message.value = '';
  try {
    const bundles = await Promise.all(regionAreas.map(loadQuestionsByArea));
    posts.value = bundles.flat();
  } catch (error) {
    setMessage(error.userMessage || '게시글을 불러오지 못했습니다.', 'error');
  } finally {
    loading.value = false;
  }
}

async function loadQuestionsByArea(area) {
  try {
    const questions = await regionCommunityApi.getQuestions(area.regionId);
    return (questions || []).map((question) => ({
      key: `${question.regionId || area.regionId}-${question.id}`,
      id: question.id,
      regionId: question.regionId || area.regionId,
      areaCode: area.code,
      regionLabel: area.label,
      userId: question.userId,
      authorNickname: question.authorNickname,
      title: question.title,
      content: question.content,
      notice: Boolean(question.notice),
      likeCount: question.likeCount || 0,
      commentCount: (question.answers || []).length,
      answers: question.answers || [],
      createdAt: question.createdAt
    }));
  } catch (error) {
    if ([404, 405].includes(error.response?.status)) {
      return [];
    }
    throw error;
  }
}

function matchesSearch(post, normalized) {
  if (!normalized) return true;
  const comments = (post.answers || []).map((answer) => answer.content).join(' ');
  if (searchMode.value === 'title') return post.title?.toLowerCase().includes(normalized);
  if (searchMode.value === 'author') return post.authorNickname?.toLowerCase().includes(normalized);
  if (searchMode.value === 'comment') return comments.toLowerCase().includes(normalized);
  return [post.title, post.content, comments].some((value) => String(value || '').toLowerCase().includes(normalized));
}

function openPost(post) {
  router.push({ name: 'CommunityPostDetail', params: { regionId: post.regionId, questionId: post.id } });
}

function goPage(page) {
  currentPage.value = Math.min(Math.max(1, page), totalPages.value);
}

function scrollTabs(direction) {
  tabScroller.value?.scrollBy({ left: direction * 240, behavior: 'smooth' });
}

function popularity(post) {
  return (post.likeCount || 0) + (post.commentCount || 0);
}

function latestSort(left, right) {
  return new Date(right.createdAt || 0) - new Date(left.createdAt || 0);
}

function setMessage(text, type = 'success') {
  message.value = text;
  messageType.value = type;
}
</script>

<style scoped>
.community-page { min-height: 100vh; box-sizing: border-box; padding: 24px 16px 126px; background: #f8fbff; color: #172033; font-family: 'Noto Sans KR', system-ui, sans-serif; }
.hero, .board-shell { width: min(100%, 920px); box-sizing: border-box; margin: 0 auto; }
.hero { display: flex; justify-content: space-between; align-items: flex-end; gap: 16px; margin-bottom: 14px; padding: 22px; border: 1px solid #d7e2ef; border-radius: 16px; background: #fff; }
.hero p { margin: 0 0 8px; color: #2563eb; font-size: .75rem; font-weight: 900; letter-spacing: .14em; }
h1 { margin: 0; font-size: clamp(2rem, 6vw, 3.4rem); letter-spacing: 0; }
.hero span { color: #64748b; }
button, select { min-height: 40px; border: 1px solid #cbd5e1; border-radius: 10px; background: #fff; color: #172033; font: inherit; font-weight: 900; padding: 0 12px; cursor: pointer; }
.hero button { background: #2563eb; color: #fff; border-color: #2563eb; }
.board-shell { padding: 16px; border: 1px solid #d7e2ef; border-radius: 16px; background: #fff; }
.region-tabs { display: grid; grid-template-columns: 42px minmax(0, 1fr) 42px; gap: 8px; align-items: center; margin-bottom: 14px; }
.tab-scroller { display: flex; gap: 8px; overflow: hidden; scroll-behavior: smooth; }
.tab-scroller button { flex: 0 0 auto; min-width: 92px; }
.tab-scroller button.active { background: #dbeafe; border-color: #2563eb; color: #1d4ed8; }
.slide-btn { font-size: 1.3rem; color: #2563eb; }
.board-head, .search-row { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-bottom: 12px; }
.board-head h2 { margin: 0; }
.search-row input { flex: 1; min-height: 42px; box-sizing: border-box; border: 1px solid #cbd5e1; border-radius: 10px; padding: 0 12px; font: inherit; }
.search-row select { min-width: 150px; }
.notice-frame { margin-bottom: 12px; padding: 12px; border: 1px solid #facc15; border-radius: 12px; background: #fefce8; }
.notice-frame strong { display: block; margin-bottom: 8px; }
.notice-frame article { display: grid; grid-template-columns: auto 1fr auto; gap: 10px; align-items: center; padding: 8px 0; border-top: 0; cursor: pointer; }
.notice-frame span { padding: 3px 7px; border-radius: 999px; background: #facc15; font-size: .75rem; font-weight: 900; }
.notice-frame small { color: #64748b; }
.post-list { display: grid; gap: 10px; }
.post-card { padding: 14px; border: 1px solid #d7e2ef; border-radius: 12px; background: #fff; cursor: pointer; }
.post-card:hover { border-color: #93c5fd; }
.region { color: #2563eb; font-size: .78rem; font-weight: 900; }
.post-card h3 { margin: 6px 0; font-size: 1.08rem; }
.post-card p { margin: 0; color: #64748b; line-height: 1.5; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.post-card footer { display: flex; flex-wrap: wrap; gap: 12px; margin-top: 10px; color: #64748b; font-size: .86rem; font-weight: 800; }
.pager { display: flex; justify-content: center; gap: 8px; margin-top: 14px; flex-wrap: wrap; }
.pager button { min-width: 38px; padding: 0 10px; border-radius: 999px; }
.pager button.active { background: #2563eb; border-color: #2563eb; color: #fff; }
.pager button:disabled { opacity: .45; cursor: default; }
.state, .toast { padding: 16px; border: 1px dashed #cbd5e1; border-radius: 12px; color: #64748b; text-align: center; }
.toast { margin-bottom: 12px; border-style: solid; background: #ecfdf5; color: #047857; }
.toast.error { background: #fff1f2; color: #be123c; }
@media (max-width: 640px) {
  .hero, .board-head, .search-row { display: grid; }
  .notice-frame article { grid-template-columns: 1fr; }
}
</style>
