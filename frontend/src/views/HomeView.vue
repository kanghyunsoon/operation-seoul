<template>
  <div class="dashboard-container">
    <div class="bg-glow blob-1"></div>
    <div class="bg-glow blob-2"></div>

    <div class="content-wrapper">
      <header class="dashboard-header">
        <div class="title-group">
          <h1 class="title">OPERATION<span class="highlight">: SEOUL</span></h1>
          <p class="subtitle">작전 목록 데이터베이스 접근 중...</p>
        </div>
        <div class="user-panel">
          <span class="agent-name">요원 [ {{ sessionStore.userInfo?.nickname || 'UNKNOWN' }} ]</span>
          <button @click="handleLogout" class="logout-btn">로그아웃</button>
        </div>
      </header>

      <div v-if="isAdmin" class="admin-panel">
              <button @click="showAdminModal = true" class="admin-generate-btn">
                [ ⚠️ 지휘부 권한: 신규 구역 AI 스캔 및 작전 수립 ]
              </button>
            </div>

            <div v-if="showAdminModal" class="admin-modal-overlay">
              <div class="admin-modal-content">
                <h3>🤖 AI 자동 작전 수립 시스템</h3>
                <p>TourAPI와 Gemini를 가동하여 주변 명소 기반 스토리를 생성합니다.</p>

                <div class="input-group">
                  <label>지역 ID (Region ID)</label>
                  <input type="number" v-model="adminForm.regionId" />
                </div>
                <div class="input-group">
                  <label>기준 위도 (Latitude)</label>
                  <input type="number" step="0.000001" v-model="adminForm.lat" />
                </div>
                <div class="input-group">
                  <label>기준 경도 (Longitude)</label>
                  <input type="number" step="0.000001" v-model="adminForm.lng" />
                </div>

                <button @click="generateMissionByAi" class="execute-btn" :disabled="isGenerating">
                  {{ isGenerating ? 'AI가 스토리를 작성 중입니다 (약 5~10초)...' : '스캔 및 미션 생성 실행' }}
                </button>
                <button @click="showAdminModal = false" class="close-btn" :disabled="isGenerating">취소</button>
              </div>
            </div>

      <main class="mission-grid">
        <div
            v-for="mission in missions"
            :key="mission.id"
            class="glass-card"
            :class="{ 'analyzing': !mission.isReady }"
            @click="handleMissionClick(mission)"
        >
          <div class="card-header">
            <span v-if="mission.isReady" :class="['status-badge', mission.status.toLowerCase()]">
              {{ mission.status === 'ACTIVE' ? '진행 가능' : mission.status === 'LOCKED' ? '해금 필요' : '작전 완료' }}
            </span>
            <span v-else class="status-badge analyzing-badge">데이터 분석 중</span>

            <span :class="['diff-badge', mission.difficulty.toLowerCase()]">
              난이도: {{ mission.difficulty }}
            </span>
          </div>

          <h2 class="mission-title">{{ mission.title }}</h2>

          <p class="mission-desc" v-html="mission.description"></p>

          <div class="card-footer">
            <span class="location-tag">📍 {{ mission.location }}</span>
            <span class="enter-text">{{ mission.isReady ? '작전 브리핑 ➔' : '접근 제한' }}</span>
          </div>
        </div>
      </main>
    </div>
  </div>
</template>
<script setup>
import { ref, onMounted, computed } from 'vue'; // 🚨 computed 추가됨!
import { useRouter } from 'vue-router';
import { useSessionStore } from '@/stores/sessionStore';
import apiClient from '@/api/axiosInstance';

const router = useRouter();
const sessionStore = useSessionStore();

// 💡 템플릿과 연동되는 반응형 작전 리스트
const missions = ref([]);

// 1. 관리자 권한 확인 (테스트용으로 닉네임이 admin이거나 이메일이 admin@seoul.go.kr 이면 관리자로 인식)
const isAdmin = computed(() => {
  const user = sessionStore.userInfo;
  return user && (user.nickname === 'admin' || user.email === 'admin@seoul.go.kr');
});

