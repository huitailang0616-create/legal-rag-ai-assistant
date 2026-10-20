import { createRouter, createWebHistory } from 'vue-router'
import Home from '../views/Home.vue'
import { useAuthStore } from '../stores/auth'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: Home,
    meta: { requiresAuth: true }
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue')
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('../views/Register.vue')
  },
  {
    path: '/profile',
    name: 'Profile',
    component: () => import('../views/Profile.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/consult',
    name: 'Consult',
    component: () => import('../views/Consult.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/consultations',
    name: 'ConsultationHistory',
    component: () => import('../views/ConsultationHistory.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/case-analysis',
    name: 'CaseAnalysis',
    component: () => import('../views/CaseAnalysis.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/legal-writing',
    name: 'LegalWriting',
    component: () => import('../views/LegalWriting.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/admin/users',
    name: 'UserManage',
    component: () => import('../views/admin/UserManage.vue'),
    meta: { requiresAuth: true, requiresAdmin: true }
  },
  {
    path: '/admin/legal',
    name: 'LegalManage',
    component: () => import('../views/admin/LegalManage.vue'),
    meta: { requiresAuth: true, requiresAdmin: true }
  },
  {
    path: '/admin/evaluation',
    name: 'EvaluationManage',
    component: () => import('../views/admin/EvaluationManage.vue'),
    meta: { requiresAuth: true, requiresAdmin: true }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to, _from, next) => {
  const token = localStorage.getItem('token')

  if (to.meta.requiresAuth && !token) {
    next('/login')
    return
  }

  if ((to.path === '/login' || to.path === '/register') && token) {
    next('/')
    return
  }

  if (to.meta.requiresAdmin && token) {
    const authStore = useAuthStore()
    try {
      if (!authStore.user) {
        await authStore.fetchProfile()
      }
      if (authStore.user?.role !== 'ADMIN') {
        next('/')
        return
      }
    } catch {
      authStore.logout()
      next('/login')
      return
    }
  }

  next()
})

export default router
