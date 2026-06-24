<template>
  <main class="mypage">
    <header class="hero">
      <button type="button" class="back" @click="router.push({ name: 'EpisodeList' })">미션 파일 목록</button>
      <p>MY FILES</p>
      <h1>내 정보</h1>
      <span>내 관심목록, 팔로우, 활동 정보를 확인합니다.</span>
    </header>

    <p v-if="message" class="toast" :class="messageType">{{ message }}</p>
    <section class="profile-panel">
      <article>
        <p>PROFILE</p>
        <h2>내 정보 수정</h2>
        <span>닉네임과 계정 정보는 프로필 설정에서 관리합니다.</span>
        <button type="button" @click="setMessage('프로필 수정 화면은 준비 중입니다.')">프로필 설정</button>
      </article>
      <article>
        <p>LIKES</p>
        <h2>내가 한 좋아요</h2>
        <span>{{ favorites.length }}개의 미션 파일을 관심목록에 담았습니다.</span>
        <button type="button" @click="router.push({ name: 'Favorites' })">관심목록 보기</button>
      </article>
      <article>
        <p>REVIEWS</p>
        <h2>내가 쓴 리뷰</h2>
        <span>{{ myReviews.length }}개의 리뷰를 작성했습니다.</span>
        <button type="button" @click="scrollToReviews">리뷰 보기</button>
      </article>
    </section>

    <section v-if="loading" class="state">관심 에피소드 목록을 불러오는 중입니다.</section>
    <section v-else-if="error" class="state error">{{ error }}</section>
    <section v-else-if="!favorites.length" class="state">아직 관심 에피소드가 없습니다.</section>
    <section v-else class="favorite-list">
      <article v-for="item in favorites" :key="item.favoriteId || item.episodeId" class="favorite-card">
        <div class="card-head">
          <span>CASE {{ String(item.episodeId).padStart(2, '0') }}</span>
          <button type="button" :disabled="busyId === item.episodeId" @click="removeFavorite(item)">관심 해제</button>
        </div>
        <h2>{{ item.title }}</h2>
        <p>{{ item.subtitle }}</p>
        <div class="meta">
          <span>{{ item.era }}</span>
          <span>{{ item.genre }}</span>
          <span>{{ item.difficulty }}</span>
        </div>
        <button type="button" class="open" @click="router.push({ name: 'EpisodeDetail', params: { episodeId: item.episodeId } })">상세 보기</button>
      </article>
    </section>

    <section ref="reviewsSection" class="review-panel">
      <div class="social-head">
        <div>
          <p>REVIEWS</p>
          <h2>내가 쓴 리뷰</h2>
        </div>
        <button type="button" @click="loadMyReviews">새로고침</button>
      </div>
      <p v-if="reviewLoading" class="empty-social">리뷰를 불러오는 중입니다.</p>
      <p v-else-if="!myReviews.length" class="empty-social">아직 작성한 리뷰가 없습니다.</p>
      <article v-for="review in myReviews" :key="review.id" class="review-card">
        <strong>{{ review.episodeTitle || review.title || '미션 리뷰' }}</strong>
        <span>별점 {{ review.rating || 0 }} · 난이도 {{ review.difficultyRating || '-' }}</span>
        <p>{{ review.content }}</p>
      </article>
    </section>

    <section class="social-panel">
      <div class="social-head">
        <div>
          <p>SOCIAL</p>
          <h2>팔로우</h2>
        </div>
        <button type="button" @click="loadSocial">새로고침</button>
      </div>
      <div class="social-grid">
        <article>
          <h3>팔로잉 {{ following.length }}</h3>
          <p v-if="!following.length" class="empty-social">아직 팔로우한 요원이 없습니다.</p>
          <div v-for="user in following" :key="`following-${user.userId}`" class="user-row">
            <img :src="user.profileImageUrl || defaultProfile" alt="" />
            <div>
              <strong>{{ user.nickname || '요원' }}</strong>
              <small>팔로워 {{ user.followerCount || 0 }} · 팔로잉 {{ user.followingCount || 0 }}</small>
            </div>
            <button type="button" @click="unfollow(user)">해제</button>
          </div>
        </article>
        <article>
          <h3>팔로워 {{ followers.length }}</h3>
          <p v-if="!followers.length" class="empty-social">아직 나를 팔로우한 요원이 없습니다.</p>
          <div v-for="user in followers" :key="`follower-${user.userId}`" class="user-row">
            <img :src="user.profileImageUrl || defaultProfile" alt="" />
            <div>
              <strong>{{ user.nickname || '요원' }}</strong>
              <small>팔로워 {{ user.followerCount || 0 }} · 팔로잉 {{ user.followingCount || 0 }}</small>
            </div>
            <button type="button" @click="user.following ? unfollow(user) : follow(user)">
              {{ user.following ? '해제' : '팔로우' }}
            </button>
          </div>
        </article>
      </div>
    </section>
    <MainBottomNav />
  </main>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import MainBottomNav from '@/components/MainBottomNav.vue';
