<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import type { KeywordCount } from '../api/analysis'

const props = defineProps<{
  items: KeywordCount[]
  title?: string
}>()

const el = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null

function render() {
  if (!el.value) return
  if (!chart) chart = echarts.init(el.value)
  const x = props.items.map((i) => i.keyword)
  const y = props.items.map((i) => i.count)
  chart.setOption({
    title: { text: props.title || '关键词 Top' },
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'value' },
    yAxis: { type: 'category', data: x, axisLabel: { interval: 0 } },
    series: [{ type: 'bar', data: y }],
    grid: { left: 80, right: 16, top: 48, bottom: 16 },
  })
}

onMounted(render)
watch(() => props.items, render, { deep: true })
</script>

<template>
  <div ref="el" style="height: 360px; width: 100%" />
</template>

