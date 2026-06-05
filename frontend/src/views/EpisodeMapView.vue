<template>
  <main class="map-page">
    <CaseFileTabMenu :episode-id="episodeId" active="map" />

    <header class="topbar">
      <div>
        <p>FIELD MAP</p>
        <h1>{{ mapData?.title || '지도 로딩 중' }}</h1>
      </div>
      <button type="button" @click="showClues = true">단서 보드</button>
    </header>

    <section v-if="statusMessage" class="status-message" :class="statusType">
      <span>{{ statusMessage }}</span>
      <button v-if="caseFileUpdated" type="button" @click="router.push({ name: 'EpisodeCaseFile', params: { episodeId } })">
        사건파일 확인
      </button>
    </section>

    <section class="map-panel">
      <div class="legend">
        <span v-for="type in markerTypes" :key="type" :class="type">{{ markerLabel(type) }}</span>
      </div>

      <div ref="mapContainer" class="kakao-map">
        <div v-if="mapLoadFailed" class="map-error">
          <strong>지도를 불러오지 못했습니다.</strong>
          <p>{{ mapLoadMessage }}</p>
        </div>
      </div>

      <p class="map-caption">
        모든 조사 장소는 처음부터 표시됩니다. 마커 색상은 공개 역할만 나타내며, 실제 최종 장소 여부는 도착 판정 전까지 공개되지 않습니다.
      </p>

      <div class="spot-list" aria-label="조사 장소 목록">
        <button
          v-for="spot in mapData?.spots || []"
          :key="spot.spotId"
          type="button"
          class="spot-list-item"
          :class="[spot.publicMarkerType, { selected: selectedSpot?.spotId === spot.spotId }]"
          @click="selectSpot(spot)"
        >
          <span>{{ shortLabel(spot.publicMarkerType) }}</span>
          <strong>{{ spot.placeName }}</strong>
          <small>{{ markerLabel(spot.publicMarkerType) }}</small>
        </button>
      </div>
    </section>

    <button class="floating clue" type="button" @click="showClues = true">단서</button>
    <button class="floating refresh" type="button" @click="loadAll">갱신</button>

    <PuzzleCard :puzzle="puzzle" :message="puzzleMessage" :correct="puzzleCorrect" @submit="submitPuzzle" />

    <SpotBottomSheet
      :spot="selectedSpot"
      :arrival-result="arrivalResults[selectedSpot?.spotId]"
      @navigate="navigateToSpot"
      @arrive="arriveAtSpot"
      @open-puzzle="openPuzzle"
      @start-deduction="goDeduction"
    />

    <ClueBoard :board="clueBoard" :open="showClues" @close="showClues = false" />
  </main>
</template>