import { favoriteApi } from '@/api/favoriteApi';
import { followApi } from '@/api/followApi';
import { reviewApi } from '@/api/reviewApi';

const router = useRouter();
const favorites = ref([]);
const following = ref([]);
const followers = ref([]);
const myReviews = ref([]);
const loading = ref(true);
const reviewLoading = ref(true);
const error = ref('');
const message = ref('');
const messageType = ref('success');
const busyId = ref(null);
const reviewsSection = ref(null);
const defaultProfile = 'data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 width=%2264%22 height=%2264%22 viewBox=%220 0 64 64%22%3E%3Crect width=%2264%22 height=%2264%22 rx=%2218%22 fill=%22%23334155%22/%3E%3Ccircle cx=%2232%22 cy=%2225%22 r=%2211%22 fill=%22%23cbd5e1%22/%3E%3Cpath d=%22M14 56c3-12 12-18 18-18s15 6 18 18%22 fill=%22%23cbd5e1%22/%3E%3C/svg%3E';

onMounted(async () => {
  await Promise.all([loadFavorites(), loadSocial(), loadMyReviews()]);
});

async function loadFavorites() {
  loading.value = true;
  error.value = '';
  try {
    favorites.value = await favoriteApi.getMyFavorites();
  } catch (err) {
    error.value = err.userMessage || '관심 에피소드 목록을 불러오지 못했습니다.';
  } finally {
    loading.value = false;
  }
}

async function loadSocial() {
  try {
    [following.value, followers.value] = await Promise.all([
      followApi.getFollowing(),
      followApi.getFollowers()
    ]);
  } catch (err) {
    setMessage(err.userMessage || '팔로우 목록을 불러오지 못했습니다.', 'error');
  }
}

async function loadMyReviews() {
  reviewLoading.value = true;
  try {
    myReviews.value = await reviewApi.getMyReviews();
  } catch (err) {
    setMessage(err.userMessage || '내 리뷰 목록을 불러오지 못했습니다.', 'error');
  } finally {
    reviewLoading.value = false;
  }
}

