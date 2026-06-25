<template>
  <main class="feed-page">
    <section v-if="loading" class="state">피드를 불러오는 중입니다.</section>
    <section v-else-if="error" class="state error">{{ error }}</section>
    <section v-else-if="privateProfile" class="state">{{ privateMessage }}</section>
    <template v-else>
      <section class="profile-panel">
        <div class="avatar">
          <img v-if="profileImage" :src="profileImage" alt="프로필 사진" />
          <span v-else>사진</span>
        </div>
        <div class="profile-copy">
          <h1>{{ profile.nickname || '닉네임' }}</h1>
          <p>{{ profile.statusMessage || '상태 메시지를 입력해 보세요.' }}</p>
        </div>
        <div class="profile-stats" aria-label="프로필 통계">
          <button type="button" class="stat-card" disabled>
            <strong>{{ profile.achievedChallengeCount || 0 }}</strong>
            <span>챌린지 수</span>
          </button>
          <button type="button" class="stat-card" :disabled="!isOwnFeed" @click="openFollowModal('followers')">
            <strong>{{ profile.followerCount || 0 }}</strong>
            <span>팔로워</span>
          </button>
          <button type="button" class="stat-card" :disabled="!isOwnFeed" @click="openFollowModal('following')">
            <strong>{{ profile.followingCount || 0 }}</strong>
            <span>팔로잉</span>
          </button>
        </div>
      </section>

      <section class="section-block">
        <div class="section-head">
          <h2>사용자 플레이 성향</h2>
          <button v-if="isOwnFeed" type="button" @click="reload">분석 업데이트</button>
        </div>
        <article v-if="analysis" class="analysis-card">
          <div class="analysis-title">
            <span>AI PLAYER TYPE</span>
            <strong>{{ analysis.playerType }}</strong>
          </div>
          <p>{{ analysis.summary }}</p>
          <div class="mbti-grid" aria-label="플레이 MBTI">
            <h3>플레이 MBTI</h3>
            <div v-for="item in analysis.playMbti || []" :key="item.dimension" class="mbti-row">
              <strong class="dimension-title">{{ item.dimension }}</strong>
              <div class="mbti-label">
                <span>{{ item.leftLabel }} {{ item.leftPercent }}%</span>
                <span>{{ item.rightPercent }}% {{ item.rightLabel }}</span>
              </div>
              <div class="bar">
                <i :style="{ width: `${item.leftPercent}%` }"></i>
              </div>
            </div>
          </div>
        </article>
        <article v-else class="empty-card">아직 분석할 플레이 기록이 없습니다.</article>
      </section>

      <section class="section-block">
        <div class="section-head">
          <h2>유저가 쓴 커뮤니티 게시글</h2>
          <span>{{ community.totalItems || 0 }}개</span>
        </div>
        <div v-if="community.items?.length" class="post-list">
          <article v-for="post in community.items" :key="post.id" class="post-card" @click="openPost(post)">
            <div>
              <span>{{ post.regionName || '커뮤니티' }}</span>
              <h3>{{ post.title }}</h3>
              <p>{{ post.content }}</p>
            </div>
            <footer>
              <span>좋아요 {{ post.likeCount }}</span>
              <span>댓글 {{ post.commentCount }}</span>
            </footer>
          </article>
        </div>
        <article v-else class="empty-card">작성한 커뮤니티 게시글이 없습니다.</article>
        <nav v-if="community.totalPages > 1" class="pager" aria-label="게시글 페이지">
          <button type="button" :disabled="!community.hasPrevious" @click="changePage(page - 1)">‹</button>
          <button
            v-for="pageNumber in pageNumbers"
            :key="pageNumber"
            type="button"
            :class="{ active: pageNumber - 1 === page }"
            @click="changePage(pageNumber - 1)"
          >
            {{ pageNumber }}
          </button>
          <button type="button" :disabled="!community.hasNext" @click="changePage(page + 1)">›</button>
        </nav>
      </section>

      <section class="section-block clear-section">
        <div class="section-head">
          <h2>클리어 맵</h2>
          <span>{{ clearMaps.length }}개</span>
        </div>
        <div v-if="activeMap" class="clear-carousel">
          <button type="button" class="arrow" :disabled="clearMaps.length <= 1" @click="moveMap(-1)">‹</button>
          <article class="clear-card" @click="router.push({ name: 'EpisodeClearReport', params: { episodeId: activeMap.episodeId } })">
            <small>{{ activeMap.regionName || 'CLEAR MAP' }}</small>
            <strong>{{ activeMap.title }}</strong>
            <p>{{ activeMap.subtitle || '클리어한 미션 기록입니다.' }}</p>
            <div class="clear-meta">
              <span>{{ activeMap.difficulty || '난이도' }}</span>
              <span>{{ activeMap.score ?? 0 }}점</span>
              <span>{{ mapIndex + 1 }} / {{ clearMaps.length }}</span>
            </div>
          </article>
          <button type="button" class="arrow" :disabled="clearMaps.length <= 1" @click="moveMap(1)">›</button>
        </div>
        <article v-else class="empty-card">아직 클리어한 맵이 없습니다.</article>
      </section>
    </template>

    <div v-if="followModalOpen" class="modal-backdrop" @click.self="closeFollowModal">
      <section class="follow-modal" role="dialog" aria-modal="true" :aria-label="followModalTitle">
        <button type="button" class="modal-close" aria-label="닫기" @click="closeFollowModal">×</button>
        <h2>{{ followModalTitle }}</h2>
        <p v-if="followLoading" class="modal-state">목록을 불러오는 중입니다.</p>
        <p v-else-if="followError" class="modal-state error">{{ followError }}</p>
        <p v-else-if="!followUsers.length" class="modal-state">목록이 없습니다.</p>
        <div v-else class="follow-list">
          <article v-for="user in followUsers" :key="user.userId" @click="openUserFeed(user.userId)">
            <img :src="user.profileImageUrl || defaultProfile" alt="" />
            <div>
              <strong>{{ user.nickname || '유저' }}</strong>
              <span>팔로워 {{ user.followerCount || 0 }} · 팔로잉 {{ user.followingCount || 0 }}</span>
            </div>
            <button type="button" class="follow-toggle" @click.stop="toggleFollowUser(user)">
              {{ user.following ? '팔로우 해제' : '팔로우 하기' }}
            </button>
          </article>
        </div>
      </section>
    </div>

    <MainBottomNav />
  </main>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import MainBottomNav from '@/components/MainBottomNav.vue';
