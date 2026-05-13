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

        <div class="intel-grid">
          <div>
            <span>HINT NODES</span>
            <strong>{{ hintMissionCount }}</strong>
          </div>
          <div>
            <span>FINAL TARGET</span>
            <strong>{{ finalTargetLabel }}</strong>
          </div>
          <div>
            <span>PROTOCOL</span>
            <strong>FIELD_TRACE</strong>
          </div>
        </div>

        <div class="message-area">
          <div class="typewriter">
            <p v-for="(paragraph, index) in displayedParagraphs" :key="index">
              {{ paragraph }}<span class="cursor" v-if="!isFinished && index === displayedParagraphs.length - 1">_</span>
            </p>
          </div>
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
import { computed, ref, onMounted, onBeforeUnmount } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import apiClient from '@/api/axiosInstance';
import { useSessionStore } from '@/stores/sessionStore';

const route = useRoute();
const router = useRouter();
const sessionStore = useSessionStore();

// 동적 할당용 변수
const regionId = route.query.regionId || 1;
const regionName = ref('LOADING...');
const fullText = ref('');
const displayedText = ref('');
const isFinished = ref(false);
const missions = ref([]);
let typingInterval = null;

const hintMissions = computed(() => missions.value.filter(mission => !getIsFinalMission(mission)));
const finalMission = computed(() => missions.value.find(getIsFinalMission) || null);
const hintMissionCount = computed(() => hintMissions.value.length || '---');
const finalTargetLabel = computed(() => getIsUnlockedMission(finalMission.value) ? finalMission.value.title : 'CLASSIFIED');

const displayedParagraphs = computed(() => {
  return displayedText.value
    .split(/\n{2,}/)
    .map(paragraph => paragraph.trim())
    .filter(Boolean);
});

onMounted(async () => {
  try {
    const [regionResult, missionsResult] = await Promise.allSettled([
      apiClient.get(`/v1/regions/${regionId}`),
      apiClient.get(`/v1/regions/${regionId}/missions`, {
        params: { userId: sessionStore.userId || 1 }
      })
    ]);

    if (regionResult.status !== 'fulfilled') {
      throw regionResult.reason;
    }

    const regionResponse = regionResult.value;
    regionName.value = regionResponse.data.name;
    missions.value = missionsResult.status === 'fulfilled' ? missionsResult.value.data || [] : [];
    fullText.value = buildBriefingText(regionResponse.data);
    startTyping();
  } catch (error) {
    console.error("데이터 로드 실패:", error);
    fullText.value = "본부와의 통신이 원활하지 않음을 인지하라.\n\n봉인된 기록이 완전히 복호화되지 않았음을 기억하라. 잠시 후 다시 접속하라.";
    startTyping();
  }
});

onBeforeUnmount(() => {
  clearInterval(typingInterval);
});

