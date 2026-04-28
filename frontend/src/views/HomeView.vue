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

      <main class="mission-grid">
        <div
          v-for="mission in mockMissions"
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
          <p class="mission-desc">{{ mission.description }}</p>

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
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { useSessionStore } from '@/stores/sessionStore';

const router = useRouter();
const sessionStore = useSessionStore();

// 임시 미션 데이터 (각자 고유한 id와 isReady 상태를 가짐)
const mockMissions = ref([
  {
    id: 1,
    title: '중명전의 비밀',
    description: '덕수궁 돌담길에 숨겨진 제국의 마지막 문서를 확보하라.',
    difficulty: 'NORMAL',
    location: '서울 정동길',
    status: 'ACTIVE',
    isReady: true // 데이터 있음 (접근 가능)
  },
  {
    id: 2,
    title: '광화문 아티팩트',
    description: '경복궁 앞 광장에 흩어진 암호화된 유물을 스캔하여 해독하라.',
    difficulty: 'HARD',
    location: '서울 광화문',
    status: 'ACTIVE',
    isReady: false // 데이터 없음 (분석 중)
  },
  {
    id: 3,
    title: '남산 타워의 그림자',
    description: '남산 송신탑에 접근하여 적의 통신망을 교란하라.',
    difficulty: 'EASY',
    location: '서울 남산',
    status: 'CLEARED',
    isReady: false // 데이터 없음 (분석 중)
  }
]);

// 🚨 미션 클릭 시 사전 검증 로직
const handleMissionClick = (mission) => {
  if (!mission.isReady) {
    // 데이터가 없는 경우 알림창 표시 후 이동 차단
    alert(`[접근 거부] 요원님, 섹터 #${mission.id}은(는) 현재 본부에서 분석 중인 사건입니다. 정보가 확보될 때까지 대기하십시오.`);
    return;
  }

  if (mission.status === 'LOCKED') {
    alert('이 작전은 아직 해금되지 않았습니다. 이전 작전을 먼저 완수하십시오.');
    return;
  }

  // 🟢 접근 가능한 경우에만 브리핑 화면으로 실제 missionId를 전달
  router.push({ name: 'Briefing', query: { missionId: mission.id } });
};

// 로그아웃 로직
const handleLogout = () => {
  sessionStore.logout();
  router.push({ name: 'Intro' });
};
</script>

<style scoped>
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

/* 배경 빛 번짐 효과 */
.bg-glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(100px);
  z-index: 0;
  opacity: 0.5;
}
.blob-1 { width: 400px; height: 400px; background: #06b6d4; top: -100px; left: -100px; }
.blob-2 { width: 500px; height: 500px; background: #3b82f6; bottom: -150px; right: -100px; }

.content-wrapper {
  position: relative;
  z-index: 1;
  max-width: 1000px;
  margin: 0 auto;
}

/* 헤더 스타일 */
.dashboard-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: 40px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  padding-bottom: 20px;
}

.title { font-size: 2rem; font-weight: 700; margin: 0 0 5px 0; color: #fff; }
.highlight { color: #06b6d4; }
.subtitle { font-size: 0.9rem; color: #94a3b8; margin: 0; }

.user-panel { display: flex; align-items: center; gap: 20px; }
.agent-name { color: #06b6d4; font-weight: 500; font-size: 0.9rem; }
.logout-btn {
  background: rgba(239, 68, 68, 0.1); border: 1px solid #ef4444; color: #ef4444;
  padding: 6px 12px; border-radius: 6px; cursor: pointer; transition: 0.3s;
}
.logout-btn:hover { background: #ef4444; color: #fff; }

/* 미션 카드 그리드 */
.mission-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 25px;
}

/* 글래스모피즘 카드 기본 스타일 */
.glass-card {
  background: rgba(255, 255, 255, 0.03);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 16px;
  padding: 24px;
  cursor: pointer;
  transition: transform 0.3s ease, border-color 0.3s ease, box-shadow 0.3s ease;
  display: flex;
  flex-direction: column;
  height: 100%;
}

.glass-card:hover {
  transform: translateY(-5px);
  border-color: rgba(6, 182, 212, 0.5);
  box-shadow: 0 10px 30px -10px rgba(0, 0, 0, 0.5), 0 0 15px rgba(6, 182, 212, 0.2);
  background: rgba(255, 255, 255, 0.05);
}

/* 🚨 분석 중인 카드의 비활성화 스타일 */
.glass-card.analyzing {
  opacity: 0.6;
  cursor: not-allowed;
}

.glass-card.analyzing:hover {
  transform: none; /* 애니메이션 제거 */
  border-color: rgba(255, 255, 255, 0.08);
  box-shadow: none;
  background: rgba(255, 255, 255, 0.03);
}

.card-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 15px;
}

.status-badge, .diff-badge {
  font-size: 0.75rem; font-weight: 700; padding: 4px 10px; border-radius: 20px;
}

/* 상태별 색상 */
.active { background: rgba(16, 185, 129, 0.2); color: #10b981; border: 1px solid rgba(16, 185, 129, 0.3); }
.locked { background: rgba(239, 68, 68, 0.2); color: #ef4444; border: 1px solid rgba(239, 68, 68, 0.3); }
.cleared { background: rgba(59, 130, 246, 0.2); color: #3b82f6; border: 1px solid rgba(59, 130, 246, 0.3); }

/* 분석 중 상태 배지 색상 */
.analyzing-badge {
  background: rgba(148, 163, 184, 0.2); color: #94a3b8; border: 1px solid rgba(148, 163, 184, 0.3);
}

/* 난이도별 색상 */
.easy { color: #10b981; }
.normal { color: #f59e0b; }
.hard { color: #ef4444; }

.mission-title { font-size: 1.25rem; font-weight: 700; color: #fff; margin: 0 0 10px 0; }
.mission-desc { font-size: 0.85rem; color: #94a3b8; line-height: 1.5; margin: 0 0 20px 0; flex-grow: 1; }

.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: auto;
  padding-top: 15px;
  border-top: 1px solid rgba(255, 255, 255, 0.05);
}

.location-tag { font-size: 0.8rem; color: #cbd5e1; }
.enter-text { font-size: 0.8rem; color: #06b6d4; font-weight: 700; opacity: 0; transition: opacity 0.3s; }
.glass-card:hover .enter-text { opacity: 1; }
/* 비활성화된 카드는 호버 시 글씨가 나타나지 않음 */
.glass-card.analyzing:hover .enter-text { opacity: 0; }
</style>