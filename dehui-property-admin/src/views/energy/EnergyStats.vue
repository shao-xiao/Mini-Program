<template>
  <div class="page-container" :class="{ 'page-container--embedded': props.embedded }">
    <el-card shadow="never">
      <template #header>
        <div class="page-header">
          <div>
            <div class="page-title">能耗统计</div>
            <div class="page-subtitle">{{ isTenantView ? '按月查看当前租户的水电气用量与结算费用' : '按表具、空间、租户和账单状态分析水电气用量' }}</div>
          </div>
          <div class="header-actions"><span class="updated-at">{{ loading ? '正在更新…' : updatedAt ? `更新于 ${updatedAt}` : '' }}</span><el-button type="primary" :loading="loading" @click="loadStats">刷新</el-button></div>
        </div>
      </template>

      <el-form :inline="true" :model="queryForm" class="query-form">
        <el-form-item label="能耗类型">
          <el-select v-model="queryForm.meterType" clearable placeholder="全部" class="query-select" @change="loadStats">
            <el-option label="电" value="ELECTRIC" />
            <el-option label="水" value="WATER" />
            <el-option label="燃气" value="GAS" />
          </el-select>
        </el-form-item>
        <el-form-item label="月份">
          <el-date-picker
            v-model="queryForm.periodMonth"
            type="month"
            value-format="YYYY-MM"
            placeholder="选择月份"
            clearable
            class="query-select"
            @change="loadStats"
          />
        </el-form-item>
        <el-form-item v-if="canFilterBuildings" label="楼宇">
          <el-select v-model="queryForm.buildingId" clearable placeholder="全部楼宇" class="query-select" @change="onBuildingChange">
            <el-option v-for="item in buildings" :key="item.id" :label="item.buildingName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="canFilterFloors" label="楼层">
          <el-select v-model="queryForm.floorId" clearable placeholder="全部楼层" class="query-select" @change="onFloorChange">
            <el-option v-for="item in floors" :key="item.id" :label="formatFloor(item)" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="canFilterRooms" label="房间">
          <el-select v-model="queryForm.roomId" clearable placeholder="全部房间" class="query-select" @change="loadStats">
            <el-option v-for="item in rooms" :key="item.id" :label="formatRoom(item)" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="!props.embedded" label="租户">
          <el-select v-model="queryForm.tenantId" clearable filterable placeholder="全部租户" class="query-select" @change="loadStats">
            <el-option v-for="item in tenants" :key="item.id" :label="item.tenantName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="!isTenantView" label="账单状态">
          <el-select v-model="queryForm.billStatus" clearable placeholder="全部" class="query-select" @change="loadStats">
            <el-option label="未生成" value="NOT_GENERATED" />
            <el-option label="已生成" value="GENERATED" />
            <el-option label="已入账" value="POSTED" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="!isTenantView" label="异常">
          <el-select v-model="queryForm.abnormalFlag" clearable placeholder="全部" class="query-select-small" @change="loadStats">
            <el-option label="异常" :value="true" />
            <el-option label="正常" :value="false" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadStats">分析</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>

      <el-alert
        v-if="errorMessage"
        class="stats-error"
        title="能耗统计加载失败"
        type="error"
        :closable="false"
        show-icon
      >
        <template #default>
          <span>{{ errorMessage }}</span>
          <el-button link type="primary" @click="loadStats">重试</el-button>
        </template>
      </el-alert>

      <div class="scope-summary"><span>{{ queryForm.periodMonth || '全部账期' }}</span><span>{{ stats.recordCount }} 条抄表记录</span><span v-if="stats.trend.length">有记录 {{ stats.trend.length }} 个月</span><span>当前主体与筛选范围</span></div>
      <div class="summary-grid" :class="{ 'tenant-summary': isTenantView }">
        <el-card v-for="item in summaryCards" :key="item.label" class="summary-card" shadow="never">
          <div class="summary-label">{{ item.label }}</div>
          <div class="summary-value" :class="item.className">{{ item.value }}</div>
          <div class="summary-desc">{{ item.desc }}</div>
        </el-card>
      </div>

      <el-card v-if="isTenantView" shadow="never" class="monthly-card">
        <template #header><div class="card-header-inline"><div><strong>每月用量与费用</strong><small>按账期汇总，选择月份可查看当月数据</small></div><span class="chart-meta">{{ monthlyRows.length }} 个账期</span></div></template>
        <WorkspaceDataGrid :loading="loading" :rows="monthlyRows" :columns="monthlyColumns" row-key="periodMonth" :show-index="false" density="compact" data-mode="client" empty-text="所选月份暂无抄表记录" aria-label="租户每月能耗明细" />
      </el-card>

      <div v-if="!isTenantView || stats.recordCount > 0" class="analysis-toolbar"><div><strong>用量分析</strong><span>按同一能耗类型比较 · {{ activeChartType.unit }}</span></div><el-segmented v-model="chartMeterType" :options="chartTypeOptions" size="small" /></div>
      <div v-if="!isTenantView || stats.recordCount > 0" class="chart-grid" :class="{ 'tenant-charts': isTenantView }">
        <el-card class="chart-card wide" shadow="never">
          <template #header><div class="card-header-inline"><div><strong>近 6 个账期用量</strong><small>以最近有记录的账期为终点，缺少记录的月份留空</small></div><span class="chart-meta">{{ activeChartType.label }}</span></div></template>
          <el-empty v-if="isTrendEmpty" :description="stats.recordCount ? '当前筛选暂无该类型的月度记录' : '当前主体与筛选范围暂无抄表记录'" :image-size="80" />
          <div v-else ref="trendRef" class="chart" role="img" :aria-label="`${activeChartType.label}近六个账期趋势`"></div>
        </el-card>

        <el-card v-if="!isTenantView" class="chart-card" shadow="never">
          <template #header>
            <div class="card-header-inline">
              <div><strong>结算费用构成</strong><small>以金额比较，水电气用量分别展示</small></div>
              <span class="chart-meta">人民币 · 元</span>
            </div>
          </template>
          <el-empty v-if="isStructureEmpty" description="当前范围暂无结算金额" :image-size="80" />
          <div v-else class="cost-breakdown"><div ref="typeRef" class="chart cost-chart" role="img" aria-label="各能耗类型的结算费用占比"></div><ul class="cost-legend"><li v-for="item in costStructure" :key="item.meterType"><span><i :class="`type-dot type-dot--${item.meterType}`"></i>{{ meterTypeText(item.meterType) }}</span><strong>¥ {{ formatNumber(item.settlementAmount) }}<small>{{ costPercent(item.settlementAmount) }}%</small></strong></li></ul></div>
        </el-card>

        <el-card v-if="!isTenantView" class="chart-card rank-card" shadow="never">
          <template #header><div class="card-header-inline"><div><strong>房间用量排行</strong><small>同类型用量 · 前 10 名<span v-if="roomRankRows.some(item => !item.roomId)"> · 含未关联记录</span></small></div><span class="chart-meta">{{ roomRankRows.filter(item => item.roomId).length }} 个房间 · {{ activeChartType.unit }}</span></div></template>
          <el-empty v-if="!roomRankRows.length" description="当前范围暂无该类型的房间用量" :image-size="80" />
          <div v-else ref="roomRankRef" class="chart"></div>
        </el-card>

        <el-card v-if="!isTenantView" class="chart-card rank-card" shadow="never">
          <template #header><div class="card-header-inline"><div><strong>楼宇用量排行</strong><small>同类型用量 · 前 10 名<span v-if="buildingRankRows.some(item => !item.buildingId)"> · 含未关联记录</span></small></div><span class="chart-meta">{{ buildingRankRows.filter(item => item.buildingId).length }} 栋楼宇 · {{ activeChartType.unit }}</span></div></template>
          <el-empty v-if="!buildingRankRows.length" description="当前范围暂无该类型的楼宇用量" :image-size="80" />
          <div v-else ref="buildingRankRef" class="chart"></div>
        </el-card>
      </div>

      <el-card v-if="isTenantView" shadow="never" class="reading-card chart-card">
        <template #header>
          <div class="card-header-inline">
            <div><strong>表具读数趋势</strong><small>按账期对比上次与本期读数；读数为表盘累计值，用量另按倍率计算</small></div>
            <el-select v-model="selectedReadingMeterId" filterable placeholder="选择表具" class="reading-meter-select" aria-label="读数趋势表具">
              <el-option v-for="meter in readingMeters" :key="meter.meterId" :value="meter.meterId" :label="`${meter.meterName || meter.meterNo} · ${meter.meterNo || meter.meterId}`" />
            </el-select>
          </div>
        </template>
        <el-alert v-if="readingError" :title="readingError" type="error" :closable="false" show-icon />
        <el-empty v-else-if="!selectedReadingMeter" description="当前类型与月份暂无表具读数" :image-size="64" />
        <template v-else>
          <div class="reading-meta"><span>{{ selectedReadingMeter.meterName }}</span><span>{{ selectedReadingMeter.meterNo }}</span><span>{{ meterTypeText(selectedReadingMeter.meterType) }} · {{ selectedReadingMeter.unit }}</span><span>{{ selectedReadingMeter.readings.length }} 条记录</span></div>
          <div ref="readingTrendRef" class="chart reading-chart" role="img" :aria-label="`${selectedReadingMeter.meterName}各账期上次与本期读数趋势`"></div>
          <WorkspaceDataGrid :loading="loading" :rows="readingDetailRows" :columns="readingColumns" row-key="id" :show-index="false" density="compact" data-mode="client" aria-label="表具各账期读数明细" />
        </template>
      </el-card>

      <el-card v-if="!isTenantView || stats.anomalies.length" shadow="never" class="table-card">
        <template #header>异常用量提示</template>
        <WorkspaceDataGrid
          :loading="loading"
          :rows="stats.anomalies"
          :columns="energyAnomalyColumns"
          row-key="id"
          density="compact"
          data-mode="client"
          empty-text="当前筛选暂无异常记录"
          :operation-column="{ width: 210, fixed: 'right', align: 'left' }"
          aria-label="能耗异常明细表格"
        >
          <template #operations="{ row }">
            <div class="anomaly-actions">
              <el-button v-if="hasPermission('energy:reading:update')" size="small" @click="setAnomalyStatus(row, 'CONFIRMED')">确认</el-button>
              <el-button v-if="hasPermission('energy:reading:update')" size="small" @click="setAnomalyStatus(row, 'IGNORED')">忽略</el-button>
              <el-button v-if="hasPermission('energy:reading:update')" size="small" type="success" @click="setAnomalyStatus(row, 'RESOLVED')">解决</el-button>
            </div>
          </template>
        </WorkspaceDataGrid>
      </el-card>
    </el-card>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import * as echarts from 'echarts'
