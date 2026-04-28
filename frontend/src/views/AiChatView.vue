<template>
  <div class="tactical-viewport">
    <div class="noise-overlay"></div>
    <div class="scanlines"></div>

    <header class="hud-header">
      <button @click="goBackToMap" class="btn-back">[ ⬅ MAP ]</button>

      <div class="header-glitch" data-text="HQ_SECURE_UPLINK">HQ_SECURE_UPLINK</div>
      <div class="sys-metrics">
        <span class="metric">ENC_KEY: AES-256</span>
        <span class="metric">PING: 18ms</span>
        <span class="metric highlight">Q_REMAIN: {{ 20 - interactionCount }}</span>
      </div>
    </header>

    <div v-if="questionCount >= 10" class="intercept-warning blink-text">
      [!] CRITICAL: SIGNAL INTERCEPTION AT 87%. MAINTAIN RADIO SILENCE OR INPUT FINAL CODE.
    </div>

    <main class="hud-body" ref="chatContainer">
      <TransitionGroup name="terminal-msg">
        <div v-for="(msg, index) in chatHistory" :key="index" :class="['message-block', msg.sender]">
          <div class="msg-meta">
            <span class="sender-id">{{ msg.sender === 'ai' ? 'SYS.COMMAND' : 'OP.AGENT_01' }}</span>
            <span class="timestamp">[{{ new Date().toISOString().substr(11, 8) }}]</span>
          </div>

          <div class="content-frame">
            <div class="corner tl"></div><div class="corner tr"></div>
            <div class="corner bl"></div><div class="corner br"></div>

            <img v-if="msg.type === 'image'" :src="msg.text" class="scan-image" />
            <div v-else-if="msg.sender === 'ai' && msg.isTyping" class="type-writer" v-html="displayedText"></div>
            <div v-else v-html="msg.text" class="text-content"></div>
          </div>
        </div>

        <div v-if="isWaiting" class="message-block ai" key="loading">
          <div class="msg-meta"><span class="sender-id">SYS.COMMAND</span></div>
          <div class="content-frame loading-frame">
            <div class="corner tl"></div><div class="corner tr"></div>
            <div class="corner bl"></div><div class="corner br"></div>

            <div class="decrypt-header">DECRYPTING_PACKETS...</div>
            <div class="progress-bar"><div class="progress-fill"></div></div>
          </div>
        </div>
      </TransitionGroup>
    </main>

    <footer class="hud-footer">
      <div class="terminal-interface">
        <button @click="isScannerOpen = true" class="btn-scan" title="VISUAL_SCAN" :disabled="isWaiting">
          [ 📷 SCAN ]
        </button>

        <div class="input-area">
          <span class="prompt-symbol">&gt;</span>
          <input
              v-model="userInput"
              @keyup.enter="sendMessage"
              :disabled="isWaiting || questionCount >= 20"
              class="cmd-input"
              autocomplete="off"
              spellcheck="false"
              :placeholder="questionCount >= 20 ? 'LIMIT REACHED. FINAL CODE REQUIRED.' : 'AWAITING AGENT INPUT...'"
          />
          <span class="cursor-block" :class="{'typing': userInput.length > 0}"></span>
        </div>

        <button @click="sendMessage" :disabled="isWaiting || !userInput.trim() || (questionCount >= 20 && !isFinalAnswer)" class="btn-exec">
          EXECUTE
        </button>
      </div>
    </footer>

    <div v-if="isScannerOpen" class="scanner-modal">
      <CameraScanner @capture="handleManualCapture" @close="isScannerOpen = false" />
      <button @click="isScannerOpen = false" class="btn-abort">ABORT_LINK</button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, computed, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import axios from 'axios';
import CameraScanner from '@/components/CameraScanner.vue';
import { useTypingBuffer } from '@/composables/useTypingBuffer';

const route = useRoute();
const router = useRouter(); // 💡 맵 복귀를 위해 라우터 추가
const sessionId = ref(route.params.sessionId);

const chatHistory = ref([]);
const userInput = ref('');
const isWaiting = ref(false);
const questionCount = ref(0);
const interactionCount = ref(0);
const chatContainer = ref(null);
const isScannerOpen = ref(false);

const isFinalAnswer = computed(() => !userInput.value.includes('?'));
const { displayedText, isTyping, isFinished, addChunk, finishTyping, reset } = useTypingBuffer(30);

// 💡 추가됨: 맵뷰로 복귀하는 로직
const goBackToMap = () => {
  const regionId = route.query.regionId || 1;
  router.push({ name: 'Map', query: { regionId: regionId } });
};