import { feedApi } from '@/api/feedApi';
import { followApi } from '@/api/followApi';

const router = useRouter();
const route = useRoute();
const loading = ref(true);
const error = ref('');
const profile = ref({});
const analysis = ref(null);
const community = ref({ items: [], page: 0, size: 5, totalItems: 0, totalPages: 0 });
const clearMaps = ref([]);
const page = ref(0);
const mapIndex = ref(0);
const privateProfile = ref(false);
const privateMessage = ref('');
const followModalOpen = ref(false);
const followModalType = ref('followers');
const followUsers = ref([]);
const followLoading = ref(false);
const followError = ref('');
const defaultProfile = 'data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 width=%2264%22 height=%2264%22 viewBox=%220 0 64 64%22%3E%3Crect width=%2264%22 height=%2264%22 rx=%2218%22 fill=%22%23f1eafd%22/%3E%3Ccircle cx=%2232%22 cy=%2225%22 r=%2211%22 fill=%22%238b66c9%22/%3E%3Cpath d=%22M14 56c3-12 12-18 18-18s15 6 18 18%22 fill=%22%238b66c9%22/%3E%3C/svg%3E';

const isOwnFeed = computed(() => !route.params.userId);
const profileImage = computed(() => profile.value.profileImageUrl || '');
const activeMap = computed(() => clearMaps.value[mapIndex.value] || null);
const followModalTitle = computed(() => followModalType.value === 'followers' ? '팔로워 목록' : '팔로잉 목록');
const pageNumbers = computed(() => {
  const total = community.value.totalPages || 0;
  return Array.from({ length: total }, (_, index) => index + 1);
});

