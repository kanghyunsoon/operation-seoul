<template>
  <nav class="main-bottom-nav" aria-label="주요 메뉴">
    <button
      v-for="item in items"
      :key="item.name"
      type="button"
      :class="{ active: route.name === item.name }"
      @click="router.push({ name: item.name })"
    >
      <span class="tab-mark">{{ item.mark }}</span>
      <span>{{ item.label }}</span>
    </button>
  </nav>
</template>

<script setup>
import { useRoute, useRouter } from 'vue-router';

const route = useRoute();
const router = useRouter();

const items = [
  { name: 'EpisodeList', label: '홈', mark: 'H' },
  { name: 'Challenges', label: '챌린지', mark: 'C' },
  { name: 'Ranking', label: '랭킹', mark: 'R' },
  { name: 'CommunityHub', label: '커뮤니티', mark: 'Q' },
  { name: 'MyPage', label: '내 정보', mark: 'M' }
];
</script>

<style scoped>
.main-bottom-nav {
  position: fixed;
  left: 50%;
  bottom: max(12px, env(safe-area-inset-bottom));
  z-index: 35;
  width: min(720px, calc(100vw - 24px));
  height: 72px;
  transform: translateX(-50%);
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  align-items: stretch;
  padding: 7px;
  border: 1px solid rgba(248,250,252,.14);
  border-radius: 18px;
  background: rgba(15,23,42,.96);
  box-shadow: 0 20px 52px rgba(0,0,0,.42);
  backdrop-filter: blur(14px);
}
.main-bottom-nav button {
  min-height: 0;
  display: grid;
  place-items: center;
  gap: 3px;
  padding: 6px 3px;
  border: 0;
  border-radius: 12px;
  background: transparent;
  color: #cbd5e1;
  font-family: 'Noto Sans KR', system-ui, sans-serif;
  font-size: .7rem;
  font-weight: 900;
  line-height: 1.15;
}
.main-bottom-nav button.active,
.main-bottom-nav button:hover {
  background: rgba(180,83,9,.22);
  color: #fff7ed;
}
.tab-mark {
  width: 24px;
  height: 24px;
  display: grid;
  place-items: center;
  border: 1px solid rgba(251,191,36,.45);
  border-radius: 50%;
  color: #fde68a;
  font-size: .72rem;
  font-weight: 1000;
}
@media (max-width: 560px) {
  .main-bottom-nav {
    width: calc(100vw - 16px);
    height: 70px;
    bottom: max(8px, env(safe-area-inset-bottom));
  }
  .main-bottom-nav button { font-size: .66rem; }
  .tab-mark { width: 22px; height: 22px; }
}
</style>
