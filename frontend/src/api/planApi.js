import apiClient from '@/api/axiosInstance';

const unwrap = (response) => response.data?.data ?? response.data;

export const planApi = {
  async getMyPlans() {
    return unwrap(await apiClient.get('/v1/users/me/plans'));
  },
  async createPlan(payload) {
    return unwrap(await apiClient.post('/v1/users/me/plans', payload));
  },
  async updatePlan(planId, payload) {
    return unwrap(await apiClient.put(`/v1/users/me/plans/${planId}`, payload));
  },
  async deletePlan(planId) {
    return unwrap(await apiClient.delete(`/v1/users/me/plans/${planId}`));
  }
};
