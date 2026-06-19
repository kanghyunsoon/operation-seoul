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
        미션 파일 확인
      </button>
    </section>

    <section class="map-shell">
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
          8개의 조사 미션을 모두 해결하면 최종 장소가 자동으로 공개됩니다. 각 미션에서 범인, 흉기, 동기, 방법을 좁히는 서로 다른 단서를 수집하세요.
        </p>
      </section>

      <aside class="route-panel">
        <div class="route-panel-head">
          <p>INVESTIGATION ROUTE</p>
          <strong>{{ mapData?.spots?.length || 0 }}개 단서 지점</strong>
        </div>
        <div class="spot-list" aria-label="조사 장소 목록">
          <button
            v-for="spot in mapData?.spots || []"
            :key="spot.spotId"
            type="button"
            class="spot-list-item"
            :class="[spot.publicMarkerType, { selected: selectedSpot?.spotId === spot.spotId, done: spot.completed, visited: spot.visited }]"
            @click="selectSpot(spot)"
          >
            <span>{{ shortLabel(spot.publicMarkerType) }}</span>
            <strong>{{ spot.placeName }}</strong>
            <small>{{ markerLabel(spot.publicMarkerType) }}</small>
          </button>
        </div>
      </aside>
    </section>

    <button class="floating clue" type="button" @click="showClues = true">단서</button>
    <button class="floating refresh" type="button" @click="loadAll">갱신</button>
    <button v-if="sessionStore.isAdmin && selectedSpot" class="floating admin-skip" type="button" @click="adminSkipArrival(selectedSpot)">
      관리자 스킵
    </button>
    <button v-if="sessionStore.isAdmin && mapData?.adminFinalSpot" class="floating admin-final" type="button" @click="adminMoveToFinalSpot">
      최종 GPS 이동
    </button>
    <button v-if="mapData?.finalDestinationUnlocked" class="floating final-check" type="button" @click="arriveAtFinalPlace">
      추리 장소 확인
    </button>

    <PuzzleCard :puzzle="puzzle" :message="puzzleMessage" :correct="puzzleCorrect" :explanation="puzzleExplanation" @submit="submitPuzzle" @close="closePuzzle" />

    <SpotBottomSheet
      :spot="selectedSpot"
      :arrival-result="arrivalResults[selectedSpot?.spotId]"
      :puzzle-open="Boolean(puzzle && puzzle.spotId === selectedSpot?.spotId)"
      @navigate="navigateToSpot"
      @arrive="arriveAtSpot"
      @open-puzzle="openPuzzle"
      @close-puzzle="closePuzzle"
      @start-deduction="goDeduction"
    />

    <ClueBoard :board="clueBoard" :open="showClues" @close="showClues = false" />

    <Teleport to="body">
      <section v-if="rewardPopup.visible" class="reward-pop-overlay" aria-live="polite">
        <article class="reward-pop-card" :class="{ fly: rewardPopup.flying }">
          <span class="reward-kicker">MISSION CLEAR</span>
          <h2>단서를 획득했습니다</h2>
          <span v-if="rewardPopup.typeLabel" class="reward-type">{{ rewardPopup.typeLabel }}</span>
          <p class="reward-clue">{{ rewardPopup.clue }}</p>
          <div v-if="rewardPopup.items.length" class="reward-items">
            <span v-for="item in rewardPopup.items" :key="`${item.rewardType}-${item.itemType}-${item.targetId}`">
              {{ rewardItemLabel(item) }}
            </span>
          </div>
          <strong class="reward-destination">미션 메모에 보관 중</strong>
        </article>
      </section>
    </Teleport>
  </main>
</template>

