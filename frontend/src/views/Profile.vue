<template>
  <div class="profile-container">
    <el-card class="profile-card">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <h2 style="margin:0">个人资料</h2>
          <el-button @click="$router.push('/')">返回首页</el-button>
        </div>
      </template>
      <el-form :model="form" label-width="100px" size="large">
        <el-form-item label="用户名">
          <el-input :model-value="user?.username" disabled />
        </el-form-item>
        <el-form-item label="角色">
          <el-tag :type="user?.role === 'ADMIN' ? 'danger' : 'primary'">{{ user?.role }}</el-tag>
        </el-form-item>
        <el-form-item label="真实姓名">
          <el-input v-model="form.realName" placeholder="请输入真实姓名" :disabled="!editing" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" placeholder="请输入手机号" :disabled="!editing" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" placeholder="请输入邮箱" :disabled="!editing" />
        </el-form-item>
        <el-form-item label="注册时间">
          <span>{{ user?.createdAt }}</span>
        </el-form-item>
        <el-form-item>
          <template v-if="!editing">
            <el-button type="primary" @click="startEdit">编辑</el-button>
          </template>
          <template v-else>
            <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
            <el-button @click="cancelEdit">取消</el-button>
          </template>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { ElMessage } from 'element-plus'

const router = useRouter()
const authStore = useAuthStore()
const user = computed(() => authStore.user)
const editing = ref(false)
const saving = ref(false)

const form = reactive({
  realName: '',
  phone: '',
  email: ''
})

const originalForm = {
  realName: '',
  phone: '',
  email: ''
}

function syncFormFromUser() {
  form.realName = user.value?.realName || ''
  form.phone = user.value?.phone || ''
  form.email = user.value?.email || ''
}

onMounted(async () => {
  if (!authStore.user) {
    try {
      await authStore.fetchProfile()
    } catch {
      authStore.logout()
      router.push('/login')
      return
    }
  }
  syncFormFromUser()
})

function startEdit() {
  syncFormFromUser()
  originalForm.realName = form.realName
  originalForm.phone = form.phone
  originalForm.email = form.email
  editing.value = true
}

function cancelEdit() {
  form.realName = originalForm.realName
  form.phone = originalForm.phone
  form.email = originalForm.email
  editing.value = false
}

async function handleSave() {
  saving.value = true
  try {
    await authStore.updateProfile({
      realName: form.realName || undefined,
      phone: form.phone || undefined,
      email: form.email || undefined
    })
    await authStore.fetchProfile()
    syncFormFromUser()
    ElMessage.success('修改成功')
    editing.value = false
  } catch (e: any) {
    if (e?.response?.data?.message) {
      ElMessage.error(e.response.data.message)
    }
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.profile-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: #f5f7fa;
}
.profile-card {
  width: 520px;
}
</style>
