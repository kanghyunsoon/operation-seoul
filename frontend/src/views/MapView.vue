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
import { ref, onMounted, onUnmounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import apiClient from '@/api/axiosInstance';
import CameraScanner from '@/components/CameraScanner.vue';

const route = useRoute();
const router = useRouter();
const mapContainer = ref(null);

const regionName = ref('조회 중...');
const isArrived = ref(false); 
const currentTargetName = ref('타겟 미지정 (마커를 선택하세요)'); // 💡 초기 상태 안내 텍스트
const targetDistance = ref(0);
const finalDistance = ref(999);
const showHintModal = ref(false);
const isScannerOpen = ref(false);
const collectedHints = ref(0);
const requiredHints = ref(3);
const currentMission = ref(null);
const clearedMissions = ref([]);

// 🚨 현재 요원의 위경도를 실시간으로 저장할 변수 (마커 클릭 시 즉시 거리 계산용)
const currentLat = ref(null);
const currentLng = ref(null);

const regionId = route.query.regionId || 1;
const missions = ref([]);
let map = null;
let userMarker = null;
let activeInfoOverlay = null;
let gpsWatcherId = null;

// 두 좌표 간 거리 계산 (단위: 미터)
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

// 📍 GPS 추적 시스템 가동
const startGpsTracking = () => {
  // 🚨 임시 GPS 가동 함수
  const executeFakeGpsFallback = () => {
    console.log("🚨 임시 GPS 가동: 요원 위치를 강제 배치합니다.");

    // 타겟이 있으면 그 근처, 없으면 1번 미션 기준, 그것도 없으면 기본 서울 좌표
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

    // 즉시 계산을 위해 상태 저장
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

    // 현재 선택된 타겟이 있을 때만 거리 계산 적용
    if (currentMission.value && currentMission.value.targetLat) {
      targetDistance.value = calculateDistance(fakeLat, fakeLng, currentMission.value.targetLat, currentMission.value.targetLng);
      isArrived.value = targetDistance.value <= 50;
    }

    map.setCenter(fakePosition);
  };

  if (navigator.geolocation) {
    console.log("📡 GPS 위성 링크 활성화 시도 중...");

    const options = {
      enableHighAccuracy: true,
      maximumAge: 0,
      timeout: 5000
    };

    gpsWatcherId = navigator.geolocation.watchPosition((position) => {
      const lat = position.coords.latitude;
      const lng = position.coords.longitude;
      const locPosition = new window.kakao.maps.LatLng(lat, lng);

      // 실시간 위치 전역 저장
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

      // 타겟이 지정되어 있을 때만 거리 및 도착 여부 검사
      if (currentMission.value && currentMission.value.targetLat) {
        targetDistance.value = calculateDistance(lat, lng, currentMission.value.targetLat, currentMission.value.targetLng);
        isArrived.value = targetDistance.value <= 50;
      }
    }, (error) => {
      console.error("🚨 GPS 신호 유실 또는 권한 거부:", error.message);
      alert("GPS 신호를 잡을 수 없거나 권한이 없습니다. 테스트용 임시 좌표를 가동합니다.");
      executeFakeGpsFallback();
    }, options);
  } else {
    alert("이 브라우저는 GPS 위치 추적을 지원하지 않습니다. 임시 좌표를 가동합니다.");
    executeFakeGpsFallback();
  }
};

onMounted(() => {
  if (!window.kakao || !window.kakao.maps) {
    console.error("🚨 Kakao Maps API 로드 실패. index.html의 키를 확인하세요.");
    return;
  }

  window.kakao.maps.load(async () => {
    const options = {
      center: new window.kakao.maps.LatLng(37.5665, 126.9780),
      level: 4
    };
    map = new window.kakao.maps.Map(mapContainer.value, options);
    map.setMapTypeId(window.kakao.maps.MapTypeId.HYBRID);

    try {
      const regRes = await apiClient.get(`/v1/regions/${regionId}`);
      regionName.value = regRes.data.name;

      const misRes = await apiClient.get(`/v1/regions/${regionId}/missions`);
      missions.value = misRes.data;

      if (missions.value.length > 0) {
        // 💡 초기 진입 시 타겟을 잡지 않고 지도의 중심만 첫 미션으로 이동시킴
        currentMission.value = null;
        map.setCenter(new window.kakao.maps.LatLng(missions.value[0].targetLat, missions.value[0].targetLng));

        missions.value.forEach((m, idx) => {
          if (!m.targetLat || !m.targetLng) return;

          const position = new window.kakao.maps.LatLng(m.targetLat, m.targetLng);
          const content = document.createElement('div');
          content.className = 'custom-marker';

          if (m.sessionStatus === 'CLEARED') content.classList.add('cleared');
          if (m.isFinal) content.classList.add('final-target');

          content.innerHTML = `
            <div class="marker-core"></div>
            <div class="marker-ring"></div>
            <div class="marker-label">${m.isFinal ? 'TGT' : idx + 1}</div>
          `;

          // 🚨 [핵심 수정] 마커 클릭 시 타겟 활성화 및 토글 해제 기능
          content.addEventListener('click', () => {

            // 1️⃣ 이미 선택된 타겟 마커를 다시 클릭한 경우 -> 선택 해제 (Toggle OFF)
            if (currentMission.value && currentMission.value.id === m.id) {
              if (activeInfoOverlay) {
                activeInfoOverlay.setMap(null);
                activeInfoOverlay = null;
              }
              currentMission.value = null;
              currentTargetName.value = '타겟 미지정 (마커를 선택하세요)';
              targetDistance.value = 0;
              isArrived.value = false;
              return; // 함수 조기 종료
            }

            // 2️⃣ 새로운 마커를 클릭한 경우 -> 타겟 변경 (Toggle ON)
            if (activeInfoOverlay) activeInfoOverlay.setMap(null); // 다른 오버레이 닫기

            currentMission.value = m;
            currentTargetName.value = m.title;

            // GPS 데이터가 파악된 상태라면 클릭 즉시 거리 계산하여 하단 패널에 반영
            if (currentLat.value !== null && currentLng.value !== null) {
              targetDistance.value = calculateDistance(currentLat.value, currentLng.value, m.targetLat, m.targetLng);
              isArrived.value = targetDistance.value <= 50;
            } else {
              targetDistance.value = 0;
            }

            const infoContent = document.createElement('div');
            infoContent.className = 'info-overlay-content';
            infoContent.innerHTML = `
              <div class="title">${m.title}</div>
              <div class="desc">${m.visionKeyword ? '목표 단서: ' + m.visionKeyword : '최종 목적지'}</div>
              <div class="status ${m.sessionStatus || 'ACTIVE'}">${m.isFinal ? '[ FINAL TARGET ]' : (m.sessionStatus === 'CLEARED' ? '[ CLEARED ]' : '진행 대기')}</div>
            `;

            activeInfoOverlay = new window.kakao.maps.CustomOverlay({
              map: map,
              position: position,
              content: infoContent,
              yAnchor: 1.5
            });
          });

          new window.kakao.maps.CustomOverlay({
            map: map,
            position: position,
            content: content,
            yAnchor: 1
          });
        });
      }

      startGpsTracking();

    } catch (error) {
      console.error("데이터 로드 중 오류 발생:", error);
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
  if(!currentMission.value) {
    alert("타겟을 먼저 선택해주십시오.");
    return;
  }
  isArrived.value = true;
};

// 📸 카메라 스캔 파일 전송
const uploadImage = async (imageFile) => {
  let finalFile = imageFile;

  // 🚨 [수정됨] CameraScanner가 Base64(텍스트 URL)로 사진을 넘길 경우 강제로 File 객체 변환
  if (typeof imageFile === 'string' && imageFile.startsWith('data:image')) {
    try {
      const arr = imageFile.split(',');
      const mime = arr[0].match(/:(.*?);/)[1];
      const bstr = atob(arr[1]);
      let n = bstr.length;
      const u8arr = new Uint8Array(n);
      while (n--) {
        u8arr[n] = bstr.charCodeAt(n);
      }
      finalFile = new File([u8arr], 'capture.png', { type: mime });
    } catch (e) {
      alert("이미지 변환 중 오류가 발생했습니다.");
      return;
    }
  }

  if (!finalFile || !(finalFile instanceof File)) {
      alert("이미지 파일 획득에 실패했습니다. 다시 촬영해 주십시오.");
      return;
  }

  try {
    const formData = new FormData();
    formData.append('image', finalFile);

   const response = await apiClient.post(`/v1/sessions/${currentMission.value.id}/vision`, formData, {
     headers: {
       'Content-Type': 'multipart/form-data'
     }
   });

    if (response.data.success) {
       alert(`[분석 성공] 단서를 찾았습니다! 목표 확인 완료.`);
       collectedHints.value++;
    } else {
       alert("[분석 실패] 목표물을 정확히 프레임에 담아주십시오.");
    }
  } catch (error) {
    console.error("🚨 비전 AI 통신 오류:", error);
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

.override-btn { width: 100%; padding: 15px; background: transparent; color: #ffaa00; border: 1px solid #ffaa00; font-family: inherit; font-weight: bold; border-radius: 4px; cursor: pointer; }
.override-btn:hover { background: rgba(255, 170, 0, 0.2); }

.capture-btn { width: 100%; padding: 15px; background: rgba(0, 255, 204, 0.1); color: #00ffcc; border: 1px solid #00ffcc; font-family: inherit; font-weight: bold; border-radius: 4px; cursor: pointer; text-align: center; box-sizing: border-box; }
.capture-btn:hover { background: #00ffcc; color: #000; box-shadow: 0 0 15px #00ffcc; }
.final-btn { border-color: #ff4444 !important; color: #ff4444 !important; background: rgba(255, 68, 68, 0.1) !important; margin-top: 10px; }
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

/* 🚨 내 위치(GPS) 마커 CSS 스타일 보강 */
:deep(.custom-marker.user) { 
  width: 16px; 
  height: 16px; 
  background-color: #ff007a; 
  border-radius: 50%; 
  border: 2px solid #fff; 
  box-shadow: 0 0 15px #ff007a; 
  transform: translate(-50%, -50%); /* 정확히 정중앙에 위치하도록 수정 */
  position: absolute; /* position 속성 추가 */
  animation: pulse-gps 4s infinite; 
}
@keyframes pulse-gps { 
  0% { box-shadow: 0 0 0 0 rgba(255, 0, 122, 0.7); } 
  70% { box-shadow: 0 0 0 15px rgba(255, 0, 122, 0); } 
  100% { box-shadow: 0 0 0 0 rgba(255, 0, 122, 0); } 
}
</style>