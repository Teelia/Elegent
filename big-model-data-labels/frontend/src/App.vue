<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from './stores/auth'
import { Folder, List, Connection, User, Setting, SwitchButton, Edit, Coin, Operation, PriceTag } from '@element-plus/icons-vue'
import policeBadge from './assets/police-badge.png'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const isLoginPage = computed(() => route.path === '/login')
const isAdmin = computed(() => auth.user?.role === 'admin')

async function onLogout() {
  try {
    await auth.logout()
  } finally {
    router.replace('/login')
  }
}
</script>

<template>
  <el-container class="app-shell">
    <el-header v-if="!isLoginPage" class="app-header" height="56px">
      <div class="header-left">
        <img :src="policeBadge" alt="警徽" class="logo-badge" />
        <div class="logo-text">
          <div class="app-title">芜湖市公安局</div>
          <div class="app-subtitle">警情分析智能体</div>
        </div>
      </div>

      <div class="header-right">
        <div class="user-info">
          <el-icon :size="18"><User /></el-icon>
          <div class="user-text">
            <div class="user-name">{{ auth.user?.username }}</div>
            <div class="user-role">{{ auth.user?.role === 'admin' ? '管理员' : '普通用户' }}</div>
          </div>
        </div>
        <el-button class="logout-btn" size="default" @click="onLogout" plain>
          <el-icon style="margin-right: 6px"><SwitchButton /></el-icon>
          退出登录
        </el-button>
      </div>
    </el-header>

    <el-container class="app-body">
      <el-aside v-if="!isLoginPage" class="app-sidebar" width="220px">
        <el-menu :default-active="route.path" router class="sidebar-menu">
          <el-menu-item-group>
            <template #title>核心功能</template>
            <el-menu-item index="/datasets">
              <el-icon><Folder /></el-icon>
              <span>数据集</span>
            </el-menu-item>
            <el-menu-item index="/builtin-labels">
              <el-icon><PriceTag /></el-icon>
              <span>内置标签库</span>
            </el-menu-item>
            <el-menu-item index="/extractors">
              <el-icon><Operation /></el-icon>
              <span>提取器配置</span>
            </el-menu-item>
            <el-menu-item index="/tasks">
              <el-icon><List /></el-icon>
              <span>任务列表</span>
            </el-menu-item>
          </el-menu-item-group>

          <el-menu-item-group>
            <template #title>数据管理</template>
            <el-menu-item index="/data-sources">
              <el-icon><Coin /></el-icon>
              <span>数据源管理</span>
            </el-menu-item>
            <el-menu-item index="/sync-configs">
              <el-icon><Connection /></el-icon>
              <span>同步配置</span>
            </el-menu-item>
          </el-menu-item-group>

          <el-menu-item-group v-if="isAdmin">
            <template #title>系统管理</template>
            <el-menu-item index="/admin/users">
              <el-icon><User /></el-icon>
              <span>用户管理</span>
            </el-menu-item>
            <el-menu-item index="/admin/model-config">
              <el-icon><Setting /></el-icon>
              <span>模型配置</span>
            </el-menu-item>
            <el-menu-item index="/admin/prompts">
              <el-icon><Edit /></el-icon>
              <span>提示词管理</span>
            </el-menu-item>
          </el-menu-item-group>
        </el-menu>
      </el-aside>

      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
}

.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--space-5);
  background: var(--bg-surface);
  border-bottom: 1px solid var(--border-color);
}

.header-left {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  overflow: hidden;
}

.logo-badge {
  width: 32px;
  height: 32px;
  object-fit: contain;
  flex-shrink: 0;
}

.logo-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.app-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  line-height: 1.2;
}

.app-subtitle {
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.2;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.header-right {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.user-info {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
  background: var(--bg-muted);
  border-radius: var(--radius-md);
  color: var(--text-secondary);
}

.user-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.user-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
  line-height: 1.2;
}

.user-role {
  font-size: 11px;
  color: var(--text-tertiary);
  line-height: 1.2;
}

.logout-btn {
  border-radius: var(--radius-md) !important;
}

.app-sidebar {
  background: var(--bg-surface) !important;
  border-right: 1px solid var(--border-color);
}

.sidebar-menu {
  padding: var(--space-2);
  background: transparent !important;
}

.sidebar-menu :deep(.el-menu-item-group__title) {
  padding: var(--space-2) var(--space-2);
  color: var(--text-tertiary);
  font-size: 12px;
}

.sidebar-menu :deep(.el-menu-item) {
  margin: 4px 0;
  border-radius: var(--radius-md);
  height: 44px;
  line-height: 44px;
}

.sidebar-menu :deep(.el-menu-item.is-active) {
  background: rgba(22, 119, 255, 0.1);
}

.sidebar-menu :deep(.el-menu-item .el-icon) {
  font-size: 18px;
  margin-right: 10px;
}

.app-main {
  padding: var(--space-5);
  min-height: calc(100vh - 56px);
}

@media (max-width: 768px) {
  .app-header {
    padding: 0 var(--space-3);
  }

  .user-text {
    display: none;
  }

  .app-main {
    padding: var(--space-3);
  }
}
</style>
