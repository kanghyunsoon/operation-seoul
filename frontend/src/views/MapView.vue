<template>
  <div class="tactical-fullscreen">
    <div class="device-frame">
      <header class="device-header">
        <div class="status-lights">
          <span class="light red blink"></span>
          <span class="light green"></span>
        </div>
        <h2>📍 작전 구역: 정동길의 비밀</h2>
        <div class="battery">BAT 87%</div>
      </header>

      <div class="screen-container">
        <div class="screen-overlay scanline"></div>
        <div id="map" class="map-view"></div>
        <div class="coord-overlay top-left">힌트: {{ collectedHints }} / {{ requiredHints }}</div>
      </div>

      <div class="control-panel">
        <div class="info-screen">
          <p class="tgt-text">TGT: 중명전(重明殿)</p>
          <p class="distance">DIST: {{ isArrived ? '0' : distance }}m</p>
          <p class="status-text" :class="{ 'ready': isArrived }">
            {{ isArrived ? '> SIGNAL_LOCKED: 현장 도착 완료!' : '> 이동 중 (TRACKING...)' }}
          </p>
        </div>

        <button v-if="!isArrived" @click="forceArrival" class="override-btn">
          [ MANUAL_OVERRIDE : 강제 도착 ]
        </button>

        <button v-if="isArrived" @click="isScannerOpen = true" class="capture-btn">
          [ 📸 단서 스캐너 가동 ]
        </button>
      </div>
    </div>

    <div v-if="isScannerOpen" class="scanner-modal">
      <CameraScanner @capture="uploadImage" />
      <button @click="isScannerOpen = false" class="abort-btn">ABORT_SCAN</button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import axios from 'axios';
import { useRouter } from 'vue-router';
import CameraScanner from '@/components/CameraScanner.vue'; // 스캐너 컴포넌트 임포트 복구!

const router = useRouter();
const missions = ref([]);
const currentMission = ref(null);
const gameStatus = ref('LOCKED');
const currentSessionId = ref(null);

const isArrived = ref(false);
const isScannerOpen = ref(false);
const distance = ref(100);

const collectedHints = ref(0);
const requiredHints = ref(3);
let mapInstance = null;

const initMap = (missionData) => {
  const container = document.getElementById('map');
  const userPos = new window.kakao.maps.LatLng(37.5665, 126.9780);

  if (!mapInstance) {
    mapInstance = new window.kakao.maps.Map(container, { center: userPos, level: 4 });
  } else {
    mapInstance = new window.kakao.maps.Map(container, { center: userPos, level: 4 });
  }

  const map = mapInstance;
  map.setMapTypeId(window.kakao.maps.MapTypeId.HYBRID);

  const blueIcon = new window.kakao.maps.MarkerImage('https://t1.daumcdn.net/localimg/localimages/07/mapapidoc/markerStar.png', new window.kakao.maps.Size(24, 35));
  const redIcon = new window.kakao.maps.MarkerImage('https://t1.daumcdn.net/localimg/localimages/07/mapapidoc/marker_red.png', new window.kakao.maps.Size(32, 32));

  new window.kakao.maps.Marker({ position: userPos, image: blueIcon, map: map, title: '요원 위치' });

  const bounds = new window.kakao.maps.LatLngBounds();
  bounds.extend(userPos);

  missionData.forEach(m => {
    if (m.isFinal && collectedHints.value < requiredHints.value) return;

    const targetPos = new window.kakao.maps.LatLng(m.targetLat, m.targetLng);
    const marker = new window.kakao.maps.Marker({ position: targetPos, image: redIcon, map: map });

    let iwContent = `<div style="padding:10px; color:#000; font-size:12px; width:200px;"><strong style="color:#ff4444;">[작전 목표]</strong><br/>${m.title}</div>`;
    if(m.isFinal) {
      iwContent = `<div style="padding:10px; color:#000; font-size:12px; width:200px; border: 2px solid red;"><strong style="color:red;">🚨 [최종 비밀 목적지 발견!]</strong><br/>${m.title}</div>`;
    }

    const infowindow = new window.kakao.maps.InfoWindow({ content: iwContent, removable: true });
    window.kakao.maps.event.addListener(marker, 'click', () => { infowindow.open(map, marker); });

    bounds.extend(targetPos);
    if (m.id === 1) currentMission.value = m;
  });

  if (missionData.length > 0) setTimeout(() => map.setBounds(bounds), 100);
};

const fetchMissions = async () => {
  try {
    const response = await axios.get('http://localhost:8080/api/v1/regions/1/missions');
    missions.value = response.data;
    initMap(missions.value);
  } catch (error) {
    console.error("데이터 로드 실패", error);
    initMap([]);
  }
};

