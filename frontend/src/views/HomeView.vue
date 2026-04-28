<template>
  <div class="home-container">
    <div class="dashboard">
      <header class="header">
        <h1 class="welcome-text">HQ TERMINAL: ACTIVE</h1>
        <p v-if="userInfo" class="agent-info">AGENT: {{ userInfo.nickname }} [ONLINE]</p>
      </header>

      <section class="mission-section">
        <h2 class="section-title">> AVAILABLE OPERATIONS (작전 지역)</h2>

        <div v-if="loading" class="status-msg">지형 데이터를 분석 중입니다...</div>

        <div v-else-if="regions.length === 0" class="status-msg"> 현재 사용 가능한 작전 구역이 없습니다.</div>

        <div v-else class="region-list">
          <div v-for="region in regions" :key="region.id" class="region-card">
            <div class="region-header">
              <span class="region-id">#ID-0{{ region.id }}</span>
              <h3>{{ region.name }}</h3>
            </div>
            <p class="region-desc">{{ region.description }}</p>

            <div class="mission-list">
              <div
                  v-for="mission in region.missions || defaultMissions"
                  :key="mission.id"
                  class="mission-item"
                  @click="selectMission(mission, region.name)"
              >
                <span class="status-dot"></span>
                <div class="mission-info">
                  <span class="mission-title">{{ mission.title }}</span>
                  <span class="mission-diff">{{ mission.difficulty || 'Normal' }}</span>
                </div>
                <button class="select-btn">START</button>
              </div>
            </div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { useSessionStore } from '@/stores/sessionStore.js';
import apiClient from '@/api/axiosInstance'; // 앞서 만든 axios 인스턴스

const router = useRouter();
const sessionStore = useSessionStore();
const userInfo = computed(() => sessionStore.userInfo);

const regions = ref([]);
const loading = ref(true);

// 만약 DB의 Mission 테이블이 비어있을 경우를 대비한 템플릿 데이터
const defaultMissions = [
  { id: 1, title: "오퍼레이션: 서울 (프롤로그)", difficulty: "Easy" }
];

/**
 * 서버에서 지역 정보를 불러옵니다.
 * 현재 DB의 id: 1 데이터를 타겟팅합니다.
 */
const fetchRegions = async () => {
  try {
    loading.value = true;
    // GET /api/regions (팀원 A가 구현할 API 엔드포인트 가정)
    const response = await apiClient.get('/v1/regions');
    regions.value = response.data;
  } catch (error) {
    console.error("데이터 로드 실패:", error);
    // 테스트를 위해 API 오류 시에도 id:1 더미 데이터를 수동 주입 (서버 미구동 대비)
    regions.value = [{
      id: 1,
      name: "서울 종로구",
      description: "고궁의 비밀과 현대의 암호가 공존하는 구역",
      missions: null // 미션이 아직 없다면 defaultMissions 사용
    }];
  } finally {
    loading.value = false;
  }
};

const selectMission = (mission, regionName) => {
  if (confirm(`[${regionName} - ${mission.title}] 작전을 시작하시겠습니까?`)) {
    // 📌 미션 ID를 가지고 브리핑 뷰로 이동
    router.push({
      name: 'Briefing',
      query: { missionId: mission.id }
    });
  }
};

onMounted(() => {
  fetchRegions();
});
</script>

<style scoped>
/* 이전 스타일 유지 및 보강 */
.home-container {
  min-height: 100vh;
  background-color: #0b0f19;
  color: #06b6d4;
  padding: 20px;
  display: flex;
  justify-content: center;
  font-family: 'Noto Sans KR', sans-serif;
}

.dashboard {
  width: 100%;
  max-width: 600px;
  background: rgba(15, 23, 42, 0.8);
  border: 1px solid #1e293b;
  border-radius: 16px;
  padding: 30px;
  backdrop-filter: blur(10px);
}

.header { border-bottom: 1px solid rgba(6, 182, 212, 0.3); margin-bottom: 30px; padding-bottom: 15px; }
.welcome-text { font-size: 1.4rem; letter-spacing: 2px; color: #fff; }
.agent-info { color: #94a3b8; font-size: 0.85rem; margin-top: 5px; }

.status-msg { text-align: center; padding: 40px; color: #64748b; font-style: italic; }

.region-card {
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 20px;
}

.region-header { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
.region-id { font-size: 0.7rem; background: #06b6d4; color: #0b0f19; padding: 2px 6px; border-radius: 4px; font-weight: bold; }
.region-card h3 { margin: 0; color: #fff; font-size: 1.1rem; }
.region-desc { font-size: 0.85rem; color: #94a3b8; line-height: 1.5; margin-bottom: 20px; }

.mission-item {
  display: flex;
  align-items: center;
  padding: 12px 15px;
  background: rgba(0, 0, 0, 0.4);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
  border: 1px solid transparent;
}

.mission-item:hover {
  background: rgba(6, 182, 212, 0.1);
  border-color: #06b6d4;
  transform: translateX(5px);
}

.status-dot { width: 6px; height: 6px; background: #22c55e; border-radius: 50%; margin-right: 15px; box-shadow: 0 0 8px #22c55e; }
.mission-info { flex: 1; display: flex; flex-direction: column; gap: 2px; }
.mission-title { font-size: 0.9rem; color: #e2e8f0; font-weight: 500; }
.mission-diff { font-size: 0.7rem; color: #64748b; text-transform: uppercase; }

.select-btn {
  background: transparent;
  border: 1px solid #06b6d4;
  color: #06b6d4;
  padding: 5px 12px;
  font-size: 0.75rem;
  font-weight: bold;
  border-radius: 4px;
  cursor: pointer;
}
</style>