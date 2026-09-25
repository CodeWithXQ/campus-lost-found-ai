import { defineStore } from 'pinia'
import { authApi } from '../api'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    user: JSON.parse(localStorage.getItem('user') || 'null')
  }),
  getters: {
    isLogin: (s) => !!s.token,
    isAdmin: (s) => s.user?.role === 'ADMIN'
  },
  actions: {
    async login(form) {
      const data = await authApi.login(form)
      this.token = data.token
      this.user = data.user
      localStorage.setItem('token', data.token)
      localStorage.setItem('user', JSON.stringify(data.user))
    },
    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem('token')
      localStorage.removeItem('user')
    },
    async fetchMe() {
      try {
        this.user = await authApi.me()
        localStorage.setItem('user', JSON.stringify(this.user))
      } catch (e) {
        /* 忽略 */
      }
    },
    async updateProfile(data) {
      this.user = await authApi.updateProfile(data)
      localStorage.setItem('user', JSON.stringify(this.user))
    }
  }
})
