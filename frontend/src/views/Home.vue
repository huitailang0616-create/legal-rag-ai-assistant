<template>
  <div class="home-page">
    <!-- Hero -->
    <section class="hero">
      <h1>AI 智能法律咨询助手</h1>
      <p class="hero-sub">
        面向劳动争议、房屋租赁、消费者权益，提供法规检索、案件分析与法律文书辅助。
      </p>
      <div class="hero-actions">
        <el-button type="primary" size="large" @click="$router.push('/consult')">
          开始法律咨询
        </el-button>
        <el-button size="large" plain @click="$router.push('/consultations')">
          查看咨询记录
        </el-button>
        <el-button size="large" plain @click="$router.push('/case-analysis')">
          查看案件分析
        </el-button>
      </div>
    </section>

    <!-- 核心功能 -->
    <section class="section">
      <h2 class="section-title">核心功能</h2>
      <el-row :gutter="20" class="feature-row">
        <el-col v-for="f in features" :key="f.path" :xs="24" :sm="12" :lg="6">
          <el-card class="feature-card" shadow="hover" @click="$router.push(f.path)">
            <div class="feature-icon">
              <el-icon :size="28"><component :is="f.icon" /></el-icon>
            </div>
            <div class="feature-title">{{ f.title }}</div>
            <div class="feature-desc">{{ f.desc }}</div>
            <el-button type="primary" plain size="small">{{ f.btn }}</el-button>
          </el-card>
        </el-col>
      </el-row>
    </section>

    <!-- 支持领域 -->
    <section class="section">
      <h2 class="section-title">当前支持的法律领域</h2>
      <div class="domain-row">
        <el-tag v-for="d in domains" :key="d.name" :type="d.type" size="large" effect="light" round>
          {{ d.name }}
        </el-tag>
      </div>
      <p class="domain-note">当前系统聚焦以上三个领域，其他法律问题可能提示暂不支持。</p>
    </section>

    <!-- 管理专区（仅 ADMIN） -->
    <section v-if="authStore.isAdmin" class="section">
      <div class="admin-zone">
        <h2 class="section-title admin-title">管理功能</h2>
        <p class="admin-note">管理员专区，仅管理员可见。</p>
        <el-row :gutter="20">
          <el-col v-for="m in adminMenus" :key="m.path" :xs="24" :sm="8">
            <el-card class="admin-card" shadow="hover" @click="$router.push(m.path)">
              <div class="admin-card-icon">
                <el-icon :size="24"><component :is="m.icon" /></el-icon>
              </div>
              <div class="feature-title">{{ m.title }}</div>
              <div class="feature-desc">{{ m.desc }}</div>
            </el-card>
          </el-col>
        </el-row>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  ChatDotRound,
  Tickets,
  FolderOpened,
  Document,
  User,
  Collection,
  DataAnalysis,
} from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const features = [
  {
    icon: ChatDotRound,
    title: '智能法律咨询',
    desc: '根据问题检索相关法规，提供有依据的法律信息与风险提示。',
    btn: '开始咨询',
    path: '/consult',
  },
  {
    icon: Tickets,
    title: '咨询记录',
    desc: '查看自己的历史咨询、法律依据和回答记录。',
    btn: '查看记录',
    path: '/consultations',
  },
  {
    icon: FolderOpened,
    title: '案件分析',
    desc: '结构化录入案情，AI 辅助提取诉求与风险。',
    btn: '分析案件',
    path: '/case-analysis',
  },
  {
    icon: Document,
    title: '法律文书',
    desc: '基于确认后的案件信息，辅助生成和编辑法律文书。',
    btn: '创建文书',
    path: '/legal-writing',
  },
]

const domains = [
  { name: '劳动争议', type: 'warning' as const },
  { name: '房屋租赁', type: 'success' as const },
  { name: '消费者权益', type: 'primary' as const },
]

const adminMenus = [
  { icon: User, title: '用户管理', desc: '管理平台用户账户与启用状态', path: '/admin/users' },
  { icon: Collection, title: '法律知识库', desc: '管理法律知识文档与检索数据', path: '/admin/legal' },
  { icon: DataAnalysis, title: '系统评测与运行管理', desc: '评测运行记录与系统状态管理', path: '/admin/evaluation' },
]

onMounted(async () => {
  if (authStore.token && !authStore.user) {
    try {
      await authStore.fetchProfile()
    } catch {
      authStore.logout()
      router.push('/login')
    }
  }
})
</script>

<style scoped>
.home-page {
  min-height: 100vh;
  background: #f5f7fa;
}

.hero {
  max-width: var(--app-content-max);
  margin: 24px auto 0;
  text-align: center;
  background: #fff;
  border-radius: 8px;
  padding: 56px 24px;
}

.hero h1 {
  margin: 0 0 12px;
  font-size: 30px;
  font-weight: 600;
  color: #303133;
}

.hero-sub {
  margin: 0 0 28px;
  font-size: 15px;
  color: #606266;
  line-height: 1.7;
}

.hero-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
  flex-wrap: wrap;
}

.section {
  max-width: var(--app-content-max);
  margin: 0 auto;
  padding: 24px;
}

.section-title {
  margin: 0 0 16px;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.feature-row {
  row-gap: 16px;
}

.feature-card {
  cursor: pointer;
  text-align: left;
}

.feature-card :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}

.feature-icon {
  color: #409eff;
  margin-bottom: 14px;
}

.feature-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
}

.feature-desc {
  font-size: 13px;
  color: #909399;
  line-height: 1.6;
  margin-bottom: 16px;
  min-height: 42px;
}

.domain-row {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.domain-note {
  margin: 14px 0 0;
  font-size: 13px;
  color: #909399;
}

.admin-zone {
  background: #ecf5ff;
  border-radius: 8px;
  padding: 20px;
}

.admin-title {
  margin-bottom: 4px;
}

.admin-note {
  margin: 0 0 16px;
  font-size: 13px;
  color: #606266;
}

.admin-card {
  cursor: pointer;
}

.admin-card-icon {
  color: #409eff;
  margin-bottom: 12px;
}
</style>
