import apiClient from '@/api/axiosInstance';

const unwrap = (response) => response.data?.data ?? response.data;

export const challengeApi = {
  async getChallenges() {
    return unwrap(await apiClient.get('/v1/challenges'));
  },
  async getMyChallenges() {
    return unwrap(await apiClient.get('/v1/challenges/me'));
  },
  async joinChallenge(challengeId) {
    return unwrap(await apiClient.post(`/v1/challenges/${challengeId}/join`));
  }
};
