<template>
  <main class="tab-page">
    <header class="hero">
      <button type="button" class="back" @click="router.push({ name: 'EpisodeList' })">미션 파일 목록</button>
      <p>COMMUNITY</p>
      <h1>커뮤니티</h1>
      <span>권역별 게시판에서 플레이 후기와 질문을 나눕니다.</span>
    </header>

    <section class="region-grid">
      <article v-for="area in regionAreas" :key="area.regionId" class="region-card" @click="openRegion(area)">
        <span :style="{ background: area.color }"></span>
        <h2>{{ area.label }}</h2>
        <p>{{ area.includes }}</p>
        <button type="button">게시판 보기</button>
      </article>
    </section>

    <MainBottomNav />
  </main>
</template>

<script setup>
import { useRouter } from 'vue-router';
import MainBottomNav from '@/components/MainBottomNav.vue';
import { regionAreas } from '@/constants/regionAreas.js';

const router = useRouter();

function openRegion(area) {
  router.push({
    name: 'RegionCommunity',
    params: { regionId: area.regionId },
    query: { areaCode: area.code }
  });
}
</script>

<style scoped>
.tab-page { min-height: 100vh; box-sizing: border-box; padding: 24px 16px 126px; background: radial-gradient(circle at 20% 0%, rgba(14,116,144,.24), transparent 34%), linear-gradient(160deg, #0f172a, #111827 60%, #050505); color: #f8fafc; font-family: Georgia, 'Noto Sans KR', serif; }
.hero, .region-grid { width: min(100%, 880px); box-sizing: border-box; margin-left: auto; margin-right: auto; }
.hero { margin-bottom: 18px; padding: 22px; border: 1px solid rgba(125,211,252,.22); border-radius: 20px; background: rgba(15,23,42,.58); }
.back { min-height: 38px; border: 1px solid rgba(148,163,184,.28); border-radius: 999px; background: transparent; color: #cbd5e1; padding: 0 14px; }
.hero p { margin: 18px 0 8px; color: #67e8f9; font-weight: 900; letter-spacing: .16em; font-size: .78rem; }
h1 { margin: 0; font-size: clamp(2rem, 9vw, 3.5rem); line-height: 1; }
.hero span { display: block; margin-top: 10px; color: #cbd5e1; }
.region-grid { display: grid; grid-template-columns: repeat(2, minmax(0,1fr)); gap: 14px; }
.region-card { position: relative; padding: 20px; border-radius: 18px; border: 1px solid rgba(248,250,252,.13); background: linear-gradient(135deg, rgba(255,247,237,.08), rgba(15,23,42,.78)); box-shadow: 0 20px 52px rgba(0,0,0,.22); cursor: pointer; overflow: hidden; }
.region-card > span { position: absolute; inset: 0 auto 0 0; width: 7px; }
h2 { margin: 0 0 8px; font-size: 1.34rem; }
p { color: #cbd5e1; line-height: 1.55; }
button { min-height: 40px; border: 0; border-radius: 12px; background: #0e7490; color: #fff; font: inherit; font-weight: 900; padding: 0 14px; }
@media (max-width: 560px) {
  .region-grid { grid-template-columns: 1fr; }
}
</style>
