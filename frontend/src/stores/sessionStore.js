import { ref, computed } from 'vue';
import { defineStore } from 'pinia';

export const useSessionStore = defineStore('session', () => {
    const token = ref(localStorage.getItem('accessToken') || null);
    const userInfo = ref(normalizeUserInfo(JSON.parse(localStorage.getItem('userInfo')) || null));

    const isLoggedIn = computed(() => !!token.value);

    const userId = computed(() => userInfo.value?.id || null);

    const login = (payload) => {
        const normalizedUser = normalizeUserInfo(payload.user);
        token.value = payload.token;
        userInfo.value = normalizedUser;

        localStorage.setItem('accessToken', payload.token);
        localStorage.setItem('userInfo', JSON.stringify(normalizedUser));
    };

    const logout = () => {
        token.value = null;
        userInfo.value = null;
        localStorage.removeItem('accessToken');
        localStorage.removeItem('userInfo');
    };

    return { token, userInfo, userId, isLoggedIn, login, logout };
});

const normalizeUserInfo = (user) => {
    if (!user) return null;

    return {
        ...user,
        isAdmin: user.isAdmin === true || user.admin === true,
    };
};
