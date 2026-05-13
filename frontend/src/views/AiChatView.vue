<template>
  <div class="tactical-viewport">
    <!-- 최종 미션에서 수집 단서를 바탕으로 AI와 대화하고 정답을 제출하는 화면입니다. -->
    <div class="noise-overlay"></div>
    <div class="scanlines"></div>

    <header class="hud-header">
      <div class="header-actions">
        <button @click="goBackToMap" class="btn-back">[ ⬅ MAP ]</button>
        <button @click="showCluePanel = true" class="btn-clues">
          [ CLUES {{ collectedMissions.length }} ]
        </button>
      </div>

      <div class="header-glitch" data-text="HQ_SECURE_UPLINK">HQ_SECURE_UPLINK</div>
      <div class="sys-metrics">
        <span class="metric">ENC_KEY: AES-256</span>
        <span class="metric">PING: 18ms</span>
        <span class="metric highlight">Q_REMAIN: {{ questionRemaining }}</span>
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
              ref="commandInput"
              v-model="userInput"
              @keyup.enter="sendMessage"
              :disabled="isWaiting"
              class="cmd-input"
              autocomplete="off"
              spellcheck="false"
              :placeholder="questionCount >= 20 ? 'FINAL CODE REQUIRED.' : 'AWAITING AGENT INPUT...'"
          />
          <span class="cursor-block" :class="{'typing': userInput.length > 0}"></span>
        </div>

        <button @click="sendMessage" :disabled="isWaiting || !userInput.trim()" class="btn-exec">
          EXECUTE
        </button>
      </div>
    </footer>

    <div v-if="showCluePanel" class="clue-panel-overlay" @click="showCluePanel = false">
      <section class="clue-panel" @click.stop>
        <div class="clue-panel-header">
          <div>
            <p class="panel-kicker">ACQUIRED FIELD NOTES</p>
            <h2>획득한 미션 단서</h2>
          </div>
          <button class="panel-close" @click="showCluePanel = false">CLOSE</button>
        </div>

        <div v-if="collectedMissions.length" class="clue-log-list">
          <article v-for="mission in collectedMissions" :key="mission.id" class="clue-log">
            <strong>{{ mission.title }}</strong>
            <p>{{ mission.clue || mission.description || '본부에 등록된 단서 문구가 없습니다.' }}</p>
          </article>
        </div>
        <p v-else class="empty-clue-log">아직 확보한 단서가 없습니다. 현장 미션을 먼저 완료하십시오.</p>
      </section>
    </div>

    <div v-if="isScannerOpen" class="scanner-modal">
      <CameraScanner @capture="handleManualCapture" @close="isScannerOpen = false" />
      <button @click="isScannerOpen = false" class="btn-abort">ABORT_LINK</button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, computed, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useSessionStore } from '@/stores/sessionStore';
import apiClient from '@/api/axiosInstance';
import CameraScanner from '@/components/CameraScanner.vue';
import { useTypingBuffer } from '@/composables/useTypingBuffer';

const route = useRoute();
const router = useRouter();
const sessionId = ref(route.params.sessionId);
const regionId = computed(() => route.query.regionId || 1);
const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api').replace(/\/$/, '');

// 세션 store는 JWT 전송, userId fallback, 관리자 Vision bypass 여부 판단에 사용합니다.
const sessionStore = useSessionStore();
const isAdmin = computed(() => sessionStore.userInfo?.isAdmin || false);
const userId = computed(() => sessionStore.userId);

const chatHistory = ref([]);
const userInput = ref('');
const isWaiting = ref(false);
const questionCount = ref(0);
const chatContainer = ref(null);
const commandInput = ref(null);
const isScannerOpen = ref(false);
const finalMissionInfo = ref(null);
const collectedMissions = ref([]);
const showCluePanel = ref(false);

const questionRemaining = computed(() => Math.max(0, 20 - questionCount.value));
const { displayedText, isTyping, isFinished, addChunk, finishTyping, reset } = useTypingBuffer(30);

// MapView가 기록한 시간/이동거리 metric과 같은 key를 사용합니다.
const getMetricKey = () => `operation-seoul:mission-metrics:${userId.value || 1}:${regionId.value}`;

// 최종 정답 제출 시 점수 계산에 필요한 metric을 localStorage에서 읽습니다.
const readMissionMetrics = () => {
  try {
    const saved = localStorage.getItem(getMetricKey());
    return saved ? JSON.parse(saved) : {};
  } catch (error) {
    console.warn('Mission metric read failed:', error);
    return {};
  }
};