import request from '../../utils/request'
import { hasPermission } from '../../utils/permission'
import { loadAllWorkspaceRecords } from '../../utils/workspaceExport'
import { readPage } from '../../utils/pagination'
import { createLatestRequestGuard } from '../../utils/requestLifecycle'
import { energyChartTypes, energyTrendWindow, energyRanking, energyTrendSeries, energyReadingMeters, energyReadingValue } from '../../utils/energyStatistics'
import { getEnergyStats, updateAnomalyStatus } from '../../api/energy'
import WorkspaceDataGrid from '../../components/workspace/WorkspaceDataGrid.vue'
import { energyAnomalyColumns, energyMeterTypeOptions } from '../../workspaceConfigs/energyWorkspaceConfig'

const props = defineProps({
  embedded: { type: Boolean, default: false },
  subjectTenantId: { type: [Number, String], default: null },
  subjectScope: { type: String, default: '' }
})

const loading = ref(false)
const errorMessage = ref('')
const chartMeterType = ref('ELECTRIC')
const updatedAt = ref('')
let resizeObserver
const charts = []
const isTenantView = computed(() => props.embedded && props.subjectScope !== 'property' && Number(props.subjectTenantId) > 0)
const canFilterBuildings = computed(() => !isTenantView.value && (hasPermission('building:view') || hasPermission('building:*')))
const canFilterFloors = computed(() => canFilterBuildings.value && (hasPermission('floor:view') || hasPermission('floor:*')))
const canFilterRooms = computed(() => canFilterFloors.value && (hasPermission('room:view') || hasPermission('room:*')))

