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
        <div id="map" class="map-view" ref="mapContainer"></div>

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

        <div v-if="isArrived && (!currentMission?.isFinal)" class="target-guide">
          📸 촬영 목표: <span class="highlight">{{ currentMission?.visionKeyword }}</span>
        </div>
        <button v-if="isArrived && (!currentMission?.isFinal)" @click="isScannerOpen = true" class="capture-btn">
          [ 스캐너 가동 ]
        </button>

        <div v-if="isArrived && currentMission?.isFinal" class="target-guide">
          📸 촬영 목표: <span class="highlight">{{ currentMission?.visionKeyword }}</span>
        </div>
        <button v-if="isArrived && currentMission?.isFinal" @click="isScannerOpen = true" class="capture-btn final-btn">
          [ 목적지 진입 인증 스캔 ]
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
            <span class="highlight">[단서]: {{ m.clue }}</span>
          </li>
        </ul>
        <p v-else class="no-hints">아직 획득한 단서가 없습니다.</p>
        <button class="close-btn" @click="showHintModal = false">닫기</button>
      </div>
    </div>

  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import apiClient from '@/api/axiosInstance';
import CameraScanner from '@/components/CameraScanner.vue';

const route = useRoute();
const router = useRouter();
const mapContainer = ref(null);

const regionName = ref('조회 중...');
const isArrived = ref(false);
const currentTargetName = ref('타겟 미지정 (마커를 선택하세요)');
const targetDistance = ref(0);
const finalDistance = ref(999);
const showHintModal = ref(false);
const isScannerOpen = ref(false);
const collectedHints = ref(0);
const requiredHints = ref(3);

const currentMission = ref(null);
const clearedMissions = ref([]);

const currentLat = ref(null);
const currentLng = ref(null);

const regionId = route.query.regionId || 1;
const missions = ref([]);
let map = null;
let userMarker = null;
let gpsWatcherId = null;
let markerOverlays = [];

// 🚨 말풍선(오버레이) 객체를 하나만 유지하기 위한 변수
let activeTooltipOverlay = null;

const calculateDistance = (lat1, lon1, lat2, lon2) => {
  const R = 6371e3;
  const p1 = lat1 * Math.PI / 180;
  const p2 = lat2 * Math.PI / 180;
  const dp = (lat2 - lat1) * Math.PI / 180;
  const dl = (lon2 - lon1) * Math.PI / 180;
  const a = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return Math.floor(R * c);
};

// 미클리어 마커 클릭
const handleMissionClick = (mission) => {
  // 열려있는 말풍선 닫기
  if (activeTooltipOverlay) {
    activeTooltipOverlay.setMap(null);
    activeTooltipOverlay = null;
  }

  currentMission.value = mission;
  currentTargetName.value = mission.title;

  if (currentLat.value !== null && currentLng.value !== null) {
    targetDistance.value = calculateDistance(
      currentLat.value,
      currentLng.value,
      mission.targetLat,
      mission.targetLng
    );
    isArrived.value = targetDistance.value <= 50;
  } else {
    targetDistance.value = 999;
    isArrived.value = false;
  }
};

// 🚨 클리어된 마커 클릭 시 말풍선 토글 함수
const toggleTooltip = (mission, latLng) => {
  // 이미 열려있고, 같은 미션 마커를 눌렀다면 닫기
  if (activeTooltipOverlay && activeTooltipOverlay.getTitle() === mission.id.toString()) {
    activeTooltipOverlay.setMap(null);
    activeTooltipOverlay = null;
    return;
  }

  // 다른 곳이 열려있으면 먼저 닫기
  if (activeTooltipOverlay) {
    activeTooltipOverlay.setMap(null);
  }

  // 말풍선 DOM 생성
  const content = document.createElement('div');
  content.className = 'marker-tooltip';
  content.innerHTML = `
    <h4>${mission.title}</h4>
    <p>${mission.clue}</p>
  `;

  // 닫기 버튼 또는 말풍선 자체 클릭 시 닫히게 설정
  content.onclick = () => {
    if (activeTooltipOverlay) activeTooltipOverlay.setMap(null);
    activeTooltipOverlay = null;
  };

  // 마커 위에 띄울 새 오버레이 생성 (yAnchor를 조절하여 마커 위로 올림)
  activeTooltipOverlay = new window.kakao.maps.CustomOverlay({
    map: map,
    position: latLng,
    content: content,
    yAnchor: 2.2, // 숫자가 커질수록 오버레이가 위로 올라감 (마커와 겹치지 않게 조절)
    zIndex: 10
  });

  // 식별용 타이틀 강제 주입 (토글 로직용)
  activeTooltipOverlay.getTitle = () => mission.id.toString();
};


