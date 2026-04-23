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

        <span class="cursor" v-if="isTyping">_</span>
      </div>

      <button
          v-if="isTyping"
          @click="skipTyping"
          class="mission-btn skip-btn"
      >
        ⏩ [ 브리핑 스킵 ]
      </button>

      <button
          v-if="isFinished"
          @click="goToMap"
          class="mission-btn"
      >
        [ 요원 확인 완료. 작전 지역으로 이동한다 ]
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import axios from 'axios';
// 컴포저블 불러오기
import { useTypingBuffer } from '@/composables/useTypingBuffer';

const router = useRouter();
const isFetching = ref(true);

// 타자기 컴포저블 초기화 (속도 40ms 설정)
const {
  displayedText,
  isTyping,
  isFinished,
  addChunk,
  finishTyping,
  skipTyping
} = useTypingBuffer(40);

const fetchBriefing = async () => {
  try {
    const response = await axios.get('http://localhost:8080/api/v1/regions/1');
    const fullText = response.data.description || "데이터가 비어 있습니다.";

    isFetching.value = false;

    // 1. 받은 텍스트를 버퍼 큐에 추가
    addChunk(fullText);
    // 2. 데이터 전송 완료 신호 (큐가 비면 종료 처리)
    finishTyping();

  } catch (error) {
    console.error("브리핑 데이터 로드 실패", error);
    isFetching.value = false;
    addChunk("통신 상태 불량. 즉시 정동길로 이동하여 단서를 확보하라.");
    finishTyping();
  }
};

const goToMap = () => router.push('/map');

onMounted(() => {
  fetchBriefing();
});
</script>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Share+Tech+Mono&display=swap');

.briefing-container {
  background: #050505;
  height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 20px;
  font-family: 'Share Tech Mono', monospace;
  box-sizing: border-box;
  overflow: hidden;
}

.monitor-overlay {
  position: absolute;
  top: 0; left: 0; width: 100%; height: 100%;
  background: linear-gradient(rgba(18, 16, 16, 0) 50%, rgba(0, 0, 0, 0.1) 50%),
  linear-gradient(90deg, rgba(255, 0, 0, 0.03), rgba(0, 255, 0, 0.01), rgba(0, 255, 0, 0.03));
  background-size: 100% 3px, 2px 100%;
  pointer-events: none;
  z-index: 10;
}

.terminal-box {
  background: rgba(15, 15, 15, 0.95);
  border: 2px solid #333;
  border-radius: 12px;
  width: 100%;
  max-width: 800px;
  height: 85vh;
  display: flex;
  flex-direction: column;
  padding: 30px;
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

.text-area {
  flex: 1;
  overflow-y: auto;
  padding-right: 10px;
  margin-bottom: 15px;
}

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
  margin-top: 10px;
}

.mission-btn:hover {
  background: #00ffcc;
  color: #000;
  box-shadow: 0 0 20px #00ffcc;
}

.skip-btn {
  color: #ffaa00;
  border-color: #ffaa00;
  background: rgba(255, 170, 0, 0.05);
}

.skip-btn:hover {
  background: #ffaa00;
  color: #000;
  box-shadow: 0 0 20px #ffaa00;
}

.cursor { animation: blink 1s infinite; color: #00ffcc; font-weight: bold; }
@keyframes blink { 0%, 100% { opacity: 1; } 50% { opacity: 0; } }

.blink-text { animation: blink 0.5s infinite; }

@media (max-width: 768px) {
  .terminal-box {
    height: 95vh;
    padding: 20px;
  }
  .top-secret { font-size: 1.3rem; }
  .typewriter { font-size: 1.05rem; }
}
</style>