const readingTrendRef = ref(null)
const readingRecords = ref([])
const readingError = ref('')
const selectedReadingMeterId = ref('')
const readingMeters = computed(() => energyReadingMeters(readingRecords.value, activeChartType.value.value))
const selectedReadingMeter = computed(() => readingMeters.value.find(meter => meter.meterId === selectedReadingMeterId.value) || readingMeters.value[0])
const readingDetailRows = computed(() => [...(selectedReadingMeter.value?.readings || [])].reverse())
const readingColumns = computed(() => [
  { key: 'periodMonth', label: '账期', minWidth: 110 },
  { key: 'readingDate', label: '抄表日期', minWidth: 120 },
  ...['previousReading', 'currentReading'].map((key, index) => ({ key, label: `${index ? '本期' : '上次'}读数（${selectedReadingMeter.value?.unit || ''}）`, type: 'number', decimals: 2, minWidth: 170, align: 'right', valueGetter: row => energyReadingValue(row[key]) })),
  { key: 'multiplier', label: '倍率', type: 'number', decimals: 2, minWidth: 90, align: 'right' }
])
const trendRef = ref(null)
const typeRef = ref(null)
const roomRankRef = ref(null)
const buildingRankRef = ref(null)

const buildings = ref([])
const floors = ref([])
const rooms = ref([])
const tenants = ref([])
const statsRequestGuard = createLatestRequestGuard()

