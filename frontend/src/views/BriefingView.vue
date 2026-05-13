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
const finalTargetLabel = computed(() => finalMission.value?.isUnlocked ? finalMission.value.title : 'CLASSIFIED');

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
    fullText.value = "본부와의 통신이 원활하지 않습니다.\n\n지역 기록과 작전 노드가 완전히 복호화되지 않았습니다. 잠시 후 다시 시도하십시오.";
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
  const description = formatReadableParagraphs(String(region.description || '').replace(/<br\s*\/?>/gi, '\n'));
  const hintTitles = hintMissions.value
    .slice(0, 3)
    .map(mission => mission.title)
    .filter(Boolean);
  const hintLine = hintTitles.length
    ? hintTitles.join(', ')
    : '아직 복호화 중인 현장 노드';
  const hintCount = hintMissions.value.length || 3;

  return [
    `요원, ${region.name || regionName.value} 구역 작전 브리핑을 시작한다. 지금부터 전송되는 내용은 단순 관광 안내가 아니라 현장 기록, 장소의 잔향, 그리고 숨겨진 사건 키워드를 역추적하기 위한 작전 명령이다.`,
    description || '이 구역의 기록은 완전히 정리되지 않았다. 본부는 현장 주변에 흩어진 표식과 장소의 맥락을 통해 최종 사건 키워드를 복원해야 한다고 판단했다.',
    `초기 조사 노드는 ${hintCount}개다. 현재 우선 탐색 대상으로 분류된 지점은 ${hintLine}이며, 각 지점은 표면적으로는 평범해 보여도 최종 장소와 같은 시대적 긴장, 인물의 선택, 사건의 흔적을 서로 다른 각도에서 비춘다.`,
    `각 현장에서는 사진 인증 목표만 좇지 말고 주변 안내문, 비문, 현판, 연도, 인명, 반복되는 표현을 함께 확인하라. 본부가 제공하는 단서는 정답을 직접 말하지 않는다. 서로 맞지 않아 보이는 문장들을 겹쳐 읽을 때만 최종 키워드의 윤곽이 드러난다.`,
    `최종 목적지는 아직 봉인되어 있다. 힌트 노드를 충분히 확보하면 좌표가 해금되고, 도착 후에는 촬영 임무가 아니라 본부 AI와의 추론 채널이 열린다. 그때부터는 모아온 단서와 현장 표식을 바탕으로 사건의 이름을 직접 도출해야 한다.`,
    `작전 원칙은 세 가지다. 첫째, 장소명과 인물명에 너무 빨리 매달리지 말 것. 둘째, 같은 단어가 다른 지점에서 반복되는지 확인할 것. 셋째, 최종 장소에서 보이는 물리적 단서를 마지막 검증 축으로 삼을 것. 준비가 끝났다면 현장 투입을 승인하라.`
  ].join('\n\n');
};

const getIsFinalMission = (mission) => {
  return mission && (mission.missionType === 'FINAL' || mission.isFinal === true || mission.final === true);
};

const formatReadableParagraphs = (text) => {
  const normalized = String(text || '').replace(/\r\n/g, '\n').trim();
  if (!normalized) return '';

  const sentences = normalized
    .replace(/\n+/g, ' ')
    .match(/[^.!?。！？]+[.!?。！？]?/g)
    ?.map(sentence => sentence.trim())
    .filter(Boolean);

  if (!sentences?.length) return normalized;

  return sentences.join('\n\n');
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
