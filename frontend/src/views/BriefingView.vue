<template>
  <div class="briefing-container">
    <div class="scanlines"></div>
    <div class="terminal-box">
      <div class="terminal-header">
        <div class="dot-group">
          <span class="dot red"></span>
          <span class="dot yellow"></span>
          <span class="dot green"></span>
        </div>
        <span class="title">SECURE_CHANNEL_ESTABLISHED // REGION-{{ regionId }}</span>
      </div>

      <div class="terminal-body">
        <p class="system-text">> INCOMING TRANSMISSION...</p>
        <p class="system-text">> DECRYPTING MISSION DATA [ SECTOR: {{ regionName }} ]</p>
        <div class="divider"></div>

        <div class="message-area">
          <p class="typewriter">
            {{ displayedText }}<span class="cursor" v-if="!isFinished">_</span>
          </p>
        </div>
      </div>

      <div class="terminal-footer">
        <button v-if="!isFinished" @click="skipTyping" class="action-btn skip-btn">
          >> SKIP
        </button>
        <button v-if="isFinished" @click="startMission" class="action-btn accept-btn">
          작전 투입 (ACCEPT) ➔
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import apiClient from '@/api/axiosInstance';

const route = useRoute();
const router = useRouter();

// 동적 할당용 변수
const regionId = route.query.regionId || 2;
const regionName = ref('LOADING...');
const fullText = ref('');
const displayedText = ref('');
const isFinished = ref(false);
let typingInterval = null;

onMounted(async () => {
  try {
    const response = await apiClient.get(`/v1/regions/${regionId}`);
    regionName.value = response.data.name;
    // 🚨 핵심 수정: <br> 태그를 타자기 효과가 인식할 수 있는 실제 줄바꿈(\n)으로 치환
    fullText.value = response.data.description.replace(/<br\s*\/?>/gi, '\n');
    startTyping();
  } catch (error) {
    console.error("데이터 로드 실패:", error);
    fullText.value = "본부와의 통신이 원활하지 않습니다. 다시 시도하십시오.";
    startTyping();
  }
});

const startTyping = () => {
  let i = 0;
  typingInterval = setInterval(() => {
    if (i < fullText.value.length) {
      displayedText.value += fullText.value[i];
      i++;
    } else {
      completeTyping();
    }
  }, 50);
};

const skipTyping = () => {
  clearInterval(typingInterval);
  displayedText.value = fullText.value;
  completeTyping();
};

const completeTyping = () => {
  isFinished.value = true;
  clearInterval(typingInterval);
};

const startMission = () => {
  router.push({ name: 'Map', query: { regionId: regionId } });
};
</script>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Share+Tech+Mono&family=Noto+Sans+KR:wght@400;500;700&display=swap');

/* 전체 배경 */
.briefing-container {
  min-height: 100vh;
  background-color: #050505;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 20px;
  position: relative;
  overflow: hidden;
  font-family: 'Share Tech Mono', 'Noto Sans KR', monospace;
}

/* CRT 스캔라인 효과 */
.scanlines {
  position: absolute;
  top: 0; left: 0; width: 100%; height: 100%;
  background: linear-gradient(rgba(18, 16, 16, 0) 50%, rgba(0, 0, 0, 0.25) 50%), linear-gradient(90deg, rgba(255, 0, 0, 0.06), rgba(0, 255, 0, 0.02), rgba(0, 0, 255, 0.06));
  background-size: 100% 2px, 3px 100%;
  pointer-events: none;
  z-index: 10;
}

/* 터미널 창 디자인 */
.terminal-box {
  width: 100%;
  max-width: 800px;
  min-height: 60vh;
  background: rgba(10, 15, 20, 0.85);
  border: 1px solid #00ffcc;
  border-radius: 8px;
  box-shadow: 0 0 20px rgba(0, 255, 204, 0.1), inset 0 0 10px rgba(0, 255, 204, 0.05);
  display: flex;
  flex-direction: column;
  position: relative;
  z-index: 20;
}

/* 상단 헤더 (맥 스타일 버튼 + 제목) */
.terminal-header {
  background: rgba(0, 255, 204, 0.1);
  padding: 12px 20px;
  display: flex;
  align-items: center;
  border-bottom: 1px solid #00ffcc;
  border-radius: 8px 8px 0 0;
}

.dot-group {
  display: flex;
  gap: 8px;
  margin-right: 20px;
}
.dot { width: 12px; height: 12px; border-radius: 50%; }
.red { background-color: #ff5f56; }
.yellow { background-color: #ffbd2e; }
.green { background-color: #27c93f; }

.title {
  color: #00ffcc;
  font-size: 0.9rem;
  letter-spacing: 1px;
}

/* 터미널 본문 */
.terminal-body {
  padding: 30px;
  flex-grow: 1;
}

.system-text {
  color: #475569;
  margin: 0 0 8px 0;
  font-size: 0.85rem;
}

.divider {
  width: 100%;
  height: 1px;
  background: rgba(0, 255, 204, 0.2);
  margin: 20px 0;
}

.message-area {
  min-height: 200px;
}

.typewriter {
  color: #e2e8f0;
  font-size: 1.1rem;
  line-height: 1.8;
  white-space: pre-wrap; /* \n 기호를 실제 줄바꿈으로 인식하게 만듭니다 */
}

/* 깜빡이는 커서 */
.cursor {
  color: #00ffcc;
  font-weight: bold;
  animation: blink 1s step-end infinite;
}
@keyframes blink { 50% { opacity: 0; } }

/* 터미널 하단 버튼 */
.terminal-footer {
  padding: 20px 30px;
  display: flex;
  justify-content: flex-end;
  border-top: 1px solid rgba(0, 255, 204, 0.1);
}

.action-btn {
  background: transparent;
  padding: 10px 25px;
  font-family: 'Share Tech Mono', 'Noto Sans KR', monospace;
  font-size: 1rem;
  font-weight: bold;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.3s;
}

.skip-btn {
  border: 1px solid #475569;
  color: #94a3b8;
}
.skip-btn:hover {
  background: rgba(71, 85, 105, 0.2);
  color: #fff;
  border-color: #94a3b8;
}

.accept-btn {
  border: 1px solid #00ffcc;
  color: #00ffcc;
  background: rgba(0, 255, 204, 0.05);
  box-shadow: 0 0 10px rgba(0, 255, 204, 0.2);
  animation: pulse-glow 2s infinite;
}
.accept-btn:hover {
  background: #00ffcc;
  color: #000;
  box-shadow: 0 0 20px rgba(0, 255, 204, 0.6);
}

@keyframes pulse-glow {
  0%, 100% { box-shadow: 0 0 10px rgba(0, 255, 204, 0.2); }
  50% { box-shadow: 0 0 20px rgba(0, 255, 204, 0.5); }
}
</style>