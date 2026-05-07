import { ref, computed } from 'vue';
import { defineStore } from 'pinia';

export const useSessionStore = defineStore('session', () => {
    const token = ref(localStorage.getItem('accessToken') || null);
    const userInfo = ref(JSON.parse(localStorage.getItem('userInfo')) || null);

    const isLoggedIn = computed(() => !!token.value);

    // 🚨 컴포넌트에서 편하게 userId를 꺼내 쓰기 위한 getter
    const userId = computed(() => userInfo.value?.id || null);

    const login = (payload) => {
        token.value = payload.token;
        userInfo.value = payload.user;

        localStorage.setItem('accessToken', payload.token);
        localStorage.setItem('userInfo', JSON.stringify(payload.user));
    };

    const logout = () => {
        token.value = null;
        userInfo.value = null;
        localStorage.removeItem('accessToken');
        localStorage.removeItem('userInfo');
    };

    return { token, userInfo, userId, isLoggedIn, login, logout };
});