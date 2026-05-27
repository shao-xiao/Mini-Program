<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="page-header">
          <div>
            <div class="page-title">能耗抄表</div>
            <div class="page-subtitle">表具档案、抄表记录、异常识别与账单生成</div>
          </div>
          <el-button type="primary" @click="openCreateDialog">新增抄表</el-button>
        </div>
      </template>

      <el-form :inline="true" :model="queryForm" class="query-form">
        <el-form-item label="能耗类型">
          <el-select v-model="queryForm.meterType" placeholder="全部" clearable class="query-select">
            <el-option v-for="item in meterTypes" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="账期">
          <el-date-picker
            v-model="queryForm.periodMonth"
            type="month"
            value-format="YYYY-MM"
            placeholder="选择月份"
            clearable
            class="query-select"
          />
        </el-form-item>
        <el-form-item label="表号">
          <el-input v-model="queryForm.meterNo" placeholder="请输入表号" clearable class="query-input" />
        </el-form-item>
        <el-form-item label="楼宇">
          <el-select v-model="queryForm.buildingId" placeholder="全部楼宇" clearable class="query-select" @change="onQueryBuildingChange">
            <el-option v-for="item in buildings" :key="item.id" :label="item.buildingName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="楼层">
          <el-select v-model="queryForm.floorId" placeholder="全部楼层" clearable class="query-select" @change="onQueryFloorChange">
            <el-option v-for="item in queryFloors" :key="item.id" :label="formatFloor(item)" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="房间">
          <el-select v-model="queryForm.roomId" placeholder="全部房间" clearable class="query-select">
            <el-option v-for="item in queryRooms" :key="item.id" :label="formatRoom(item)" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="账单状态">
          <el-select v-model="queryForm.billStatus" placeholder="全部" clearable class="query-select">
            <el-option v-for="item in billStatuses" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="异常">
          <el-select v-model="queryForm.abnormalFlag" placeholder="全部" clearable class="query-select-small">
            <el-option label="异常" :value="true" />
            <el-option label="正常" :value="false" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="records" border stripe class="record-table">
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column prop="meterNo" label="表号" min-width="110" />
        <el-table-column label="能耗类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="meterTypeTag(row.meterType)">{{ meterTypeText(row.meterType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="periodMonth" label="账期" width="105" align="center" />
        <el-table-column prop="readingDate" label="抄表日期" width="120" align="center" />
        <el-table-column prop="previousReading" label="上期读数" width="105" align="right" />
        <el-table-column prop="currentReading" label="本次读数" width="105" align="right" />
        <el-table-column prop="multiplier" label="倍率" width="80" align="right" />
        <el-table-column label="本期用量" width="120" align="right">
          <template #default="{ row }">{{ formatNumber(row.usageAmount) }} {{ row.unit }}</template>
        </el-table-column>
        <el-table-column prop="unitPrice" label="单价" width="90" align="right" />
        <el-table-column label="结算金额" width="110" align="right">
          <template #default="{ row }">¥ {{ formatNumber(row.settlementAmount) }}</template>
        </el-table-column>
        <el-table-column label="房间" min-width="130">
          <template #default="{ row }">{{ row.roomName || '-' }}</template>
        </el-table-column>
        <el-table-column label="租户" min-width="130">
          <template #default="{ row }">{{ row.tenantName || '-' }}</template>
        </el-table-column>
        <el-table-column label="账单状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="billStatusTag(row.billStatus)">{{ billStatusText(row.billStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="异常状态" width="150" align="center">
          <template #default="{ row }">
            <el-tooltip v-if="row.abnormalFlag" :content="abnormalReasonText(row.abnormalReason)" placement="top">
              <el-tag type="danger">{{ anomalyStatusText(row.abnormalStatus) }}</el-tag>
            </el-tooltip>
            <el-tag v-else type="success">正常</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="155">
          <template #default="{ row }">{{ formatTime(row.createdTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="245" fixed="right">
          <template #default="{ row }">
            <template v-if="row.billStatus === 'NOT_GENERATED'">
              <el-button size="small" @click="openEditDialog(row)">编辑</el-button>
              <el-button size="small" type="danger" @click="deleteRecord(row)">删除</el-button>
              <el-button size="small" type="primary" @click="generateBill(row)">生成账单</el-button>
            </template>
            <template v-else-if="row.billStatus === 'GENERATED'">
              <el-button size="small" @click="viewBill(row)">查看账单</el-button>
              <el-button size="small" type="success" @click="markPosted(row)">标记入账</el-button>
            </template>
            <template v-else>
              <el-button size="small" @click="viewBill(row)">查看账单</el-button>
              <el-button size="small" disabled>只读</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pagination.currentPage"
          v-model:page-size="pagination.pageSize"
          :total="pagination.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="loadRecords"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑抄表' : '新增抄表'" width="680px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="表具" prop="meterId">
          <el-select v-model="form.meterId" filterable style="width: 100%" placeholder="请选择表具" @change="handleMeterChange">
            <el-option
              v-for="meter in meters"
              :key="meter.id"
              :label="`${meter.meterNo} | ${meterTypeText(meter.meterType)} | ${meter.roomName || '-'} | ${meter.tenantName || '-'}`"
              :value="meter.id"
            />
          </el-select>
        </el-form-item>

        <div class="meter-info">
          <span>上期读数：{{ formatNumber(lastInfo.previousReading) }}</span>
          <span>单位：{{ lastInfo.unit || '-' }}</span>
          <span>倍率：{{ lastInfo.multiplier || '-' }}</span>
          <span>房间：{{ lastInfo.roomName || '-' }}</span>
          <span>租户：{{ lastInfo.tenantName || '-' }}</span>
        </div>

        <div class="form-grid">
          <el-form-item label="账期" prop="periodMonth">
            <el-date-picker
              v-model="form.periodMonth"
              type="month"
              value-format="YYYY-MM"
              placeholder="选择账期"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="抄表日期" prop="readingDate">
            <el-date-picker
              v-model="form.readingDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="请选择抄表日期"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="本次读数" prop="currentReading">
            <el-input-number v-model="form.currentReading" :min="0" :precision="2" :step="1" style="width: 100%" />
          </el-form-item>
          <el-form-item label="单价" prop="unitPrice">
            <el-input-number v-model="form.unitPrice" :min="0" :precision="4" :step="0.1" style="width: 100%" />
          </el-form-item>
          <el-form-item label="操作人">
            <el-input v-model="form.operatorName" placeholder="默认 admin" />
          </el-form-item>
        </div>

        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../utils/request'
