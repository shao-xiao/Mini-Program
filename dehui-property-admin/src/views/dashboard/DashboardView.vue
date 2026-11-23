<template>
  <div class="dashboard-page" :aria-busy="loading">
    <PageHeader title="首页驾驶舱" description="德汇创新中心运营 BI 总览">
      <template #actions>
        <span class="update-time" aria-live="polite">{{ loading ? '正在更新数据…' : updatedAt ? `更新于 ${updatedAt}` : '等待加载' }}</span>
        <el-button type="primary" :loading="loading" @click="loadDashboard">刷新数据</el-button>
      </template>
    </PageHeader>

    <div v-if="failedSections.length" class="data-notice" role="status">{{ failedSections.join('、') }}暂时无法加载，请稍后刷新。</div>
    <div class="kpi-grid" aria-label="核心运营指标">
      <div v-for="item in kpiCards" :key="item.label" class="kpi-card" :class="item.className">
        <div class="card-title">{{ item.label }}</div>
        <div class="card-value" :class="item.className">{{ item.value }}</div>
        <div class="card-desc">{{ item.desc }}</div>
        <progress v-if="item.progress !== undefined" class="metric-track" :value="Math.max(0, Math.min(100, item.progress))" max="100" aria-hidden="true"></progress>
      </div>
    </div>

    <div class="chart-grid">
      <DashboardPanel class="trend-panel" title="应收 / 实收趋势" subtitle="近 6 个月 · 按账期统计" :meta="bills ? `${monthlyTrend[0].month} — ${monthlyTrend.at(-1).month}` : ''">
        <div v-if="bills?.length" class="trend-body">
          <div class="trend-legend" role="group" aria-label="趋势图显示系列">
            <button v-for="series in trendSeries" :key="series.name" type="button" :aria-pressed="visibleSeries[series.name]"
              aria-controls="dashboard-revenue-trend" @click="toggleSeries(series.name)">
              <i :class="series.mark" aria-hidden="true"></i>{{ series.name }}
            </button>
          </div>
          <div id="dashboard-revenue-trend" ref="revenueTrendRef"
            class="chart trend-chart" role="img" aria-label="近六个月应收与实收金额趋势"></div>
          <div v-if="!visibleSeries['应收金额'] && !visibleSeries['实收金额']" class="trend-no-series chart-empty">请选择要查看的金额系列</div>
        </div>
        <div v-else class="chart-empty">{{ billHint || '暂无账单记录' }}</div>
      </DashboardPanel>

      <DashboardPanel class="source-panel" title="账单来源结构" subtitle="按账单金额占比" :meta="bills ? `${billSourceData.length} 类来源` : ''">
        <div v-if="sourceTotal > 0" class="source-breakdown">
          <div ref="billSourceRef" class="chart source-chart" role="img" aria-label="账单来源金额占比环形图"></div>
          <ul class="source-legend">
            <li v-for="(item, index) in billSourceData" :key="item.name">
              <span class="legend-name"><i :style="{ backgroundColor: sourceColors[index % sourceColors.length] }"></i>{{ item.name }}</span>
              <strong :title="`¥ ${formatMoney(item.value)}`">{{ formatShortMoney(item.value) }}</strong>
              <small>{{ sourcePercent(item.value) }}%</small>
            </li>
          </ul>
        </div>
        <div v-else class="chart-empty">{{ billHint || '暂无账单来源数据' }}</div>
      </DashboardPanel>

      <DashboardPanel class="room-panel" title="房源出租状态" subtitle="房源数量与出租占比" :meta="roomStats ? `共 ${roomTotal} 间` : ''">
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

      <DashboardPanel class="risk-panel" title="账单到期风险" subtitle="未支付账单 · 按到期日分布" :meta="bills ? `${finance.overdueBillCount} 笔已逾期` : ''" :alert="finance.overdueBillCount > 0">
        <div v-if="hasPendingBills" ref="overdueRiskRef" class="chart risk-chart" role="img" :aria-label="overdueRiskData.map(item => `${item.name}${item.value}笔`).join('，')"></div>
        <div v-else class="risk-empty">{{ billHint || (bills?.length ? '当前没有待收账单' : '暂无账单记录') }}</div>
      </DashboardPanel>
    </div>

    <div class="detail-grid">
      <div class="detail-grid__bills">
        <DashboardPanel class="summary-card" title="最近账单" subtitle="最近创建的 8 笔账单" :meta="bills ? `${recentBills.length} 笔` : ''">

          <el-table :data="recentBills" size="small" max-height="330" :empty-text="billHint || '暂无账单记录'" style="width: 100%">
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
        <DashboardPanel class="summary-card" title="合同到期预警" subtitle="未来 30 天内到期的生效合同" :meta="contracts ? `${expiringContracts.length} 份` : ''" :alert="expiringContracts.length > 0">

          <el-table :data="expiringContracts" size="small" max-height="330" :empty-text="contractHint || '未来 30 天暂无到期合同'" style="width: 100%">
            <el-table-column prop="contractNumber" label="合同编号" min-width="140" />
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

    <DashboardPanel class="summary-card" title="租户财务排行" subtitle="按未收金额从高到低排列" :meta="bills ? `${tenantFinanceList.length} 家租户` : ''">

      <el-table :empty-text="billHint || '暂无租户账单'" :data="tenantFinanceList" size="small" max-height="330" style="width: 100%">
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
            <span :class="{ warning: row.unpaidAmount > 0 }">
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
      <p class="ai-summary">{{ report.summaryText || report.summary || (failedSections.includes('运营摘要') ? '运营摘要加载失败' : loading ? '正在加载…' : '暂无运营摘要') }}</p>
    </DashboardPanel>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import request from '../../utils/request'
