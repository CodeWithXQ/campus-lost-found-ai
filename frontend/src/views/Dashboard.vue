<template>
  <div class="dashboard">
    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stat-row">
      <el-col :xs="12" :sm="8" :md="4" v-for="card in cards" :key="card.label">
        <div class="stat-card" :style="{ borderTopColor: card.color }">
          <div class="stat-num" :style="{ color: card.color }">{{ card.value }}</div>
          <div class="stat-label">{{ card.label }}</div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :xs="24" :md="24">
        <el-card class="chart-card">
          <template #header>
            <div class="card-title">
              <el-icon color="#409EFF"><PieChart /></el-icon>
              <b>在线物品类别分布（失物 / 招领）</b>
            </div>
          </template>
          <div class="category-split">
            <div ref="lostRef" class="half-chart"></div>
            <div ref="foundRef" class="half-chart"></div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :xs="24" :md="8">
        <el-card class="chart-card">
          <template #header>
            <div class="card-title">
              <el-icon color="#67C23A"><Location /></el-icon>
              <b>高频地点热力图 TOP10</b>
            </div>
          </template>
          <div ref="locationRef" class="chart"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="8">
        <el-card class="chart-card">
          <template #header>
            <div class="card-title">
              <el-icon color="#E6A23C"><TrendCharts /></el-icon>
              <b>近 6 个月发布趋势</b>
            </div>
          </template>
          <div ref="trendRef" class="chart"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="8">
        <el-card class="chart-card">
          <template #header>
            <div class="card-title">
              <el-icon color="#F56C6C"><Aim /></el-icon>
              <b>匹配成功率</b>
            </div>
          </template>
          <div class="rate-box">
            <el-progress type="dashboard" :percentage="Math.round(rate.rate || 0)" :width="180" :stroke-width="12"
              color="#67C23A">
              <template #default>
                <div class="rate-num">{{ (rate.rate || 0).toFixed(1) }}%</div>
              </template>
            </el-progress>
            <div class="rate-detail">
              <div>总匹配：<b>{{ rate.total || 0 }}</b> 次</div>
              <div>已认领：<b>{{ rate.claimed || 0 }}</b> 次</div>
              <div class="tip">匹配成功率 = 已认领匹配 / 总匹配</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'
import { statsApi } from '../api'

const lostRef = ref()
const foundRef = ref()
const locationRef = ref()
const trendRef = ref()

const overview = ref({})
const rate = ref({})

let charts = []

function getChart(el) {
  if (!el) return null
  let chart = charts.find((c) => c.getDom() === el)
  if (!chart) {
    chart = echarts.init(el)
    charts.push(chart)
  }
  return chart
}

const cards = computed(() => [
  { label: '在线信息', value: overview.value.totalPosts ?? '-', color: '#409EFF' },
  { label: '在线失物', value: overview.value.lostPosts ?? '-', color: '#E6A23C' },
  { label: '在线招领', value: overview.value.foundPosts ?? '-', color: '#67C23A' },
  { label: '已认领', value: overview.value.claimedPosts ?? '-', color: '#F56C6C' },
  { label: '注册用户', value: overview.value.totalUsers ?? '-', color: '#909399' },
  { label: '匹配记录', value: overview.value.totalMatches ?? '-', color: '#8E44AD' }
])

async function loadOverview() {
  overview.value = await statsApi.overview()
  rate.value = await statsApi.successRate()
}

function renderCategory() {
  const lostChart = getChart(lostRef.value)
  const foundChart = getChart(foundRef.value)
  if (!lostChart || !foundChart) return
  statsApi.category().then((rows) => {
    const lost = rows.filter((r) => r.type === 'LOST')
    const found = rows.filter((r) => r.type === 'FOUND')
    const base = {
      tooltip: { trigger: 'item', formatter: '{b}: {c} 条 ({d}%)' },
      legend: { bottom: 0, type: 'scroll', icon: 'circle', itemWidth: 10, itemHeight: 10 },
      series: [
        {
          type: 'pie',
          radius: ['32%', '60%'],
          center: ['50%', '52%'],
          label: { formatter: '{b} {d}%', fontSize: 11, color: '#606266' },
          labelLine: { length: 10, length2: 8 },
          itemStyle: { borderRadius: 4, borderColor: '#fff', borderWidth: 1 }
        }
      ]
    }
    lostChart.setOption({
      ...base,
      title: { text: '失物', left: 'center', top: 6, textStyle: { fontSize: 14, fontWeight: 600, color: '#E6A23C' } },
      series: [{ ...base.series[0], data: lost.map((r) => ({ name: r.category, value: Number(r.cnt) })) }]
    })
    foundChart.setOption({
      ...base,
      title: { text: '招领', left: 'center', top: 6, textStyle: { fontSize: 14, fontWeight: 600, color: '#67C23A' } },
      series: [{ ...base.series[0], data: found.map((r) => ({ name: r.category, value: Number(r.cnt) })) }]
    })
  })
}

