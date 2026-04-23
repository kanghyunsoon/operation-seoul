import axios from 'axios';
import { useSessionStore } from '@/stores/sessionStore';

// 1. 기본 Axios 인스턴스 생성
const apiClient = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api', // 백엔드 서버 주소
    timeout: 10000,
    headers: {
        'Content-Type': 'application/json',
    },
});

// 2. Request 인터셉터: 요청이 서버로 가기 직전에 가로채기
apiClient.interceptors.request.use(
    (config) => {
        // Pinia 스토어 호출
        const sessionStore = useSessionStore();

        // 토큰이 존재하면 Authorization 헤더에 Bearer 타입으로 주입
        if (sessionStore.isLoggedIn && sessionStore.token) {
            config.headers['Authorization'] = `Bearer ${sessionStore.token}`;
        }

        return config;
    },
    (error) => {
        return Promise.reject(error);
    }
);

// 3. Response 인터셉터 (선택 사항: 만료된 토큰 처리 등)
apiClient.interceptors.response.use(
    (response) => response,
    (error) => {
        if (error.response && error.response.status === 401) {
            // 401 Unauthorized 에러 발생 시 강제 로그아웃 처리 등 추가 가능
            const sessionStore = useSessionStore();
            sessionStore.logout();
            window.location.href = '/intro'; // 로그인 화면으로 튕겨내기
        }
        return Promise.reject(error);
    }
);

export default apiClient;