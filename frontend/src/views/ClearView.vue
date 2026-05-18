<template>
  <main class="clear-view">
    <!-- 최종 미션 성공 후 실제 역사 해설과 단서별 각색 의도를 보여주는 결과 화면입니다. -->
    <section class="clear-panel">
      <template v-if="isLoading">
        <p class="eyebrow">LOADING CLEAR RECORD</p>
        <h1>클리어 기록 불러오는 중</h1>
        <p class="status-message">기록과 단서 해설을 복호화하고 있습니다. 잠시만 기다려 주세요.</p>
      </template>

      <template v-else-if="loadError || report.cleared === false">
        <p class="eyebrow">CLEAR RECORD LOCKED</p>
        <h1>{{ report.title || '기록 확인 불가' }}</h1>
        <p class="status-message">{{ loadError || report.message || '클리어하지 못한 사건입니다.' }}</p>
        <button class="primary-action" @click="goRegionDetail">작전 상세로</button>
      </template>

      <template v-else>
      <p class="eyebrow">MISSION CLEARED</p>
      <h1>{{ report.title || '작전 완료' }}</h1>
      <p class="keyword">핵심 키워드: {{ report.answerKeyword || '분석 완료' }}</p>
      <div v-if="hasScore" class="score-summary">
        <div>
          <span>SCORE</span>
          <strong>{{ report.score }}</strong>
        </div>
        <div>
          <span>TIME</span>
          <strong>{{ elapsedText }}</strong>
        </div>
        <div>
          <span>ROUTE</span>
          <strong>{{ routeDistanceText }}</strong>
        </div>
      </div>

      <article v-if="step === 'history'" class="history-stage">
        <h2>실제 역사 기록</h2>
        <div class="typewriter" aria-live="polite">
          <p v-for="(paragraph, index) in typedHistoryParagraphs" :key="index">
            {{ paragraph }}
          </p>
        </div>
        <button class="primary-action" :disabled="isTyping" @click="showClues">다음</button>
      </article>

      <article v-else class="clue-stage">
        <h2>수집한 단서</h2>
        <p class="stage-note">
          작전 중 마주친 장소와 연출은 실제 역사를 그대로 재현한 것이 아니라, 사건의 방향을 추리하도록 각색한 힌트입니다.
          단서를 선택하면 실제 사건의 어떤 면과 이어지는지 확인할 수 있습니다.
        </p>

        <div v-if="visibleClues.length" class="clue-list">
          <div v-for="clue in visibleClues" :key="clue.id" class="clue-item">
            <button
              class="clue-card"
              :class="{ selected: selectedClue?.id === clue.id }"
              @click="selectClue(clue)"
            >
              <strong>{{ clue.title }}</strong>
              <span>{{ clue.clue || clue.description || '현장에서 확보한 단서입니다.' }}</span>
            </button>

            <section v-if="selectedClue?.id === clue.id" class="adaptation-box">
              <h3>{{ selectedClue.title }}</h3>
              <p v-for="(paragraph, index) in selectedClueExplanation" :key="index">
                {{ paragraph }}
              </p>
            </section>
          </div>
        </div>
        <p v-else class="empty-message">수집한 단서 기록이 없습니다.</p>

        <button class="primary-action" @click="goHome">홈으로</button>
      </article>
      </template>
    </section>
  </main>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import apiClient from '@/api/axiosInstance';
import { useSessionStore } from '@/stores/sessionStore';

const route = useRoute();
const router = useRouter();
const sessionStore = useSessionStore();

const missionId = computed(() => route.params.missionId);
const regionId = computed(() => route.query.regionId || 1);
const userId = computed(() => sessionStore.userId || 1);

// clear-report API 응답과 지역 미션 목록에서 추출한 클리어 단서를 화면 상태로 관리합니다.
const report = ref({});
const clues = ref([]);
const visibleClues = ref([]);
const selectedClue = ref(null);
const typedHistory = ref('');
const isTyping = ref(false);
const step = ref('history');
const isLoading = ref(true);
const loadError = ref('');

let typingTimer = null;
const revealTimers = [];

