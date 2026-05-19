<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>空间资产</span>
          <el-button type="primary" @click="openCreateDialog">新增资产</el-button>
        </div>
      </template>

      <el-form inline>
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" class="filter-input" placeholder="编号/名称/序列号" clearable />
        </el-form-item>
        <el-form-item label="楼宇">
          <el-select v-model="query.buildingId" class="filter-select" clearable placeholder="全部楼宇" @change="onQueryBuildingChange">
            <el-option v-for="item in buildings" :key="item.id" :label="item.buildingName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="楼层">
          <el-select v-model="query.floorId" class="filter-select" clearable placeholder="全部楼层" @change="loadAssets">
            <el-option v-for="item in queryFloors" :key="item.id" :label="formatFloorLabel(item)" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" class="filter-select" clearable placeholder="全部状态" @change="loadAssets">
            <el-option v-for="item in assetStatuses" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadAssets">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="assets" border style="width: 100%">
        <el-table-column prop="assetCode" label="资产编号" min-width="140" />
        <el-table-column prop="assetName" label="资产名称" min-width="150" />
        <el-table-column prop="assetCategory" label="分类" width="110" />
        <el-table-column prop="assetType" label="类型" width="110" />
        <el-table-column prop="buildingName" label="楼宇" min-width="120" />
        <el-table-column label="楼层" width="100">
          <template #default="{ row }">{{ row.floorName || `${row.floorNumber}F` }}</template>
        </el-table-column>
        <el-table-column label="房间/区域" min-width="120">
          <template #default="{ row }">{{ row.roomName || row.roomNumber || '-' }}</template>
        </el-table-column>
        <el-table-column prop="locationDesc" label="详细位置" min-width="130" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="assetStatusTag(row.status)">{{ row.statusText || assetStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="nextMaintenanceDate" label="下次维保" width="120" />
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openEditDialog(row)">编辑</el-button>
            <el-button size="small" @click="openTransferDialog(row)">转移</el-button>
            <el-button size="small" type="danger" @click="deleteAsset(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑资产' : '新增资产'" width="680px">
      <el-form :model="form" label-width="110px">
        <div class="form-grid">
          <el-form-item label="资产编号" required>
            <el-input v-model="form.assetCode" />
          </el-form-item>
          <el-form-item label="资产名称" required>
            <el-input v-model="form.assetName" />
          </el-form-item>
          <el-form-item label="分类">
            <el-input v-model="form.assetCategory" placeholder="如强电/空调/消防" />
          </el-form-item>
          <el-form-item label="类型">
            <el-input v-model="form.assetType" placeholder="如配电柜/空调主机" />
          </el-form-item>
          <el-form-item label="楼宇" required>
            <el-select v-model="form.buildingId" style="width:100%" @change="onFormBuildingChange">
              <el-option v-for="item in buildings" :key="item.id" :label="item.buildingName" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="楼层" required>
            <el-select v-model="form.floorId" style="width:100%" @change="onFormFloorChange">
              <el-option v-for="item in formFloors" :key="item.id" :label="formatFloorLabel(item)" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="房间/区域">
            <el-select v-model="form.roomId" style="width:100%" clearable>
              <el-option v-for="item in formRooms" :key="item.id" :label="item.roomName || item.roomNumber" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="form.status" style="width:100%">
              <el-option v-for="item in assetStatuses" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="厂家">
            <el-input v-model="form.manufacturer" />
          </el-form-item>
          <el-form-item label="型号">
            <el-input v-model="form.model" />
          </el-form-item>
          <el-form-item label="序列号">
            <el-input v-model="form.serialNo" />
          </el-form-item>
          <el-form-item label="维保周期">
            <el-input-number v-model="form.maintenanceCycleDays" :min="0" style="width:100%" />
          </el-form-item>
        </div>

        <el-form-item label="详细位置">
          <el-input v-model="form.locationDesc" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveAsset">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="transferVisible" title="资产转移" width="560px">
      <el-form :model="transferForm" label-width="100px">
        <el-form-item label="楼宇">
          <el-select v-model="transferForm.buildingId" style="width:100%" @change="onTransferBuildingChange">
            <el-option v-for="item in buildings" :key="item.id" :label="item.buildingName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="楼层">
          <el-select v-model="transferForm.floorId" style="width:100%" @change="onTransferFloorChange">
            <el-option v-for="item in transferFloors" :key="item.id" :label="formatFloorLabel(item)" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="房间">
          <el-select v-model="transferForm.roomId" style="width:100%" clearable>
            <el-option v-for="item in transferRooms" :key="item.id" :label="item.roomName || item.roomNumber" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="详细位置">
          <el-input v-model="transferForm.locationDesc" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="transferForm.description" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="transferVisible = false">取消</el-button>
        <el-button type="primary" @click="transferAsset">确认转移</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../utils/request'

const buildings = ref([])
const queryFloors = ref([])
const formFloors = ref([])
const formRooms = ref([])
const transferFloors = ref([])
const transferRooms = ref([])
const assets = ref([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const transferVisible = ref(false)

const assetStatuses = [
  { label: '使用中', value: 'IN_USE' },
  { label: '闲置', value: 'IDLE' },
  { label: '维修中', value: 'MAINTENANCE' },
  { label: '停用', value: 'DISABLED' },
  { label: '报废', value: 'SCRAPPED' }
]

const query = reactive({
  keyword: '',
  buildingId: null,
  floorId: null,
  status: ''
})

const form = reactive(defaultForm())
const transferForm = reactive({
  assetId: null,
  buildingId: null,
  floorId: null,
  roomId: null,
  locationDesc: '',
  operator: 'admin',
  description: ''
})

function defaultForm() {
  return {
    id: null,
    assetCode: '',
    assetName: '',
    assetCategory: '',
    assetType: '',
    manufacturer: '',
    model: '',
    serialNo: '',
    buildingId: null,
    floorId: null,
    roomId: null,
    locationDesc: '',
    status: 'IN_USE',
    maintenanceCycleDays: null,
    remark: ''
  }
}

function assetStatusText(value) {
  return assetStatuses.find(item => item.value === value)?.label || value
}

function assetStatusTag(value) {
  if (value === 'IN_USE') return 'success'
  if (value === 'MAINTENANCE') return 'warning'
  if (value === 'SCRAPPED' || value === 'DISABLED') return 'danger'
  return 'info'
}

function formatFloorLabel(floor) {
  return floor.floorName || `${floor.floorNumber}F`
}

async function loadBuildings() {
  const data = await request.get('/buildings')
  buildings.value = Array.isArray(data) ? data : (data?.content || [])
}

async function loadFloors(buildingId) {
  if (!buildingId) return []
  const data = await request.get('/floors', { params: { building_id: buildingId } })
  return Array.isArray(data) ? data : []
}

async function loadRooms(buildingId, floorId) {
  if (!buildingId || !floorId) return []
  const data = await request.get('/rooms', { params: { building_id: buildingId, floor_id: floorId } })
  return Array.isArray(data) ? data : []
}

async function onQueryBuildingChange() {
  query.floorId = null
  queryFloors.value = await loadFloors(query.buildingId)
  await loadAssets()
}

async function loadAssets() {
  loading.value = true
  try {
    const data = await request.get('/assets', {
      params: {
        keyword: query.keyword || undefined,
        buildingId: query.buildingId || undefined,
        floorId: query.floorId || undefined,
        status: query.status || undefined
      }
    })
    assets.value = Array.isArray(data) ? data : []
  } finally {
    loading.value = false
  }
}

async function onFormBuildingChange() {
  form.floorId = null
  form.roomId = null
  formFloors.value = await loadFloors(form.buildingId)
  formRooms.value = []
}

async function onFormFloorChange() {
  form.roomId = null
  formRooms.value = await loadRooms(form.buildingId, form.floorId)
}

function openCreateDialog() {
  Object.assign(form, defaultForm())
  formFloors.value = []
  formRooms.value = []
  dialogVisible.value = true
}

async function openEditDialog(row) {
  Object.assign(form, {
    ...defaultForm(),
    ...row
  })
  formFloors.value = await loadFloors(form.buildingId)
  formRooms.value = await loadRooms(form.buildingId, form.floorId)
  dialogVisible.value = true
}

async function saveAsset() {
  if (!form.assetCode || !form.assetName || !form.buildingId || !form.floorId) {
    ElMessage.warning('请填写资产编号、名称、楼宇和楼层')
    return
  }
  saving.value = true
  try {
    const payload = { ...form }
    if (form.id) {
      await request.put(`/assets/${form.id}`, payload)
      ElMessage.success('资产更新成功')
    } else {
      await request.post('/assets', payload)
      ElMessage.success('资产新增成功')
    }
    dialogVisible.value = false
    await loadAssets()
  } finally {
    saving.value = false
  }
}

async function openTransferDialog(row) {
  Object.assign(transferForm, {
    assetId: row.id,
    buildingId: row.buildingId,
    floorId: row.floorId,
    roomId: row.roomId || null,
    locationDesc: row.locationDesc || '',
    operator: 'admin',
    description: ''
  })
  transferFloors.value = await loadFloors(row.buildingId)
  transferRooms.value = await loadRooms(row.buildingId, row.floorId)
  transferVisible.value = true
}

async function onTransferBuildingChange() {
  transferForm.floorId = null
  transferForm.roomId = null
  transferFloors.value = await loadFloors(transferForm.buildingId)
  transferRooms.value = []
}

async function onTransferFloorChange() {
  transferForm.roomId = null
  transferRooms.value = await loadRooms(transferForm.buildingId, transferForm.floorId)
}

async function transferAsset() {
  await request.post(`/assets/${transferForm.assetId}/transfer`, { ...transferForm })
  ElMessage.success('资产位置已更新')
  transferVisible.value = false
  await loadAssets()
}

async function deleteAsset(row) {
  await ElMessageBox.confirm(`确认软删除资产 ${row.assetName}？`, '提示', { type: 'warning' })
  await request.delete(`/assets/${row.id}`)
  ElMessage.success('删除成功')
  await loadAssets()
}

onMounted(async () => {
  await loadBuildings()
  await loadAssets()
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

.filter-input {
  width: 190px;
}

.filter-select {
  width: 160px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 12px;
}
</style>
