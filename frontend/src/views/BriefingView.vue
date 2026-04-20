<template>
  <div class="briefing-container">
    <div class="monitor-overlay"></div>

    <div class="text-area">
      <span class="cmd-label">COMMANDER:</span>
      <p class="typewriter">{{ displayedText }}<span class="cursor">_</span></p>
    </div>

    <button v-if="isFinished" @click="goToMap" class="mission-btn">
      확인. 작전 지역으로 이동한다.
    </button>
  </div>
</template>

<script setup>
/**
 * 연출 제어 및 화면 전환 로직
 * 특징: setInterval 기반의 텍스트 애니메이션 및 상태 기반 UI 전환
 */
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';

// 정적 데이터 정의: 연출에 사용될 전체 스토리 텍스트
const fullText = "작전 지역 '중명전' 진입을 확인했다. 이곳은 고종 황제가 을사늑약의 부당함을 알리기 위해 헤이그 특사를 파견했던 비밀 거점이다. 요원, 현판의 암호를 해독하고 작전의 실마리를 찾아라.";

// 반응형 상태 관리
const displayedText = ref(''); // 화면에 실제 출력 중인 텍스트
const isFinished = ref(false); // 타자기 연출 완료 여부
const router = useRouter();    // 라우터 인스턴스

/**
 * 타자기 연출 실행 함수
 * 기능: 50ms마다 문자열 인덱스를 증가시키며 한 글자씩 출력 배열에 추가
 */
const typeText = () => {
  let i = 0;
  const interval = setInterval(() => {
    if (i < fullText.length) {
      // 순차적으로 문자 추가
      displayedText.value += fullText[i];
      i++;
    } else {
      // 모든 텍스트 출력 시 인터벌 종료 및 버튼 활성화 상태 전환
      clearInterval(interval);
      isFinished.value = true;
    }
  }, 50);
};

/**
 * 생명주기 훅: 마운트 시점
 * 컴포넌트 로드 즉시 타자기 연출 시작
 */
onMounted(() => typeText());

/**
 * 화면 전환 함수
 * 기능: 작전 지역 확인 버튼 클릭 시 지도 화면으로 라우팅
 */
const goToMap = () => router.push('/map');
</script>

<style scoped>
/* 연출 디자인: 블랙 테마 및 네온 그린 텍스트 조합 */
.briefing-container {
  background: #000;
  height: 100vh;
  padding: 40px;
  font-family: 'Courier New', monospace;
  color: #00ffcc;
}

/* 발신자 라벨 디자인: 경고의 의미를 담은 레드 컬러 적용 */
.cmd-label {
  font-weight: bold;
  display: block;
  margin-bottom: 10px;
  color: #ff4444;
}

/* 텍스트 행간 및 크기 설정 */
.typewriter {
  line-height: 1.8;
  font-size: 1.2rem;
}

/* 커서 깜빡임 애니메이션 정의 */
.cursor {
  animation: blink 1s infinite;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

/* 미션 진입 버튼 스타일링: 테두리 중심의 모던 디자인 및 호버 연출 */
.mission-btn {
  margin-top: 50px;
  background: none;
  border: 1px solid #00ffcc;
  color: #00ffcc;
  padding: 15px 30px;
  cursor: pointer;
  transition: 0.3s;
}

.mission-btn:hover {
  background: #00ffcc;
  color: #000;
}
</style>