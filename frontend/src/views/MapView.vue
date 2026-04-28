<template>
  <div class="tactical-fullscreen">
    <div class="device-frame">
      <header class="device-header">
        <div class="status-lights">
          <span class="light red blink"></span>
          <span class="light green"></span>
        </div>
        <h2>📍 작전 구역: {{ currentMission?.title || '데이터 수신 중...' }}</h2>
        <div class="battery">BAT 87%</div>
      </header>

      <div class="screen-container">
        <div class="screen-overlay scanline"></div>
        <div id="map" class="map-view"></div>
        <div class="coord-overlay top-left">힌트: {{ collectedHints }} / {{ requiredHints }}</div>
      </div>

      <div class="control-panel">
        <div class="info-screen">
          <p class="tgt-text">TGT: {{ currentMission?.title || '분석중...' }}</p>
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
import { useRoute, useRouter } from 'vue-router';
import apiClient from '@/api/axiosInstance'; // 🚨 403 에러 방지를 위해 토큰이 담기는 apiClient 사용
import CameraScanner from '@/components/CameraScanner.vue';

const route = useRoute();
const router = useRouter();

const currentMission = ref(null);
const gameStatus = ref('LOCKED');
const currentSessionId = ref(null);

const isArrived = ref(false);
const isScannerOpen = ref(false);
const distance = ref(9999);

const collectedHints = ref(0);
const requiredHints = ref(3);
let mapInstance = null;

// GPS 실패 시 사용할 임의 위치 (서울 시청)
const fallbackLocation = { lat: 37.5665, lng: 126.9780 };
const userLocation = ref({ ...fallbackLocation });

// 1. GPS 위치 획득 (try-catch 및 Timeout 대응)
const fetchUserLocation = () => {
  return new Promise((resolve) => {
    if (!navigator.geolocation) {
      console.warn('GPS 미지원 기기. 임의 위치 적용');
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
        console.warn('GPS 권한 거부 또는 실패. 임의 위치 적용', err);
        userLocation.value = { ...fallbackLocation };
        resolve();
      },
      { enableHighAccuracy: true, timeout: 5000 }
    );
  });
};

// 2. 맵 초기화 및 마커 렌더링
const initMap = () => {
  if (!currentMission.value) return;

  const container = document.getElementById('map');
  const userPos = new window.kakao.maps.LatLng(userLocation.value.lat, userLocation.value.lng);
  const targetPos = new window.kakao.maps.LatLng(currentMission.value.targetLat, currentMission.value.targetLng);

  mapInstance = new window.kakao.maps.Map(container, { center: userPos, level: 4 });
  mapInstance.setMapTypeId(window.kakao.maps.MapTypeId.HYBRID);

  // 마커 아이콘 설정
  const blueIcon = new window.kakao.maps.MarkerImage('https://t1.daumcdn.net/localimg/localimages/07/mapapidoc/markerStar.png', new window.kakao.maps.Size(24, 35));
  const redIcon = new window.kakao.maps.MarkerImage('https://t1.daumcdn.net/localimg/localimages/07/mapapidoc/marker_red.png', new window.kakao.maps.Size(32, 32));

  // 본인 위치 마커
  new window.kakao.maps.Marker({ position: userPos, image: blueIcon, map: mapInstance, title: '요원 위치' });

  // 목적지 마커
  const targetMarker = new window.kakao.maps.Marker({ position: targetPos, image: redIcon, map: mapInstance });

  // 📝 목적지 마커 클릭 시 정보창(미션명) 표시
  const iwContent = `<div style="padding:10px; color:#000; font-size:12px; width:200px; font-weight:bold;">
                      <strong style="color:#ff4444;">[작전 목표]</strong><br/>${currentMission.value.title}
                    </div>`;
  const infowindow = new window.kakao.maps.InfoWindow({ content: iwContent, removable: true });
  window.kakao.maps.event.addListener(targetMarker, 'click', () => { infowindow.open(mapInstance, targetMarker); });

  // 지도 범위 재설정 (내 위치와 타겟이 모두 보이게)
  const bounds = new window.kakao.maps.LatLngBounds();
  bounds.extend(userPos);
  bounds.extend(targetPos);
  setTimeout(() => mapInstance.setBounds(bounds), 100);

  // 폴리라인으로 직선 거리 계산
  const polyline = new window.kakao.maps.Polyline({ path: [userPos, targetPos] });
  distance.value = Math.floor(polyline.getLength());
};

// 3. 데이터 로딩 메인 로직
const loadMissionAndMap = async () => {
  const missionId = route.query.missionId;
  if (!missionId) {
    alert('작전 코드가 손실되었습니다.');
    router.push('/home');
    return;
  }

  try {
    // 동적 데이터 수신
    const response = await apiClient.get(`/v1/missions/${missionId}`);
    currentMission.value = response.data;

    // GPS 위치 수신 후 지도 그리기
    await fetchUserLocation();
    initMap();
  } catch (error) {
    console.error("데이터 로드 실패", error);
    alert("작전 지역 데이터 수신에 실패했습니다.");
    router.push('/home');
  }
};

// 4. 강제 도착 처리 (기존 로직 유지, ID만 동적 할당)
const forceArrival = async () => {
  if (!currentMission.value) return;

  try {
    const response = await apiClient.post(`/v1/sessions/start/${currentMission.value.id}`);
    currentSessionId.value = response.data.id || response.data; // 서버 응답 형태에 따라 유연하게 대처
    gameStatus.value = 'ARRIVED';
    isArrived.value = true;
    distance.value = 0;
  } catch (error) {
    console.error("세션 시작 실패", error);
    alert("본부 통신 실패. 다시 시도하십시오.");
  }
};

// 5. 이미지 업로드 로직 (기존 유지, axios만 apiClient로 교체)
const uploadImage = async (imageDataUrl) => {
  isScannerOpen.value = false;

  const res = await fetch(imageDataUrl);
  const blob = await res.blob();
  const file = new File([blob], "clue.jpg", { type: "image/jpeg" });

  const formData = new FormData();
  formData.append("image", file);

  try {
    await apiClient.post(`/v1/sessions/${currentSessionId.value}/vision`, formData, {
      headers: { "Content-Type": "multipart/form-data" }
    });

    sessionStorage.setItem('capturedImage', imageDataUrl);
    router.push(`/chat/${currentSessionId.value}`);
  } catch (error) {
    alert("인증 실패: 유효한 단서를 찾을 수 없습니다.");
  }
};

onMounted(() => {
  // 카카오맵 스크립트 중복 방지 및 안전 로드
  if (window.kakao && window.kakao.maps) {
    loadMissionAndMap();
  } else {
    const script = document.createElement('script');
    script.src = `//dapi.kakao.com/v2/maps/sdk.js?autoload=false&appkey=${import.meta.env.VITE_KAKAO_MAP_KEY}`;
    script.onload = () => window.kakao.maps.load(() => loadMissionAndMap());
    document.head.appendChild(script);
  }
});
</script>

<style scoped>
/* 요원님의 기존 스타일 완벽 보존 */
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