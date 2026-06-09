import axios from 'axios';
import { useSessionStore } from '@/stores/sessionStore';

const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api',
  timeout: Number(import.meta.env.VITE_API_TIMEOUT_MS || 20000),
  headers: {
    'Content-Type': 'application/json',
  },
});

apiClient.interceptors.request.use(
  (config) => {
    const sessionStore = useSessionStore();
    if (sessionStore.isLoggedIn && sessionStore.token) {
      config.headers.Authorization = `Bearer ${sessionStore.token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.data) {
      const responseData = error.response.data;
      error.errorCode = responseData.code;
      error.requestId = responseData.requestId || error.response.headers?.['x-request-id'];
      error.userMessage = typeof responseData === 'string'
        ? responseData
        : responseData.message || JSON.stringify(responseData);
    }

    if (error.code === 'ECONNABORTED') {
      error.userMessage = '서버 응답 시간이 초과되었습니다. 잠시 후 다시 시도해 주세요.';
    } else if (!error.response) {
      error.userMessage = '서버에 연결할 수 없습니다. 네트워크 상태를 확인해 주세요.';
    }

    if (error.response?.status === 401) {
      const sessionStore = useSessionStore();
      sessionStore.logout();
      error.userMessage = error.userMessage || '다시 로그인해 주세요.';
      if (window.location.pathname !== '/intro') {
        window.location.href = '/intro';
      }
    }

    if (error.response?.status === 403) {
      error.userMessage = error.userMessage || '접근 권한이 없습니다.';
    }

    if (error.response?.status === 429) {
      error.userMessage = error.userMessage || '요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.';
    }

    return Promise.reject(error);
  }
);

export default apiClient;