const queryForm = reactive({
  meterType: '',
  periodMonth: '',
  buildingId: null,
  floorId: null,
  roomId: null,
  tenantId: null,
  billStatus: '',
  abnormalFlag: ''
})

const subjectQueryParams = computed(() => {
  if (props.subjectScope === 'property') return { tenantScope: 'PROPERTY' }
  const tenantId = Number(props.subjectTenantId)
  return Number.isFinite(tenantId) && tenantId > 0 ? { tenantId } : {}
})

const stats = reactive({
  recordCount: 0,
  electricUsage: 0,
  waterUsage: 0,
  gasUsage: 0,
  totalAmount: 0,
  averageUsage: 0,
  abnormalCount: 0,
  trend: [],
  typeStructure: [],
  roomRanking: [],
  buildingRanking: [],
  anomalies: []
})

const availableChartTypes = computed(() => energyChartTypes.filter(type => queryForm.meterType
  ? type.value === queryForm.meterType
  : !isTenantView.value || stats.typeStructure.some(row => row.meterType === type.value && number(row.recordCount) > 0)))
const chartTypeOptions = computed(() => availableChartTypes.value.map(type => ({ label: type.label, value: type.value })))
const activeChartType = computed(() => energyChartTypes.find(type => type.value === (queryForm.meterType || (availableChartTypes.value.some(type => type.value === chartMeterType.value) ? chartMeterType.value : availableChartTypes.value[0]?.value))) || energyChartTypes[0])
const trendWindow = computed(() => energyTrendWindow(stats.trend, queryForm.periodMonth))
const monthlyRows = computed(() => [...stats.trend].sort((a, b) => String(b.periodMonth).localeCompare(String(a.periodMonth))))
const monthlyColumns = computed(() => [
  { key: 'periodMonth', label: '账期', minWidth: 110 },
  ...availableChartTypes.value.map(type => ({ key: type.field, label: type.label, type: 'number', decimals: 2, minWidth: 160, align: 'right', valueGetter: row => number(row[type.countField]) > 0 ? row[type.field] : null })),
  { key: 'settlementAmount', label: '结算费用（元）', type: 'money', decimals: 2, minWidth: 170, align: 'right' },
  { key: 'recordCount', label: '抄表记录数', type: 'number', decimals: 0, minWidth: 110, align: 'right' }
])
const roomRankRows = computed(() => energyRanking(stats.roomRanking, activeChartType.value.value))
const buildingRankRows = computed(() => energyRanking(stats.buildingRanking, activeChartType.value.value))
const costStructure = computed(() => stats.typeStructure.filter(item => number(item.settlementAmount) > 0))
function costPercent(value) { return stats.totalAmount > 0 ? (number(value) / stats.totalAmount * 100).toFixed(1) : '0.0' }
const meterTypeLabelMap = new Map(energyMeterTypeOptions.map(option => [option.value, option.label]))

const summaryCards = computed(() => [
  { label: '记录数', value: stats.recordCount, desc: '当前筛选范围', className: '' },
  { meterType: 'ELECTRIC', label: '电用量', value: `${formatNumber(stats.electricUsage)} kWh`, desc: '电表用量', className: 'warning' },
  { meterType: 'WATER', label: '水用量', value: `${formatNumber(stats.waterUsage)} m³`, desc: '水表用量', className: 'primary' },
  { meterType: 'GAS', label: '燃气用量', value: `${formatNumber(stats.gasUsage)} m³`, desc: '燃气表用量', className: 'danger' },
  { label: '结算金额', value: `¥ ${formatNumber(stats.totalAmount)}`, desc: '当前筛选范围', className: '' },
  { label: '异常记录', value: stats.abnormalCount, desc: '待关注抄表记录', className: 'danger' }
].filter(item => !isTenantView.value || (item.meterType ? availableChartTypes.value.some(type => type.value === item.meterType) : item.label !== '异常记录' || stats.abnormalCount > 0)))

const isTrendEmpty = computed(() => !energyTrendSeries(trendWindow.value, activeChartType.value).some(value => value !== null))
const isStructureEmpty = computed(() => costStructure.value.length === 0)

