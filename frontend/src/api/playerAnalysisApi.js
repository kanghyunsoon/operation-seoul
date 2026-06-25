import apiClient from '@/api/axiosInstance';

const unwrapNullable = (response) => (Object.prototype.hasOwnProperty.call(response.data || {}, 'data') ? response.data.data : response.data);

export const playerAnalysisApi = {
  async getLatest(userId) {
    return unwrapNullable(await apiClient.get('/ai/player-analysis/latest', { params: userId ? { userId } : {} }));
  },
  async createAnalysis(payload) {
    return unwrapNullable(await apiClient.post('/ai/player-analysis', payload));
  }
};