const loadMissionsData = async () => {
  try {
    const misRes = await apiClient.get(`/v1/regions/${regionId}/missions`);
    missions.value = misRes.data;

    markerOverlays.forEach(overlay => overlay.setMap(null));
    markerOverlays = [];

    // 데이터 갱신 시 기존 말풍선 초기화
    if (activeTooltipOverlay) {
      activeTooltipOverlay.setMap(null);
      activeTooltipOverlay = null;
    }

    clearedMissions.value = missions.value.filter(m => m.sessionStatus === 'CLEARED');
    collectedHints.value = clearedMissions.value.length;

    missions.value.forEach((mission) => {
      const isCleared = mission.sessionStatus === 'CLEARED';

      const content = document.createElement('div');
      content.className = isCleared ? 'custom-marker cleared' : (mission.isFinal ? 'custom-marker final' : 'custom-marker');

      const position = new window.kakao.maps.LatLng(mission.targetLat, mission.targetLng);

      content.onclick = () => {
        if (isCleared) {
           toggleTooltip(mission, position);
        } else {
          handleMissionClick(mission);
        }
      };

      const customOverlay = new window.kakao.maps.CustomOverlay({
        map: map,
        position: position,
        content: content,
        yAnchor: 1,
        xAnchor: 0.5,
        zIndex: 2
      });

      markerOverlays.push(customOverlay);
    });

  } catch (error) {
    console.error("미션 데이터 갱신 중 오류:", error);
  }
};

const startGpsTracking = () => {
  const executeFakeGpsFallback = () => {
    let baseLat = 37.5665;
    let baseLng = 126.9780;

    if (currentMission.value && currentMission.value.targetLat) {
      baseLat = currentMission.value.targetLat;
      baseLng = currentMission.value.targetLng;
    } else if (missions.value.length > 0) {
      baseLat = missions.value[0].targetLat;
      baseLng = missions.value[0].targetLng;
    }

    const fakeLat = baseLat - 0.0003;
    const fakeLng = baseLng - 0.0003;
    const fakePosition = new window.kakao.maps.LatLng(fakeLat, fakeLng);

    currentLat.value = fakeLat;
    currentLng.value = fakeLng;

    if (!userMarker) {
      const userContent = document.createElement('div');
      userContent.className = 'custom-marker user';

      userMarker = new window.kakao.maps.CustomOverlay({
        map: map,
        position: fakePosition,
        content: userContent,
        yAnchor: 0.5,
        xAnchor: 0.5,
        zIndex: 3
      });
    } else {
      userMarker.setPosition(fakePosition);
    }

    if (currentMission.value && currentMission.value.targetLat) {
      targetDistance.value = calculateDistance(fakeLat, fakeLng, currentMission.value.targetLat, currentMission.value.targetLng);
      isArrived.value = targetDistance.value <= 50;
    }
    map.setCenter(fakePosition);
  };

  if (navigator.geolocation) {
    gpsWatcherId = navigator.geolocation.watchPosition((position) => {
      const lat = position.coords.latitude;
      const lng = position.coords.longitude;
      const locPosition = new window.kakao.maps.LatLng(lat, lng);

      currentLat.value = lat;
      currentLng.value = lng;

      if (!userMarker) {
        const userContent = document.createElement('div');
        userContent.className = 'custom-marker user';

        userMarker = new window.kakao.maps.CustomOverlay({
          map: map,
          position: locPosition,
          content: userContent,
          yAnchor: 0.5,
          xAnchor: 0.5,
          zIndex: 3
        });
      } else {
        userMarker.setPosition(locPosition);
      }

      if (currentMission.value && currentMission.value.targetLat) {
        targetDistance.value = calculateDistance(lat, lng, currentMission.value.targetLat, currentMission.value.targetLng);
        isArrived.value = targetDistance.value <= 50;
      }
    }, (error) => {
      executeFakeGpsFallback();
    }, { enableHighAccuracy: true, maximumAge: 0, timeout: 5000 });
  } else {
    executeFakeGpsFallback();
  }
};

