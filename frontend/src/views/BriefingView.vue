<template>
  <div class="briefing-container">
    <!-- 작전 시작 전 지역 서사를 타자기 효과로 보여주는 화면입니다. -->
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

// route query의 regionId가 이후 미션 목록/지도 화면의 기준값입니다.
const regionId = route.query.regionId || 1;
const regionName = ref('LOADING...');
const fullText = ref('');
const displayedText = ref('');
const isFinished = ref(false);
const missions = ref([]);
let typingInterval = null;

// 힌트/최종 미션 구분은 백엔드 DTO 필드명이 바뀌어도 동작하도록 helper를 사용합니다.
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

// Region과 Mission을 병렬로 불러오고, 실패하면 fallback 브리핑을 출력합니다.
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
    fullText.value = "본부와의 통신이 불안정하다.\n\n봉인된 기록은 아직 완전히 복호화되지 않았다. 잠시 후 암호 채널을 다시 개방하라.";
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

// 전체 브리핑을 즉시 보여주고 시작 버튼을 활성화합니다.
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

// 백엔드가 저장한 region.description을 우선 사용하고, 품질이 낮으면 로컬 템플릿으로 보정합니다.
const buildBriefingText = (region) => {
  const scenario = buildNarrativeScenario(region);

  return formatBriefingBlock(scenario);
};

// 저장된 작전 설명을 우선 사용합니다. 로컬 추론 템플릿은 엉뚱한 지역 서사를 만들 수 있어 최소 fallback으로만 씁니다.
const buildNarrativeScenario = (region) => {
  const generatedStory = compactText(region.description, 1200);
  if (isUsableNarrative(generatedStory)) {
    return normalizeDirectiveTone(generatedStory);
  }

  return buildStoryPrequel(region);
};

// 저장된 설명이 없거나 명백히 깨졌을 때만 작전명 기반의 일반 fallback을 사용합니다.
const buildStoryPrequel = (region) => {
  return buildArchivePrequel(region);
};

const buildRailIndustryPrequel = (region) => {
  const operationName = getOperationName(region);
  return [
    operationName ? `작전명 ${operationName}.` : '',
    '눈이 길게 남는 동쪽 산맥 아래에서 철길은 생계를 나르는 약속이었다. 새벽마다 검은 가루를 뒤집어쓴 작업복들이 역 앞에 모였고, 가족들은 돌아올 시간을 열차 소리로 가늠했다.',
    '관광의 불빛이 그 길 위에 덧칠되기 훨씬 전, 한 역무원이 낡은 운행일지에서 사라진 칸을 발견했다. 그 칸에는 화물의 무게도 승객의 이름도 아닌, 누군가 일부러 지운 겨울밤의 기록이 남아 있었다.',
    '역무원은 그 사실을 알린 뒤 마지막 열차를 확인하러 나갔고 돌아오지 않았다. 남겨진 것은 찢어진 일지 몇 장과 서로 다른 장소를 가리키는 짧은 문장들뿐이다.',
    '이 이야기는 그가 사라진 다음 날 아침부터 시작된다. 너는 철길 위에 덧씌워진 밝은 이름 아래로 내려가, 왜 한 시대의 노동과 이동의 기억이 하나의 결말을 감추게 되었는지 밝혀내야 한다.'
  ].filter(Boolean).join('\n\n');
};

const buildRoyalModernPrequel = (region) => {
  const operationName = getOperationName(region);
  return [
    operationName ? `작전명 ${operationName}.` : '',
    '궁의 불이 하나둘 꺼지던 밤, 서고를 지키던 하급 관리가 봉인되지 않은 문서 한 장을 발견했다. 문서에는 왕의 이름도, 외교관의 서명도 없었지만 붉은 인장이 찍힌 자리가 칼로 긁혀 있었다.',
    '다음 날 아침, 관리의 자리는 비어 있었고 책상에는 같은 문장을 여러 번 베껴 쓴 종이만 남아 있었다. 그는 누가 명령했는지 쓰지 못한 채, 오래된 질서가 무너지는 순간을 조각으로만 남겼다.',
    '본편은 그가 숨긴 문서가 다시 발견되며 시작된다. 너는 권력의 복도에 남은 빈칸을 따라가며, 한 시대가 왜 다른 이름으로 덮였는지 밝혀야 한다.'
  ].filter(Boolean).join('\n\n');
};

