import apiClient from '@/api/axiosInstance';
import { regionAreas } from '@/constants/regionAreas';

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
  async getQuestion(regionId, questionId) {
    try {
      return unwrap(await apiClient.get(`/v1/regions/${regionId}/questions/${questionId}`));
    } catch (error) {
      if (![404, 405].includes(error.response?.status)) {
        throw error;
      }
      const sameRegionQuestions = unwrap(await apiClient.get(`/v1/regions/${regionId}/questions`));
      const question = (sameRegionQuestions || []).find((item) => String(item.id) === String(questionId));
      if (question) {
        return question;
      }
      for (const area of regionAreas) {
        if (String(area.regionId) === String(regionId)) continue;
        const questions = unwrap(await apiClient.get(`/v1/regions/${area.regionId}/questions`));
        const matched = (questions || []).find((item) => String(item.id) === String(questionId));
        if (matched) {
          return matched;
        }
      }
      throw error;
    }
  },
  async createQuestion(regionId, payload) {
    return unwrap(await apiClient.post(`/v1/regions/${regionId}/questions`, payload));
  },
  async updateQuestion(regionId, questionId, payload) {
    return unwrap(await apiClient.put(`/v1/regions/${regionId}/questions/${questionId}`, payload));
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
  async updateAnswer(regionId, questionId, answerId, payload) {
    return unwrap(await apiClient.put(`/v1/regions/${regionId}/questions/${questionId}/answers/${answerId}`, payload));
  },
  async deleteAnswer(regionId, questionId, answerId) {
    return unwrap(await apiClient.delete(`/v1/regions/${regionId}/questions/${questionId}/answers/${answerId}`));
  }
};
