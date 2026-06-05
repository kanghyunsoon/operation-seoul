import apiClient from '@/api/axiosInstance';

const unwrap = (response) => response.data?.data ?? response.data;

export const adminReviewApi = {
  async getReviews(params = {}) {
    return unwrap(await apiClient.get('/v1/admin/reviews', { params }));
  },
  async getReview(reviewId) {
    return unwrap(await apiClient.get(`/v1/admin/reviews/${reviewId}`));
  },
  async hideReview(reviewId) {
    return unwrap(await apiClient.put(`/v1/admin/reviews/${reviewId}/hide`));
  },
  async restoreReview(reviewId) {
    return unwrap(await apiClient.put(`/v1/admin/reviews/${reviewId}/restore`));
  },
  async deleteReview(reviewId) {
    return unwrap(await apiClient.delete(`/v1/admin/reviews/${reviewId}`));
  }
};