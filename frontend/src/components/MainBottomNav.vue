<template>
  <nav class="main-bottom-nav" aria-label="주요 메뉴">
    <button
      v-for="item in items"
      :key="item.name"
      type="button"
      :class="{ active: route.name === item.name }"
      @click="router.push({ name: item.name })"
    >
      <span class="tab-icon" aria-hidden="true">
        <svg viewBox="0 0 24 24" focusable="false">
          <path
            v-for="path in item.icon"
            :key="path"
            :d="path"
          />
        </svg>
      </span>
      <span>{{ item.label }}</span>
    </button>
  </nav>
</template>

<script setup>
import { useRoute, useRouter } from 'vue-router';

const route = useRoute();
const router = useRouter();

const icons = {
  home: [
    'M3 10.8 12 3l9 7.8v9.7a.5.5 0 0 1-.5.5H15v-6H9v6H3.5a.5.5 0 0 1-.5-.5v-9.7Z'
  ],
  trophy: [
    'M8 4h8v3a4 4 0 0 1-8 0V4Z',
    'M6 5H3v2a4 4 0 0 0 4 4h1',
    'M18 5h3v2a4 4 0 0 1-4 4h-1',
    'M12 11v5',
    'M9 21h6',
    'M10 16h4v5h-4v-5Z'
  ],
  feed: [
    'M5 4h14a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1Z',
    'M8 8h8',
    'M8 12h8',
    'M8 16h5'
  ],
  community: [
    'M4 6.5A2.5 2.5 0 0 1 6.5 4h11A2.5 2.5 0 0 1 20 6.5v6A2.5 2.5 0 0 1 17.5 15H11l-5 4v-4.2A2.5 2.5 0 0 1 4 12.5v-6Z',
    'M8 8h8',
    'M8 11h5'
  ],
  user: [
    'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8Z',
    'M4 21a8 8 0 0 1 16 0'
  ]
};

const items = [
  { name: 'EpisodeList', label: '홈', icon: icons.home },
  { name: 'Challenges', label: '챌린지', icon: icons.trophy },
  { name: 'Feed', label: '피드', icon: icons.feed },
  { name: 'CommunityHub', label: '커뮤니티', icon: icons.community },
  { name: 'MyPage', label: '내 정보', icon: icons.user }
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
.tab-icon {
  width: 25px;
  height: 25px;
  display: grid;
  place-items: center;
  color: #fde68a;
}
.tab-icon svg {
  width: 100%;
  height: 100%;
  display: block;
  fill: none;
  stroke: currentColor;
  stroke-width: 2.1;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.tab-icon svg path:first-child {
  fill: color-mix(in srgb, currentColor 14%, transparent);
}
@media (max-width: 560px) {
  .main-bottom-nav {
    width: calc(100vw - 16px);
    height: 70px;
    bottom: max(8px, env(safe-area-inset-bottom));
  }
  .main-bottom-nav button { font-size: .66rem; }
  .tab-icon { width: 23px; height: 23px; }
}
</style>
