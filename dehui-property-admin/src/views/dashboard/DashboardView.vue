<template>
  <div class="dashboard-page" :aria-busy="loading">
    <div class="page-header">
      <div>
        <h2>首页驾驶舱</h2>
        <p>德汇创新中心运营 BI 总览</p>
      </div>
      <div class="header-actions">
        <span class="update-time" aria-live="polite">{{ loading ? '正在更新数据…' : updatedAt ? `更新于 ${updatedAt}` : '等待加载' }}</span>
        <el-button type="primary" :loading="loading" @click="loadDashboard">刷新数据</el-button>
      </div>
    </div>

    <div v-if="failedSections.length" class="data-notice" role="status">{{ failedSections.join('、') }}暂时无法加载，请稍后刷新。</div>
    <div class="kpi-grid" aria-label="核心运营指标">
      <div v-for="item in kpiCards" :key="item.label" class="kpi-card" :class="item.className">
        <div class="card-title"><span>{{ item.label }}</span><span class="card-scope">{{ item.scope }}</span></div>
        <div class="card-value" :class="item.className">{{ item.value }}</div>
        <div class="card-desc">{{ item.desc }}</div>
        <progress v-if="item.progress !== undefined" class="metric-track" :value="Math.max(0, Math.min(100, item.progress))" max="100" aria-hidden="true"></progress>
      </div>
    </div>

    <div class="chart-grid">
      <DashboardPanel class="trend-panel" title="应收 / 实收趋势" subtitle="近 6 个月 · 按账期统计" :meta="`${monthlyTrend[0]?.month || ''} — ${monthlyTrend.at(-1)?.month || ''}`">
        <div ref="revenueTrendRef" class="chart trend-chart" role="img" aria-label="近六个月应收与实收金额趋势"></div>
      </DashboardPanel>

      <DashboardPanel class="source-panel" title="账单来源结构" subtitle="按账单金额占比" :meta="`${billSourceData.length} 类来源`">
        <div v-if="sourceTotal > 0" class="source-breakdown">
          <div ref="billSourceRef" class="chart source-chart" role="img" aria-label="账单来源金额占比环形图"></div>
          <ul class="source-legend">
            <li v-for="(item, index) in billSourceData" :key="item.name">
              <span class="legend-name"><i :class="`source-color-${index % sourceColors.length}`"></i>{{ item.name }}</span>
              <strong>{{ formatShortMoney(item.value) }}<small>{{ sourcePercent(item.value) }}%</small></strong>
            </li>
          </ul>
        </div>
        <div v-else class="chart-empty">暂无账单来源数据</div>
      </DashboardPanel>

      <DashboardPanel class="room-panel" title="房源出租状态" subtitle="房源数量与出租占比" :meta="roomStats ? `共 ${roomTotal} 间` : roomStatsHint">
        <div class="room-breakdown">
          <div v-show="hasRoomStats" ref="roomStatusRef" class="chart room-chart" role="img" aria-label="已出租及未出租房源数量"></div>
          <div v-if="!hasRoomStats" class="room-empty"><span>—</span><p>{{ roomStatsHint }}</p></div>
          <div class="room-counts">
            <div><span><i class="dot rented"></i>已出租</span><strong>{{ roomStats ? roomStats.rentedRoomCount : '—' }}<small> 间</small></strong></div>
            <div><span><i class="dot vacant"></i>未出租</span><strong>{{ roomStats ? roomStats.availableRoomCount : '—' }}<small> 间</small></strong></div>
            <p>出租率 <b>{{ roomStats ? `${roomRentRate}%` : '—' }}</b></p>
          </div>
        </div>
      </DashboardPanel>

      <DashboardPanel class="risk-panel" title="账单到期风险" subtitle="未支付账单 · 按到期日分布" :meta="`${finance.overdueBillCount} 笔已逾期`" :alert="finance.overdueBillCount > 0">
        <div ref="overdueRiskRef" class="chart risk-chart" role="img" :aria-label="overdueRiskData.map(item => `${item.name}${item.value}笔`).join('，')"></div>
      </DashboardPanel>
    </div>

    <div class="detail-grid">
      <div class="detail-grid__bills">
        <DashboardPanel class="summary-card" title="最近账单" subtitle="最近创建的 8 笔账单" :meta="`${recentBills.length} 笔`">

          <el-table :data="recentBills" size="small" max-height="330" empty-text="暂无账单记录" style="width: 100%">
            <el-table-column prop="billNumber" label="账单编号" min-width="160" />
            <el-table-column prop="sourceName" label="来源" width="80" />
            <el-table-column prop="tenantId" label="租户ID" width="85" />
            <el-table-column label="金额" min-width="120" align="right">
              <template #default="{ row }">¥ {{ formatMoney(row.amount) }}</template>
            </el-table-column>
            <el-table-column prop="dueDate" label="到期日" width="115" />
            <el-table-column label="状态" width="95">
              <template #default="{ row }">
                <el-tag v-if="row.status === 'PAID'" type="success">已支付</el-tag>
                <el-tag v-else-if="isOverdue(row)" type="danger">已逾期</el-tag>
                <el-tag v-else-if="row.status === 'UNPAID'" type="warning">未支付</el-tag>
                <el-tag v-else type="info">{{ statusText(row.status) }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </DashboardPanel>
      </div>

      <div class="detail-grid__contracts">
        <DashboardPanel class="summary-card" title="合同到期预警" subtitle="未来 30 天内到期的生效合同" :meta="`${expiringContracts.length} 份`" :alert="expiringContracts.length > 0">

          <el-table :data="expiringContracts" size="small" max-height="330" empty-text="未来 30 天暂无到期合同" style="width: 100%">
            <el-table-column prop="contractNo" label="合同编号" min-width="140" />
            <el-table-column prop="tenantId" label="租户ID" width="85" />
            <el-table-column prop="endDate" label="结束日期" width="115" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag v-if="row.status === 'ACTIVE'" type="success">生效</el-tag>
                <el-tag v-else type="info">{{ statusText(row.status) }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </DashboardPanel>
      </div>
    </div>

    <DashboardPanel class="summary-card" title="租户财务排行" subtitle="按未收金额从高到低排列" :meta="`${tenantFinanceList.length} 家租户`">

      <el-table :data="tenantFinanceList" size="small" max-height="330" style="width: 100%">
        <el-table-column prop="tenantId" label="租户ID" width="100" />
        <el-table-column prop="billCount" label="账单数" width="100" />
        <el-table-column label="应收总额" min-width="140" align="right">
          <template #default="{ row }">¥ {{ formatMoney(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column label="已收总额" min-width="140" align="right">
          <template #default="{ row }">¥ {{ formatMoney(row.paidAmount) }}</template>
        </el-table-column>
        <el-table-column label="未收金额" min-width="140" align="right">
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
    </DashboardPanel>

    <DashboardPanel class="summary-card" title="AI 运营摘要" subtitle="运营信息参考">
      <p class="ai-summary">{{ report.summaryText || report.summary || '暂无数据' }}</p>
    </DashboardPanel>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import request from '../../utils/request'
import DashboardPanel from '../../components/dashboard/DashboardPanel.vue'
import { readPage } from '../../utils/pagination'
import { loadDashboardRoomStatistics } from '../../utils/dashboardRoomStatistics'

  const loading = ref(false)
  const report = ref({})
  const bills = ref([])
  const contracts = ref([])

const revenueTrendRef = ref(null)
const billSourceRef = ref(null)
const roomStatusRef = ref(null)
const overdueRiskRef = ref(null)

const charts = []
let resizeObserver
const updatedAt = ref('')
const failedSections = ref([])
const roomStats = ref(null)
const roomStatsHint = ref('正在加载房源统计')
const sourceColors = ['#d93025', '#475467', '#b67b68', '#8c96a6', '#d5a164', '#b8bfc9', '#8f4540', '#6a7976']
const sourceTotal = computed(() => billSourceData.value.reduce((sum, item) => sum + item.value, 0))
const roomTotal = computed(() => roomStats.value?.roomCount ?? 0)
const hasRoomStats = computed(() => roomStats.value !== null && roomTotal.value > 0)
function sourcePercent(value) { return sourceTotal.value > 0 ? (money(value) / sourceTotal.value * 100).toFixed(1) : '0.0' }
function statusText(status) {
  return { CANCELLED: '已作废', DRAFT: '草稿', PARTIAL: '部分支付', PENDING: '待处理', EXPIRED: '已到期', TERMINATED: '已终止' }[status] || status || '—'
}

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
      DEPOSIT: '押金',
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
    scope: '本月',
    value: `¥ ${formatShortMoney(finance.value.monthReceivable)}`,
    desc: `本月账单 ${finance.value.monthBillCount} 笔`,
    className: ''
  },
  {
    label: '本月实收',
    scope: '本月',
    value: `¥ ${formatShortMoney(finance.value.monthReceived)}`,
    desc: `已支付 ${finance.value.paidBillCount} 笔`,
    className: 'success'
  },
  {
    label: '未收款金额',
    scope: '累计',
    value: `¥ ${formatShortMoney(finance.value.unpaidAmount)}`,
    desc: '各账期未收金额合计',
    className: 'danger'
  },
  {
    label: '本月收缴率',
    scope: '本月',
    progress: finance.value.collectionRate,
    value: `${finance.value.collectionRate}%`,
    desc: '按本月账期统计',
    className: finance.value.collectionRate >= 80 ? 'success' : 'warning'
  },
  {
    label: '房间出租率',
    scope: '房源',
    progress: roomStats.value ? roomRentRate.value : undefined,
    value: roomStats.value ? `${roomRentRate.value}%` : '—',
    desc: roomStats.value ? `已租 ${roomStats.value.rentedRoomCount} / 共 ${roomTotal.value} 间` : roomStatsHint.value,
    className: ''
  },
  {
    label: '逾期账单数',
    scope: '风险',
    value: finance.value.overdueBillCount,
    desc: '未支付且已过到期日',
    className: 'danger'
  }
])