// 서버 리포트가 비어도 최소한의 안내 문구를 보여주도록 fallback을 둡니다.
const historyText = computed(() => {
  const rawReport = report.value.report
    || '임무는 완료되었습니다. 이 장소와 핵심 키워드의 상세 역사 해설은 공공데이터 기반 기록 보강 단계에서 제공될 예정입니다.';

  return formatReadableParagraphs(rawReport);
});

// 점수가 0이면 아직 점수 계산 전 상태로 보고 요약 영역을 숨깁니다.
const hasScore = computed(() => Number(report.value.score) > 0);

// 초 단위 기록을 m:ss 형식으로 표시합니다.
const elapsedText = computed(() => {
  const seconds = Math.max(0, Number(report.value.elapsedSeconds) || 0);
  const minutes = Math.floor(seconds / 60);
  const remainingSeconds = seconds % 60;
  return `${minutes}m ${String(remainingSeconds).padStart(2, '0')}s`;
});

// 누적 이동 거리를 m/km 단위 문자열로 표시합니다.
const routeDistanceText = computed(() => {
  const meters = Math.max(0, Number(report.value.routeDistanceMeters) || 0);
  if (meters >= 1000) {
    return `${(meters / 1000).toFixed(2)}km`;
  }
  return `${Math.round(meters)}m`;
});

// 타자기 문자열을 문단 단위로 나눠 template에서 렌더링합니다.
const typedHistoryParagraphs = computed(() => {
  return typedHistory.value
    .split(/\n{2,}/)
    .map(paragraph => paragraph.trim())
    .filter(Boolean);
});

// 선택한 단서에 대해 AI가 만든 해설이 있으면 우선 쓰고, 없으면 로컬 fallback 해설을 구성합니다.
const selectedClueExplanation = computed(() => {
  if (!selectedClue.value) return [];

  const reportExplanation = report.value.clueExplanations?.[String(selectedClue.value.id)];
  if (Array.isArray(reportExplanation) && reportExplanation.length) {
    return reportExplanation;
  }
  if (typeof reportExplanation === 'string' && reportExplanation.trim()) {
    return formatReadableParagraphs(reportExplanation).split(/\n{2,}/).filter(Boolean);
  }

  const finalTitle = report.value.title || '최종 목적지';
  const keyword = report.value.answerKeyword || '핵심 사건';
  const clueTitle = selectedClue.value.title || '수집한 단서';
  const clueText = selectedClue.value.clue || selectedClue.value.description || '현장에서 확보한 단서';
  const relation = buildClueHistoricalRelation(clueTitle, clueText, keyword, finalTitle);

  return [
    `${clueTitle}에서 확인한 단서는 "${clueText}"였습니다.`,
    relation.connection,
    relation.caveat,
    relation.afterthought
  ];
});

// 클리어 리포트와 미션 목록을 병렬로 가져오고 역사 해설 타자기 효과를 시작합니다.
onMounted(async () => {
  try {
    const [reportResponse, missionsResponse] = await Promise.all([
      apiClient.get(`/v1/sessions/${missionId.value}/clear-report`, {
        params: { userId: userId.value || 1 }
      }),
      apiClient.get(`/v1/regions/${regionId.value}/missions`, {
        params: { userId: userId.value || 1 }
      })
    ]);

    report.value = reportResponse.data || {};
    clues.value = (missionsResponse.data || []).filter(mission =>
      mission.sessionStatus === 'CLEARED' && !getIsFinalMission(mission) && (mission.clue || mission.description)
    );

    if (report.value.cleared === false) {
      return;
    }
    startTypewriter();
  } catch (error) {
    loadError.value = error.userMessage || '클리어 기록을 불러오지 못했습니다.';
  } finally {
    isLoading.value = false;
  }
});

onBeforeUnmount(() => {
  clearTimeout(typingTimer);
  revealTimers.forEach(timer => clearTimeout(timer));
});

const startTypewriter = () => {
  clearTimeout(typingTimer);
  typedHistory.value = '';
  isTyping.value = true;

  let index = 0;
  const tick = () => {
    typedHistory.value = historyText.value.slice(0, index);
    index += 1;

    if (index <= historyText.value.length) {
      typingTimer = setTimeout(tick, 18);
    } else {
      isTyping.value = false;
    }
  };

  tick();
};

