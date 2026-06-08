import apiClient from '@/api/axiosInstance';

const unwrap = (response) => response.data?.data ?? response.data;

export const recommendationApi = {
  async getEpisodeRecommendations(params = {}) {
    return unwrap(await apiClient.get('/v1/recommendations/episodes', { params }));
  }
};