import { pageParams, readPage } from '../../utils/pagination'
import {
  createEnergyReading,
  deleteEnergyReading,
  generateEnergyBill,
  getEnergyMeters,
  getEnergyReadings,
  getLastReading,
  markEnergyPosted,
  updateEnergyReading
} from '../../api/energy'

const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const formRef = ref(null)
const records = ref([])
const meters = ref([])
const buildings = ref([])
const queryFloors = ref([])
const queryRooms = ref([])

const meterTypes = [
  { label: '电', value: 'ELECTRIC' },
  { label: '水', value: 'WATER' },
  { label: '燃气', value: 'GAS' }
]

const billStatuses = [
  { label: '未生成', value: 'NOT_GENERATED' },
  { label: '已生成', value: 'GENERATED' },
  { label: '已入账', value: 'POSTED' }
]

const queryForm = reactive({
  meterType: '',
  periodMonth: '',
  meterNo: '',
  buildingId: null,
  floorId: null,
  roomId: null,
  billStatus: '',
  abnormalFlag: ''
})

const pagination = reactive({
  currentPage: 1,
  pageSize: 10,
  total: 0
})

const form = reactive(defaultForm())
const lastInfo = reactive({
  previousReading: 0,
  unit: '',
  multiplier: '',
  roomName: '',
  tenantName: ''
})

