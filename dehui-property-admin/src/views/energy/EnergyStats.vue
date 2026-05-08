<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="page-header">
          <div>
            <div class="page-title">能耗分析</div>
            <div class="page-subtitle">按能耗类型、月份、楼宇和房间分析用量趋势与异常波动</div>
          </div>
          <el-button type="primary" :loading="loading" @click="loadRecords">刷新</el-button>
        </div>
      </template>

      <el-form :inline="true" :model="queryForm" class="query-form">
        <el-form-item label="能耗类型">
          <el-select v-model="queryForm.energyType" clearable placeholder="全部" style="width: 150px">
            <el-option label="电" value="ELECTRICITY" />
            <el-option label="水" value="WATER" />
            <el-option label="燃气" value="GAS" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>

        <el-form-item label="月份">
          <el-date-picker
            v-model="queryForm.month"
            type="month"
            value-format="YYYY-MM"
            placeholder="选择月份"
            clearable
            style="width: 160px"
          />
        </el-form-item>

        <el-form-item label="楼宇ID">
          <el-input v-model="queryForm.buildingId" clearable placeholder="楼宇ID" style="width: 120px" />
        </el-form-item>

        <el-form-item label="房间ID">
          <el-input v-model="queryForm.roomId" clearable placeholder="房间ID" style="width: 120px" />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="renderCharts">分析</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="summary-grid">
        <el-card v-for="item in summaryCards" :key="item.label" class="summary-card" shadow="never">
          <div class="summary-label">{{ item.label }}</div>
          <div class="summary-value" :class="item.className">{{ item.value }}</div>
          <div class="summary-desc">{{ item.desc }}</div>
        </el-card>
      </div>

      <div class="chart-grid">
        <el-card class="chart-card wide" shadow="never">
          <template #header>近6个月能耗趋势</template>
          <div ref="trendRef" class="chart"></div>
        </el-card>

        <el-card class="chart-card" shadow="never">
          <template #header>能耗类型结构</template>
          <div ref="typeRef" class="chart"></div>
        </el-card>

        <el-card class="chart-card" shadow="never">
          <template #header>房间用量排行</template>
          <div ref="roomRankRef" class="chart"></div>
        </el-card>

        <el-card class="chart-card" shadow="never">
          <template #header>楼宇用量排行</template>
          <div ref="buildingRankRef" class="chart"></div>
        </el-card>
      </div>

      <el-card shadow="never" class="table-card">
        <template #header>异常用量提示</template>
        <el-table v-loading="loading" :data="abnormalRecords" border stripe>
          <el-table-column prop="meterNumber" label="表号" min-width="130" />
          <el-table-column label="类型" width="90">
            <template #default="{ row }">{{ formatEnergyType(row.energyType) }}</template>
          </el-table-column>
          <el-table-column prop="recordDate" label="抄表日期" width="120" />
          <el-table-column prop="buildingId" label="楼宇ID" width="90" />
          <el-table-column prop="roomId" label="房间ID" width="90" />
          <el-table-column label="本期用量" width="130" align="right">
            <template #default="{ row }">{{ formatNumber(row.consumption) }}</template>
          </el-table-column>
          <el-table-column label="提示">
            <template #default="{ row }">
              <el-tag type="warning">超过平均用量 150%</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </el-card>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import request from '../../utils/request'

const loading = ref(false)
const records = ref([])
const charts = []

const trendRef = ref(null)
const typeRef = ref(null)
const roomRankRef = ref(null)
const buildingRankRef = ref(null)

const queryForm = reactive({
  energyType: '',
  month: '',
  buildingId: '',
  roomId: ''
})

const filteredRecords = computed(() => {
  return records.value.filter(item => {
    const matchType = !queryForm.energyType || item.energyType === queryForm.energyType
    const matchMonth = !queryForm.month || String(item.recordDate || '').startsWith(queryForm.month)
    const matchBuilding = !queryForm.buildingId || String(item.buildingId || '') === String(queryForm.buildingId)
    const matchRoom = !queryForm.roomId || String(item.roomId || '') === String(queryForm.roomId)
    return matchType && matchMonth && matchBuilding && matchRoom
  })
})

const totalConsumption = computed(() => {
  return filteredRecords.value.reduce((total, item) => total + number(item.consumption), 0)
})

const averageConsumption = computed(() => {
  return filteredRecords.value.length > 0 ? totalConsumption.value / filteredRecords.value.length : 0
})

const summaryCards = computed(() => [
  { label: '记录数', value: filteredRecords.value.length, desc: '当前筛选范围', className: '' },
  { label: '总用量', value: formatNumber(totalConsumption.value), desc: '全部类型合计', className: '' },
  { label: '平均用量', value: formatNumber(averageConsumption.value), desc: '单条记录平均', className: 'success' },
  { label: '异常记录', value: abnormalRecords.value.length, desc: '超过平均 150%', className: 'warning' }
])

const typeStats = computed(() => {
  return groupBy(filteredRecords.value, item => item.energyType || 'OTHER')
    .map(item => ({
      name: formatEnergyType(item.key),
      value: item.totalConsumption
    }))
})

const monthlyTrend = computed(() => {
  const months = []
  const today = new Date()

  for (let i = 5; i >= 0; i -= 1) {
    const date = new Date(today.getFullYear(), today.getMonth() - i, 1)
    months.push(date.toISOString().slice(0, 7))
  }

  return months.map(month => {
    const rows = filteredRecords.value.filter(item => String(item.recordDate || '').startsWith(month))
    const typeMap = new Map()

    rows.forEach(item => {
      const key = formatEnergyType(item.energyType)
      typeMap.set(key, (typeMap.get(key) || 0) + number(item.consumption))
    })

    return { month, typeMap }
  })
})

