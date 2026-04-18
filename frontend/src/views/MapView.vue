<template>
  <div class="map-container">
    <div class="header">
      <h2>📍 작전 지도: 덕수궁 중명전</h2>
      <p>목표 지점에 도달하여 단서를 확보하십시오.</p>
    </div>
    <div id="map" style="width: 100%; height: 550px; border-radius: 12px; border: 2px solid #00ffcc;"></div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'; // ref 추가
import axios from 'axios';

const missions = ref([]); // 미션 데이터 담을 공간
const appKey = import.meta.env.VITE_KAKAO_MAP_KEY;

// 1. DB에서 미션 정보 가져오기
const fetchMissions = async (map) => {
  try {
    // 경로에 /v1 추가 확인
    const response = await axios.get('http://localhost:8080/api/v1/missions');
    missions.value = response.data;

    if (missions.value.length > 0) {
      displayMarkers(map, missions.value);
    }
  } catch (error) {
    console.error("데이터 로딩 실패:", error);
  }
};

// 2. 지도에 마커 뿌리기
const displayMarkers = (map, missionList) => {
  missionList.forEach((mission) => {
    const markerPosition = new window.kakao.maps.LatLng(mission.targetLat, mission.targetLng);
    const marker = new window.kakao.maps.Marker({
      position: markerPosition,
      map: map
    });

    // 인포윈도우 설정
    const iwContent = `
      <div style="padding:10px; color:#333; width:200px;">
        <strong style="font-size:14px;">${mission.title}</strong><br>
        <small>정답 키워드: ${mission.answerKeyword}</small>
      </div>`;

    const infowindow = new window.kakao.maps.InfoWindow({
      content: iwContent,
      removable: true
    });

    window.kakao.maps.event.addListener(marker, 'click', () => {
      infowindow.open(map, marker);
    });

    // 첫 번째 미션 위치로 지도 중심 이동 (중명전으로 이동)
    map.setCenter(markerPosition);
  });
};

onMounted(() => {
  // 카카오맵 스크립트 로드
  const script = document.createElement('script');
  script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${appKey}&autoload=false`;

  script.onload = () => {
    window.kakao.maps.load(() => {
      const container = document.getElementById('map');
      const options = {
        center: new window.kakao.maps.LatLng(37.5642, 126.9733), // 기본 중명전 좌표
        level: 3
      };
      const map = new window.kakao.maps.Map(container, options);

      // 지도 객체가 생성된 후 DB 데이터를 가져옵니다.
      fetchMissions(map);
    });
  };
  document.head.appendChild(script);
});
</script>

<style scoped>
/* 기존 스타일 유지 */
.map-container {
  padding: 20px;
  background-color: #1a1a1a;
  min-height: 100vh;
  color: #00ffcc;
}
.header { text-align: center; margin-bottom: 20px; }
</style>