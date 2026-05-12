<template>
  <div class="dashboard-container" :class="{ 'area-mode': !isAreaSelected }">
    <div class="bg-glow blob-1"></div>
    <div class="bg-glow blob-2"></div>

    <div class="content-wrapper">
      <header class="dashboard-header">
        <div class="title-group">
          <h1 class="title">OPERATION<span class="highlight">: {{ activeArea?.label || 'KOREA' }}</span></h1>
          <p class="subtitle">{{ isAreaSelected ? `${activeArea.name} 작전 목록 데이터베이스 접근 중...` : '대한민국 작전망 대기 중...' }}</p>
        </div>
        <div class="user-panel">
          <button v-if="isAreaSelected" @click="returnToAreaSelection" class="region-back-btn">지역 선택</button>
          <span class="agent-name">요원 [ {{ sessionStore.userInfo?.nickname || 'UNKNOWN' }} ]</span>
          <button @click="handleLogout" class="logout-btn">로그아웃</button>
        </div>
      </header>

      <div v-if="isAdmin && isAreaSelected" class="admin-panel">
        <button @click="showAdminModal = true" class="admin-generate-btn">
          [ ⚠️ 지휘부 권한: 신규 구역 AI 스캔 및 작전 수립 ]
        </button>
      </div>

      <div v-if="showAdminModal" class="admin-modal-overlay">
        <div class="admin-modal-content">
          <h3>🤖 AI 자동 작전 수립 시스템</h3>
          <p>TourAPI와 Gemini를 가동하여 주변 명소 기반 스토리를 생성합니다.</p>

          <div class="input-group">
            <label>기준 위도 (Latitude)</label>
            <input type="number" step="0.000001" v-model="adminForm.lat" />
          </div>
          <div class="input-group">
            <label>기준 경도 (Longitude)</label>
            <input type="number" step="0.000001" v-model="adminForm.lng" />
          </div>

          <button @click="fetchCandidates" class="execute-btn" :disabled="isScanning || isGenerating">
            {{ isScanning ? '주변 반경 스캔 중...' : '1단계: 주변 명소 스캔 (TourAPI)' }}
          </button>

          <div v-if="candidates.length > 0" class="candidate-list">
            <label style="display: block; font-size: 0.85rem; color: #aaa; margin-top:15px; margin-bottom: 5px;">작전 목표 장소를 선택하십시오</label>
            <div class="candidate-scroll-area">
              <div v-for="spot in candidates" :key="spot.title"
                   class="spot-item"
                   :class="{ 'spot-selected': selectedSpot === spot }"
                   @click="selectedSpot = spot">
                <strong>{{ spot.title }}</strong>
                <span>{{ spot.address }}</span>
              </div>
            </div>
          </div>

          <button v-if="selectedSpot" @click="generateMissionByAi" class="execute-btn" :disabled="isGenerating" style="margin-top: 15px; background: #00ffcc; color: #000;">
            {{ isGenerating ? 'AI가 스토리를 작성 중입니다 (약 5~10초)...' : '2단계: [' + selectedSpot.title + '] 작전 수립' }}
          </button>

          <button @click="closeAdminModal" class="close-btn" :disabled="isGenerating" style="margin-top: 10px;">취소</button>
        </div>
      </div>

      <main v-if="!isAreaSelected" class="area-selector">
        <section class="map-panel">
          <div class="map-shell">
            <svg class="korea-map" :viewBox="`0 0 ${MAP_VIEW.width} ${MAP_VIEW.height}`" role="img" aria-label="대한민국 작전 지도">
              <defs>
                <filter id="map-glow" x="-40%" y="-40%" width="180%" height="180%">
                  <feGaussianBlur stdDeviation="4" result="blur" />
                  <feMerge>
                    <feMergeNode in="blur" />
                    <feMergeNode in="SourceGraphic" />
                  </feMerge>
                </filter>
                <radialGradient id="seoul-signal" cx="50%" cy="50%" r="50%">
                  <stop offset="0%" stop-color="#ff6b6b" stop-opacity="1" />
                  <stop offset="55%" stop-color="#ef4444" stop-opacity="0.7" />
                  <stop offset="100%" stop-color="#ef4444" stop-opacity="0" />
                </radialGradient>
                <clipPath id="south-korea-clip">
                  <polygon :points="projectPolygon(koreaOutline)" />
                </clipPath>
              </defs>

              <polygon class="nation-base" :points="projectPolygon(koreaOutline)" />

              <g clip-path="url(#south-korea-clip)">
                <g
                  v-for="area in areaCatalog"
                  :key="area.code"
                  class="map-region"
                  :class="{ selected: pendingAreaCode === area.code, disabled: !area.enabled }"
                  tabindex="0"
                  role="button"
                  :aria-label="area.name"
                  :aria-disabled="!area.enabled"
                  @click="openAreaConfirm(area.code)"
                  @keyup.enter="openAreaConfirm(area.code)"
                >
                  <polygon class="sector-fill" :points="projectPolygon(area.points)" />
                </g>
              </g>

              <g
                class="seoul-hotspot"
                :class="{ selected: pendingAreaCode === 'seoul' }"
                @click="openAreaConfirm('seoul')"
              >
                <circle class="signal-ring" :cx="projectPoint(seoulPoint).x" :cy="projectPoint(seoulPoint).y" r="34" />
                <circle class="region-core" :cx="projectPoint(seoulPoint).x" :cy="projectPoint(seoulPoint).y" r="8" />
              </g>

              <polygon class="jeju-outline" :points="projectPolygon(jejuOutline)" />
              <polyline class="nation-outline" :points="projectPolygon(koreaOutlineLoop)" />
              <polyline class="nation-inner-line" :points="projectPolygon(dmzLine)" />

              <g v-for="area in areaCatalog" :key="`${area.code}-label`">
                <text class="sector-label" :x="projectPoint(area.center).x" :y="projectPoint(area.center).y">
                  {{ area.mapLabel }}
                </text>
              </g>

              <g class="gps-marker" :class="{ fallback: userPosition.isFallback }">
                <circle class="gps-pulse" :cx="userMapPoint.x" :cy="userMapPoint.y" r="18" />
                <circle class="gps-dot" :cx="userMapPoint.x" :cy="userMapPoint.y" r="5" />
                <text class="gps-label" :x="userMapPoint.x + 12" :y="userMapPoint.y - 10">
                  {{ userPosition.isFallback ? 'SEOUL DEFAULT' : 'USER GPS' }}
                </text>
              </g>
            </svg>
          </div>
        </section>

        <section class="area-intel-panel">
          <p class="eyebrow">REGION NETWORK</p>
          <h2>대한민국 작전망</h2>
          <div class="area-choice-list">
            <button
              v-for="area in areaCatalog"
              :key="area.code"
              class="area-choice"
              :class="{ selected: pendingAreaCode === area.code, disabled: !area.enabled }"
              :aria-disabled="!area.enabled"
              @click="openAreaConfirm(area.code)"
            >
              <span>{{ area.name }}</span>
              <strong>{{ area.status }}</strong>
            </button>
          </div>
        </section>
      </main>

      <main v-else class="mission-grid">
        <div
            v-for="mission in missions"
            :key="mission.id"
            class="glass-card"
            :class="{ 'analyzing': !mission.isReady, 'cleared-card': mission.isCleared }"
            @click="handleMissionClick(mission)"
        >
          <div class="card-header">
            <div style="display: flex; gap: 8px;">
              <span v-if="mission.isReady" :class="['status-badge', mission.status.toLowerCase()]">
                {{ mission.status === 'ACTIVE' ? '진행 가능' : mission.status === 'LOCKED' ? '해금 필요' : '사건 해결' }}
              </span>
              <span v-else class="status-badge analyzing-badge">데이터 분석 중</span>

              <span :class="['diff-badge', mission.difficulty.toLowerCase()]">
                난이도: {{ mission.difficulty }}
              </span>
            </div>

            <button v-if="isAdmin" @click.stop="deleteRegion(mission.id, mission.title)" class="delete-btn" title="작전 파기">
              ✖
            </button>
          </div>

          <div v-if="mission.isCleared" class="clear-stamp" aria-label="해결한 작전">
            <span>CLEARED</span>
            <strong>{{ mission.answerKeyword || '사건 해결' }}</strong>
          </div>

          <h2 class="mission-title">{{ mission.title }}</h2>
          <p class="mission-desc" v-html="mission.description"></p>

          <div v-if="mission.isCleared" class="clear-summary">
            <div class="clear-metric">
              <span>점수</span>
              <strong>{{ mission.score || '-' }}</strong>
            </div>
            <div class="clear-metric">
              <span>시간</span>
              <strong>{{ formatElapsed(mission.elapsedSeconds) }}</strong>
            </div>
            <div class="clear-metric">
              <span>이동</span>
              <strong>{{ formatDistance(mission.routeDistanceMeters) }}</strong>
            </div>
          </div>

          <div class="card-footer">
            <span class="location-tag">📍 {{ mission.location }}</span>
            <span class="enter-text">{{ mission.isCleared ? '클리어 기록 보기 ➔' : mission.isReady ? '작전 브리핑 ➔' : '접근 제한' }}</span>
          </div>
        </div>
      </main>

      <div v-if="pendingArea" class="area-confirm-overlay">
        <section class="area-confirm-dialog">
          <p class="eyebrow">REGION CONFIRM</p>
          <h2>{{ pendingArea.enabled ? `${pendingArea.name}을 선택하시겠습니까?` : `${pendingArea.name} 작전망은 준비 중입니다` }}</h2>
          <div class="confirm-actions" :class="{ single: !pendingArea.enabled }">
            <button v-if="pendingArea.enabled" class="confirm-primary" @click="confirmAreaSelection">진입</button>
            <button class="confirm-secondary" @click="cancelAreaSelection">취소</button>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useSessionStore } from '@/stores/sessionStore';