// 서버가 GameSession에 저장할 elapsedSeconds와 routeDistanceMeters payload를 구성합니다.
const getMissionMetricPayload = () => {
  const metrics = readMissionMetrics();
  const startedAt = Number(metrics.startedAt);
  const elapsedSeconds = Number.isFinite(startedAt)
      ? Math.max(0, Math.floor((Date.now() - startedAt) / 1000))
      : 0;

  return {
    elapsedSeconds,
    routeDistanceMeters: Math.max(0, Math.round(Number(metrics.routeDistanceMeters) || 0))
  };
};

// 지도 화면으로 돌아가 현재 현장 상태를 다시 확인합니다.
const goBackToMap = () => {
  router.push({ name: 'Map', query: { regionId: regionId.value } });
};

// 새 메시지가 들어오면 채팅창을 최하단으로 부드럽게 이동합니다.
const scrollToBottom = async () => {
  await nextTick();
  if (chatContainer.value) {
    chatContainer.value.scrollTo({
      top: chatContainer.value.scrollHeight,
      behavior: 'smooth'
    });
  }
};

// AI 응답이 끝나면 사용자가 바로 다음 입력을 할 수 있게 input focus를 복구합니다.
const focusCommandInput = async () => {
  await nextTick();
  if (isWaiting.value || isScannerOpen.value || !commandInput.value) return;

  try {
    commandInput.value.focus({ preventScroll: true });
  } catch {
    commandInput.value.focus();
  }
};

// 타자기 출력이 끝난 AI 메시지를 일반 메시지로 확정합니다.
watch(isFinished, (newVal) => {
  if (newVal) {
    const typingMsg = chatHistory.value.find(m => m.isTyping);
    if (typingMsg) {
      typingMsg.text = displayedText.value;
      typingMsg.isTyping = false;
    }
    focusCommandInput();
  }
});

// 로딩 상태가 풀리면 입력창 focus를 되돌립니다.
watch(isWaiting, (waiting) => {
  if (!waiting) {
    focusCommandInput();
  }
});

// 이미 받은 텍스트도 useTypingBuffer를 통해 같은 타자기 효과로 출력합니다.
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

// 진입 시 최종 미션 정보와 수집 단서를 불러오고, 초기 안내 메시지를 출력합니다.
onMounted(async () => {
  await loadFinalMissionInfo();

  const capturedImage = sessionStorage.getItem('capturedImage');
  if (capturedImage) {
    chatHistory.value.push({ sender: 'user', type: 'image', text: capturedImage });
    sessionStorage.removeItem('capturedImage');
    typeWriterEffect(buildInitialMessage("<span style='color:#08bdba'>[AUTH_GRANTED]</span><br>목표 지점 확인. 작전 지역 진입을 환영한다 요원."));
  } else {
    typeWriterEffect(buildInitialMessage("작전 지역 진입을 확인했다. 수집한 단서를 이용해 질문하면 본부 데이터베이스를 통해 지원하겠다."));
  }
});

// 채팅 화면의 field clue와 단서 패널 표시를 위해 현재 region의 미션 목록을 불러옵니다.
const loadFinalMissionInfo = async () => {
  try {
    const response = await apiClient.get(`/v1/regions/${regionId.value}/missions`, {
      params: { userId: userId.value || 1 }
    });
    const missionList = response.data || [];
    finalMissionInfo.value = missionList.find(mission => String(mission.id) === String(sessionId.value)) || null;
    collectedMissions.value = missionList.filter(mission =>
        mission.sessionStatus === 'CLEARED' && !getIsFinalMission(mission)
    );
  } catch (error) {
    console.warn('Final mission field clue load failed:', error);
  }
};

// 최초 진입 안내 메시지에 최종 현장 단서와 질문 제한을 포함합니다.
const buildInitialMessage = (prefix) => {
  const fieldTarget = finalMissionInfo.value?.visionKeyword || '현장 표식';
  const fieldClue = finalMissionInfo.value?.fieldClue
      || '마지막 표식은 이름을 감추고 연도와 인물의 그림자만 남긴다. 닫힌 사건의 방향이 한쪽으로 기울어 있다.';

  return `${prefix}<br><span style='color:#f8d66d'>[FIELD_CLUE]</span> ${escapeHtml(fieldTarget)}: ${escapeHtml(fieldClue)}<br>단, 적들의 도청 위험이 있어 조력 횟수는 20회로 제한한다. 최종 암호를 입력하라.`;
};

