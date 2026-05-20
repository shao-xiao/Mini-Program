<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="page-header">
          <div>
            <div class="page-title">能耗统计</div>
            <div class="page-subtitle">按表具、空间、租户和账单状态分析水电气用量</div>
          </div>
          <el-button type="primary" :loading="loading" @click="loadStats">刷新</el-button>
        </div>
      </template>

      <el-form :inline="true" :model="queryForm" class="query-form">
        <el-form-item label="能耗类型">
          <el-select v-model="queryForm.meterType" clearable placeholder="全部" class="query-select">
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
          />
        </el-form-item>
        <el-form-item label="楼宇">
          <el-select v-model="queryForm.buildingId" clearable placeholder="全部楼宇" class="query-select" @change="onBuildingChange">
            <el-option v-for="item in buildings" :key="item.id" :label="item.buildingName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="楼层">
          <el-select v-model="queryForm.floorId" clearable placeholder="全部楼层" class="query-select" @change="onFloorChange">
            <el-option v-for="item in floors" :key="item.id" :label="formatFloor(item)" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="房间">
          <el-select v-model="queryForm.roomId" clearable placeholder="全部房间" class="query-select">
            <el-option v-for="item in rooms" :key="item.id" :label="formatRoom(item)" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="租户">
          <el-select v-model="queryForm.tenantId" clearable filterable placeholder="全部租户" class="query-select">
            <el-option v-for="item in tenants" :key="item.id" :label="item.tenantName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="账单状态">
          <el-select v-model="queryForm.billStatus" clearable placeholder="全部" class="query-select">
            <el-option label="未生成" value="NOT_GENERATED" />
            <el-option label="已生成" value="GENERATED" />
            <el-option label="已入账" value="POSTED" />
          </el-select>
        </el-form-item>
        <el-form-item label="异常">
          <el-select v-model="queryForm.abnormalFlag" clearable placeholder="全部" class="query-select-small">
            <el-option label="异常" :value="true" />
            <el-option label="正常" :value="false" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadStats">分析</el-button>
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
          <template #header>近6个月趋势</template>
          <el-empty v-if="isTrendEmpty" description="暂无趋势数据" :image-size="80" />
          <div v-else ref="trendRef" class="chart"></div>
        </el-card>

        <el-card class="chart-card" shadow="never">
          <template #header>
            <div class="card-header-inline">
              <span>能耗类型结构</span>
              <el-segmented v-model="structureMode" :options="structureModeOptions" size="small" />
            </div>
          </template>
          <el-empty v-if="isStructureEmpty" description="暂无结构数据" :image-size="80" />
          <div v-else ref="typeRef" class="chart"></div>
        </el-card>

        <el-card class="chart-card" shadow="never">
          <template #header>房间用量排行</template>
          <el-empty v-if="!stats.roomRanking.length" description="暂无房间排行" :image-size="80" />
          <div v-else ref="roomRankRef" class="chart"></div>
        </el-card>

        <el-card class="chart-card" shadow="never">
          <template #header>楼宇用量排行</template>
          <el-empty v-if="!stats.buildingRanking.length" description="暂无楼宇排行" :image-size="80" />
          <div v-else ref="buildingRankRef" class="chart"></div>
        </el-card>
      </div>

      <el-card shadow="never" class="table-card">
        <template #header>异常用量提示</template>
        <el-table v-loading="loading" :data="stats.anomalies" border stripe>
          <el-table-column prop="meterNo" label="表号" min-width="120" />
          <el-table-column label="类型" width="90">
            <template #default="{ row }">{{ meterTypeText(row.meterType) }}</template>
          </el-table-column>
          <el-table-column prop="periodMonth" label="账期" width="100" />
          <el-table-column prop="buildingName" label="楼宇" min-width="130" />
          <el-table-column prop="roomName" label="房间" min-width="120" />
          <el-table-column label="本期用量" width="120" align="right">
            <template #default="{ row }">{{ formatNumber(row.usageAmount) }}</template>
          </el-table-column>
          <el-table-column label="异常原因" min-width="130">
            <template #default="{ row }">
              <el-tag type="danger">{{ abnormalReasonText(row.abnormalReason) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="{ row }">{{ anomalyStatusText(row.abnormalStatus) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="210" fixed="right">
            <template #default="{ row }">
              <el-button size="small" @click="setAnomalyStatus(row, 'CONFIRMED')">确认</el-button>
              <el-button size="small" @click="setAnomalyStatus(row, 'IGNORED')">忽略</el-button>
              <el-button size="small" type="success" @click="setAnomalyStatus(row, 'RESOLVED')">解决</el-button>
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
import request from '../../utils/request'
import { getEnergyStats, updateAnomalyStatus } from '../../api/energy'

const loading = ref(false)
const structureMode = ref('usage')
const charts = []

const trendRef = ref(null)
const typeRef = ref(null)
const roomRankRef = ref(null)
const buildingRankRef = ref(null)

const buildings = ref([])
const floors = ref([])
const rooms = ref([])
const tenants = ref([])

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

const structureModeOptions = [
  { label: '用量', value: 'usage' },
  { label: '金额', value: 'amount' }
]

const summaryCards = computed(() => [
  { label: '记录数', value: stats.recordCount, desc: '当前筛选范围', className: '' },
  { label: '电用量', value: `${formatNumber(stats.electricUsage)} kWh`, desc: '电表用量', className: 'warning' },
  { label: '水用量', value: `${formatNumber(stats.waterUsage)} m³`, desc: '水表用量', className: 'primary' },
  { label: '燃气用量', value: `${formatNumber(stats.gasUsage)} m³`, desc: '燃气表用量', className: 'danger' },
  { label: '结算金额', value: `¥ ${formatNumber(stats.totalAmount)}`, desc: '当前筛选范围', className: '' },
  { label: '异常记录', value: stats.abnormalCount, desc: '待关注抄表记录', className: 'danger' }
])

const isTrendEmpty = computed(() => {
  return !stats.trend.some(item => number(item.electricUsage) || number(item.waterUsage) || number(item.gasUsage))
})

const isStructureEmpty = computed(() => {
  return !stats.typeStructure.some(item => number(item.usageAmount) || number(item.settlementAmount))
})

async function loadStats() {
  loading.value = true
  try {
    const data = await getEnergyStats({
      meterType: queryForm.meterType || undefined,
      periodMonth: queryForm.periodMonth || undefined,
      buildingId: queryForm.buildingId || undefined,
      floorId: queryForm.floorId || undefined,
      roomId: queryForm.roomId || undefined,
      tenantId: queryForm.tenantId || undefined,
      billStatus: queryForm.billStatus || undefined,
      abnormalFlag: queryForm.abnormalFlag === '' ? undefined : queryForm.abnormalFlag
    })
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
  } finally {
    loading.value = false
    await nextTick()
    renderCharts()
  }
}

async function loadBaseData() {
  const [buildingData, tenantData] = await Promise.all([
    request.get('/buildings'),
    request.get('/tenant/list')
  ])
  buildings.value = Array.isArray(buildingData) ? buildingData : (buildingData?.content || [])
  tenants.value = Array.isArray(tenantData) ? tenantData : []
}

async function onBuildingChange() {
  queryForm.floorId = null
  queryForm.roomId = null
  floors.value = queryForm.buildingId
    ? await request.get('/floors', { params: { building_id: queryForm.buildingId } })
    : []
  rooms.value = []
}

async function onFloorChange() {
  queryForm.roomId = null
  rooms.value = queryForm.buildingId
    ? await request.get('/rooms', { params: { building_id: queryForm.buildingId, floor_id: queryForm.floorId || undefined } })
    : []
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
    color: ['#d93025', '#409eff', '#e6a23c'],
    tooltip: { trigger: 'axis' },
    legend: { top: 0 },
    grid: { left: 46, right: 20, top: 44, bottom: 28 },
    xAxis: { type: 'category', data: stats.trend.map(item => item.periodMonth) },
    yAxis: { type: 'value' },
    series: [
      { name: '电', type: 'line', smooth: true, data: stats.trend.map(item => number(item.electricUsage)) },
      { name: '水', type: 'line', smooth: true, data: stats.trend.map(item => number(item.waterUsage)) },
      { name: '燃气', type: 'line', smooth: true, data: stats.trend.map(item => number(item.gasUsage)) }
    ]
  })

  type?.setOption({
    color: ['#d93025', '#409eff', '#e6a23c'],
    tooltip: { trigger: 'item' },
    legend: { bottom: 0 },
    series: [{
      type: 'pie',
      radius: ['45%', '68%'],
      center: ['50%', '45%'],
      data: stats.typeStructure.map(item => ({
        name: meterTypeText(item.meterType),
        value: structureMode.value === 'usage' ? number(item.usageAmount) : number(item.settlementAmount)
      }))
    }]
  })

  room?.setOption(rankBarOption(stats.roomRanking.map(item => ({
    name: item.roomName || item.name || '未关联房间',
    value: number(item.usageAmount)
  }))))

  building?.setOption(rankBarOption(stats.buildingRanking.map(item => ({
    name: item.buildingName || item.name || '未关联楼宇',
    value: number(item.usageAmount)
  }))))
}

function rankBarOption(rows) {
  const sorted = [...rows].sort((a, b) => b.value - a.value).slice(0, 10).reverse()
  return {
    color: ['#d93025'],
    tooltip: { trigger: 'axis' },
    grid: { left: 90, right: 20, top: 18, bottom: 24 },
    xAxis: { type: 'value' },
    yAxis: { type: 'category', data: sorted.map(item => item.name) },
    series: [{
      type: 'bar',
      barMaxWidth: 22,
      label: { show: true, position: 'right' },
      data: sorted.map(item => Number(item.value.toFixed(2)))
    }]
  }
}

function resizeCharts() {
  charts.forEach(chart => chart.resize())
}

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

function meterTypeText(type) {
  return {
    ELECTRIC: '电',
    ELECTRICITY: '电',
    WATER: '水',
    GAS: '燃气'
  }[type] || type || '-'
}

function abnormalReasonText(reason) {
  return {
    NEGATIVE_USAGE: '负用量',
    ZERO_USAGE: '零用量',
    HIGH_USAGE: '高用量',
    LOW_USAGE: '低用量',
    NO_CHANGE: '连续读数无变化'
  }[reason] || reason || '-'
}

function anomalyStatusText(status) {
  return {
    PENDING: '待处理',
    CONFIRMED: '已确认',
    IGNORED: '已忽略',
    RESOLVED: '已解决'
  }[status] || status || '-'
}

function formatFloor(floor) {
  return floor.floorName || `${floor.floorNumber}F`
}

function formatRoom(room) {
  return room.roomName || room.roomNumber || `房间${room.id}`
}

watch(structureMode, async () => {
  await nextTick()
  renderCharts()
})

onMounted(async () => {
  await loadBaseData()
  await loadStats()
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

.query-select {
  width: 150px;
}

.query-select-small {
  width: 110px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
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
  font-size: 22px;
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

.card-header-inline {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.chart {
  width: 100%;
  height: 300px;
}

.table-card {
  margin-top: 16px;
}

.primary {
  color: #409eff;
}

.warning {
  color: #e6a23c;
}

.danger {
  color: #d93025;
}
</style>
