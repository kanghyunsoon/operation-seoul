import { ref, computed } from 'vue';
import { defineStore } from 'pinia';

// 로그인 세션을 관리하는 전역 store입니다.
// 새로고침 후에도 인증 상태가 유지되도록 token/userInfo를 localStorage에 동기화합니다.
export const useSessionStore = defineStore('session', () => {
    const token = ref(localStorage.getItem('accessToken') || null);
    const userInfo = ref(normalizeUserInfo(JSON.parse(localStorage.getItem('userInfo')) || null));

    const isLoggedIn = computed(() => !!token.value);

    const userId = computed(() => userInfo.value?.id || null);

    // AuthController.login 응답을 받아 프론트 상태와 localStorage를 동시에 갱신합니다.
    const login = (payload) => {
        const normalizedUser = normalizeUserInfo(payload.user);
        token.value = payload.token;
        userInfo.value = normalizedUser;

        localStorage.setItem('accessToken', payload.token);
        localStorage.setItem('userInfo', JSON.stringify(normalizedUser));
    };

    // 로그아웃 또는 401 응답 시 모든 인증 정보를 제거합니다.
    const logout = () => {
        token.value = null;
        userInfo.value = null;
        localStorage.removeItem('accessToken');
        localStorage.removeItem('userInfo');
    };

    return { token, userInfo, userId, isLoggedIn, login, logout };
});

// 백엔드/Lombok 직렬화 방식에 따라 isAdmin 또는 admin으로 내려올 수 있어 프론트에서 통일합니다.
const normalizeUserInfo = (user) => {
    if (!user) return null;

    return {
        ...user,
        isAdmin: user.isAdmin === true || user.admin === true,
    };
};
