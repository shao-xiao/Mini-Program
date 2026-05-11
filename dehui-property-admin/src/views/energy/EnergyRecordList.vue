<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="page-header">
          <div>
            <div class="page-title">能耗抄表</div>
            <div class="page-subtitle">记录水、电、煤等能耗抄表数据</div>
          </div>
          <div class="header-actions">
            <el-button @click="openRuleDialog">计费规则</el-button>
            <el-button type="primary" @click="openCreateDialog">新增抄表</el-button>
          </div>
        </div>
      </template>

      <el-form :inline="true" :model="queryForm" class="query-form">
        <el-form-item label="能耗类型">
          <el-select v-model="queryForm.energyType" placeholder="全部" clearable style="width: 150px">
            <el-option label="电" value="ELECTRICITY" />
            <el-option label="水" value="WATER" />
            <el-option label="煤" value="GAS" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>

        <el-form-item label="表号">
          <el-input v-model="queryForm.meterNumber" placeholder="请输入表号" clearable style="width: 180px" />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="pagedRecords" border stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" align="center" />
        <el-table-column prop="meterNumber" label="表号" min-width="120" />
        <el-table-column label="能耗类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="getEnergyTagType(row.energyType)">
              {{ formatEnergyType(row.energyType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="recordDate" label="抄表日期" width="130" align="center" />
        <el-table-column prop="reading" label="本次读数" width="120" align="right" />
        <el-table-column prop="consumption" label="本期用量" width="120" align="right" />
        <el-table-column prop="unitPrice" label="单价" width="100" align="right">
          <template #default="{ row }">{{ row.unitPrice ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="amount" label="结算金额" width="120" align="right">
          <template #default="{ row }">{{ row.amount ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="房间" min-width="130">
          <template #default="{ row }">{{ roomText(row.roomId) }}</template>
        </el-table-column>
        <el-table-column label="账单" width="110" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.billId" type="success">已生成</el-tag>
            <el-tag v-else type="warning">未生成</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="160">
          <template #default="{ row }">
            {{ formatTime(row.createdTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="!row.billId"
              size="small"
              type="primary"
              @click="generateBill(row)"
            >
              生成账单
            </el-button>
            <el-button v-else size="small" disabled>已入账</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pagination.currentPage"
          v-model:page-size="pagination.pageSize"
          :total="filteredRecords.length"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
        />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" title="新增抄表记录" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="表号" prop="meterNumber">
          <el-input v-model="form.meterNumber" placeholder="请输入表号" @blur="calculateConsumption" />
        </el-form-item>

        <el-form-item label="能耗类型" prop="energyType">
          <el-select v-model="form.energyType" style="width: 100%" @change="calculateConsumption">
            <el-option label="电" value="ELECTRICITY" />
            <el-option label="水" value="WATER" />
            <el-option label="煤" value="GAS" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>

        <el-form-item label="抄表日期" prop="recordDate">
          <el-date-picker
            v-model="form.recordDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="请选择抄表日期"
            style="width: 100%"
            @change="calculateConsumption"
          />
        </el-form-item>

        <el-form-item label="上次读数">
          <el-input-number
            v-model="lastReading"
            disabled
            :precision="2"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="本次读数" prop="reading">
          <el-input-number
            v-model="form.reading"
            :min="0"
            :precision="2"
            :step="1"
            style="width: 100%"
            @change="calculateConsumption"
          />
        </el-form-item>

        <el-form-item label="本期用量" prop="consumption">
          <el-input-number
            v-model="form.consumption"
            disabled
            :precision="2"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="楼层">
          <el-select
            v-model="form.floorId"
            placeholder="请选择楼层"
            style="width: 100%"
            @change="handleFloorChange"
          >
            <el-option
              v-for="floor in floors"
              :key="floor.id"
              :label="floor.floorName"
              :value="floor.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="房间号" prop="roomId">
          <el-select
            v-model="form.roomId"
            filterable
            placeholder="请选择房间"
            style="width: 100%"
          >
            <el-option
              v-for="room in currentRooms"
              :key="room.id"
              :label="roomLabel(room)"
              :value="room.id"
            />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="ruleDialogVisible" title="能耗计费规则" width="860px" class="rule-dialog">
      <el-table
        :data="rateRules"
        border
        height="280"
        class="rule-table"
      >
        <el-table-column label="类型" width="100">
          <template #default="{ row }">{{ formatEnergyType(row.energyType) }}</template>
        </el-table-column>
        <el-table-column prop="unitPrice" label="单价" width="110" align="right" />
        <el-table-column label="默认项" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.defaultRule" type="success">默认</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">
              {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="260" show-overflow-tooltip />
        <el-table-column label="操作" width="160" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" @click="editRateRule(row)">编辑</el-button>
            <el-button
              size="small"
              type="danger"
              :disabled="row.defaultRule"
              @click="deleteRateRule(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="rule-form-header">
        <span>{{ ruleForm.id ? `正在编辑：${formatEnergyType(ruleForm.energyType)}规则` : '新增计费规则' }}</span>
        <el-button v-if="ruleForm.id" class="cancel-edit-button" @click="resetRuleForm">取消编辑</el-button>
      </div>

      <el-form :model="ruleForm" label-width="72px" class="rule-form">
        <el-form-item label="类型">
          <el-select v-model="ruleForm.energyType" style="width: 100%">
            <el-option label="电" value="ELECTRICITY" />
            <el-option label="水" value="WATER" />
            <el-option label="煤" value="GAS" />
          </el-select>
        </el-form-item>
        <el-form-item label="单价">
          <el-input-number
            v-model="ruleForm.unitPrice"
            :min="0.01"
            :precision="4"
            :step="0.1"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="ruleForm.status" style="width: 100%">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="停用" value="INACTIVE" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="ruleForm.remark" placeholder="例如：2026年度园区电价" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="ruleDialogVisible = false">关闭</el-button>
        <el-button type="primary" @click="saveRateRule">
          {{ ruleForm.id ? '保存修改' : '保存规则' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../utils/request'

const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const ruleDialogVisible = ref(false)
const formRef = ref(null)
const records = ref([])
const rateRules = ref([])
const floors = ref([])
const rooms = ref([])
const lastReading = ref(0)

const queryForm = reactive({
  energyType: '',
  meterNumber: ''
})

const pagination = reactive({
  currentPage: 1,
  pageSize: 10
})

const form = reactive({
  meterNumber: '',
  energyType: 'ELECTRICITY',
  recordDate: '',
  reading: 0,
  consumption: 0,
  floorId: null,
  roomId: null
})

const ruleForm = reactive({
  id: null,
  energyType: 'ELECTRICITY',
  unitPrice: 1,
  status: 'ACTIVE',
  remark: ''
})

const rules = {
  meterNumber: [{ required: true, message: '请输入表号', trigger: 'blur' }],
  energyType: [{ required: true, message: '请选择能耗类型', trigger: 'change' }],
  recordDate: [{ required: true, message: '请选择抄表日期', trigger: 'change' }],
  reading: [{ required: true, message: '请输入本次读数', trigger: 'blur' }],
  roomId: [{ required: true, message: '请选择房间', trigger: 'change' }]
}

const filteredRecords = computed(() => {
  return records.value.filter(item => {
    const matchType = !queryForm.energyType || item.energyType === queryForm.energyType
    const matchMeter = !queryForm.meterNumber || String(item.meterNumber || '').includes(queryForm.meterNumber)
    return matchType && matchMeter
  })
})

const pagedRecords = computed(() => {
  const start = (pagination.currentPage - 1) * pagination.pageSize
  return filteredRecords.value.slice(start, start + pagination.pageSize)
})

const currentRooms = computed(() => {
  return rooms.value.filter(room => room.floorId === form.floorId)
})

function unwrap(res) {
  if (res?.code === 200) return res.data
  if (res?.data?.code === 200) return res.data.data
  if (Array.isArray(res)) return res
  if (Array.isArray(res?.data)) return res.data
  return res?.data ?? res
}

async function loadRecords() {
  loading.value = true
  try {
    const res = await request.get('/energy/list')
    records.value = unwrap(res) || []
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function loadRateRules() {
  try {
    const res = await request.get('/energy/rate-rules')
    rateRules.value = unwrap(res) || []
  } catch (e) {
    ElMessage.error(e.message || '计费规则加载失败')
  }
}

async function loadFloorsAndRooms() {
  try {
    const floorData = await request.get('/buildings/1/floors')
    floors.value = unwrap(floorData) || []
    const roomGroups = await Promise.all(
      floors.value.map(async floor => {
        const roomData = await request.get(`/buildings/1/floors/${floor.id}/rooms`)
        return unwrap(roomData) || []
      })
    )
    rooms.value = roomGroups.flat()
  } catch (e) {
    ElMessage.error(e.message || '房间数据加载失败')
  }
}

function resetForm() {
  form.meterNumber = ''
  form.energyType = 'ELECTRICITY'
  form.recordDate = ''
  form.reading = 0
  form.consumption = 0
  form.floorId = null
  form.roomId = null
  lastReading.value = 0
}

function openCreateDialog() {
  resetForm()
  dialogVisible.value = true
}

async function openRuleDialog() {
  resetRuleForm()
  await loadRateRules()
  ruleDialogVisible.value = true
}

function resetRuleForm() {
  ruleForm.id = null
  ruleForm.energyType = 'ELECTRICITY'
  ruleForm.unitPrice = 1
  ruleForm.status = 'ACTIVE'
  ruleForm.remark = ''
}

function calculateConsumption() {
  const history = records.value
    .filter(item => {
      return item.meterNumber === form.meterNumber &&
        item.energyType === form.energyType &&
        item.recordDate &&
        (!form.recordDate || item.recordDate < form.recordDate)
    })
    .sort((a, b) => String(b.recordDate).localeCompare(String(a.recordDate)))

  lastReading.value = Number(history[0]?.reading || 0)

  const current = Number(form.reading || 0)
  const usage = current - lastReading.value
  form.consumption = usage > 0 ? Number(usage.toFixed(2)) : 0
}

async function submitForm() {
  if (!formRef.value) return

  await formRef.value.validate(async valid => {
    if (!valid) return

    calculateConsumption()

    saving.value = true
    try {
      const payload = {
        meterNumber: form.meterNumber,
        energyType: form.energyType,
        recordDate: form.recordDate,
        reading: form.reading,
        consumption: form.consumption,
        roomId: form.roomId
      }

      const res = await request.post('/energy/save', payload)
      unwrap(res)

      ElMessage.success('保存成功')
      dialogVisible.value = false
      await loadRecords()
    } catch (e) {
      ElMessage.error(e.message || '保存失败')
    } finally {
      saving.value = false
    }
  })
}

async function saveRateRule() {
  if (!ruleForm.unitPrice || Number(ruleForm.unitPrice) <= 0) {
    ElMessage.warning('单价必须大于0')
    return
  }

  try {
    const payload = {
      energyType: ruleForm.energyType,
      unitPrice: ruleForm.unitPrice,
      status: ruleForm.status,
      remark: ruleForm.remark
    }
    if (ruleForm.id) {
      await request.put(`/energy/rate-rules/${ruleForm.id}`, payload)
    } else {
      await request.post('/energy/rate-rules', payload)
    }
    ElMessage.success('计费规则已保存')
    resetRuleForm()
    await loadRateRules()
  } catch (e) {
    ElMessage.error(e.message || '计费规则保存失败')
  }
}

function editRateRule(row) {
  ruleForm.id = row.id
  ruleForm.energyType = row.energyType
  ruleForm.unitPrice = Number(row.unitPrice || 0)
  ruleForm.status = row.status || 'ACTIVE'
  ruleForm.remark = row.remark || ''
}

async function deleteRateRule(row) {
  if (row.defaultRule) {
    ElMessage.warning('默认计费规则不可删除')
    return
  }
  try {
    await ElMessageBox.confirm(`确定删除「${formatEnergyType(row.energyType)}」计费规则吗？`, '删除确认', {
      type: 'warning'
    })
    await request.delete(`/energy/rate-rules/${row.id}`)
    ElMessage.success('已删除')
    await loadRateRules()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e?.response?.data?.message || e.message || '删除失败')
    }
  }
}

async function generateBill(row) {
  try {
    await request.post(`/energy/records/${row.id}/generate-bill`)
    ElMessage.success('能耗账单已生成')
    await loadRecords()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || e.message || '账单生成失败')
  }
}

function handleSearch() {
  pagination.currentPage = 1
}

function resetSearch() {
  queryForm.energyType = ''
  queryForm.meterNumber = ''
  pagination.currentPage = 1
}

function handleFloorChange() {
  form.roomId = null
}

function roomLabel(room) {
  const area = room.area ? ` | ${room.area}㎡` : ''
  return `${room.roomNumber}${area}`
}

function roomText(roomId) {
  if (!roomId) return '-'
  const room = rooms.value.find(item => item.id === roomId)
  if (!room) return `房间 ${roomId}`
  const floor = floors.value.find(item => item.id === room.floorId)
  return `${floor?.floorName || ''} ${room.roomNumber}`.trim()
}


function formatTime(time) {
  if (!time) return '-'
  return time.replace('T', ' ').slice(0, 16)
}

function formatEnergyType(type) {
  return {
    ELECTRICITY: '电',
    WATER: '水',
    GAS: '煤',
    OTHER: '其他'
  }[type] || type || '-'
}

function getEnergyTagType(type) {
  return {
    ELECTRICITY: 'warning',
    WATER: 'primary',
    GAS: 'danger',
    OTHER: 'info'
  }[type] || 'info'
}

onMounted(()=>{
  loadRecords()
  loadFloorsAndRooms()
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

.header-actions {
  display: flex;
  gap: 10px;
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

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.rule-table {
  width: 100%;
  margin-bottom: 18px;
}

.rule-form {
  padding-top: 2px;
}

.rule-form-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 34px;
  margin-bottom: 10px;
  color: #303133;
  font-weight: 600;
}

.cancel-edit-button {
  color: #d93025;
  border-color: #f1b4ae;
  background: #fff;
  font-weight: 600;
}

.cancel-edit-button:hover,
.cancel-edit-button:focus {
  color: #b42318;
  border-color: #d93025;
  background: #fff5f4;
}

:deep(.rule-dialog .el-dialog__body) {
  padding-top: 12px;
}

:deep(.rule-dialog .el-form-item) {
  margin-bottom: 16px;
}
</style>
