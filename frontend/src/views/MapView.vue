<template>
  <div class="tactical-fullscreen">
    <div class="device-frame">
      <header class="device-header">
        <div class="status-lights">
          <span class="light red" :class="{ blink: collectedHints < requiredHints }"></span>
          <span class="light green" :class="{ blink: collectedHints >= requiredHints }"></span>
        </div>
        <h2>📍 작전 구역: {{ regionName }}</h2>
        <div class="battery">BAT 87%</div>
      </header>

      <div class="screen-container">
        <div class="screen-overlay scanline"></div>
        <div id="map" class="map-view"></div>

        <div class="coord-overlay top-left">힌트: {{ collectedHints }} / {{ requiredHints }}</div>

        <div v-if="collectedHints >= 1" class="coord-overlay top-right final-dist-blink">
          최종 TGT DIST: {{ finalDistance }}m
        </div>
      </div>

      <div class="control-panel">
        <div class="info-screen">
          <p class="tgt-text">TGT: {{ currentTargetName }}</p>
          <p class="distance">DIST: {{ isArrived ? '0' : targetDistance }}m</p>
          <p class="status-text" :class="{ 'ready': isArrived }">
            {{ isArrived ? '> SIGNAL_LOCKED: 현장 도착 완료!' : '> 이동 중 (TRACKING...)' }}
          </p>
        </div>

        <button v-if="!isArrived" @click="forceArrival" class="override-btn">
          [ MANUAL_OVERRIDE : 강제 도착 ]
        </button>

        <button v-if="isArrived && (!currentMission?.isFinal)" @click="isScannerOpen = true" class="capture-btn">
          [ 📸 단서 스캐너 가동 ]
        </button>

        <button v-if="isArrived && currentMission?.isFinal" @click="goToChat" class="capture-btn final-btn">
          [ 📡 최종 본부 통신 연결 ]
        </button>

        <button @click="goToChat" class="override-btn chat-btn">
          [ 💬 간이 챗봇 접속 (본부) ]
        </button>
      </div>
    </div>

    <div v-if="isScannerOpen" class="scanner-modal">
      <CameraScanner @capture="uploadImage" @close="isScannerOpen = false" />
      <button @click="isScannerOpen = false" class="abort-btn">ABORT_SCAN</button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import axios from 'axios';
import apiClient from '@/api/axiosInstance';
import CameraScanner from '@/components/CameraScanner.vue';

const route = useRoute();
const router = useRouter();

const regionId = route.query.regionId || 1;
const regionName = ref('서울 시청 작전구역');

const currentMission = ref(null);
const currentSessionId = ref(null);
const isArrived = ref(false);
const isScannerOpen = ref(false);

const collectedHints = ref(0);
const requiredHints = ref(3);

const targetDistance = ref('----'); // 선택한 단서와의 거리
const finalDistance = ref('----');  // 최종 목적지와의 거리

let mapInstance = null;
let markers = [];
let navPolyline = null; // 실제 네비게이션 선
let trackingId = null;

const missions = ref([]);

// 💡 기본 위치 (서울시청)
const fallbackLocation = { lat: 37.5665, lng: 126.9780 };
const userLocation = ref({ ...fallbackLocation });

// UI 타겟 텍스트 로직
const currentTargetName = computed(() => {
  if (collectedHints.value >= requiredHints.value) return '최종 목적지 (네비게이션 가동 중)';
  return currentMission.value ? currentMission.value.title : '단서 마커를 선택하십시오.';
});

const fetchUserLocation = () => {
  return new Promise((resolve) => {
    if (!navigator.geolocation) {
      userLocation.value = { ...fallbackLocation };
      resolve();
      return;
    }
    navigator.geolocation.getCurrentPosition(
        (pos) => {
          userLocation.value = { lat: pos.coords.latitude, lng: pos.coords.longitude };
          resolve();
        },
        (err) => {
          console.warn('GPS 획득 실패. 서울시청으로 대체합니다.', err);
          userLocation.value = { ...fallbackLocation };
          resolve();
        },
        { enableHighAccuracy: true, timeout: 5000 }
    );
  });
};

const startTracking = () => {
  if (navigator.geolocation) {
    trackingId = navigator.geolocation.watchPosition(
        (pos) => {
          userLocation.value = { lat: pos.coords.latitude, lng: pos.coords.longitude };
          updateDistances();
        },
        (err) => console.warn('GPS 추적 실패', err),
        { enableHighAccuracy: true }
    );
  }
};