onMounted(loadFeed);

watch(() => route.params.userId, () => {
  page.value = 0;
  mapIndex.value = 0;
  closeFollowModal();
  loadFeed();
});

async function loadFeed() {
  loading.value = true;
  error.value = '';
  try {
    const targetUserId = route.params.userId;
    const data = targetUserId
      ? await feedApi.getUserFeed(targetUserId, { page: page.value, size: 5 })
      : await feedApi.getMyFeed({ page: page.value, size: 5 });
    privateProfile.value = Boolean(data.privateProfile);
    privateMessage.value = data.message || '프로필 정보가 비공개된 사용자입니다.';
    if (privateProfile.value) {
      profile.value = data.profile || {};
      return;
    }
    profile.value = data.profile || {};
    analysis.value = data.playerAnalysis || null;
    community.value = data.communityPosts || { items: [], page: page.value, size: 5, totalItems: 0, totalPages: 0 };
    page.value = community.value.page || 0;
    clearMaps.value = data.clearMaps || [];
    if (mapIndex.value >= clearMaps.value.length) mapIndex.value = 0;
  } catch (err) {
    error.value = err.userMessage || '피드를 불러오지 못했습니다.';
  } finally {
    loading.value = false;
  }
}

function reload() {
  loadFeed();
}

function changePage(nextPage) {
  if (nextPage < 0 || nextPage >= (community.value.totalPages || 0)) return;
  page.value = nextPage;
  loadFeed();
}

function moveMap(delta) {
  if (!clearMaps.value.length) return;
  mapIndex.value = (mapIndex.value + delta + clearMaps.value.length) % clearMaps.value.length;
}

function openPost(post) {
  if (!post?.regionId || !post?.id) return;
  router.push({ name: 'CommunityPostDetail', params: { regionId: post.regionId, questionId: post.id } });
}

async function openFollowModal(type) {
  if (!isOwnFeed.value) return;
  followModalType.value = type;
  followModalOpen.value = true;
  followLoading.value = true;
  followError.value = '';
  followUsers.value = [];
  try {
    followUsers.value = type === 'followers'
      ? await followApi.getFollowers()
      : await followApi.getFollowing();
  } catch (err) {
    followError.value = err.userMessage || '목록을 불러오지 못했습니다.';
  } finally {
    followLoading.value = false;
  }
}

async function toggleFollowUser(user) {
  if (!user?.userId) return;
  try {
    const updated = user.following
      ? await followApi.unfollowUser(user.userId)
      : await followApi.followUser(user.userId);
    user.following = Boolean(updated.following);
    user.followerCount = updated.followerCount ?? user.followerCount;
    user.followingCount = updated.followingCount ?? user.followingCount;
    await loadFeed();
  } catch (err) {
    followError.value = err.userMessage || '팔로우 상태를 변경하지 못했습니다.';
  }
}

function closeFollowModal() {
  followModalOpen.value = false;
}

function openUserFeed(userId) {
  if (!userId) return;
  closeFollowModal();
  router.push({ name: 'UserFeed', params: { userId } });
}
</script>