<script setup>
import { nextTick, onMounted, onUnmounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { episodeApi } from '@/api/episodeApi';
import { useSessionStore } from '@/stores/sessionStore';
import SpotBottomSheet from '@/components/episode/SpotBottomSheet.vue';
import PuzzleCard from '@/components/episode/PuzzleCard.vue';
import ClueBoard from '@/components/episode/ClueBoard.vue';
import CaseFileTabMenu from '@/components/episode/CaseFileTabMenu.vue';

const route = useRoute();
const router = useRouter();
const sessionStore = useSessionStore();
const episodeId = route.params.episodeId;

const mapContainer = ref(null);
const mapData = ref(null);
const clueBoard = ref(null);
const selectedSpot = ref(null);
const puzzle = ref(null);
const puzzleMessage = ref('');
const puzzleCorrect = ref(null);
const puzzleExplanation = ref('');
const statusMessage = ref('');
const statusType = ref('info');
const caseFileUpdated = ref(false);
const showClues = ref(false);
const arrivalResults = ref({});
const finalArrivalResult = ref(null);
const mapLoadFailed = ref(false);
const mapLoadMessage = ref('');
const currentPosition = ref(null);
const rewardPopup = ref({
  visible: false,
  flying: false,
  typeLabel: '',
  clue: '',
  items: []
});

const devArrival = import.meta.env.VITE_DEV_ARRIVAL === 'true';
const kakaoMapKey = import.meta.env.VITE_KAKAO_MAP_KEY || '';
const tmapAppKey = import.meta.env.VITE_TMAP_APP_KEY || '';
const markerTypes = ['START', 'KEYWORD_1', 'KEYWORD_2', 'KEYWORD_3', 'FINAL_DESTINATION'];

let kakaoMap = null;
let overlays = [];
let routeLine = null;
let currentPositionOverlay = null;
let sdkPromise = null;
let rewardPopupTimer = null;
let rewardPopupClearTimer = null;

onMounted(async () => {
  try {
    await loadAll();
    await initializeKakaoMap();
  } catch (error) {
    setStatus(error.userMessage || error.message || '지도 화면을 초기화할 수 없습니다.', 'error');
  }
});

onUnmounted(() => {
  clearOverlays();
  clearRewardPopupTimers();
});

async function loadAll() {
  mapData.value = await episodeApi.getMap(episodeId);
  clueBoard.value = await episodeApi.getClueBoard(episodeId);
  if (!selectedSpot.value && mapData.value?.spots?.length) selectedSpot.value = mapData.value.spots[0];
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
  if (window.kakao?.maps?.Map && window.kakao?.maps?.LatLng) return Promise.resolve(window.kakao.maps);
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
    script.onerror = () => reject(new Error('Kakao Maps SDK 스크립트 로딩에 실패했습니다. Kakao JavaScript 키와 허용 도메인을 확인하세요.'));
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
  renderCurrentPosition();
}

function clearOverlays() {
  overlays.forEach((overlay) => overlay.setMap(null));
  overlays = [];
  if (routeLine) {
    routeLine.setMap(null);
    routeLine = null;
  }
  if (currentPositionOverlay) {
    currentPositionOverlay.setMap(null);
    currentPositionOverlay = null;
  }
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

async function navigateToSpot(spot) {
  if (!kakaoMap || !window.kakao?.maps) {
    setStatus('지도 SDK가 준비되지 않아 앱 안에 경로선을 표시할 수 없습니다. Kakao JavaScript 키와 허용 도메인을 확인하세요.', 'error');
    return;
  }
  const start = await getNavigationStartPosition(spot);
  const end = { lat: Number(spot.latitude), lng: Number(spot.longitude) };
  try {
    const routePath = await fetchTmapPedestrianRoute(start, end, spot);
    drawRoutePath(routePath);
    fitRouteBounds(routePath);
    setStatus('카카오 지도 위에 TMAP 도보 네비게이션 경로를 표시했습니다.', 'success');
  } catch (error) {
    drawRoutePath([start, end]);
    fitRouteBounds([start, end]);
    setStatus(error.message || 'TMAP 경로를 불러오지 못해 직선 참고 경로를 표시했습니다.', 'info');
  }
}

async function arriveAtSpot(spot) {
  try {
    const position = await getPosition(spot);
    const result = await episodeApi.arrive(episodeId, spot.spotId, { userLat: position.lat, userLng: position.lng, devMode: devArrival });
    arrivalResults.value = { ...arrivalResults.value, [spot.spotId]: result };
    const wrongFinalCandidate = ['FINAL_CANDIDATE', 'FINAL_DESTINATION'].includes(spot.publicMarkerType) && result.arrived && !result.canStartDeduction;
    setStatus(wrongFinalCandidate ? '이 장소에서는 최종 추리를 시작할 수 없습니다. 키워드 미션 진행 상태를 다시 확인해 주세요.' : result.message, result.arrived ? 'success' : 'info');
    const shouldOpenPuzzle = Boolean(result.arrived && result.canOpenPuzzle);
    await loadAll();
    if (shouldOpenPuzzle) {
      const updatedSpot = (mapData.value?.spots || []).find((item) => item.spotId === spot.spotId) || spot;
      selectedSpot.value = updatedSpot;
      await openPuzzle(updatedSpot);
    }
  } catch (error) {
    setStatus(error.userMessage || error.message || '도착 판정을 진행할 수 없습니다.', 'error');
  }
}

async function arriveAtFinalPlace() {
  try {
    const position = await getCurrentPositionForFinalCheck();
    const result = await episodeApi.arriveFinalPlace(episodeId, { userLat: position.lat, userLng: position.lng, devMode: devArrival });
    finalArrivalResult.value = result;
    setStatus(result.message, result.canStartDeduction ? 'success' : 'info');
    if (result.canStartDeduction) {
      await loadAll();
    }
  } catch (error) {
    setStatus(error.userMessage || error.message || '추리 장소 확인을 진행할 수 없습니다.', 'error');
  }
}

async function adminMoveToFinalSpot() {
  const finalSpot = mapData.value?.adminFinalSpot;
  if (!finalSpot) return;
  setCurrentPosition({ lat: Number(finalSpot.latitude), lng: Number(finalSpot.longitude) }, true);
  if (kakaoMap && window.kakao?.maps) {
    kakaoMap.panTo(new window.kakao.maps.LatLng(finalSpot.latitude, finalSpot.longitude));
  }
  try {
    const result = await episodeApi.arriveFinalPlace(episodeId, { userLat: finalSpot.latitude, userLng: finalSpot.longitude, devMode: true });
    finalArrivalResult.value = result;
    setStatus('관리자 테스트 GPS를 최종 장소로 이동했고 최종 추리 가능 상태를 확인했습니다.', 'success');
    await loadAll();
  } catch (error) {
    setStatus(error.userMessage || '관리자 최종 장소 이동에 실패했습니다.', 'error');
  }
}

async function adminSkipArrival(spot) {
  try {
    const result = await episodeApi.arrive(episodeId, spot.spotId, { userLat: spot.latitude, userLng: spot.longitude, devMode: true });
    arrivalResults.value = { ...arrivalResults.value, [spot.spotId]: result };
    setStatus(result.message || '관리자 권한으로 도착 처리를 스킵했습니다.', result.arrived ? 'success' : 'info');
    await loadAll();
  } catch (error) {
    setStatus(error.userMessage || '관리자 도착 스킵에 실패했습니다.', 'error');
  }
}

async function openPuzzle(spot) {
  try {
    if (puzzle.value?.spotId === spot.spotId) {
      closePuzzle();
      return;
    }
    puzzle.value = await episodeApi.getPuzzle(spot.spotId);
    puzzleMessage.value = '';
    puzzleCorrect.value = null;
    puzzleExplanation.value = '';
  } catch (error) {
    setStatus(error.userMessage || '퍼즐을 열 수 없습니다.', 'error');
  }
}

function closePuzzle() {
  puzzle.value = null;
  puzzleMessage.value = '';
  puzzleCorrect.value = null;
  puzzleExplanation.value = '';
}

async function submitPuzzle(answer) {
  try {
    const result = await episodeApi.submitPuzzle(puzzle.value.puzzleId, answer);
    puzzleMessage.value = result.message;
    puzzleCorrect.value = result.correct;
    puzzleExplanation.value = '';
    clueBoard.value = result.clueBoard || await episodeApi.getClueBoard(episodeId);
    caseFileUpdated.value = Boolean(result.correct && result.caseFileUpdated);
    const unlockedTypes = result.unlockedRewardTypes || [];
    if (result.correct) {
      const popupData = rewardPopupData(result, unlockedTypes);
      closePuzzle();
      showRewardPopup(popupData);
      if (unlockedTypes.includes('STORY_CLUE')) setStatus('새 사건 기록이 해금되어 사건 개요 카드가 갱신되었습니다.', 'success');
      else if (result.caseFileUpdated) setStatus('새 미션 자료가 미션 파일에 추가되었습니다.', 'success');
      else setStatus('정답입니다. 단서 보드가 갱신되었습니다.', 'success');
    } else if (result.retryInteraction && puzzle.value) {
      puzzle.value = {
        ...puzzle.value,
        interaction: result.retryInteraction
      };
    }
    await loadAll();
  } catch (error) {
    puzzleMessage.value = error.userMessage || '정답을 제출할 수 없습니다.';
    puzzleCorrect.value = false;
  }
}

function rewardPopupData(result, unlockedTypes = []) {
  const typeLabel = hintTypeLabel(unlockedTypes);
  const clue = String(result.rewardClue || '').trim()
    || unlockedTypes.map(rewardTypeLabel).filter(Boolean).join(' · ')
    || '새로운 현장 단서';
  return {
    typeLabel,
    clue,
    items: Array.isArray(result.newlyUnlockedItems) ? result.newlyUnlockedItems : []
  };
}

function showRewardPopup(data) {
  clearRewardPopupTimers();
  rewardPopup.value = {
    visible: true,
    flying: false,
    typeLabel: data.typeLabel || '',
    clue: data.clue,
    items: data.items
  };
  rewardPopupTimer = window.setTimeout(() => {
    rewardPopup.value = { ...rewardPopup.value, flying: true };
  }, 1150);
  rewardPopupClearTimer = window.setTimeout(() => {
    rewardPopup.value = { visible: false, flying: false, typeLabel: '', clue: '', items: [] };
  }, 2150);
}

function clearRewardPopupTimers() {
  clearTimeout(rewardPopupTimer);
  clearTimeout(rewardPopupClearTimer);
  rewardPopupTimer = null;
  rewardPopupClearTimer = null;
}

function goDeduction() {
  router.push({ name: 'FinalDeduction', params: { episodeId } });
}

async function getPosition(spot) {
  if (devArrival) {
    const position = { lat: Number(spot.latitude), lng: Number(spot.longitude) };
    setCurrentPosition(position, true);
    return position;
  }
  try {
    const position = await getBrowserPosition();
    setCurrentPosition(position, false);
    return position;
  } catch {
    const position = fallbackPosition(spot);
    setCurrentPosition(position, true);
    setStatus('GPS를 사용할 수 없어 선택 지점 기준 임시 GPS를 표시했습니다.', 'info');
    return position;
  }
}

async function getCurrentPositionForFinalCheck() {
  if (devArrival) return currentPosition.value || fallbackPosition(selectedSpot.value || mapData.value?.spots?.[0]);
  try {
    const position = await getBrowserPosition();
    setCurrentPosition(position, false);
    return position;
  } catch {
    const position = currentPosition.value || fallbackPosition(selectedSpot.value || mapData.value?.spots?.[0]);
    setCurrentPosition(position, true);
    setStatus('GPS를 사용할 수 없어 임시 GPS 위치로 추리 장소를 확인합니다.', 'info');
    return position;
  }
}

function getBrowserPosition() {
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

async function getNavigationStartPosition(spot) {
  try {
    const position = await getBrowserPosition();
    setCurrentPosition(position, false);
    return position;
  } catch {
    const position = currentPosition.value || fallbackPosition(startSpotForNavigation() || spot);
    setCurrentPosition(position, true);
    return position;
  }
}

function fallbackPosition(spot) {
  const source = spot || mapData.value?.spots?.[0] || { latitude: 37.5665, longitude: 126.9780 };
  return { lat: Number(source.latitude), lng: Number(source.longitude) };
}

function startSpotForNavigation() {
  return (mapData.value?.spots || []).find((spot) => spot.publicMarkerType === 'START') || mapData.value?.spots?.[0];
}

function setCurrentPosition(position, temporary = false) {
  currentPosition.value = { ...position, temporary };
  renderCurrentPosition();
}

function renderCurrentPosition() {
  if (!kakaoMap || !window.kakao?.maps || !currentPosition.value) return;
  if (currentPositionOverlay) currentPositionOverlay.setMap(null);
  const content = document.createElement('div');
  content.className = `gps-marker${currentPosition.value.temporary ? ' temporary' : ''}`;
  content.textContent = currentPosition.value.temporary ? '임시 GPS' : '현재 GPS';
  currentPositionOverlay = new window.kakao.maps.CustomOverlay({
    position: new window.kakao.maps.LatLng(currentPosition.value.lat, currentPosition.value.lng),
    content,
    yAnchor: 1.2,
    zIndex: 9
  });
  currentPositionOverlay.setMap(kakaoMap);
}

async function fetchTmapPedestrianRoute(start, end, spot) {
  if (!tmapAppKey || tmapAppKey.startsWith('YOUR_')) {
    throw new Error('frontend/.env에 VITE_TMAP_APP_KEY를 설정해야 TMAP 도보 경로를 표시할 수 있습니다.');
  }
  const response = await fetch('https://apis.openapi.sk.com/tmap/routes/pedestrian?version=1&format=json', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      appKey: tmapAppKey
    },
    body: JSON.stringify({
      startX: String(start.lng),
      startY: String(start.lat),
      endX: String(end.lng),
      endY: String(end.lat),
      reqCoordType: 'WGS84GEO',
      resCoordType: 'WGS84GEO',
      startName: '현재 위치',
      endName: spot.placeName || '목적지'
    })
  });
  if (!response.ok) {
    throw new Error(`TMAP 도보 경로 API 호출 실패: ${response.status}`);
  }
  const data = await response.json();
  const path = [];
  for (const feature of data.features || []) {
    const geometry = feature.geometry || {};
    if (geometry.type === 'LineString') {
      geometry.coordinates.forEach(([lng, lat]) => path.push({ lat: Number(lat), lng: Number(lng) }));
    } else if (geometry.type === 'Point' && geometry.coordinates?.length >= 2 && path.length === 0) {
      path.push({ lat: Number(geometry.coordinates[1]), lng: Number(geometry.coordinates[0]) });
    }
  }
  if (path.length < 2) throw new Error('TMAP 경로 좌표가 충분하지 않아 직선 참고 경로를 표시합니다.');
  return path;
}

function drawRoutePath(path) {
  if (!kakaoMap || !window.kakao?.maps) return;
  if (routeLine) routeLine.setMap(null);
  routeLine = new window.kakao.maps.Polyline({
    path: path.map((point) => new window.kakao.maps.LatLng(point.lat, point.lng)),
    strokeWeight: 7,
    strokeColor: '#38bdf8',
    strokeOpacity: 0.9,
    strokeStyle: 'solid'
  });
  routeLine.setMap(kakaoMap);
}

function fitRouteBounds(path) {
  if (!kakaoMap || !window.kakao?.maps || !path.length) return;
  const bounds = new window.kakao.maps.LatLngBounds();
  path.forEach((point) => bounds.extend(new window.kakao.maps.LatLng(point.lat, point.lng)));
  kakaoMap.setBounds(bounds, 48, 48, 48, 48);
}

function setStatus(text, type = 'info') {
  statusMessage.value = text;
  statusType.value = type;
}

const markerLabel = (type) => ({
  START: '시작 장소',
  KEYWORD_1: '조사 미션',
  KEYWORD_2: '조사 미션',
  KEYWORD_3: '조사 미션',
  FINAL_DESTINATION: '최종 장소',
}[type] || '조사 미션');

const shortLabel = (type) => ({ START: 'S', KEYWORD_1: '1', KEYWORD_2: '2', KEYWORD_3: '3', FINAL_DESTINATION: 'F', ANSWER_HINT: 'I', DESTINATION_HINT: 'I', STORY: 'R', FINAL_CANDIDATE: 'F' }[type] || '•');

const rewardTypeLabel = (type) => ({
  SUSPECT_CLUE: '용의자 단서',
  ANSWER_CLUE: '추리 단서',
  DESTINATION_CLUE: '사건 단서',
  STORY_CLUE: '사건 기록',
  EVIDENCE_UNLOCK: '사건자료 해금',
  PHOTO_UNLOCK: '사진 자료 해금',
  MEMO_UNLOCK: '메모 해금',
  SUSPECT_UNLOCK: '관계자 카드 해금',
  SUSPECT_UPDATE: '관계자 정보 갱신'
}[type] || type);

const hintTypeLabel = (types = []) => {
  const type = (types || []).find((value) => ['SUSPECT_CLUE', 'ANSWER_CLUE', 'DESTINATION_CLUE', 'STORY_CLUE'].includes(value));
  return rewardTypeLabel(type) || '';
};

const rewardItemLabel = (item) => {
  const type = rewardTypeLabel(item.rewardType);
  const target = item.itemType ? ` · ${item.itemType}` : '';
  return `${type}${target}`;
};
</script>

<style scoped>
.map-page { min-height: 100vh; box-sizing: border-box; padding: 18px 16px 260px; background: linear-gradient(180deg, #0f172a, #020617); color: #f8fafc; font-family: 'Noto Sans KR', sans-serif; }
.topbar, .status-message, .map-shell { width: min(100%, 1180px); margin: 0 auto 12px; }
.topbar { display: flex; justify-content: space-between; gap: 12px; align-items: center; }
.topbar p, .route-panel-head p { margin: 0; color: #67e8f9; font-size: .7rem; font-weight: 900; letter-spacing: .12em; }
h1 { margin: 2px 0 0; font-size: clamp(1.25rem, 3vw, 2rem); }
.topbar button, .status-message button { border: 1px solid rgba(103,232,249,.35); border-radius: 999px; background: rgba(8,47,73,.5); color: #a5f3fc; padding: 9px 12px; font-weight: 900; }
.status-message { display: flex; align-items: center; justify-content: space-between; gap: 8px; box-sizing: border-box; padding: 10px 12px; border-radius: 14px; font-size: .9rem; font-weight: 900; }
.status-message.info { border: 1px solid rgba(56,189,248,.28); background: rgba(8,47,73,.28); color: #a5f3fc; }
.status-message.success { border: 1px solid rgba(34,197,94,.32); background: rgba(20,83,45,.3); color: #bbf7d0; }
.status-message.error { border: 1px solid rgba(248,113,113,.38); background: rgba(127,29,29,.32); color: #fecaca; }
.map-shell { display: grid; gap: 12px; }
.map-panel, .route-panel { border: 1px solid rgba(148,163,184,.18); border-radius: 24px; background: rgba(15,23,42,.64); padding: 12px; }
.legend { display: flex; gap: 6px; overflow-x: auto; padding-bottom: 8px; }
.legend span { flex: 0 0 auto; border-radius: 999px; padding: 6px 9px; font-size: .72rem; font-weight: 900; background: rgba(30,41,59,.8); }
.legend .START { color: #60a5fa; }
.legend .KEYWORD_1 { color: #fb923c; }
.legend .KEYWORD_2 { color: #22d3ee; }
.legend .KEYWORD_3 { color: #a3e635; }
.legend .FINAL_DESTINATION { color: #e5e7eb; }
.legend .STORY { color: #4ade80; }
.legend .FINAL_CANDIDATE { color: #cbd5e1; }
.kakao-map { position: relative; height: min(62vh, 620px); min-height: 430px; overflow: hidden; border: 1px solid rgba(148,163,184,.2); border-radius: 20px; background: #0f172a; }
.map-error { position: absolute; inset: 0; display: grid; place-content: center; gap: 8px; padding: 24px; text-align: center; background: rgba(15,23,42,.92); color: #cbd5e1; z-index: 2; }
.map-error strong { color: #fecaca; }
.map-caption { box-sizing: border-box; margin: 8px 0 0; padding: 10px 12px; border-radius: 14px; background: rgba(2,6,23,.72); color: #cbd5e1; font-size: .82rem; line-height: 1.45; }
.route-panel-head { display: flex; align-items: end; justify-content: space-between; gap: 8px; margin-bottom: 10px; }
.spot-list { display: grid; gap: 8px; max-height: 620px; overflow: auto; }
.spot-list-item { display: grid; grid-template-columns: 34px minmax(0, 1fr) auto; align-items: center; gap: 9px; width: 100%; min-height: 52px; border: 1px solid rgba(148,163,184,.2); border-radius: 14px; background: rgba(15,23,42,.72); color: #f8fafc; text-align: left; }
.spot-list-item span { width: 28px; height: 28px; display: grid; place-items: center; border-radius: 999px; color: #fff; font-weight: 1000; }
.spot-list-item strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.spot-list-item small { color: #cbd5e1; font-weight: 900; font-size: .72rem; }
.spot-list-item.START span { background: #2563eb; }
.spot-list-item.KEYWORD_1 span { background: #ea580c; }
.spot-list-item.KEYWORD_2 span { background: #0891b2; }
.spot-list-item.KEYWORD_3 span { background: #65a30d; }
.spot-list-item.FINAL_DESTINATION span { background: #020617; border: 1px solid rgba(255,255,255,.7); }
.spot-list-item.STORY span { background: #15803d; }
.spot-list-item.FINAL_CANDIDATE span { background: #1f2937; }
.spot-list-item.selected { border-color: rgba(251,146,60,.62); box-shadow: 0 0 0 3px rgba(251,146,60,.14); }
.spot-list-item.visited { outline: 1px solid rgba(56,189,248,.35); }
.spot-list-item.done { outline: 1px solid rgba(34,197,94,.5); opacity: .58; background: rgba(20,83,45,.24); }
.spot-list-item.done strong { text-decoration: line-through; text-decoration-thickness: 2px; text-decoration-color: rgba(134,239,172,.7); }
:deep(.case-marker) { position: relative; width: 42px; height: 42px; border: 3px solid rgba(255,255,255,.72); border-radius: 999px; color: #fff; font-weight: 1000; box-shadow: 0 10px 18px rgba(0,0,0,.3); cursor: pointer; }
:deep(.case-marker.START) { background: #2563eb; }
:deep(.case-marker.KEYWORD_1) { background: #ea580c; }
:deep(.case-marker.KEYWORD_2) { background: #0891b2; }
:deep(.case-marker.KEYWORD_3) { background: #65a30d; }
:deep(.case-marker.FINAL_DESTINATION) { background: #020617; border-color: rgba(255,255,255,.88); box-shadow: 0 0 0 4px rgba(0,0,0,.36), 0 14px 24px rgba(0,0,0,.44); }
:deep(.case-marker.STORY) { background: #15803d; }
:deep(.case-marker.FINAL_CANDIDATE) { background: #1f2937; }
:deep(.case-marker.visited) { outline: 3px solid rgba(125,211,252,.72); }
:deep(.case-marker.completed) { opacity: .62; box-shadow: 0 0 0 4px rgba(34,197,94,.5), 0 10px 18px rgba(0,0,0,.3); }
:deep(.case-marker.completed::after) { content: '✓'; position: absolute; right: -5px; bottom: -7px; width: 20px; height: 20px; display: grid; place-items: center; border-radius: 999px; background: #16a34a; color: #fff; font-size: 13px; }
:deep(.gps-marker) { min-width: 72px; min-height: 30px; display: grid; place-items: center; padding: 0 10px; border: 2px solid rgba(255,255,255,.82); border-radius: 999px; background: #0284c7; color: #fff; font-size: 12px; font-weight: 1000; box-shadow: 0 10px 18px rgba(0,0,0,.34); white-space: nowrap; }
:deep(.gps-marker.temporary) { background: #b45309; }
.floating { position: fixed; z-index: 25; right: 14px; border: 0; border-radius: 999px; min-width: 54px; min-height: 46px; color: #fff; font-weight: 900; box-shadow: 0 12px 24px rgba(0,0,0,.32); }
.floating.clue { bottom: 250px; background: #b45309; }
.floating.refresh { bottom: 304px; background: #0369a1; }
.floating.admin-skip { bottom: 358px; background: #15803d; padding: 0 14px; }
.floating.admin-final { bottom: 412px; background: #7c3aed; padding: 0 14px; }
.floating.final-check { bottom: 466px; background: #991b1b; padding: 0 14px; }
.reward-pop-overlay { position: fixed; inset: 0; z-index: 120; display: grid; place-items: center; pointer-events: none; background: radial-gradient(circle at 50% 42%, rgba(251,191,36,.18), transparent 34%); }
.reward-pop-card { width: min(calc(100vw - 34px), 390px); box-sizing: border-box; padding: 18px; border: 1px solid rgba(251,191,36,.5); border-radius: 22px; background: linear-gradient(145deg, rgba(120,53,15,.96), rgba(15,23,42,.98)); color: #fff7ed; box-shadow: 0 30px 90px rgba(0,0,0,.52), 0 0 0 8px rgba(251,191,36,.08); transform-origin: 50% 20%; animation: reward-pop-in .34s cubic-bezier(.2, .9, .2, 1.15) both; }
.reward-pop-card.fly { animation: reward-file-fly .92s cubic-bezier(.56, -.02, .2, 1) forwards; }
.reward-kicker { display: inline-flex; border-radius: 999px; padding: 5px 8px; background: rgba(250,204,21,.18); color: #fde68a; font-size: .7rem; font-weight: 1000; letter-spacing: .12em; }
.reward-pop-card h2 { margin: 10px 0 8px; font-size: 1.3rem; }
.reward-type { display: inline-flex; width: fit-content; margin: 0 0 8px; border-radius: 999px; padding: 5px 8px; background: rgba(14,165,233,.2); color: #bae6fd; font-size: .76rem; font-weight: 1000; }
.reward-clue { margin: 0; padding: 12px; border-radius: 16px; background: rgba(2,6,23,.42); color: #fef3c7; font-size: 1.02rem; font-weight: 1000; line-height: 1.45; }
.reward-items { display: flex; flex-wrap: wrap; gap: 7px; margin-top: 10px; }
.reward-items span { border-radius: 999px; padding: 6px 8px; background: rgba(34,197,94,.18); color: #bbf7d0; font-size: .76rem; font-weight: 900; }
.reward-destination { display: block; margin-top: 12px; color: #a5f3fc; font-size: .82rem; text-align: right; }
@keyframes reward-pop-in {
  from { opacity: 0; transform: translateY(22px) scale(.9) rotate(-2deg); filter: blur(5px); }
  to { opacity: 1; transform: translateY(0) scale(1) rotate(0); filter: blur(0); }
}
@keyframes reward-file-fly {
  0% { opacity: 1; transform: translateY(0) scale(1) rotate(0); filter: blur(0); }
  55% { opacity: .92; transform: translateY(-26vh) scale(.56) rotate(-7deg); filter: blur(.4px); }
  100% { opacity: 0; transform: translate(calc(-50vw + 150px), calc(-50vh + 76px)) scale(.12) rotate(-14deg); filter: blur(3px); }
}
@media (min-width: 900px) {
  .map-page { padding-bottom: 32px; }
  .map-shell { grid-template-columns: minmax(0, 1fr) 340px; align-items: start; }
  .floating { right: 430px; }
  .floating.clue { bottom: 34px; }
  .floating.refresh { bottom: 88px; }
  .floating.admin-skip { bottom: 142px; }
  .floating.admin-final { bottom: 196px; }
  .floating.final-check { bottom: 250px; }
}
@media (max-width: 370px) {
  .map-page { padding-bottom: 300px; }
  .spot-list-item { grid-template-columns: 30px 1fr; }
  .spot-list-item small { grid-column: 2; }
}
</style>
