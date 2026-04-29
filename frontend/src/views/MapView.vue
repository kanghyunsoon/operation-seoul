<template>
  <div class="tactical-fullscreen">
    <div class="device-frame">
      <header class="device-header">
        <div class="status-lights">
          <span class="light red" :class="{ blink: !isArrived }"></span>
          <span class="light green" :class="{ blink: isArrived }"></span>
        </div>
        <h2>📍 작전 구역: {{ regionName }}</h2>
        <div class="battery">BAT 87%</div>
      </header>

      <div class="screen-container">
        <div class="screen-overlay scanline"></div>
        <div id="map" class="map-view"></div>

        <button class="hint-collection-btn" @click="showHintModal = true">
          💡 획득한 단서 {{ collectedHints }} / {{ requiredHints }}
        </button>

        <div v-if="collectedHints >= 1" class="coord-overlay top-right" :class="{ 'final-dist-blink': isArrived }">
          최종 TGT DIST: {{ finalDistance }}m
        </div>

        <div class="floating-chat-btn" @click="goToChat">
          <div class="chat-icon">
            <svg viewBox="0 0 24 24" width="28" height="28" fill="currentColor">
              <path d="M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zM9 11H7V9h2v2zm4 0h-2V9h2v2zm4 0h-2V9h2v2z"/>
            </svg>
          </div>
        </div>
      </div>

      <div class="control-panel">
        <div class="info-screen">
          <p class="tgt-text">TGT: {{ currentTargetName }}</p>
          <p class="distance">DIST: {{ isArrived ? '0' : targetDistance }}m</p>
          <p class="status-text" :class="{ 'ready blink-fast': isArrived }">
            {{ isArrived ? '> SIGNAL_LOCKED: 현장 도착 완료!' : '> 이동 중 (TRACKING...)' }}
          </p>
        </div>

        <button v-if="!isArrived" @click="forceArrival" class="override-btn">
          [ MANUAL_OVERRIDE : 강제 도착 ]
        </button>

        <button v-if="isArrived && (!currentMission?.isFinal)" @click="isScannerOpen = true" class="capture-btn">
          [ 📸 현장 단서 스캐너 가동 ]
        </button>

        <button v-if="isArrived && currentMission?.isFinal" @click="isScannerOpen = true" class="capture-btn final-btn">
          [ 📸 목적지 진입 인증 (스캔) ]
        </button>
      </div>
    </div>

    <div v-if="isScannerOpen" class="scanner-modal">
      <CameraScanner @capture="uploadImage" @close="isScannerOpen = false" />
      <button @click="isScannerOpen = false" class="abort-btn">ABORT_SCAN</button>
    </div>

    <div v-if="showHintModal" class="hint-modal-overlay" @click="showHintModal = false">
      <div class="hint-modal-content" @click.stop>
        <h3>🔍 분석 완료된 단서 목록</h3>
        <ul v-if="clearedMissions.length > 0">
          <li v-for="m in clearedMissions" :key="m.id">
            📍 {{ m.title }} <br>
            <span class="highlight">[단서]: {{ m.answer }}</span>
          </li>
        </ul>
        <p v-else class="no-hints">아직 획득한 단서가 없습니다.</p>
        <button class="close-btn" @click="showHintModal = false">닫기</button>
      </div>
    </div>

  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import axios from 'axios';
import apiClient from '@/api/axiosInstance';
import CameraScanner from '@/components/CameraScanner.vue';
import { useSessionStore } from '@/stores/sessionStore';

const route = useRoute();
const router = useRouter();
const sessionStore = useSessionStore();

const regionId = route.query.regionId || 1;
const regionName = ref('서울 시청 작전구역');

const currentMission = ref(null);
const currentSessionId = ref(null);
const isArrived = ref(false);
const isScannerOpen = ref(false);
// 💡 모달 상태 관리 변수 추가
const showHintModal = ref(false);