const updateDistances = () => {
  if (!mapInstance) return;
  const userPos = new window.kakao.maps.LatLng(userLocation.value.lat, userLocation.value.lng);

  // 1. 선택한 타겟 마커와의 직선 거리
  if (currentMission.value && currentMission.value.targetLat) {
    const targetPos = new window.kakao.maps.LatLng(currentMission.value.targetLat, currentMission.value.targetLng);
    const polylineTarget = new window.kakao.maps.Polyline({ path: [userPos, targetPos] });
    targetDistance.value = Math.floor(polylineTarget.getLength());
  }

  // 2. 최종 목적지와의 거리 (항상 계산)
  const finalM = missions.value.find(m => m.isFinal);
  if (finalM && finalM.targetLat) {
    const finalPos = new window.kakao.maps.LatLng(finalM.targetLat, finalM.targetLng);
    const polylineFinal = new window.kakao.maps.Polyline({ path: [userPos, finalPos] });
    finalDistance.value = Math.floor(polylineFinal.getLength());
  }
};

// DB 데이터 로드 (없으면 서울시청 반경 강제 생성)
const loadMissions = async () => {
  try {
    const res = await apiClient.get(`/v1/regions/${regionId}/missions`);
    if (res.data && res.data.length >= 4) {
      missions.value = res.data;
    } else {
      throw new Error("DB 데이터 부족");
    }
  } catch (err) {
    console.warn("데이터가 부족하여 임의의 단서 마커를 생성합니다.");
    missions.value = [
      { id: 1, title: '단서 1: 덕수궁 대한문', description: '매표소 옆 게시판 확인.', answer: '요금', targetLat: 37.5658, targetLng: 126.9751, isFinal: false, status: 'ACTIVE' },
      { id: 2, title: '단서 2: 시청 광장', description: '잔디밭 앞 석판 확인.', answer: '숫자', targetLat: 37.5663, targetLng: 126.9779, isFinal: false, status: 'ACTIVE' },
      { id: 3, title: '단서 3: 무교동 사거리', description: '길안내 표지판 거리 확인.', answer: '방향', targetLat: 37.5678, targetLng: 126.9785, isFinal: false, status: 'ACTIVE' },
      { id: 4, title: '최종 목적지: 중명전', description: '비밀 회담 장소 진입', answer: '비밀', targetLat: 37.5642, targetLng: 126.9733, isFinal: true, status: 'LOCKED' }
    ];
  }
};

// 💡 100% 실제 도로 길찾기 API (Kakao Mobility)
const drawActualRoute = async (startLat, startLng, endLat, endLng) => {
  const REST_API_KEY = import.meta.env.VITE_KAKAO_REST_KEY || import.meta.env.VITE_KAKAO_MAP_KEY;
  try {
    const url = `https://apis-navi.kakaomobility.com/v1/directions?origin=${startLng},${startLat}&destination=${endLng},${endLat}`;
    const response = await axios.get(url, {
      headers: { 'Authorization': `KakaoAK ${REST_API_KEY}` }
    });

    const linePath = [];
    response.data.routes[0].sections[0].roads.forEach(road => {
      for (let i = 0; i < road.vertexes.length; i += 2) {
        linePath.push(new window.kakao.maps.LatLng(road.vertexes[i+1], road.vertexes[i]));
      }
    });

    if (navPolyline) navPolyline.setMap(null);
    navPolyline = new window.kakao.maps.Polyline({
      path: linePath,
      strokeWeight: 6, strokeColor: '#ff4444', strokeOpacity: 0.9, strokeStyle: 'solid'
    });
    navPolyline.setMap(mapInstance);
  } catch (err) {
    console.error('카카오 길찾기 API 연동 실패 (REST 키가 필요합니다). 직선으로 대체합니다.', err);
    // API 키 문제 시 예외 방지를 위한 직선 대체
    if (navPolyline) navPolyline.setMap(null);
    navPolyline = new window.kakao.maps.Polyline({
      path: [new window.kakao.maps.LatLng(startLat, startLng), new window.kakao.maps.LatLng(endLat, endLng)],
      strokeWeight: 5, strokeColor: '#ff4444', strokeOpacity: 0.8, strokeStyle: 'dashed'
    });
    navPolyline.setMap(mapInstance);
  }
};

