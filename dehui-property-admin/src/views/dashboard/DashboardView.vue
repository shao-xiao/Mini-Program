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
  const contracts = ref([])

const revenueTrendRef = ref(null)
const billSourceRef = ref(null)
const roomStatusRef = ref(null)
const overdueRiskRef = ref(null)

const charts = []

function todayText() {
  const date = new Date()
  return localDateText(date)
}

function localDateText(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

  function monthText(date) {
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
  }

  function formatBillType(type) {
    return {
      RENT: '租金',
      PROPERTY: '物业费',
      MEETING: '会议',
      MEETING_ROOM: '会议',
      WATER: '水',
      ELECTRICITY: '电',
      GAS: '煤',
      PARKING: '停车',
      OTHER: '其它',
      UTILITY: '水电煤'
    }[type] || type || '其它'
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
      source: item.billType || 'OTHER',
      sourceName: formatBillType(item.billType),
      paidAmount: money(item.paidAmount)
    }
  }

  const allBills = computed(() => {
    return bills.value.map(normalizeBill)
  })

const monthBills = computed(() => {
  const currentMonth = monthText(new Date())
  return allBills.value.filter(item => String(item.periodStart || '').startsWith(currentMonth))
})

function isOverdue(row) {
  if (!row || row.status === 'PAID' || row.status === 'CANCELLED' || !row.dueDate) {
    return false
  }

  return String(row.dueDate) < todayText()
}

const expiringContracts = computed(() => {
  const today = new Date()
  const startText = localDateText(today)

  const limit = new Date(today)
  limit.setDate(limit.getDate() + 30)
  const limitText = localDateText(limit)

  return contracts.value
    .filter(item => {
      return item.status === 'ACTIVE'
        && item.endDate
        && String(item.endDate) >= startText
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
  if (report.value.rentalRate !== undefined && report.value.rentalRate !== null) {
    return Number(report.value.rentalRate || 0)
  }
  const roomCount = money(report.value.roomCount)
  const rentedRoomCount = money(report.value.rentedRoomCount)
  return roomCount > 0 ? Math.round((rentedRoomCount / roomCount) * 100) : 0
})

const monthlyTrend = computed(() => {
  const endMonth = latestTrendMonth.value
  const months = []

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

const billSourceData = computed(() => {
  const map = new Map()

  allBills.value.forEach(item => {
    const key = formatBillType(item.billType || 'OTHER')
    map.set(key, (map.get(key) || 0) + money(item.amount))
  })

  return Array.from(map.entries()).map(([name, value]) => ({ name, value }))
})

const overdueRiskData = computed(() => {
  const currentDate = todayText()

  const result = {
    '已逾期': 0,
    '7天内到期': 0,
    '正常未收': 0
  }

  allBills.value.forEach(item => {
    if (item.status === 'PAID' || item.status === 'CANCELLED') return

    if (String(item.dueDate || '') < currentDate) {
      result['已逾期'] += 1
      return
    }

    const limit = new Date()
    limit.setDate(limit.getDate() + 7)
    const limitText = localDateText(limit)

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
        formatter: value => formatShortMoney(value)
      }
    },
    series: [
      {
        name: '应收金额',
        type: 'bar',
        data: monthlyTrend.value.map(item => item.receivable),
        barMaxWidth: 28,
        label: {
          show: true,
          position: 'top',
          formatter: params => (money(params.value) > 0 ? formatShortMoney(params.value) : '')
        }
      },
      {
        name: '实收金额',
        type: 'line',
        smooth: true,
        symbolSize: 8,
        label: {
          show: true,
          formatter: params => (money(params.value) > 0 ? formatShortMoney(params.value) : '')
        },
        data: monthlyTrend.value.map(item => item.received)
      }
    ]
  })

  billSource?.setOption({
    animation: false,
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
    animation: false,
    color: ['#d93025', '#9ca3af'],
    tooltip: { trigger: 'item' },
    series: [
      {
        type: 'pie',
        radius: '68%',
        data: [
          { name: '已出租', value: money(report.value.rentedRoomCount) },
          { name: '未出租', value: money(report.value.availableRoomCount) }
        ]
      }
    ]
  })

  overdueRisk?.setOption({
    animation: false,
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
