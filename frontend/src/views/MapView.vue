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
const userInput = ref(''); // 채팅 입력 변수 통일

// 1. 지도 렌더링 및 마커 표시
const fetchMissions = async (map) => {
  try {
    const response = await axios.get('http://localhost:8080/api/v1/regions/1/missions');
    const missions = response.data;

    if (missions.length > 0) {
      currentMission.value = missions[0];

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

// 2. 강제 도착 처리
const forceArrival = async () => {
  if(!currentMission.value) return;
  try {
    const response = await axios.post(`http://localhost:8080/api/v1/sessions/start/${currentMission.value.id}`);
    currentSessionId.value = response.data;
    gameStatus.value = 'ARRIVED';
    alert("목표 지점에 도착했습니다! 단서를 촬영하세요.");
  } catch (error) {
    console.error("세션 생성 실패:", error);
  }
};

// ⌨️ 프론트엔드 전용 타자기 효과 함수 (🚨 버그 수정: index로 직접 접근)
const typeWriter = (fullText, messageIndex) => {
  let i = 0;
  chatHistory.value[messageIndex].text = ''; // 빈칸으로 시작
  const speed = 50; // 0.05초마다 한 글자

  const typing = setInterval(() => {
    if (i < fullText.length) {
      // 🚨 핵심: Proxy 배열에 직접 접근해서 글자를 더해야 Vue가 실시간으로 화면을 그립니다!
      chatHistory.value[messageIndex].text += fullText.charAt(i);
      i++;

      // 스크롤 맨 아래로 유지
      const chatContainer = document.querySelector('.chat-history');
      if (chatContainer) chatContainer.scrollTop = chatContainer.scrollHeight;
    } else {
      clearInterval(typing);
    }
  }, speed);
};

// 3. 사진 업로드 (Vision AI 연동) 및 AI 선톡 발사!
const uploadImage = async (event) => {
  const file = event.target.files[0];
  if (!file || !currentSessionId.value) return;

  const formData = new FormData();
  formData.append("image", file);

  try {
    alert("AI가 이미지를 분석 중입니다...");
    await axios.post(`http://localhost:8080/api/v1/sessions/${currentSessionId.value}/vision`, formData, {
      headers: { "Content-Type": "multipart/form-data" }
    });

    gameStatus.value = 'PHOTO_VERIFIED'; // 채팅창 엶

    // AI 빈 말풍선 넣고 그 위치(index) 기억하기
    chatHistory.value.push({ sender: 'ai', text: '' });
    const aiMessageIndex = chatHistory.value.length - 1;

    // 화면 멈춤 없이 바로 타자기 효과 발동!
    typeWriter("현장 도착을 확인했다 요원. 주위를 둘러보고 암호를 입력하라.", aiMessageIndex);

  } catch (error) {
    alert("인증 실패: " + (error.response?.data || "이미지를 다시 촬영해주세요."));
  }
};

// 4. 채팅 전송 (진짜 실시간 스트리밍 - SSE 방식)
// 4. 채팅 전송 (진짜 실시간 스트리밍 - 무조건 타다다닥 나오게 수정)
const sendChat = async () => {
  if (!userInput.value.trim() || !currentSessionId.value) return;

  const question = userInput.value;
  chatHistory.value.push({ sender: 'user', text: question });
  userInput.value = '';

  // 1. AI 말풍선을 미리 추가 (초기값 빈칸)
  const aiMessageIndex = chatHistory.value.length;
  chatHistory.value.push({ sender: 'ai', text: '' });

  try {
    const response = await fetch(`http://localhost:8080/api/v1/sessions/${currentSessionId.value}/chat/stream`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ userAnswer: question })
    });

    const reader = response.body.getReader();
    const decoder = new TextDecoder("utf-8");

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      const chunk = decoder.decode(value, { stream: true });

      // 🚨 핵심 포인트:
      // 단순히 .text += chunk 를 하면 Vue가 감지를 못할 때가 있습니다.
      // 객체 자체를 새로 할당해서 Vue가 "어! 데이터 바뀌었네? 화면 그려야지!"라고 강제로 인식하게 만듭니다.
      const currentText = chatHistory.value[aiMessageIndex].text;
      chatHistory.value[aiMessageIndex] = {
        ...chatHistory.value[aiMessageIndex],
        text: currentText + chunk
      };

      // 스크롤 제어
      const chatContainer = document.querySelector('.chat-history');
      if (chatContainer) chatContainer.scrollTop = chatContainer.scrollHeight;
    }

    // 최종 정답 체크 및 클리어 처리
    if (chatHistory.value[aiMessageIndex].text.includes("훌륭하다")) {
      setTimeout(() => { gameStatus.value = 'CLEARED'; }, 1500);
    }

  } catch (error) {
    console.error("스트리밍 실패:", error);
    chatHistory.value[aiMessageIndex].text = "본부 통신 두절. 재입력 바람.";
  }
};

// 🗺️ 카카오 맵 초기화
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