import apiClient from '@/api/axiosInstance';

const route = useRoute();
const router = useRouter();
const sessionStore = useSessionStore();

const missions = ref([]);
const pendingAreaCode = ref(null);
const MAP_VIEW = { width: 420, height: 620, padding: 28 };
const MAP_BOUNDS = { minLng: 124.7, maxLng: 130.2, minLat: 33.0, maxLat: 38.75 };
const DEFAULT_USER_POSITION = { lng: 126.9780, lat: 37.5665, isFallback: true };
const userPosition = ref({ ...DEFAULT_USER_POSITION });
const seoulPoint = [126.9780, 37.5665];

const koreaOutline = [
  [126.1, 38.55], [126.8, 38.32], [127.7, 38.35], [128.55, 38.58], [129.12, 38.23],
  [129.45, 37.52], [129.35, 36.72], [129.45, 35.95], [129.25, 35.22], [128.9, 34.78],
  [128.16, 34.46], [127.35, 34.43], [126.55, 34.28], [125.82, 34.58], [126.05, 35.22],
  [126.02, 35.78], [125.74, 36.32], [126.18, 36.86], [126.04, 37.46]
];
const koreaOutlineLoop = [...koreaOutline, koreaOutline[0]];

const jejuOutline = [
  [126.10, 33.36], [126.32, 33.24], [126.66, 33.24], [126.92, 33.36],
  [126.82, 33.54], [126.48, 33.60], [126.18, 33.52]
];