// AI 응답 영역에 삽입하는 field clue 값이 HTML로 해석되지 않도록 escaping합니다.
const escapeHtml = (value) => {
  return String(value || '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
};

// 백엔드 DTO 필드명 변화에 대응하기 위한 최종 미션 판별 helper입니다.
const getIsFinalMission = (mission) => {
  return mission && (mission.missionType === 'FINAL' || mission.isFinal === true || mission.final === true);
};

// 채팅 화면에서도 수동 스캔을 보낼 수 있게 data URL을 파일로 바꿔 Vision API에 업로드합니다.
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

    // 내 계정(userId)과 관리자 여부(isAdmin)를 폼 데이터에 실어서 전송합니다.
    formData.append("userId", userId.value);
    if (isAdmin.value) {
      formData.append("isAdmin", "true");
    }

    const response = await apiClient.post(`/v1/sessions/${sessionId.value}/vision`, formData, {
      headers: { "Content-Type": "multipart/form-data" }
    });

    if (!response.data?.success) {
      isWaiting.value = false;
      typeWriterEffect("<span style='color:#ef5350'>[SCAN_FAILED]</span> 유효한 단서를 찾을 수 없습니다. 다시 촬영하십시오.");
      return;
    }

    isWaiting.value = false;
    await requestGeminiStream("새로운 시각 단서를 전송했습니다. 분석 결과를 보고해 주십시오.");

  } catch (error) {
    console.error("비전 API 에러:", error);
    isWaiting.value = false;
    typeWriterEffect("<span style='color:#ef5350'>[SCAN_FAILED]</span> 유효한 단서를 찾을 수 없습니다. 다시 촬영하십시오.");
  }
};

// 사용자의 입력을 채팅 로그에 추가하고, 질문성 입력이면 조력 횟수를 증가시킵니다.
const sendMessage = async () => {
  if (!userInput.value.trim() || isWaiting.value) return;

  const text = userInput.value;
  chatHistory.value.push({ sender: 'user', text: text });

  if (hasQuestionIntent(text)) {
    questionCount.value++;
  }

  userInput.value = '';
  await requestGeminiStream(text);
};

// fetch reader로 백엔드 ResponseBodyEmitter 스트림을 읽고 typing buffer에 전달합니다.
const requestGeminiStream = async (textMessage) => {
  isWaiting.value = true;
  scrollToBottom();

  try {
    const headers = { 'Content-Type': 'application/json' };
    if (sessionStore.isLoggedIn && sessionStore.token) {
      headers.Authorization = `Bearer ${sessionStore.token}`;
    }

    const response = await fetch(`${apiBaseUrl}/v1/sessions/${sessionId.value}/chat/stream`, {
      method: 'POST',
      headers,
      body: JSON.stringify({
        userId: userId.value || 1,
        userAnswer: textMessage,
        ...getMissionMetricPayload()
      })
    });

    if (response.status === 401) {
      sessionStore.logout();
      router.push({ name: 'Intro' });
      return;
    }
    if (!response.ok) throw new Error(`서버 응답 오류: ${response.status}`);
    if (!response.body) throw new Error("스트리밍 응답을 받을 수 없습니다.");

    const reader = response.body.getReader();
    const decoder = new TextDecoder('utf-8');

    chatHistory.value.push({ sender: 'ai', text: '', isTyping: true });
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
    isWaiting.value = false;
    finishTyping();
    const cleared = await navigateIfMissionCleared();
    if (!cleared) {
      await focusCommandInput();
    }

    if (questionCount.value === 10) {
      setTimeout(() => {
        typeWriterEffect('<span style="color:#ef5350">[!] CRITICAL WARNING</span><br>더 이상의 질문은 도청의 위험이 있다. 확보한 단서로 정답을 도출하라.');
      }, 1000);
    }
  } catch (error) {
    console.error("통신 에러:", error);
    isWaiting.value = false;
    typeWriterEffect("<span style='color:#ef5350'>[SYS_ERROR]</span> 위성 연결 불안정. 재전송 하십시오.");
    await focusCommandInput();
  }
};

// 스트리밍 응답 뒤 서버 세션 상태를 확인해 클리어 화면으로 이동할지 판단합니다.
const navigateIfMissionCleared = async () => {
  try {
    const response = await apiClient.get(`/v1/sessions/${sessionId.value}/status`, {
      params: { userId: userId.value || 1 }
    });

    if (response.data?.cleared) {
      localStorage.removeItem(getMetricKey());
      setTimeout(() => {
        router.push({
          name: 'Clear',
          params: { missionId: sessionId.value },
          query: { regionId: regionId.value }
        });
      }, 900);
      return true;
    }
  } catch (error) {
    console.error('클리어 상태 확인 실패:', error);
  }
  return false;
};