const roomRentRate = computed(() => roomStats.value?.rentalRate ?? 0)

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

  return Array.from(map.entries()).map(([name, value]) => ({ name, value })).sort((a, b) => b.value - a.value)
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
  const billSource = sourceTotal.value > 0 ? createChart(billSourceRef) : null
  const roomStatus = hasRoomStats.value ? createChart(roomStatusRef) : null
  const overdueRisk = createChart(overdueRiskRef)

  revenueTrend?.setOption({
    animation: false,
    color: ['#d93025', '#475467'],
    textStyle: { fontFamily: 'sans-serif', color: '#667085', fontSize: 11 },
    tooltip: {
      trigger: 'axis',
      valueFormatter: value => `¥ ${formatMoney(value)}`
    },
    legend: { top: 0, right: 0, itemWidth: 12, itemHeight: 8, textStyle: { color: '#667085', fontSize: 11 }, data: ['应收金额', '实收金额'] },
    grid: { left: 58, right: 20, top: 40, bottom: 26 },
    xAxis: { type: 'category', axisTick: { show: false }, axisLine: { lineStyle: { color: '#e4e7ec' } }, axisLabel: { color: '#667085', fontSize: 11, formatter: value => `${value.slice(2, 4)}年${value.slice(5)}月` }, data: monthlyTrend.value.map(item => item.month) },
    yAxis: {
      type: 'value',
      splitNumber: 3,
      splitLine: { lineStyle: { color: '#eef0f3', type: 'dashed' } },
      axisLabel: {
        formatter: value => formatShortMoney(value)
      }
    },
    series: [
      {
        name: '应收金额',
        type: 'bar',
        data: monthlyTrend.value.map(item => item.receivable),
        barMaxWidth: 26,
        itemStyle: { borderRadius: [3, 3, 0, 0] },
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
        symbolSize: 6,
        lineStyle: { width: 2 },
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
    color: sourceColors,
    tooltip: { trigger: 'item', valueFormatter: value => `¥ ${formatMoney(value)}` },
    title: { text: '账单总额', subtext: formatShortMoney(sourceTotal.value), left: 'center', top: '35%', textStyle: { fontSize: 11, color: '#667085', fontWeight: 400 }, subtextStyle: { fontSize: 18, fontWeight: 600, color: '#202124' }, itemGap: 6 },
    series: [{ type: 'pie', radius: ['64%', '82%'], center: ['50%', '48%'], label: { show: false }, itemStyle: { borderColor: '#fff', borderWidth: 2 }, stillShowZeroSum: false, data: billSourceData.value }]
  })

  roomStatus?.setOption({
    animation: false,
    color: ['#d93025', '#e4e7ec'],
    tooltip: { trigger: 'item', valueFormatter: value => `${value} 间` },
    title: { text: `${roomRentRate.value}%`, subtext: '出租率', left: 'center', top: '32%', textStyle: { color: '#202124', fontSize: 24, fontWeight: 600 }, subtextStyle: { color: '#667085', fontSize: 11 } },
    series: [{ type: 'pie', radius: ['67%', '84%'], center: ['50%', '50%'], label: { show: false }, stillShowZeroSum: false, data: [
      { name: '已出租', value: roomStats.value.rentedRoomCount },
      { name: '未出租', value: roomStats.value.availableRoomCount }
    ] }]
  })

  overdueRisk?.setOption({
    animation: false,
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, valueFormatter: value => `${value} 笔` },
    grid: { left: 82, right: 42, top: 10, bottom: 22 },
    xAxis: { type: 'value', minInterval: 1, splitNumber: 4, axisLabel: { color: '#98a2b3', fontSize: 11 }, splitLine: { lineStyle: { color: '#eef0f3', type: 'dashed' } } },
    yAxis: { type: 'category', inverse: true, axisLine: { show: false }, axisTick: { show: false }, axisLabel: { color: '#475467', fontSize: 12 }, data: overdueRiskData.value.map(item => item.name) },
    series: [{ type: 'bar', showBackground: true, backgroundStyle: { color: '#f7f8fa', borderRadius: 3 },
      data: overdueRiskData.value.map(item => ({ value: item.value, itemStyle: { color: { '已逾期': '#d93025', '7天内到期': '#d5a164', '正常未收': '#8c96a6' }[item.name], borderRadius: [0, 3, 3, 0] } })),
      barMaxWidth: 16, label: { show: true, position: 'right', color: '#475467', fontWeight: 600, formatter: '{c} 笔' }
    }]
  })
}