const buildWarMemoryPrequel = (region) => {
  const operationName = getOperationName(region);
  return [
    operationName ? `작전명 ${operationName}.` : '',
    '포성이 멎은 뒤에도 어떤 마을에는 밤마다 같은 발소리가 남았다. 피난 수첩에는 돌아오지 못한 사람들의 이름이 줄을 잇고, 마지막 장만 누군가 찢어 간 흔적이 남아 있었다.',
    '수첩을 보관하던 노인은 죽기 전, 그 마지막 장에 전투의 승패가 아니라 살아남은 사람들이 감추어야 했던 약속이 적혀 있었다고 말했다. 그러나 그는 끝내 그 약속의 이름을 말하지 않았다.',
    '이 이야기는 사라진 마지막 장을 찾는 데서 시작된다. 너는 전쟁의 큰 이름 뒤에 가려진 개인의 기억을 복원하고, 왜 그 약속이 지금까지 봉인되었는지 밝혀야 한다.'
  ].filter(Boolean).join('\n\n');
};

const buildCoastalMemoryPrequel = (region) => {
  const operationName = getOperationName(region);
  return [
    operationName ? `작전명 ${operationName}.` : '',
    '바람이 거센 해안 마을에는 오래전부터 불빛을 보고 돌아오지 못한 사람들에 대한 이야기가 전해졌다. 낡은 항해 일지에는 날씨와 파도 대신, 같은 밤을 가리키는 짧은 표시만 반복되어 있었다.',
    '일지를 맡아 보관하던 등대지기는 어느 날 새벽, 표시가 가리키는 방향을 확인하러 나간 뒤 사라졌다. 그의 방에는 젖은 모래와 찢긴 지도, 그리고 아직 마르지 않은 잉크 자국이 남았다.',
    '본편은 그가 마지막으로 본 불빛에서 시작된다. 너는 바다 위에 흩어진 기억을 모아, 한 마을이 왜 그 밤을 다른 이야기로 바꾸어 전해 왔는지 밝혀야 한다.'
  ].filter(Boolean).join('\n\n');
};

const buildLocalTourismPrequel = (region) => {
  const operationName = getOperationName(region);
  return [
    operationName ? `작전명 ${operationName}.` : '',
    '사람들이 떠난 뒤 비어 가던 마을은 어느 해부터 축제와 간판, 새로운 이름으로 다시 불리기 시작했다. 그러나 오래된 장부에는 그 이름이 생기기 전 사라진 골목과 지워진 가게들이 그대로 남아 있었다.',
    '장부를 정리하던 기획자는 새 지도와 옛 지도가 맞지 않는다는 사실을 발견했다. 그는 마을이 되살아난 이유보다, 무엇을 잊어야 되살아날 수 있었는지를 먼저 기록했다.',
    '이 이야기는 축제가 시작되기 전날 밤에서 열린다. 너는 밝은 표면 아래 묻힌 생활의 흔적을 따라가며, 새 이름이 덮어 버린 오래된 결말을 찾아야 한다.'
  ].filter(Boolean).join('\n\n');
};

const buildArchivePrequel = (region) => {
  const operationName = getOperationName(region);
  return [
    operationName ? `작전명 ${operationName}.` : '',
    '오래된 기록을 정리하던 조사원이 같은 날짜가 서로 다른 이름으로 남아 있다는 사실을 발견했다. 한 기록은 사건이 끝났다고 말했고, 다른 기록은 아직 시작되지 않았다고 적혀 있었다.',
    '조사원은 두 기록 사이에 빠진 하루를 찾기 위해 현장으로 향했지만, 그날 밤 이후 연락이 끊겼다. 책상 위에는 찢긴 메모와 순서가 뒤바뀐 사진 몇 장만 남아 있었다.',
    '본편은 그가 찾지 못한 하루에서 시작된다. 너는 흩어진 장면을 제자리에 놓고, 왜 누군가 이 이야기의 시작을 바꾸려 했는지 밝혀야 한다.'
  ].filter(Boolean).join('\n\n');
};

const getOperationName = (region) => {
  const name = compactText(region?.name, 36);
  if (!name || name.includes('작전명 봉인된 현장')) return '';
  return name.replace(/^작전명\s*/g, '').trim();
};

// 명백히 비어 있거나 내부 오류 문구인 경우만 fallback 처리합니다.
const isUsableNarrative = (text) => {
  if (!text) return false;
  return !isMechanicalBriefing(text);
};