const scrollToBottom = async () => {
  await nextTick();
  if (chatContainer.value) {
    chatContainer.value.scrollTo({
      top: chatContainer.value.scrollHeight,
      behavior: 'smooth'
    });
  }
};

watch(isFinished, (newVal) => {
  if (newVal) {
    const typingMsg = chatHistory.value.find(m => m.isTyping);
    if (typingMsg) {
      typingMsg.text = displayedText.value;
      typingMsg.isTyping = false;
    }
  }
});

const typeWriterEffect = (text) => {
  reset();
  chatHistory.value.push({ sender: 'ai', text: '', isTyping: true });
  scrollToBottom();

  addChunk(text);
  finishTyping();

  const scrollInterval = setInterval(() => {
    scrollToBottom();
    if (!isTyping.value) clearInterval(scrollInterval);
  }, 100);
};

onMounted(() => {
  const capturedImage = sessionStorage.getItem('capturedImage');
  if (capturedImage) {
    chatHistory.value.push({ sender: 'user', type: 'image', text: capturedImage });
    sessionStorage.removeItem('capturedImage');
    typeWriterEffect("<span style='color:#08bdba'>[AUTH_GRANTED]</span><br>목표 지점 확인. 작전 지역 진입을 환영한다 요원.<br>수집한 단서를 이용해 질문하거나 암호를 해독하라.");
  } else {
    typeWriterEffect("작전 지역 진입을 확인했다. 수집한 단서를 이용해 질문하면 본부 데이터베이스를 통해 지원하겠다. 단, 적들의 도청 위험이 있어 조력 횟수는 20회로 제한한다. 최종 암호를 입력하라.");
  }
});

const handleManualCapture = async (imageDataUrl) => {
  isScannerOpen.value = false;
  chatHistory.value.push({ sender: 'user', type: 'image', text: imageDataUrl });
  scrollToBottom();

  isWaiting.value = true;

  try {
    const res = await fetch(imageDataUrl);
    const blob = await res.blob();
    const file = new File([blob], "clue.jpg", { type: "image/jpeg" });
    const formData = new FormData();
    formData.append("image", file);

    await axios.post(`http://localhost:8080/api/v1/sessions/${sessionId.value}/vision`, formData, {
      headers: { "Content-Type": "multipart/form-data" }
    });

    questionCount.value++;
    interactionCount.value++;
    isWaiting.value = false;
    await requestGeminiStream("새로운 시각 단서를 전송했습니다. 분석 결과를 보고해 주십시오.");

  } catch (error) {
    console.error("비전 API 에러:", error);
    isWaiting.value = false;
    typeWriterEffect("<span style='color:#ef5350'>[SCAN_FAILED]</span> 유효한 단서를 찾을 수 없습니다. 다시 촬영하십시오.");
  }
};

const sendMessage = async () => {
  if (!userInput.value.trim() || isWaiting.value) return;

  const text = userInput.value;
  chatHistory.value.push({ sender: 'user', text: text });

  if (text.includes('?')) {
    questionCount.value++;
    interactionCount.value++;
  }

  userInput.value = '';
  await requestGeminiStream(text);
};

const requestGeminiStream = async (textMessage) => {
  isWaiting.value = true;
  scrollToBottom();

  try {
    const response = await fetch(`http://localhost:8080/api/v1/sessions/${sessionId.value}/chat/stream`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ userAnswer: textMessage })
    });

    if (!response.ok) throw new Error("서버 응답 오류");

    const reader = response.body.getReader();
    const decoder = new TextDecoder('utf-8');

    chatHistory.value.push({ sender: 'ai', text: '', isTyping: true });
    const aiMessageIndex = chatHistory.value.length - 1;
    reset();

    let isFirstChunk = true;
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      if (isFirstChunk) {
        isWaiting.value = false;
        isFirstChunk = false;
      }

      const chunk = decoder.decode(value, { stream: true });
      const lines = chunk.split('\n');
      for (let line of lines) {
        if (line.startsWith('data:')) {
          line = line.replace('data:', '').trim();
        }
        if (line) {
          addChunk(line);
          scrollToBottom();
        }
      }
    }
    finishTyping();

    if (questionCount.value === 10) {
      setTimeout(() => {
        typeWriterEffect('<span style="color:#ef5350">[!] CRITICAL WARNING</span><br>더 이상의 질문은 도청의 위험이 있다. 확보한 단서로 정답을 도출하라.');
      }, 1000);
    }
  } catch (error) {
    console.error("통신 에러:", error);
    isWaiting.value = false;
    typeWriterEffect("<span style='color:#ef5350'>[SYS_ERROR]</span> 위성 연결 불안정. 재전송 하십시오.");
  }
};
</script>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Share+Tech+Mono&display=swap');