function resizeCharts() {
  charts.forEach(chart => chart.resize())
}

async function loadReport() {
  try {
    const data = await request.get('/ai/daily-report', { silent: true })
    const metrics = data?.metrics || {}
    report.value = {
      ...(data || {}),
      summary: data?.summaryText || data?.summary,
      unpaidBillCount: metrics.unpaidBillCount ?? data?.unpaidBillCount,
      paidBillCount: metrics.paidBillCount ?? data?.paidBillCount,
      todayPaidAmount: metrics.todayIncomeAmount ?? data?.todayPaidAmount
    }
  } catch {
    failedSections.value.push('运营摘要')
    report.value = {}
  }
}

async function loadRooms() {
  try {
    roomStats.value = await loadDashboardRoomStatistics((url, config) => request.get(url, config))
    roomStatsHint.value = roomStats.value.roomCount === 0 ? '暂无房源记录' : ''
  } catch (error) {
    roomStats.value = null
    roomStatsHint.value = error?.response?.status === 403 ? '暂无权限查看房源统计' : '房源统计加载失败，请刷新'
    failedSections.value.push('房源统计')
  }
}

async function loadBills() {
  try {
    const data = await request.get('/bills', { params: { page: 1, pageSize: 100 }, silent: true })
    bills.value = readPage(data).records
  } catch {
    failedSections.value.push('账单数据')
    bills.value = []
  }
}