async function loadStats() {
  const requestId = statsRequestGuard.start()
  loading.value = true
  try {
    const params = {
      meterType: queryForm.meterType || undefined,
      periodMonth: queryForm.periodMonth || undefined,
      buildingId: queryForm.buildingId || undefined,
      floorId: queryForm.floorId || undefined,
      roomId: queryForm.roomId || undefined,
      tenantId: queryForm.tenantId || undefined,
      billStatus: queryForm.billStatus || undefined,
      abnormalFlag: queryForm.abnormalFlag === '' ? undefined : queryForm.abnormalFlag,
      ...subjectQueryParams.value
    }
    const [statsResult, readingsResult] = await Promise.allSettled([
      getEnergyStats(params, { silent: true }),
      isTenantView.value ? loadAllWorkspaceRecords(page => request.get('/energy/readings', { params: { ...params, ...page }, silent: true })) : Promise.resolve([])
    ])
    if (!statsRequestGuard.isCurrent(requestId)) return
    readingRecords.value = readingsResult.status === 'fulfilled' ? readingsResult.value : []
    readingError.value = readingsResult.status === 'rejected' ? '表具读数加载失败，请缩小月份范围或刷新重试。' : ''
    if (!readingMeters.value.some(meter => meter.meterId === selectedReadingMeterId.value)) selectedReadingMeterId.value = ''
    if (statsResult.status === 'rejected') throw statsResult.reason
    const data = statsResult.value
    errorMessage.value = ''
    Object.assign(stats, {
      recordCount: data.recordCount || 0,
      electricUsage: data.electricUsage || 0,
      waterUsage: data.waterUsage || 0,
      gasUsage: data.gasUsage || 0,
      totalAmount: data.totalAmount || 0,
      averageUsage: data.averageUsage || 0,
      abnormalCount: data.abnormalCount || 0,
      trend: data.trend || [],
      typeStructure: data.typeStructure || [],
      roomRanking: data.roomRanking || [],
      buildingRanking: data.buildingRanking || [],
      anomalies: data.anomalies || []
    })
  } catch (error) {
    if (!statsRequestGuard.isCurrent(requestId)) return
    errorMessage.value = '未能读取当前主体的统计数据，请稍后重试。'
    Object.assign(stats, {
      recordCount: 0,
      electricUsage: 0,
      waterUsage: 0,
      gasUsage: 0,
      totalAmount: 0,
      averageUsage: 0,
      abnormalCount: 0,
      trend: [],
      typeStructure: [],
      roomRanking: [],
      buildingRanking: [],
      anomalies: []
    })
  } finally {
    if (statsRequestGuard.isCurrent(requestId)) {
      loading.value = false
      updatedAt.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
      await nextTick()
      renderCharts()
    }
  }
}

async function loadBaseData() {
  const [buildingResult, tenantResult] = await Promise.allSettled([
    canFilterBuildings.value ? request.get('/buildings', { silent: true }) : Promise.resolve([]),
    !props.embedded && hasPermission('tenant:view')
      ? request.get('/tenant/list', { silent: true })
      : Promise.resolve([])
  ])
  buildings.value = buildingResult.status === 'fulfilled' ? readPage(buildingResult.value).records : []
  tenants.value = tenantResult.status === 'fulfilled' ? readPage(tenantResult.value).records : []
}

async function onBuildingChange() {
  queryForm.floorId = null
  queryForm.roomId = null
  floors.value = queryForm.buildingId
    ? readPage(await request.get('/floors', { params: { building_id: queryForm.buildingId } })).records
    : []
  rooms.value = []
  loadStats()
}

async function onFloorChange() {
  queryForm.roomId = null
  rooms.value = queryForm.buildingId
    ? readPage(await request.get('/rooms', { params: { building_id: queryForm.buildingId, floor_id: queryForm.floorId || undefined, page: 1, pageSize: 100 } })).records
    : []
  loadStats()
}

function resetSearch() {
  queryForm.meterType = ''
  queryForm.periodMonth = ''
  queryForm.buildingId = null
  queryForm.floorId = null
  queryForm.roomId = null
  queryForm.tenantId = null
  queryForm.billStatus = ''
  queryForm.abnormalFlag = ''
  floors.value = []
  rooms.value = []
  loadStats()
}

async function setAnomalyStatus(row, status) {
  await updateAnomalyStatus(row.id, status)
  await loadStats()
}

function createChart(elRef) {
  if (!elRef.value || !elRef.value.clientWidth) return null
  const chart = echarts.init(elRef.value)
  charts.push(chart)
  return chart
}

