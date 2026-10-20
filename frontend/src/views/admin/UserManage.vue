<template>
  <div class="user-manage">
    <div class="content">
      <h1 class="page-title">用户管理</h1>
      <p class="page-sub">
        管理系统注册用户，可禁用或启用账号。管理员账号与当前登录账号不可操作。
      </p>
      <el-card>
        <template #header>
          <div style="display:flex;justify-content:space-between;align-items:center">
            <span>用户列表</span>
            <div style="display:flex;align-items:center;gap:10px">
              <el-select v-model="filterRole" placeholder="角色" clearable style="width:130px">
                <el-option label="普通用户" value="USER" />
                <el-option label="管理员" value="ADMIN" />
              </el-select>
              <el-select v-model="filterStatus" placeholder="状态" clearable style="width:120px">
                <el-option label="正常" :value="1" />
                <el-option label="已禁用" :value="0" />
              </el-select>
              <el-button size="small" @click="fetchUsers">刷新</el-button>
            </div>
          </div>
        </template>
        <el-table :data="filteredUsers" v-loading="loading" empty-text="暂无用户">
          <el-table-column prop="username" label="用户名" min-width="150" />
          <el-table-column label="姓名" width="120">
            <template #default="{ row }">{{ row.realName || '-' }}</template>
          </el-table-column>
          <el-table-column label="联系方式" width="150">
            <template #default="{ row }">{{ row.phone || row.email || '-' }}</template>
          </el-table-column>
          <el-table-column label="角色" width="110">
            <template #default="{ row }">
              <el-tag :type="roleTagType(row.role)" size="small">{{ roleLabel(row.role) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
                {{ row.status === 1 ? '正常' : '已禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="注册时间" width="180">
            <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <template v-if="row.role === 'ADMIN'">
                <span class="admin-note">管理员账号不可修改</span>
              </template>
              <template v-else-if="row.id === currentUserId">
                <span class="admin-note">当前登录账号</span>
              </template>
              <el-button v-else size="small" :type="row.status === 1 ? 'danger' : 'success'"
                         @click="handleToggleStatus(row)">
                {{ row.status === 1 ? '禁用' : '启用' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../utils/request'
import { useAuthStore } from '../../stores/auth'

interface AdminUser {
  id: number
  username: string
  realName: string
  phone: string
  email: string
  role: string
  status: number
  createdAt: string
}

const authStore = useAuthStore()
const users = ref<AdminUser[]>([])
const loading = ref(false)
const currentUserId = ref<number | null>(authStore.user?.id ?? null)

/** 角色筛选 / 状态筛选（列表全量返回，前端过滤即可，避免新增后端筛选参数） */
const filterRole = ref('')
const filterStatus = ref<number | null>(null)

const filteredUsers = computed(() => {
  let list = users.value
  if (filterRole.value) list = list.filter(u => u.role === filterRole.value)
  if (filterStatus.value !== null && filterStatus.value !== undefined) {
    list = list.filter(u => u.status === filterStatus.value)
  }
  return list
})

/** 角色中文：USER→普通用户 / ADMIN→管理员 */
function roleLabel(r: string) {
  const m: Record<string, string> = { USER: '普通用户', ADMIN: '管理员' }
  return m[r] || r || '未知'
}
function roleTagType(r: string): 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  if (r === 'ADMIN') return 'danger'
  if (r === 'USER') return 'primary'
  return 'info'
}

function formatTime(iso: string) {
  return iso ? iso.replace('T', ' ').slice(0, 19) : '-'
}

async function fetchUsers() {
  loading.value = true
  try {
    const res = await request.get('/admin/users')
    users.value = res.data.data
  } finally {
    loading.value = false
  }
}

async function handleToggleStatus(row: AdminUser) {
  const newStatus = row.status === 1 ? 0 : 1
  const action = newStatus === 0 ? '禁用' : '启用'
  try {
    await ElMessageBox.confirm(
      `确认${action}用户“${row.username}”吗？${newStatus === 0 ? '该账号将无法登录系统。' : ''}`,
      `${action}确认`,
      { confirmButtonText: `确认${action}`, cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return // 用户取消
  }
  await request.put(`/admin/users/${row.id}/status`, { status: newStatus })
  ElMessage.success(`已${action}用户“${row.username}”`)
  await fetchUsers()
}

onMounted(fetchUsers)
</script>

<style scoped>
.user-manage {
  min-height: 100vh;
  background: #f5f7fa;
}
.content {
  padding: 20px;
  max-width: 1080px;
  margin: 0 auto;
}
.page-sub {
  margin: -12px 0 16px;
  font-size: 13px;
  color: #606266;
  line-height: 1.6;
}
.admin-note {
  font-size: 12px;
  color: #909399;
}
</style>
