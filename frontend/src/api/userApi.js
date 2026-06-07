import apiClient from '@/api/axiosInstance';

const unwrap = (response) => response.data?.data ?? response.data;

export const userApi = {
  async me() {
    return unwrap(await apiClient.get('/v1/users/me'));
  },
  async updateMe(payload) {
    return unwrap(await apiClient.put('/v1/users/me', payload));
  },
  async changePassword(payload) {
    return unwrap(await apiClient.put('/v1/users/me/password', payload));
  },
  async deleteMe() {
    return unwrap(await apiClient.delete('/v1/users/me'));
  },
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
