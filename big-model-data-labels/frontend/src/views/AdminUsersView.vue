<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import type { AdminUser, UserRole } from '../api/adminUsers'
import * as adminUsersApi from '../api/adminUsers'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const currentUserId = computed(() => auth.user?.id)

const loading = ref(false)
const page = ref(1)
const size = ref(10)
const total = ref(0)
const items = ref<AdminUser[]>([])

const keyword = ref('')

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editing = ref<AdminUser | null>(null)
const form = reactive({
  username: '',
  password: '',
  role: 'normal' as UserRole,
  email: '',
  fullName: '',
  isActive: true,
})

const resetVisible = ref(false)
const resetUser = ref<AdminUser | null>(null)
const resetForm = reactive({
  newPassword: '',
})

async function fetchList() {
  loading.value = true
  try {
    const resp = await adminUsersApi.listUsers({
      page: page.value,
      size: size.value,
      keyword: keyword.value.trim() || undefined,
    })
    items.value = resp.items
    total.value = resp.total
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  dialogMode.value = 'create'
  editing.value = null
  Object.assign(form, {
    username: '',
    password: '',
    role: 'normal',
    email: '',
    fullName: '',
    isActive: true,
  })
  dialogVisible.value = true
}

function openEdit(row: AdminUser) {
  dialogMode.value = 'edit'
  editing.value = row
  Object.assign(form, {
    username: row.username,
    password: '',
    role: row.role,
    email: row.email || '',
    fullName: row.fullName || '',
    isActive: !!row.isActive,
  })
  dialogVisible.value = true
}

async function submit() {
  try {
    if (dialogMode.value === 'create') {
      if (!form.username.trim() || !form.password.trim()) {
        ElMessage.warning('请输入用户名和密码')
        return
      }
      await adminUsersApi.createUser({
        username: form.username.trim(),
        password: form.password,
        role: form.role,
        email: form.email.trim() || undefined,
        fullName: form.fullName.trim() || undefined,
        isActive: form.isActive,
      })
      ElMessage.success('创建成功')
    } else if (editing.value) {
      await adminUsersApi.updateUser(editing.value.id, {
        role: form.role,
        email: form.email.trim() || undefined,
        fullName: form.fullName.trim() || undefined,
        isActive: form.isActive,
      })
      ElMessage.success('更新成功')
    }
    dialogVisible.value = false
    await fetchList()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  }
}

async function toggleActive(row: AdminUser, next: boolean) {
  try {
    if (row.id === currentUserId.value) {
      ElMessage.warning('不允许修改当前登录账号状态')
      return
    }
    await adminUsersApi.updateUser(row.id, {
      role: row.role,
      email: row.email,
      fullName: row.fullName,
      isActive: next,
    })
    row.isActive = next
    ElMessage.success('已更新')
  } catch (e: any) {
    ElMessage.error(e?.message || '更新失败')
    await fetchList()
  }
}

function openReset(row: AdminUser) {
  resetUser.value = row
  resetForm.newPassword = ''
  resetVisible.value = true
}

async function submitReset() {
  if (!resetUser.value) return
  try {
    if (!resetForm.newPassword.trim()) {
      ElMessage.warning('请输入新密码')
      return
    }
    await adminUsersApi.resetPassword(resetUser.value.id, { newPassword: resetForm.newPassword })
    ElMessage.success('密码已重置')
    resetVisible.value = false
  } catch (e: any) {
    ElMessage.error(e?.message || '重置失败')
  }
}

async function confirmPromoteAdmin(row: AdminUser) {
  try {
    await ElMessageBox.confirm(`确认将「${row.username}」设为管理员？`, '提示', { type: 'warning' })
    await adminUsersApi.updateUser(row.id, {
      role: 'admin',
      email: row.email,
      fullName: row.fullName,
      isActive: row.isActive,
    })
    ElMessage.success('已更新')
    await fetchList()
  } catch (e: any) {
    if (e === 'cancel') return
    ElMessage.error(e?.message || '更新失败')
  }
}

onMounted(fetchList)
</script>

<template>
  <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px;gap:12px;flex-wrap:wrap">
    <h2 style="margin:0">用户管理</h2>
    <div style="display:flex;gap:8px;align-items:center;flex-wrap:wrap;justify-content:flex-end">
      <el-input v-model="keyword" placeholder="搜索用户名/邮箱" style="width: 240px" clearable @keyup.enter="fetchList" />
      <el-button @click="fetchList">搜索</el-button>
      <el-button type="primary" @click="openCreate">新建用户</el-button>
    </div>
  </div>

  <el-table :data="items" v-loading="loading" style="width: 100%">
    <el-table-column prop="username" label="用户名" min-width="140" />
    <el-table-column prop="role" label="角色" width="110">
      <template #default="{ row }">
        <el-tag :type="row.role === 'admin' ? 'danger' : 'info'">{{ row.role }}</el-tag>
      </template>
    </el-table-column>
    <el-table-column prop="email" label="邮箱" min-width="180" />
    <el-table-column prop="fullName" label="全名" min-width="160" />
    <el-table-column label="启用" width="110">
      <template #default="{ row }">
        <el-switch
          :model-value="row.isActive"
          :disabled="row.id === currentUserId"
          @change="(v:any)=>toggleActive(row, !!v)"
        />
      </template>
    </el-table-column>
    <el-table-column prop="lastLogin" label="最后登录" width="180" />
    <el-table-column prop="createdAt" label="创建时间" width="180" />
    <el-table-column label="操作" width="320">
      <template #default="{ row }">
        <el-button size="small" @click="openEdit(row)">编辑</el-button>
        <el-button size="small" :disabled="row.id === currentUserId" @click="openReset(row)">重置密码</el-button>
        <el-button size="small" :disabled="row.role === 'admin'" type="warning" @click="confirmPromoteAdmin(row)">
          设为管理员
        </el-button>
      </template>
    </el-table-column>
  </el-table>

  <div style="margin-top: 12px; display:flex; justify-content:flex-end">
    <el-pagination
      v-model:current-page="page"
      v-model:page-size="size"
      :total="total"
      layout="prev, pager, next, sizes, total"
      @current-change="fetchList"
      @size-change="fetchList"
    />
  </div>

  <el-dialog v-model="dialogVisible" :title="dialogMode === 'create' ? '新建用户' : '编辑用户'" width="560px">
    <el-form label-width="110px">
      <el-form-item label="用户名">
        <el-input v-model="form.username" :disabled="dialogMode === 'edit'" />
      </el-form-item>
      <el-form-item v-if="dialogMode === 'create'" label="初始密码">
        <el-input v-model="form.password" type="password" show-password />
      </el-form-item>
      <el-form-item label="角色">
        <el-select v-model="form.role" style="width: 100%" :disabled="editing?.id === currentUserId">
          <el-option label="normal" value="normal" />
          <el-option label="admin" value="admin" />
        </el-select>
      </el-form-item>
      <el-form-item label="邮箱">
        <el-input v-model="form.email" />
      </el-form-item>
      <el-form-item label="全名">
        <el-input v-model="form.fullName" />
      </el-form-item>
      <el-form-item label="是否启用">
        <el-switch v-model="form.isActive" :disabled="editing?.id === currentUserId" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" @click="submit">保存</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="resetVisible" title="重置密码" width="480px">
    <div style="margin-bottom: 10px; color:#666">用户：{{ resetUser?.username }}</div>
    <el-form label-width="90px">
      <el-form-item label="新密码">
        <el-input v-model="resetForm.newPassword" type="password" show-password />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="resetVisible = false">取消</el-button>
      <el-button type="primary" :disabled="!resetForm.newPassword" @click="submitReset">确定</el-button>
    </template>
  </el-dialog>
</template>

