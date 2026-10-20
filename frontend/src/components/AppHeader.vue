<template>
  <header class="app-header">
    <div class="app-header__inner">
      <div class="app-header__brand" @click="$router.push('/')">
        <el-icon class="brand-icon"><Reading /></el-icon>
        <span class="brand-name">AI 智能法律咨询</span>
      </div>

      <nav class="app-header__nav">
        <router-link
          v-for="item in navItems"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          :class="{ active: isActive(item) }"
        >
          {{ item.label }}
        </router-link>
      </nav>

      <div class="app-header__user">
        <span class="user-name" :title="displayName">{{ displayName }}</span>
        <el-tag v-if="authStore.isAdmin" type="danger" size="small">管理员</el-tag>
        <router-link to="/profile" class="user-link">个人资料</router-link>
        <router-link v-if="authStore.isAdmin" to="/admin/users" class="user-link">管理后台</router-link>
        <el-button link type="danger" @click="handleLogout">退出登录</el-button>
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Reading } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const navItems = [
  { path: '/', label: '首页' },
  { path: '/consult', label: '法律咨询' },
  { path: '/consultations', label: '咨询记录' },
  { path: '/case-analysis', label: '案件分析' },
  { path: '/legal-writing', label: '法律文书' },
]

const displayName = computed(() => authStore.user?.realName || authStore.user?.username || '用户')

function isActive(item: { path: string }) {
  if (item.path === '/') return route.path === '/'
  // /consult 与 /consultations 前缀冲突，必须精确匹配首级路由
  if (item.path === '/consult') return route.path === '/consult'
  return route.path.startsWith(item.path)
}

function handleLogout() {
  authStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.app-header {
  height: var(--app-header-height);
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
}

.app-header__inner {
  max-width: var(--app-content-max);
  height: 100%;
  margin: 0 auto;
  padding: 0 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
}

.app-header__brand {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  flex-shrink: 0;
}

.brand-icon {
  font-size: 22px;
  color: #409eff;
}

.brand-name {
  font-size: 17px;
  font-weight: 600;
  color: #303133;
  white-space: nowrap;
}

.app-header__nav {
  display: flex;
  align-items: center;
  gap: 4px;
  flex: 1;
  min-width: 0;
}

.nav-item {
  padding: 6px 14px;
  font-size: 14px;
  color: #606266;
  text-decoration: none;
  border-radius: 4px;
  white-space: nowrap;
  transition: color 0.15s, background-color 0.15s;
}

.nav-item:hover {
  color: #409eff;
  background: #ecf5ff;
}

.nav-item.active {
  color: #409eff;
  font-weight: 600;
}

.app-header__user {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
  flex-shrink: 0;
}

.user-name {
  color: #303133;
  font-weight: 500;
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-link {
  color: #606266;
  text-decoration: none;
  white-space: nowrap;
}

.user-link:hover {
  color: #409eff;
}
</style>
