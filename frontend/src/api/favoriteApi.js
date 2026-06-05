import apiClient from '@/api/axiosInstance';

const unwrap = (response) => response.data?.data ?? response.data;

export const favoriteApi = {
  async addFavorite(episodeId) {
    return unwrap(await apiClient.post(`/v1/episodes/${episodeId}/favorite`));
  },
  async removeFavorite(episodeId) {
    return unwrap(await apiClient.delete(`/v1/episodes/${episodeId}/favorite`));
  },
  async getMyFavorites() {
    return unwrap(await apiClient.get('/v1/users/me/favorites'));
  }
};
