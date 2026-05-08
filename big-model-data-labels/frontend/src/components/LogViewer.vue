<script setup lang="ts">
import { ref, watch, nextTick, onUnmounted } from 'vue'
import { VideoPlay, VideoPause, Delete } from '@element-plus/icons-vue'

export interface LogEntry {
  timestamp: number
  message: string
  taskId: number
}

const props = defineProps<{
  logs: LogEntry[]
  height?: string
}>()

const logContainer = ref<HTMLElement | null>(null)
const autoScroll = ref(true)

watch(() => props.logs.length, () => {
  if (autoScroll.value) {
    nextTick(() => {
      scrollToBottom()
    })
  }
})

function scrollToBottom() {
  if (logContainer.value) {
    logContainer.value.scrollTop = logContainer.value.scrollHeight
  }
}

function formatTime(ts: number) {
  return new Date(ts).toLocaleTimeString()
}

function clearLogs() {
  // emit clear event if needed, or just let parent handle it
}
</script>

<template>
  <div class="log-viewer" :style="{ height: height || '200px' }">
    <div class="log-header">
      <span class="title">实时分析日志</span>
      <div class="actions">
        <el-tooltip content="自动滚动">
          <el-switch v-model="autoScroll" size="small" />
        </el-tooltip>
      </div>
    </div>
    <div class="log-content" ref="logContainer">
      <div v-if="logs.length === 0" class="empty-logs">暂无日志...</div>
      <div v-for="(log, index) in logs" :key="index" class="log-line">
        <span class="log-time">[{{ formatTime(log.timestamp) }}]</span>
        <span class="log-msg">{{ log.message }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.log-viewer {
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  display: flex;
  flex-direction: column;
  background-color: #1e1e1e;
  color: #d4d4d4;
  font-family: 'Consolas', 'Monaco', monospace;
}

.log-header {
  padding: 5px 10px;
  background-color: #2d2d2d;
  border-bottom: 1px solid #3e3e3e;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.title {
  font-size: 12px;
  font-weight: bold;
  color: #ccc;
}

.log-content {
  flex: 1;
  overflow-y: auto;
  padding: 10px;
  font-size: 12px;
  line-height: 1.5;
}

.log-line {
  word-break: break-all;
  margin-bottom: 2px;
}

.log-time {
  color: #569cd6;
  margin-right: 8px;
}

.log-msg {
  color: #ce9178;
}

.empty-logs {
  color: #666;
  text-align: center;
  margin-top: 20px;
}
</style>
