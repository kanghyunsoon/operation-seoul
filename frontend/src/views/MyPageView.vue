<template>
  <main class="mypage">
    <header class="hero">
      <button type="button" class="back" @click="router.push({ name: 'EpisodeList' })">사건파일 목록</button>
      <p>MY FILES</p>
      <h1>내 관심 에피소드</h1>
      <span>나중에 플레이할 사건파일을 모아볼 수 있습니다.</span>
    </header>

    <p v-if="message" class="toast" :class="messageType">{{ message }}</p>
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
  </main>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { favoriteApi } from '@/api/favoriteApi';

const router = useRouter();
const favorites = ref([]);
const loading = ref(true);
const error = ref('');
const message = ref('');
const messageType = ref('success');
const busyId = ref(null);

onMounted(loadFavorites);

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
.mypage { min-height: 100vh; box-sizing: border-box; padding: 24px 16px 64px; background: radial-gradient(circle at 80% 0%, rgba(245,158,11,.2), transparent 34%), linear-gradient(160deg, #0f172a, #111827 60%, #050505); color: #f8fafc; font-family: Georgia, 'Noto Sans KR', serif; }
.hero, .favorite-list, .state, .toast { width: min(100%, 880px); box-sizing: border-box; margin-left: auto; margin-right: auto; }
.hero { margin-bottom: 18px; padding: 22px; border: 1px solid rgba(245,158,11,.24); border-radius: 20px; background: rgba(15,23,42,.58); }
.back { min-height: 38px; border: 1px solid rgba(148,163,184,.28); border-radius: 999px; background: transparent; color: #cbd5e1; padding: 0 14px; }
.hero p { margin: 18px 0 8px; color: #f59e0b; font-weight: 900; letter-spacing: .16em; font-size: .78rem; }
h1 { margin: 0; font-size: clamp(2rem, 9vw, 3.5rem); line-height: 1; }
.hero span { display: block; margin-top: 10px; color: #cbd5e1; }
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
@media (max-width: 560px) {
  .card-head { display: grid; align-items: stretch; }
  .card-head button { width: 100%; }
}
</style>