function scrollToReviews() {
  reviewsSection.value?.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

async function follow(user) {
  try {
    await followApi.followUser(user.userId);
    setMessage(`${user.nickname || '요원'}님을 팔로우했습니다.`);
    await loadSocial();
  } catch (err) {
    setMessage(err.userMessage || '팔로우하지 못했습니다.', 'error');
  }
}

async function unfollow(user) {
  try {
    await followApi.unfollowUser(user.userId);
    setMessage(`${user.nickname || '요원'}님 팔로우를 해제했습니다.`);
    await loadSocial();
  } catch (err) {
    setMessage(err.userMessage || '팔로우를 해제하지 못했습니다.', 'error');
  }
}

async function removeFavorite(item) {
  busyId.value = item.episodeId;
  try {
    await favoriteApi.removeFavorite(item.episodeId);
    favorites.value = favorites.value.filter((favorite) => favorite.episodeId !== item.episodeId);
    setMessage('관심 에피소드에서 제거했습니다.');
  } catch (err) {
    setMessage(err.userMessage || '관심 에피소드에서 제거하지 못했습니다.', 'error');
  } finally {
    busyId.value = null;
  }
}

function setMessage(text, type = 'success') {
  message.value = text;
  messageType.value = type;
}
</script>

<style scoped>
.mypage { min-height: 100vh; box-sizing: border-box; padding: 24px 16px 126px; background: radial-gradient(circle at 80% 0%, rgba(245,158,11,.2), transparent 34%), linear-gradient(160deg, #0f172a, #111827 60%, #050505); color: #f8fafc; font-family: Georgia, 'Noto Sans KR', serif; }
.hero, .profile-panel, .favorite-list, .state, .toast, .social-panel, .review-panel { width: min(100%, 880px); box-sizing: border-box; margin-left: auto; margin-right: auto; }
.hero { margin-bottom: 18px; padding: 22px; border: 1px solid rgba(245,158,11,.24); border-radius: 20px; background: rgba(15,23,42,.58); }
.back { min-height: 38px; border: 1px solid rgba(148,163,184,.28); border-radius: 999px; background: transparent; color: #cbd5e1; padding: 0 14px; }
.hero p { margin: 18px 0 8px; color: #f59e0b; font-weight: 900; letter-spacing: .16em; font-size: .78rem; }
h1 { margin: 0; font-size: clamp(2rem, 9vw, 3.5rem); line-height: 1; }
.hero span { display: block; margin-top: 10px; color: #cbd5e1; }
.profile-panel { display: grid; grid-template-columns: repeat(3, minmax(0,1fr)); gap: 12px; margin-bottom: 18px; }
.profile-panel article { padding: 16px; border: 1px solid rgba(248,250,252,.13); border-radius: 16px; background: rgba(15,23,42,.62); }
.profile-panel p { margin: 0 0 8px; color: #f59e0b; font-size: .72rem; font-weight: 900; letter-spacing: .14em; }
.profile-panel h2 { margin: 0 0 8px; font-size: 1.1rem; }
.profile-panel span { display: block; min-height: 44px; color: #cbd5e1; line-height: 1.45; }
.profile-panel button { width: 100%; margin-top: 12px; }
.favorite-list { display: grid; gap: 14px; }
.favorite-card { padding: 20px; border-radius: 18px; border: 1px solid rgba(248,250,252,.13); background: linear-gradient(135deg, rgba(255,247,237,.09), rgba(15,23,42,.78)); box-shadow: 0 20px 52px rgba(0,0,0,.22); }
.card-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.card-head span { color: #fca5a5; font-size: .72rem; font-weight: 900; letter-spacing: .1em; }
h2 { margin: 14px 0 8px; font-size: 1.35rem; }
p { color: #cbd5e1; line-height: 1.55; }
.meta { display: flex; flex-wrap: wrap; gap: 7px; margin: 14px 0; }
.meta span { border: 1px solid rgba(245,158,11,.28); border-radius: 999px; padding: 5px 9px; color: #fde68a; font-size: .78rem; }
button { min-height: 40px; border: 0; border-radius: 12px; background: #334155; color: #fff; font: inherit; font-weight: 900; padding: 0 14px; }
.open { width: 100%; background: #b45309; }
.state, .toast { padding: 16px; border: 1px dashed rgba(148,163,184,.34); border-radius: 16px; color: #cbd5e1; text-align: center; }
.toast { margin-bottom: 12px; border-style: solid; background: rgba(22,101,52,.18); color: #bbf7d0; }
.toast.error, .state.error { color: #fecaca; background: rgba(127,29,29,.18); }
.social-panel, .review-panel { margin-top: 18px; padding: 18px; border: 1px solid rgba(125,211,252,.2); border-radius: 20px; background: rgba(15,23,42,.62); box-shadow: 0 20px 52px rgba(0,0,0,.18); }
.social-head { display: flex; justify-content: space-between; align-items: end; gap: 12px; margin-bottom: 12px; }
.social-head p { margin: 0 0 6px; color: #67e8f9; font-size: .74rem; font-weight: 900; letter-spacing: .14em; }
.social-head h2 { margin: 0; }
.social-grid { display: grid; grid-template-columns: repeat(2, minmax(0,1fr)); gap: 12px; }
.social-grid article { padding: 14px; border: 1px solid rgba(148,163,184,.16); border-radius: 16px; background: rgba(2,6,23,.34); }
.social-grid h3 { margin: 0 0 10px; color: #fde68a; }
.user-row { display: grid; grid-template-columns: 42px 1fr auto; align-items: center; gap: 10px; padding: 10px 0; border-top: 1px solid rgba(148,163,184,.14); }
.user-row:first-of-type { border-top: 0; }
.user-row img { width: 42px; height: 42px; border-radius: 14px; object-fit: cover; background: #334155; }
.user-row strong { display: block; }
.user-row small, .empty-social { color: #94a3b8; }
.user-row button { min-height: 34px; padding: 0 10px; background: #0e7490; }
.review-card { padding: 14px 0; border-top: 1px solid rgba(148,163,184,.14); }
.review-card:first-of-type { border-top: 0; }
.review-card strong { display: block; color: #fde68a; }
.review-card span { display: block; margin-top: 4px; color: #94a3b8; font-size: .86rem; }
.review-card p { margin: 8px 0 0; white-space: pre-wrap; }
@media (max-width: 560px) {
  .profile-panel { grid-template-columns: 1fr; }
  .card-head { display: grid; align-items: stretch; }
  .card-head button { width: 100%; }
  .social-head { display: block; }
  .social-grid { grid-template-columns: 1fr; }
  .user-row { grid-template-columns: 42px 1fr; }
  .user-row button { grid-column: 1 / -1; }
}
</style>
