<template>
  <div class="dashboard-page">
    <div class="page-header">
      <div>
        <h2>首页驾驶舱</h2>
        <p>德汇创新中心运营 BI 总览</p>
      </div>
      <el-button type="primary" :loading="loading" @click="loadDashboard">
        刷新数据
      </el-button>
    </div>

    <div class="kpi-grid">
      <el-card v-for="item in kpiCards" :key="item.label" class="kpi-card">
        <div class="card-title">{{ item.label }}</div>
        <div class="card-value" :class="item.className">{{ item.value }}</div>
        <div class="card-desc">{{ item.desc }}</div>
      </el-card>
    </div>

    <div class="chart-grid">
      <el-card class="chart-card wide">
        <template #header>
          <span>近6个月应收 / 实收趋势</span>
        </template>
        <div ref="revenueTrendRef" class="chart"></div>
      </el-card>

      <el-card class="chart-card">
        <template #header>
          <span>账单来源结构</span>
        </template>
        <div ref="billSourceRef" class="chart"></div>
      </el-card>

      <el-card class="chart-card">
        <template #header>
          <span>房源出租状态</span>
        </template>
        <div ref="roomStatusRef" class="chart"></div>
      </el-card>

      <el-card class="chart-card">
        <template #header>
          <span>逾期风险分布</span>
        </template>
        <div ref="overdueRiskRef" class="chart"></div>
      </el-card>
    </div>

    <el-row :gutter="16">
      <el-col :span="14">
        <el-card class="summary-card">
          <template #header>
            <span>最近账单</span>
          </template>

          <el-table :data="recentBills" border style="width: 100%">
            <el-table-column prop="billNumber" label="账单编号" min-width="160" />
            <el-table-column prop="sourceName" label="来源" width="80" />
            <el-table-column prop="tenantId" label="租户ID" width="85" />
            <el-table-column label="金额" width="110">
              <template #default="{ row }">¥ {{ formatMoney(row.amount) }}</template>
            </el-table-column>
            <el-table-column prop="dueDate" label="到期日" width="115" />
            <el-table-column label="状态" width="95">
              <template #default="{ row }">
                <el-tag v-if="row.status === 'PAID'" type="success">已支付</el-tag>
                <el-tag v-else-if="isOverdue(row)" type="danger">已逾期</el-tag>
                <el-tag v-else-if="row.status === 'UNPAID'" type="warning">未支付</el-tag>
                <el-tag v-else type="info">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <el-col :span="10">
        <el-card class="summary-card">
          <template #header>
            <span>合同到期预警</span>
          </template>

          <el-table :data="expiringContracts" border style="width: 100%">
            <el-table-column prop="contractNo" label="合同编号" min-width="140" />
            <el-table-column prop="tenantId" label="租户ID" width="85" />
            <el-table-column prop="endDate" label="结束日期" width="115" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag v-if="row.status === 'ACTIVE'" type="success">生效</el-tag>
                <el-tag v-else type="info">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="summary-card">
      <template #header>
        <span>租户财务排行</span>
      </template>

      <el-table :data="tenantFinanceList" border style="width: 100%">
        <el-table-column prop="tenantId" label="租户ID" width="100" />
        <el-table-column prop="billCount" label="账单数" width="100" />
        <el-table-column label="应收总额" width="140">
          <template #default="{ row }">¥ {{ formatMoney(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column label="已收总额" width="140">
          <template #default="{ row }">¥ {{ formatMoney(row.paidAmount) }}</template>
        </el-table-column>
        <el-table-column label="未收金额" width="140">
          <template #default="{ row }">
            <span :class="{ danger: row.unpaidAmount > 0 }">
              ¥ {{ formatMoney(row.unpaidAmount) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="overdueBillCount" label="逾期账单" width="120" />
        <el-table-column label="风险状态">
          <template #default="{ row }">
            <el-tag v-if="row.overdueBillCount > 0" type="danger">有逾期</el-tag>
            <el-tag v-else-if="row.unpaidAmount > 0" type="warning">有欠费</el-tag>
            <el-tag v-else type="success">正常</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card class="summary-card">
      <template #header>
        <span>AI运营摘要</span>
      </template>
      <p class="ai-summary">{{ report.summary || '暂无数据' }}</p>
    </el-card>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import request from '../../utils/request'

const loading = ref(false)
const report = ref({})
const bills = ref([])
const parkingBills = ref([])
const contracts = ref([])

const revenueTrendRef = ref(null)
const billSourceRef = ref(null)
const roomStatusRef = ref(null)
const overdueRiskRef = ref(null)

const charts = []
const currentMonth = new Date().toISOString().slice(0, 7)

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

function formatShortMoney(value) {
  const n = money(value)
  if (Math.abs(n) >= 10000) {
    return `${(n / 10000).toFixed(1)}万`
  }
  return formatMoney(n)
}

function normalizeBill(item) {
  return {
    ...item,
    source: 'RENT',
    sourceName: '租赁',
    paidAmount: money(item.paidAmount)
  }
}

function normalizeParkingBill(item) {
  const amount = money(item.amount)

  return {
    ...item,
    source: 'PARKING',
    sourceName: '停车',
    contractId: '-',
    paidAmount: item.status === 'PAID' ? amount : 0
  }
}

const allBills = computed(() => {
  return [
    ...bills.value.map(normalizeBill),
    ...parkingBills.value.map(normalizeParkingBill)
  ]
})

const monthBills = computed(() => {
  return allBills.value.filter(item => String(item.periodStart || '').startsWith(currentMonth))
})

function isOverdue(row) {
  if (!row || row.status === 'PAID' || row.status === 'CANCELLED' || !row.dueDate) {
    return false
  }

  const today = new Date().toISOString().slice(0, 10)
  return String(row.dueDate) < today
}

const expiringContracts = computed(() => {
  const today = new Date()
  const todayText = today.toISOString().slice(0, 10)

  const limit = new Date(today)
  limit.setDate(limit.getDate() + 30)
  const limitText = limit.toISOString().slice(0, 10)

  return contracts.value
    .filter(item => {
      return item.status === 'ACTIVE'
        && item.endDate
        && String(item.endDate) >= todayText
        && String(item.endDate) <= limitText
    })
    .sort((a, b) => String(a.endDate || '').localeCompare(String(b.endDate || '')))
})

const recentBills = computed(() => {
  return [...allBills.value]
    .sort((a, b) => String(b.createdTime || '').localeCompare(String(a.createdTime || '')))
    .slice(0, 8)
})

const tenantFinanceList = computed(() => {
  const map = new Map()

  allBills.value.forEach(item => {
    const tenantId = item.tenantId || '未知'

    if (!map.has(tenantId)) {
      map.set(tenantId, {
        tenantId,
        billCount: 0,
        totalAmount: 0,
        paidAmount: 0,
        unpaidAmount: 0,
        unpaidBillCount: 0,
        overdueBillCount: 0
      })
    }

    const row = map.get(tenantId)
    const amount = money(item.amount)
    const paid = money(item.paidAmount)

    row.billCount += 1
    row.totalAmount += amount
    row.paidAmount += paid

    if (item.status !== 'PAID' && item.status !== 'CANCELLED') {
      row.unpaidAmount += Math.max(amount - paid, 0)
      row.unpaidBillCount += 1
    }

    if (isOverdue(item)) {
      row.overdueBillCount += 1
    }
  })

  return Array.from(map.values()).sort((a, b) => b.unpaidAmount - a.unpaidAmount)
})

const finance = computed(() => {
  const monthReceivable = monthBills.value.reduce((sum, item) => sum + money(item.amount), 0)
  const monthReceived = monthBills.value.reduce((sum, item) => sum + money(item.paidAmount), 0)
  const unpaidAmount = allBills.value.reduce((sum, item) => {
    if (item.status === 'PAID' || item.status === 'CANCELLED') {
      return sum
    }

    return sum + Math.max(money(item.amount) - money(item.paidAmount), 0)
  }, 0)

  const monthBillCount = monthBills.value.length
  const paidBillCount = monthBills.value.filter(item => item.status === 'PAID').length
  const unpaidBillCount = monthBills.value.filter(item => item.status !== 'PAID').length
  const overdueBillCount = allBills.value.filter(item => isOverdue(item)).length
  const collectionRate = monthReceivable > 0
    ? Math.round((monthReceived / monthReceivable) * 100)
    : 0

  return {
    monthReceivable,
    monthReceived,
    unpaidAmount,
    collectionRate,
    monthBillCount,
    paidBillCount,
    unpaidBillCount,
    overdueBillCount
  }
})

const kpiCards = computed(() => [
  {
    label: '本月应收',
    value: `¥ ${formatShortMoney(finance.value.monthReceivable)}`,
    desc: `本月账单 ${finance.value.monthBillCount} 笔`,
    className: ''
  },
  {
    label: '本月实收',
    value: `¥ ${formatShortMoney(finance.value.monthReceived)}`,
    desc: `已支付 ${finance.value.paidBillCount} 笔`,
    className: 'success'
  },
  {
    label: '未收款金额',
    value: `¥ ${formatShortMoney(finance.value.unpaidAmount)}`,
    desc: `未支付 ${finance.value.unpaidBillCount} 笔`,
    className: 'danger'
  },
  {
    label: '本月收缴率',
    value: `${finance.value.collectionRate}%`,
    desc: '按本月账期统计',
    className: finance.value.collectionRate >= 80 ? 'success' : 'warning'
  },
  {
    label: '房间出租率',
    value: `${roomRentRate.value}%`,
    desc: `${report.value.rentedRoomCount || 0}/${report.value.roomCount || 0} 间`,
    className: ''
  },
  {
    label: '逾期账单数',
    value: finance.value.overdueBillCount,
    desc: '租赁与停车合并',
    className: 'danger'
  }
])

const roomRentRate = computed(() => {
  const roomCount = money(report.value.roomCount)
  const rentedRoomCount = money(report.value.rentedRoomCount)
  return roomCount > 0 ? Math.round((rentedRoomCount / roomCount) * 100) : 0
})

const monthlyTrend = computed(() => {
  const months = []
  const today = new Date()

  for (let i = 5; i >= 0; i -= 1) {
    const date = new Date(today.getFullYear(), today.getMonth() - i, 1)
    months.push(date.toISOString().slice(0, 7))
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

const billSourceData = computed(() => {
  const rent = bills.value.reduce((sum, item) => sum + money(item.amount), 0)
  const parking = parkingBills.value.reduce((sum, item) => sum + money(item.amount), 0)

  return [
    { name: '租赁账单', value: rent },
    { name: '停车账单', value: parking }
  ].filter(item => item.value > 0)
})

const overdueRiskData = computed(() => {
  const today = new Date().toISOString().slice(0, 10)

  const result = {
    '已逾期': 0,
    '7天内到期': 0,
    '正常未收': 0
  }

  allBills.value.forEach(item => {
    if (item.status === 'PAID' || item.status === 'CANCELLED') return

    if (String(item.dueDate || '') < today) {
      result['已逾期'] += 1
      return
    }

    const limit = new Date()
    limit.setDate(limit.getDate() + 7)
    const limitText = limit.toISOString().slice(0, 10)

    if (item.dueDate && String(item.dueDate) <= limitText) {
      result['7天内到期'] += 1
      return
    }

    result['正常未收'] += 1
  })

  return Object.entries(result).map(([name, value]) => ({ name, value }))
})

function createChart(elRef) {
  if (!elRef.value) return null
  const chart = echarts.init(elRef.value)
  charts.push(chart)
  return chart
}

function renderCharts() {
  charts.splice(0).forEach(chart => chart.dispose())

  const revenueTrend = createChart(revenueTrendRef)
  const billSource = createChart(billSourceRef)
  const roomStatus = createChart(roomStatusRef)
  const overdueRisk = createChart(overdueRiskRef)

  revenueTrend?.setOption({
    color: ['#d93025', '#555555'],
    tooltip: { trigger: 'axis' },
    legend: { top: 0 },
    grid: { left: 36, right: 20, top: 44, bottom: 28 },
    xAxis: { type: 'category', data: monthlyTrend.value.map(item => item.month) },
    yAxis: { type: 'value' },
    series: [
      {
        name: '应收',
        type: 'bar',
        data: monthlyTrend.value.map(item => item.receivable),
        barMaxWidth: 28
      },
      {
        name: '实收',
        type: 'line',
        smooth: true,
        data: monthlyTrend.value.map(item => item.received)
      }
    ]
  })

  billSource?.setOption({
    color: ['#d93025', '#4a4a4a'],
    tooltip: { trigger: 'item' },
    legend: { bottom: 0 },
    series: [
      {
        type: 'pie',
        radius: ['48%', '70%'],
        center: ['50%', '45%'],
        data: billSourceData.value.length ? billSourceData.value : [{ name: '暂无数据', value: 0 }]
      }
    ]
  })

  roomStatus?.setOption({
    color: ['#d93025', '#9ca3af'],
    tooltip: { trigger: 'item' },
    series: [
      {
        type: 'pie',
        radius: '68%',
        data: [
          { name: '已出租', value: money(report.value.rentedRoomCount) },
          { name: '可出租', value: money(report.value.availableRoomCount) }
        ]
      }
    ]
  })

  overdueRisk?.setOption({
    color: ['#d93025', '#e6a23c', '#67c23a'],
    tooltip: { trigger: 'axis' },
    grid: { left: 80, right: 20, top: 18, bottom: 24 },
    xAxis: { type: 'value', minInterval: 1 },
    yAxis: { type: 'category', data: overdueRiskData.value.map(item => item.name) },
    series: [
      {
        type: 'bar',
        data: overdueRiskData.value.map(item => item.value),
        barMaxWidth: 24,
        label: { show: true, position: 'right' }
      }
    ]
  })
}

function resizeCharts() {
  charts.forEach(chart => chart.resize())
}

async function loadReport() {
  try {
    const data = await request.get('/ai/daily-report', { silent: true })
    report.value = data || {}
  } catch {
    report.value = {}
  }
}

async function loadBills() {
  try {
    const data = await request.get('/bills', { silent: true })
    bills.value = Array.isArray(data) ? data : []
  } catch {
    bills.value = []
  }
}

async function loadParkingBills() {
  try {
    const data = await request.get('/parking/bills', { silent: true })
    parkingBills.value = Array.isArray(data) ? data : []
  } catch {
    parkingBills.value = []
  }
}

async function loadContracts() {
  try {
    const data = await request.get('/contracts', { silent: true })
    contracts.value = Array.isArray(data) ? data : []
  } catch {
    contracts.value = []
  }
}

async function loadDashboard() {
  loading.value = true

  try {
    await Promise.allSettled([
      loadReport(),
      loadBills(),
      loadParkingBills(),
      loadContracts()
    ])
  } finally {
    loading.value = false
    await nextTick()
    renderCharts()
  }
}

watch([allBills, report], async () => {
  await nextTick()
  renderCharts()
})

onMounted(() => {
  loadDashboard()
  window.addEventListener('resize', resizeCharts)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  charts.splice(0).forEach(chart => chart.dispose())
})
</script>

<style scoped>
.dashboard-page {
  padding: 4px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.page-header h2 {
  margin: 0;
}

.page-header p {
  margin: 6px 0 0;
  color: #777;
}

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}

.kpi-card {
  min-height: 126px;
}

.card-title {
  color: #666;
  font-size: 14px;
}

.card-value {
  margin-top: 10px;
  font-size: 25px;
  font-weight: bold;
  line-height: 1.15;
}

.card-desc {
  margin-top: 10px;
  color: #909399;
  font-size: 13px;
}

.chart-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}

.chart-card.wide {
  grid-column: span 2;
}

.chart {
  height: 300px;
  width: 100%;
}

.summary-card {
  margin-bottom: 16px;
}

.success {
  color: #67c23a;
}

.danger {
  color: #f56c6c;
}

.warning {
  color: #e6a23c;
}

.ai-summary {
  margin: 0;
  color: #303133;
}
</style>
