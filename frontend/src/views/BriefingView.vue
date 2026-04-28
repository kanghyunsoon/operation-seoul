<template>
  <div class="briefing-container">
    <div v-if="isLoading" class="loading-wrapper">
      <div class="radar"></div>
      <p class="loading-text">1급 기밀 문서를 복호화 중입니다...</p>
    </div>

    <div v-else-if="missionData" class="content-wrapper">
      <div class="glass-panel">
        <header class="header">
          <div class="status-line">
            <span class="id-tag">SEC_CODE: #00{{ missionData.id }}</span>
            <span class="auth-status">보안 등급: TOP SECRET</span>
          </div>
          <h1 class="mission-title">{{ missionData.title }}</h1>
        </header>

        <main class="briefing-main">
          <div class="video-placeholder">
            <div class="scan-line"></div>
            <p class="intel-text">위성 데이터 수신 완료</p>
            <div class="target-coord">
              LAT: {{ missionData.targetLat }} | LNG: {{ missionData.targetLng }}
            </div>
          </div>

          <section class="intel-section">
            <h3>[ 작전 브리핑 ]</h3>
            <p class="description">
              요원, 해당 구역에 진입하여 목표물을 스캔하라.
              작전명 <strong>[{{ missionData.title }}]</strong>의 핵심 단서는 반경 {{ missionData.radiusInMeters || 30 }}m 내에 존재한다.
            </p>

            <div class="meta-info">
              <div class="info-item">
                <label>현장 인증 타겟</label>
                <span class="highlight-text">{{ missionData.visionKeyword || '비공개' }}</span>
              </div>
            </div>
          </section>

          <div class="btn-group">
            <button @click="startDeployment" class="start-btn">현장 투입 (DEPLOY)</button>
            <button @click="goBack" class="back-btn">뒤로 가기</button>
          </div>
        </main>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import apiClient from '@/api/axiosInstance';

const route = useRoute();
const router = useRouter();

const missionData = ref(null);
const isLoading = ref(true);

onMounted(async () => {
  const missionId = route.query.missionId;

  if (!missionId) {
    alert('잘못된 접근입니다.');
    router.push('/home');
    return;
  }

  try {
    // 📡 팀원 A가 생성한 단일 미션 조회 API 호출
    const response = await apiClient.get(`/v1/missions/${missionId}`);
    missionData.value = response.data;
  } catch (error) {
    console.error("데이터 로드 실패:", error);
    alert('기밀 데이터를 불러오지 못했습니다. 본부 통신 상태를 확인하세요.');
    router.push('/home');
  } finally {
    isLoading.value = false;
  }
});

const startDeployment = () => {
  // 실제 맵 화면으로 이동 (임시 세션 시작 로직은 나중에 맵뷰에 통합 가능)
  router.push({ name: 'Map', query: { missionId: route.query.missionId } });
};

const goBack = () => {
  router.push('/home');
};
</script>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Noto+Sans+KR:wght@400;500;700&display=swap');

.briefing-container {
  min-height: 100vh;
  background-color: #0b0f19;
  color: #e2e8f0;
  font-family: 'Noto Sans KR', sans-serif;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 20px;
}

.content-wrapper { width: 100%; max-width: 650px; }

/* 글래스모피즘 패널 */
.glass-panel {
  background: rgba(255, 255, 255, 0.03);
  backdrop-filter: blur(15px);
  -webkit-backdrop-filter: blur(15px);
  border: 1px solid rgba(6, 182, 212, 0.3);
  border-radius: 12px;
  padding: 30px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.5);
}

.header { margin-bottom: 20px; border-bottom: 1px solid rgba(255, 255, 255, 0.1); padding-bottom: 15px; }
.status-line { display: flex; justify-content: space-between; font-size: 0.8rem; color: #f59e0b; margin-bottom: 10px; font-weight: bold; }
.mission-title { font-size: 1.8rem; margin: 0; color: #fff; text-shadow: 0 0 10px rgba(6, 182, 212, 0.5); }

/* 영상/위성 Placeholder */
.video-placeholder {
  width: 100%; height: 200px;
  background: rgba(0, 0, 0, 0.5);
  border: 1px solid rgba(6, 182, 212, 0.2);
  border-radius: 8px;
  position: relative;
  overflow: hidden;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  margin-bottom: 20px;
}

.scan-line {
  position: absolute; width: 100%; height: 2px;
  background: rgba(6, 182, 212, 0.5);
  box-shadow: 0 0 10px #06b6d4;
  animation: scan 2.5s linear infinite;
}
@keyframes scan { 0% { top: 0; } 100% { top: 100%; } }

.intel-text { color: #06b6d4; margin-bottom: 5px; font-weight: bold; letter-spacing: 2px; }
.target-coord { font-family: monospace; color: #94a3b8; font-size: 0.9rem; }

.intel-section { background: rgba(0, 0, 0, 0.3); padding: 20px; border-radius: 8px; margin-bottom: 30px; }
.intel-section h3 { color: #06b6d4; margin: 0 0 10px 0; font-size: 1rem; }
.description { line-height: 1.6; color: #cbd5e1; font-size: 0.95rem; margin-bottom: 15px; }
.info-item { display: flex; justify-content: space-between; padding: 10px 0; border-top: 1px dashed rgba(255,255,255,0.1); }
.highlight-text { color: #f59e0b; font-weight: bold; }

/* 버튼 그룹 */
.btn-group { display: flex; gap: 15px; }
.start-btn {
  flex: 2; padding: 15px; border-radius: 8px; border: none;
  background: #06b6d4; color: #000; font-weight: 700; cursor: pointer; transition: 0.3s;
}
.start-btn:hover { background: #0891b2; transform: translateY(-2px); }
.back-btn {
  flex: 1; padding: 15px; border-radius: 8px;
  background: transparent; border: 1px solid #64748b; color: #94a3b8; cursor: pointer; transition: 0.3s;
}
.back-btn:hover { border-color: #fff; color: #fff; }

/* 로딩 화면 */
.loading-wrapper { display: flex; flex-direction: column; align-items: center; justify-content: center; }
.radar { width: 50px; height: 50px; border: 2px solid #06b6d4; border-radius: 50%; animation: pulse 1.5s infinite; }
@keyframes pulse { 0% { transform: scale(0.5); opacity: 1; } 100% { transform: scale(1.5); opacity: 0; } }
.loading-text { margin-top: 20px; color: #06b6d4; font-family: monospace; letter-spacing: 1px; }
</style>