async function loadContracts() {
  try {
    const data = await request.get('/contracts', { silent: true })
    contracts.value = Array.isArray(data) ? data : []
  } catch {
    failedSections.value.push('合同数据')
    contracts.value = []
  }
}

async function loadDashboard() {
  if (loading.value) return
  loading.value = true
  failedSections.value = []

  try {
    await Promise.allSettled([
      loadReport(),
      loadRooms(),
      loadBills(),
      loadContracts()
    ])
  } finally {
    loading.value = false
    updatedAt.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
    await nextTick()
    renderCharts()
  }
}

watch([allBills, report, roomStats], async () => {
  await nextTick()
  renderCharts()
})

onMounted(() => {
  loadDashboard()
  window.addEventListener('resize', resizeCharts)
  resizeObserver = new ResizeObserver(resizeCharts)
  if (revenueTrendRef.value?.parentElement) resizeObserver.observe(revenueTrendRef.value.parentElement)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  resizeObserver?.disconnect()
  charts.splice(0).forEach(chart => chart.dispose())
})
</script>

<style scoped>
.dashboard-page { min-width: 0; color: var(--dhic-text-primary); }
.page-header { display: flex; justify-content: space-between; align-items: center; gap: 16px; margin-bottom: 18px; }
.page-header h2 { margin: 0; font-size: 20px; font-weight: 600; line-height: 28px; }
.page-header p { margin: 4px 0 0; color: var(--dhic-text-secondary); font-size: 12px; }
.header-actions { display: flex; align-items: center; gap: 14px; }
.update-time { color: #8a94a3; font-size: 12px; font-variant-numeric: tabular-nums; }
.data-notice { padding: 10px 14px; margin-bottom: 12px; border: 1px solid #f4d9bd; background: #fffbf5; border-radius: 6px; color: #9a4d00; font-size: 12px; }
.kpi-grid { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 0; margin-bottom: 16px; border: 1px solid var(--dhic-border-color); border-radius: var(--dhic-radius-large); background: #fff; box-shadow: 0 1px 3px rgba(32,33,36,.035); overflow: hidden; }
.kpi-card { min-width: 0; position: relative; padding: 18px 18px 16px; border-right: 1px solid #eef0f3; }
.kpi-card:last-child { border-right: 0; }
.card-title { display: flex; align-items: center; justify-content: space-between; gap: 4px; color: #667085; font-size: 12px; }
.card-scope { padding: 1px 4px; border-radius: 3px; background: #f6f7f9; color: #8a94a3; font-size: 10px; white-space: nowrap; }
.card-value { margin-top: 12px; color: var(--dhic-text-primary); font-size: clamp(22px, 1.75vw, 30px); font-weight: 650; font-variant-numeric: tabular-nums; line-height: 1.2; letter-spacing: -.7px; white-space: nowrap; }
.card-desc { margin-top: 9px; color: #8a94a3; font-size: 11px; line-height: 16px; }
.metric-track { display: block; width: 100%; border: 0; appearance: none; height: 3px; margin-top: 10px; border-radius: 3px; background: #f0f1f3; overflow: hidden; }
.metric-track::-webkit-progress-bar { background: #f0f1f3; border-radius: 3px; }
.metric-track::-webkit-progress-value { background: #d5a164; border-radius: 3px; }
.metric-track::-moz-progress-bar { background: #d5a164; border-radius: 3px; }
.success .metric-track::-webkit-progress-value { background: #18a058; }
.success .metric-track::-moz-progress-bar { background: #18a058; }
.chart-grid { display: grid; grid-template-columns: repeat(12, minmax(0, 1fr)); gap: 14px; margin-bottom: 14px; }
.trend-panel, .risk-panel { grid-column: span 7; }
.source-panel, .room-panel { grid-column: span 5; }
.risk-panel { grid-column: 6 / 13; }
.room-panel { grid-column: 1 / 6; }
.chart { width: 100%; min-width: 0; }
.trend-chart, .source-breakdown, .chart-empty { height: 226px; }
.source-breakdown { display: grid; grid-template-columns: minmax(140px, .9fr) minmax(130px, 1fr); align-items: center; gap: 10px; }
.source-chart { height: 208px; }
.source-legend { list-style: none; margin: 0; padding: 0; max-height: 218px; overflow-y: auto; }
.source-legend li { display: flex; justify-content: space-between; align-items: center; gap: 8px; padding: 9px 0; border-bottom: 1px solid #f2f3f5; font-size: 12px; }
.source-legend li:last-child { border-bottom: 0; }
.legend-name { display: inline-flex; align-items: center; gap: 7px; color: #667085; white-space: nowrap; }
.legend-name i, .dot { display: inline-block; width: 7px; height: 7px; border-radius: 2px; flex-shrink: 0; }
.source-color-0 { background: #d93025; }
.source-color-1 { background: #475467; }
.source-color-2 { background: #b67b68; }
.source-color-3 { background: #8c96a6; }
.source-color-4 { background: #d5a164; }
.source-color-5 { background: #b8bfc9; }
.source-color-6 { background: #8f4540; }
.source-color-7 { background: #6a7976; }
.source-legend strong { text-align: right; font-size: 12px; font-weight: 600; font-variant-numeric: tabular-nums; }
.source-legend small { display: inline-block; min-width: 46px; margin-left: 7px; font-size: 11px; color: #8a94a3; font-weight: 400; }
.room-breakdown { display: grid; grid-template-columns: 1fr 1fr; gap: 24px; align-items: center; height: 148px; }
.room-chart { height: 148px; }
.room-empty { display: flex; flex-direction: column; align-items: center; justify-content: center; height: 124px; border-radius: 6px; background: #f8f9fb; color: #98a2b3; }
.room-empty span { font-size: 30px; font-weight: 500; }
.room-empty p { margin: 7px 0 0; font-size: 12px; }
.room-counts > div { display: flex; justify-content: space-between; padding: 9px 0; font-size: 12px; }
.room-counts > div > span { display: inline-flex; align-items: center; gap: 8px; color: #667085; }
.room-counts strong { font-size: 19px; font-weight: 600; font-variant-numeric: tabular-nums; }
.room-counts small { font-size: 11px; font-weight: 400; color: #98a2b3; }
.room-counts p { margin: 8px 0 0; padding-top: 10px; border-top: 1px solid #eef0f3; font-size: 12px; color: #667085; }
.room-counts b { float: right; font-weight: 600; color: #344054; }
.rented { background: #d93025; }
.vacant { background: #c5cbd3; }
.risk-chart { height: 148px; }
.chart-empty { display: flex; align-items: center; justify-content: center; color: #98a2b3; font-size: 12px; }
.detail-grid { display: grid; grid-template-columns: minmax(0, 1.5fr) minmax(0, 1fr); align-items: start; gap: 14px; }
.detail-grid > div { min-width: 0; }
.summary-card { margin-bottom: 14px; }
.summary-card :deep(.el-table) { font-size: 12px; font-variant-numeric: tabular-nums; }
.summary-card :deep(.el-table th.el-table__cell) { height: 34px; padding: 5px 0; font-size: 11px; }
.summary-card :deep(.el-table td.el-table__cell) { height: 36px; padding: 5px 0; }
.summary-card :deep(.el-tag) { font-size: 11px; height: 22px; }
.summary-card :deep(.el-table__empty-block) { min-height: 96px; }
.success { color: var(--dhic-success); }
.danger { color: var(--dhic-danger); }
.warning { color: var(--dhic-warning); }
.ai-summary { margin: 0; color: var(--dhic-text-primary); font-size: 13px; line-height: 1.8; }
@media (max-width: 1280px) {
  .kpi-card { padding: 16px 12px; }
  .card-scope { display: none; }
  .source-breakdown { grid-template-columns: minmax(110px, .8fr) 1fr; gap: 6px; }
  .source-legend small { display: block; margin-left: 0; min-width: 0; }
  .detail-grid { grid-template-columns: 1fr; }
}
@media (max-width: 1050px) {
  .kpi-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .kpi-card:nth-child(-n+3) { border-bottom: 1px solid #eef0f3; }
  .kpi-card:nth-child(3) { border-right: 0; }
  .card-scope { display: inline; }
  .card-value { font-size: 26px; }
  .trend-panel, .source-panel, .room-panel, .risk-panel { grid-column: 1 / -1; }
  .source-breakdown { grid-template-columns: minmax(120px, 1fr) 1.2fr; }
  .source-legend small { display: inline-block; min-width: 50px; margin-left: 10px; }
  .room-breakdown { grid-template-columns: minmax(120px, 1fr) 1.2fr; }
}
@media (max-width: 640px) {
  .page-header { flex-wrap: wrap; gap: 10px; }
  .header-actions { width: 100%; justify-content: space-between; }
  .kpi-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .kpi-card:nth-child(3) { border-right: 1px solid #eef0f3; }
  .kpi-card:nth-child(even) { border-right: 0; }
  .kpi-card:nth-child(4) { border-bottom: 1px solid #eef0f3; }
  .card-value { font-size: 24px; }
  .source-legend small { display: block; margin: 0; }
  .room-breakdown { gap: 14px; }
}
</style>