// 역사 해설을 다 본 뒤 단서 카드를 순차적으로 노출합니다.
const showClues = () => {
  if (isLoading.value || report.value.cleared === false) return;
  step.value = 'clues';
  visibleClues.value = [];
  selectedClue.value = null;

  clues.value.forEach((clue, index) => {
    const timer = setTimeout(() => {
      visibleClues.value.push(clue);
    }, index * 260);
    revealTimers.push(timer);
  });
};

// 같은 단서를 다시 누르면 상세 해설을 접습니다.
const selectClue = (clue) => {
  selectedClue.value = selectedClue.value?.id === clue.id ? null : clue;
};

// 백엔드 DTO 필드명 변화에 대응하기 위한 최종 미션 판별 helper입니다.
const getIsFinalMission = (mission) => {
  return mission && (mission.missionType === 'FINAL' || mission.isFinal === true || mission.final === true);
};

// 결과 확인 후 홈으로 돌아갑니다.
const goHome = () => {
  router.push({ name: 'Home' });
};

const goRegionDetail = () => {
  router.push({ name: 'RegionDetail', params: { regionId: regionId.value } });
};

// 긴 리포트를 한 문장씩 읽기 쉬운 문단으로 나눕니다.
const formatReadableParagraphs = (text) => {
  const normalized = String(text || '').replace(/\r\n/g, '\n').trim();
  if (!normalized) return '';

  const sentences = normalized
    .replace(/\n+/g, ' ')
    .match(/[^.!?。！？]+[.!?。！？]?/g)
    ?.map(sentence => sentence.trim())
    .filter(Boolean);

  return sentences?.length ? sentences.join('\n\n') : normalized;
};