// 💡 깨짐 없는 커스텀 마커 렌더링
const renderMapAndMarkers = () => {
  const container = document.getElementById('map');
  const userPos = new window.kakao.maps.LatLng(userLocation.value.lat, userLocation.value.lng);

  if (!mapInstance) {
    mapInstance = new window.kakao.maps.Map(container, { center: userPos, level: 4 });
    mapInstance.setMapTypeId(window.kakao.maps.MapTypeId.HYBRID);
  }

  markers.forEach(m => m.setMap(null));
  markers = [];
  const bounds = new window.kakao.maps.LatLngBounds();

  // 내 위치 커스텀 마커
  const userEl = document.createElement('div');
  userEl.className = 'custom-marker user';
  const userOverlay = new window.kakao.maps.CustomOverlay({ position: userPos, content: userEl });
  userOverlay.setMap(mapInstance);
  bounds.extend(userPos);

  missions.value.forEach(mission => {
    // 힌트 3개 모으기 전까지 최종 목적지 숨김
    if (mission.isFinal && collectedHints.value < requiredHints.value) return;

    const pos = new window.kakao.maps.LatLng(mission.targetLat, mission.targetLng);

    // HTML Element로 마커 생성 (깨짐 원천 차단)
    const el = document.createElement('div');
    el.className = 'custom-marker';

    if (mission.isFinal) el.classList.add('final');
    else if (mission.status === 'CLEARED') el.classList.add('cleared');

    // 마커 클릭 시
    el.onclick = () => {
      if (mission.status === 'CLEARED') {
        alert(`[해독된 단서 기록]\n작전: ${mission.title}\n단서: ${mission.description}\n결과: ${mission.answer}`);
        return;
      }
      currentMission.value = mission;
      isArrived.value = false;
      updateDistances();
    };

    const overlay = new window.kakao.maps.CustomOverlay({ position: pos, content: el, yAnchor: 1 });
    overlay.setMap(mapInstance);
    markers.push(overlay);
    bounds.extend(pos);
  });

  // 💡 3개를 다 모으면 네비게이션 그리기 호출
  if (collectedHints.value >= requiredHints.value) {
    const finalM = missions.value.find(m => m.isFinal);
    if (finalM) {
      drawActualRoute(userLocation.value.lat, userLocation.value.lng, finalM.targetLat, finalM.targetLng);
    }
  }

  if (markers.length > 0) mapInstance.setBounds(bounds);
};

const forceArrival = () => {
  if (!currentMission.value) { alert("지도에서 탐색할 마커를 먼저 클릭하십시오."); return; }
  isArrived.value = true;
  targetDistance.value = 0;
};

const uploadImage = (imageDataUrl) => {
  isScannerOpen.value = false;
  isArrived.value = false;

  if (currentMission.value) {
    currentMission.value.status = 'CLEARED';
    collectedHints.value++;
    alert(`[SYSTEM] 단서 획득 성공.\n총 회수량: ${collectedHints.value} / 3`);

    if (collectedHints.value >= requiredHints.value) {
      currentMission.value = missions.value.find(m => m.isFinal);
    } else {
      currentMission.value = null;
    }

    updateDistances();
    renderMapAndMarkers();
  }
};

const goToChat = () => {
  const sId = currentSessionId.value || '1';
  router.push(`/chat/${sId}?regionId=${regionId}`);
};

const bootSystem = async () => {
  await fetchUserLocation();
  await loadMissions();
  renderMapAndMarkers();
  startTracking();
  updateDistances();
};

onMounted(() => {
  if (window.kakao && window.kakao.maps) {
    bootSystem();
  } else {
    const script = document.createElement('script');
    script.src = `//dapi.kakao.com/v2/maps/sdk.js?autoload=false&appkey=${import.meta.env.VITE_KAKAO_MAP_KEY}`;
    script.onload = () => window.kakao.maps.load(() => bootSystem());
    document.head.appendChild(script);
  }
});

onUnmounted(() => {
  if (trackingId && navigator.geolocation) navigator.geolocation.clearWatch(trackingId);
});
</script>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Share+Tech+Mono&display=swap');

/* ========================================================
   1. 요원님의 완벽한 원본 스타일 100% 보존
   ======================================================== */
