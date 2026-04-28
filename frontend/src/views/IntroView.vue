<template>
  <div class="intro-container">
    <div class="bg-shape circle-1"></div>
    <div class="bg-shape circle-2"></div>

    <div class="login-card">
      <div class="header">
        <h1 class="title">OPERATION<span class="highlight">: SEOUL</span></h1>
        <p class="subtitle">현장 요원 시스템에 연결합니다</p>
      </div>

      <form @submit.prevent="handleSubmit" class="auth-form">
        <div class="input-group">
          <label for="email">Agent Email</label>
          <div class="input-wrapper">
            <input
                id="email"
                v-model="email"
                type="email"
                required
                placeholder="이메일을 입력하세요"
                autocomplete="off"
            />
          </div>
        </div>

        <div class="input-group">
          <label for="password">Password</label>
          <div class="input-wrapper">
            <input
                id="password"
                v-model="password"
                type="password"
                required
                placeholder="비밀번호를 입력하세요"
            />
          </div>
        </div>

        <transition name="fade">
          <div v-if="!isLoginMode" class="input-group">
            <label for="nickname">Codename</label>
            <div class="input-wrapper">
              <input
                  id="nickname"
                  v-model="nickname"
                  type="text"
                  required
                  placeholder="사용할 코드네임을 입력하세요"
              />
            </div>
          </div>
        </transition>

        <button type="submit" class="submit-btn">
          {{ isLoginMode ? 'SYSTEM ACCESS' : 'REGISTER AGENT' }}
        </button>
      </form>

      <div class="toggle-mode">
        <button type="button" @click="toggleMode" class="text-btn">
          {{ isLoginMode ? '새로운 요원으로 등록하시겠습니까?' : '이미 등록된 요원이신가요?' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { useSessionStore } from '@/stores/sessionStore.js';
// 실제 백엔드 연동 시 사용할 API 인스턴스
// import apiClient from '@/api/axiosInstance';

const router = useRouter();
const sessionStore = useSessionStore();

const isLoginMode = ref(true);
const email = ref('');
const password = ref('');
const nickname = ref('');

const toggleMode = () => {
  isLoginMode.value = !isLoginMode.value;
  email.value = '';
  password.value = '';
  nickname.value = '';
};

const handleSubmit = async () => {
  try {
    if (isLoginMode.value) {
      // 🟢 [로그인 모드]
      const response = await apiClient.post('/auth/login', {
        email: email.value,
        password: password.value
      });

      // 백엔드에서 받은 토큰과 유저 정보를 Pinia 스토어에 저장
      sessionStore.login({
        token: response.data.token,
        user: response.data.user
      });

      alert('요원 인증 완료. HQ 터미널에 접속합니다.');
      router.push({name: 'Home'});

    } else {
      // 🔵 [회원가입 모드] (새로 추가된 부분!)
      const response = await apiClient.post('/auth/register', {
        email: email.value,
        password: password.value,
        nickname: nickname.value
      });

      alert('신규 요원 등록이 완료되었습니다. 이제 로그인해주십시오.');
      toggleMode(); // 가입 성공 시 다시 로그인 화면으로 부드럽게 자동 전환
    }
  } catch (error) {
      // 에러 발생 시 모드에 따라 알림 다르게 표시
      if (isLoginMode.value) {
        alert('시스템 접근 거부: 등록되지 않은 요원이거나 암호가 틀렸습니다.');
      } else {
        alert('등록 실패: 이미 사용 중인 이메일이거나 서버 오류입니다.');
      }
      console.error(error);
  }
};
</script>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Noto+Sans+KR:wght@300;400;500;700&display=swap');

.intro-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background-color: #0b0f19; /* 깊은 남색 계열의 다크 테마 */
  font-family: 'Noto Sans KR', sans-serif;
  position: relative;
  overflow: hidden;
}

/* 배경 장식 (모던한 느낌의 빛 번짐 효과) */
.bg-shape {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  z-index: 0;
}
.circle-1 {
  width: 300px;
  height: 300px;
  background: rgba(6, 182, 212, 0.2); /* Cyan 빛 */
  top: -50px;
  left: -50px;
}
.circle-2 {
  width: 400px;
  height: 400px;
  background: rgba(59, 130, 246, 0.15); /* Blue 빛 */
  bottom: -100px;
  right: -100px;
}

/* Glassmorphism 로그인 카드 */
.login-card {
  position: relative;
  z-index: 1;
  background: rgba(255, 255, 255, 0.03);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 20px;
  padding: 40px;
  width: 100%;
  max-width: 400px;
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5);
}

.header {
  text-align: center;
  margin-bottom: 35px;
}

.title {
  font-size: 1.8rem;
  font-weight: 700;
  color: #ffffff;
  margin: 0 0 8px 0;
  letter-spacing: 1px;
}

.title .highlight {
  color: #06b6d4; /* 세련된 포인트 컬러 */
}

.subtitle {
  font-size: 0.9rem;
  color: #94a3b8;
  margin: 0;
}

.auth-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.input-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.input-group label {
  font-size: 0.85rem;
  color: #cbd5e1;
  font-weight: 500;
}

.input-wrapper input {
  width: 100%;
  padding: 14px 16px;
  background: rgba(0, 0, 0, 0.2);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 10px;
  color: #ffffff;
  font-size: 1rem;
  transition: all 0.3s ease;
  box-sizing: border-box;
}

.input-wrapper input::placeholder {
  color: #475569;
}

.input-wrapper input:focus {
  outline: none;
  border-color: #06b6d4;
  background: rgba(0, 0, 0, 0.4);
  box-shadow: 0 0 0 3px rgba(6, 182, 212, 0.15);
}

.submit-btn {
  margin-top: 10px;
  padding: 14px;
  background: #06b6d4;
  color: #ffffff;
  border: none;
  border-radius: 10px;
  font-size: 1rem;
  font-weight: 700;
  cursor: pointer;
  transition: background 0.3s ease, transform 0.1s ease;
  letter-spacing: 0.5px;
}

.submit-btn:hover {
  background: #0891b2;
}

.submit-btn:active {
  transform: scale(0.98);
}

.toggle-mode {
  margin-top: 25px;
  text-align: center;
}

.text-btn {
  background: none;
  border: none;
  color: #64748b;
  font-size: 0.85rem;
  cursor: pointer;
  transition: color 0.3s ease;
}

.text-btn:hover {
  color: #06b6d4;
}

/* Vue Transition 애니메이션 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease, transform 0.3s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}
</style>