// 특정 역사 사건에는 더 정확한 단서 연결 설명을 제공하고, 나머지는 일반 해설로 fallback합니다.
const buildClueHistoricalRelation = (title, clue, keyword, finalTitle) => {
  const source = `${title} ${clue}`.toLowerCase();
  const isAgwan = keyword.includes('아관파천');

  if (isAgwan && hasAny(source, ['이화', '교육', '여성', '독립', '발상지'])) {
    return {
      connection: `이 단서는 ${keyword}의 발생 장소를 직접 가리키기보다, 그 사건을 둘러싼 시대 분위기를 끌어온 힌트입니다. 왕실이 외세의 압박 속에서 흔들리던 시기에도 정동 일대에는 근대 교육, 선교, 독립 의식 같은 새로운 흐름이 함께 자라고 있었습니다.`,
      caveat: `${title}가 ${keyword}의 실제 현장이라는 뜻은 아닙니다. 다만 이 장소가 품은 근대성, 교육, 독립의 이미지를 빌려 ${finalTitle}로 이어지는 시대의 긴장감을 힌트 위치에 심어 둔 것입니다.`,
      afterthought: `정답을 알고 나면 이 단서는 궁궐 안팎에서 동시에 진행되던 권력의 위기와 사회의 변화를 떠올리게 하는 우회 단서가 됩니다.`
    };
  }

  if (isAgwan && hasAny(source, ['러시아', '공사관', '외교', '공사', '정동'])) {
    return {
      connection: `${keyword}에서 중요한 축은 고종이 러시아 공사관으로 거처를 옮기며 조선의 권력 중심과 외교 지형이 흔들렸다는 점입니다. 이 단서는 이동의 목적지, 외세의 영향력, 정동 일대의 외교 공간성을 떠올리도록 배치했습니다.`,
      caveat: `${title}에서 본 세부 물건이나 지점이 모두 실제 사건 기록과 일치한다는 뜻은 아닙니다. 대신 러시아 공사관과 외교 공간이라는 역사적 방향을 따라가게 하려고 해당 위치를 힌트로 각색했습니다.`,
      afterthought: `그래서 이 단서는 길을 맞히는 문제라기보다, 왜 한 나라의 국왕이 궁궐 밖 외국 공관으로 이동해야 했는지를 묻는 단서에 가깝습니다.`
    };
  }

  if (isAgwan && hasAny(source, ['덕수', '경운궁', '궁', '돌담', '문', '대한제국', '황제', '고종'])) {
    return {
      connection: `${keyword}는 고종의 신변 위협, 궁궐을 둘러싼 정치적 불안, 이후 경운궁과 대한제국으로 이어지는 흐름을 함께 봐야 이해되는 사건입니다. 이 단서는 궁궐의 경계와 왕의 이동이라는 요소를 빌려 최종 목적지 주변의 역사적 압박감을 느끼도록 설계했습니다.`,
      caveat: `${title}의 현재 위치가 사건의 모든 장면을 그대로 증명하는 장소라는 뜻은 아닙니다. 실제 역사의 큰 흐름인 왕실의 이동, 궁궐의 변화, 권력 중심의 재편을 현장 힌트로 압축한 것입니다.`,
      afterthought: `정답을 알고 다시 보면 돌담과 문, 궁궐 주변의 길은 배경이 아니라 당시 권력이 어디에 머물고 어디로 밀려났는지를 보여주는 단서가 됩니다.`
    };
  }

  if (isAgwan && hasAny(source, ['명성황후', '을미', '시해', '일본', '친러', '친일', '위협'])) {
    return {
      connection: `${keyword}의 배경에는 명성황후 시해 이후 커진 신변 위협과 조선을 둘러싼 열강의 압박이 놓여 있습니다. 이 단서는 고종이 궁궐을 떠나는 선택을 하게 된 정치적 배경을 떠올리도록 배치했습니다.`,
      caveat: `${title}가 사건의 직접 현장이라고 단정하는 힌트는 아닙니다. 대신 당시 조선 왕실이 느낀 위기감과 외세 사이의 긴장을 현장 단서로 바꿔 놓은 것입니다.`,
      afterthought: `이 관점에서 보면 단서는 정답 이름을 맞히는 암호가 아니라, 왜 그 사건이 불가피한 선택처럼 밀려왔는지 이해하게 만드는 조각입니다.`
    };
  }

  const theme = inferClueTheme(`${title} ${clue}`);
  return {
    connection: `이 단서는 ${keyword}와 연결되는 ${theme}을 현장에서 떠올리도록 배치했습니다. 힌트 문구가 직접 정답을 말하지 않는 대신, 사건을 둘러싼 장소, 인물, 시대 분위기 중 하나를 우회해서 보여주는 방식입니다.`,
    caveat: `${title}가 실제 사건의 직접 현장이라는 뜻은 아닐 수 있습니다. 다만 해당 장소의 이미지와 단서 문구를 이용해 ${finalTitle}에 얽힌 역사적 맥락으로 시선을 옮기도록 각색했습니다.`,
    afterthought: `정답을 알고 다시 보면 이 단서는 단순한 픽션 장치가 아니라, 실제 역사에서 무엇을 봐야 하는지 방향을 잡아 주는 작은 표식으로 읽을 수 있습니다.`
  };
};

// 여러 키워드 중 하나라도 포함하는지 확인하는 단순 유틸입니다.
const hasAny = (text, keywords) => {
  return keywords.some(keyword => text.includes(keyword));
};

// 단서 문구의 키워드를 바탕으로 어떤 역사적 관점의 힌트인지 대략 분류합니다.
const inferClueTheme = (text) => {
  const value = text.toLowerCase();

  if (value.includes('궁') || value.includes('문') || value.includes('길') || value.includes('공사관')) {
    return '장소성과 이동 경로';
  }
  if (value.includes('왕') || value.includes('고종') || value.includes('황제') || value.includes('권력')) {
    return '권력의 이동과 당시 인물의 선택';
  }
  if (value.includes('러시아') || value.includes('일본') || value.includes('외교') || value.includes('공사')) {
    return '국제 정세와 외교적 긴장';
  }
  if (value.includes('명판') || value.includes('비석') || value.includes('표지') || value.includes('기록')) {
    return '현장에 남은 기록과 기억';
  }

  return '배경, 장소, 인물 관계';
};
</script>

<style scoped>
.clear-view {
  min-height: 100vh;
  background: #06100f;
  color: #e5f7f4;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 42px 18px;
  font-family: 'Noto Sans KR', system-ui, sans-serif;
}

