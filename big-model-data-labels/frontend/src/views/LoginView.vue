<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Check } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
import policeBadge from '../assets/police-badge.png'

const router = useRouter()
const auth = useAuthStore()

const form = reactive({
  username: 'admin',
  password: 'admin123',
})

const loading = ref(false)

async function onSubmit() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }

  loading.value = true
  try {
    await auth.login(form.username, form.password)
    ElMessage.success('登录成功')
    await router.replace('/tasks')
  } catch (e: any) {
    ElMessage.error(e?.message || '登录失败，请检查用户名或密码')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-container">
    <!-- 背景装饰元素 -->
    <div class="bg-decoration">
      <div class="bg-circle bg-circle-1"></div>
      <div class="bg-circle bg-circle-2"></div>
      <div class="bg-circle bg-circle-3"></div>
    </div>

    <div class="login-content">
      <!-- 左侧品牌区域 -->
      <div class="brand-section">
        <div class="brand-content">
          <div class="brand-logo">
            <img :src="policeBadge" alt="警徽" class="brand-badge" />
          </div>
          <h1 class="brand-title">芜湖市公安局</h1>
          <h2 class="brand-subtitle">智能数据分析系统</h2>
          <p class="brand-description">
            Smart Data Analysis System
          </p>
          <div class="brand-features">
            <div class="feature-item">
              <el-icon><Check /></el-icon>
              <span>AI智能标注</span>
            </div>
            <div class="feature-item">
              <el-icon><Check /></el-icon>
              <span>大数据分析</span>
            </div>
            <div class="feature-item">
              <el-icon><Check /></el-icon>
              <span>实时任务监控</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧登录表单 -->
      <div class="form-section">
        <div class="form-card">
          <div class="form-header">
            <h3 class="form-title">用户登录</h3>
            <p class="form-subtitle">欢迎回来,请登录您的账户</p>
          </div>

          <el-form size="large" @submit.prevent="onSubmit" class="login-form">
            <el-form-item>
              <el-input
                v-model="form.username"
                placeholder="请输入用户名"
                :prefix-icon="User"
                autocomplete="username"
                @keyup.enter="onSubmit"
                class="form-input"
              />
            </el-form-item>
            <el-form-item>
              <el-input
                v-model="form.password"
                type="password"
                placeholder="请输入密码"
                :prefix-icon="Lock"
                autocomplete="current-password"
                show-password
                @keyup.enter="onSubmit"
                class="form-input"
              />
            </el-form-item>
            <el-form-item>
              <el-button
                type="primary"
                :loading="loading"
                class="login-button"
                @click="onSubmit"
              >
                <span v-if="!loading">登 录</span>
                <span v-else>登录中...</span>
              </el-button>
            </el-form-item>
          </el-form>
        </div>

        <div class="copyright">
          <p>&copy; {{ new Date().getFullYear() }} 芜湖市公安局 版权所有</p>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 登录容器 - 科幻蓝风格设计 */
.login-container {
  min-height: 100vh;
  width: 100%;
  background: linear-gradient(135deg, #0d1b2a 0%, #1a365d 50%, #0d1b2a 100%);
  display: flex;
  justify-content: center;
  align-items: center;
  position: relative;
  overflow: hidden;
}

/* 科幻网格背景 */
.login-container::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-image:
    linear-gradient(rgba(0, 212, 255, 0.03) 1px, transparent 1px),
    linear-gradient(90deg, rgba(0, 212, 255, 0.03) 1px, transparent 1px);
  background-size: 50px 50px;
  animation: gridMove 20s linear infinite;
}

@keyframes gridMove {
  0% { transform: translate(0, 0); }
  100% { transform: translate(50px, 50px); }
}

/* 背景装饰 - 科幻光圈 */
.bg-decoration {
  position: absolute;
  width: 100%;
  height: 100%;
  overflow: hidden;
  z-index: 0;
}

.bg-circle {
  position: absolute;
  border-radius: 50%;
  background: transparent;
  border: 1px solid rgba(0, 212, 255, 0.1);
  box-shadow:
    0 0 40px rgba(0, 212, 255, 0.1),
    inset 0 0 40px rgba(0, 212, 255, 0.05);
  animation: pulseRing 8s ease-in-out infinite;
}

.bg-circle-1 {
  width: 600px;
  height: 600px;
  top: -200px;
  right: -200px;
  animation-delay: 0s;
}

.bg-circle-2 {
  width: 400px;
  height: 400px;
  bottom: -150px;
  left: -150px;
  animation-delay: 2s;
}

.bg-circle-3 {
  width: 300px;
  height: 300px;
  top: 40%;
  left: 30%;
  animation-delay: 4s;
}

@keyframes pulseRing {
  0%, 100% {
    transform: scale(1);
    opacity: 0.3;
    border-color: rgba(0, 212, 255, 0.1);
  }
  50% {
    transform: scale(1.05);
    opacity: 0.6;
    border-color: rgba(0, 212, 255, 0.3);
  }
}

/* 登录内容 */
.login-content {
  width: 100%;
  max-width: 1200px;
  height: 80vh;
  min-height: 600px;
  z-index: 1;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 48px;
  padding: 48px;
  animation: scaleIn 0.5s ease-out;
}

/* 左侧品牌区域 */
.brand-section {
  display: flex;
  align-items: center;
  justify-content: center;
  animation: slideInLeft 0.6s ease-out;
}

.brand-content {
  color: white;
  text-align: center;
}

.brand-logo {
  width: 120px;
  height: 120px;
  margin: 0 auto 32px;
  background: rgba(0, 212, 255, 0.1);
  backdrop-filter: blur(10px);
  border-radius: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #00d4ff;
  border: 1px solid rgba(0, 212, 255, 0.3);
  box-shadow:
    0 0 30px rgba(0, 212, 255, 0.3),
    inset 0 0 30px rgba(0, 212, 255, 0.1);
  animation: logoBreath 4s ease-in-out infinite;
}

.brand-badge {
  width: 80px;
  height: 80px;
  object-fit: contain;
  filter: drop-shadow(0 0 10px rgba(0, 212, 255, 0.5));
}

@keyframes logoBreath {
  0%, 100% {
    box-shadow:
      0 0 30px rgba(0, 212, 255, 0.3),
      inset 0 0 30px rgba(0, 212, 255, 0.1);
  }
  50% {
    box-shadow:
      0 0 50px rgba(0, 212, 255, 0.5),
      inset 0 0 40px rgba(0, 212, 255, 0.2);
  }
}

.brand-title {
  font-size: 42px;
  font-weight: 700;
  margin: 0 0 12px 0;
  letter-spacing: 2px;
  text-shadow: 0 0 20px rgba(0, 212, 255, 0.3);
}

.brand-subtitle {
  font-size: 24px;
  font-weight: 500;
  margin: 0 0 16px 0;
  color: #00d4ff;
  text-shadow: 0 0 15px rgba(0, 212, 255, 0.4);
}

.brand-description {
  font-size: 14px;
  margin: 0 0 48px 0;
  color: rgba(0, 212, 255, 0.7);
  letter-spacing: 3px;
  text-transform: uppercase;
}

.brand-features {
  display: flex;
  flex-direction: column;
  gap: 16px;
  align-items: center;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 16px;
  font-weight: 500;
  padding: 12px 24px;
  background: rgba(0, 212, 255, 0.08);
  backdrop-filter: blur(10px);
  border-radius: 12px;
  border: 1px solid rgba(0, 212, 255, 0.2);
  transition: all 0.3s ease;
  cursor: default;
}

.feature-item:hover {
  background: rgba(0, 212, 255, 0.15);
  border-color: rgba(0, 212, 255, 0.4);
  transform: translateX(8px);
  box-shadow: 0 0 20px rgba(0, 212, 255, 0.2);
}

.feature-item .el-icon {
  color: #00d4ff;
  font-size: 20px;
  filter: drop-shadow(0 0 5px rgba(0, 212, 255, 0.5));
}

/* 右侧表单区域 */
.form-section {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  animation: slideInRight 0.6s ease-out;
}

.form-card {
  width: 100%;
  max-width: 440px;
  background: rgba(255, 255, 255, 0.95);
  border-radius: 24px;
  padding: 48px;
  box-shadow:
    0 20px 60px rgba(0, 0, 0, 0.4),
    0 0 40px rgba(0, 212, 255, 0.1);
  animation: fadeInUp 0.6s ease-out 0.2s both;
  position: relative;
  overflow: hidden;
}

/* 表单卡片顶部发光线 */
.form-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 10%;
  right: 10%;
  height: 2px;
  background: linear-gradient(90deg, transparent, #00d4ff, transparent);
  box-shadow: 0 0 20px #00d4ff;
}

.form-header {
  text-align: center;
  margin-bottom: 40px;
}

.form-title {
  font-size: 28px;
  font-weight: 700;
  color: #1a365d;
  margin: 0 0 8px 0;
}

.form-subtitle {
  font-size: 14px;
  color: #718096;
  margin: 0;
}

.login-form {
  margin-bottom: 32px;
}

.form-input :deep(.el-input__wrapper) {
  padding: 14px 16px;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  transition: all 0.3s ease;
  border: 1px solid #e2e8f0;
}

.form-input :deep(.el-input__wrapper):hover {
  border-color: rgba(0, 212, 255, 0.5);
  box-shadow: 0 4px 12px rgba(0, 212, 255, 0.15);
}

.form-input :deep(.el-input__wrapper.is-focus) {
  border-color: #00d4ff;
  box-shadow: 0 0 0 3px rgba(0, 212, 255, 0.15), 0 0 20px rgba(0, 212, 255, 0.1);
}

.login-button {
  width: 100%;
  height: 50px;
  font-size: 16px;
  font-weight: 600;
  border-radius: 12px;
  letter-spacing: 2px;
  margin-top: 12px;
  background: linear-gradient(135deg, #00d4ff 0%, #0891b2 100%) !important;
  border: none !important;
  box-shadow: 0 4px 15px rgba(0, 212, 255, 0.3);
  transition: all 0.3s ease;
  position: relative;
  overflow: hidden;
}

.login-button::before {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.3), transparent);
  transition: left 0.5s ease;
}

.login-button:hover::before {
  left: 100%;
}

.login-button:hover {
  box-shadow: 0 6px 25px rgba(0, 212, 255, 0.5), 0 0 30px rgba(0, 212, 255, 0.2);
  transform: translateY(-2px);
}

.copyright {
  margin-top: 32px;
  text-align: center;
  color: rgba(0, 212, 255, 0.6);
  font-size: 12px;
}

.copyright p {
  margin: 0;
}

/* 响应式设计 */
@media (max-width: 1024px) {
  .login-content {
    grid-template-columns: 1fr;
    height: auto;
    min-height: auto;
    padding: 32px;
  }

  .brand-section {
    display: none;
  }

  .form-card {
    max-width: 480px;
  }
}

@media (max-width: 640px) {
  .login-content {
    padding: 16px;
  }

  .form-card {
    padding: 32px 24px;
    border-radius: 20px;
  }

  .form-title {
    font-size: 24px;
  }
}
</style>

