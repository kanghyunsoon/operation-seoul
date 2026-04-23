import { defineStore } from 'pinia';
import { ref, computed } from 'vue';

/**
 * 전역 유저 세션 상태 관리 스토어 (Pinia Setup Store)
 * @description 로그인 상태, 유저 정보, 인증 토큰을 관리하며 앱 전역에서 유저 상태를 동기화합니다.
 */
export const useSessionStore = defineStore('session', () => {
    // ==========================================
    // 1. State (상태 데이터)
    // ==========================================

    /** * @type {import('vue').Ref<string|null>}
     * @description 백엔드(Spring Boot) API 통신 시 Authorization 헤더에 담을 JWT 토큰
     */
    const token = ref(null);

    /** * @type {import('vue').Ref<Object|null>}
     * @description 현재 로그인한 유저의 상세 정보 (예: id, email, nickname, role 등)
     */
    const userInfo = ref(null);

    // ==========================================
    // 2. Getters (계산된 상태)
    // ==========================================

    /**
     * @type {import('vue').ComputedRef<boolean>}
     * @description 현재 로그인 여부를 반환 (token이 존재하면 true)
     */
    const isLoggedIn = computed(() => !!token.value);

    // ==========================================
    // 3. Actions (상태 변경 메서드)
    // ==========================================

    /**
     * 로그인 성공 시 호출되어 전역 상태를 업데이트합니다.
     * @param {Object} payload
     * @param {string} payload.token - 발급받은 JWT 토큰
     * @param {Object} payload.user - 유저 상세 정보
     */
    const login = (payload) => {
        token.value = payload.token;
        userInfo.value = payload.user;

        // (선택) 새로고침 시 로그인 상태 유지를 위해 localStorage 등에 토큰을 저장할 수 있습니다.
        // localStorage.setItem('accessToken', payload.token);
    };

    /**
     * 로그아웃 시 호출되어 전역 상태를 초기화합니다.
     */
    const logout = () => {
        token.value = null;
        userInfo.value = null;

        // localStorage.removeItem('accessToken');
    };

    // 외부 컴포넌트에서 사용할 수 있도록 반환
    return {
        token,
        userInfo,
        isLoggedIn,
        login,
        logout
    };
});