const dmzLine = [
  [126.10, 38.12], [126.85, 38.02], [127.70, 38.14], [128.54, 38.32]
];

const areaCatalog = [
  {
    code: 'seoul',
    name: '서울',
    label: 'SEOUL',
    mapLabel: '서울',
    status: '작전 가능',
    enabled: true,
    center: [126.98, 37.52],
    points: [
      [126.02, 37.06], [126.16, 37.46], [126.10, 38.12], [126.85, 38.02],
      [127.70, 38.14], [127.88, 37.46], [127.34, 36.94], [126.55, 36.92]
    ]
  },
  {
    code: 'gangwon',
    name: '강원',
    label: 'GANGWON',
    mapLabel: '강원',
    status: '준비 중',
    enabled: false,
    center: [128.32, 37.54],
    points: [
      [127.70, 38.14], [128.54, 38.32], [129.12, 38.23], [129.45, 37.52],
      [129.28, 36.88], [128.68, 36.58], [127.88, 37.46]
    ]
  },
  {
    code: 'chungbuk',
    name: '충북',
    label: 'CHUNGBUK',
    mapLabel: '충북',
    status: '준비 중',
    enabled: false,
    center: [127.74, 36.42],
    points: [
      [126.55, 36.92], [127.34, 36.94], [127.88, 37.46], [128.68, 36.58],
      [128.36, 35.92], [127.54, 35.76], [126.94, 35.96]
    ]
  },
  {
    code: 'chungnam',
    name: '충남',
    label: 'CHUNGNAM',
    mapLabel: '충남',
    status: '준비 중',
    enabled: false,
    center: [126.44, 36.30],
    points: [
      [125.74, 36.32], [126.02, 35.78], [126.28, 35.54], [126.94, 35.96],
      [126.55, 36.92], [126.18, 36.86]
    ]
  },
  {
    code: 'jeonbuk',
    name: '전북',
    label: 'JEONBUK',
    mapLabel: '전북',
    status: '준비 중',
    enabled: false,
    center: [126.92, 35.48],
    points: [
      [126.02, 35.78], [126.28, 35.54], [126.94, 35.96], [127.54, 35.76],
      [127.70, 35.28], [127.14, 35.00], [126.34, 35.12], [126.05, 35.22]
    ]
  },
  {
    code: 'jeonnam',
    name: '전남',
    label: 'JEONNAM',
    mapLabel: '전남',
    status: '준비 중',
    enabled: false,
    center: [126.58, 34.72],
    points: [
      [125.82, 34.58], [126.55, 34.28], [127.35, 34.43], [127.70, 35.28],
      [127.14, 35.00], [126.34, 35.12], [126.05, 35.22]
    ]
  },
  {
    code: 'gyeongbuk',
    name: '경북',
    label: 'GYEONGBUK',
    mapLabel: '경북',
    status: '준비 중',
    enabled: false,
    center: [128.62, 36.18],
    points: [
      [127.54, 35.76], [128.36, 35.92], [128.68, 36.58], [129.28, 36.88],
      [129.45, 35.95], [129.25, 35.22], [128.48, 35.06], [127.92, 35.26]
    ]
  },
  {
    code: 'gyeongnam',
    name: '경남',
    label: 'GYEONGNAM',
    mapLabel: '경남',
    status: '준비 중',
    enabled: false,
    center: [128.22, 34.86],
    points: [
      [127.14, 35.00], [127.70, 35.28], [127.92, 35.26], [128.48, 35.06],
      [129.25, 35.22], [128.90, 34.78], [128.16, 34.46], [127.35, 34.43]
    ]
  }
];

const projectPoint = ([lng, lat]) => {
  const usableWidth = MAP_VIEW.width - (MAP_VIEW.padding * 2);
  const usableHeight = MAP_VIEW.height - (MAP_VIEW.padding * 2);
  const x = MAP_VIEW.padding + ((lng - MAP_BOUNDS.minLng) / (MAP_BOUNDS.maxLng - MAP_BOUNDS.minLng)) * usableWidth;
  const y = MAP_VIEW.padding + ((MAP_BOUNDS.maxLat - lat) / (MAP_BOUNDS.maxLat - MAP_BOUNDS.minLat)) * usableHeight;

  return {
    x: Number(x.toFixed(1)),
    y: Number(y.toFixed(1))
  };
};

