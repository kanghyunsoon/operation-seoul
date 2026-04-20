<template>
  <div class="map-container">
    <div class="header">
      <h2>📍 작전 지도: {{ currentMission ? currentMission.title : '탐색 중...' }}</h2>
      <p>목표 지점에 도달하여 단서를 확보하십시오.</p>
    </div>

    <div id="map" style="width: 100%; height: 400px; border-radius: 12px; border: 2px solid #00ffcc;"></div>

    <div class="game-ui">
      <button v-if="gameStatus === 'LOCKED'" @click="forceArrival" class="btn debug-btn">
        (테스트) 강제 도착 처리
      </button>

      <div v-if="gameStatus === 'ARRIVED'" class="action-box">
        <h3>✅ 현장 도착 완료!</h3>
        <p>현판을 촬영하여 AI에게 전송하십시오.</p>
        <input type="file" accept="image/*" capture="environment" @change="uploadImage" class="file-input" id="camera" />
        <label for="camera" class="btn camera-btn">📸 카메라 켜기</label>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import axios from 'axios';
import { useRouter } from 'vue-router';

const router = useRouter();
const missions = ref([]);
const currentMission = ref(null);
const gameStatus = ref('LOCKED');
const currentSessionId = ref(null);

// 지도 초기화 및 마커/설명창 로직
const initMap = (missionData) => {
  const container = document.getElementById('map');
  const userLat = 37.5665;
  const userLng = 126.9780;
  const userPos = new window.kakao.maps.LatLng(userLat, userLng);

  const map = new window.kakao.maps.Map(container, { center: userPos, level: 4 });

  // 마커 이미지 설정
  const blueIcon = new window.kakao.maps.MarkerImage('https://t1.daumcdn.net/localimg/localimages/07/mapapidoc/markerStar.png', new window.kakao.maps.Size(24, 35));
  const redIcon = new window.kakao.maps.MarkerImage('https://t1.daumcdn.net/localimg/localimages/07/mapapidoc/marker_red.png', new window.kakao.maps.Size(32, 32));

  // 유저 마커
  new window.kakao.maps.Marker({ position: userPos, image: blueIcon, map: map, title: '요원 위치' });

  const bounds = new window.kakao.maps.LatLngBounds();
  bounds.extend(userPos);

  // 목적지 마커 및 설명창(InfoWindow)
  missionData.forEach(m => {
    const targetPos = new window.kakao.maps.LatLng(m.targetLat, m.targetLng);
    const marker = new window.kakao.maps.Marker({ position: targetPos, image: redIcon, map: map });

    // 마커 클릭 시 나타날 설명창 내용
    const iwContent = `
      <div style="padding:10px; color:#000; font-size:12px; width:200px;">
        <strong style="color:#ff4444;">[작전 목표]</strong><br/>
        ${m.title}<br/>
        <p style="margin-top:5px; font-size:11px; color:#666;">${m.description || '현장에 접근하여 단서를 확인하라.'}</p>
      </div>
    `;
    const infowindow = new window.kakao.maps.InfoWindow({ content: iwContent, removable: true });

    // 마커 클릭 이벤트 등록
    window.kakao.maps.event.addListener(marker, 'click', () => {
      infowindow.open(map, marker);
    });

    bounds.extend(targetPos);
    if (m.id === 1) currentMission.value = m;
  });

  if (missionData.length > 0) {
    setTimeout(() => map.setBounds(bounds), 100);
  }
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

const uploadImage = async (event) => {
  const file = event.target.files[0];
  if (!file) return;
  const formData = new FormData();
  formData.append("image", file);
  try {
    alert("AI 분석 중...");
    await axios.post(`http://localhost:8080/api/v1/sessions/${currentSessionId.value}/vision`, formData, {
      headers: { "Content-Type": "multipart/form-data" }
    });
    router.push(`/chat/${currentSessionId.value}`);
  } catch (error) {
    alert("인증 실패");
  }
};

const forceArrival = async () => {
  try {
    const response = await axios.post(`http://localhost:8080/api/v1/sessions/start/1`);
    currentSessionId.value = response.data;
    gameStatus.value = 'ARRIVED';
  } catch (error) {
    console.error(error);
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
/* 🚀 로봇틱한 구글 폰트 임포트 */
@import url('https://fonts.googleapis.com/css2?family=Share+Tech+Mono&display=swap');

.map-container {
  padding: 20px;
  background-color: #1a1a1a;
  min-height: 100vh;
  color: #00ffcc;
  /* 폰트 적용 */
  font-family: 'Share Tech Mono', monospace;
}

.header { text-align: center; margin-bottom: 20px; }

.game-ui {
  margin-top: 20px;
  padding: 20px;
  background-color: #050505;
  border-radius: 12px;
  border: 1px solid #333;
  /* UI 박스 안의 텍스트들도 폰트 통일 */
  font-family: 'Share Tech Mono', monospace;
}

.btn {
  padding: 12px 20px;
  border-radius: 8px;
  border: none;
  font-weight: bold;
  cursor: pointer;
  transition: 0.3s;
  /* 버튼 글씨체 적용 */
  font-family: 'Share Tech Mono', monospace;
}

.debug-btn {
  background-color: #440000;
  color: #ff4444;
  border: 1px solid #ff4444;
  width: 100%;
  margin-bottom: 15px;
  text-transform: uppercase; /* 로봇 느낌나게 대문자로 강제 변환 */
}

.camera-btn {
  background-color: #00ffcc;
  color: #000;
  display: inline-block;
  width: 100%;
  text-align: center;
  cursor: pointer;
}

.file-input { display: none; }
.action-box { text-align: center; }
</style>