const energyTypesInTrend = computed(() => {
  const set = new Set()
  monthlyTrend.value.forEach(item => {
    item.typeMap.forEach((_value, key) => set.add(key))
  })
  return Array.from(set)
})

const roomRank = computed(() => {
  return groupBy(filteredRecords.value, item => `房间 ${item.roomId || '-'}`)
    .sort((a, b) => b.totalConsumption - a.totalConsumption)
    .slice(0, 8)
})

const buildingRank = computed(() => {
  return groupBy(filteredRecords.value, item => `楼宇 ${item.buildingId || '-'}`)
    .sort((a, b) => b.totalConsumption - a.totalConsumption)
    .slice(0, 8)
})

const abnormalRecords = computed(() => {
  if (averageConsumption.value <= 0) return []
  return filteredRecords.value
    .filter(item => number(item.consumption) > averageConsumption.value * 1.5)
    .sort((a, b) => number(b.consumption) - number(a.consumption))
    .slice(0, 10)
})

function number(value) {
  const n = Number(value || 0)
  return Number.isFinite(n) ? n : 0
}

function formatNumber(value) {
  return number(value).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })
}

function groupBy(list, getKey) {
  const map = new Map()

  list.forEach(item => {
    const key = getKey(item)
    if (!map.has(key)) {
      map.set(key, { key, count: 0, totalReading: 0, totalConsumption: 0 })
    }

    const current = map.get(key)
    current.count += 1
    current.totalReading += number(item.reading)
    current.totalConsumption += number(item.consumption)
  })

  return Array.from(map.values())
}

function formatEnergyType(type) {
  return {
    ELECTRICITY: '电',
    WATER: '水',
    GAS: '燃气',
    OTHER: '其他'
  }[type] || type || '-'
}

function createChart(elRef) {
  if (!elRef.value) return null
  const chart = echarts.init(elRef.value)
  charts.push(chart)
  return chart
}

function renderCharts() {
  charts.splice(0).forEach(chart => chart.dispose())

  const trend = createChart(trendRef)
  const type = createChart(typeRef)
  const room = createChart(roomRankRef)
  const building = createChart(buildingRankRef)

  trend?.setOption({
    color: ['#d93025', '#409eff', '#e6a23c', '#67c23a'],
    tooltip: { trigger: 'axis' },
    legend: { top: 0 },
    grid: { left: 42, right: 20, top: 44, bottom: 28 },
    xAxis: { type: 'category', data: monthlyTrend.value.map(item => item.month) },
    yAxis: { type: 'value' },
    series: energyTypesInTrend.value.map(typeName => ({
      name: typeName,
      type: 'line',
      smooth: true,
      data: monthlyTrend.value.map(item => item.typeMap.get(typeName) || 0)
    }))
  })

  type?.setOption({
    color: ['#d93025', '#409eff', '#e6a23c', '#67c23a'],
    tooltip: { trigger: 'item' },
    legend: { bottom: 0 },
    series: [{ type: 'pie', radius: ['48%', '70%'], center: ['50%', '45%'], data: typeStats.value }]
  })

  room?.setOption(rankBarOption(roomRank.value))
  building?.setOption(rankBarOption(buildingRank.value))
}

function rankBarOption(rows) {
  return {
    color: ['#d93025'],
    tooltip: { trigger: 'axis' },
    grid: { left: 78, right: 20, top: 18, bottom: 24 },
    xAxis: { type: 'value' },
    yAxis: { type: 'category', data: rows.map(item => item.key).reverse() },
    series: [
      {
        type: 'bar',
        barMaxWidth: 22,
        label: { show: true, position: 'right' },
        data: rows.map(item => Number(item.totalConsumption.toFixed(2))).reverse()
      }
    ]
  }
}

function resizeCharts() {
  charts.forEach(chart => chart.resize())
}

async function loadRecords() {
  loading.value = true
  try {
    const data = await request.get('/energy/list')
    records.value = Array.isArray(data) ? data : []
  } catch (e) {
    ElMessage.error(e.message || '加载能耗分析数据失败')
  } finally {
    loading.value = false
    await nextTick()
    renderCharts()
  }
}

function resetSearch() {
  queryForm.energyType = ''
  queryForm.month = ''
  queryForm.buildingId = ''
  queryForm.roomId = ''
}

watch(filteredRecords, async () => {
  await nextTick()
  renderCharts()
})

onMounted(() => {
  loadRecords()
  window.addEventListener('resize', resizeCharts)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  charts.splice(0).forEach(chart => chart.dispose())
})
</script>

<style scoped>
.page-container {
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
}

.page-subtitle {
  margin-top: 6px;
  font-size: 13px;
  color: #909399;
}

.query-form {
  margin: 18px 0 8px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin: 18px 0;
}

.summary-card {
  min-height: 112px;
}

.summary-label {
  color: #909399;
  font-size: 13px;
}

.summary-value {
  margin-top: 12px;
  font-size: 25px;
  font-weight: 700;
  color: #1f1b1b;
}

.summary-desc {
  margin-top: 8px;
  color: #909399;
  font-size: 13px;
}

.chart-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.chart-card.wide {
  grid-column: span 2;
}

.chart {
  width: 100%;
  height: 300px;
}

.table-card {
  margin-top: 16px;
}

.success {
  color: #67c23a;
}

.warning {
  color: #e6a23c;
}
</style>
