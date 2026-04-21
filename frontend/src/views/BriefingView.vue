<template>
  <div class="briefing-container">
    <div class="monitor-overlay"></div>

    <div class="terminal-box">
      <header class="terminal-header">
        <span class="status-indicator">📡 보안 채널 연결됨...</span>
        <h1 class="top-secret">🔒 TOP SECRET: OPERATION SEOUL</h1>
      </header>

      <div class="text-area">
        <span class="cmd-label">COMMANDER:</span>

        <p v-if="isFetching" class="typewriter blink-text">데이터 암호화 수신 중...</p>

        <p v-else class="typewriter" v-html="displayedText"></p>

        <span class="cursor" v-if="!isFinished && !isFetching">_</span>
      </div>

      <button v-if="isFinished" @click="goToMap" class="mission-btn">
        [ 요원 확인 완료. 작전 지역으로 이동한다 ]
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import axios from 'axios';

const router = useRouter();
const displayedText = ref('');
const isFinished = ref(false);
const isFetching = ref(true); // API 통신 상태
let fullText = '';            // DB에서 받아올 원본 텍스트

// 백엔드에서 브리핑 텍스트 가져오기
const fetchBriefing = async () => {
  try {
    const response = await axios.get('http://localhost:8080/api/v1/regions/1');

    // response.data.briefing -> response.data.description
    fullText = response.data.description;

    if (!fullText) {
      fullText = "데이터는 수신되었으나 내용이 비어 있습니다. 본부와 교신을 재시도하십시오.";
    }

    isFetching.value = false;
    typeText();
  } catch (error) {
    console.error("브리핑 데이터 로드 실패", error);
    fullText = "통신 상태 불량. 즉시 정동길로 이동하여 단서를 확보하라.";
    isFetching.value = false;
    typeText();
  }
};

const typeText = () => {
  let i = 0;
  const interval = setInterval(() => {
    // HTML 태그 깨짐 방지 파서
    if (fullText.substring(i, i + 4) === '<br>') {
      displayedText.value += '<br>'; i += 4;
    } else if (fullText.substring(i, i + 3) === '<b>') {
      displayedText.value += '<b>'; i += 3;
    } else if (fullText.substring(i, i + 4) === '</b>') {
      displayedText.value += '</b>'; i += 4;
    } else {
      displayedText.value += fullText[i]; i++;
    }

    if (i >= fullText.length) {
      clearInterval(interval);
      isFinished.value = true;
    }
  }, 40);
};

const goToMap = () => router.push('/map');

onMounted(() => {
  // 컴포넌트가 마운트되면 가장 먼저 백엔드에 데이터를 요청
  fetchBriefing();
});
</script>
<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Share+Tech+Mono&display=swap');

/* 전체 배경: 화면 전체를 중앙 정렬 베이스로 설정 */
.briefing-container {
  background: #050505;
  height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 20px; /* 화면 끝과의 최소 여백 */
  font-family: 'Share Tech Mono', monospace;
  box-sizing: border-box;
  overflow: hidden;
}

/* 옛날 모니터 지지직거리는 오버레이 효과 */
.monitor-overlay {
  position: absolute;
  top: 0; left: 0; width: 100%; height: 100%;
  background: linear-gradient(rgba(18, 16, 16, 0) 50%, rgba(0, 0, 0, 0.1) 50%),
  linear-gradient(90deg, rgba(255, 0, 0, 0.03), rgba(0, 255, 0, 0.01), rgba(0, 255, 0, 0.03));
  background-size: 100% 3px, 2px 100%;
  pointer-events: none;
  z-index: 10;
}

/* 보더 박스: 중앙에 위치하며 적당한 크기 유지 */
.terminal-box {
  background: rgba(15, 15, 15, 0.95);
  border: 2px solid #333; /* 뚜렷한 테두리 */
  border-radius: 12px;
  width: 100%;
  max-width: 800px; /* 너무 퍼지지 않게 적당히 제한 */
  height: 85vh; /* 화면 높이의 85% 정도 차지 */
  display: flex;
  flex-direction: column;
  padding: 30px; /* 내부 패딩 */
  box-shadow: 0 0 40px rgba(0, 255, 204, 0.05);
  position: relative;
  z-index: 2;
  box-sizing: border-box;
}

.terminal-header {
  border-bottom: 1px solid #222;
  margin-bottom: 20px;
  padding-bottom: 10px;
}

.top-secret {
  color: #ff4444;
  font-size: 1.6rem;
  margin: 5px 0;
  letter-spacing: 2px;
  text-shadow: 0 0 10px rgba(255, 68, 68, 0.3);
}

.status-indicator { color: #666; font-size: 0.8rem; }

/* 핵심: 내부 텍스트 영역만 스크롤됨 */
.text-area {
  flex: 1;
  overflow-y: auto;
  padding-right: 10px;
  margin-bottom: 15px;
}

/* 커스텀 스크롤바 디자인 */
.text-area::-webkit-scrollbar { width: 4px; }
.text-area::-webkit-scrollbar-track { background: #0a0a0a; }
.text-area::-webkit-scrollbar-thumb { background: #00ffcc; border-radius: 10px; }

.typewriter {
  color: #00ffcc;
  font-size: 1.2rem;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: keep-all;
}

.typewriter :deep(b) {
  color: #fff;
  text-shadow: 0 0 8px #00ffcc;
}

/* 하단 버튼 영역 */
.action-btn-wrapper {
  margin-top: auto;
  padding-top: 20px;
  border-top: 1px solid #222;
}

.mission-btn {
  background: rgba(0, 255, 204, 0.05);
  color: #00ffcc;
  border: 1px solid #00ffcc;
  padding: 18px;
  width: 100%;
  font-size: 1.1rem;
  font-weight: bold;
  cursor: pointer;
  transition: all 0.2s ease;
  border-radius: 6px;
}

.mission-btn:hover {
  background: #00ffcc;
  color: #000;
  box-shadow: 0 0 20px #00ffcc;
}

.cursor { animation: blink 1s infinite; color: #00ffcc; font-weight: bold; }
@keyframes blink { 0%, 100% { opacity: 1; } 50% { opacity: 0; } }

/* 📱 모바일 반응형: 화면이 작아지면 더 꽉 차게 조절 */
@media (max-width: 768px) {
  .terminal-box {
    height: 95vh;
    padding: 20px;
  }
  .top-secret { font-size: 1.3rem; }
  .typewriter { font-size: 1.05rem; }
}
</style>