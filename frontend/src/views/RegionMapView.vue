<template>
  <main class="region-page">
    <header class="hero">
      <div>
        <p>REGION SELECT</p>
        <h1>어느 권역을 조사할까요?</h1>
        <span>{{ sessionStore.currentUser?.nickname || '요원' }}님, 플레이할 미션 파일의 지역을 선택하세요.</span>
      </div>
      <div class="hero-actions">
        <button type="button" @click="router.push({ name: 'EpisodeList' })">전체 사건 보기</button>
        <button type="button" class="danger" @click="logout">로그아웃</button>
      </div>
    </header>

    <section class="region-shell">
      <div class="map-card">
        
        <svg :viewBox="regionMapMeta.viewBox" role="img" aria-label="대한민국 권역 선택 지도">
          <defs>
            <filter id="shadow" x="-20%" y="-20%" width="140%" height="140%">
              <feDropShadow dx="0" dy="8" stdDeviation="8" flood-color="#020617" flood-opacity="0.35" />
            </filter>
          </defs>
          <g :transform="regionMapMeta.transform">
            <path
              v-for="area in regionAreas"
              :key="area.code"
              :d="area.path"
              class="region-path"
              :class="{
                hovered: hoveredAreaCode === area.code,
                active: activeAreaCode === area.code
              }"
              :fill="area.color"
              :aria-label="`${area.label} 선택`"
              role="button"
              tabindex="0"
              @mouseenter="hoveredAreaCode = area.code"
              @mouseleave="hoveredAreaCode = ''"
              @focus="hoveredAreaCode = area.code"
              @blur="hoveredAreaCode = ''"
              @click="openRegion(area.code)"
              @keydown.enter.prevent="openRegion(area.code)"
              @keydown.space.prevent="openRegion(area.code)"
            />
            <g class="region-labels" aria-hidden="true">
              <text
                v-for="area in regionAreas"
                :key="`${area.code}-label`"
                :x="area.labelX"
                :y="area.labelY"
                :font-size="area.labelSize || 64"
                text-anchor="start"
              >
                {{ area.label }}
              </text>
            </g>
          </g>
        </svg>
        <p class="map-note">
          권역을 선택하면 해당 권역에 공개된 미션 파일 목록으로 이동합니다.
        </p>
      </div>
    </section>
  </main>
</template>

<script setup>
import { computed, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useSessionStore } from '@/stores/sessionStore.js';
import { regionAreas, regionMapMeta } from '@/constants/regionAreas.js';

const router = useRouter();
const route = useRoute();
const sessionStore = useSessionStore();
const hoveredAreaCode = ref('');
const selectedAreaCode = ref('');
const activeAreaCode = computed(() => selectedAreaCode.value || route.query.areaCode || '');

function openRegion(areaCode) {
  selectedAreaCode.value = areaCode;
  router.push({ name: 'EpisodeList', query: { areaCode } });
}

function logout() {
  sessionStore.logout();
  router.push({ name: 'Intro' });
}
</script>

<style scoped>
.region-page { height: 100dvh; box-sizing: border-box; display: flex; flex-direction: column; gap: 12px; padding: 14px 16px calc(92px + env(safe-area-inset-bottom)); overflow: hidden; background: radial-gradient(circle at 18% 8%, rgba(14,165,233,.24), transparent 30%), linear-gradient(155deg, #101827, #0f172a 56%, #020617); color: #f8fafc; font-family: 'Noto Sans KR', Georgia, serif; }
.hero, .region-shell { width: min(100%, 1120px); margin: 0 auto; }
.hero { flex: 0 0 auto; display: flex; align-items: end; justify-content: space-between; gap: 16px; margin-bottom: 0; padding: 14px 18px; border: 1px solid rgba(125,211,252,.22); border-radius: 18px; background: rgba(15,23,42,.72); }
.hero p { margin: 0 0 8px; color: #67e8f9; font-size: .76rem; font-weight: 900; letter-spacing: .16em; }
h1 { margin: 0; font-size: clamp(1.7rem, 5vw, 3.1rem); line-height: .98; }
.hero span { display: block; margin-top: 8px; color: #cbd5e1; }
.hero-actions { display: flex; flex-wrap: wrap; gap: 8px; }
button { min-height: 42px; border: 0; border-radius: 12px; background: #2563eb; color: #fff; font: inherit; font-weight: 900; padding: 0 14px; cursor: pointer; }
button.danger { background: #7f1d1d; }
.region-shell { flex: 1 1 auto; min-height: 0; display: grid; grid-template-columns: 1fr; gap: 0; align-items: stretch; }
.map-card { border: 1px solid rgba(148,163,184,.18); border-radius: 26px; background: rgba(15,23,42,.68); box-shadow: 0 28px 70px rgba(0,0,0,.26); }
.map-card { min-height: 0; display: flex; flex-direction: column; padding: 12px; }
.map-title { display: flex; justify-content: space-between; gap: 10px; margin-bottom: 12px; color: #bfdbfe; }
.map-title span, .map-note { color: #cbd5e1; }
svg { flex: 1 1 auto; width: 100%; min-height: 0; height: 100%; border-radius: 18px; background: linear-gradient(180deg, rgba(8,47,73,.68), rgba(2,6,23,.78)); }
.region-path { cursor: pointer; stroke: #fff; stroke-width: 3.07949; stroke-linejoin: round; stroke-miterlimit: 10; filter: url(#shadow); opacity: 1; fill-opacity: 1; transform-box: fill-box; transform-origin: center; transition: filter .16s ease, opacity .16s ease, stroke .16s ease, stroke-width .16s ease, transform .16s ease; }
.region-path.hovered, .region-path:hover, .region-path:focus-visible { opacity: 1; fill-opacity: 1; stroke: #f8fafc; stroke-width: 7; transform: scale(1.01); outline: none; }
.region-path.active { opacity: 1; stroke: #facc15; stroke-width: 8; }
.region-labels { pointer-events: none; }
.region-labels text { fill: #fff; font-family: 'Noto Sans KR', Pretendard, sans-serif; font-weight: 900; paint-order: stroke; stroke: rgba(2,6,23,.55); stroke-width: 8px; }
.map-note { flex: 0 0 auto; margin: 8px 2px 0; font-size: .78rem; line-height: 1.4; }
@media (max-width: 840px) {
  .hero { display: block; }
  .hero-actions { margin-top: 10px; }
}
@media (max-width: 430px) {
  .hero-actions { display: grid; }
  .region-page { padding: 10px 10px calc(86px + env(safe-area-inset-bottom)); gap: 8px; }
  .hero { padding: 12px; }
  h1 { font-size: 1.55rem; }
  .hero span { font-size: .86rem; }
  button { min-height: 38px; }
  .map-card { padding: 10px; }
  .map-note { font-size: .72rem; }
}
</style>
