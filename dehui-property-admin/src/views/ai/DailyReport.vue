<template>
  <div class="daily-report-page">
    <div class="page-header">
      <div>
        <h2>AI运营日报</h2>
        <p>{{ reportDateText }} · {{ generatedAtText }}</p>
      </div>
      <div class="header-actions">
        <el-tag :type="riskTagType(report.riskLevel)" effect="light">
          {{ riskLevelText(report.riskLevel) }}
        </el-tag>
        <el-button type="primary" :loading="refreshing" :disabled="loading || refreshing" @click="refreshReport">
          刷新日报
        </el-button>
      </div>
    </div>

    <el-alert
      v-if="errorMessage"
      class="page-alert"
      type="error"
      :title="errorMessage"
      show-icon
      :closable="false"
    />

    <el-skeleton v-if="loading" :rows="8" animated />

    <el-empty v-else-if="!report.id" description="暂无日报数据" />

    <template v-else>
      <el-card class="summary-card">
        <template #header>
          <span>运营摘要</span>
        </template>
        <p class="summary-text">{{ textOf(report.summaryText) }}</p>
      </el-card>

      <section class="section">
        <div class="section-title">核心指标</div>
        <el-row :gutter="16">
          <el-col v-for="item in roomCards" :key="item.label" :xs="24" :sm="12" :md="8" :lg="4">
            <el-card class="metric-card">
              <div class="card-title">{{ item.label }}</div>
              <div class="card-value" :class="item.className">{{ item.value }}</div>
            </el-card>
          </el-col>
        </el-row>
      </section>

      <el-row :gutter="16" class="section">
        <el-col :xs="24" :lg="8">
          <el-card>
            <template #header>
              <span>财务收缴</span>
            </template>
            <div v-for="item in financeRows" :key="item.label" class="metric-row">
              <span>{{ item.label }}</span>
              <strong :class="item.className">{{ item.value }}</strong>
            </div>
          </el-card>
        </el-col>

        <el-col :xs="24" :lg="8">
          <el-card>
            <template #header>
              <span>工单运营</span>
            </template>
            <div v-for="item in workOrderRows" :key="item.label" class="metric-row">
              <span>{{ item.label }}</span>
              <strong :class="item.className">{{ item.value }}</strong>
            </div>
          </el-card>
        </el-col>

        <el-col :xs="24" :lg="8">
          <el-card>
            <template #header>
              <span>设备巡检</span>
            </template>
            <div v-for="item in inspectionRows" :key="item.label" class="metric-row">
              <span>{{ item.label }}</span>
              <strong :class="item.className">{{ item.value }}</strong>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="16" class="section">
        <el-col :xs="24" :lg="12">
          <el-card>
            <template #header>
              <span>AI风险提醒</span>
            </template>
            <el-empty v-if="riskItems.length === 0" description="暂无风险提醒" />
            <div v-for="item in riskItems" :key="item.type + item.title" class="analysis-item">
              <div class="analysis-main">
                <div class="analysis-title">
                  <span>{{ textOf(item.title) }}</span>
                  <el-tag :type="riskTagType(item.level)" size="small">{{ riskLevelText(item.level) }}</el-tag>
                </div>
                <p>{{ textOf(item.description) }}</p>
              </div>
              <el-button v-if="item.targetUrl" class="action-button" type="primary" size="small" @click="goTarget(item.targetUrl)">
                去处理
              </el-button>
            </div>
          </el-card>
        </el-col>

        <el-col :xs="24" :lg="12">
          <el-card>
            <template #header>
              <span>今日行动建议</span>
            </template>
            <el-empty v-if="actionItems.length === 0" description="暂无行动建议" />
            <div v-for="item in actionItems" :key="item.priority + item.title" class="analysis-item">
              <div class="analysis-main">
                <div class="analysis-title">
                  <span>{{ textOf(item.title) }}</span>
                  <el-tag :type="priorityTagType(item.priority)" size="small">{{ priorityText(item.priority) }}</el-tag>
                </div>
                <p>{{ textOf(item.description) }}</p>
              </div>
              <el-button v-if="item.targetUrl" class="action-button" type="primary" size="small" @click="goTarget(item.targetUrl)">
                去处理
              </el-button>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <el-card class="section">
        <template #header>
          <span>历史快照</span>
        </template>
        <el-table :data="history" empty-text="暂无历史日报" @row-click="loadHistoryDetail">
          <el-table-column prop="reportDate" label="日期" min-width="120" />
          <el-table-column label="风险等级" min-width="100">
            <template #default="{ row }">
              <el-tag :type="riskTagType(row.riskLevel)" size="small">{{ riskLevelText(row.riskLevel) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="生成时间" min-width="170">
            <template #default="{ row }">
              {{ formatDateTime(row.generatedAt) }}
            </template>
          </el-table-column>
          <el-table-column prop="summaryText" label="摘要" min-width="360" show-overflow-tooltip />
        </el-table>
      </el-card>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getDailyReport,
  getDailyReportDetail,
  getDailyReportHistory,
  refreshDailyReport
} from '../../api/ai'

const router = useRouter()
const loading = ref(false)
const refreshing = ref(false)
const errorMessage = ref('')
const report = ref({})
const history = ref([])

const metrics = computed(() => report.value.metrics || {})
const riskItems = computed(() => Array.isArray(report.value.riskItems) ? report.value.riskItems : [])
const actionItems = computed(() => Array.isArray(report.value.actionItems) ? report.value.actionItems : [])

const reportDateText = computed(() => report.value.reportDate || '今日')
const generatedAtText = computed(() => report.value.generatedAt ? formatDateTime(report.value.generatedAt) : '尚未生成')

const roomCards = computed(() => [
  { label: '房间总数', value: numberOf('roomTotal') },
  { label: '已出租房间', value: numberOf('roomRented'), className: 'success' },
  { label: '可租房间', value: numberOf('roomAvailable') },
  { label: '出租率', value: formatPercent(valueOf('occupancyRate')), className: valueOf('occupancyRate') < 60 ? 'warning' : 'success' },
  { label: '活跃合同', value: numberOf('activeContractCount') }
])

const financeRows = computed(() => [
  { label: '已支付账单', value: numberOf('paidBillCount'), className: 'success' },
  { label: '未支付账单', value: numberOf('unpaidBillCount'), className: numberOf('unpaidBillCount') > 0 ? 'warning' : '' },
  { label: '逾期账单', value: numberOf('overdueBillCount'), className: numberOf('overdueBillCount') > 0 ? 'danger' : '' },
  { label: '今日收款', value: formatMoney(valueOf('todayIncomeAmount')) },
  { label: '本月收款', value: formatMoney(valueOf('monthIncomeAmount')) }
])

const workOrderRows = computed(() => [
  { label: '今日新增工单', value: numberOf('todayWorkOrderCount') },
  { label: '处理中工单', value: numberOf('processingWorkOrderCount'), className: numberOf('processingWorkOrderCount') > 0 ? 'warning' : '' },
  { label: '高优先级工单', value: numberOf('highPriorityWorkOrderCount'), className: numberOf('highPriorityWorkOrderCount') > 0 ? 'danger' : '' },
  { label: '超时工单', value: numberOf('overdueWorkOrderCount'), className: numberOf('overdueWorkOrderCount') > 0 ? 'danger' : '' }
])

const inspectionRows = computed(() => [
  { label: '设备总数', value: numberOf('deviceTotal') },
  { label: '故障设备', value: numberOf('faultDeviceCount'), className: numberOf('faultDeviceCount') > 0 ? 'danger' : '' },
  { label: '异常巡检', value: numberOf('abnormalInspectionCount'), className: numberOf('abnormalInspectionCount') > 0 ? 'warning' : '' },
  { label: '今日巡检完成率', value: formatPercent(valueOf('todayInspectionCompletionRate')) }
])

function valueOf(key) {
  return Number(metrics.value[key] ?? 0)
}

function numberOf(key) {
  return Number(metrics.value[key] ?? 0)
}

function textOf(value) {
  return value || '暂无数据'
}

function formatMoney(value) {
  const n = Number(value || 0)
  return `¥${Number.isFinite(n) ? n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) : '0.00'}`
}

function formatPercent(value) {
  const n = Number(value || 0)
  return `${Number.isFinite(n) ? n.toFixed(2) : '0.00'}%`
}

function formatDateTime(value) {
  if (!value) return '暂无数据'
  return String(value).replace('T', ' ').slice(0, 19)
}

function riskTagType(level) {
  if (level === 'CRITICAL') return 'danger'
  if (level === 'WARNING') return 'warning'
  return 'success'
}

function riskLevelText(level) {
  return {
    NORMAL: '正常',
    WARNING: '关注',
    CRITICAL: '严重'
  }[level] || '正常'
}

function priorityTagType(priority) {
  if (priority === 'HIGH') return 'danger'
  if (priority === 'MEDIUM') return 'warning'
  return 'primary'
}

function priorityText(priority) {
  return {
    HIGH: '高优先级',
    MEDIUM: '中优先级',
    LOW: '低优先级'
  }[priority] || '低优先级'
}

async function loadReport() {
  loading.value = true
  errorMessage.value = ''
  try {
    report.value = await getDailyReport()
    await loadHistory()
  } catch (error) {
    report.value = {}
    errorMessage.value = error?.message || '日报加载失败'
  } finally {
    loading.value = false
  }
}

async function refreshReport() {
  if (refreshing.value) return
  refreshing.value = true
  errorMessage.value = ''
  try {
    report.value = await refreshDailyReport()
    await loadHistory()
    ElMessage.success('日报已刷新')
  } catch (error) {
    errorMessage.value = error?.message || '日报刷新失败'
  } finally {
    refreshing.value = false
  }
}

async function loadHistory() {
  try {
    const data = await getDailyReportHistory()
    history.value = Array.isArray(data) ? data : []
  } catch {
    history.value = []
  }
}

async function loadHistoryDetail(row) {
  if (!row?.id) return
  try {
    report.value = await getDailyReportDetail(row.id)
  } catch {
    ElMessage.error('历史日报加载失败')
  }
}

function goTarget(targetUrl) {
  router.push(targetUrl)
}

onMounted(loadReport)
</script>

<style scoped>
.daily-report-page {
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

.header-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

.page-alert {
  margin-bottom: 16px;
}

.summary-card,
.section {
  margin-bottom: 20px;
}

.summary-text {
  margin: 0;
  line-height: 1.8;
  color: #303133;
}

.section-title {
  margin-bottom: 12px;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.metric-card {
  margin-bottom: 16px;
}

.card-title {
  color: #666;
  font-size: 14px;
}

.card-value {
  margin-top: 10px;
  font-size: 26px;
  font-weight: bold;
}

.metric-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid #f0f0f0;
}

.metric-row:last-child {
  border-bottom: none;
}

.analysis-item {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 0;
  border-bottom: 1px solid #f0f0f0;
}

.analysis-item:last-child {
  border-bottom: none;
}

.analysis-main {
  min-width: 0;
}

.analysis-title {
  display: flex;
  gap: 8px;
  align-items: center;
  font-weight: 600;
}

.analysis-main p {
  margin: 8px 0 0;
  color: #606266;
  line-height: 1.6;
}

.action-button {
  flex: 0 0 auto;
  min-width: 72px;
  color: #fff !important;
}

.action-button:hover,
.action-button:focus {
  color: #fff !important;
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
</style>