const projectPolygon = (coordinates) => {
  return coordinates.map((coordinate) => {
    const point = projectPoint(coordinate);
    return `${point.x},${point.y}`;
  }).join(' ');
};

const userMapPoint = computed(() => projectPoint([userPosition.value.lng, userPosition.value.lat]));

const selectedAreaCode = computed(() => {
  return typeof route.query.area === 'string' ? route.query.area : '';
});

const activeArea = computed(() => {
  return areaCatalog.find(area => area.enabled && area.code === selectedAreaCode.value) || null;
});

const isAreaSelected = computed(() => Boolean(activeArea.value));

const pendingArea = computed(() => {
  return areaCatalog.find(area => area.code === pendingAreaCode.value) || null;
});

const isAdmin = computed(() => {
  const user = sessionStore.userInfo;
  return user?.isAdmin === true;
});

const showAdminModal = ref(false);
const isGenerating = ref(false);
const isScanning = ref(false);

const adminForm = ref({ lat: 37.5658, lng: 126.9751 });

const candidates = ref([]);
const selectedSpot = ref(null);

const closeAdminModal = () => {
  showAdminModal.value = false;
  candidates.value = [];
  selectedSpot.value = null;
};

const fetchCandidates = async () => {
  isScanning.value = true;
  candidates.value = [];
  selectedSpot.value = null;

  try {
    const response = await apiClient.get('/v1/admin/missions/candidates', {
      params: { lat: adminForm.value.lat, lng: adminForm.value.lng }
    });
    candidates.value = response.data;
  } catch (error) {
    console.error(error);
    alert(error.userMessage || "스캔 실패: 주변에 가용한 역사적 장소가 없거나 서버 오류입니다.");
  } finally {
    isScanning.value = false;
  }
};

const generateMissionByAi = async () => {
  if (!selectedSpot.value) return;

  isGenerating.value = true;
  try {
    // 🚨 팩트체크: AI가 경유지를 짤 수 있도록 '최종 목적지'와 '후보지 리스트'를 모두 보냅니다!
    const response = await apiClient.post('/v1/admin/missions/generate-selected', {
      targetSpot: selectedSpot.value,
      candidateSpots: candidates.value
    });

    alert(`[SYSTEM] ${response.data}`);

    closeAdminModal();
    fetchMissions();

  } catch (error) {
    console.error(error);
    alert(error.userMessage || '작전 수립에 실패했습니다. 백엔드 로그를 확인하세요.');
  } finally {
    isGenerating.value = false;
  }
};

// 💡 추가된 삭제 함수
const deleteRegion = async (regionId, title) => {
  if (!confirm(`[경고] '${title}' 작전을 데이터베이스에서 영구 파기하시겠습니까?`)) return;

  try {
    await apiClient.delete(`/v1/admin/missions/regions/${regionId}`);
    alert('[SYSTEM] 작전이 안전하게 파기되었습니다.');
    fetchMissions(); // 카드 목록 갱신
  } catch (error) {
    console.error(error);
    alert(error.userMessage || '작전 파기 통신 실패. 본부에 문의하십시오.');
  }
};

const fetchMissions = async () => {
  try {
    const response = await apiClient.get('/v1/regions/cards', {
      params: { userId: sessionStore.userId || 1 }
    });
    missions.value = response.data.map(region => ({
      id: region.id,
      title: region.name,
      description: region.description,
      difficulty: 'NORMAL',
      location: region.cleared ? '클리어 기록 보관함' : '현장 작전 구역',
      status: region.cleared ? 'CLEARED' : 'ACTIVE',
      isCleared: region.cleared === true,
      finalMissionId: region.finalMissionId,
      answerKeyword: region.answerKeyword,
      score: region.score,
      elapsedSeconds: region.elapsedSeconds,
      routeDistanceMeters: region.routeDistanceMeters,
      isReady: true
    }));
  } catch (error) {
    console.error('[시스템 오류] 데이터 동기화 실패. 예비 서버로 전환합니다.', error);
    missions.value = [
      { id: 1, title: '중명전의 비밀', description: 'DB 연결 확인 중...', difficulty: 'NORMAL', location: '서울 정동길', status: 'ACTIVE', isCleared: false, isReady: true }
    ];
  }
};

const formatElapsed = (seconds) => {
  if (seconds === null || seconds === undefined || seconds === '') {
    return '-';
  }
  const safeSeconds = Math.max(0, Number(seconds) || 0);
  const minutes = Math.floor(safeSeconds / 60);
  const remainingSeconds = safeSeconds % 60;
  return `${minutes}m ${String(remainingSeconds).padStart(2, '0')}s`;
};

const formatDistance = (meters) => {
  if (meters === null || meters === undefined || meters === '') {
    return '-';
  }
  const safeMeters = Math.max(0, Number(meters) || 0);
  if (safeMeters >= 1000) {
    return `${(safeMeters / 1000).toFixed(2)}km`;
  }
  return `${Math.round(safeMeters)}m`;
};

watch(isAreaSelected, (selected) => {
  if (selected) {
    fetchMissions();
    return;
  }

  missions.value = [];
}, { immediate: true });