.tactical-fullscreen { width: 100vw; height: 100vh; background: #050505; display: flex; justify-content: center; align-items: center; font-family: 'Share Tech Mono', monospace; color: #00ffcc; padding: 10px; box-sizing: border-box; overflow: hidden; }
.device-frame { width: 100%; height: 100%; max-width: 600px; background: #111; border: 2px solid #333; border-radius: 12px; box-shadow: 0 0 20px rgba(0, 0, 0, 0.8); padding: 15px; box-sizing: border-box; display: flex; flex-direction: column; gap: 10px; }
.device-header { display: flex; justify-content: space-between; align-items: center; }
.status-lights { display: flex; gap: 5px; }
.light { width: 8px; height: 8px; border-radius: 50%; }
.light.red { background: #ff4444; box-shadow: 0 0 5px #ff4444; }
.light.green { background: #00ffcc; }
.blink { animation: blinker 1s linear infinite; }
@keyframes blinker { 50% { opacity: 0; } }

.device-header h2 { margin: 0; font-size: 1rem; color: #aaa; }
.battery { font-size: 0.8rem; color: #aaa; border: 1px solid #aaa; padding: 2px 4px; border-radius: 3px; }

.screen-container { flex: 1; position: relative; width: 100%; border: 2px solid #00ffcc; border-radius: 8px; overflow: hidden; }
.map-view { width: 100%; height: 100%; }
.screen-overlay { position: absolute; inset: 0; pointer-events: none; z-index: 10; }
.scanline { background: linear-gradient(rgba(0, 255, 204, 0.05) 50%, rgba(0, 0, 0, 0.1) 50%); background-size: 100% 4px; }

.coord-overlay { position: absolute; background: rgba(0,0,0,0.8); padding: 5px 10px; font-size: 0.8rem; z-index: 11; font-weight: bold; }
.top-left { top: 10px; left: 10px; border-left: 2px solid #00ffcc; }

.control-panel { background: #0a0a0a; border: 1px solid #222; padding: 15px; border-radius: 8px; text-align: center; }

.tgt-text { margin: 0 0 5px 0; color: #00ffcc; font-size: 1rem; }
.distance { margin: 0 0 5px 0; font-size: 1.4rem; font-weight: bold; color: #fff; }
.status-text { margin: 0 0 10px 0; color: #ffaa00; font-size: 0.9rem; }
.status-text.ready { color: #00ffcc; font-weight: bold; animation: blinker 1.5s infinite; }

.override-btn { width: 100%; padding: 15px; background: transparent; color: #ffaa00; border: 1px solid #ffaa00; font-family: inherit; font-weight: bold; border-radius: 4px; cursor: pointer; }
.override-btn:hover { background: rgba(255, 170, 0, 0.2); }

.capture-btn { width: 100%; padding: 15px; background: rgba(0, 255, 204, 0.1); color: #00ffcc; border: 1px solid #00ffcc; font-family: inherit; font-weight: bold; border-radius: 4px; cursor: pointer; text-align: center; box-sizing: border-box; }
.capture-btn:hover { background: #00ffcc; color: #000; box-shadow: 0 0 15px #00ffcc; }

.scanner-modal { position: fixed; inset: 0; z-index: 1000; background: #000; }
.abort-btn { position: absolute; top: 20px; right: 20px; background: transparent; border: 1px solid #ff4444; color: #ff4444; padding: 8px 15px; z-index: 1001; cursor: pointer; font-family: inherit; font-weight: bold; }

/* ========================================================
   2. 추가된 기획 대응 요소 (원본 레이아웃 파괴 안함)
   ======================================================== */
.top-right { top: 10px; right: 10px; border-right: 2px solid #00ffcc; }
.final-dist-blink { color: #00ffcc; text-shadow: 0 0 8px #00ffcc; animation: blinker 1s linear infinite; }

.chat-btn { margin-top: 10px; border-color: #06b6d4; color: #06b6d4; padding: 10px;}
.chat-btn:hover { background: rgba(6, 182, 212, 0.2); }
.final-btn { border-color: #ff4444 !important; color: #ff4444 !important; background: rgba(255, 68, 68, 0.1) !important; margin-bottom: 5px; }
.final-btn:hover { background: #ff4444 !important; color: #fff !important; box-shadow: 0 0 15px #ff4444 !important; }

/* 💡 깨짐 방지용 커스텀 마커 CSS (이미지 로딩 X) */
:deep(.custom-marker) {
  width: 24px; height: 24px;
  background-color: rgba(0, 255, 204, 0.8); /* 기본 파랑(시안) 힌트 */
  border: 2px solid #000;
  border-radius: 50% 50% 50% 0;
  transform: rotate(-45deg);
  box-shadow: 0 0 10px #00ffcc;
  cursor: pointer;
  position: relative;
  top: -24px; left: -12px; /* yAnchor 대용 */
}
:deep(.custom-marker.cleared) {
  background-color: #777; /* 확 눈에 띄는 회색 */
  border-color: #00ffcc;  /* 테두리는 네온 유지로 가시성 확보 */
  box-shadow: none;
}
:deep(.custom-marker.final) {
  background-color: #ff4444; /* 최종 목적지 붉은색 */
  box-shadow: 0 0 15px #ff4444;
}
:deep(.custom-marker.user) {
  width: 14px; height: 14px; background-color: #fff;
  border-radius: 50%; border: 3px solid #00ffcc;
  box-shadow: 0 0 15px #00ffcc; transform: none;
  top: -7px; left: -7px; animation: pulse 1.5s infinite;
}
@keyframes pulse {
  0% { box-shadow: 0 0 0 0 rgba(0, 255, 204, 0.7); }
  70% { box-shadow: 0 0 0 12px rgba(0, 255, 204, 0); }
  100% { box-shadow: 0 0 0 0 rgba(0, 255, 204, 0); }
}
</style>