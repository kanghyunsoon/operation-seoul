<template>
  <main class="region-page">
    <header class="hero">
      <div>
        <p>REGION SELECT</p>
        <h1>어느 권역을 조사할까요?</h1>
        <span>{{ sessionStore.currentUser?.nickname || '요원' }}님, 플레이할 사건파일의 지역을 선택하세요.</span>
      </div>
      <div class="hero-actions">
        <button type="button" @click="router.push({ name: 'EpisodeList' })">전체 사건 보기</button>
        <button type="button" @click="router.push({ name: 'Ranking' })">랭킹</button>
        <button type="button" @click="router.push({ name: 'Recommendations' })">추천</button>
        <button type="button" @click="router.push({ name: 'Coaching' })">코칭</button>
        <button type="button" @click="router.push({ name: 'Challenges' })">챌린지</button>
        <button type="button" @click="router.push({ name: 'MyPage' })">내 관심 목록</button>
        <button type="button" class="danger" @click="logout">로그아웃</button>
      </div>
    </header>

    <section class="region-shell">
      <div class="map-card">
        <div class="map-title">
          <strong>대전/수도권 조사 권역</strong>
          <span>서울 포함 10개 권역</span>
        </div>
        <svg viewBox="60 20 330 540" role="img" aria-label="대전/수도권 권역 선택 지도">
          <defs>
            <filter id="shadow" x="-20%" y="-20%" width="140%" height="140%">
              <feDropShadow dx="0" dy="8" stdDeviation="8" flood-color="#020617" flood-opacity="0.35" />
            </filter>
          </defs>
          <button
            v-for="area in regionAreas"
            :key="area.code"
            type="button"
            class="region-hit"
            :aria-label="`${area.label} 선택`"
            @click="openRegion(area.code)"
          >
            <rect :x="area.x" :y="area.y" :width="area.w" :height="area.h" rx="18" :fill="area.color" filter="url(#shadow)" />
            <text :x="area.x + area.w / 2" :y="area.y + area.h / 2 - 4" text-anchor="middle">{{ area.label }}</text>
            <text :x="area.x + area.w / 2" :y="area.y + area.h / 2 + 16" text-anchor="middle" class="small">{{ area.includes }}</text>
          </button>
          <path
            d="M112 64 C154 22 250 30 318 82 C378 132 382 238 352 326 C322 414 260 456 206 452 C142 448 84 396 78 306 C72 202 62 112 112 64Z"
            fill="none"
            stroke="rgba(255,255,255,.42)"
            stroke-width="5"
            stroke-linejoin="round"
          />
        </svg>
        <p class="map-note">
          현재 지도는 서비스 권역 선택용 의식 지도입니다. 운영 단계에서 행정 경계 GeoJSON으로 교체하면 더 정확하게 표시할 수 있습니다.
        </p>
      </div>

      <div class="region-list">
        <article v-for="area in regionAreas" :key="area.code" class="region-card">
          <button type="button" class="region-main" @click="openRegion(area.code)">
            <span :style="{ background: area.color }"></span>
            <strong>{{ area.label }}</strong>
            <small>{{ area.includes }}</small>
          </button>
          <button type="button" class="community-link" @click="openCommunity(area)">권역 커뮤니티</button>
        </article>
      </div>
    </section>
  </main>
</template>

<script setup>
import { useRouter } from 'vue-router';
import { useSessionStore } from '@/stores/sessionStore.js';
import { regionAreas } from '@/constants/regionAreas.js';

const router = useRouter();
const sessionStore = useSessionStore();

function openRegion(areaCode) {
  router.push({ name: 'EpisodeList', query: { areaCode } });
}

function openCommunity(area) {
  router.push({ name: 'RegionCommunity', params: { regionId: area.regionId }, query: { areaCode: area.code } });
}

function logout() {
  sessionStore.logout();
  router.push({ name: 'Intro' });
}
</script>