function renderCharts() {
  charts.splice(0).forEach(chart => chart.dispose())

  const reading = createChart(readingTrendRef)
  const meter = selectedReadingMeter.value
  if (meter) reading?.setOption({
    animation: false, color: ['#8c96a6', '#d93025'],
    tooltip: { trigger: 'axis', valueFormatter: value => value == null ? '无读数' : `${formatNumber(value)} ${meter.unit || ''}` },
    legend: { top: 0, right: 12, textStyle: { color: '#667085', fontSize: 11 } },
    grid: { left: 78, right: 35, top: 45, bottom: 65 },
    xAxis: { type: 'category', data: meter.readings.map(row => row.periodMonth), axisLabel: { color: '#667085', fontSize: 11 }, axisLine: { lineStyle: { color: '#e4e7ec' } } },
    yAxis: { type: 'value', name: meter.unit, axisLabel: { color: '#98a2b3', fontSize: 11 }, splitLine: { lineStyle: { color: '#eef0f3', type: 'dashed' } } },
    dataZoom: [{ type: 'inside' }, { type: 'slider', height: 16, bottom: 12, startValue: Math.max(0, meter.readings.length - 12), endValue: meter.readings.length - 1 }],
    series: ['previousReading', 'currentReading'].map((key, index) => ({ name: index ? '本期读数' : '上次读数', type: 'line', symbolSize: 7, connectNulls: false, data: meter.readings.map(row => energyReadingValue(row[key])) }))
  })

  const trend = createChart(trendRef)
  const type = createChart(typeRef)
  const room = createChart(roomRankRef)
  const building = createChart(buildingRankRef)

  const chartType = activeChartType.value
  trend?.setOption({
    animation: false,
    color: [chartType.color],
    tooltip: { trigger: 'axis', valueFormatter: value => value == null ? '无记录' : `${formatNumber(value)} ${chartType.unit}` },
    grid: { left: 65, right: 24, top: 28, bottom: 28 },
    xAxis: { type: 'category', data: trendWindow.value.map(item => item.periodMonth), axisTick: { show: false }, axisLine: { lineStyle: { color: '#e4e7ec' } }, axisLabel: { color: '#667085', fontSize: 11 } },
    yAxis: { type: 'value', name: chartType.unit, nameTextStyle: { color: '#98a2b3', fontSize: 11 }, splitNumber: 3, splitLine: { lineStyle: { color: '#eef0f3', type: 'dashed' } }, axisLabel: { color: '#98a2b3', fontSize: 11 } },
    series: [{ name: chartType.label, type: 'line', symbolSize: 8, connectNulls: false, smooth: false, label: { show: true, position: 'top', formatter: params => params.value == null ? '' : formatNumber(params.value), color: '#475467', fontSize: 11 }, data: energyTrendSeries(trendWindow.value, chartType) }]
  })

  type?.setOption({
    animation: false,
    color: costStructure.value.map(item => energyChartTypes.find(type => type.value === item.meterType)?.color || '#8c96a6'),
    tooltip: { trigger: 'item', valueFormatter: value => `¥ ${formatNumber(value)}` },
    title: { text: '结算合计', subtext: `¥ ${formatNumber(stats.totalAmount)}`, left: 'center', top: '35%', textStyle: { fontSize: 11, fontWeight: 400, color: '#98a2b3' }, subtextStyle: { color: '#202124', fontSize: 17, fontWeight: 600 } },
    series: [{ type: 'pie', radius: ['65%', '83%'], center: ['50%', '50%'], label: { show: false }, stillShowZeroSum: false, itemStyle: { borderColor: '#fff', borderWidth: 2 }, data: costStructure.value.map(item => ({ name: meterTypeText(item.meterType), value: number(item.settlementAmount) })) }]
  })

  room?.setOption(rankBarOption(roomRankRows.value.map(item => ({ name: item.roomName || '未关联房间', value: item.usageAmount }))))
  building?.setOption(rankBarOption(buildingRankRows.value.map(item => ({ name: item.buildingName || '未关联楼宇', value: item.usageAmount }))))
}

function rankBarOption(rows) {
  const chartType = activeChartType.value
  return {
    animation: false,
    color: [chartType.color],
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, valueFormatter: value => `${formatNumber(value)} ${chartType.unit}` },
    grid: { left: 100, right: 75, top: 18, bottom: 24 },
    xAxis: { type: 'value', splitNumber: 3, axisLabel: { color: '#98a2b3', fontSize: 11 }, splitLine: { lineStyle: { color: '#eef0f3', type: 'dashed' } } },
    yAxis: { type: 'category', inverse: true, axisLine: { show: false }, axisTick: { show: false }, axisLabel: { color: '#475467', width: 84, overflow: 'truncate', fontSize: 12 }, data: rows.map(item => item.name) },
    series: [{ type: 'bar', barMaxWidth: 18, showBackground: true, backgroundStyle: { color: '#f7f8fa', borderRadius: 3 }, itemStyle: { borderRadius: [0, 3, 3, 0] }, label: { show: true, position: 'right', color: '#475467', fontSize: 11, formatter: params => formatNumber(params.value) }, data: rows.map(item => Number(item.value.toFixed(2))) }]
  }
}

