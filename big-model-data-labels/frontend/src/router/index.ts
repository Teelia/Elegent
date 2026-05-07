import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { ElMessage } from 'element-plus'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/datasets' },
    { path: '/login', component: () => import('../views/LoginView.vue') },
    // 数据集路由
    { path: '/datasets', component: () => import('../views/DatasetsView.vue') },
    { path: '/datasets/:id', component: () => import('../views/DatasetDetailView.vue') },
    // 分析任务路由（使用新的分析任务API）
    { path: '/tasks', component: () => import('../views/TasksView.vue') },
    { path: '/builtin-labels', component: () => import('../views/BuiltinLabelsView.vue') },
    { path: '/extractors', component: () => import('../views/ExtractorsView.vue') },
    { path: '/admin/prompts', component: () => import('../views/PromptManagementView.vue') },
    { path: '/sync-configs', component: () => import('../views/SyncConfigsView.vue') },
    { path: '/data-sources', component: () => import('../views/DataSourcesView.vue') },
    { path: '/admin/users', component: () => import('../views/AdminUsersView.vue') },
    { path: '/admin/model-config', component: () => import('../views/ModelConfigView.vue') },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (to.path === '/login') {
    return true
  }
  if (!auth.accessToken) {
    return '/login'
  }
  if (!auth.user) {
    try {
      await auth.loadMe()
    } catch {
      return '/login'
    }
  }

  if (to.path.startsWith('/admin') && auth.user?.role !== 'admin') {
    ElMessage.error('仅管理员可访问')
    return '/tasks'
  }
  return true
})

export default router