<style scoped>
.region-page { min-height: 100vh; box-sizing: border-box; padding: 28px 16px 60px; background: radial-gradient(circle at 18% 8%, rgba(14,165,233,.24), transparent 30%), linear-gradient(155deg, #101827, #0f172a 56%, #020617); color: #f8fafc; font-family: 'Noto Sans KR', Georgia, serif; }
.hero, .region-shell { width: min(100%, 1120px); margin: 0 auto; }
.hero { display: flex; align-items: end; justify-content: space-between; gap: 16px; margin-bottom: 18px; padding: 22px; border: 1px solid rgba(125,211,252,.22); border-radius: 24px; background: rgba(15,23,42,.72); }
.hero p { margin: 0 0 8px; color: #67e8f9; font-size: .76rem; font-weight: 900; letter-spacing: .16em; }
h1 { margin: 0; font-size: clamp(2rem, 7vw, 4.4rem); line-height: .98; }
.hero span { display: block; margin-top: 12px; color: #cbd5e1; }
.hero-actions { display: flex; flex-wrap: wrap; gap: 8px; }
button { min-height: 42px; border: 0; border-radius: 12px; background: #2563eb; color: #fff; font: inherit; font-weight: 900; padding: 0 14px; cursor: pointer; }
button.danger { background: #7f1d1d; }
.region-shell { display: grid; grid-template-columns: minmax(0, 1.1fr) minmax(300px, .9fr); gap: 16px; align-items: start; }
.map-card, .region-list { border: 1px solid rgba(148,163,184,.18); border-radius: 26px; background: rgba(15,23,42,.68); box-shadow: 0 28px 70px rgba(0,0,0,.26); }
.map-card { padding: 18px; }
.map-title { display: flex; justify-content: space-between; gap: 10px; margin-bottom: 12px; color: #bfdbfe; }
.map-title span, .map-note, .region-main small { color: #cbd5e1; }
svg { width: 100%; min-height: 560px; border-radius: 22px; background: linear-gradient(180deg, rgba(8,47,73,.68), rgba(2,6,23,.78)); }
.region-hit { all: unset; cursor: pointer; }
.region-hit rect { stroke: rgba(255,255,255,.72); stroke-width: 3; transition: transform .16s ease, opacity .16s ease; transform-box: fill-box; transform-origin: center; opacity: .9; }
.region-hit:hover rect { transform: scale(1.04); opacity: 1; }
.region-hit text { pointer-events: none; fill: white; font-size: 14px; font-weight: 900; paint-order: stroke; stroke: rgba(2,6,23,.45); stroke-width: 3px; }
.region-hit text.small { font-size: 9px; font-weight: 800; }
.map-note { margin: 12px 2px 0; font-size: .82rem; line-height: 1.5; }
.region-list { display: grid; gap: 10px; padding: 14px; }
.region-card { display: grid; grid-template-columns: 1fr auto; gap: 8px; align-items: stretch; padding: 0; border: 1px solid rgba(148,163,184,.16); border-radius: 16px; background: rgba(2,6,23,.42); }
.region-main { display: grid; grid-template-columns: 14px 1fr; gap: 4px 10px; align-items: center; min-height: 62px; width: 100%; text-align: left; background: transparent; }
.region-main span { grid-row: 1 / 3; width: 14px; height: 42px; border-radius: 999px; }
.region-main strong { font-size: 1rem; }
.community-link { align-self: center; margin-right: 10px; min-height: 38px; background: rgba(14,116,144,.7); color: #cffafe; font-size: .82rem; }
.region-card:hover { border-color: rgba(125,211,252,.44); }
@media (max-width: 840px) {
  .hero { display: block; }
  .hero-actions { margin-top: 14px; }
  .region-shell { grid-template-columns: 1fr; }
  svg { min-height: 500px; }
}
@media (max-width: 520px) {
  .region-card { grid-template-columns: 1fr; padding-bottom: 10px; }
  .community-link { margin: 0 10px; }
}
@media (max-width: 430px) {
  .hero-actions { display: grid; }
  .map-card { padding: 12px; }
  svg { min-height: 430px; }
}
</style>
