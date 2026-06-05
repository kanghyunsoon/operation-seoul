import apiClient from '@/api/axiosInstance';

const unwrap = (response) => response.data?.data ?? response.data;

export const adminUserApi = {
  async getUsers(params = {}) {
    return unwrap(await apiClient.get('/v1/admin/users', { params }));
  },
  async getUser(userId) {
    return unwrap(await apiClient.get(`/v1/admin/users/${userId}`));
  },
  async updateUser(userId, payload) {
    return unwrap(await apiClient.put(`/v1/admin/users/${userId}`, payload));
  },
  async deleteUser(userId) {
    return unwrap(await apiClient.delete(`/v1/admin/users/${userId}`));
  }
};