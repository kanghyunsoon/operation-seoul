import apiClient from '@/api/axiosInstance';

const unwrap = (response) => response.data?.data ?? response.data;

export const reviewApi = {
  async getEpisodeReviews(episodeId) {
    return unwrap(await apiClient.get(`/v1/episodes/${episodeId}/reviews`));
  },
  async createEpisodeReview(episodeId, payload) {
    return unwrap(await apiClient.post(`/v1/episodes/${episodeId}/reviews`, payload));
  },
  async updateReview(reviewId, payload) {
    return unwrap(await apiClient.put(`/v1/reviews/${reviewId}`, payload));
  },
  async deleteReview(reviewId) {
    return unwrap(await apiClient.delete(`/v1/reviews/${reviewId}`));
  },
  async createReviewComment(reviewId, payload) {
    return unwrap(await apiClient.post(`/v1/reviews/${reviewId}/comments`, payload));
  },
  async updateReviewComment(commentId, payload) {
    return unwrap(await apiClient.put(`/v1/review-comments/${commentId}`, payload));
  },
  async deleteReviewComment(commentId) {
    return unwrap(await apiClient.delete(`/v1/review-comments/${commentId}`));
  },
  async getMyReviews() {
    return unwrap(await apiClient.get('/v1/users/me/reviews'));
  }
};