// 2. 모달 상태 및 폼 데이터 (기본값: 서울 시청/덕수궁 부근 좌표)
const showAdminModal = ref(false);
const isGenerating = ref(false);
const adminForm = ref({
  regionId: 1,
  lat: 37.5658,
  lng: 126.9751
});

// 3. AI 미션 생성 백엔드 API 호출
const generateMissionByAi = async () => {
  isGenerating.value = true;
  try {
    // 백엔드의 AdminMissionController 호출
    const response = await apiClient.post('/v1/admin/missions/generate', null, {
      params: {
        regionId: adminForm.value.regionId,
        lat: adminForm.value.lat,
        lng: adminForm.value.lng
      }
    });

    alert(`[SYSTEM] ${response.data}`);
    showAdminModal.value = false;

    // 미션 목록 새로고침
    fetchMissions();

  } catch (error) {
    console.error(error);
    alert('작전 수립에 실패했습니다. 백엔드 로그를 확인하세요.');
  } finally {
    isGenerating.value = false;
  }
};

/**
 * [함수: 실시간 작전 데이터 로드]
 */
const fetchMissions = async () => {
  try {
    const response = await apiClient.get('/v1/regions');

    missions.value = response.data.map(region => ({
      id: region.id,
      title: region.name,
      description: region.description,
      difficulty: 'NORMAL',
      location: '현장 작전 구역',
      status: 'ACTIVE',
      isReady: true
    }));
  } catch (error) {
    console.error('[시스템 오류] 데이터 동기화 실패. 예비 서버로 전환합니다.', error);
    missions.value = [
      { id: 1, title: '중명전의 비밀', description: 'DB 연결 확인 중...', difficulty: 'NORMAL', location: '서울 정동길', status: 'ACTIVE', isReady: true }
    ];
  }
};

onMounted(() => {
  fetchMissions();
});

const handleMissionClick = (mission) => {
  if (!mission.isReady) {
    alert(`[접근 거부] 분석 중인 섹터입니다.`);
    return;
  }
  router.push({ name: 'Briefing', query: { missionId: mission.id } });
};

const handleLogout = () => {
  sessionStore.logout();
  router.push({ name: 'Intro' });
};
</script>


<style scoped>
/* 🚨 요원님의 멋진 글래스모피즘 스타일 원본 그대로입니다. (수정 금지 명령 준수) */
@import url('https://fonts.googleapis.com/css2?family=Noto+Sans+KR:wght@400;500;700&display=swap');