onMounted(() => {
  if (!window.kakao || !window.kakao.maps) return;

  window.kakao.maps.load(async () => {
    const options = { center: new window.kakao.maps.LatLng(37.5665, 126.9780), level: 4 };
    map = new window.kakao.maps.Map(mapContainer.value, options);
    map.setMapTypeId(window.kakao.maps.MapTypeId.HYBRID);

    try {
      const regRes = await apiClient.get(`/v1/regions/${regionId}`);
      regionName.value = regRes.data.name;

      await loadMissionsData();

      if (missions.value.length > 0) {
        map.setCenter(new window.kakao.maps.LatLng(missions.value[0].targetLat, missions.value[0].targetLng));
      }
      startGpsTracking();

    } catch (error) {
      currentTargetName.value = '데이터 수신 실패';
    }
  });
});

onUnmounted(() => {
  if (gpsWatcherId && navigator.geolocation) {
    navigator.geolocation.clearWatch(gpsWatcherId);
  }
});

const goToChat = () => {
  if (currentMission.value) {
    router.push(`/chat/${currentMission.value.id}`);
  } else {
    alert("먼저 지도에서 작전을 수행할 마커를 선택해 주십시오.");
  }
};

const forceArrival = () => {
  if(!currentMission.value) return;
  isArrived.value = true;
};

const uploadImage = async (imageFile) => {
  let finalFile = imageFile;

  if (typeof imageFile === 'string' && imageFile.startsWith('data:image')) {
    try {
      const arr = imageFile.split(',');
      const mime = arr[0].match(/:(.*?);/)[1];
      const bstr = atob(arr[1]);
      let n = bstr.length;
      const u8arr = new Uint8Array(n);
      while (n--) { u8arr[n] = bstr.charCodeAt(n); }
      finalFile = new File([u8arr], 'capture.png', { type: mime });
    } catch (e) {
      return;
    }
  }

  if (!finalFile || !(finalFile instanceof File)) return;

  try {
    const formData = new FormData();
    formData.append('image', finalFile);

    const response = await apiClient.post(`/v1/sessions/${currentMission.value.id}/vision`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    });

    if (response.data.success) {
      alert(`[분석 성공] 단서를 찾았습니다! 목표 확인 완료.`);

      await loadMissionsData();

      currentMission.value = null;
      currentTargetName.value = '타겟 미지정 (마커를 선택하세요)';
      isArrived.value = false;
      targetDistance.value = 0;

    } else {
       alert("[분석 실패] 목표물을 정확히 프레임에 담아주십시오.");
    }
  } catch (error) {
    alert("본부와의 통신 연결이 원활하지 않습니다.");
  } finally {
    isScannerOpen.value = false;
  }
};
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

