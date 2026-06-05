import apiClient from '@/api/axiosInstance';

const unwrap = (response) => response.data?.data ?? response.data;

export const caseFileApi = {
  async getCaseFile(episodeId) {
    return unwrap(await apiClient.get(`/v1/episodes/${episodeId}/case-file`));
  }
};