function resizeCharts() {
  charts.forEach(chart => chart.resize())
}

function number(value) {
  const n = Number(value || 0)
  return Number.isFinite(n) ? n : 0
}

function meterTypeText(value) {
  return meterTypeLabelMap.get(value) || String(value || '未知类型')
}

function formatNumber(value) {
  return number(value).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })
}

function formatFloor(floor) {
  return floor.floorName || `${floor.floorNumber}F`
}

function formatRoom(room) {
  return room.roomName || room.roomNumber || `房间${room.id}`
}

watch(readingMeters, meters => {
  if (!meters.some(meter => meter.meterId === selectedReadingMeterId.value)) selectedReadingMeterId.value = meters[0]?.meterId || ''
})

watch([activeChartType, selectedReadingMeter], async () => {
  await nextTick()
  renderCharts()
})

watch(
  () => [props.subjectScope, props.subjectTenantId],
  () => {
    if (!props.embedded) return
    queryForm.tenantId = null
    if (isTenantView.value) {
      queryForm.buildingId = null
      queryForm.floorId = null
      queryForm.roomId = null
      queryForm.billStatus = ''
      queryForm.abnormalFlag = ''
    }
    loadStats()
  }
)

onMounted(async () => {
  await loadBaseData()
  await loadStats()
  window.addEventListener('resize', resizeCharts)
  resizeObserver = new ResizeObserver(resizeCharts)
  if (trendRef.value?.parentElement) resizeObserver.observe(trendRef.value.parentElement)
})

onBeforeUnmount(() => {
  statsRequestGuard.dispose()
  window.removeEventListener('resize', resizeCharts)
  resizeObserver?.disconnect()
  charts.splice(0).forEach(chart => chart.dispose())
})
</script>

