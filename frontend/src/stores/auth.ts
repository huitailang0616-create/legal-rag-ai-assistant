import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import request from '../utils/request'

interface UserInfo {
  id: number
  username: string
  realName: string
  phone: string
  email: string
  role: string
  status: number
  createdAt: string
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('token') || '')
  const user = ref<UserInfo | null>(
    JSON.parse(localStorage.getItem('user') || 'null')
  )

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => user.value?.role === 'ADMIN')

  async function login(username: string, password: string) {
    const res = await request.post('/auth/login', { username, password })
    token.value = res.data.data.token
    localStorage.setItem('token', token.value)
    await fetchProfile()
  }

  async function register(form: {
    username: string
    password: string
    realName?: string
    phone?: string
    email?: string
  }) {
    await request.post('/auth/register', form)
  }

  async function fetchProfile() {
    const res = await request.get('/user/profile')
    user.value = res.data.data
    localStorage.setItem('user', JSON.stringify(user.value))
  }

  async function updateProfile(form: {
    realName?: string
    phone?: string
    email?: string
  }) {
    const res = await request.put('/user/profile', form)
    user.value = res.data.data
    localStorage.setItem('user', JSON.stringify(user.value))
  }

  function logout() {
    token.value = ''
    user.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('user')
  }

  return { token, user, isLoggedIn, isAdmin, login, register, fetchProfile, updateProfile, logout }
})