.clear-panel {
  width: min(980px, 100%);
  min-height: 760px;
  border: 1px solid rgba(24, 208, 194, 0.35);
  background: rgba(3, 14, 16, 0.94);
  padding: 40px;
  border-radius: 8px;
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.35);
}

.eyebrow {
  margin: 0 0 8px;
  color: #18d0c2;
  font-size: 0.82rem;
  letter-spacing: 0.12em;
  font-weight: 700;
}

h1,
h2,
h3,
p {
  margin-top: 0;
}

h1 {
  margin-bottom: 8px;
  font-size: clamp(1.85rem, 3vw, 2.65rem);
  line-height: 1.2;
}

h2 {
  color: #18d0c2;
  font-size: 1.22rem;
  margin-bottom: 18px;
}

h3 {
  color: #f8d66d;
  font-size: 1.08rem;
  margin-bottom: 12px;
}

.keyword {
  color: #f8d66d;
  margin-bottom: 18px;
  font-size: 1.08rem;
  font-weight: 700;
}

.status-message {
  max-width: 720px;
  color: #adc3c0;
  font-size: 1.08rem;
  line-height: 1.7;
}

.score-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 32px;
}

.score-summary div {
  border: 1px solid rgba(248, 214, 109, 0.35);
  background: rgba(248, 214, 109, 0.07);
  border-radius: 6px;
  padding: 13px 14px;
}

.score-summary span {
  display: block;
  color: #adc3c0;
  font-size: 0.74rem;
  font-weight: 800;
  letter-spacing: 0.08em;
  margin-bottom: 5px;
}

.score-summary strong {
  color: #f8d66d;
  font-size: 1.3rem;
}

.history-stage,
.clue-stage {
  border-top: 1px solid rgba(255, 255, 255, 0.12);
  padding-top: 28px;
}

.typewriter {
  min-height: 440px;
  color: #dce8e6;
  font-size: 1.22rem;
  line-height: 1.9;
}

.typewriter p {
  margin-bottom: 20px;
}

.stage-note {
  color: #adc3c0;
  font-size: 1.03rem;
  line-height: 1.75;
  margin-bottom: 22px;
}

.clue-list {
  display: grid;
  gap: 12px;
  margin-bottom: 20px;
}

.clue-item {
  display: grid;
  gap: 10px;
  animation: rise-in 360ms ease both;
}

.clue-card {
  width: 100%;
  text-align: left;
  display: grid;
  gap: 6px;
  color: #e5f7f4;
  border: 1px solid rgba(255, 255, 255, 0.1);
  background: rgba(255, 255, 255, 0.05);
  border-radius: 6px;
  padding: 15px 17px;
  cursor: pointer;
}

.clue-card:hover,
.clue-card.selected {
  border-color: rgba(248, 214, 109, 0.8);
  background: rgba(248, 214, 109, 0.09);
}

.clue-card strong {
  font-size: 1.03rem;
}

.clue-card span {
  color: #c5d1cf;
  line-height: 1.6;
  white-space: pre-line;
}

.adaptation-box {
  border-left: 3px solid #f8d66d;
  background: rgba(248, 214, 109, 0.08);
  padding: 20px;
  margin: 0 0 10px;
}

.adaptation-box p,
.empty-message {
  color: #dce8e6;
  font-size: 1.04rem;
  line-height: 1.8;
  margin-bottom: 16px;
}

.adaptation-box p:last-child {
  margin-bottom: 0;
}

.primary-action {
  display: block;
  margin-left: auto;
  min-width: 120px;
  background: #18d0c2;
  color: #031010;
  border: 1px solid #18d0c2;
  border-radius: 6px;
  padding: 11px 18px;
  font-weight: 800;
  cursor: pointer;
}

.primary-action:disabled {
  opacity: 0.45;
  cursor: wait;
}

@keyframes rise-in {
  from {
    opacity: 0;
    transform: translateY(18px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (max-width: 640px) {
  .clear-view {
    padding: 24px 14px;
  }

  .clear-panel {
    min-height: auto;
    padding: 24px 18px;
  }

  .typewriter {
    min-height: 440px;
    font-size: 1.08rem;
  }

  .score-summary {
    grid-template-columns: 1fr;
  }
}
</style>
