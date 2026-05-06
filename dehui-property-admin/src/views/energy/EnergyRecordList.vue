<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="page-header">
          <div>
            <div class="page-title">能耗抄表</div>
            <div class="page-subtitle">记录水、电、燃气等能耗抄表数据</div>
          </div>
          <el-button type="primary" @click="openCreateDialog">新增抄表</el-button>
        </div>
      </template>

      <el-form :inline="true" :model="queryForm" class="query-form">
        <el-form-item label="能耗类型">
          <el-select v-model="queryForm.energyType" placeholder="全部" clearable style="width: 150px">
            <el-option label="电" value="ELECTRICITY" />
            <el-option label="水" value="WATER" />
            <el-option label="燃气" value="GAS" />
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
        <el-table-column prop="buildingId" label="楼宇ID" width="100" align="center" />
        <el-table-column prop="roomId" label="房间ID" width="100" align="center" />
        <el-table-column label="创建时间" min-width="160">
          <template #default="{ row }">
            {{ formatTime(row.createdTime) }}
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
            <el-option label="燃气" value="GAS" />
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

        <el-form-item label="楼宇ID">
          <el-input-number v-model="form.buildingId" :min="1" :precision="0" style="width: 100%" />
        </el-form-item>

        <el-form-item label="房间ID">
          <el-input-number v-model="form.roomId" :min="1" :precision="0" style="width: 100%" />
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
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../../utils/request'

const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const formRef = ref(null)
const records = ref([])
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
  buildingId: null,
  roomId: null
})

const rules = {
  meterNumber: [{ required: true, message: '请输入表号', trigger: 'blur' }],
  energyType: [{ required: true, message: '请选择能耗类型', trigger: 'change' }],
  recordDate: [{ required: true, message: '请选择抄表日期', trigger: 'change' }],
  reading: [{ required: true, message: '请输入本次读数', trigger: 'blur' }]
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

function resetForm() {
  form.meterNumber = ''
  form.energyType = 'ELECTRICITY'
  form.recordDate = ''
  form.reading = 0
  form.consumption = 0
  form.buildingId = null
  form.roomId = null
  lastReading.value = 0
}

function openCreateDialog() {
  resetForm()
  dialogVisible.value = true
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
        buildingId: form.buildingId,
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

function handleSearch() {
  pagination.currentPage = 1
}

function resetSearch() {
  queryForm.energyType = ''
  queryForm.meterNumber = ''
  pagination.currentPage = 1
}


function formatTime(time) {
  if (!time) return '-'
  return time.replace('T', ' ').slice(0, 16)
}

function formatEnergyType(type) {
  return {
    ELECTRICITY: '电',
    WATER: '水',
    GAS: '燃气',
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

onMounted(loadRecords)
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

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
