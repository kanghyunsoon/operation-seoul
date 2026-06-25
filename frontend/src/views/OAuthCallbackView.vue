<template>
  <main class="callback-page">
    <section class="callback-status" role="status" aria-live="polite">
      <span class="spinner" aria-hidden="true"></span>
      <h1>카카오 로그인 처리 중</h1>
      <p>계정 정보를 확인하고 있습니다.</p>
    </section>
  </main>
</template>

<script setup>
import { onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import apiClient from '@/api/axiosInstance';
import { useSessionStore } from '@/stores/sessionStore.js';

const route = useRoute();
const router = useRouter();
const sessionStore = useSessionStore();

onMounted(async () => {
  const expectedState = sessionStorage.getItem('kakaoOAuthState');
  sessionStorage.removeItem('kakaoOAuthState');

  try {
    if (route.query.error) {
      const message = route.query.error === 'KOE004'
        ? '카카오 개발자 콘솔에서 카카오 로그인을 활성화해야 합니다.'
        : route.query.error_description || '카카오 로그인이 취소되었습니다.';
      throw new Error(message);
    }
    if (!route.query.code || !route.query.state || route.query.state !== expectedState) {
      throw new Error('카카오 로그인 요청을 검증할 수 없습니다. 다시 시도해 주세요.');
    }
    const configResponse = await apiClient.get('/v1/auth/oauth/config');
    const config = configResponse.data?.data || configResponse.data;
    const response = await apiClient.post('/v1/auth/oauth/kakao', {
      code: route.query.code,
      redirectUri: config.kakaoRedirectUri,
    });
    sessionStore.login(response.data?.data || response.data);
    await router.replace({ name: sessionStore.isAdmin ? 'AdminEpisodes' : 'RegionMap' });
  } catch (error) {
    sessionStorage.setItem('oauthLoginError', error.userMessage || error.message || '카카오 로그인에 실패했습니다.');
    await router.replace({ name: 'Intro' });
  }
});
</script>

<style scoped>
.callback-page { min-height: 100vh; display: grid; place-items: center; padding: 24px; box-sizing: border-box; background: #08111f; color: #f8fafc; }
.callback-status { width: min(100%, 380px); text-align: center; }
.spinner { display: block; width: 34px; height: 34px; margin: 0 auto 20px; border: 3px solid rgba(148,163,184,.25); border-top-color: #f59e0b; border-radius: 50%; animation: spin .8s linear infinite; }
h1 { margin: 0 0 10px; font-size: 1.35rem; letter-spacing: 0; }
p { margin: 0; color: #94a3b8; }
@keyframes spin { to { transform: rotate(360deg); } }
</style>