/* 🚨 타겟 안내 문구 CSS */
.target-guide { margin-bottom: 8px; font-size: 0.9rem; color: #bbb; }
.target-guide .highlight { color: #ffaa00; font-weight: bold; }

.override-btn { width: 100%; padding: 15px; background: transparent; color: #ffaa00; border: 1px solid #ffaa00; font-family: inherit; font-weight: bold; border-radius: 4px; cursor: pointer; }
.override-btn:hover { background: rgba(255, 170, 0, 0.2); }

.capture-btn { width: 100%; padding: 12px; background: rgba(0, 255, 204, 0.1); color: #00ffcc; border: 1px solid #00ffcc; font-family: inherit; font-weight: bold; border-radius: 4px; cursor: pointer; text-align: center; box-sizing: border-box; }
.capture-btn:hover { background: #00ffcc; color: #000; box-shadow: 0 0 15px #00ffcc; }
.final-btn { border-color: #ff4444 !important; color: #ff4444 !important; background: rgba(255, 68, 68, 0.1) !important; margin-top: 5px; }
.final-btn:hover { background: #ff4444 !important; color: #fff !important; box-shadow: 0 0 15px #ff4444 !important; }

.scanner-modal { position: fixed; inset: 0; z-index: 1000; background: #000; }
.abort-btn { position: absolute; top: 20px; right: 20px; background: transparent; border: 1px solid #ff4444; color: #ff4444; padding: 8px 15px; z-index: 1001; cursor: pointer; font-family: inherit; font-weight: bold; }

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

:deep(.custom-marker) { width: 24px; height: 24px; background-color: rgba(0, 255, 204, 0.8); border: 2px solid #000; border-radius: 50% 50% 50% 0; transform: rotate(-45deg); box-shadow: 0 0 10px #00ffcc; cursor: pointer; position: relative; top: -24px; left: -12px; }
:deep(.custom-marker.cleared) { background-color: #8fa3a3; border: 2px solid #fff; box-shadow: 0 0 5px rgba(255, 255, 255, 0.5); opacity: 0.95; }
:deep(.custom-marker.final) { background-color: #ff4444; box-shadow: 0 0 15px #ff4444; }

:deep(.custom-marker.user) {
  width: 16px;
  height: 16px;
  background-color: #ff007a;
  border-radius: 50%;
  border: 2px solid #fff;
  box-shadow: 0 0 15px #ff007a;
  transform: translate(-50%, -50%);
  position: absolute;
  animation: pulse-gps 4s infinite;
}

/* 🚨 마커 말풍선 전용 CSS */
:deep(.marker-tooltip) {
  /* 배경은 어두운 남색 계열로 유지, 테두리와 폰트 색상을 변경해 가독성을 높입니다. */
  background: rgba(10, 20, 30, 0.95);
  border: 1px solid #00ffcc;
  padding: 10px 14px;
  border-radius: 8px;
  color: white;
  text-align: center;
  cursor: pointer;
  /* 최소 너비는 유지하되, 텍스트가 길면 자동으로 줄바꿈 되도록 최대 너비 제한 */
  min-width: 200px;
  max-width: 300px;
  white-space: normal;
  box-shadow: 0 4px 15px rgba(0, 255, 204, 0.5);
  position: relative;
  font-family: 'Share Tech Mono', monospace;
  /* 말풍선이 항상 마커 바로 위 중앙에 뜨도록 위치 조정 */
  bottom: -100px;
}

:deep(.marker-tooltip h4) {
  margin: 0 0 6px 0;
  color: #00ffcc;
  font-size: 0.9rem;
  border-bottom: 1px solid rgba(0, 255, 204, 0.3);
  padding-bottom: 4px;
}

:deep(.marker-tooltip p) {
  margin: 0;
  font-size: 0.8rem;
  color: #f0f0f0;
  line-height: 1.3;
}

/* 말풍선 꼬리표 (아래쪽 뾰족한 삼각형 모양) */
:deep(.marker-tooltip::after) {
  content: '';
  position: absolute;
  bottom: -8px;
  left: 46%;
  transform: translateX(-50%);
  border-width: 8px 8px 0;
  border-style: solid;
  border-color: rgba(10, 20, 30, 0.95) transparent transparent transparent;
  /* 꼬리표에도 네온 효과를 살짝 줌 */
  filter: drop-shadow(0 2px 2px rgba(0,255,204,0.4));
}
</style>