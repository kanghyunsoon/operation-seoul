import apiClient from '@/api/axiosInstance';

const unwrap = (response) => response.data?.data ?? response.data;

export const adminEpisodeApi = {
  async getEpisodes() {
    return unwrap(await apiClient.get('/v1/admin/episodes'));
  },
  async getEpisode(episodeId) {
    return unwrap(await apiClient.get(`/v1/admin/episodes/${episodeId}`));
  },
  async getPublishReadiness(episodeId) {
    return unwrap(await apiClient.get(`/v1/admin/episodes/${episodeId}/publish-readiness`));
  },
  async getPlaceCandidates(areaCode) {
    return unwrap(await apiClient.get('/v1/admin/episodes/place-candidates', { params: { areaCode } }));
  },
  async getNearbyPlaceCandidates({ lat, lng, radius = 1500 }) {
    return unwrap(await apiClient.get('/v1/admin/episodes/place-candidates/nearby', { params: { lat, lng, radius } }));
  },
  async updateEpisode(episodeId, payload) {
    return unwrap(await apiClient.put(`/v1/admin/episodes/${episodeId}`, payload));
  },
  async updateSpot(episodeId, spotId, payload) {
    return unwrap(await apiClient.put(`/v1/admin/episodes/${episodeId}/spots/${spotId}`, payload));
  },
  async createSpot(episodeId, payload) {
    return unwrap(await apiClient.post(`/v1/admin/episodes/${episodeId}/spots`, payload));
  },
  async deleteSpot(episodeId, spotId) {
    return unwrap(await apiClient.delete(`/v1/admin/episodes/${episodeId}/spots/${spotId}`));
  },
  async updatePuzzle(episodeId, puzzleId, payload) {
    return unwrap(await apiClient.put(`/v1/admin/episodes/${episodeId}/puzzles/${puzzleId}`, payload));
  },
  async updateSuspect(episodeId, suspectId, payload) {
    return unwrap(await apiClient.put(`/v1/admin/episodes/${episodeId}/suspects/${suspectId}`, payload));
  },
  async createSuspect(episodeId, payload) {
    return unwrap(await apiClient.post(`/v1/admin/episodes/${episodeId}/suspects`, payload));
  },
  async deleteSuspect(episodeId, suspectId) {
    return unwrap(await apiClient.delete(`/v1/admin/episodes/${episodeId}/suspects/${suspectId}`));
  },
  async updateEvidence(episodeId, evidenceId, payload) {
    return unwrap(await apiClient.put(`/v1/admin/episodes/${episodeId}/evidences/${evidenceId}`, payload));
  },
  async createEvidence(episodeId, payload) {
    return unwrap(await apiClient.post(`/v1/admin/episodes/${episodeId}/evidences`, payload));
  },
  async deleteEvidence(episodeId, evidenceId) {
    return unwrap(await apiClient.delete(`/v1/admin/episodes/${episodeId}/evidences/${evidenceId}`));
  },
  async updatePartnerReward(episodeId, rewardId, payload) {
    return unwrap(await apiClient.put(`/v1/admin/episodes/${episodeId}/partner-rewards/${rewardId}`, payload));
  },
  async validateRewardPayload(episodeId, rewardPayload) {
    return unwrap(await apiClient.post(`/v1/admin/episodes/${episodeId}/reward-payload/validate`, { rewardPayload }));
  },
  async createAiDraft(payload) {
    return unwrap(await apiClient.post('/v1/admin/episodes/ai-draft', payload));
  },
  async createGeminiDraft(payload) {
    return unwrap(await apiClient.post('/v1/admin/episodes/ai-draft/gemini', payload));
  },
  async validateAiDraft(payload) {
    return unwrap(await apiClient.post('/v1/admin/episodes/ai-draft/validate', payload));
  },
  async saveAiDraft(payload) {
    return unwrap(await apiClient.post('/v1/admin/episodes/ai-draft/save', payload));
  }
};
