import apiClient from '@/api/axiosInstance';

const unwrap = (response) => response.data?.data ?? response.data;

export const followApi = {
  async getFollowing() {
    return unwrap(await apiClient.get('/v1/users/me/following'));
  },
  async getFollowers() {
    return unwrap(await apiClient.get('/v1/users/me/followers'));
  },
  async followUser(userId) {
    return unwrap(await apiClient.post(`/v1/users/${userId}/follow`));
  },
  async unfollowUser(userId) {
    return unwrap(await apiClient.delete(`/v1/users/${userId}/follow`));
  }
};