<style scoped>
.feed-page { min-height: 100vh; box-sizing: border-box; padding: 18px 14px 126px; background: #fbf8ff; color: #1f1f29; font-family: 'Noto Sans KR', system-ui, sans-serif; }
.profile-panel, .section-block, .state { width: min(100%, 860px); box-sizing: border-box; margin: 0 auto; }
.profile-panel { display: grid; grid-template-columns: 140px 1fr; gap: 20px 26px; align-items: center; padding: 28px 34px 24px; border: 3px solid #a77bd8; border-bottom-width: 2px; border-radius: 18px 18px 0 0; background: #fff; }
.avatar { width: 126px; aspect-ratio: 1; display: grid; place-items: center; border: 2px solid #b99be0; border-radius: 50%; background: linear-gradient(145deg, #fff, #f1eafd); color: #7c56bc; font-weight: 900; overflow: hidden; }
.avatar img { width: 100%; height: 100%; object-fit: cover; }
.profile-copy h1 { margin: 0; font-size: clamp(2rem, 7vw, 3.2rem); line-height: 1.05; letter-spacing: 0; }
.profile-copy p { margin: 12px 0 0; color: #686274; font-size: 1.25rem; font-weight: 800; }
.profile-stats { grid-column: 2; display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; }
.stat-card { min-height: 54px; display: grid; grid-template-columns: auto 1fr; gap: 8px; align-items: center; justify-content: center; padding: 0 14px; border: 2px solid #c2a8e6; border-radius: 12px; background: #fbf8ff; color: #4d3c69; font: inherit; cursor: pointer; }
.stat-card:disabled { cursor: default; opacity: 1; }
.profile-stats strong { color: #8b66c9; font-size: 1.1rem; }
.profile-stats span { font-weight: 900; white-space: nowrap; }
.section-block { padding: 28px 34px; border-left: 3px solid #a77bd8; border-right: 3px solid #a77bd8; background: #fff; }
.section-block:last-of-type { border-bottom: 3px solid #a77bd8; border-radius: 0 0 18px 18px; }
.section-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 16px; }
.section-head h2 { margin: 0; font-size: 1.75rem; letter-spacing: 0; }
.section-head span { color: #7b6f8e; font-weight: 900; }
.section-head button, .pager button, .arrow { min-height: 42px; border: 2px solid #c2a8e6; border-radius: 12px; background: #fbf8ff; color: #513875; font: inherit; font-weight: 900; padding: 0 16px; cursor: pointer; }
.analysis-card, .empty-card, .post-card, .clear-card { border: 2px solid #c2a8e6; border-radius: 18px; background: linear-gradient(145deg, #fff, #faf6ff); }
.analysis-card { min-height: 220px; padding: 24px; display: grid; gap: 18px; }
.analysis-title span { display: block; color: #8b66c9; font-size: .78rem; font-weight: 1000; letter-spacing: .12em; }
.analysis-title strong { display: block; margin-top: 4px; font-size: 1.75rem; }
.analysis-card p { margin: 0; color: #67bed9; font-weight: 800; line-height: 1.55; }
.mbti-grid { display: grid; gap: 15px; padding: 18px; border-radius: 14px; background: #151f31; color: #f8fafc; }
.mbti-grid h3 { margin: 0 0 2px; color: #c7f2ff; font-family: Georgia, 'Noto Sans KR', serif; font-size: 1.05rem; letter-spacing: 0; }
.mbti-row { display: grid; gap: 8px; }
.dimension-title { font-size: 1.02rem; color: #ffffff; }
.mbti-label { display: flex; justify-content: space-between; gap: 10px; color: #dbe4f0; font-size: .9rem; font-weight: 900; }
.bar { height: 12px; border-radius: 999px; background: #46536a; overflow: hidden; }
.bar i { display: block; height: 100%; border-radius: inherit; background: linear-gradient(90deg, #44c8f5 0%, #8ed3b6 48%, #ffd21f 100%); }
.empty-card { min-height: 112px; display: grid; place-items: center; color: #6f657d; font-weight: 900; text-align: center; padding: 20px; }
.post-list { display: grid; gap: 10px; }
.post-card { display: grid; gap: 12px; padding: 16px 18px; cursor: pointer; }
.post-card span { color: #8b66c9; font-size: .8rem; font-weight: 1000; }
.post-card h3 { margin: 4px 0 6px; font-size: 1.05rem; }
.post-card p { margin: 0; color: #67bed9; line-height: 1.45; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.post-card footer { display: flex; gap: 10px; }
.pager { display: flex; justify-content: center; gap: 12px; margin-top: 18px; flex-wrap: wrap; }
.pager button { min-width: 42px; padding: 0 10px; border-radius: 999px; }
.pager button.active { background: #e6d8fb; color: #3f2b5e; }
.pager button:disabled, .arrow:disabled { opacity: .42; cursor: default; }
.clear-carousel { display: grid; grid-template-columns: 64px minmax(0, 380px) 64px; justify-content: center; align-items: center; gap: 24px; }
.arrow { min-height: 64px; border: 0; background: transparent; color: #8b66c9; font-size: 4rem; line-height: 1; padding: 0; }
.clear-card { min-height: 178px; display: grid; align-content: center; justify-items: center; gap: 10px; padding: 24px; text-align: center; cursor: pointer; }
.clear-card small { color: #8b66c9; font-weight: 1000; }
.clear-card strong { font-size: 1.55rem; }
.clear-card p { margin: 0; color: #6f657d; line-height: 1.45; }
.clear-meta { display: flex; gap: 8px; flex-wrap: wrap; justify-content: center; }
.clear-meta span { padding: 5px 9px; border-radius: 999px; background: #f0e8fb; color: #63458e; font-size: .8rem; font-weight: 900; }
.state { margin-top: 40px; padding: 20px; border: 2px dashed #c2a8e6; border-radius: 18px; background: #fff; color: #6f657d; text-align: center; font-weight: 900; }
.state.error { color: #991b1b; background: #fff1f2; }
.modal-backdrop { position: fixed; inset: 0; z-index: 80; display: grid; place-items: center; padding: 18px; background: rgba(31,31,41,.48); backdrop-filter: blur(8px); }
.follow-modal { position: relative; width: min(420px, 100%); max-height: min(680px, calc(100vh - 40px)); overflow: auto; box-sizing: border-box; padding: 22px; border: 2px solid #c2a8e6; border-radius: 18px; background: #fff; box-shadow: 0 24px 70px rgba(31,31,41,.28); }
.follow-modal h2 { margin: 0 42px 16px 0; }
.modal-close { position: absolute; top: 14px; right: 14px; width: 34px; min-height: 34px; border: 0; border-radius: 50%; background: #f0e8fb; color: #513875; font-size: 1.2rem; font-weight: 900; }
.modal-state { margin: 0; padding: 16px; border-radius: 12px; background: #fbf8ff; color: #6f657d; text-align: center; font-weight: 900; }
.modal-state.error { color: #991b1b; background: #fff1f2; }
.follow-list { display: grid; gap: 10px; }
.follow-list article { display: grid; grid-template-columns: 48px 1fr auto; gap: 12px; align-items: center; padding: 10px; border: 1px solid #e5d8f8; border-radius: 12px; background: #fbf8ff; cursor: pointer; }
.follow-list img { width: 48px; height: 48px; border-radius: 14px; object-fit: cover; background: #f1eafd; }
.follow-list strong { display: block; color: #2a2433; }
.follow-list span { color: #6f657d; font-size: .84rem; font-weight: 800; }
.follow-toggle { min-height: 36px; border: 0; border-radius: 10px; background: #8b66c9; color: #fff; font: inherit; font-size: .82rem; font-weight: 900; padding: 0 10px; }
@media (max-width: 720px) {
  .profile-panel { grid-template-columns: 1fr; justify-items: center; text-align: center; padding: 24px 18px; }
  .profile-stats { grid-column: auto; width: 100%; grid-template-columns: 1fr; }
  .section-block { padding: 24px 18px; }
  .section-head { align-items: flex-start; flex-direction: column; }
  .mbti-label { display: grid; }
  .clear-carousel { grid-template-columns: 44px minmax(0, 1fr) 44px; gap: 8px; }
  .arrow { font-size: 3rem; }
  .follow-list article { grid-template-columns: 48px 1fr; }
  .follow-toggle { grid-column: 1 / -1; }
}
</style>
