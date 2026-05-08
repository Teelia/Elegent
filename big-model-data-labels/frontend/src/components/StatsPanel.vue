<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import type { TaskStatistics } from '../api/analysis'

const props = defineProps<{
  stats: TaskStatistics | null
}>()

const el = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null

function displayLabelKey(labelKey: string) {
  const m = /^(.*)_v\d+$/.exec(labelKey)
  return m ? m[1] : labelKey
}

function formatPercent(value: number) {
  if (!isFinite(value)) return '0%'
  return `${(value * 100).toFixed(1)}%`
}

const labelRows = computed(() => {
  if (!props.stats) return []
  const total = props.stats.totalRows || 0
  const stats = props.stats.labelStatistics || {}

  return Object.keys(stats).map((labelKey) => {
    const m = stats[labelKey] || {}
    const hit = m['是'] || 0
    const rate = total > 0 ? hit / total : 0
    return {
      labelKey,
      labelName: displayLabelKey(labelKey),
      hitCount: hit,
      hitRate: rate,
      hitRateText: formatPercent(rate),
    }
  })
})

function render() {
  if (!el.value || !props.stats) return
  if (!chart) chart = echarts.init(el.value)

  const rows = props.stats.labelDistributions || []
  const seriesData = rows
    .filter((x) => x.labelValue === '是')
    .map((x) => ({ name: displayLabelKey(x.labelName), value: x.count }))

  chart.setOption({
    tooltip: { trigger: 'item' },
    series: [
      {
        type: 'pie',
        radius: '70%',
        data: seriesData,
      },
    ],
  })
}

onMounted(render)
watch(() => props.stats, render)
</script>

<template>
  <div v-if="stats">
    <div style="display:flex;gap:16px;flex-wrap:wrap;margin-bottom:12px;color:#666">
      <div>总行数：{{ stats.totalRows }}</div>
      <div>已处理：{{ stats.processedRows }}</div>
      <div>失败：{{ stats.failedRows }}</div>
    </div>

    <el-table v-if="labelRows.length" :data="labelRows" size="small" style="width: 100%; margin-bottom: 12px">
      <el-table-column prop="labelName" label="标签" min-width="160" />
      <el-table-column prop="hitCount" label="命中数" width="100" />
      <el-table-column prop="hitRateText" label="命中率" width="110" />
    </el-table>

    <div ref="el" style="height: 320px; width: 100%" />
  </div>
  <div v-else style="color:#999">暂无统计数据</div>
</template>

