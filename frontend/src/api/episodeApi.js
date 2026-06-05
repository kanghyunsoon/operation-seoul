import apiClient from '@/api/axiosInstance';

const unwrap = (response) => response.data?.data ?? response.data;

export const episodeApi = {
  async listEpisodes() {
    return unwrap(await apiClient.get('/v1/episodes'));
  },
  async getEpisode(episodeId) {
    return unwrap(await apiClient.get(`/v1/episodes/${episodeId}`));
  },
  async startEpisode(episodeId) {
    return unwrap(await apiClient.post(`/v1/episodes/${episodeId}/start`));
  },
  async getMap(episodeId) {
    return unwrap(await apiClient.get(`/v1/episodes/${episodeId}/map`));
  },
  async arrive(episodeId, spotId, payload) {
    return unwrap(await apiClient.post(`/v1/episodes/${episodeId}/spots/${spotId}/arrive`, payload));
  },
  async getPuzzle(spotId) {
    return unwrap(await apiClient.get(`/v1/spots/${spotId}/puzzle`));
  },
  async submitPuzzle(puzzleId, answer) {
    return unwrap(await apiClient.post(`/v1/puzzles/${puzzleId}/submit`, { answer }));
  },
  async getClueBoard(episodeId) {
    return unwrap(await apiClient.get(`/v1/episodes/${episodeId}/clue-board`));
  },
  async startDeduction(episodeId) {
    return unwrap(await apiClient.post(`/v1/episodes/${episodeId}/deduction/start`));
  },
  async askDeduction(sessionId, question) {
    return unwrap(await apiClient.post(`/v1/deduction/${sessionId}/ask`, { question }));
  },
  async getDeductionQuestions(sessionId) {
    return unwrap(await apiClient.get(`/v1/deduction/${sessionId}/questions`));
  },
  async submitFinalAnswer(episodeId, sessionId, finalAnswer) {
    return unwrap(await apiClient.post(`/v1/episodes/${episodeId}/final-answer`, { sessionId, finalAnswer }));
  },
  async getClearReport(episodeId) {
    return unwrap(await apiClient.get(`/v1/episodes/${episodeId}/clear-report`));
  },
  async addFavorite(episodeId) {
    return unwrap(await apiClient.post(`/v1/episodes/${episodeId}/favorite`));
  },
  async removeFavorite(episodeId) {
    return unwrap(await apiClient.delete(`/v1/episodes/${episodeId}/favorite`));
  }
};
