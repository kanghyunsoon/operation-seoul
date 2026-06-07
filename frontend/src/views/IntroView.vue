<template>
  <main class="intro-container">
    <div class="bg-shape circle-1"></div>
    <div class="bg-shape circle-2"></div>

    <section class="login-card">
      <header class="header">
        <p class="eyebrow">FIELD ACCESS</p>
        <h1 class="title">OPERATION<span> KOREA</span></h1>
        <p class="subtitle">오프라인 사건파일 키트를 모바일 현장 조사로 이어갑니다.</p>
      </header>

      <form class="auth-form" @submit.prevent="handleSubmit">
        <label class="input-group" for="email">
          이메일
          <input id="email" v-model.trim="email" type="email" required placeholder="agent@example.com" autocomplete="email" />
        </label>

        <label class="input-group" for="password">
          비밀번호
          <input id="password" v-model="password" type="password" required placeholder="8자 이상 입력" autocomplete="current-password" />
        </label>

        <label v-if="!isLoginMode" class="input-group" for="nickname">
          닉네임
          <input id="nickname" v-model.trim="nickname" type="text" required placeholder="수사관 닉네임" autocomplete="nickname" />
        </label>

        <button type="submit" class="submit-btn" :disabled="submitting">
          {{ submitting ? '처리 중...' : isLoginMode ? '로그인' : '회원가입' }}
        </button>
      </form>

      <p v-if="formMessage" class="form-message" :class="formMessageType">{{ formMessage }}</p>

      <button type="button" class="text-btn" @click="toggleMode">
        {{ isLoginMode ? '처음이라면 회원가입하기' : '이미 계정이 있다면 로그인하기' }}
      </button>
    </section>
  </main>
</template>

<script setup>
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { useSessionStore } from '@/stores/sessionStore.js';
import apiClient from '@/api/axiosInstance';

const router = useRouter();
const sessionStore = useSessionStore();

const isLoginMode = ref(true);
const email = ref('');
const password = ref('');
const nickname = ref('');
const formMessage = ref('');
const formMessageType = ref('info');
const submitting = ref(false);

function toggleMode() {
  isLoginMode.value = !isLoginMode.value;
  email.value = '';
  password.value = '';
  nickname.value = '';
  formMessage.value = '';
}

async function handleSubmit() {
  if (!email.value || !password.value || (!isLoginMode.value && !nickname.value)) {
    setMessage('필수 입력값을 확인해 주세요.', 'error');
    return;
  }
  if (password.value.length < 8) {
    setMessage('비밀번호는 최소 8자 이상 입력해 주세요.', 'error');
    return;
  }

  submitting.value = true;
  try {
    if (isLoginMode.value) {
      const response = await apiClient.post('/v1/auth/login', { email: email.value, password: password.value });
      sessionStore.login(response.data.data || response.data);
      setMessage(response.data.message || '로그인되었습니다.', 'success');
      router.push({ name: sessionStore.isAdmin ? 'AdminEpisodes' : 'RegionMap' });
    } else {
      const response = await apiClient.post('/v1/auth/register', {
        email: email.value,
        password: password.value,
        nickname: nickname.value,
      });
      setMessage(response.data.message || '회원가입이 완료되었습니다. 로그인해 주세요.', 'success');
      isLoginMode.value = true;
      password.value = '';
      nickname.value = '';
    }
  } catch (error) {
    setMessage(error.userMessage || (isLoginMode.value ? '이메일 또는 비밀번호를 확인해 주세요.' : '회원가입을 완료할 수 없습니다.'), 'error');
  } finally {
    submitting.value = false;
  }
}

function setMessage(text, type) {
  formMessage.value = text;
  formMessageType.value = type;
}
</script>

<style scoped>
.intro-container { position: relative; min-height: 100vh; box-sizing: border-box; display: grid; place-items: center; overflow: hidden; padding: 24px 16px; background: radial-gradient(circle at 20% 12%, rgba(245,158,11,.24), transparent 30%), linear-gradient(155deg, #17110b, #0f172a 58%, #020617); color: #f8fafc; font-family: 'Noto Sans KR', Georgia, serif; }
.bg-shape { position: absolute; border-radius: 999px; filter: blur(4px); opacity: .55; }
.circle-1 { width: 260px; height: 260px; left: -80px; top: -70px; background: rgba(180,83,9,.34); }
.circle-2 { width: 220px; height: 220px; right: -70px; bottom: 8%; background: rgba(20,184,166,.2); }
.login-card { position: relative; width: min(100%, 420px); padding: 28px 22px; border: 1px solid rgba(245,158,11,.24); border-radius: 24px; background: rgba(15,23,42,.78); box-shadow: 0 28px 80px rgba(0,0,0,.34); backdrop-filter: blur(14px); }
.header { margin-bottom: 24px; }
.eyebrow { margin: 0 0 8px; color: #f59e0b; font-size: .76rem; font-weight: 900; letter-spacing: .16em; }
.title { margin: 0; font-size: clamp(2.1rem, 12vw, 3.7rem); line-height: .95; }
.title span { color: #f59e0b; }
.subtitle { margin: 12px 0 0; color: #cbd5e1; line-height: 1.5; }
.auth-form { display: grid; gap: 14px; }
.input-group { display: grid; gap: 7px; color: #e5e7eb; font-weight: 800; }
input { min-height: 48px; box-sizing: border-box; width: 100%; border: 1px solid rgba(148,163,184,.32); border-radius: 14px; background: rgba(2,6,23,.56); color: #f8fafc; padding: 0 14px; font: inherit; }
input:focus { outline: 2px solid rgba(245,158,11,.45); border-color: transparent; }
.submit-btn { min-height: 50px; margin-top: 8px; border: 0; border-radius: 14px; background: #b45309; color: white; font: inherit; font-weight: 900; }
.submit-btn:disabled { opacity: .65; }
.form-message { margin: 16px 0 0; padding: 12px 14px; border-radius: 14px; background: rgba(30,64,175,.2); color: #bfdbfe; }
.form-message.success { background: rgba(22,163,74,.16); color: #bbf7d0; }
.form-message.error { background: rgba(220,38,38,.18); color: #fecaca; }
.text-btn { display: block; width: 100%; min-height: 44px; margin-top: 14px; border: 0; background: transparent; color: #fde68a; font: inherit; font-weight: 900; }
@media (max-width: 390px) { .login-card { padding: 24px 18px; } }
</style>
