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
            config.headers['Authorization'] = `Bearer ${sessionStore.token}`;
        }

        return config;
    },
    (error) => {
        return Promise.reject(error);
    }
);

apiClient.interceptors.response.use(
    (response) => response,
    (error) => {
        if (error.response && error.response.data) {
            const responseData = error.response.data;
            error.userMessage = typeof responseData === 'string'
                ? responseData
                : responseData.message || JSON.stringify(responseData);
        }
        if (error.response && error.response.status === 401) {
            const sessionStore = useSessionStore();
            sessionStore.logout();
            window.location.href = '/intro';
        }
        if (error.response && error.response.status === 403) {
            error.userMessage = error.userMessage || '해당 기능을 실행할 권한이 없습니다.';
        }
        return Promise.reject(error);
    }
);

export default apiClient;