onMounted(() => {
  locateUser();
});

const locateUser = () => {
  if (!navigator.geolocation) {
    userPosition.value = { ...DEFAULT_USER_POSITION };
    return;
  }

  navigator.geolocation.getCurrentPosition(
    ({ coords }) => {
      const nextPosition = {
        lng: coords.longitude,
        lat: coords.latitude,
        isFallback: false
      };

      userPosition.value = isCoordinateInsideMap(nextPosition)
        ? nextPosition
        : { ...DEFAULT_USER_POSITION };
    },
    () => {
      userPosition.value = { ...DEFAULT_USER_POSITION };
    },
    {
      enableHighAccuracy: true,
      timeout: 5000,
      maximumAge: 300000
    }
  );
};

const isCoordinateInsideMap = ({ lng, lat }) => {
  return lng >= MAP_BOUNDS.minLng
    && lng <= MAP_BOUNDS.maxLng
    && lat >= MAP_BOUNDS.minLat
    && lat <= MAP_BOUNDS.maxLat;
};

const openAreaConfirm = (areaCode) => {
  const area = areaCatalog.find(item => item.code === areaCode);
  if (!area) return;

  pendingAreaCode.value = area.code;
};

const confirmAreaSelection = () => {
  if (!pendingArea.value || !pendingArea.value.enabled) return;

  const areaCode = pendingArea.value.code;
  pendingAreaCode.value = null;
  router.push({ name: 'Home', query: { area: areaCode } });
};

const cancelAreaSelection = () => {
  pendingAreaCode.value = null;
};

const returnToAreaSelection = () => {
  pendingAreaCode.value = null;
  showAdminModal.value = false;
  router.push({ name: 'Home' });
};

const handleMissionClick = (mission) => {
  if (!mission.isReady) {
    alert(`[접근 거부] 분석 중인 섹터입니다.`);
    return;
  }

  if (mission.isCleared && mission.finalMissionId) {
    router.push({
      name: 'Clear',
      params: { missionId: mission.finalMissionId },
      query: { regionId: mission.id }
    });
    return;
  }

  // BriefingView가 알아들을 수 있도록 regionId 로 이름을 변경
  router.push({ name: 'Briefing', query: { regionId: mission.id } });
};

const handleLogout = () => {
  sessionStore.logout();
  router.push({ name: 'Intro' });
};
</script>

<style scoped>
/* 🚨 요원님의 멋진 글래스모피즘 스타일 원본 그대로 유지 */
@import url('https://fonts.googleapis.com/css2?family=Noto+Sans+KR:wght@400;500;700&display=swap');

.dashboard-container {
  min-height: 100vh;
  background-color: #0b0f19;
  font-family: 'Noto Sans KR', sans-serif;
  color: #e2e8f0;
  position: relative;
  overflow-x: hidden;
  padding: 40px 20px;
}
.dashboard-container.area-mode {
  height: 100svh;
  overflow: hidden;
  padding: clamp(18px, 3vh, 30px) 20px;
}

