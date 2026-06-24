import apiClient from '@/api/axiosInstance';

const unwrap = (response) => response.data?.data ?? response.data;

export const rankingApi = {
  async getRankings(params = {}) {
    return unwrap(await apiClient.get('/v1/rankings', { params }));
  },
  async getMyRankings(params = {}) {
    return unwrap(await apiClient.get('/v1/rankings/me', { params }));
  },
  async getPlayerRankings(params = {}) {
    return unwrap(await apiClient.get('/v1/rankings/players', { params }));
  }
};
