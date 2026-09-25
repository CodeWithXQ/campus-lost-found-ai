import request from './request'

export const authApi = {
  login: (data) => request.post('/auth/login', data),
  register: (data) => request.post('/auth/register', data),
  me: () => request.get('/auth/me'),
  updateProfile: (data) => request.put('/auth/me', data)
}

export const postApi = {
  list: (params) => request.get('/post/list', { params }),
  detail: (id) => request.get(`/post/detail/${id}`),
  my: (params) => request.get('/post/my', { params }),
  publish: (data) => request.post('/post', data),
  update: (id, data) => request.put(`/post/${id}`, data),
  remove: (id) => request.delete(`/post/${id}`),
  archive: (id) => request.post(`/post/${id}/archive`),
  categories: () => request.get('/post/categories'),
  search: (text) => request.post('/post/search', { text })
}

export const matchApi = {
  forPost: (postId) => request.get(`/match/post/${postId}`),
  my: () => request.get('/match/my'),
  run: (postId) => request.post(`/match/run/${postId}`),
  ignore: (matchId) => request.post(`/match/ignore/${matchId}`)
}

export const claimApi = {
  apply: (data) => request.post('/claim/apply', data),
  my: (params) => request.get('/claim/my', { params }),
  detail: (id) => request.get(`/claim/${id}`),
  confirm: (id) => request.post(`/claim/${id}/confirm`),
  reject: (id) => request.post(`/claim/${id}/reject`)
}

export const notifyApi = {
  list: (params) => request.get('/notification/list', { params }),
  unreadCount: (params) => request.get('/notification/unread-count', { params }),
  read: (id) => request.put(`/notification/read/${id}`),
  readAll: () => request.put('/notification/read-all')
}

export const statsApi = {
  overview: () => request.get('/stats/overview'),
  category: () => request.get('/stats/category'),
  location: () => request.get('/stats/location'),
  trend: () => request.get('/stats/trend'),
  successRate: () => request.get('/stats/success-rate')
}

export const aiApi = {
  classify: (formData) => request.post('/ai/classify', formData),
  health: () => request.get('/ai/health')
}

export const fileApi = {
  upload: (formData) => request.post('/file/upload', formData)
}

export const contactApi = {
  contact: (postId, otherPostId) => request.post('/contact', null, { params: { postId, otherPostId } })
}

export const chatApi = {
  send: (data) => request.post('/chat/send', data),
  history: (peerId, postId) => request.get('/chat/history', { params: { peerId, postId } }),
  conversations: () => request.get('/chat/conversations'),
  unreadCount: () => request.get('/chat/unread-count'),
  markRead: (peerId, postId) => request.put('/chat/read', null, { params: { peerId, postId } })
}

export const adminApi = {
  posts: (params) => request.get('/admin/posts', { params }),
  pendingPosts: (params) => request.get('/admin/posts/pending', { params }),
  pendingCount: () => request.get('/admin/posts/pending/count'),
  approvePost: (id) => request.post(`/admin/post/${id}/approve`),
  rejectPost: (id, reason) => request.post(`/admin/post/${id}/reject`, null, { params: { reason } }),
  archivePost: (id) => request.post(`/admin/post/${id}/archive`),
  relistPost: (id) => request.post(`/admin/post/${id}/relist`),
  deletePost: (id) => request.delete(`/admin/post/${id}`),
  users: (params) => request.get('/admin/users', { params }),
  claims: (params) => request.get('/admin/claims', { params }),
  disableUser: (id) => request.post(`/admin/user/${id}/disable`),
  enableUser: (id) => request.post(`/admin/user/${id}/enable`),
  deleteUser: (id) => request.delete(`/admin/user/${id}`)
}

export const subscriptionApi = {
  add: (data) => request.post('/subscription', data),
  my: () => request.get('/subscription/my'),
  remove: (id) => request.delete(`/subscription/${id}`)
}