const isMechanicalBriefing = (text) => {
  const rememberCount = (text.match(/기억하라/g) || []).length;
  const suspectCount = (text.match(/의심하라/g) || []).length;
  const silenceCount = (text.match(/침묵/g) || []).length;
  return rememberCount + suspectCount >= 3
    || silenceCount >= 3
    || text.includes('한 번 지워진 장면을 다시 비추고')
    || text.includes('서로 맞지 않는 장면들이')
    || text.includes('기록보관소 지하 서버')
    || text.includes('출처 불명의 음성 파일')
    || text.includes('파일을 남긴 기록관')
    || text.includes('본부는 이 조작된 이야기')
    || text.includes('배경 기록은 일부만 복호화');
};

// AI 문체가 명령형/존댓말로 흔들릴 때 방탈출 서사형 문체로 정리합니다.
const normalizeDirectiveTone = (text) => {
  return normalizeImperativeTone(cleanBriefingText(text))
    .replace(/있음을 기억하라/g, '있다')
    .replace(/했음을 기억하라/g, '했다')
    .replace(/였음을 기억하라/g, '였다')
    .replace(/있음을 의심하라/g, '있을 가능성이 높다')
    .replace(/했음을 의심하라/g, '했다는 신호다')
    .replace(/침묵만 남겼음을 의심하라/g, '결말을 숨긴 채 멈춰 있다');
};

// 브리핑에 불필요한 bracket, 고정 오프닝, placeholder 표현을 제거합니다.
const cleanBriefingText = (text) => {
  return String(text || '')
    .replace(/요원,\s*본부 암호 채널을 개방한다\.?/g, '')
    .replace(/작전명\s*\[([^\]]+)\]/g, '작전명 $1')
    .replace(/\[([^\]]+)\]/g, '$1')
    .replace(/'핵심 기록'/g, '핵심 기록')
    .replace(/'최종 현장'/g, '최종 현장');
};

// 존댓말/지시형 표현을 단정형에 가깝게 보정합니다.
const normalizeImperativeTone = (text) => {
  return String(text || '')
    .replace(/이었습니다/g, '이었다')
    .replace(/였습니다/g, '였다')
    .replace(/있었습니다/g, '있었다')
    .replace(/없었습니다/g, '없었다')
    .replace(/했습니다/g, '했다')
    .replace(/되었습니다/g, '되었다')
    .replace(/었습니다/g, '었다')
    .replace(/았습니다/g, '았다')
    .replace(/됩니다/g, '된다')
    .replace(/있습니다/g, '있다')
    .replace(/없습니다/g, '없다')
    .replace(/합니다/g, '한다')
    .replace(/하십시오/g, '하라')
    .replace(/하세요/g, '하라')
    .replace(/해 주세요/g, '하라')
    .replace(/입니다/g, '이다')
    .replace(/하십시오/g, '하라')
    .replace(/하시오/g, '하라')
    .replace(/시오/g, '라');
};

// 긴 문단을 터미널에서 읽기 좋은 길이로 나눕니다.
const formatBriefingBlock = (text) => {
  const normalized = cleanBriefingText(text).trim();
  if (!normalized) return '';

  return normalized
    .split(/\n{2,}/)
    .flatMap(splitReadableParagraphs)
    .filter(Boolean)
    .join('\n\n');
};

// 문장 단위로 잘라 1~2문장 정도의 문단으로 재조합합니다.
const splitReadableParagraphs = (block) => {
  const sentences = splitSentences(block);
  const paragraphs = [];
  let current = [];
  let currentLength = 0;

  sentences.forEach(sentence => {
    const nextLength = currentLength + sentence.length;
    if (current.length && (current.length >= 2 || nextLength > 170)) {
      paragraphs.push(current.join(' '));
      current = [];
      currentLength = 0;
    }
    current.push(sentence);
    currentLength += sentence.length;
  });

  if (current.length) {
    paragraphs.push(current.join(' '));
  }
  return paragraphs;
};

// 한국어 마침표와 영문 구두점을 기준으로 문장을 분리합니다.
const splitSentences = (text) => {
  const normalized = String(text || '').replace(/\s+/g, ' ').trim();
  if (!normalized) return [];
  return normalized.match(/[^.!?]+[.!?]?/g)?.map(sentence => sentence.trim()).filter(Boolean) || [normalized];
};

// 백엔드/프론트 필드명 차이를 흡수하는 최종 미션 판별 helper입니다.
const getIsFinalMission = (mission) => {
  return mission && (mission.missionType === 'FINAL' || mission.isFinal === true || mission.final === true);
};

// 최종 미션 해금 여부도 isUnlocked/unlocked 모두 허용합니다.
const getIsUnlockedMission = (mission) => {
  return mission && (mission.isUnlocked === true || mission.unlocked === true);
};

// HTML 태그와 과도한 공백을 제거하고 최대 길이를 제한합니다.
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
