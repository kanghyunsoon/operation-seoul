import apiClient from '@/api/axiosInstance';

const unwrap = (response) => response.data?.data ?? response.data;

export const groupApi = {
  async getGroups() {
    return unwrap(await apiClient.get('/v1/groups'));
  },
  async getMyGroups() {
    return unwrap(await apiClient.get('/v1/groups/me'));
  },
  async createGroup(payload) {
    return unwrap(await apiClient.post('/v1/groups', payload));
  },
  async joinGroup(groupId) {
    return unwrap(await apiClient.post(`/v1/groups/${groupId}/join`));
  },
  async leaveGroup(groupId) {
    return unwrap(await apiClient.delete(`/v1/groups/${groupId}/join`));
  },
  async getMembers(groupId) {
    return unwrap(await apiClient.get(`/v1/groups/${groupId}/members`));
  }
};