const forceArrival = async () => {
  try {
    const response = await axios.post(`http://localhost:8080/api/v1/sessions/start/1`);
    currentSessionId.value = response.data;
    gameStatus.value = 'ARRIVED';
    isArrived.value = true; // 거리 0 처리용
    distance.value = 0;

    collectedHints.value++;
    if(collectedHints.value >= requiredHints.value) {
      alert("🚨 모든 단서를 모았습니다! 지도에 숨겨진 최종 목적지가 나타납니다.");
      initMap(missions.value);
    }
  } catch (error) {
    console.error("세션 시작 실패", error);
  }
};

// 💡 캡처된 Base64 데이터를 File로 변환하여 요원님의 Vision API 로 전송
const uploadImage = async (imageDataUrl) => {
  isScannerOpen.value = false;

  // Base64 -> Blob -> File 변환 과정
  const res = await fetch(imageDataUrl);
  const blob = await res.blob();
  const file = new File([blob], "clue.jpg", { type: "image/jpeg" });

  const formData = new FormData();
  formData.append("image", file);

  try {
    // 1. 백엔드 비전 API로 검증 (요원님 원본 로직)
    await axios.post(`http://localhost:8080/api/v1/sessions/${currentSessionId.value}/vision`, formData, {
      headers: { "Content-Type": "multipart/form-data" }
    });

    // 2. 검증 성공 시에만 세션 스토리지에 이미지 담고 채팅방 이동
    sessionStorage.setItem('capturedImage', imageDataUrl);
    router.push(`/chat/${currentSessionId.value}`);
  } catch (error) {
    alert("인증 실패: 유효한 단서를 찾을 수 없습니다.");
  }
};

onMounted(() => {
  const script = document.createElement('script');
  script.src = `//dapi.kakao.com/v2/maps/sdk.js?autoload=false&appkey=${import.meta.env.VITE_KAKAO_MAP_KEY}`;
  script.onload = () => window.kakao.maps.load(() => fetchMissions());
  document.head.appendChild(script);
});
</script>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Share+Tech+Mono&display=swap');

.tactical-fullscreen {
  width: 100vw; height: 100vh; background: #050505;
  display: flex; justify-content: center; align-items: center;
  font-family: 'Share Tech Mono', monospace; color: #00ffcc;
  padding: 10px; box-sizing: border-box; overflow: hidden;
}

.device-frame {
  width: 100%; height: 100%; max-width: 600px;
  background: #111; border: 2px solid #333; border-radius: 12px;
  box-shadow: 0 0 20px rgba(0, 0, 0, 0.8);
  padding: 15px; box-sizing: border-box;
  display: flex; flex-direction: column; gap: 10px;
}

.device-header { display: flex; justify-content: space-between; align-items: center; }
.status-lights { display: flex; gap: 5px; }
.light { width: 8px; height: 8px; border-radius: 50%; }
.light.red { background: #ff4444; box-shadow: 0 0 5px #ff4444; }
.light.green { background: #00ffcc; }
.blink { animation: blinker 1s linear infinite; }
@keyframes blinker { 50% { opacity: 0; } }

.device-header h2 { margin: 0; font-size: 1rem; color: #aaa; }
.battery { font-size: 0.8rem; color: #aaa; border: 1px solid #aaa; padding: 2px 4px; border-radius: 3px; }

.screen-container {
  flex: 1; position: relative; width: 100%;
  border: 2px solid #00ffcc; border-radius: 8px; overflow: hidden;
}

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

.override-btn {
  width: 100%; padding: 15px; background: transparent; color: #ffaa00;
  border: 1px solid #ffaa00; font-family: inherit; font-weight: bold; border-radius: 4px; cursor: pointer;
}
.override-btn:hover { background: rgba(255, 170, 0, 0.2); }

.capture-btn {
  width: 100%; padding: 15px; background: rgba(0, 255, 204, 0.1);
  color: #00ffcc; border: 1px solid #00ffcc; font-family: inherit; font-weight: bold;
  border-radius: 4px; cursor: pointer; text-align: center; box-sizing: border-box;
}
.capture-btn:hover { background: #00ffcc; color: #000; box-shadow: 0 0 15px #00ffcc; }

/* 스캐너 모달 */
.scanner-modal { position: fixed; inset: 0; z-index: 1000; background: #000; }
.abort-btn { position: absolute; top: 20px; right: 20px; background: transparent; border: 1px solid #ff4444; color: #ff4444; padding: 8px 15px; z-index: 1001; cursor: pointer; font-family: inherit; font-weight: bold; }
</style>