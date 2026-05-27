<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>房间管理</span>
          <el-button type="primary" :disabled="!query.buildingId || !query.floorId" @click="openCreateDialog">
            新增房间
          </el-button>
        </div>
      </template>

      <el-form inline>
        <el-form-item label="楼宇">
          <el-select v-model="query.buildingId" class="filter-select" placeholder="请选择楼宇" @change="onBuildingChange">
            <el-option v-for="item in buildings" :key="item.id" :label="item.buildingName" :value="item.id" />
          </el-select>
        </el-form-item>

        <el-form-item label="楼层">
          <el-select v-model="query.floorId" class="filter-select" placeholder="请选择楼层" @change="searchRooms">
            <el-option v-for="item in floors" :key="item.id" :label="formatFloorLabel(item)" :value="item.id" />
          </el-select>
        </el-form-item>

        <el-form-item label="状态">
          <el-select v-model="query.status" class="filter-select" clearable placeholder="全部状态" @change="searchRooms">
            <el-option v-for="item in roomStatuses" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="searchRooms">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="rooms" border style="width: 100%">
        <el-table-column label="序号" width="70">
          <template #default="{ $index }">
            {{ (pagination.currentPage - 1) * pagination.pageSize + $index + 1 }}
          </template>
        </el-table-column>
        <el-table-column prop="buildingName" label="楼宇" min-width="130" />
        <el-table-column prop="floorName" label="楼层" min-width="100" />
        <el-table-column prop="roomNumber" label="房间号" min-width="110" />
        <el-table-column prop="roomName" label="房间名称" min-width="130" />
        <el-table-column prop="area" label="面积㎡" width="100" />
        <el-table-column label="类型" width="110">
          <template #default="{ row }">{{ row.roomTypeText || roomTypeText(row.roomType) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">{{ row.statusText || roomStatusText(row.status) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openEditDialog(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="deleteRoom(row)">删除</el-button>
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
          @current-change="loadRooms"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑房间' : '新增房间'" width="540px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="房间号" required>
          <el-input v-model="form.roomNumber" />
        </el-form-item>

        <el-form-item label="房间名称">
          <el-input v-model="form.roomName" />
        </el-form-item>

        <el-form-item label="面积">
          <el-input-number v-model="form.area" :min="0" :precision="2" style="width:100%" />
        </el-form-item>

        <el-form-item label="类型">
          <el-select v-model="form.roomType" style="width:100%">
            <el-option v-for="item in roomTypes" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>

        <el-form-item label="状态">
          <el-select v-model="form.status" style="width:100%">
            <el-option v-for="item in roomStatuses" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>

        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveRoom">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../utils/request'
import { createPagination, pageParams, readPage, resetToFirstPage } from '../../utils/pagination'

const buildings = ref([])
const floors = ref([])
const rooms = ref([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const pagination = reactive(createPagination(20))

const roomTypes = [
  { label: '办公', value: 'OFFICE' },
  { label: '仓储', value: 'WAREHOUSE' },
  { label: '设备间', value: 'EQUIPMENT' },
  { label: '车位', value: 'PARKING' },
  { label: '公区', value: 'PUBLIC_AREA' }
]

const roomStatuses = [
  { label: '空置', value: 'AVAILABLE' },
  { label: '已出租', value: 'RENTED' },
  { label: '预留', value: 'RESERVED' },
  { label: '装修中', value: 'RENOVATING' },
  { label: '停用', value: 'DISABLED' }
]

const query = reactive({
  buildingId: null,
  floorId: null,
  status: ''
})

const form = reactive(defaultForm())

function defaultForm() {
  return {
    id: null,
    roomNumber: '',
    roomName: '',
    area: 0,
    roomType: 'OFFICE',
    status: 'AVAILABLE',
    description: ''
  }
}

function resetForm() {
  Object.assign(form, defaultForm())
}

function formatFloorLabel(floor) {
  return floor.floorName || `${floor.floorNumber}F`
}

function roomTypeText(value) {
  return roomTypes.find(item => item.value === value)?.label || value
}

function roomStatusText(value) {
  return roomStatuses.find(item => item.value === value)?.label || value
}

async function loadBuildings() {
  const data = await request.get('/buildings')
  buildings.value = readPage(data).records
}

async function loadFloors() {
  if (!query.buildingId) {
    floors.value = []
    query.floorId = null
    return
  }
  const data = await request.get('/floors', {
    params: { building_id: query.buildingId }
  })
  floors.value = readPage(data).records
  query.floorId = floors.value[0]?.id || null
}

async function onBuildingChange() {
  resetToFirstPage(pagination)
  rooms.value = []
  await loadFloors()
  await loadRooms()
}

function searchRooms() {
  resetToFirstPage(pagination)
  loadRooms()
}

async function loadRooms() {
  if (!query.buildingId) {
    rooms.value = []
    pagination.total = 0
    return
  }
  loading.value = true
  try {
    const params = {
      building_id: query.buildingId,
      floor_id: query.floorId || undefined,
      status: query.status || undefined,
      ...pageParams(pagination)
    }
    const data = await request.get('/rooms', { params })
    const page = readPage(data)
    rooms.value = page.records
    pagination.total = page.total
  } finally {
    loading.value = false
  }
}

function handleSizeChange() {
  resetToFirstPage(pagination)
  loadRooms()
}

function openCreateDialog() {
  resetForm()
  dialogVisible.value = true
}

function openEditDialog(row) {
  Object.assign(form, {
    id: row.id,
    roomNumber: row.roomNumber || '',
    roomName: row.roomName || '',
    area: row.area || 0,
    roomType: row.roomType || 'OFFICE',
    status: row.status || 'AVAILABLE',
    description: row.description || ''
  })
  query.buildingId = row.buildingId || query.buildingId
  query.floorId = row.floorId || query.floorId
  dialogVisible.value = true
}

async function saveRoom() {
  if (!query.buildingId || !query.floorId) {
    ElMessage.warning('请先选择楼宇和楼层')
    return
  }
  if (!form.roomNumber) {
    ElMessage.warning('请输入房间号')
    return
  }

  saving.value = true
  try {
    const payload = {
      ...form,
      buildingId: query.buildingId,
      floorId: query.floorId
    }
    if (form.id) {
      await request.put(`/rooms/${form.id}`, payload)
      ElMessage.success('更新成功')
    } else {
      await request.post('/rooms', payload)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    await loadRooms()
  } finally {
    saving.value = false
  }
}

async function deleteRoom(row) {
  await ElMessageBox.confirm(`确认软删除房间 ${row.roomNumber}？`, '提示', { type: 'warning' })
  await request.delete(`/rooms/${row.id}`)
  ElMessage.success('删除成功')
  await loadRooms()
}

onMounted(async () => {
  await loadBuildings()
  if (buildings.value.length > 0) {
    query.buildingId = buildings.value[0].id
    await loadFloors()
    await loadRooms()
  }
})
</script>

<style scoped>
.page-container {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.filter-select {
  width: 180px;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