<style scoped>
.page-container { padding: 20px; min-width: 0; }
.page-container--embedded { padding: 0; }
.page-container--embedded > :deep(.el-card) { border: 0; box-shadow: none; }
.page-container--embedded > :deep(.el-card > .el-card__header) { padding: 14px 18px; }
.page-container--embedded > :deep(.el-card > .el-card__body) { padding: 14px 18px 18px; }
.page-header { display: flex; justify-content: space-between; align-items: center; gap: 14px; min-height: 44px; }
.page-title { font-size: 16px; font-weight: 600; }
.page-subtitle { margin-top: 4px; font-size: 12px; color: #8a94a3; }
.header-actions { display: flex; align-items: center; gap: 12px; }
.updated-at { font-size: 11px; color: #98a2b3; white-space: nowrap; font-variant-numeric: tabular-nums; }
.query-form { display: flex; flex-wrap: wrap; align-items: center; gap: 8px 12px; margin: 0 0 14px; padding: 12px; border: 1px solid #eef0f3; border-radius: 6px; background: #fafbfc; }
.query-form :deep(.el-form-item) { margin: 0; }
.query-form :deep(.el-form-item__label) { font-size: 12px; padding-right: 8px; }
.query-select { width: 124px; }
.query-select-small { width: 100px; }
.query-form :deep(.el-date-editor) { width: 145px; }
.stats-error { margin: 8px 0 12px; }
.scope-summary { display: flex; align-items: center; gap: 0; margin: 0 0 10px; flex-wrap: wrap; font-size: 11px; color: #8a94a3; }
.scope-summary span + span::before { content: '·'; margin: 0 9px; color: #c5cbd3; }
.scope-summary span:first-child { color: #475467; font-weight: 500; }
.summary-grid { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 0; margin: 0 0 18px; border: 1px solid #e4e7ec; border-radius: 6px; overflow: hidden; }
.summary-card { min-width: 0; min-height: 102px; border: 0; border-right: 1px solid #eef0f3; border-radius: 0; box-shadow: none; background: #fff; }
.summary-card:last-child { border-right: 0; }
.summary-card :deep(.el-card__body) { padding: 16px 14px; }
.summary-label { color: #667085; font-size: 12px; }
.summary-value { margin-top: 10px; font-size: clamp(17px, 1.3vw, 24px); font-weight: 600; color: #202124; font-variant-numeric: tabular-nums; white-space: nowrap; }
.summary-desc { margin-top: 8px; color: #98a2b3; font-size: 11px; }
.analysis-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 12px; }
.analysis-toolbar strong { font-size: 13px; font-weight: 600; }
.analysis-toolbar span { margin-left: 10px; color: #8a94a3; font-size: 11px; }
.chart-grid { display: grid; grid-template-columns: repeat(12, minmax(0, 1fr)); gap: 14px; }
.chart-card { grid-column: span 5; min-width: 0; border-color: #e4e7ec; box-shadow: 0 1px 3px rgba(32,33,36,.025); }
.chart-card.wide { grid-column: span 7; }
.chart-card.rank-card { grid-column: span 6; }
.chart-card :deep(.el-card__header) { padding: 12px 16px; }
.chart-card :deep(.el-card__body) { padding: 10px 14px; }
.card-header-inline { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.card-header-inline strong { font-size: 13px; font-weight: 600; }
.card-header-inline small { display: block; margin-top: 4px; color: #8a94a3; font-size: 11px; font-weight: 400; }
.chart-meta { flex-shrink: 0; font-size: 11px; color: #667085; padding: 3px 7px; background: #f6f7f9; border-radius: 4px; }
.chart { width: 100%; height: 216px; }
.rank-card .chart { height: 178px; }
.chart-card :deep(.el-empty) { height: 216px; padding: 0; }
.chart-card :deep(.el-empty__description p) { font-size: 12px; color: #98a2b3; }
.rank-card :deep(.el-empty) { height: 178px; }
.cost-breakdown { display: grid; grid-template-columns: minmax(135px,1fr) minmax(130px,1fr); gap: 10px; align-items: center; height: 216px; }
.cost-chart { height: 210px; }
.cost-legend { margin: 0; padding: 0; list-style: none; }
.cost-legend li { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 12px 0; border-bottom: 1px solid #f0f1f3; font-size: 12px; }
.cost-legend li:last-child { border-bottom: 0; }
.cost-legend li > span { display: flex; gap: 7px; align-items: center; color: #667085; }
.cost-legend strong { font-size: 12px; font-weight: 600; text-align: right; font-variant-numeric: tabular-nums; }
.cost-legend small { display: block; color: #98a2b3; font-weight: 400; margin-top: 4px; font-size: 11px; }
.type-dot { display: inline-block; height: 7px; width: 7px; border-radius: 2px; }
.type-dot--ELECTRIC { background: #d93025; }
.type-dot--WATER { background: #6789a7; }
.type-dot--GAS { background: #c79a60; }
.reading-card { margin-top: 16px; }
.reading-meter-select { width: 300px; max-width: 100%; }
.reading-meta { display: flex; flex-wrap: wrap; gap: 12px; color: #667085; font-size: 11px; margin: 2px 0 12px; }
.reading-chart { height: 260px; }
.reading-card :deep(.el-table) { font-size: 12px; font-variant-numeric: tabular-nums; }
.table-card { margin-top: 14px; }
.monthly-card { margin: 0 0 18px; box-shadow: none; border-color: #e4e7ec; }
.monthly-card :deep(.el-card__header) { padding: 12px 16px; }
.monthly-card :deep(.el-card__body) { padding: 0 14px 8px; }
.monthly-card :deep(.el-table) { font-variant-numeric: tabular-nums; font-size: 12px; }
.tenant-summary { grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); }
.tenant-charts .chart-card.wide { grid-column: 1 / -1; }
.tenant-summary .summary-card { border-bottom: 0; }
.tenant-summary .summary-card:not(:last-child) { border-right: 1px solid #eef0f3; }
.anomaly-actions { display: flex; align-items: center; gap: 6px; white-space: nowrap; }
.primary { color: #6789a7; }
.warning { color: #b77925; }
.danger { color: #d93025; }
@media (max-width: 1250px) {
  .summary-grid { grid-template-columns: repeat(3,minmax(0,1fr)); }
  .summary-card:nth-child(-n+3) { border-bottom: 1px solid #eef0f3; }
  .summary-card:nth-child(3) { border-right: 0; }
  .chart-card, .chart-card.wide, .chart-card.rank-card { grid-column: 1 / -1; }
}
@media (max-width: 640px) {
  .page-header, .analysis-toolbar { flex-wrap: wrap; }
  .header-actions { width: 100%; justify-content: space-between; }
  .summary-grid { grid-template-columns: repeat(2,minmax(0,1fr)); }
  .summary-card:nth-child(3) { border-right: 1px solid #eef0f3; }
  .summary-card:nth-child(even) { border-right: 0; }
  .summary-card:nth-child(4) { border-bottom: 1px solid #eef0f3; }
  .summary-value { font-size: 18px; }
  .analysis-toolbar span { display: block; margin: 4px 0 0; }
  .card-header-inline { flex-wrap: wrap; gap: 6px; }
  .cost-breakdown { grid-template-columns: minmax(120px,1fr) minmax(100px,1fr); gap: 4px; }
  .cost-chart { height: 190px; }
}
</style>