const rules = {
  meterId: [{ required: true, message: '请选择表具', trigger: 'change' }],
  periodMonth: [{ required: true, message: '请选择账期', trigger: 'change' }],
  readingDate: [{ required: true, message: '请选择抄表日期', trigger: 'change' }],
  currentReading: [{ required: true, message: '请输入本次读数', trigger: 'blur' }],
  unitPrice: [{ required: true, message: '请输入单价', trigger: 'blur' }]
}

function defaultForm() {
  const today = new Date()
  const month = today.toISOString().slice(0, 7)
  const date = today.toISOString().slice(0, 10)
  return {
    id: null,
    meterId: null,
    periodMonth: month,
    readingDate: date,
    currentReading: 0,
    unitPrice: 1,
    operatorName: 'admin',
    remark: ''
  }
}

async function loadRecords() {
  loading.value = true
  try {
    const data = await getEnergyReadings({
      meterType: queryForm.meterType || undefined,
      periodMonth: queryForm.periodMonth || undefined,
      meterNo: queryForm.meterNo || undefined,
      buildingId: queryForm.buildingId || undefined,
      floorId: queryForm.floorId || undefined,
      roomId: queryForm.roomId || undefined,
      billStatus: queryForm.billStatus || undefined,
      abnormalFlag: queryForm.abnormalFlag === '' ? undefined : queryForm.abnormalFlag,
      ...pageParams(pagination)
    })
    const page = readPage(data)
    records.value = page.records
    pagination.total = page.total
  } finally {
    loading.value = false
  }
}

async function loadMeters() {
  meters.value = readPage(await getEnergyMeters({ page: 1, pageSize: 100 })).records
}

async function loadBuildings() {
  const data = await request.get('/buildings')
  buildings.value = readPage(data).records
}

async function loadFloors(buildingId) {
  if (!buildingId) return []
  return readPage(await request.get('/floors', { params: { building_id: buildingId } })).records
}

async function loadRooms(buildingId, floorId) {
  if (!buildingId) return []
  return readPage(await request.get('/rooms', { params: { building_id: buildingId, floor_id: floorId || undefined, page: 1, pageSize: 100 } })).records
}

async function onQueryBuildingChange() {
  queryForm.floorId = null
  queryForm.roomId = null
  queryFloors.value = await loadFloors(queryForm.buildingId)
  queryRooms.value = []
}

async function onQueryFloorChange() {
  queryForm.roomId = null
  queryRooms.value = await loadRooms(queryForm.buildingId, queryForm.floorId)
}

function handleSearch() {
  pagination.currentPage = 1
  loadRecords()
}

function resetSearch() {
  queryForm.meterType = ''
  queryForm.periodMonth = ''
  queryForm.meterNo = ''
  queryForm.buildingId = null
  queryForm.floorId = null
  queryForm.roomId = null
  queryForm.billStatus = ''
  queryForm.abnormalFlag = ''
  queryFloors.value = []
  queryRooms.value = []
  handleSearch()
}

function handleSizeChange() {
  pagination.currentPage = 1
  loadRecords()
}

function openCreateDialog() {
  Object.assign(form, defaultForm())
  Object.assign(lastInfo, { previousReading: 0, unit: '', multiplier: '', roomName: '', tenantName: '' })
  dialogVisible.value = true
}

function openEditDialog(row) {
  Object.assign(form, {
    id: row.id,
    meterId: row.meterId,
    periodMonth: row.periodMonth,
    readingDate: row.readingDate,
    currentReading: Number(row.currentReading || 0),
    unitPrice: Number(row.unitPrice || 0),
    operatorName: row.operatorName || 'admin',
    remark: row.remark || ''
  })
  Object.assign(lastInfo, {
    previousReading: row.previousReading,
    unit: row.unit,
    multiplier: row.multiplier,
    roomName: row.roomName,
    tenantName: row.tenantName
  })
  dialogVisible.value = true
}