/* 전체 뷰포트 - 미드나잇 다크 네이비 톤 */
.tactical-viewport {
  position: fixed; inset: 0;
  display: flex; flex-direction: column;
  background-color: #050a0d;
  color: #b0bec5;
  font-family: 'Share Tech Mono', monospace;
  overflow: hidden;
}

/* 촌스럽지 않은 아주 은은한 노이즈와 스캔라인 */
.noise-overlay {
  position: absolute; inset: 0; pointer-events: none; z-index: 100;
  background-image: url('data:image/svg+xml,%3Csvg viewBox="0 0 200 200" xmlns="http://www.w3.org/2000/svg"%3E%3Cfilter id="noiseFilter"%3E%3CfeTurbulence type="fractalNoise" baseFrequency="0.9" numOctaves="3" stitchTiles="stitch"/%3E%3C/filter%3E%3Crect width="100%25" height="100%25" filter="url(%23noiseFilter)"/%3E%3C/svg%3E');
  opacity: 0.02;
}
.scanlines {
  position: absolute; inset: 0; pointer-events: none; z-index: 1;
  background: linear-gradient(rgba(8, 189, 186, 0.02) 50%, rgba(0, 0, 0, 0.15) 50%);
  background-size: 100% 4px;
}

/* 헤더 */
.hud-header {
  position: relative; z-index: 10;
  padding: 15px 25px;
  background: rgba(4, 12, 16, 0.9);
  border-bottom: 1px solid rgba(8, 189, 186, 0.25);
  display: flex; justify-content: space-between; align-items: center;
}