function renderLocation() {
  const chart = getChart(locationRef.value)
  if (!chart) return
  statsApi.location().then((rows) => {
    const values = rows.map((r) => Number(r.cnt))
    const max = values.length ? Math.max(...values) : 1
    const min = values.length ? Math.min(...values) : 1
    // 颜色梯度：低频 → 浅蓝，高频 → 红
    const palette = ['#bfd3ff', '#409eff', '#e6a23c', '#f56c6c']
    // 手动为每个柱子设置颜色（不依赖 visualMap，避免其按索引而非数值映射的坑）
    const data = rows.map((r) => {
      const v = Number(r.cnt)
      const t = max === min ? 1 : (v - min) / (max - min)
      const idx = Math.round(t * (palette.length - 1))
      return { value: v, itemStyle: { color: palette[idx] } }
    })
    chart.setOption({
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      grid: { left: 10, right: 20, top: 10, bottom: 30, containLabel: true },
      xAxis: { type: 'value', minInterval: 1 },
      yAxis: {
        type: 'category',
        inverse: true,
        data: rows.map((r) => r.location),
        axisLabel: { fontSize: 11 }
      },
      series: [
        {
          type: 'bar',
          data,
          itemStyle: { borderRadius: [0, 4, 4, 0] },
          label: { show: true, position: 'right' }
        }
      ]
    }, true)
  })
}

function renderTrend() {
  const chart = getChart(trendRef.value)
  if (!chart) return
  statsApi.trend().then((rows) => {
    const months = [...new Set(rows.map((r) => r.month))]
    const lostData = months.map((m) => {
      const row = rows.find((r) => r.month === m && r.type === 'LOST')
      return row ? Number(row.cnt) : 0
    })
    const foundData = months.map((m) => {
      const row = rows.find((r) => r.month === m && r.type === 'FOUND')
      return row ? Number(row.cnt) : 0
    })
    chart.setOption({
      tooltip: { trigger: 'axis' },
      legend: { data: ['失物', '招领'], bottom: 0 },
      grid: { left: 10, right: 20, top: 30, bottom: 50 },
      xAxis: { type: 'category', data: months, axisLabel: { fontSize: 10 } },
      yAxis: { type: 'value', minInterval: 1 },
      series: [
        { name: '失物', type: 'line', smooth: true, data: lostData, itemStyle: { color: '#E6A23C' }, areaStyle: { opacity: 0.15 } },
        { name: '招领', type: 'line', smooth: true, data: foundData, itemStyle: { color: '#67C23A' }, areaStyle: { opacity: 0.15 } }
      ]
    })
  })
}

function onResize() {
  charts.forEach((c) => c.resize())
}

onMounted(() => {
  loadOverview()
  renderCategory()
  renderLocation()
  renderTrend()
  window.addEventListener('resize', onResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', onResize)
  charts.forEach((c) => c.dispose())
  charts = []
})
</script>

<style scoped>
.dashboard {
  overflow: auto;
}
.stat-row {
  margin-bottom: 16px;
}
.stat-card {
  background: #fff;
  border-radius: 10px;
  border-top: 3px solid #409eff;
  padding: 18px 14px;
  text-align: center;
  box-shadow: 0 2px 8px rgba(0, 21, 41, 0.06);
  margin-bottom: 12px;
}
.stat-num {
  font-size: 26px;
  font-weight: 700;
}
.stat-label {
  margin-top: 6px;
  font-size: 13px;
  color: #909399;
}
.chart-card {
  margin-bottom: 16px;
}
.card-title {
  display: flex;
  align-items: center;
  gap: 6px;
}
.category-split {
  display: flex;
  gap: 12px;
}
.half-chart {
  flex: 1;
  min-width: 0;
  height: 320px;
}
.chart {
  height: 320px;
}
.rate-box {
  height: 320px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
  padding: 10px 0;
}
.rate-num {
  font-size: 22px;
  font-weight: 700;
  color: #67c23a;
}
.rate-detail {
  text-align: center;
  line-height: 1.9;
  color: #606266;
  font-size: 13px;
}
.tip {
  color: #c0c4cc;
  font-size: 12px;
}
</style>
