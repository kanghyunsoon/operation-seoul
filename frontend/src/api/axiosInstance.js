import axios from 'axios';
import { useSessionStore } from '@/stores/sessionStore';

const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api',
  timeout: 10000,
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
      error.userMessage = typeof responseData === 'string'
        ? responseData
        : responseData.message || JSON.stringify(responseData);
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

    return Promise.reject(error);
  }
);

export default apiClient;