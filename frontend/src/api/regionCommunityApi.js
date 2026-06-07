import apiClient from '@/api/axiosInstance';

const unwrap = (response) => response.data?.data ?? response.data;

export const regionCommunityApi = {
  async getReviews(regionId, params = {}) {
    return unwrap(await apiClient.get(`/v1/regions/${regionId}/reviews`, { params }));
  },
  async createReview(regionId, payload) {
    return unwrap(await apiClient.post(`/v1/regions/${regionId}/reviews`, payload));
  },
  async updateReview(regionId, reviewId, payload) {
    return unwrap(await apiClient.put(`/v1/regions/${regionId}/reviews/${reviewId}`, payload));
  },
  async deleteReview(regionId, reviewId) {
    return unwrap(await apiClient.delete(`/v1/regions/${regionId}/reviews/${reviewId}`));
  },
  async toggleReviewLike(regionId, reviewId) {
    return unwrap(await apiClient.post(`/v1/regions/${regionId}/reviews/${reviewId}/like`));
  },
  async getQuestions(regionId) {
    return unwrap(await apiClient.get(`/v1/regions/${regionId}/questions`));
  },
  async createQuestion(regionId, payload) {
    return unwrap(await apiClient.post(`/v1/regions/${regionId}/questions`, payload));
  },
  async deleteQuestion(regionId, questionId) {
    return unwrap(await apiClient.delete(`/v1/regions/${regionId}/questions/${questionId}`));
  },
  async toggleQuestionLike(regionId, questionId) {
    return unwrap(await apiClient.post(`/v1/regions/${regionId}/questions/${questionId}/like`));
  },
  async createAnswer(regionId, questionId, payload) {
    return unwrap(await apiClient.post(`/v1/regions/${regionId}/questions/${questionId}/answers`, payload));
  },
  async deleteAnswer(regionId, questionId, answerId) {
    return unwrap(await apiClient.delete(`/v1/regions/${regionId}/questions/${questionId}/answers/${answerId}`));
  }
};
