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

      <div v-if="gameStatus === 'PHOTO_VERIFIED' || gameStatus === 'CLEARED'" class="chat-box">
        <div class="chat-history">
          <div v-for="(msg, index) in chatHistory" :key="index" :class="['chat-bubble', msg.sender]">
            <strong>{{ msg.sender === 'ai' ? '🤖 AI 요원' : '나' }}:</strong> {{ msg.text }}
          </div>
        </div>

        <div v-if="gameStatus !== 'CLEARED'" class="chat-input-area">
          <input v-model="userInput" @keyup.enter="sendChat" placeholder="정답을 입력하세요..." class="chat-input" />
          <button @click="sendChat" class="btn send-btn">전송</button>
        </div>
        <div v-else class="clear-message">
          🎉 작전 성공! 다음 미션을 대기하십시오.
        </div>
      </div>

    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import axios from 'axios';

// 환경변수 및 상태 관리
const appKey = import.meta.env.VITE_KAKAO_MAP_KEY;
const currentMission = ref(null);
const currentSessionId = ref(null);
const gameStatus = ref('LOCKED'); // 상태: LOCKED -> ARRIVED -> PHOTO_VERIFIED -> CLEARED
const chatHistory = ref([]);
const userInput = ref('');

// 1. 지도 렌더링 및 마커 표시
const fetchMissions = async (map) => {
  try {
    const response = await axios.get('http://localhost:8080/api/v1/regions/1/missions');
    const missions = response.data;

    if (missions.length > 0) {
      currentMission.value = missions[0]; // 첫 번째 미션을 타겟으로 설정

      missions.forEach((mission) => {
        const markerPosition = new window.kakao.maps.LatLng(mission.targetLat, mission.targetLng);
        const marker = new window.kakao.maps.Marker({ position: markerPosition, map: map });
        const infowindow = new window.kakao.maps.InfoWindow({
          content: `<div style="padding:10px; color:#333; width:150px;"><strong>${mission.title}</strong></div>`
        });
        window.kakao.maps.event.addListener(marker, 'click', () => { infowindow.open(map, marker); });
      });
    }
  } catch (error) {
    console.error("미션 로딩 실패:", error);
  }
};

// 2. [테스트용] 강제 도착 처리 (실제로는 GPS 값으로 checkArrival 호출)
const forceArrival = async () => {
  if(!currentMission.value) return;
  try {
    // 세션 생성 API 호출
    const response = await axios.post(`http://localhost:8080/api/v1/sessions/start/${currentMission.value.id}`);
    currentSessionId.value = response.data; // 서버가 준 sessionId 저장
    gameStatus.value = 'ARRIVED'; // UI 상태 변경
    alert("목표 지점에 도착했습니다! 단서를 촬영하세요.");
  } catch (error) {
    console.error("세션 생성 실패:", error);
  }
};

// 3. 사진 업로드 (Vision AI 모의 호출)
const uploadImage = async (event) => {
  const file = event.target.files[0];
  if (!file || !currentSessionId.value) return;

  const formData = new FormData();
  formData.append("image", file);

  try {
    alert("AI가 이미지를 분석 중입니다...");
    const response = await axios.post(`http://localhost:8080/api/v1/sessions/${currentSessionId.value}/vision`, formData, {
      headers: { "Content-Type": "multipart/form-data" }
    });

    gameStatus.value = 'PHOTO_VERIFIED';
    chatHistory.value.push({ sender: 'ai', text: response.data });
  } catch (error) {
    alert("인증 실패: " + (error.response?.data || "이미지를 다시 촬영해주세요."));
  }
};

// 4. 채팅 전송 (Gemini AI 모의 호출)
const sendChat = async () => {
  if (!userInput.value.trim() || !currentSessionId.value) return;

  const question = userInput.value;
  chatHistory.value.push({ sender: 'user', text: question });
  userInput.value = '';

  try {
    const response = await axios.post(`http://localhost:8080/api/v1/sessions/${currentSessionId.value}/chat`, {
      userAnswer: question
    });

    chatHistory.value.push({ sender: 'ai', text: response.data });

    // 모의 응답에 '훌륭하다'가 포함되어 있으면 클리어 처리
    if(response.data.includes("훌륭하다")) {
      gameStatus.value = 'CLEARED';
    }
  } catch (error) {
    console.error("채팅 전송 실패:", error);
  }
};

onMounted(() => {
  const script = document.createElement('script');
  script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${appKey}&autoload=false`;
  script.onload = () => {
    window.kakao.maps.load(() => {
      const map = new window.kakao.maps.Map(document.getElementById('map'), {
        center: new window.kakao.maps.LatLng(37.5642, 126.9733),
        level: 3
      });
      fetchMissions(map);
    });
  };
  document.head.appendChild(script);
});
</script>

<style scoped>
.map-container { padding: 20px; background-color: #1a1a1a; min-height: 100vh; color: #00ffcc; font-family: sans-serif; }
.header { text-align: center; margin-bottom: 20px; }
.game-ui { margin-top: 20px; padding: 20px; background-color: #2a2a2a; border-radius: 12px; }
.btn { padding: 10px 20px; border-radius: 8px; border: none; font-weight: bold; cursor: pointer; }
.debug-btn { background-color: #ff4444; color: white; width: 100%; margin-bottom: 10px;}
.camera-btn { background-color: #00ffcc; color: #000; display: inline-block; width: 100%; text-align: center; cursor: pointer; }
.file-input { display: none; }
.action-box, .chat-box { text-align: center; }
.chat-history { height: 200px; overflow-y: auto; background: #111; padding: 10px; border-radius: 8px; margin-bottom: 10px; text-align: left; }
.chat-bubble { margin-bottom: 10px; padding: 8px 12px; border-radius: 8px; max-width: 80%; display: inline-block;}
.chat-bubble.ai { background-color: #004433; color: #00ffcc; float: left; clear: both; }
.chat-bubble.user { background-color: #333; color: white; float: right; clear: both; }
.chat-input-area { display: flex; gap: 10px; }
.chat-input { flex: 1; padding: 10px; border-radius: 8px; border: none; }
.send-btn { background-color: #00ffcc; color: #000; }
.clear-message { color: #ffcc00; font-size: 1.2rem; font-weight: bold; margin-top: 10px; }
</style>