import PageHeader from '../../components/layout/PageHeader.vue'
import DashboardPanel from '../../components/dashboard/DashboardPanel.vue'
import { formatBillType } from '../../workspaceConfigs/financeWorkspaceConfig'
import { readPage } from '../../utils/pagination'
import { loadDashboardRoomStatistics } from '../../utils/dashboardRoomStatistics'

const loading = ref(false)
const report = ref({})
const bills = ref(null)
const contracts = ref(null)

const revenueTrendRef = ref(null)
const billSourceRef = ref(null)
const roomStatusRef = ref(null)
const overdueRiskRef = ref(null)

const trendSeries = [{ name: '应收金额', mark: 'bar-mark' }, { name: '实收金额', mark: 'line-mark' }]
const visibleSeries = ref({ '应收金额': true, '实收金额': true })

function toggleSeries(name) {
  visibleSeries.value[name] = !visibleSeries.value[name]
  echarts.getInstanceByDom(revenueTrendRef.value)?.dispatchAction({ type: 'legendToggleSelect', name })
}

const charts = []
let resizeObserver
const updatedAt = ref('')
const failedSections = ref([])
const billHint = computed(() => bills.value !== null ? '' : failedSections.value.includes('账单数据') ? '账单加载失败' : '正在加载账单…')
const contractHint = computed(() => contracts.value !== null ? '' : failedSections.value.includes('合同数据') ? '合同加载失败' : '正在加载合同…')
const roomStats = ref(null)
const roomStatsHint = ref('正在加载房源统计')
const sourceColors = ['#c84238', '#435a73', '#8f695a', '#678586', '#ac8242', '#81869e', '#814749', '#5a7761']
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
      sourceName: formatBillType(item.billType || 'OTHER'),
      paidAmount: money(item.paidAmount)
    }
  }

  const allBills = computed(() => {
    return (bills.value || []).map(normalizeBill)
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

  return (contracts.value || [])
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
    className: finance.value.monthReceived > 0 ? 'success' : ''
  },
  {
    label: '未收款金额',
    value: `¥ ${formatShortMoney(finance.value.unpaidAmount)}`,
    desc: '各账期未收金额合计',
    className: finance.value.unpaidAmount > 0 ? 'warning' : ''
  },
  {
    label: '本月收缴率',
    progress: finance.value.monthReceivable > 0 ? finance.value.collectionRate : undefined,
    value: finance.value.monthReceivable > 0 ? `${finance.value.collectionRate}%` : '—',
    desc: finance.value.monthReceivable > 0 ? '按本月账期统计' : '本月无应收金额',
    className: finance.value.monthReceivable > 0 && finance.value.collectionRate >= 80 ? 'success' : ''
  },
  {
    label: '房间出租率',
    roomMetric: true,
    progress: roomStats.value ? roomRentRate.value : undefined,
    value: roomStats.value ? `${roomRentRate.value}%` : '—',
    desc: roomStats.value ? `已租 ${roomStats.value.rentedRoomCount} / 共 ${roomTotal.value} 间` : roomStatsHint.value,
    className: ''
  },
  {
    label: '逾期账单数',
    value: finance.value.overdueBillCount,
    desc: '未支付且已过到期日',
    className: finance.value.overdueBillCount > 0 ? 'danger' : ''
  }
].map(card => !card.roomMetric && billHint.value
  ? { ...card, value: '—', desc: billHint.value, className: '', progress: undefined }
  : card))

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
    const key = item.sourceName
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

    if (item.dueDate && String(item.dueDate) < currentDate) {
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

const hasPendingBills = computed(() => overdueRiskData.value.some(bucket => bucket.value > 0))

function createChart(elRef) {
  if (!elRef.value) return null
  resizeObserver?.observe(elRef.value)
  const chart = echarts.init(elRef.value)
  charts.push(chart)
  return chart
}

function renderCharts() {
  resizeObserver?.disconnect()
  charts.splice(0).forEach(chart => chart.dispose())

  const revenueTrend = createChart(revenueTrendRef)
  const billSource = sourceTotal.value > 0 ? createChart(billSourceRef) : null
  const roomStatus = hasRoomStats.value ? createChart(roomStatusRef) : null
  const overdueRisk = createChart(overdueRiskRef)

  revenueTrend?.setOption({
    animation: false,
    color: ['#c84238', '#374c65'],
    textStyle: { fontFamily: 'sans-serif', color: '#475467', fontSize: 12 },
    tooltip: {
      trigger: 'axis', confine: true,
      valueFormatter: value => `¥ ${formatMoney(value)}`
    },
    legend: { show: false, selected: { ...visibleSeries.value } },
    grid: { left: 4, right: 12, top: 24, bottom: 4, outerBoundsMode: 'same', outerBoundsContain: 'axisLabel' },
    xAxis: { type: 'category', axisTick: { show: false }, axisLine: { lineStyle: { color: '#e4e7ec' } }, axisLabel: { color: '#475467', fontSize: 12, hideOverlap: true, formatter: value => `${value.slice(2, 4)}年${value.slice(5)}月` }, data: monthlyTrend.value.map(item => item.month) },
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
        labelLayout: { hideOverlap: true },
        label: {
          show: true,
          color: '#344054',
          position: 'top',
          formatter: params => (money(params.value) > 0 ? formatShortMoney(params.value) : '')
        }
      },
      {
        name: '实收金额',
        type: 'line',
        smooth: false,
        symbolSize: 6,
        lineStyle: { width: 2 },
        label: { show: false },
        data: monthlyTrend.value.map(item => item.received)
      }
    ]
  })

  billSource?.setOption({
    animation: false,
    color: sourceColors,
    tooltip: { trigger: 'item', confine: true, valueFormatter: value => `¥ ${formatMoney(value)}` },
    title: { text: '账单总额', subtext: formatShortMoney(sourceTotal.value), left: 'center', top: '35%', textStyle: { fontSize: 11, color: '#667085', fontWeight: 400 }, subtextStyle: { fontSize: 18, fontWeight: 600, color: '#202124' }, itemGap: 6 },
    series: [{ type: 'pie', radius: ['64%', '82%'], center: ['50%', '48%'], label: { show: false }, itemStyle: { borderColor: '#fff', borderWidth: 2 }, stillShowZeroSum: false, data: billSourceData.value }]
  })

  roomStatus?.setOption({
    animation: false,
    color: ['#c84238', '#c5cbd3'],
    tooltip: { trigger: 'item', confine: true, valueFormatter: value => `${value} 间` },
    title: { text: `${roomRentRate.value}%`, subtext: '出租率', left: 'center', top: '32%', textStyle: { color: '#202124', fontSize: 24, fontWeight: 600 }, subtextStyle: { color: '#475467', fontSize: 12 } },
    series: [{ type: 'pie', radius: ['67%', '84%'], center: ['50%', '50%'], label: { show: false }, stillShowZeroSum: false, data: [
      { name: '已出租', value: roomStats.value.rentedRoomCount },
      { name: '未出租', value: roomStats.value.availableRoomCount }
    ] }]
  })

  overdueRisk?.setOption({
    animation: false,
    tooltip: { trigger: 'axis', confine: true, axisPointer: { type: 'shadow' }, valueFormatter: value => `${value} 笔` },
    grid: { left: 82, right: 42, top: 10, bottom: 22 },
    xAxis: { type: 'value', minInterval: 1, splitNumber: 4, axisLabel: { color: '#596775', fontSize: 12 }, splitLine: { lineStyle: { color: '#eef0f3', type: 'dashed' } } },
    yAxis: { type: 'category', inverse: true, axisLine: { show: false }, axisTick: { show: false }, axisLabel: { color: '#475467', fontSize: 12 }, data: overdueRiskData.value.map(item => item.name) },
    series: [{ type: 'bar', showBackground: true, backgroundStyle: { color: '#f7f8fa', borderRadius: 3 },
      data: overdueRiskData.value.map(item => ({ value: item.value, itemStyle: { color: { '已逾期': '#b42348', '7天内到期': '#a76d19', '正常未收': '#8c96a6' }[item.name], borderRadius: [0, 3, 3, 0] } })),
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
    bills.value = null
  }
}

async function loadContracts() {
  try {
    const data = await request.get('/contracts', { silent: true })
    contracts.value = Array.isArray(data) ? data : []
  } catch {
    failedSections.value.push('合同数据')
    contracts.value = null
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
  }
}

watch([allBills, roomStats], renderCharts, { flush: 'post' })

onMounted(() => {
  loadDashboard()
  resizeObserver = new ResizeObserver(resizeCharts)
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  charts.splice(0).forEach(chart => chart.dispose())
})
</script>

<style scoped>
.dashboard-page {
  container-type: inline-size;
  min-width: 0;
  gap: var(--dhic-section-gap);
  color: var(--dhic-text-primary);
  font-variant-numeric: tabular-nums;
}
.update-time, .card-desc, .source-legend small, .room-counts small {
  color: var(--dhic-text-secondary);
  font-size: 12px;
}
.data-notice {
  padding: 8px 12px;
  border-inline-start: 3px solid var(--dhic-warning);
  background: var(--dhic-warning-light);
  color: var(--dhic-text-primary);
  border-radius: var(--dhic-radius-small);
  font-size: 13px;
}
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  overflow: hidden;
  gap: 1px;
  background: var(--dhic-border-color-light);
  border: 1px solid var(--dhic-border-color);
  border-radius: var(--dhic-radius-large);
}
.kpi-card { min-width: 0; padding: 14px 16px; background: var(--dhic-card-bg); }
.card-title { color: var(--dhic-text-regular); font-size: 13px; font-weight: 550; }
.card-value {
  margin: 6px 0;
  color: var(--dhic-text-primary);
  font-size: clamp(22px, 2.2cqi, 30px);
  font-weight: 650;
  line-height: 1.25;
  letter-spacing: -.025em;
  white-space: nowrap;
}
.card-desc { line-height: 1.5; }
.metric-track {
  display: block; width: 100%; height: 3px; appearance: none;
  margin-top: 8px; border: 0; border-radius: 4px;
  background: var(--dhic-border-color-light); overflow: hidden;
}
.metric-track::-webkit-progress-bar { background: var(--dhic-border-color-light); }
.metric-track::-webkit-progress-value { background: var(--dhic-text-secondary); }
.metric-track::-moz-progress-bar { background: var(--dhic-text-secondary); }
.success .metric-track::-webkit-progress-value { background: var(--dhic-success); }
.success .metric-track::-moz-progress-bar { background: var(--dhic-success); }
.chart-grid { display: grid; grid-template-columns: repeat(12, minmax(0, 1fr)); gap: var(--dhic-section-gap); }
.trend-panel, .risk-panel { grid-column: span 7; }
.source-panel, .room-panel { grid-column: span 5; }
.chart { width: 100%; min-width: 0; }
.source-breakdown, .chart-empty { height: 204px; }
.trend-body { position: relative; }
.trend-no-series.chart-empty { position: absolute; inset: 28px 0 0; height: auto; background: var(--dhic-card-bg); }
.trend-legend { display: flex; justify-content: flex-end; gap: 4px; }
.trend-legend button {
  display: inline-flex; align-items: center; gap: 6px; height: 28px; padding: 0 7px;
  border: 0; border-radius: var(--dhic-radius-small); background: transparent;
  color: var(--dhic-text-regular); font: inherit; font-size: 12px; cursor: pointer;
}
.trend-legend button:hover { background: var(--el-fill-color-light); }
.trend-legend button:active { background: var(--dhic-border-color-light); }
.trend-legend button:focus-visible { outline: 2px solid var(--dhic-brand-red); outline-offset: -2px; }
.trend-legend button[aria-pressed="false"] { color: var(--dhic-text-secondary); text-decoration: line-through; }
.trend-legend [aria-pressed="false"] i { opacity: .45; }
.bar-mark { width: 12px; height: 8px; border-radius: 2px; background: #c84238; }
.line-mark { position: relative; width: 14px; height: 2px; background: #374c65; }
.line-mark::before { content: ''; position: absolute; inset: -2px auto auto 4px; width: 6px; height: 6px; box-sizing: border-box; background: white; border: 2px solid #374c65; border-radius: 50%; }
.trend-chart { height: 176px; }
.source-breakdown { display: grid; grid-template-columns: minmax(100px, .82fr) minmax(150px, 1fr); align-items: center; gap: 12px; }
.source-chart { height: 100%; }
.source-legend { list-style: none; margin: 0; padding: 0; max-height: 100%; overflow-y: auto; }
.source-legend li {
  display: grid; grid-template-columns: minmax(0, 1fr) max-content 5ch;
  gap: 8px; align-items: center; min-height: 31px; padding: 3px 0;
  border-bottom: 1px solid var(--dhic-border-color-light); font-size: 12px;
}
.source-legend li:last-child { border-bottom: 0; }
.legend-name { display: flex; align-items: center; gap: 6px; min-width: 0; overflow-wrap: anywhere; color: var(--dhic-text-regular); }
.legend-name i, .dot { flex: none; display: inline-block; width: 8px; height: 8px; border-radius: 2px; }
.source-legend strong, .source-legend small { text-align: end; white-space: nowrap; }
.source-legend strong { font-weight: 600; }
.room-breakdown { display: grid; grid-template-columns: minmax(96px, .82fr) minmax(120px, 1fr); gap: 16px; align-items: center; }
.room-chart, .risk-chart, .room-empty, .risk-empty { height: 132px; }
.room-empty, .chart-empty, .risk-empty {
  display: grid; place-content: center; justify-items: center;
  color: var(--dhic-text-secondary); font-size: 13px;
}
.room-empty span { font-size: 22px; }
.room-empty p { margin: 3px 0 0; }
.room-counts > div { display: flex; align-items: baseline; justify-content: space-between; padding: 5px 0; }
.room-counts > div > span { display: inline-flex; align-items: center; gap: 8px; color: var(--dhic-text-regular); font-size: 12px; }
.room-counts strong { font-size: 18px; font-weight: 600; }
.room-counts small { font-weight: 400; }
.room-counts p { display: flex; justify-content: space-between; margin: 6px 0 0; padding-top: 8px; border-top: 1px solid var(--dhic-border-color-light); font-size: 12px; color: var(--dhic-text-secondary); }
.room-counts b { font-weight: 600; color: var(--dhic-text-primary); }
.rented { background: #c84238; }
.vacant { background: #c5cbd3; }
.detail-grid { display: grid; grid-template-columns: minmax(0, 1.5fr) minmax(0, 1fr); align-items: start; gap: var(--dhic-section-gap); }
.detail-grid > div { min-width: 0; }
.summary-card :deep(.el-table) { font-variant-numeric: tabular-nums; }
.success { color: var(--dhic-success); }
.danger { color: var(--dhic-danger); }
.warning { color: var(--dhic-warning); }
.ai-summary { margin: 0; font-size: 13px; line-height: 1.6; }

@container (max-width: 1160px) {
  .detail-grid { grid-template-columns: 1fr; }
  .kpi-card { padding: 12px; }
}
@container (max-width: 1000px) {
  .kpi-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .source-breakdown { grid-template-columns: minmax(96px, .75fr) minmax(140px, 1fr); gap: 6px; }
  .source-legend li { gap: 4px; }
}
@container (max-width: 760px) {
  .trend-panel, .source-panel, .room-panel, .risk-panel { grid-column: 1 / -1; }
  .source-breakdown { grid-template-columns: minmax(96px, .8fr) minmax(140px, 1fr); }
}
@container (max-width: 500px) {
  .kpi-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .card-value { font-size: 22px; }
}
</style>
