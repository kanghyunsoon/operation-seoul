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
/**
 * 지도 제어 및 미션 인증 로직 스크립트
 */
import { ref, onMounted } from 'vue';
import axios from 'axios';
import { useRouter } from 'vue-router';

const router = useRouter();
const missions = ref([]);             // 미션 리스트 데이터
const currentMission = ref(null);     // 현재 활성화된 타겟 미션
const gameStatus = ref('LOCKED');      // 게임 진행 상태 (LOCKED, ARRIVED 등)
const currentSessionId = ref(null);    // 백엔드 통신용 세션 식별자

/**
 * 카카오맵 초기화 및 데이터 시각화 함수
 * @param {Array} missionData 백엔드에서 수신한 미션 객체 배열
 */
const initMap = (missionData) => {
  const container = document.getElementById('map');
  const userLat = 37.5665; // 기준 위도 (현장 테스트 시 GPS 연동 필요)
  const userLng = 126.9780; // 기준 경도
  const userPos = new window.kakao.maps.LatLng(userLat, userLng);

  // 지도 인스턴스 생성 및 옵션 설정
  const map = new window.kakao.maps.Map(container, { center: userPos, level: 4 });

  // 마커 이미지 자원 정의
  const blueIcon = new window.kakao.maps.MarkerImage('https://t1.daumcdn.net/localimg/localimages/07/mapapidoc/markerStar.png', new window.kakao.maps.Size(24, 35));
  const redIcon = new window.kakao.maps.MarkerImage('https://t1.daumcdn.net/localimg/localimages/07/mapapidoc/marker_red.png', new window.kakao.maps.Size(32, 32));

  // 요원(사용자) 현재 위치 마커 배치
  new window.kakao.maps.Marker({ position: userPos, image: blueIcon, map: map, title: '요원 위치' });

  // 지도 영역 확장을 위한 좌표 범위 설정 객체
  const bounds = new window.kakao.maps.LatLngBounds();
  bounds.extend(userPos);

  // 미션 데이터 반복문을 통한 마커 생성 및 이벤트 바인딩
  missionData.forEach(m => {
    const targetPos = new window.kakao.maps.LatLng(m.targetLat, m.targetLng);
    const marker = new window.kakao.maps.Marker({ position: targetPos, image: redIcon, map: map });

    // 정보창(InfoWindow) 구성: HTML 형식
    const iwContent = `
      <div style="padding:10px; color:#000; font-size:12px; width:200px;">
        <strong style="color:#ff4444;">[작전 목표]</strong><br/>
        ${m.title}<br/>
        <p style="margin-top:5px; font-size:11px; color:#666;">${m.description || '현장에 접근하여 단서를 확인하라.'}</p>
      </div>
    `;
    const infowindow = new window.kakao.maps.InfoWindow({ content: iwContent, removable: true });

    // 마커 클릭 시 정보창 노출 이벤트 등록
    window.kakao.maps.event.addListener(marker, 'click', () => {
      infowindow.open(map, marker);
    });

    bounds.extend(targetPos);
    if (m.id === 1) currentMission.value = m; // 예시로 첫 번째 미션을 타겟으로 설정
  });

  // 모든 마커가 화면에 들어오도록 지도 시야 조정
  if (missionData.length > 0) {
    setTimeout(() => map.setBounds(bounds), 100);
  }
};

/**
 * 지역별 미션 데이터 인출 함수
 * API 엔드포인트: /api/v1/regions/{id}/missions
 */
const fetchMissions = async () => {
  try {
    const response = await axios.get('http://localhost:8080/api/v1/regions/1/missions');
    missions.value = response.data;
    initMap(missions.value);
  } catch (error) {
    console.error("데이터 로드 실패", error);
    initMap([]); // 실패 시 빈 지도로 초기화
  }
};

/**
 * 이미지 업로드 및 Vision AI 인증 요청 함수
 * @param {Event} event 파일 입력 변경 이벤트
 */
const uploadImage = async (event) => {
  const file = event.target.files[0];
  if (!file) return;

  const formData = new FormData();
  formData.append("image", file); // 'image' 키로 파일 객체 래핑

  try {
    alert("AI 분석 중...");
    // 백엔드 Vision 인증 API 호출
    await axios.post(`http://localhost:8080/api/v1/sessions/${currentSessionId.value}/vision`, formData, {
      headers: { "Content-Type": "multipart/form-data" }
    });
    // 인증 성공 시 채팅 화면으로 라우팅
    router.push(`/chat/${currentSessionId.value}`);
  } catch (error) {
    alert("인증 실패: 유효한 단서를 찾을 수 없습니다.");
  }
};

/**
 * 강제 도착 처리 함수 (개발/테스트용)
 * 기능: 세션을 생성하고 ARRIVED 상태로 전환하여 카메라 기능을 활성화함
 */
const forceArrival = async () => {
  try {
    // 세션 생성 API 호출
    const response = await axios.post(`http://localhost:8080/api/v1/sessions/start/1`);
    currentSessionId.value = response.data;
    gameStatus.value = 'ARRIVED';
  } catch (error) {
    console.error("세션 시작 실패", error);
  }
};

/**
 * 생명주기 훅: 마운트 시점
 * 외부 카카오맵 스크립트를 동적으로 로드하고 완료 후 미션 로딩 수행
 */
onMounted(() => {
  const script = document.createElement('script');
  script.src = `//dapi.kakao.com/v2/maps/sdk.js?autoload=false&appkey=${import.meta.env.VITE_KAKAO_MAP_KEY}`;
  script.onload = () => window.kakao.maps.load(() => fetchMissions());
  document.head.appendChild(script);
});
</script>

<style scoped>
/* 테크니컬 테마 스타일링 */
@import url('https://fonts.googleapis.com/css2?family=Share+Tech+Mono&display=swap');

.map-container {
  padding: 20px;
  background-color: #1a1a1a;
  min-height: 100vh;
  color: #00ffcc;
  font-family: 'Share Tech Mono', monospace;
}

.header { text-align: center; margin-bottom: 20px; }

.game-ui {
  margin-top: 20px;
  padding: 20px;
  background-color: #050505;
  border-radius: 12px;
  border: 1px solid #333;
  font-family: 'Share Tech Mono', monospace;
}

.btn {
  padding: 12px 20px;
  border-radius: 8px;
  border: none;
  font-weight: bold;
  cursor: pointer;
  transition: 0.3s;
  font-family: 'Share Tech Mono', monospace;
}

.debug-btn {
  background-color: #440000;
  color: #ff4444;
  border: 1px solid #ff4444;
  width: 100%;
  margin-bottom: 15px;
  text-transform: uppercase;
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