.dashboard-container {
  min-height: 100vh;
  background-color: #0b0f19;
  font-family: 'Noto Sans KR', sans-serif;
  color: #e2e8f0;
  position: relative;
  overflow: hidden;
  padding: 40px 20px;
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
.dashboard-header { display: flex; justify-content: space-between; align-items: flex-end; margin-bottom: 40px; border-bottom: 1px solid rgba(255, 255, 255, 0.1); padding-bottom: 20px; }
.title { font-size: 2rem; font-weight: 700; margin: 0 0 5px 0; color: #fff; }
.highlight { color: #06b6d4; }
.subtitle { font-size: 0.9rem; color: #94a3b8; margin: 0; }
.user-panel { display: flex; align-items: center; gap: 20px; }
.agent-name { color: #06b6d4; font-weight: 500; font-size: 0.9rem; }
.logout-btn { background: rgba(239, 68, 68, 0.1); border: 1px solid #ef4444; color: #ef4444; padding: 6px 12px; border-radius: 6px; cursor: pointer; transition: 0.3s; }
.logout-btn:hover { background: #ef4444; color: #fff; }
.mission-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 25px; }
.glass-card { background: rgba(255, 255, 255, 0.03); backdrop-filter: blur(12px); -webkit-backdrop-filter: blur(12px); border: 1px solid rgba(255, 255, 255, 0.08); border-radius: 16px; padding: 24px; cursor: pointer; transition: transform 0.3s ease, border-color 0.3s ease, box-shadow 0.3s ease; display: flex; flex-direction: column; height: 100%; }
.glass-card:hover { transform: translateY(-5px); border-color: rgba(6, 182, 212, 0.5); box-shadow: 0 10px 30px -10px rgba(0, 0, 0, 0.5), 0 0 15px rgba(6, 182, 212, 0.2); background: rgba(255, 255, 255, 0.05); }
.glass-card.analyzing { opacity: 0.6; cursor: not-allowed; }
.card-header { display: flex; justify-content: space-between; margin-bottom: 15px; }
.status-badge, .diff-badge { font-size: 0.75rem; font-weight: 700; padding: 4px 10px; border-radius: 20px; }
.active { background: rgba(16, 185, 129, 0.2); color: #10b981; border: 1px solid rgba(16, 185, 129, 0.3); }
.locked { background: rgba(239, 68, 68, 0.2); color: #ef4444; border: 1px solid rgba(239, 68, 68, 0.3); }
.cleared { background: rgba(59, 130, 246, 0.2); color: #3b82f6; border: 1px solid rgba(59, 130, 246, 0.3); }
.analyzing-badge { background: rgba(148, 163, 184, 0.2); color: #94a3b8; border: 1px solid rgba(148, 163, 184, 0.3); }
.easy { color: #10b981; }
.normal { color: #f59e0b; }
.hard { color: #ef4444; }
.mission-title { font-size: 1.25rem; font-weight: 700; color: #fff; margin: 0 0 10px 0; }
.mission-desc { font-size: 0.85rem; color: #94a3b8; line-height: 1.5; margin: 0 0 20px 0; flex-grow: 1; }
.card-footer { display: flex; justify-content: space-between; align-items: center; margin-top: auto; padding-top: 15px; border-top: 1px solid rgba(255, 255, 255, 0.05); }
.location-tag { font-size: 0.8rem; color: #cbd5e1; }
.enter-text { font-size: 0.8rem; color: #06b6d4; font-weight: 700; opacity: 0; transition: opacity 0.3s; }
.glass-card:hover .enter-text { opacity: 1; }

/* 관리자 패널 및 버튼 스타일 */
.admin-panel {
  text-align: center;
  margin-bottom: 20px;
}
.admin-generate-btn {
  background: rgba(255, 68, 68, 0.1);
  color: #ff4444;
  border: 2px dashed #ff4444;
  padding: 12px 20px;
  font-size: 1rem;
  font-weight: bold;
  font-family: inherit;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s ease;
}
.admin-generate-btn:hover {
  background: #ff4444;
  color: #fff;
  box-shadow: 0 0 15px #ff4444;
}

/* 관리자 모달 스타일 */
.admin-modal-overlay {
  position: fixed; top: 0; left: 0; width: 100vw; height: 100vh;
  background: rgba(0, 0, 0, 0.85); display: flex; justify-content: center; align-items: center; z-index: 9999;
}
.admin-modal-content {
  background: #111; border: 2px solid #ff4444; padding: 25px;
  border-radius: 12px; width: 90%; max-width: 450px; color: #fff;
}
.admin-modal-content h3 { color: #ff4444; margin-top: 0; border-bottom: 1px solid #ff4444; padding-bottom: 10px;}
.input-group { margin-bottom: 15px; text-align: left; }
.input-group label { display: block; font-size: 0.85rem; color: #aaa; margin-bottom: 5px; }
.input-group input { width: 100%; padding: 8px; background: #222; border: 1px solid #555; color: #00ffcc; font-family: inherit; border-radius: 4px; box-sizing: border-box; }
.execute-btn { width: 100%; padding: 12px; background: #ff4444; color: #fff; border: none; font-weight: bold; font-family: inherit; border-radius: 6px; cursor: pointer; margin-bottom: 10px; }
.execute-btn:disabled { background: #555; color: #888; cursor: not-allowed; }
.close-btn { width: 100%; padding: 12px; background: transparent; border: 1px solid #aaa; color: #aaa; font-family: inherit; border-radius: 6px; cursor: pointer; }

</style>