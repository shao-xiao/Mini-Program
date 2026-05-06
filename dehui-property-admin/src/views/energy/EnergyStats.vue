<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="page-header">
          <div>
            <div class="page-title">能耗统计</div>
            <div class="page-subtitle">基于能耗抄表记录，按类型、月份、楼宇、房间进行统计分析</div>
          </div>
          <el-button type="primary" @click="loadRecords">刷新</el-button>
        </div>
      </template>

      <el-form :inline="true" :model="queryForm" class="query-form">
        <el-form-item label="能耗类型">
          <el-select v-model="queryForm.energyType" clearable placeholder="全部" style="width: 150px">
            <el-option label="电" value="ELECTRICITY" />
            <el-option label="水" value="WATER" />
            <el-option label="燃气" value="GAS" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>

        <el-form-item label="月份">
          <el-date-picker
            v-model="queryForm.month"
            type="month"
            value-format="YYYY-MM"
            placeholder="选择月份"
            clearable
            style="width: 160px"
          />
        </el-form-item>

        <el-form-item label="楼宇ID">
          <el-input v-model="queryForm.buildingId" clearable placeholder="楼宇ID" style="width: 120px" />
        </el-form-item>

        <el-form-item label="房间ID">
          <el-input v-model="queryForm.roomId" clearable placeholder="房间ID" style="width: 120px" />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>

      <el-row :gutter="16" class="summary-row">
        <el-col :span="6">
          <el-card shadow="never" class="summary-card">
            <div class="summary-label">记录数</div>
            <div class="summary-value">{{ filteredRecords.length }}</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="never" class="summary-card">
            <div class="summary-label">总用量</div>
            <div class="summary-value">{{ totalConsumption }}</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="never" class="summary-card">
            <div class="summary-label">电用量</div>
            <div class="summary-value">{{ typeTotal('ELECTRICITY') }}</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="never" class="summary-card">
            <div class="summary-label">水用量</div>
            <div class="summary-value">{{ typeTotal('WATER') }}</div>
          </el-card>
        </el-col>
      </el-row>

      <el-tabs v-model="activeTab">
        <el-tab-pane label="按能耗类型" name="type">
          <el-table v-loading="loading" :data="typeStats" border stripe>
            <el-table-column label="能耗类型" min-width="120">
              <template #default="{ row }">
                <el-tag :type="getEnergyTagType(row.energyType)">
                  {{ formatEnergyType(row.energyType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="count" label="记录数" width="120" align="center" />
            <el-table-column prop="totalReading" label="读数合计" width="150" align="right" />
            <el-table-column prop="totalConsumption" label="用量合计" width="150" align="right" />
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="按月份" name="month">
          <el-table v-loading="loading" :data="monthStats" border stripe>
            <el-table-column prop="month" label="月份" width="140" align="center" />
            <el-table-column prop="count" label="记录数" width="120" align="center" />
            <el-table-column prop="totalReading" label="读数合计" width="150" align="right" />
            <el-table-column prop="totalConsumption" label="用量合计" width="150" align="right" />
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="按楼宇" name="building">
          <el-table v-loading="loading" :data="buildingStats" border stripe>
            <el-table-column prop="buildingId" label="楼宇ID" width="120" align="center" />
            <el-table-column prop="count" label="记录数" width="120" align="center" />
            <el-table-column prop="totalReading" label="读数合计" width="150" align="right" />
            <el-table-column prop="totalConsumption" label="用量合计" width="150" align="right" />
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="按房间" name="room">
          <el-table v-loading="loading" :data="roomStats" border stripe>
            <el-table-column prop="roomId" label="房间ID" width="120" align="center" />
            <el-table-column prop="buildingId" label="楼宇ID" width="120" align="center" />
            <el-table-column prop="count" label="记录数" width="120" align="center" />
            <el-table-column prop="totalReading" label="读数合计" width="150" align="right" />
            <el-table-column prop="totalConsumption" label="用量合计" width="150" align="right" />
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../../utils/request'

const loading = ref(false)
const records = ref([])
const activeTab = ref('type')

const queryForm = reactive({
  energyType: '',
  month: '',
  buildingId: '',
  roomId: ''
})

const filteredRecords = computed(() => {
  return records.value.filter(item => {
    const matchType = !queryForm.energyType || item.energyType === queryForm.energyType
    const matchMonth = !queryForm.month || String(item.recordDate || '').startsWith(queryForm.month)
    const matchBuilding = !queryForm.buildingId || String(item.buildingId || '') === String(queryForm.buildingId)
    const matchRoom = !queryForm.roomId || String(item.roomId || '') === String(queryForm.roomId)

    return matchType && matchMonth && matchBuilding && matchRoom
  })
})

const totalConsumption = computed(() => {
  return sum(filteredRecords.value, 'consumption')
})

const typeStats = computed(() => {
  return groupStats(filteredRecords.value, item => item.energyType || 'OTHER')
    .map(item => ({
      energyType: item.key,
      count: item.count,
      totalReading: item.totalReading,
      totalConsumption: item.totalConsumption
    }))
})

const monthStats = computed(() => {
  return groupStats(filteredRecords.value, item => String(item.recordDate || '').slice(0, 7) || '-')
    .map(item => ({
      month: item.key,
      count: item.count,
      totalReading: item.totalReading,
      totalConsumption: item.totalConsumption
    }))
    .sort((a, b) => String(b.month).localeCompare(String(a.month)))
})

const buildingStats = computed(() => {
  return groupStats(filteredRecords.value, item => item.buildingId || '-')
    .map(item => ({
      buildingId: item.key,
      count: item.count,
      totalReading: item.totalReading,
      totalConsumption: item.totalConsumption
    }))
})

const roomStats = computed(() => {
  const map = new Map()

  filteredRecords.value.forEach(item => {
    const key = `${item.buildingId || '-'}-${item.roomId || '-'}`
    if (!map.has(key)) {
      map.set(key, {
        buildingId: item.buildingId || '-',
        roomId: item.roomId || '-',
        count: 0,
        totalReading: 0,
        totalConsumption: 0
      })
    }

    const current = map.get(key)
    current.count += 1
    current.totalReading += Number(item.reading || 0)
    current.totalConsumption += Number(item.consumption || 0)
  })

  return Array.from(map.values()).map(item => ({
    ...item,
    totalReading: formatNumber(item.totalReading),
    totalConsumption: formatNumber(item.totalConsumption)
  }))
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
    ElMessage.error(e.message || '加载能耗统计数据失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {}

function resetSearch() {
  queryForm.energyType = ''
  queryForm.month = ''
  queryForm.buildingId = ''
  queryForm.roomId = ''
}

function sum(list, field) {
  return formatNumber(list.reduce((total, item) => total + Number(item[field] || 0), 0))
}

function typeTotal(type) {
  return sum(filteredRecords.value.filter(item => item.energyType === type), 'consumption')
}

function groupStats(list, getKey) {
  const map = new Map()

  list.forEach(item => {
    const key = getKey(item)
    if (!map.has(key)) {
      map.set(key, {
        key,
        count: 0,
        totalReading: 0,
        totalConsumption: 0
      })
    }

    const current = map.get(key)
    current.count += 1
    current.totalReading += Number(item.reading || 0)
    current.totalConsumption += Number(item.consumption || 0)
  })

  return Array.from(map.values()).map(item => ({
    ...item,
    totalReading: formatNumber(item.totalReading),
    totalConsumption: formatNumber(item.totalConsumption)
  }))
}

function formatNumber(value) {
  return Number(value || 0).toFixed(2)
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

.summary-row {
  margin-bottom: 20px;
}

.summary-card {
  height: 96px;
}

.summary-label {
  font-size: 13px;
  color: #909399;
}

.summary-value {
  margin-top: 12px;
  font-size: 26px;
  font-weight: 600;
  color: #303133;
}
</style>
