<template>
  <div class="chat-viewport">
    <header class="chat-header">
      <div class="header-content">
        <span class="status-dot"></span>
        <h2>본부 AI 지휘관 (보안 채널)</h2>
      </div>
      <div class="mission-status">
        <span class="limit-text" :class="{ 'warning-text': questionCount >= 10 }">
          질문 조력 횟수: {{ questionCount }} / 20
        </span>
        <p v-if="questionCount >= 10" class="warning-msg blink-text">
          ⚠️ 도청 위험 감지! 즉시 암호를 입력하십시오.
        </p>
      </div>
    </header>

    <main class="chat-history" ref="chatContainer">
      <div v-for="(msg, index) in chatHistory" :key="index" :class="['bubble-wrapper', msg.sender]">
        <div class="bubble">
          <span class="sender-label">{{ msg.sender === 'ai' ? 'COMMANDER' : 'AGENT' }}</span>
          <p class="text">{{ msg.text }}</p>
        </div>
      </div>

      <div v-if="isWaiting" class="bubble-wrapper ai">
        <div class="bubble loading-bubble">
          <span class="sender-label">SYSTEM</span>
          <p class="text blink-text">데이터 암호화 수신 중...</p>
        </div>
      </div>
    </main>

    <footer class="chat-footer">
      <div class="input-group">
        <input
            v-model="userInput"
            @keyup.enter="sendMessage"
            :disabled="isWaiting || questionCount >= 20"
            :placeholder="questionCount >= 20 ? '질문 횟수 초과. 최종 암호만 입력 가능.' : '단서를 묻거나 암호를 입력하십시오.'"
        />
        <button @click="sendMessage" :disabled="isWaiting || !userInput.trim() || (questionCount >= 20 && !isFinalAnswer)">
          전송
        </button>
      </div>
    </footer>
  </div>
</template>

<script setup>
// TODO :  ?가 들어간것만 질문으로 파악하지 않고 문맥상으로 파악하던지 하게 바꿔라
import { ref, onMounted, nextTick, computed } from 'vue';
import { useRoute } from 'vue-router';

const route = useRoute();
const sessionId = ref(route.params.sessionId);
const chatHistory = ref([]);
const userInput = ref('');
const isWaiting = ref(false);
const questionCount = ref(0); // 🚀 질문 횟수 카운트
const chatContainer = ref(null);

// 간단한 질문 판별 (물음표가 포함되면 질문으로 간주)
const isQuestion = computed(() => userInput.value.includes('?'));

const scrollToBottom = async () => {
  await nextTick();
  if (chatContainer.value) chatContainer.value.scrollTop = chatContainer.value.scrollHeight;
};

const sendMessage = async () => {
  if (!userInput.value.trim() || isWaiting.value) return;

  const text = userInput.value;
  chatHistory.value.push({ sender: 'user', text: text });

  if (text.includes('?')) {
    questionCount.value++;
  }

  userInput.value = '';
  isWaiting.value = true;
  scrollToBottom();

  try {
    const response = await fetch(`http://localhost:8080/api/v1/sessions/${sessionId.value}/chat/stream`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ userAnswer: text })
    });

    if (!response.ok) throw new Error("서버 응답 오류");

    const reader = response.body.getReader();
    const decoder = new TextDecoder('utf-8');

    chatHistory.value.push({ sender: 'ai', text: '' });
    const aiMessageIndex = chatHistory.value.length - 1;

    let isFirstChunk = true;
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      if (isFirstChunk) {
        isWaiting.value = false;
        isFirstChunk = false;
      }

      const chunk = decoder.decode(value, { stream: true });
      for (let char of chunk) {
        chatHistory.value[aiMessageIndex].text += char;
        await new Promise(r => setTimeout(r, 20));
        scrollToBottom();
      }
    }

    if (questionCount.value === 10) {
      chatHistory.value.push({
        sender: 'ai',
        text: '...경고한다 요원. 더 이상의 질문은 도청의 위험이 있다. 이제 확보한 단서로 정답을 도출하라.'
      });
      scrollToBottom();
    }
  } catch (error) {
    console.error("통신 에러:", error);
    chatHistory.value.push({ sender: 'ai', text: '통신 상태 불량. 다시 질문하라.' });
    scrollToBottom();
  } finally {
    // 🚀 방탄 로직: 에러가 나든 정상 종료되든 무조건 대기 상태를 해제하여 화면 먹통을 막습니다!
    isWaiting.value = false;
  }
};
onMounted(() => {
  // 처음 입장 시 대사 수정
  chatHistory.value.push({
    sender: 'ai',
    text: '작전 지역 진입을 확인했다. 수집한 단서를 이용해 질문하면 본부 데이터베이스를 통해 지원하겠다. 단, 적들의 도청 위험이 있어 조력 횟수는 20회로 제한한다. 최종 암호를 입력하라.'
  });
});
</script>

<style scoped>
.chat-viewport {
  position: fixed; top: 0; left: 0; right: 0; bottom: 0;
  display: flex; flex-direction: column;
  background-color: #050505; color: #e0e0e0;
  font-family: 'Courier New', monospace; overflow: hidden;
}
.chat-header { padding: 15px; background: #111; border-bottom: 2px solid #00ffcc; text-align: center; }
.mission-status { margin-top: 10px; font-size: 0.8rem; }
.limit-text { color: #00ffcc; font-weight: bold; }
.warning-text { color: #ff4444; }
.warning-msg { color: #ff4444; font-size: 0.7rem; margin-top: 4px; font-weight: bold; }

.status-dot { width: 10px; height: 10px; background: #00ffcc; border-radius: 50%; display: inline-block; margin-right: 10px; box-shadow: 0 0 10px #00ffcc; }
.chat-header h2 { display: inline; font-size: 1.1rem; color: #00ffcc; margin: 0; }

.chat-history { flex: 1; padding: 20px; overflow-y: auto; display: flex; flex-direction: column; gap: 20px; }
.bubble-wrapper { width: 100%; display: flex; }
.bubble-wrapper.user { justify-content: flex-end; }
.bubble-wrapper.ai { justify-content: flex-start; }

.bubble { max-width: 65%; padding: 14px 18px; border-radius: 15px; line-height: 1.5; font-size: 0.95rem; }

.user .bubble { background: #004d40; border: 1px solid #00796b; border-bottom-right-radius: 2px; color: #e0f2f1; }
.ai .bubble { background: #1a1a1a; border: 1px solid #333; border-bottom-left-radius: 2px; color: #00ffcc; }
.loading-bubble { border-color: #00ffcc44; font-style: italic; }
.sender-label { font-size: 0.7rem; display: block; margin-bottom: 6px; font-weight: bold; opacity: 0.8; }
.user .sender-label { text-align: right; color: #4db6ac; }
.blink-text { animation: blink 1.5s infinite; }
@keyframes blink { 0%, 100% { opacity: 1; } 50% { opacity: 0.4; } }
.chat-footer { padding: 15px; background: #111; border-top: 1px solid #222; }
.input-group { display: flex; gap: 10px; height: 50px; }
.input-group input { flex: 1; background: #222; border: 1px solid #333; border-radius: 8px; color: white; padding: 0 15px; font-size: 1rem; }
.input-group input:focus { outline: none; border-color: #00ffcc; }
.input-group button { background: #00ffcc; color: #000; border: none; border-radius: 8px; padding: 0 25px; font-weight: bold; cursor: pointer; }
.input-group button:disabled { background: #333; color: #666; }
</style>