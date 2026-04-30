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
import { ref, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import apiClient from '@/api/axiosInstance';
// 🚨 누락되었던 카메라 스캐너 컴포넌트 임포트 복구
import CameraScanner from '@/components/CameraScanner.vue';

const route = useRoute();
const router = useRouter();
const mapContainer = ref(null);

// --- 🚨 템플릿(HTML)에서 찾지 못해 에러가 났던 변수들 모두 복구 ---
const regionName = ref('조회 중...');
const isArrived = ref(false); // GPS 도착 여부
const currentTargetName = ref('통신 연결 중...'); // 현재 목적지 이름
const targetDistance = ref(0); // 현재 목적지와의 거리
const finalDistance = ref(0); // 최종 목적지와의 거리
const showHintModal = ref(false); // 힌트 모달창 상태
const isScannerOpen = ref(false); // 카메라 스캐너 상태
const collectedHints = ref(0); // 수집한 힌트 개수
const requiredHints = ref(3); // 필요 힌트 개수
// -----------------------------------------------------------------

const regionId = route.query.regionId || 1;
const missions = ref([]);
let map = null;

onMounted(async () => {
  if (!window.kakao || !window.kakao.maps) {
    console.error("🚨 Kakao Maps API 로드 실패: index.html에 실제 앱 키를 넣었는지 확인하세요.");
    return;
  }

  const mapOption = {
    center: new window.kakao.maps.LatLng(37.5665, 126.9780),
    level: 4
  };
  map = new window.kakao.maps.Map(mapContainer.value, mapOption);

  try {
    // 1. 지역 이름 세팅
    const regRes = await apiClient.get(`/v1/regions/${regionId}`);
    regionName.value = regRes.data.name;

    // 2. 미션 마커 세팅
    const misRes = await apiClient.get(`/v1/regions/${regionId}/missions`);
    missions.value = misRes.data;

    if (missions.value.length > 0) {
      // 첫 번째 미션으로 타겟 세팅 및 지도 중심 이동
      currentTargetName.value = missions.value[0].title;
      map.setCenter(new window.kakao.maps.LatLng(missions.value[0].targetLat, missions.value[0].targetLng));

      // 마커 그리기
      missions.value.forEach((m, idx) => {
        const position = new window.kakao.maps.LatLng(m.targetLat, m.targetLng);
        const content = document.createElement('div');
        content.className = 'custom-marker';
        if (m.isFinal) content.classList.add('final-target');
        content.innerHTML = `
          <div class="marker-core"></div>
          <div class="marker-ring"></div>
          <div class="marker-label">${m.isFinal ? 'TGT' : idx + 1}</div>
        `;

        new window.kakao.maps.CustomOverlay({
          map: map,
          position: position,
          content: content,
          yAnchor: 1
        });
      });
    }
  } catch (error) {
    console.error("데이터 로드 중 오류 발생:", error);
    currentTargetName.value = '데이터 수신 실패';
  }
});

const goToChat = () => {
  // 실제 세션 ID로 라우팅되도록 추후 수정 가능 (현재 임시 1번)
  router.push('/chat/1');
};

// 카메라 스캔 성공 시 처리 로직
const handleScanSuccess = (keyword) => {
  isScannerOpen.value = false;
  console.log("스캔된 키워드:", keyword);
  // 추후 API 연동을 통해 힌트 획득 로직 연결
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