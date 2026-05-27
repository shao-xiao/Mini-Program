<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="page-header">
          <div>
            <div class="page-title">账务分析</div>
            <div class="page-subtitle">合并租赁账单与停车账单，分析应收、实收、欠费和逾期风险</div>
          </div>
          <el-button type="primary" :loading="loading" @click="loadBills">刷新</el-button>
        </div>
      </template>

      <div class="summary-grid">
        <el-card v-for="item in summaryCards" :key="item.label" class="summary-card" shadow="never">
          <div class="summary-label">{{ item.label }}</div>
          <div class="summary-value" :class="item.className">{{ item.value }}</div>
          <div class="summary-desc">{{ item.desc }}</div>
        </el-card>
      </div>

      <div class="chart-grid">
        <el-card class="chart-card wide" shadow="never">
          <template #header>近6个月应收 / 实收趋势</template>
          <div ref="trendRef" class="chart"></div>
        </el-card>

        <el-card class="chart-card" shadow="never">
          <template #header>账单来源结构</template>
          <div ref="sourceRef" class="chart"></div>
        </el-card>

        <el-card class="chart-card" shadow="never">
          <template #header>费用类型分布</template>
          <div ref="typeRef" class="chart"></div>
        </el-card>

        <el-card class="chart-card" shadow="never">
          <template #header>逾期风险</template>
          <div ref="riskRef" class="chart"></div>
        </el-card>
      </div>

      <el-card shadow="never" class="table-card">
        <template #header>租户欠费排行</template>
        <el-table :data="tenantStats" border stripe v-loading="loading">
          <el-table-column prop="tenantId" label="租户ID" width="100" align="center" />
          <el-table-column prop="billCount" label="账单数" width="100" align="center" />
          <el-table-column label="应收金额" width="150" align="right">
            <template #default="{ row }">¥ {{ formatMoney(row.totalAmount) }}</template>
          </el-table-column>
          <el-table-column label="实收金额" width="150" align="right">
            <template #default="{ row }">¥ {{ formatMoney(row.paidAmount) }}</template>
          </el-table-column>
          <el-table-column label="未收金额" width="150" align="right">
            <template #default="{ row }">
              <span :class="{ danger: row.unpaidAmount > 0 }">¥ {{ formatMoney(row.unpaidAmount) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="逾期金额" width="150" align="right">
            <template #default="{ row }">
              <span :class="{ danger: row.overdueAmount > 0 }">¥ {{ formatMoney(row.overdueAmount) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="风险状态">
            <template #default="{ row }">
              <el-tag v-if="row.overdueAmount > 0" type="danger">逾期</el-tag>
              <el-tag v-else-if="row.unpaidAmount > 0" type="warning">欠费</el-tag>
              <el-tag v-else type="success">正常</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </el-card>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import request from '../../utils/request'
import { readPage } from '../../utils/pagination'
  
const loading = ref(false)
const bills = ref([])

const trendRef = ref(null)
const sourceRef = ref(null)
const typeRef = ref(null)
const riskRef = ref(null)
const charts = []

function localDateText(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

function monthText(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
}

function money(value) {
  const n = Number(value || 0)
  return Number.isFinite(n) ? n : 0
}

function formatMoney(value) {
  return money(value).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })
}

function shortMoney(value) {
  const n = money(value)
  return Math.abs(n) >= 10000 ? `${(n / 10000).toFixed(1)}万` : formatMoney(n)
}

function normalizeBill(item) {
  return {
    ...item,
    source: 'RENT',
    sourceName: '租赁',
    paidAmount: money(item.paidAmount)
  }
}

const allBills = computed(() => {
  return bills.value.map(normalizeBill)
})

const paidAmount = computed(() => {
  return allBills.value.reduce((total, item) => total + money(item.paidAmount), 0)
})

const totalAmount = computed(() => {
  return allBills.value.reduce((total, item) => total + money(item.amount), 0)
})

const unpaidAmount = computed(() => {
  return allBills.value.reduce((total, item) => {
    if (item.status === 'PAID' || item.status === 'CANCELLED') return total
    return total + Math.max(money(item.amount) - money(item.paidAmount), 0)
  }, 0)
})

const overdueAmount = computed(() => {
  return allBills.value.reduce((total, item) => {
    if (!isOverdue(item)) return total
    return total + Math.max(money(item.amount) - money(item.paidAmount), 0)
  }, 0)
})

const collectionRate = computed(() => {
  return totalAmount.value > 0 ? Math.round((paidAmount.value / totalAmount.value) * 100) : 0
})

const summaryCards = computed(() => [
  { label: '应收金额', value: `¥ ${shortMoney(totalAmount.value)}`, desc: `${allBills.value.length} 笔账单`, className: '' },
  { label: '实收金额', value: `¥ ${shortMoney(paidAmount.value)}`, desc: `收缴率 ${collectionRate.value}%`, className: 'success' },
  { label: '未收金额', value: `¥ ${shortMoney(unpaidAmount.value)}`, desc: '未支付与部分支付', className: 'warning' },
  { label: '逾期金额', value: `¥ ${shortMoney(overdueAmount.value)}`, desc: `${overdueBills.value.length} 笔逾期`, className: 'danger' }
])

const overdueBills = computed(() => allBills.value.filter(isOverdue))

const tenantStats = computed(() => {
  const map = new Map()

  allBills.value.forEach(item => {
    const key = item.tenantId || '-'
    if (!map.has(key)) {
      map.set(key, {
        tenantId: key,
        billCount: 0,
        totalAmount: 0,
        paidAmount: 0,
        unpaidAmount: 0,
        overdueAmount: 0
      })
    }

    const row = map.get(key)
    const amount = money(item.amount)
    const paid = money(item.paidAmount)
    const unpaid = item.status !== 'PAID' && item.status !== 'CANCELLED'
      ? Math.max(amount - paid, 0)
      : 0

    row.billCount += 1
    row.totalAmount += amount
    row.paidAmount += paid
    row.unpaidAmount += unpaid
    if (isOverdue(item)) row.overdueAmount += unpaid
  })

  return Array.from(map.values()).sort((a, b) => b.unpaidAmount - a.unpaidAmount)
})

const monthlyTrend = computed(() => {
  const months = []
  const endMonth = latestTrendMonth.value

  for (let i = 5; i >= 0; i -= 1) {
    const date = new Date(endMonth.getFullYear(), endMonth.getMonth() - i, 1)
    months.push(monthText(date))
  }

  return months.map(month => {
    const rows = allBills.value.filter(item => String(item.periodStart || '').startsWith(month))
    return {
      month,
      receivable: rows.reduce((sum, item) => sum + money(item.amount), 0),
      received: rows.reduce((sum, item) => sum + money(item.paidAmount), 0)
    }
  })
})

const latestTrendMonth = computed(() => {
  const current = new Date()
  const latest = new Date(current.getFullYear(), current.getMonth(), 1)

  allBills.value.forEach(item => {
    const month = String(item.periodStart || '').slice(0, 7)
    if (!/^\d{4}-\d{2}$/.test(month)) return

    const [year, monthNumber] = month.split('-').map(Number)
    const billMonth = new Date(year, monthNumber - 1, 1)
    if (billMonth > latest) {
      latest.setFullYear(billMonth.getFullYear(), billMonth.getMonth(), 1)
    }
  })

  return latest
})

const sourceData = computed(() => {
  const map = new Map()

  allBills.value.forEach(item => {
    const key = formatBillType(item.billType || 'OTHER')
    map.set(key, (map.get(key) || 0) + money(item.amount))
  })

  return Array.from(map.entries()).map(([name, value]) => ({ name, value }))
})

const typeData = computed(() => {
  const map = new Map()

  allBills.value.forEach(item => {
    const key = formatBillType(item.billType || 'OTHER')
    map.set(key, (map.get(key) || 0) + money(item.amount))
  })

  return Array.from(map.entries()).map(([name, value]) => ({ name, value }))
})

const riskData = computed(() => {
  const result = { 已逾期: 0, '7天内到期': 0, 正常未收: 0 }
  const todayText = today()
  const limit = new Date()
  limit.setDate(limit.getDate() + 7)
  const limitText = limit.toISOString().slice(0, 10)

  allBills.value.forEach(item => {
    if (item.status === 'PAID' || item.status === 'CANCELLED') return
    if (item.dueDate && String(item.dueDate) < todayText) result['已逾期'] += 1
    else if (item.dueDate && String(item.dueDate) <= limitText) result['7天内到期'] += 1
    else result['正常未收'] += 1
  })

  return Object.entries(result).map(([name, value]) => ({ name, value }))
})

function isOverdue(item) {
  if (!item || item.status === 'PAID' || item.status === 'CANCELLED' || !item.dueDate) return false
  return String(item.dueDate) < today()
}

function today() {
  return localDateText(new Date())
}

function formatBillType(type) {
  return {
    RENT: '租金',
    PROPERTY: '物业费',
    MEETING: '会议',
    MEETING_ROOM: '会议',
    WATER: '水费',
    ELECTRICITY: '电费',
    GAS: '燃气费',
    PARKING: '停车费',
    ENERGY: '能耗费',
    UTILITY: '水电煤',
    WORK_ORDER: '维修/工单服务费',
    CLEANING: '保洁费',
    DEPOSIT: '押金',
    LATE_FEE: '滞纳金',
    ADJUSTMENT: '调账补差',
    OTHER: '其它'
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
  const source = createChart(sourceRef)
  const type = createChart(typeRef)
  const risk = createChart(riskRef)

  trend?.setOption({
    animation: false,
    color: ['#d93025', '#555555'],
    tooltip: {
      trigger: 'axis',
      valueFormatter: value => `¥ ${formatMoney(value)}`
    },
    legend: { top: 0, data: ['应收金额', '实收金额'] },
    grid: { left: 58, right: 24, top: 44, bottom: 28 },
    xAxis: { type: 'category', data: monthlyTrend.value.map(item => item.month) },
    yAxis: {
      type: 'value',
      axisLabel: {
        formatter: value => shortMoney(value)
      }
    },
    series: [
      {
        name: '应收金额',
        type: 'bar',
        barMaxWidth: 28,
        data: monthlyTrend.value.map(item => item.receivable),
        label: {
          show: true,
          position: 'top',
          formatter: params => (money(params.value) > 0 ? shortMoney(params.value) : '')
        }
      },
      {
        name: '实收金额',
        type: 'line',
        smooth: true,
        symbolSize: 8,
        data: monthlyTrend.value.map(item => item.received),
        label: {
          show: true,
          formatter: params => (money(params.value) > 0 ? shortMoney(params.value) : '')
        }
      }
    ]
  })

  source?.setOption({
    animation: false,
    color: ['#d93025', '#4a4a4a'],
    tooltip: { trigger: 'item' },
    legend: { bottom: 0 },
    series: [{ type: 'pie', radius: ['48%', '70%'], center: ['50%', '45%'], data: sourceData.value }]
  })

  type?.setOption({
    animation: false,
    color: ['#d93025', '#555555', '#8a6d3b', '#67c23a'],
    tooltip: { trigger: 'item' },
    series: [{ type: 'pie', radius: '68%', data: typeData.value }]
  })

  risk?.setOption({
    animation: false,
    color: ['#d93025'],
    tooltip: { trigger: 'axis' },
    grid: { left: 80, right: 20, top: 18, bottom: 24 },
    xAxis: { type: 'value', minInterval: 1 },
    yAxis: { type: 'category', data: riskData.value.map(item => item.name) },
    series: [{ type: 'bar', barMaxWidth: 24, label: { show: true, position: 'right' }, data: riskData.value.map(item => item.value) }]
  })
}

function resizeCharts() {
  charts.forEach(chart => chart.resize())
}

async function loadBills() {
  loading.value = true
  try {
    const billData = await request.get('/bills', { params: { page: 1, pageSize: 100 } })
    bills.value = readPage(billData).records
  } catch (e) {
    ElMessage.error(e.message || '加载账务分析数据失败')
  } finally {
    loading.value = false
    await nextTick()
    renderCharts()
  }
}

watch(allBills, async () => {
  await nextTick()
  renderCharts()
})

onMounted(() => {
  loadBills()
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

.danger {
  color: #d93025;
}
</style>
