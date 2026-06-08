import apiClient from '@/api/axiosInstance';

const unwrap = (response) => response.data?.data ?? response.data;

export const coachingApi = {
  async getMyCoaching() {
    return unwrap(await apiClient.get('/v1/coaching/me'));
  },
  async getEpisodeCoaching(episodeId) {
    return unwrap(await apiClient.get(`/v1/coaching/episodes/${episodeId}`));
  }
};
