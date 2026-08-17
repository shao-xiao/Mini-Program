<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="page-header">
          <div>
            <div class="page-title">财务看板</div>
            <div class="page-subtitle">服务端按真实账单、收款和发票数据聚合，不受列表分页影响</div>
          </div>
          <div class="page-actions">
            <el-date-picker
              v-model="billingPeriod"
              type="month"
              value-format="YYYY-MM"
              clearable
              placeholder="全部账期"
              @change="loadDashboard"
            />
            <el-button type="primary" :loading="loading" @click="loadDashboard">刷新</el-button>
          </div>
        </div>
      </template>

      <div class="summary-grid" v-loading="loading">
        <el-card v-for="item in summaryCards" :key="item.label" class="summary-card" shadow="never">
          <div class="summary-label">{{ item.label }}</div>
          <div class="summary-value" :class="item.className">{{ item.value }}</div>
          <div class="summary-desc">{{ item.desc }}</div>
        </el-card>
      </div>

      <div class="chart-grid">
        <el-card class="chart-card wide" shadow="never">
          <template #header>账期应收 / 实收趋势</template>
          <div ref="trendRef" class="chart"></div>
        </el-card>
        <el-card class="chart-card" shadow="never">
          <template #header>费用类型分布</template>
          <div ref="typeRef" class="chart"></div>
        </el-card>
      </div>

      <el-card shadow="never" class="table-card">
        <template #header>租户应收与欠费</template>
        <el-table :data="tenantStats" border stripe v-loading="loading">
          <el-table-column prop="tenantName" label="租户" min-width="180" />
          <el-table-column label="应收金额" width="160" align="right">
            <template #default="{ row }">¥ {{ formatMoney(row.receivableAmount) }}</template>
          </el-table-column>
          <el-table-column label="实收金额" width="160" align="right">
            <template #default="{ row }">¥ {{ formatMoney(row.receivedAmount) }}</template>
          </el-table-column>
          <el-table-column label="未收金额" width="160" align="right">
            <template #default="{ row }">
              <span :class="{ danger: money(row.unpaidAmount) > 0 }">¥ {{ formatMoney(row.unpaidAmount) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="风险状态" width="120">
            <template #default="{ row }">
              <el-tag v-if="money(row.unpaidAmount) > 0" type="warning">待收</el-tag>
              <el-tag v-else type="success">正常</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </el-card>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import request from '../../utils/request'

const loading = ref(false)
const billingPeriod = ref('')
const summary = ref({})
const monthly = ref([])
const tenantStats = ref([])
const chargeTypes = ref([])
const trendRef = ref(null)
const typeRef = ref(null)
const charts = []

function money(value) {
  const number = Number(value || 0)
  return Number.isFinite(number) ? number : 0
}

function formatMoney(value) {
  return money(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function shortMoney(value) {
  const number = money(value)
  return Math.abs(number) >= 10000 ? `${(number / 10000).toFixed(1)}万` : formatMoney(number)
}

const collectionRate = computed(() => {
  const receivable = money(summary.value.receivableAmount)
  return receivable > 0 ? Math.round(money(summary.value.receivedAmount) / receivable * 100) : 0
})

const summaryCards = computed(() => [
  { label: '应收金额', value: `¥ ${shortMoney(summary.value.receivableAmount)}`, desc: '仅审核通过账单', className: '' },
  { label: '实收金额', value: `¥ ${shortMoney(summary.value.receivedAmount)}`, desc: `收缴率 ${collectionRate.value}%`, className: 'success' },
  { label: '未收金额', value: `¥ ${shortMoney(summary.value.unpaidAmount)}`, desc: `${summary.value.pendingReviewCount || 0} 笔待审核`, className: 'warning' },
  { label: '逾期金额', value: `¥ ${shortMoney(summary.value.overdueAmount)}`, desc: `${summary.value.pendingInvoiceCount || 0} 笔待开票`, className: 'danger' },
  { label: '已开票金额', value: `¥ ${shortMoney(summary.value.invoicedAmount)}`, desc: '有效发票版本合计', className: 'success' }
])

function chargeTypeText(type) {
  return {
    RENT: '租金',
    PROPERTY_FEE: '物业费',
    PROPERTY: '物业费',
    WATER: '水费',
    ELECTRICITY: '电费',
    GAS: '燃气费',
    PARKING: '停车费',
    MEETING_ROOM: '会议室',
    WORK_ORDER: '维修/工单服务费',
    CLEANING: '保洁费',
    DEPOSIT: '押金',
    LATE_FEE: '滞纳金',
    ADJUSTMENT: '调账补差',
    OTHER: '其他'
  }[type] || type || '未分类'
}

function createChart(elementRef) {
  if (!elementRef.value) return null
  const chart = echarts.init(elementRef.value)
  charts.push(chart)
  return chart
}

function renderCharts() {
  charts.splice(0).forEach(chart => chart.dispose())
  const trend = createChart(trendRef)
  const type = createChart(typeRef)
  const orderedMonthly = [...monthly.value].reverse()
  trend?.setOption({
    animation: false,
    color: ['#d93025', '#4f6b58'],
    tooltip: { trigger: 'axis', valueFormatter: value => `¥ ${formatMoney(value)}` },
    legend: { top: 0, data: ['应收金额', '实收金额'] },
    grid: { left: 62, right: 22, top: 44, bottom: 28 },
    xAxis: { type: 'category', data: orderedMonthly.map(item => item.dimension || '-') },
    yAxis: { type: 'value', axisLabel: { formatter: shortMoney } },
    series: [
      { name: '应收金额', type: 'bar', barMaxWidth: 28, data: orderedMonthly.map(item => money(item.receivableAmount)) },
      { name: '实收金额', type: 'line', smooth: true, data: orderedMonthly.map(item => money(item.receivedAmount)) }
    ]
  })
  type?.setOption({
    animation: false,
    tooltip: { trigger: 'item', valueFormatter: value => `¥ ${formatMoney(value)}` },
    legend: { bottom: 0 },
    series: [{
      type: 'pie',
      radius: ['42%', '68%'],
      center: ['50%', '44%'],
      data: chargeTypes.value.map(item => ({
        name: chargeTypeText(item.chargeItemType),
        value: money(item.finalAmount)
      }))
    }]
  })
}

async function loadDashboard() {
  loading.value = true
  try {
    const data = await request.get('/finance/dashboard', {
      params: { billingPeriod: billingPeriod.value || undefined },
      silent: true
    })
    summary.value = data?.summary || {}
    monthly.value = Array.isArray(data?.monthly) ? data.monthly : []
    tenantStats.value = Array.isArray(data?.byTenant) ? data.byTenant : []
    chargeTypes.value = Array.isArray(data?.byChargeType) ? data.byChargeType : []
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || '加载财务看板失败')
  } finally {
    loading.value = false
    await nextTick()
    renderCharts()
  }
}

function resizeCharts() {
  charts.forEach(chart => chart.resize())
}

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
.page-container { padding: 16px; }
.page-header, .page-actions { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.page-title { font-size: 18px; font-weight: 650; }
.page-subtitle, .summary-label, .summary-desc { color: #7a7f87; font-size: 13px; }
.page-subtitle { margin-top: 5px; }
.summary-grid { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 10px; margin: 14px 0; }
.summary-card { min-height: 98px; }
.summary-value { margin-top: 9px; font-size: 22px; font-weight: 700; }
.summary-desc { margin-top: 6px; }
.chart-grid { display: grid; grid-template-columns: 2fr 1fr; gap: 12px; }
.chart { width: 100%; height: 300px; }
.table-card { margin-top: 12px; }
.success { color: #4f6b58; }
.warning { color: #a66b19; }
.danger { color: #d93025; }
@media (max-width: 1200px) {
  .summary-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}
</style>