.bg-glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(100px);
  z-index: 0;
  opacity: 0.5;
}
.blob-1 { width: 400px; height: 400px; background: #06b6d4; top: -100px; left: -100px; }
.blob-2 { width: 500px; height: 500px; background: #3b82f6; bottom: -150px; right: -100px; }

.content-wrapper { position: relative; z-index: 1; max-width: 1000px; margin: 0 auto; }
.area-mode .content-wrapper { height: 100%; display: flex; flex-direction: column; }
.dashboard-header { display: flex; justify-content: space-between; align-items: flex-end; margin-bottom: 40px; border-bottom: 1px solid rgba(255, 255, 255, 0.1); padding-bottom: 20px; }
.area-mode .dashboard-header { flex: 0 0 auto; margin-bottom: clamp(12px, 2vh, 20px); padding-bottom: 14px; }
.title { font-size: 2rem; font-weight: 700; margin: 0 0 5px 0; color: #fff; }
.area-mode .title { font-size: clamp(1.55rem, 3.2vw, 2rem); }
.highlight { color: #06b6d4; }
.subtitle { font-size: 0.9rem; color: #94a3b8; margin: 0; }
.user-panel { display: flex; align-items: center; gap: 20px; }
.agent-name { color: #06b6d4; font-weight: 500; font-size: 0.9rem; }
.region-back-btn { background: rgba(6, 182, 212, 0.12); border: 1px solid rgba(6, 182, 212, 0.45); color: #67e8f9; padding: 6px 12px; border-radius: 6px; cursor: pointer; font-family: inherit; font-weight: 700; transition: 0.3s; }
.region-back-btn:hover { background: rgba(6, 182, 212, 0.22); color: #fff; }
.logout-btn { background: rgba(239, 68, 68, 0.1); border: 1px solid #ef4444; color: #ef4444; padding: 6px 12px; border-radius: 6px; cursor: pointer; transition: 0.3s; }
.logout-btn:hover { background: #ef4444; color: #fff; }
.area-selector { display: grid; grid-template-columns: minmax(0, 1.15fr) minmax(280px, 0.85fr); gap: 32px; align-items: center; min-height: 520px; }
.area-mode .area-selector { flex: 1 1 auto; min-height: 0; height: 100%; gap: clamp(18px, 3vw, 32px); }
.map-panel, .area-intel-panel { min-width: 0; }
.map-shell { position: relative; width: min(100%, 520px); aspect-ratio: 4 / 5; margin: 0 auto; display: flex; align-items: center; justify-content: center; overflow: hidden; border: 1px solid rgba(6, 182, 212, 0.28); border-radius: 8px; background: radial-gradient(circle at 50% 35%, rgba(6, 182, 212, 0.16), rgba(15, 23, 42, 0.16) 38%, rgba(2, 6, 23, 0.48) 100%); box-shadow: inset 0 0 36px rgba(6, 182, 212, 0.1), 0 20px 60px rgba(0, 0, 0, 0.32); }
.area-mode .map-shell { width: min(100%, 430px, 58svh); max-height: 100%; }
.map-shell::before { content: ""; position: absolute; inset: 0; background-image: linear-gradient(rgba(148, 163, 184, 0.08) 1px, transparent 1px), linear-gradient(90deg, rgba(148, 163, 184, 0.08) 1px, transparent 1px); background-size: 32px 32px; mask-image: radial-gradient(circle at center, black 30%, transparent 72%); pointer-events: none; }
.korea-map { position: relative; z-index: 1; width: min(88%, 390px); height: 94%; overflow: visible; }
.nation-base { fill: rgba(8, 47, 73, 0.24); stroke: none; }
.nation-outline { fill: none; stroke: #22d3ee; stroke-width: 3; filter: url(#map-glow); }
.jeju-outline { fill: rgba(8, 47, 73, 0.36); stroke: #22d3ee; stroke-width: 2.3; filter: url(#map-glow); }
.nation-inner-line { fill: none; stroke: rgba(103, 232, 249, 0.35); stroke-width: 1.6; stroke-dasharray: 7 8; }
.map-region { cursor: pointer; outline: none; }
.map-region.disabled { cursor: pointer; }
.sector-fill { fill: rgba(6, 182, 212, 0.18); stroke: rgba(103, 232, 249, 0.58); stroke-width: 1.4; transition: fill 0.25s ease, stroke 0.25s ease, filter 0.25s ease; }
.map-region.disabled .sector-fill { fill: rgba(15, 23, 42, 0.14); stroke: rgba(148, 163, 184, 0.34); }
.map-region:hover .sector-fill,
.map-region:focus .sector-fill,
.map-region.selected .sector-fill { fill: rgba(239, 68, 68, 0.62); stroke: #fecaca; filter: drop-shadow(0 0 10px rgba(239, 68, 68, 0.65)); }
.signal-ring { fill: url(#seoul-signal); opacity: 0.28; transition: opacity 0.25s ease; }
.region-core { fill: #ecfeff; transition: fill 0.25s ease; }
.seoul-hotspot { cursor: pointer; }
.seoul-hotspot:hover .signal-ring, .seoul-hotspot.selected .signal-ring { opacity: 0.78; }
.seoul-hotspot:hover .region-core, .seoul-hotspot.selected .region-core { fill: #fee2e2; }
.sector-label { fill: #cffafe; font-size: 12px; font-weight: 800; letter-spacing: 0; text-anchor: middle; dominant-baseline: middle; paint-order: stroke; stroke: rgba(2, 6, 23, 0.84); stroke-width: 4; pointer-events: none; }
.gps-marker { pointer-events: none; }
.gps-pulse { fill: rgba(34, 211, 238, 0.24); stroke: rgba(34, 211, 238, 0.82); stroke-width: 1.4; }
.gps-dot { fill: #67e8f9; stroke: #ecfeff; stroke-width: 2; filter: drop-shadow(0 0 9px rgba(34, 211, 238, 0.95)); }
.gps-label { fill: #e0f2fe; font-size: 11px; font-weight: 800; letter-spacing: 0; paint-order: stroke; stroke: rgba(2, 6, 23, 0.88); stroke-width: 4; }
.gps-marker.fallback .gps-pulse { fill: rgba(245, 158, 11, 0.2); stroke: rgba(245, 158, 11, 0.82); }
.gps-marker.fallback .gps-dot { fill: #fbbf24; filter: drop-shadow(0 0 9px rgba(245, 158, 11, 0.9)); }
.area-intel-panel { padding: 8px 0; }
.area-mode .area-intel-panel { align-self: center; }
.eyebrow { margin: 0 0 10px; color: #67e8f9; font-size: 0.74rem; font-weight: 800; letter-spacing: 0; }
.area-intel-panel h2 { margin: 0 0 22px; color: #fff; font-size: 1.75rem; line-height: 1.2; }
.area-mode .area-intel-panel h2 { margin-bottom: 16px; }
.area-choice-list { display: grid; gap: 12px; }
.area-mode .area-choice-list { gap: 9px; }
.area-choice { width: 100%; display: flex; justify-content: space-between; align-items: center; gap: 12px; padding: 16px 18px; border: 1px solid rgba(148, 163, 184, 0.2); border-radius: 8px; background: rgba(15, 23, 42, 0.56); color: #e2e8f0; font-family: inherit; cursor: pointer; transition: border-color 0.25s ease, background 0.25s ease, transform 0.25s ease; }
.area-mode .area-choice { padding: 12px 14px; }
.area-choice span { min-width: 0; font-size: 1rem; font-weight: 800; }
.area-choice strong { flex: 0 0 auto; color: #67e8f9; font-size: 0.78rem; }
.area-choice:hover, .area-choice.selected { border-color: rgba(239, 68, 68, 0.78); background: rgba(127, 29, 29, 0.32); transform: translateX(4px); }
.area-choice.disabled { cursor: pointer; opacity: 0.45; }
.area-choice.disabled strong { color: #94a3b8; }
.area-choice.disabled:hover, .area-choice.disabled.selected { transform: translateX(4px); border-color: rgba(239, 68, 68, 0.78); background: rgba(127, 29, 29, 0.32); }
.area-confirm-overlay { position: fixed; inset: 0; z-index: 9000; display: flex; align-items: center; justify-content: center; padding: 20px; background: rgba(2, 6, 23, 0.74); backdrop-filter: blur(10px); }
.area-confirm-dialog { width: min(100%, 380px); padding: 26px; border: 1px solid rgba(239, 68, 68, 0.62); border-radius: 8px; background: rgba(15, 23, 42, 0.96); box-shadow: 0 0 28px rgba(239, 68, 68, 0.18); }
.area-confirm-dialog h2 { margin: 0 0 22px; color: #fff; font-size: 1.3rem; line-height: 1.35; }
.confirm-actions { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.confirm-actions.single { grid-template-columns: 1fr; }
.confirm-primary, .confirm-secondary { padding: 11px 14px; border-radius: 6px; font-family: inherit; font-weight: 800; cursor: pointer; }
.confirm-primary { border: 1px solid #ef4444; background: #ef4444; color: #fff; }
.confirm-secondary { border: 1px solid rgba(148, 163, 184, 0.35); background: transparent; color: #cbd5e1; }
.mission-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(min(100%, 300px), 1fr)); column-gap: 25px; row-gap: 32px; align-items: stretch; }
.glass-card { position: relative; overflow: hidden; box-sizing: border-box; width: 100%; min-width: 0; min-height: 260px; background: rgba(255, 255, 255, 0.03); backdrop-filter: blur(12px); -webkit-backdrop-filter: blur(12px); border: 1px solid rgba(255, 255, 255, 0.08); border-radius: 16px; padding: 24px; cursor: pointer; transition: transform 0.3s ease, border-color 0.3s ease, box-shadow 0.3s ease; display: flex; flex-direction: column; }
.glass-card:hover { transform: translateY(-5px); border-color: rgba(6, 182, 212, 0.5); box-shadow: 0 10px 30px -10px rgba(0, 0, 0, 0.5), 0 0 15px rgba(6, 182, 212, 0.2); background: rgba(255, 255, 255, 0.05); }
.glass-card.analyzing { opacity: 0.6; cursor: not-allowed; }
.glass-card.cleared-card { border-color: rgba(245, 158, 11, 0.45); background: linear-gradient(160deg, rgba(245, 158, 11, 0.11), rgba(6, 182, 212, 0.05) 48%, rgba(255, 255, 255, 0.03)); }
.glass-card.cleared-card:hover { border-color: rgba(245, 158, 11, 0.75); box-shadow: 0 10px 30px -10px rgba(0, 0, 0, 0.55), 0 0 18px rgba(245, 158, 11, 0.22); }

/* 💡 카드 헤더 레이아웃 조정 (삭제 버튼과 균형) */
.card-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 15px; }
.status-badge, .diff-badge { font-size: 0.75rem; font-weight: 700; padding: 4px 10px; border-radius: 20px; }
.active { background: rgba(16, 185, 129, 0.2); color: #10b981; border: 1px solid rgba(16, 185, 129, 0.3); }
.locked { background: rgba(239, 68, 68, 0.2); color: #ef4444; border: 1px solid rgba(239, 68, 68, 0.3); }
.cleared { background: rgba(59, 130, 246, 0.2); color: #3b82f6; border: 1px solid rgba(59, 130, 246, 0.3); }
.analyzing-badge { background: rgba(148, 163, 184, 0.2); color: #94a3b8; border: 1px solid rgba(148, 163, 184, 0.3); }
.easy { color: #10b981; }
.normal { color: #f59e0b; }
.hard { color: #ef4444; }

/* 💡 삭제 버튼용 신규 스타일 (테마 호환) */
.delete-btn {
  background: transparent;
  border: none;
  color: rgba(239, 68, 68, 0.6);
  font-size: 1.1rem;
  cursor: pointer;
  transition: all 0.3s ease;
  padding: 0;
  line-height: 1;
}
.delete-btn:hover {
  color: #ef4444;
  transform: scale(1.2) rotate(90deg);
  text-shadow: 0 0 10px rgba(239, 68, 68, 0.8);
}

.mission-title { font-size: 1.25rem; font-weight: 700; color: #fff; margin: 0 0 10px 0; }
.mission-desc { font-size: 0.85rem; color: #94a3b8; line-height: 1.5; margin: 0 0 20px 0; flex-grow: 1; }
.clear-stamp {
  align-self: flex-end;
  max-width: 180px;
  margin: -8px 0 14px;
  padding: 7px 12px;
  border: 2px solid rgba(245, 158, 11, 0.85);
  border-radius: 6px;
  color: #fbbf24;
  text-align: center;
  text-transform: uppercase;
  transform: rotate(3deg);
  background: rgba(15, 23, 42, 0.8);
  box-shadow: 0 0 18px rgba(245, 158, 11, 0.14);
}
.clear-stamp span {
  display: block;
  font-size: 0.68rem;
  font-weight: 800;
  letter-spacing: 0;
}
.clear-stamp strong {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 0.92rem;
  line-height: 1.3;
}
.clear-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin: 0 0 18px;
}
.clear-metric {
  min-width: 0;
  padding: 9px 8px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 6px;
  background: rgba(2, 6, 23, 0.28);
}
.clear-metric span {
  display: block;
  margin-bottom: 4px;
  color: #94a3b8;
  font-size: 0.68rem;
  font-weight: 700;
}
.clear-metric strong {
  display: block;
  overflow: hidden;
  color: #f8fafc;
  font-size: 0.86rem;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.card-footer { display: flex; justify-content: space-between; align-items: center; margin-top: auto; padding-top: 15px; border-top: 1px solid rgba(255, 255, 255, 0.05); }
.location-tag { font-size: 0.8rem; color: #cbd5e1; }
.enter-text { font-size: 0.8rem; color: #06b6d4; font-weight: 700; opacity: 0; transition: opacity 0.3s; }
.glass-card:hover .enter-text { opacity: 1; }

.admin-panel { text-align: center; margin-bottom: 20px; }
.admin-generate-btn { background: rgba(255, 68, 68, 0.1); color: #ff4444; border: 2px dashed #ff4444; padding: 12px 20px; font-size: 1rem; font-weight: bold; font-family: inherit; border-radius: 8px; cursor: pointer; transition: all 0.3s ease; }
.admin-generate-btn:hover { background: #ff4444; color: #fff; box-shadow: 0 0 15px #ff4444; }

.admin-modal-overlay { position: fixed; top: 0; left: 0; width: 100vw; height: 100vh; background: rgba(0, 0, 0, 0.85); display: flex; justify-content: center; align-items: center; z-index: 9999; }
.admin-modal-content { background: #111; border: 2px solid #ff4444; padding: 25px; border-radius: 12px; width: 90%; max-width: 450px; color: #fff; }
.admin-modal-content h3 { color: #ff4444; margin-top: 0; border-bottom: 1px solid #ff4444; padding-bottom: 10px;}
.input-group { margin-bottom: 15px; text-align: left; }
.input-group label { display: block; font-size: 0.85rem; color: #aaa; margin-bottom: 5px; }
.input-group input { width: 100%; padding: 8px; background: #222; border: 1px solid #555; color: #00ffcc; font-family: inherit; border-radius: 4px; box-sizing: border-box; }
.execute-btn { width: 100%; padding: 12px; background: #ff4444; color: #fff; border: none; font-weight: bold; font-family: inherit; border-radius: 6px; cursor: pointer; }
.execute-btn:disabled { background: #555; color: #888; cursor: not-allowed; }
.close-btn { width: 100%; padding: 12px; background: transparent; border: 1px solid #aaa; color: #aaa; font-family: inherit; border-radius: 6px; cursor: pointer; }

.candidate-scroll-area { max-height: 150px; overflow-y: auto; background: #1a1a1a; border: 1px solid #333; border-radius: 4px; }
.spot-item { padding: 10px; border-bottom: 1px solid #333; cursor: pointer; transition: background 0.2s; }
.spot-item:last-child { border-bottom: none; }
.spot-item:hover { background: #2a2a2a; }
.spot-selected { background: rgba(0, 255, 204, 0.15) !important; border-left: 3px solid #00ffcc; }
.spot-item strong { display: block; color: #eee; font-size: 0.9rem; margin-bottom: 3px; }
.spot-item span { display: block; color: #777; font-size: 0.75rem; }

@media (max-width: 760px) {
  .dashboard-container { padding: 28px 14px; }
  .dashboard-container.area-mode { padding: 16px 14px; }
  .dashboard-header { align-items: flex-start; gap: 18px; flex-direction: column; }
  .area-mode .dashboard-header { gap: 10px; margin-bottom: 10px; padding-bottom: 10px; }
  .user-panel { width: 100%; flex-wrap: wrap; gap: 10px; }
  .agent-name { flex: 1 1 100%; }
  .area-selector { grid-template-columns: 1fr; min-height: auto; gap: 14px; }
  .area-mode .area-selector { grid-template-rows: minmax(0, 1fr) auto; }
  .map-shell { width: 100%; max-height: 470px; }
  .area-mode .map-shell { width: min(72vw, 280px); }
  .area-intel-panel h2 { font-size: 1.45rem; }
  .area-mode .area-intel-panel h2 { display: none; }
  .area-mode .eyebrow { margin-bottom: 8px; }
  .area-mode .area-choice-list { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; }
  .area-mode .area-choice { padding: 9px 10px; }
  .area-mode .area-choice span { font-size: 0.86rem; }
  .area-mode .area-choice strong { font-size: 0.68rem; }
  .confirm-actions { grid-template-columns: 1fr; }
}
</style>