/* 💡 추가됨: 뒤로가기 버튼 스타일 (요원님 테마색 준수) */
.btn-back {
  background: rgba(8, 189, 186, 0.1); border: 1px solid #08bdba;
  color: #08bdba; padding: 5px 10px; font-family: inherit; font-size: 0.8rem;
  font-weight: bold; cursor: pointer; border-radius: 3px; transition: 0.2s;
}
.btn-back:hover { background: #08bdba; color: #000; box-shadow: 0 0 8px #08bdba; }

.header-glitch { font-size: 1.2rem; font-weight: bold; color: #80cbc4; letter-spacing: 2px; }
.sys-metrics { display: flex; gap: 15px; font-size: 0.8rem; color: #4dd0e1; opacity: 0.7; }
.sys-metrics .highlight { color: #08bdba; font-weight: bold; opacity: 1; text-shadow: 0 0 8px rgba(8,189,186,0.4); }

.intercept-warning {
  background: rgba(239, 83, 80, 0.1);
  color: #ef5350; text-align: center; padding: 6px;
  font-size: 0.8rem; font-weight: bold; border-bottom: 1px solid rgba(239, 83, 80, 0.3);
  position: relative; z-index: 9; letter-spacing: 1px;
}

/* 본문 */
.hud-body {
  flex: 1; overflow-y: auto; padding: 25px;
  display: flex; flex-direction: column; gap: 30px;
  z-index: 5;
}
.hud-body::-webkit-scrollbar { width: 5px; }
.hud-body::-webkit-scrollbar-track { background: transparent; }
.hud-body::-webkit-scrollbar-thumb { background: rgba(8, 189, 186, 0.2); }

/* 말풍선 */
.message-block { width: 100%; display: flex; flex-direction: column; max-width: 52%; }
.message-block.user { align-self: flex-end; align-items: flex-end; }
.message-block.ai { align-self: flex-start; align-items: flex-start; }

.msg-meta { display: flex; gap: 10px; font-size: 0.75rem; margin-bottom: 6px; opacity: 0.6; }
.user .msg-meta { color: #cfd8dc; flex-direction: row-reverse; }
.ai .msg-meta { color: #80cbc4; }

.content-frame {
  position: relative;
  padding: 16px 20px;
  font-size: 0.95rem; line-height: 1.6; word-break: break-word;
  background: rgba(8, 189, 186, 0.05);
  border: 1px solid rgba(8, 189, 186, 0.15);
}

.user .content-frame { background: rgba(255, 255, 255, 0.03); color: #eceff1; border-color: rgba(255,255,255,0.1); }
.ai .content-frame { color: #80cbc4; }

.corner { position: absolute; width: 6px; height: 6px; border-color: rgba(8, 189, 186, 0.5); border-style: solid; }
.tl { top: -1px; left: -1px; border-width: 2px 0 0 2px; }
.tr { top: -1px; right: -1px; border-width: 2px 2px 0 0; }
.bl { bottom: -1px; left: -1px; border-width: 0 0 2px 2px; }
.br { bottom: -1px; right: -1px; border-width: 0 2px 2px 0; }

.user .corner { border-color: rgba(255, 255, 255, 0.3); }

.scan-image { max-width: 100%; border-radius: 4px; border: 1px solid rgba(8, 189, 186, 0.3); }

.type-writer :deep(b), .text-content :deep(b) { color: #fff; font-weight: bold; text-shadow: 0 0 6px rgba(8, 189, 186, 0.4); }

/* 로딩 바 */
.loading-frame { min-width: 180px; display: flex; flex-direction: column; gap: 8px; }
.decrypt-header { font-size: 0.8rem; color: #4dd0e1; letter-spacing: 1px; animation: blink 1.5s infinite; }
.progress-bar { width: 100%; height: 3px; background: rgba(8, 189, 186, 0.1); overflow: hidden; position: relative; }
.progress-fill { position: absolute; top: 0; left: 0; height: 100%; width: 30%; background: #08bdba; animation: scanning 1.5s ease-in-out infinite alternate; }

@keyframes scanning { 0% { left: 0%; width: 10%; } 100% { left: 90%; width: 30%; } }
@keyframes blink { 0%, 100% { opacity: 1; } 50% { opacity: 0.3; } }

/* 하단 풋터 */
.hud-footer {
  position: relative; z-index: 10;
  padding: 20px 30px; background: rgba(2, 6, 8, 0.95);
  border-top: 1px solid rgba(8, 189, 186, 0.2);
}

.terminal-interface { display: flex; align-items: center; gap: 15px; }

.btn-scan {
  background: rgba(8, 189, 186, 0.05); border: 1px solid rgba(8, 189, 186, 0.3);
  color: #08bdba; font-family: inherit; font-size: 1rem; font-weight: bold;
  padding: 15px 20px; cursor: pointer; transition: 0.2s; border-radius: 4px;
}
.btn-scan:hover:not(:disabled) { background: rgba(8, 189, 186, 0.15); color: #fff; }

.input-area {
  flex: 1; display: flex; align-items: center; gap: 12px;
  background: rgba(0, 0, 0, 0.4); border: 1px solid rgba(8, 189, 186, 0.2);
  padding: 15px 20px; border-radius: 4px;
}

.prompt-symbol { color: #08bdba; font-weight: bold; font-size: 1.2rem; opacity: 0.8; }
.cmd-input {
  flex: 1; background: transparent; border: none; color: #e0f2f1;
  font-family: inherit; font-size: 1.1rem;
}
.cmd-input:focus { outline: none; }
.cmd-input::placeholder { color: rgba(176, 190, 197, 0.3); }

.cursor-block { display: inline-block; width: 8px; height: 18px; background: #08bdba; animation: blink 1s step-end infinite; }
.cursor-block.typing { animation: none; opacity: 0; }

.btn-exec {
  background: rgba(8, 189, 186, 0.1); color: #08bdba; border: 1px solid #08bdba;
  font-family: inherit; font-weight: bold; font-size: 1.1rem; padding: 15px 30px;
  cursor: pointer; transition: 0.2s; border-radius: 4px;
}
.btn-exec:hover:not(:disabled) { background: #08bdba; color: #000; box-shadow: 0 0 12px rgba(8,189,186,0.4); }
.btn-exec:disabled, .btn-scan:disabled { opacity: 0.3; border-color: rgba(176, 190, 197, 0.2); cursor: not-allowed; }

.terminal-msg-enter-active, .terminal-msg-leave-active { transition: all 0.3s ease; }
.terminal-msg-enter-from { opacity: 0; transform: translateX(-10px); }

.scanner-modal { position: fixed; inset: 0; z-index: 1000; background: #000; }
.btn-abort { position: absolute; top: 20px; right: 20px; background: transparent; border: 1px solid #ef5350; color: #ef5350; padding: 10px 20px; z-index: 1001; cursor: pointer; font-family: inherit; font-weight: bold; border-radius: 4px; }
.btn-abort:hover { background: rgba(239, 83, 80, 0.1); }
</style>