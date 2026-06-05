import { ref, computed } from 'vue';
import { defineStore } from 'pinia';

export const useSessionStore = defineStore('session', () => {
  const token = ref(localStorage.getItem('accessToken') || null);
  const userInfo = ref(normalizeUserInfo(safeParse(localStorage.getItem('userInfo'))));
  const initialized = ref(false);

  const isLoggedIn = computed(() => !!token.value);
  const isAuthenticated = computed(() => !!token.value && !!userInfo.value);
  const currentUser = computed(() => userInfo.value);
  const userId = computed(() => userInfo.value?.id || null);
  const isAdmin = computed(() => userInfo.value?.isAdmin === true || userInfo.value?.role === 'ROLE_ADMIN');

  const login = (payload) => {
    const authPayload = payload?.data || payload;
    const normalizedUser = normalizeUserInfo(authPayload.user);
    token.value = authPayload.token;
    userInfo.value = normalizedUser;
    initialized.value = true;

    localStorage.setItem('accessToken', authPayload.token);
    localStorage.setItem('userInfo', JSON.stringify(normalizedUser));
  };

  const setCurrentUser = (user) => {
    const normalizedUser = normalizeUserInfo(user);
    userInfo.value = normalizedUser;
    initialized.value = true;
    if (normalizedUser) {
      localStorage.setItem('userInfo', JSON.stringify(normalizedUser));
    }
  };

  const fetchCurrentUser = async () => {
    if (!token.value) {
      initialized.value = true;
      return null;
    }
    const { userApi } = await import('@/api/userApi');
    const user = await userApi.me();
    setCurrentUser(user);
    return user;
  };

  const ensureInitialized = async () => {
    if (initialized.value) return userInfo.value;
    if (!token.value) {
      initialized.value = true;
      return null;
    }
    try {
      return await fetchCurrentUser();
    } catch (error) {
      logout();
      return null;
    }
  };

  const logout = () => {
    token.value = null;
    userInfo.value = null;
    initialized.value = true;
    localStorage.removeItem('accessToken');
    localStorage.removeItem('userInfo');
  };

  return {
    token,
    userInfo,
    currentUser,
    userId,
    isLoggedIn,
    isAuthenticated,
    isAdmin,
    initialized,
    login,
    logout,
    fetchCurrentUser,
    ensureInitialized,
    setCurrentUser
  };
});

const normalizeUserInfo = (user) => {
  if (!user) return null;
  return {
    ...user,
    isAdmin: user.isAdmin === true || user.admin === true || user.role === 'ROLE_ADMIN',
  };
};

const safeParse = (value) => {
  if (!value) return null;
  try {
    return JSON.parse(value);
  } catch {
    return null;
  }
};