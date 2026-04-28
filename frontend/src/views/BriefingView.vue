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
        <span class="title">SECURE_CHANNEL_ESTABLISHED // SEC-{{ missionId }}</span>
      </div>

      <div class="terminal-body">
        <p class="system-text">> INCOMING TRANSMISSION...</p>
        <p class="system-text">> DECRYPTING MISSION DATA [ SECTOR: {{ missionId }} ]</p>
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
import { ref, onMounted, onUnmounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';

const route = useRoute();
const router = useRouter();

// 💡 홈 뷰에서 넘겨준 쿼리 파라미터(missionId)를 안전하게 받아옵니다.
// 값이 없을 경우를 대비해 'UNKNOWN' 기본값을 줍니다.
const missionId = route.query.missionId || 'UNKNOWN';

// 타이핑 효과를 위한 로컬 상태
const displayedText = ref('');
const isFinished = ref(false);
let typingInterval = null;
let currentIndex = 0;

// 📡 본부(AI) 브리핑 스크립트 (추후 백엔드 Gemini API 연동 시 이 부분을 교체)
const fullMessage = `요원, 접속을 환영한다.

당신이 선택한 구역(섹터 ${missionId})에 얽힌 역사적 진실이 적들에 의해 훼손될 위기에 처했다.
본부는 이 지역 주변에 3개의 핵심 단서를 암호화하여 숨겨두었다.

당신의 첫 번째 임무는 현장으로 즉시 이동하여 스캐너(카메라)로 주변 사물의 글자를 스캔하고 단서를 회수하는 것이다.
3개의 단서를 모두 모으면, 최종 목적지의 위치와 진짜 임무가 해금될 것이다.

본부의 통신 지원은 여기까지다. 행운을 빈다.`;

// [기능: 한 글자씩 출력하는 타자기 효과]
const typeText = () => {
  if (currentIndex < fullMessage.length) {
    displayedText.value += fullMessage.charAt(currentIndex);
    currentIndex++;
  } else {
    clearInterval(typingInterval);
    isFinished.value = true;
  }
};

// [기능: 스킵 버튼 - 즉시 전체 텍스트 출력]
const skipTyping = () => {
  if (typingInterval) clearInterval(typingInterval);
  displayedText.value = fullMessage;
  isFinished.value = true;
};

// [기능: 맵 뷰로 이동]
const startMission = () => {
  console.log(`[시스템] 현장 투입. 맵 뷰(MapView)로 이동합니다. Region ID: ${missionId}`);
  // 💡 다음 화면인 지도로 넘어갈 때도 어떤 지역인지 ID를 넘겨줍니다.
  router.push({ name: 'Map', query: { regionId: missionId } });
};

// 화면이 켜지면 1초 대기 후 타자기 효과 시작
onMounted(() => {
  setTimeout(() => {
    typingInterval = setInterval(typeText, 40); // 40ms 속도
  }, 1000);
});

// 컴포넌트가 파괴될 때 메모리 누수 방지
onUnmounted(() => {
  if (typingInterval) clearInterval(typingInterval);
});
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