<script setup>
import { nextTick, onMounted, onUnmounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { episodeApi } from '@/api/episodeApi';
import SpotBottomSheet from '@/components/episode/SpotBottomSheet.vue';
import PuzzleCard from '@/components/episode/PuzzleCard.vue';
import ClueBoard from '@/components/episode/ClueBoard.vue';
import CaseFileTabMenu from '@/components/episode/CaseFileTabMenu.vue';

const route = useRoute();
const router = useRouter();
const episodeId = route.params.episodeId;

const mapContainer = ref(null);
const mapData = ref(null);
const clueBoard = ref(null);
const selectedSpot = ref(null);
const puzzle = ref(null);
const puzzleMessage = ref('');
const puzzleCorrect = ref(null);
const statusMessage = ref('');
const statusType = ref('info');
const caseFileUpdated = ref(false);
const showClues = ref(false);
const arrivalResults = ref({});
const mapLoadFailed = ref(false);
const mapLoadMessage = ref('');

const devArrival = import.meta.env.VITE_DEV_ARRIVAL === 'true';
const kakaoMapKey = import.meta.env.VITE_KAKAO_MAP_KEY || '';
const tmapAppKey = import.meta.env.VITE_TMAP_APP_KEY || '';
const markerTypes = ['START', 'ANSWER_HINT', 'DESTINATION_HINT', 'STORY', 'FINAL_CANDIDATE'];

let kakaoMap = null;
let overlays = [];
let sdkPromise = null;

onMounted(async () => {
  try {
    await loadAll();
    await initializeKakaoMap();
  } catch (error) {
    setStatus(error.userMessage || error.message || '지도 화면을 초기화할 수 없습니다.', 'error');
  }
});

onUnmounted(() => clearOverlays());

async function loadAll() {
  mapData.value = await episodeApi.getMap(episodeId);
  clueBoard.value = await episodeApi.getClueBoard(episodeId);
  if (selectedSpot.value) {
    selectedSpot.value = mapData.value.spots.find((spot) => spot.spotId === selectedSpot.value.spotId) || selectedSpot.value;
  }
  await renderMarkers();
}

async function initializeKakaoMap() {
  await nextTick();
  try {
    const maps = await loadKakaoMapSdk();
    const spots = mapData.value?.spots || [];
    const centerSpot = spots[0];
    const center = new maps.LatLng(centerSpot?.latitude || 37.5665, centerSpot?.longitude || 126.9780);
    kakaoMap = new maps.Map(mapContainer.value, { center, level: 5 });
    await renderMarkers();
    fitMapBounds();
  } catch (error) {
    mapLoadFailed.value = true;
    mapLoadMessage.value = error.message || 'Kakao Maps SDK 초기화에 실패했습니다.';
  }
}

function loadKakaoMapSdk() {
  if (window.kakao?.maps?.Map && window.kakao?.maps?.LatLng) {
    return Promise.resolve(window.kakao.maps);
  }

  if (!kakaoMapKey || kakaoMapKey.startsWith('YOUR_')) {
    return Promise.reject(new Error('frontend/.env에 VITE_KAKAO_MAP_KEY를 설정해야 실제 지도가 표시됩니다.'));
  }

  if (sdkPromise) return sdkPromise;

  sdkPromise = new Promise((resolve, reject) => {
    const existingScript = document.querySelector('script[data-kakao-map-sdk="true"]');
    const finish = () => {
      if (!window.kakao?.maps?.load) {
        reject(new Error('Kakao Maps SDK가 로드되었지만 maps.load를 찾을 수 없습니다.'));
        return;
      }
      window.kakao.maps.load(() => resolve(window.kakao.maps));
    };

    if (existingScript) {
      existingScript.addEventListener('load', finish, { once: true });
      existingScript.addEventListener('error', () => reject(new Error('Kakao Maps SDK 스크립트 로딩에 실패했습니다.')), { once: true });
      return;
    }

    const script = document.createElement('script');
    script.dataset.kakaoMapSdk = 'true';
    script.async = true;
    script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${encodeURIComponent(kakaoMapKey)}&autoload=false`;
    script.onload = finish;
    script.onerror = () => reject(new Error('Kakao Maps SDK 스크립트 로딩에 실패했습니다. Kakao JavaScript 키와 도메인 등록을 확인해 주세요.'));
    document.head.appendChild(script);
  });

  return sdkPromise;
}

async function renderMarkers() {
  if (!kakaoMap || !window.kakao?.maps) return;
  clearOverlays();
  const maps = window.kakao.maps;

  for (const spot of mapData.value?.spots || []) {
    const position = new maps.LatLng(spot.latitude, spot.longitude);
    const content = document.createElement('button');
    content.type = 'button';
    content.className = `case-marker ${spot.publicMarkerType}${spot.visited ? ' visited' : ''}${spot.completed ? ' completed' : ''}`;
    content.textContent = shortLabel(spot.publicMarkerType);
    content.setAttribute('aria-label', `${spot.placeName} ${markerLabel(spot.publicMarkerType)}`);
    content.addEventListener('click', () => selectSpot(spot));

    const overlay = new maps.CustomOverlay({ position, content, yAnchor: 0.5, xAnchor: 0.5, zIndex: spot.completed ? 4 : 3 });
    overlay.setMap(kakaoMap);
    overlays.push(overlay);
  }
}

function clearOverlays() {
  overlays.forEach((overlay) => overlay.setMap(null));
  overlays = [];
}

function fitMapBounds() {
  const spots = mapData.value?.spots || [];
  if (!kakaoMap || !spots.length || !window.kakao?.maps) return;
  const bounds = new window.kakao.maps.LatLngBounds();
  spots.forEach((spot) => bounds.extend(new window.kakao.maps.LatLng(spot.latitude, spot.longitude)));
  kakaoMap.setBounds(bounds, 36, 36, 36, 36);
}

function selectSpot(spot) {
  selectedSpot.value = spot;
  puzzle.value = null;
  puzzleMessage.value = '';
  puzzleCorrect.value = null;
}

function navigateToSpot(spot) {
  if (!tmapAppKey || tmapAppKey.startsWith('YOUR_')) {
    setStatus('frontend/.env에 VITE_TMAP_APP_KEY를 설정해야 Tmap 내비를 실행할 수 있습니다.', 'error');
    return;
  }
  const url = new URL('https://apis.openapi.sk.com/tmap/app/routes');
  url.searchParams.set('appKey', tmapAppKey);
  url.searchParams.set('name', spot.placeName);
  url.searchParams.set('lon', String(spot.longitude));
  url.searchParams.set('lat', String(spot.latitude));
  window.location.href = url.toString();
}

async function arriveAtSpot(spot) {
  try {
    const position = await getPosition(spot);
    const result = await episodeApi.arrive(episodeId, spot.spotId, { userLat: position.lat, userLng: position.lng, devMode: devArrival });
    arrivalResults.value = { ...arrivalResults.value, [spot.spotId]: result };
    const wrongFinalCandidate = spot.publicMarkerType === 'FINAL_CANDIDATE' && result.arrived && !result.canStartDeduction;
    const text = wrongFinalCandidate
      ? '이 장소에서는 최종 추리를 시작할 수 없습니다. 목적지 힌트를 다시 확인해 주세요.'
      : result.message;
    setStatus(text, result.arrived ? 'success' : 'info');
    await loadAll();
  } catch (error) {
    setStatus(error.userMessage || error.message || '도착 판정을 진행할 수 없습니다.', 'error');
  }
}

async function openPuzzle(spot) {
  try {
    puzzle.value = await episodeApi.getPuzzle(spot.spotId);
    puzzleMessage.value = '';
    puzzleCorrect.value = null;
  } catch (error) {
    setStatus(error.userMessage || '퍼즐을 열 수 없습니다.', 'error');
  }
}

async function submitPuzzle(answer) {
  try {
    const result = await episodeApi.submitPuzzle(puzzle.value.puzzleId, answer);
    puzzleMessage.value = result.message;
    puzzleCorrect.value = result.correct;
    clueBoard.value = result.clueBoard || await episodeApi.getClueBoard(episodeId);
    caseFileUpdated.value = Boolean(result.correct && result.caseFileUpdated);
    if (result.correct && result.caseFileUpdated) {
      setStatus('새 사건 자료가 사건파일에 추가되었습니다.', 'success');
    } else if (result.correct) {
      setStatus('정답입니다. 단서 보드가 갱신되었습니다.', 'success');
    }
    await loadAll();
  } catch (error) {
    puzzleMessage.value = error.userMessage || '정답을 제출할 수 없습니다.';
    puzzleCorrect.value = false;
  }
}

function goDeduction() {
  router.push({ name: 'FinalDeduction', params: { episodeId } });
}

async function getPosition(spot) {
  if (devArrival) return { lat: spot.latitude, lng: spot.longitude };
  return new Promise((resolve, reject) => {
    if (!navigator.geolocation) {
      reject(new Error('현재 브라우저에서 위치 확인을 지원하지 않습니다.'));
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (pos) => resolve({ lat: pos.coords.latitude, lng: pos.coords.longitude }),
      () => reject(new Error('현재 위치를 확인할 수 없습니다. 위치 권한을 허용해 주세요.')),
      { enableHighAccuracy: true, timeout: 8000 }
    );
  });
}

function setStatus(text, type = 'info') {
  statusMessage.value = text;
  statusType.value = type;
}

const markerLabel = (type) => ({
  START: '시작 장소',
  ANSWER_HINT: '정답 힌트',
  DESTINATION_HINT: '목적지 힌트',
  STORY: '스토리 단서',
  FINAL_CANDIDATE: '조사 후보'
}[type] || type);

const shortLabel = (type) => ({ START: 'S', ANSWER_HINT: 'A', DESTINATION_HINT: 'D', STORY: 'T', FINAL_CANDIDATE: 'C' }[type] || '?');
</script>

<style scoped>
.map-page { min-height: 100vh; box-sizing: border-box; padding: 14px 12px 250px; background: linear-gradient(180deg, #0f172a, #020617); color: #f8fafc; font-family: 'Noto Sans KR', sans-serif; }
.topbar, .status-message, .map-panel { width: min(100%, 430px); margin: 0 auto 12px; }
.topbar { display: flex; justify-content: space-between; gap: 12px; align-items: center; }
.topbar p { margin: 0; color: #67e8f9; font-size: .7rem; font-weight: 900; letter-spacing: .12em; }
h1 { margin: 2px 0 0; font-size: 1.08rem; }
.topbar button, .status-message button { border: 1px solid rgba(103,232,249,.35); border-radius: 999px; background: rgba(8,47,73,.5); color: #a5f3fc; padding: 9px 12px; font-weight: 900; }
.status-message { display: flex; align-items: center; justify-content: space-between; gap: 8px; box-sizing: border-box; padding: 10px 12px; border-radius: 14px; font-size: .85rem; font-weight: 900; }
.status-message.info { border: 1px solid rgba(56,189,248,.28); background: rgba(8,47,73,.28); color: #a5f3fc; }
.status-message.success { border: 1px solid rgba(34,197,94,.32); background: rgba(20,83,45,.3); color: #bbf7d0; }
.status-message.error { border: 1px solid rgba(248,113,113,.38); background: rgba(127,29,29,.32); color: #fecaca; }
.legend { display: flex; gap: 6px; overflow-x: auto; padding-bottom: 8px; }
.legend span { flex: 0 0 auto; border-radius: 999px; padding: 6px 9px; font-size: .72rem; font-weight: 900; background: rgba(30,41,59,.8); }
.legend .START { color: #60a5fa; }
.legend .ANSWER_HINT { color: #fb923c; }
.legend .DESTINATION_HINT { color: #c084fc; }
.legend .STORY { color: #4ade80; }
.legend .FINAL_CANDIDATE { color: #cbd5e1; }
.kakao-map { position: relative; height: min(62vh, 560px); min-height: 430px; overflow: hidden; border: 1px solid rgba(148,163,184,.2); border-radius: 24px; background: #0f172a; }
.map-error { position: absolute; inset: 0; display: grid; place-content: center; gap: 8px; padding: 24px; text-align: center; background: rgba(15,23,42,.92); color: #cbd5e1; z-index: 2; }
.map-error strong { color: #fecaca; }
.map-caption { box-sizing: border-box; margin: 8px 0 0; padding: 10px 12px; border-radius: 14px; background: rgba(2,6,23,.72); color: #cbd5e1; font-size: .78rem; line-height: 1.45; }
.spot-list { display: grid; gap: 8px; margin-top: 10px; }
.spot-list-item { display: grid; grid-template-columns: 34px minmax(0, 1fr) auto; align-items: center; gap: 9px; width: 100%; min-height: 48px; border: 1px solid rgba(148,163,184,.2); border-radius: 14px; background: rgba(15,23,42,.72); color: #f8fafc; text-align: left; }
.spot-list-item span { width: 28px; height: 28px; display: grid; place-items: center; border-radius: 999px; color: #fff; font-weight: 1000; }
.spot-list-item strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.spot-list-item small { color: #cbd5e1; font-weight: 900; font-size: .72rem; }
.spot-list-item.START span { background: #2563eb; }
.spot-list-item.ANSWER_HINT span { background: #ea580c; }
.spot-list-item.DESTINATION_HINT span { background: #7e22ce; }
.spot-list-item.STORY span { background: #15803d; }
.spot-list-item.FINAL_CANDIDATE span { background: #1f2937; }
.spot-list-item.selected { border-color: rgba(251,146,60,.62); box-shadow: 0 0 0 3px rgba(251,146,60,.14); }
:deep(.case-marker) { position: relative; width: 42px; height: 42px; border: 3px solid rgba(255,255,255,.72); border-radius: 999px; color: #fff; font-weight: 1000; box-shadow: 0 10px 18px rgba(0,0,0,.3); cursor: pointer; }
:deep(.case-marker.START) { background: #2563eb; }
:deep(.case-marker.ANSWER_HINT) { background: #ea580c; }
:deep(.case-marker.DESTINATION_HINT) { background: #7e22ce; }
:deep(.case-marker.STORY) { background: #15803d; }
:deep(.case-marker.FINAL_CANDIDATE) { background: #1f2937; }
:deep(.case-marker.visited) { outline: 3px solid rgba(125,211,252,.72); }
:deep(.case-marker.completed) { box-shadow: 0 0 0 4px rgba(34,197,94,.5), 0 10px 18px rgba(0,0,0,.3); }
.floating { position: fixed; z-index: 25; right: calc(50% - min(50%, 215px) + 14px); border: 0; border-radius: 999px; min-width: 54px; min-height: 46px; color: #fff; font-weight: 900; box-shadow: 0 12px 24px rgba(0,0,0,.32); }
.floating.clue { bottom: 250px; background: #b45309; }
.floating.refresh { bottom: 304px; background: #0369a1; }
@media (max-width: 370px) {
  .map-page { padding-bottom: 300px; }
  .topbar { align-items: flex-start; }
  .kakao-map { min-height: 360px; }
  .spot-list-item { grid-template-columns: 30px 1fr; }
  .spot-list-item small { grid-column: 2; }
  .floating.clue { bottom: 300px; }
  .floating.refresh { bottom: 354px; }
}
</style>
