import { defineStore } from 'pinia'
import * as authApi from '../api/auth'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    accessToken: localStorage.getItem('accessToken') || '',
    user: (localStorage.getItem('user') ? JSON.parse(localStorage.getItem('user') as string) : null) as authApi.User | null,
  }),
  actions: {
    async login(username: string, password: string) {
      const resp = await authApi.login(username, password)
      this.accessToken = resp.accessToken
      this.user = resp.user
      localStorage.setItem('accessToken', resp.accessToken)
      localStorage.setItem('user', JSON.stringify(resp.user))
    },
    async loadMe() {
      if (!this.accessToken) return
      const u = await authApi.me()
      this.user = u
      localStorage.setItem('user', JSON.stringify(u))
    },
    async logout() {
      try {
        await authApi.logout()
      } finally {
        this.accessToken = ''
        this.user = null
        localStorage.removeItem('accessToken')
        localStorage.removeItem('user')
      }
    },
  },
})