async function handleMeterChange(meterId) {
  if (!meterId) return
  const data = await getLastReading(meterId)
  Object.assign(lastInfo, data)
}

async function submitForm() {
  if (!formRef.value) return
  await formRef.value.validate(async valid => {
    if (!valid) return

    saving.value = true
    try {
      const payload = {
        meterId: form.meterId,
        periodMonth: form.periodMonth,
        readingDate: form.readingDate,
        currentReading: form.currentReading,
        unitPrice: form.unitPrice,
        operatorName: form.operatorName || 'admin',
        remark: form.remark
      }
      if (form.id) {
        await updateEnergyReading(form.id, payload)
        ElMessage.success('抄表记录已更新')
      } else {
        await createEnergyReading(payload)
        ElMessage.success('抄表记录已保存')
      }
      dialogVisible.value = false
      await loadRecords()
    } finally {
      saving.value = false
    }
  })
}

async function deleteRecord(row) {
  await ElMessageBox.confirm(`确定删除表号 ${row.meterNo} 的 ${row.periodMonth} 抄表记录吗？`, '删除确认', { type: 'warning' })
  await deleteEnergyReading(row.id)
  ElMessage.success('已删除')
  await loadRecords()
}

async function generateBill(row) {
  await generateEnergyBill(row.id)
  ElMessage.success('能源账单已生成')
  await loadRecords()
}

async function markPosted(row) {
  await markEnergyPosted(row.id)
  ElMessage.success('已标记入账并发布到租户端')
  await loadRecords()
}

function viewBill(row) {
  if (!row.billId) {
    ElMessage.warning('该记录尚未生成账单')
    return
  }
  ElMessage.info(`账单ID：${row.billId}，可在账单管理中查看`)
}

function formatFloor(floor) {
  return floor.floorName || `${floor.floorNumber}F`
}

function formatRoom(room) {
  return room.roomName || room.roomNumber || `房间${room.id}`
}

function formatNumber(value) {
  return Number(value || 0).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })
}

function formatTime(time) {
  if (!time) return '-'
  return String(time).replace('T', ' ').slice(0, 16)
}

function meterTypeText(type) {
  return {
    ELECTRIC: '电',
    ELECTRICITY: '电',
    WATER: '水',
    GAS: '燃气'
  }[type] || type || '-'
}

function meterTypeTag(type) {
  return {
    ELECTRIC: 'warning',
    ELECTRICITY: 'warning',
    WATER: 'primary',
    GAS: 'danger'
  }[type] || 'info'
}

function billStatusText(status) {
  return {
    NOT_GENERATED: '未生成',
    GENERATED: '已生成',
    POSTED: '已入账'
  }[status] || status || '-'
}

function billStatusTag(status) {
  return {
    NOT_GENERATED: 'warning',
    GENERATED: 'primary',
    POSTED: 'success'
  }[status] || 'info'
}

function anomalyStatusText(status) {
  return {
    PENDING: '待处理',
    CONFIRMED: '已确认',
    IGNORED: '已忽略',
    RESOLVED: '已解决'
  }[status] || status || '异常'
}

function abnormalReasonText(reason) {
  return {
    NEGATIVE_USAGE: '负用量',
    ZERO_USAGE: '零用量',
    HIGH_USAGE: '高用量异常',
    LOW_USAGE: '低用量异常',
    NO_CHANGE: '连续读数无变化'
  }[reason] || reason || '异常用量'
}

onMounted(async () => {
  await Promise.all([loadBuildings(), loadMeters()])
  await loadRecords()
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
  margin-bottom: 16px;
}

.query-input {
  width: 180px;
}

.query-select {
  width: 150px;
}

.query-select-small {
  width: 110px;
}

.record-table {
  width: 100%;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.meter-info {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 8px;
  margin: 0 0 18px 110px;
  color: #606266;
  font-size: 13px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 12px;
}
</style>
