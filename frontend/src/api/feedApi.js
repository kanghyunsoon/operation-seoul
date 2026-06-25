import apiClient from '@/api/axiosInstance';

const unwrap = (response) => response.data?.data ?? response.data;

export const feedApi = {
  async getMyFeed(params = {}) {
    return unwrap(await apiClient.get('/v1/users/me/feed', { params }));
  },
  async getUserFeed(userId, params = {}) {
    return unwrap(await apiClient.get(`/v1/users/me/feed/${userId}`, { params }));
  }
};