const collectedHints = ref(0);
const requiredHints = ref(3);

const targetDistance = ref('----');
const finalDistance = ref('----');


let mapInstance = null;
let markers = [];
let navPolyline = null;
let trackingId = null;
let activeInfoOverlay = null;

const missions = ref([]);

const fallbackLocation = { lat: 37.5665, lng: 126.9780 };
const userLocation = ref({ ...fallbackLocation });

// 💡 획득한 힌트만 모아서 모달에 보여주기 위한 Computed 속성
const clearedMissions = computed(() => {
  return missions.value.filter(m => m.status === 'CLEARED' && !m.isFinal);
});

const currentTargetName = computed(() => {
  if (collectedHints.value >= requiredHints.value) return '최종 목적지 해금됨 (경로 탐색 중)';
  return currentMission.value ? currentMission.value.title : '지도에서 힌트 마커를 선택하십시오.';
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

  if (currentMission.value && currentMission.value.targetLat) {
    const targetPos = new window.kakao.maps.LatLng(currentMission.value.targetLat, currentMission.value.targetLng);
    const polylineTarget = new window.kakao.maps.Polyline({ path: [userPos, targetPos] });
    targetDistance.value = Math.floor(polylineTarget.getLength());
  }

  const finalM = missions.value.find(m => m.isFinal);
  if (finalM && finalM.targetLat) {
    const finalPos = new window.kakao.maps.LatLng(finalM.targetLat, finalM.targetLng);
    const polylineFinal = new window.kakao.maps.Polyline({ path: [userPos, finalPos] });
    finalDistance.value = Math.floor(polylineFinal.getLength());
  }
};

const loadMissions = async () => {
  if (sessionStore.mapProgress && sessionStore.mapProgress.regionId === regionId) {
    missions.value = sessionStore.mapProgress.missions;
    collectedHints.value = sessionStore.mapProgress.collectedHints;
    requiredHints.value = sessionStore.mapProgress.requiredHints || 3;
    return;
  }

  try {
    const res = await apiClient.get(`/v1/regions/${regionId}/missions`);
    if (res.data && res.data.length >= 4) {
      missions.value = res.data;
    } else {
      throw new Error("DB 데이터 부족");
    }
  } catch (err) {
    missions.value = [
      { id: 1, title: '단서 1: 덕수궁 대한문', description: '매표소 옆 게시판 확인.', answer: '요금', targetLat: 37.5658, targetLng: 126.9751, isFinal: false, status: 'ACTIVE' },
      { id: 2, title: '단서 2: 시청 광장', description: '잔디밭 앞 석판 확인.', answer: '숫자', targetLat: 37.5663, targetLng: 126.9779, isFinal: false, status: 'ACTIVE' },
      { id: 3, title: '단서 3: 무교동 사거리', description: '길안내 표지판 거리 확인.', answer: '방향', targetLat: 37.5678, targetLng: 126.9785, isFinal: false, status: 'ACTIVE' },
      { id: 4, title: '최종 목적지: 중명전', description: '비밀 회담 장소 진입', answer: '비밀', targetLat: 37.5642, targetLng: 126.9733, isFinal: true, status: 'LOCKED' }
    ];
  }
};

const drawActualRoute = async (startLat, startLng, endLat, endLng) => {
  const TMAP_KEY = import.meta.env.VITE_TMAP_APP_KEY || '발급받은_TMAP_키를_여기에_넣으세요';

  if (!TMAP_KEY || TMAP_KEY === '발급받은_TMAP_키를_여기에_넣으세요') {
    console.warn("🚨 [경로 에러]: VITE_TMAP_APP_KEY가 없습니다. 직선으로 대체됩니다.");
    drawFallbackLine(startLat, startLng, endLat, endLng);
    return;
  }

  try {
    const response = await axios.post(
      'https://apis.openapi.sk.com/tmap/routes/pedestrian?version=1&format=json',
      {
        startX: startLng.toString(),
        startY: startLat.toString(),
        endX: endLng.toString(),
        endY: endLat.toString(),
        reqCoordType: "WGS84GEO",
        resCoordType: "WGS84GEO",
        startName: "출발지",
        endName: "목적지"
      },
      { headers: { appKey: TMAP_KEY } }
    );

    const linePath = [];
    const features = response.data.features;

    features.forEach(feature => {
      if (feature.geometry.type === "LineString") {
        feature.geometry.coordinates.forEach(coord => {
          linePath.push(new window.kakao.maps.LatLng(coord[1], coord[0]));
        });
      }
    });

    if (navPolyline) navPolyline.setMap(null);

    navPolyline = new window.kakao.maps.Polyline({
      path: linePath,
      strokeWeight: 6,
      strokeColor: '#ff007a',
      strokeOpacity: 0.5,
      strokeStyle: 'solid'
    });
    navPolyline.setMap(mapInstance);
  } catch (err) {
    console.error('🚨 [경로 에러]: TMAP 도보 API 통신 실패', err);
    drawFallbackLine(startLat, startLng, endLat, endLng);
  }
};

const drawFallbackLine = (startLat, startLng, endLat, endLng) => {
  if (navPolyline) navPolyline.setMap(null);
  navPolyline = new window.kakao.maps.Polyline({
    path: [new window.kakao.maps.LatLng(startLat, startLng), new window.kakao.maps.LatLng(endLat, endLng)],
    strokeWeight: 5, strokeColor: '#00ffcc', strokeOpacity: 0.6, strokeStyle: 'shortdash'
  });
  navPolyline.setMap(mapInstance);
};

const renderMapAndMarkers = () => {
  const container = document.getElementById('map');
  const userPos = new window.kakao.maps.LatLng(userLocation.value.lat, userLocation.value.lng);

  if (!mapInstance) {
    mapInstance = new window.kakao.maps.Map(container, { center: userPos, level: 4 });
    mapInstance.setMapTypeId(window.kakao.maps.MapTypeId.HYBRID);
  }

  markers.forEach(m => { if (m.setMap) m.setMap(null); });
  markers = [];
  const bounds = new window.kakao.maps.LatLngBounds();

  const userEl = document.createElement('div');
  userEl.className = 'custom-marker user';
  const userOverlay = new window.kakao.maps.CustomOverlay({ position: userPos, content: userEl });
  userOverlay.setMap(mapInstance);
  bounds.extend(userPos);

  missions.value.forEach(mission => {
    // 💡 수정됨: 최종 목적지 마커 숨김 로직 제거 (항상 떠 있도록)
    // if (mission.isFinal && collectedHints.value < requiredHints.value) return;

    const pos = new window.kakao.maps.LatLng(mission.targetLat, mission.targetLng);
    const el = document.createElement('div');
    el.className = 'custom-marker';

    if (mission.isFinal) el.classList.add('final');
    else if (mission.status === 'CLEARED') el.classList.add('cleared');

    const infoEl = document.createElement('div');
    infoEl.className = 'info-overlay-content';
    infoEl.innerHTML = `
      <div class="title">📍 ${mission.title}</div>
      <div class="desc">${mission.description}</div>
      <div class="status ${mission.status}">
        ${mission.status === 'CLEARED' ? `[획득 단서]: <span class="highlight">${mission.answer}</span>` : '단서 미확인'}
      </div>
    `;
    const infoOverlay = new window.kakao.maps.CustomOverlay({
      position: pos, content: infoEl, yAnchor: 2.2, zIndex: 10
    });

    let isInfoOpen = false;

    el.onclick = () => {
      if (activeInfoOverlay && activeInfoOverlay !== infoOverlay) activeInfoOverlay.setMap(null);

      if (isInfoOpen) {
        infoOverlay.setMap(null);
      } else {
        infoOverlay.setMap(mapInstance);
        activeInfoOverlay = infoOverlay;
      }
      isInfoOpen = !isInfoOpen;

      if (mission.status !== 'CLEARED') {
        currentMission.value = mission;
        isArrived.value = false;
        updateDistances();
      }
    };

    const overlay = new window.kakao.maps.CustomOverlay({ position: pos, content: el, yAnchor: 1 });
    overlay.setMap(mapInstance);

    markers.push(overlay);
    markers.push({ setMap: (map) => infoOverlay.setMap(map) });
    bounds.extend(pos);
  });

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

// 💡 수정됨: 비전 AI 예외처리 로직 (try-catch 우회)
const uploadImage = async (imageDataUrl) => {
  isScannerOpen.value = false;

  try {
    // 백엔드 비전 AI 통신 코드가 들어갈 자리입니다.
    // 예: const response = await apiClient.post('/api/v1/vision/scan', ...);

    // 에러 발생을 대비하여 무조건 임시 성공 처리 로직으로 넘깁니다.
    processMissionClear();
  } catch (error) {
    console.error("비전 AI 연결 끊김:", error);
    alert("통신 위성(Vision AI) 연결이 불안정합니다. 작전 속행을 위해 임시 통과 처리합니다.");
    processMissionClear();
  }
};

// 실제 미션 클리어 상태 관리 로직 분리
const processMissionClear = () => {
  isArrived.value = false;

  if (currentMission.value) {
    currentMission.value.status = 'CLEARED';

    if (currentMission.value.isFinal) {
      alert(`[SYSTEM] 목적지 진입 완료. 지휘부에서 통신이 수신되었습니다.`);

      if (navPolyline) navPolyline.setMap(null);

      missions.value = [
        { id: 901, title: '내부 조사: 1구역', description: '관련 유물 확인', answer: '문서', targetLat: currentMission.value.targetLat + 0.0001, targetLng: currentMission.value.targetLng + 0.0001, isFinal: false, status: 'ACTIVE' },
        { id: 903, title: '최종 지휘부 교신', description: '모든 단서를 모아 통신 연결', answer: '최종', targetLat: currentMission.value.targetLat, targetLng: currentMission.value.targetLng, isFinal: true, status: 'LOCKED' }
      ];
      collectedHints.value = 0;
      requiredHints.value = 1;
      currentMission.value = null;

    } else {
      collectedHints.value++;
      alert(`[SYSTEM] 단서 획득 성공.\n총 회수량: ${collectedHints.value} / ${requiredHints.value}`);

      if (collectedHints.value >= requiredHints.value) {
        currentMission.value = missions.value.find(m => m.isFinal);
        alert(`[SYSTEM] 모든 단서가 모였습니다. 최종 목적지 경로가 활성화됩니다.`);
      } else {
        currentMission.value = null;
      }
    }

    sessionStore.mapProgress = {
      regionId, missions: missions.value, collectedHints: collectedHints.value, requiredHints: requiredHints.value
    };

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

.tactical-fullscreen { width: 100vw; height: 100vh; background: #050505; display: flex; justify-content: center; align-items: center; font-family: 'Share Tech Mono', monospace; color: #00ffcc; padding: 10px; box-sizing: border-box; overflow: hidden; }
.device-frame { width: 100%; height: 100%; max-width: 600px; background: #111; border: 2px solid #333; border-radius: 12px; box-shadow: 0 0 20px rgba(0, 0, 0, 0.8); padding: 15px; box-sizing: border-box; display: flex; flex-direction: column; gap: 10px; position: relative; }
.device-header { display: flex; justify-content: space-between; align-items: center; }
.status-lights { display: flex; gap: 5px; }
.light { width: 8px; height: 8px; border-radius: 50%; }
.light.red { background: #ff4444; box-shadow: 0 0 5px #ff4444; }
.light.green { background: #00ffcc; }

.blink { animation: blinker 2s linear infinite; }
@keyframes blinker { 50% { opacity: 0.3; } }

.device-header h2 { margin: 0; font-size: 1rem; color: #aaa; }
.battery { font-size: 0.8rem; color: #aaa; border: 1px solid #aaa; padding: 2px 4px; border-radius: 3px; }

.screen-container { flex: 1; position: relative; width: 100%; border: 2px solid #00ffcc; border-radius: 8px; overflow: hidden; }
.map-view { width: 100%; height: 100%; }
.screen-overlay { position: absolute; inset: 0; pointer-events: none; z-index: 10; }
.scanline { background: linear-gradient(rgba(0, 255, 204, 0.05) 50%, rgba(0, 0, 0, 0.1) 50%); background-size: 100% 4px; }

/* 💡 수정됨: 힌트 모아보기 버튼 스타일 추가 */
.hint-collection-btn {
  position: absolute; top: 15px; left: 15px;
  padding: 10px 16px; font-size: 0.9rem; font-weight: bold;
  background-color: rgba(0, 20, 30, 0.85); color: #00ffcc;
  border: 2px solid #00ffcc; border-radius: 6px;
  z-index: 1000; cursor: pointer;
  box-shadow: 0 0 10px rgba(0, 255, 204, 0.4);
  font-family: inherit; transition: all 0.2s ease;
}
.hint-collection-btn:hover { background-color: #00ffcc; color: #000; }

.coord-overlay { position: absolute; background: rgba(0,0,0,0.8); padding: 5px 10px; font-size: 0.8rem; z-index: 11; font-weight: bold; }
.top-right { top: 15px; right: 15px; border-right: 2px solid #00ffcc; }

.final-dist-blink { color: #00ffcc; text-shadow: 0 0 10px #00ffcc; animation: blinker-fast 0.8s linear infinite; }
.blink-fast { animation: blinker-fast 0.8s linear infinite; }
@keyframes blinker-fast { 50% { opacity: 0; } }

.control-panel { background: #0a0a0a; border: 1px solid #222; padding: 15px; border-radius: 8px; text-align: center; }

.tgt-text { margin: 0 0 5px 0; color: #00ffcc; font-size: 1rem; }
.distance { margin: 0 0 5px 0; font-size: 1.4rem; font-weight: bold; color: #fff; }
.status-text { margin: 0 0 10px 0; color: #ffaa00; font-size: 0.9rem; }
.status-text.ready { color: #00ffcc; font-weight: bold; }

.override-btn { width: 100%; padding: 15px; background: transparent; color: #ffaa00; border: 1px solid #ffaa00; font-family: inherit; font-weight: bold; border-radius: 4px; cursor: pointer; }
.override-btn:hover { background: rgba(255, 170, 0, 0.2); }

.capture-btn { width: 100%; padding: 15px; background: rgba(0, 255, 204, 0.1); color: #00ffcc; border: 1px solid #00ffcc; font-family: inherit; font-weight: bold; border-radius: 4px; cursor: pointer; text-align: center; box-sizing: border-box; }
.capture-btn:hover { background: #00ffcc; color: #000; box-shadow: 0 0 15px #00ffcc; }
.final-btn { border-color: #ff4444 !important; color: #ff4444 !important; background: rgba(255, 68, 68, 0.1) !important; margin-top: 10px; }
.final-btn:hover { background: #ff4444 !important; color: #fff !important; box-shadow: 0 0 15px #ff4444 !important; }

.scanner-modal { position: fixed; inset: 0; z-index: 1000; background: #000; }
.abort-btn { position: absolute; top: 20px; right: 20px; background: transparent; border: 1px solid #ff4444; color: #ff4444; padding: 8px 15px; z-index: 1001; cursor: pointer; font-family: inherit; font-weight: bold; }

/* 💡 수정됨: 힌트 모달 창 스타일 */
.hint-modal-overlay {
  position: fixed; top: 0; left: 0; width: 100vw; height: 100vh;
  background: rgba(0, 0, 0, 0.8); display: flex; justify-content: center; align-items: center; z-index: 2000;
}
.hint-modal-content {
  background: #111; border: 2px solid #00ffcc; padding: 25px;
  border-radius: 12px; width: 85%; max-width: 400px; color: white;
  box-shadow: 0 0 20px rgba(0, 255, 204, 0.3);
}
.hint-modal-content h3 { color: #00ffcc; margin-top: 0; margin-bottom: 20px; border-bottom: 1px dashed #00ffcc; padding-bottom: 10px;}
.hint-modal-content ul { list-style: none; padding: 0; margin: 0; line-height: 1.8; }
.hint-modal-content li { margin-bottom: 15px; background: rgba(0, 255, 204, 0.05); padding: 10px; border-radius: 6px; }
.no-hints { color: #aaa; text-align: center; margin: 20px 0; }
.close-btn { margin-top: 15px; width: 100%; padding: 12px; background: transparent; border: 1px solid #ff4444; color: #ff4444; font-family: inherit; font-weight: bold; border-radius: 8px; cursor: pointer; transition: 0.2s;}
.close-btn:hover { background: #ff4444; color: #fff; }

.floating-chat-btn {
  position: absolute; right: 15px; bottom: 15px;
  width: 55px; height: 55px; background: rgba(0, 40, 60, 0.85);
  border: 2px solid #00ffcc; border-radius: 50%;
  display: flex; justify-content: center; align-items: center;
  box-shadow: 0 0 10px rgba(0, 255, 204, 0.4);
  cursor: pointer; z-index: 99; transition: all 0.3s ease; color: #00ffcc;
}
.floating-chat-btn:hover { background: #00ffcc; color: #000; box-shadow: 0 0 20px rgba(0, 255, 204, 0.8); }

:deep(.info-overlay-content) {
  background: rgba(0, 15, 30, 0.95); border: 1px solid #00ffcc; border-radius: 6px;
  padding: 10px 15px; color: #fff; text-align: center;
  font-family: 'Share Tech Mono', monospace;
  box-shadow: 0 0 15px rgba(0,255,204,0.5); pointer-events: none; width: 180px;
}
:deep(.info-overlay-content .title) { font-size: 0.9rem; font-weight: bold; border-bottom: 1px solid #00ffcc; margin-bottom: 5px; padding-bottom: 5px; }
:deep(.info-overlay-content .desc) { font-size: 0.75rem; color: #aaa; margin-bottom: 5px; }
:deep(.info-overlay-content .status.CLEARED) { color: #888; }
:deep(.info-overlay-content .status.ACTIVE) { color: #ffaa00; }
:deep(.info-overlay-content .highlight) { color: #00ffcc; font-weight: bold; text-decoration: underline; }

:deep(.custom-marker) { width: 24px; height: 24px; background-color: rgba(0, 255, 204, 0.8); border: 2px solid #000; border-radius: 50% 50% 50% 0; transform: rotate(-45deg); box-shadow: 0 0 10px #00ffcc; cursor: pointer; position: relative; top: -24px; left: -12px; }
:deep(.custom-marker.cleared) { background-color: #555; border-color: #00ffcc; box-shadow: none; }
:deep(.custom-marker.final) { background-color: #ff4444; box-shadow: 0 0 15px #ff4444; }
:deep(.custom-marker.user) { width: 16px; height: 16px; background-color: #ff007a; border-radius: 50%; border: 2px solid #fff; box-shadow: 0 0 15px #ff007a; transform: none; top: -8px; left: -8px; animation: pulse-gps 4s infinite; }
@keyframes pulse-gps { 0% { box-shadow: 0 0 0 0 rgba(255, 0, 122, 0.7); } 70% { box-shadow: 0 0 0 15px rgba(255, 0, 122, 0); } 100% { box-shadow: 0 0 0 0 rgba(255, 0, 122, 0); } }
</style>