const startTyping = () => {
  clearInterval(typingInterval);
  displayedText.value = '';
  isFinished.value = false;

  let i = 0;
  typingInterval = setInterval(() => {
    if (i < fullText.value.length) {
      displayedText.value += fullText.value[i];
      i++;
    } else {
      completeTyping();
    }
  }, 28);
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

const buildBriefingText = (region) => {
  const scenario = buildNarrativeScenario(region);
  const finalThread = buildFinalStoryThread();

  return [
    '요원, 봉인된 기록을 수신하라.',
    scenario,
    finalThread
  ].join('\n\n');
};

const buildNarrativeScenario = (region) => {
  const generatedStory = compactText(region.description, 700);
  if (isUsableNarrative(generatedStory)) {
    return normalizeImperativeTone(generatedStory);
  }

  const fragments = hintMissions.value
    .map(buildStoryFragment)
    .filter(Boolean);

  if (!fragments.length) {
    return '오래된 문이 안쪽에서 잠기고, 이름 없는 기록만 어둠 속에 남았음을 기억하라. 누군가 지워 둔 문장 사이에서 사건의 그림자가 다시 움직이기 시작했음을 의심하라.';
  }

  return [
    '오래된 기록은 닫힌 방처럼 침묵하고, 첫 문장은 이미 누군가에 의해 찢겨 나갔음을 기억하라.',
    fragments.join(' '),
    '서로 맞지 않는 장면들이 하나의 사건을 가리키고 있음을 의심하라. 마지막 이름은 아직 어둠 속에 남겨 두라.'
  ].join('\n\n');
};

const buildStoryFragment = (mission) => {
  const narrative = compactText(mission.description, 180);
  if (isUsableNarrative(narrative)) {
    return normalizeImperativeTone(narrative);
  }

  const signal = getStorySignal(mission);
  return `낡은 ${signal}이 한 번 지워진 장면을 다시 비추고 있음을 기억하라. 그 흔적이 결말을 말하지 않고 침묵만 남겼음을 의심하라.`;
};

const buildFinalStoryThread = () => {
  const final = finalMission.value;
  if (!final) {
    return '마지막 장면은 아직 도착하지 않았음을 기억하라. 결말을 먼저 열지 말고, 닫힌 기록이 스스로 균열을 낼 때까지 의심하라.';
  }

  if (getIsUnlockedMission(final)) {
    const finalNarrative = compactText(final.description, 220);
    if (isUsableNarrative(finalNarrative)) {
      return normalizeImperativeTone(finalNarrative);
    }
    return `마지막 장면에 남은 ${getStorySignal(final)}이 앞선 기록들을 하나의 사건으로 묶고 있음을 기억하라. 그 이름을 너무 일찍 부르지 말고, 침묵이 끝까지 남긴 균열을 의심하라.`;
  }

  return '마지막 장면은 아직 봉인되어 있음을 기억하라. 흩어진 장면들이 충분히 서로를 부를 때, 감춰진 결말도 스스로 어둠 밖으로 밀려날 것이라 의심하라.';
};

const getStorySignal = (mission) => {
  if (mission?.visionKeyword) {
    return `'${mission.visionKeyword}'`;
  }
  if (mission?.fieldClue) {
    return compactText(mission.fieldClue, 90);
  }
  return '이름 없는 표식';
};

const isUsableNarrative = (text) => {
  if (!text) return false;
  const blockedTerms = ['마커', '좌표', 'TourAPI', '사진', '촬영', 'AI', '채팅', '이동', '지역', '지도', '힌트 노드', '투입 지시', '판독 기준'];
  const mixedToneTerms = ['습니다', '입니다', '하세요', '하십시오', '하시오'];
  return !blockedTerms.some(term => text.includes(term))
    && !mixedToneTerms.some(term => text.includes(term));
};

const normalizeImperativeTone = (text) => {
  return String(text || '')
    .replace(/하십시오/g, '하라')
    .replace(/하세요/g, '하라')
    .replace(/해 주세요/g, '하라')
    .replace(/합니다/g, '하라')
    .replace(/입니다/g, '이다')
    .replace(/하십시오/g, '하라')
    .replace(/하시오/g, '하라')
    .replace(/시오/g, '라');
};

const getIsFinalMission = (mission) => {
  return mission && (mission.missionType === 'FINAL' || mission.isFinal === true || mission.final === true);
};

const getIsUnlockedMission = (mission) => {
  return mission && (mission.isUnlocked === true || mission.unlocked === true);
};

const compactText = (text, maxLength) => {
  const normalized = String(text || '')
    .replace(/<br\s*\/?>/gi, ' ')
    .replace(/<[^>]*>/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();

  if (!normalized) return '';
  if (normalized.length <= maxLength) return normalized;
  return `${normalized.slice(0, maxLength).trim()}...`;
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
  max-width: 920px;
  min-height: 72vh;
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
  padding: 34px;
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

.intel-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 26px;
}

.intel-grid div {
  min-width: 0;
  border: 1px solid rgba(0, 255, 204, 0.18);
  border-radius: 6px;
  background: rgba(0, 255, 204, 0.04);
  padding: 12px 14px;
}

.intel-grid span {
  display: block;
  margin-bottom: 5px;
  color: #64748b;
  font-size: 0.72rem;
  font-weight: 700;
}

.intel-grid strong {
  display: block;
  overflow: hidden;
  color: #00ffcc;
  font-size: 0.94rem;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.message-area {
  min-height: 360px;
}

.typewriter {
  color: #e2e8f0;
  font-size: 1.18rem;
  line-height: 1.9;
  white-space: pre-wrap; /* \n 기호를 실제 줄바꿈으로 인식하게 만듭니다 */
}

/* 깜빡이는 커서 */
.typewriter p {
  margin: 0 0 18px;
}

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

@media (max-width: 680px) {
  .terminal-body {
    padding: 24px 20px;
  }

  .intel-grid {
    grid-template-columns: 1fr;
  }

  .typewriter {
    font-size: 1.02rem;
  }
}
</style>