// 짧은 키워드 입력은 정답 제출로 보고, 질문문/힌트 요청만 조력 횟수에 반영합니다.
const hasQuestionIntent = (text) => {
  const value = String(text || '').trim();
  if (!value) return false;

  const normalized = value.replace(/[?!.,~\s]/g, '');
  if (/^[가-힣A-Za-z0-9]{2,12}[?？]?$/.test(value) && normalized.length <= 8) {
    return false;
  }

  return value.includes('?')
      || value.includes('？')
      || /관련|맞아|인가|인가요|이야|뭐|무엇|어디|누구|왜|언제|어떻게|얼마나|힌트/.test(value);
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

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

/* 💡 추가됨: 뒤로가기 버튼 스타일 (요원님 테마색 준수) */
.btn-back,
.btn-clues {
  background: rgba(8, 189, 186, 0.1); border: 1px solid #08bdba;
  color: #08bdba; padding: 5px 10px; font-family: inherit; font-size: 0.8rem;
  font-weight: bold; cursor: pointer; border-radius: 3px; transition: 0.2s;
}
.btn-back:hover,
.btn-clues:hover { background: #08bdba; color: #000; box-shadow: 0 0 8px #08bdba; }
.btn-clues { border-color: rgba(248, 214, 109, 0.72); color: #f8d66d; background: rgba(248, 214, 109, 0.08); }
.btn-clues:hover { background: #f8d66d; border-color: #f8d66d; color: #000; box-shadow: 0 0 8px rgba(248, 214, 109, 0.55); }

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

.clue-panel-overlay {
  position: fixed;
  inset: 0;
  z-index: 900;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 22px;
  background: rgba(1, 8, 12, 0.82);
  backdrop-filter: blur(8px);
}

.clue-panel {
  width: min(620px, 100%);
  max-height: min(680px, 86vh);
  overflow-y: auto;
  border: 1px solid rgba(248, 214, 109, 0.52);
  border-radius: 8px;
  background: rgba(4, 12, 16, 0.96);
  padding: 24px;
  box-shadow: 0 0 28px rgba(248, 214, 109, 0.14);
}

.clue-panel-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 18px;
  padding-bottom: 14px;
  border-bottom: 1px solid rgba(248, 214, 109, 0.18);
}

.panel-kicker {
  margin: 0 0 6px;
  color: #f8d66d;
  font-size: 0.74rem;
  font-weight: 700;
  letter-spacing: 0;
}

.clue-panel h2 {
  margin: 0;
  color: #e0f2f1;
  font-size: 1.24rem;
}

.panel-close {
  flex: 0 0 auto;
  border: 1px solid rgba(248, 214, 109, 0.58);
  border-radius: 4px;
  background: transparent;
  color: #f8d66d;
  padding: 7px 10px;
  font-family: inherit;
  font-weight: 700;
  cursor: pointer;
}

.clue-log-list {
  display: grid;
  gap: 10px;
}

.clue-log {
  border: 1px solid rgba(8, 189, 186, 0.18);
  border-radius: 6px;
  background: rgba(8, 189, 186, 0.05);
  padding: 14px 16px;
}

.clue-log strong {
  display: block;
  margin-bottom: 7px;
  color: #80cbc4;
  font-size: 0.98rem;
}

.clue-log p,
.empty-clue-log {
  margin: 0;
  color: #cfd8dc;
  font-size: 0.9rem;
  line-height: 1.6;
  white-space: pre-line;
}

.scanner-modal { position: fixed; inset: 0; z-index: 1000; background: #000; }
.btn-abort { position: absolute; top: 20px; right: 20px; background: transparent; border: 1px solid #ef5350; color: #ef5350; padding: 10px 20px; z-index: 1001; cursor: pointer; font-family: inherit; font-weight: bold; border-radius: 4px; }
.btn-abort:hover { background: rgba(239, 83, 80, 0.1); }

@media (max-width: 720px) {
  .hud-header {
    gap: 12px;
    align-items: flex-start;
    flex-direction: column;
  }

  .sys-metrics {
    flex-wrap: wrap;
  }

  .message-block {
    max-width: 82%;
  }

  .terminal-interface {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
