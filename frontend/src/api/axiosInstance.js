import axios from 'axios';
import { useSessionStore } from '@/stores/sessionStore';

// 백엔드 공통 HTTP 클라이언트입니다.
// Vite 환경 변수에 값이 없으면 로컬 Spring Boot 기본 주소를 사용합니다.
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

        // 로그인 후 발급받은 JWT를 모든 API 요청에 자동으로 첨부합니다.
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
        // 백엔드가 문자열 또는 JSON으로 에러를 내려도 화면에서는 error.userMessage만 보면 되게 표준화합니다.
        if (error.response && error.response.data) {
            const responseData = error.response.data;
            error.userMessage = typeof responseData === 'string'
                ? responseData
                : responseData.message || JSON.stringify(responseData);
        }
        // 토큰 만료/인증 실패는 세션을 지우고 로그인 화면으로 보냅니다.
        if (error.response && error.response.status === 401) {
            const sessionStore = useSessionStore();
            sessionStore.logout();
            window.location.href = '/intro';
        }
        // 관리자 API 접근 실패 등은 사용자에게 명확한 권한 메시지를 보여줍니다.
        if (error.response && error.response.status === 403) {
            error.userMessage = error.userMessage || '해당 기능을 실행할 권한이 없습니다.';
        }
        return Promise.reject(error);
    }
);

export default apiClient;
