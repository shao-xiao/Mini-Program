<template>
  <main class="asset-page spaces-page">
    <header class="asset-page__header">
      <div>
        <h1>楼宇空间管理</h1>
        <p>按楼宇、楼层逐级定位房间，房间数据继续使用统一数据工作台。</p>
      </div>
      <div class="asset-page__header-actions">
        <el-button
          v-if="canViewBuildings"
          text
          @click="navigationCollapsed = !navigationCollapsed"
        >
          {{ navigationCollapsed ? '展开空间导航' : '收起空间导航' }}
        </el-button>
        <el-button :icon="Refresh" :loading="loadingBuildings || loadingFloors" @click="refreshAll">
          刷新
        </el-button>
      </div>
    </header>

    <section class="space-explorer" :class="spaceExplorerClass">
      <aside v-if="canViewBuildings && !navigationCollapsed" class="space-column" aria-label="楼宇列表">
        <div class="space-column__header">
          <div>
            <h2>楼宇</h2>
            <span>{{ filteredBuildings.length }} 栋</span>
          </div>
          <el-button
            v-if="hasPermission('building:create')"
            circle
            text
            :icon="Plus"
            aria-label="新增楼宇"
            @click="openBuildingDialog()"
          />
        </div>
        <div class="space-column__search">
          <el-input
            v-model.trim="buildingKeyword"
            :prefix-icon="Search"
            clearable
            placeholder="搜索楼宇"
            aria-label="搜索楼宇"
          />
        </div>
        <div v-loading="loadingBuildings" class="space-column__list">
          <el-empty v-if="!loadingBuildings && !filteredBuildings.length" description="暂无楼宇" :image-size="64" />
          <button
            v-for="building in filteredBuildings"
            :key="building.id"
            type="button"
            class="space-list-item"
            :class="{ 'is-active': Number(building.id) === Number(selectedBuildingId) }"
            @click="selectBuilding(building.id)"
          >
            <span class="space-list-item__main">
              <strong>{{ building.buildingName || '未命名楼宇' }}</strong>
              <small>{{ building.buildingCode || '未设置编号' }}</small>
            </span>
            <span class="space-list-item__stats">
              <span>{{ floorCount(building) }} 层</span>
              <span>{{ roomCountByBuilding.get(Number(building.id)) ?? '—' }} 间</span>
            </span>
            <span class="space-list-item__status" :class="statusClass(building.status)">
              {{ statusLabel(building.status) }}
            </span>
            <el-dropdown
              v-if="hasPermission('building:update') || hasPermission('building:delete')"
              trigger="click"
              @command="command => handleBuildingCommand(command, building)"
              @click.stop
            >
              <el-button text circle :icon="MoreFilled" :aria-label="`管理${building.buildingName || '楼宇'}`" />
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="detail">查看详情</el-dropdown-item>
                  <el-dropdown-item v-if="hasPermission('building:update')" command="edit">编辑</el-dropdown-item>
                  <el-dropdown-item v-if="hasPermission('building:delete')" command="delete" divided>删除</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </button>
        </div>
      </aside>

      <aside v-if="showFloorNavigation && !navigationCollapsed" class="space-column" aria-label="楼层列表">
        <div class="space-column__header">
          <div>
            <h2>楼层</h2>
            <span>{{ selectedBuilding?.buildingName || '全部楼宇' }}</span>
          </div>
          <el-dropdown
            v-if="selectedBuildingId && hasPermission('floor:create')"
            trigger="click"
            @command="handleFloorCreateCommand"
          >
            <el-button circle text :icon="Plus" aria-label="新增或批量生成楼层" />
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="create">新增楼层</el-dropdown-item>
                <el-dropdown-item command="batch">批量生成楼层</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
        <div class="space-column__search">
          <el-input
            v-model.trim="floorKeyword"
            :prefix-icon="Search"
            clearable
            placeholder="搜索楼层"
            aria-label="搜索楼层"
          />
        </div>
        <div v-loading="loadingFloors" class="space-column__list">
          <button
            v-if="selectedBuildingId"
            type="button"
            class="space-list-item space-list-item--all"
            :class="{ 'is-active': !selectedFloorId }"
            @click="selectFloor(null)"
          >
            <span class="space-list-item__main">
              <strong>全部楼层</strong>
              <small>显示当前楼宇全部房间</small>
            </span>
            <span class="space-list-item__stats">
              <span>{{ roomCountByBuilding.get(Number(selectedBuildingId)) ?? '—' }} 间</span>
            </span>
          </button>
          <el-empty v-if="!loadingFloors && selectedBuildingId && !filteredFloors.length" description="暂无楼层" :image-size="64" />
          <el-empty v-else-if="!selectedBuildingId" description="请先选择楼宇" :image-size="64" />
          <button
            v-for="floor in filteredFloors"
            :key="floor.id"
            type="button"
            class="space-list-item"
            :class="{ 'is-active': Number(floor.id) === Number(selectedFloorId) }"
            @click="selectFloor(floor.id)"
          >
            <span class="space-list-item__main">
              <strong>{{ floorLabel(floor) }}</strong>
              <small>{{ floor.floorCode || `${floor.floorNumber ?? '-'}F` }}</small>
            </span>
            <span class="space-list-item__stats">
              <span>{{ roomCountByFloor.get(Number(floor.id)) ?? '—' }} 间</span>
              <span>{{ areaLabel(floor.totalArea) }}</span>
            </span>
            <span class="space-list-item__status" :class="statusClass(floor.status)">
              {{ statusLabel(floor.status) }}
            </span>
            <el-dropdown
              v-if="hasPermission('floor:update') || hasPermission('floor:delete')"
              trigger="click"
              @command="command => handleFloorCommand(command, floor)"
              @click.stop
            >
              <el-button text circle :icon="MoreFilled" :aria-label="`管理${floorLabel(floor)}`" />
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="detail">查看详情</el-dropdown-item>
                  <el-dropdown-item v-if="hasPermission('floor:update')" command="edit">编辑</el-dropdown-item>
                  <el-dropdown-item v-if="hasPermission('floor:delete')" command="delete" divided>删除</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </button>
        </div>
      </aside>

      <section class="room-panel" aria-label="房间工作台">
        <el-alert
          v-if="canViewFloors && !canViewBuildings"
          class="room-panel__permission-note"
          title="查看楼层导航还需要楼宇查看权限"
          type="warning"
          :closable="false"
          show-icon
        />
        <RoomList
          v-if="canViewRooms"
          embedded
          :building-id="selectedBuildingId"
          :floor-id="selectedFloorId"
        />
        <div v-else class="room-panel__empty">
          <el-empty
            description="当前账号没有房间查看权限"
            :image-size="84"
          />
        </div>
      </section>
    </section>

    <el-dialog v-model="buildingDialogVisible" :title="buildingForm.id ? '编辑楼宇' : '新增楼宇'" width="min(620px, calc(100vw - 32px))">
      <el-form ref="buildingFormRef" :model="buildingForm" :rules="buildingRules" label-width="96px">
        <el-form-item label="名称" prop="buildingName"><el-input v-model.trim="buildingForm.buildingName" /></el-form-item>
        <el-form-item label="编码"><el-input v-model.trim="buildingForm.buildingCode" /></el-form-item>
        <el-form-item label="地址"><el-input v-model.trim="buildingForm.address" /></el-form-item>
        <el-form-item label="楼层数"><el-input-number v-model="buildingForm.totalFloors" :min="0" style="width:100%" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="buildingForm.status" style="width:100%">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明"><el-input v-model="buildingForm.description" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="buildingDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingBuilding" @click="saveBuilding">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="floorDialogVisible" :title="floorForm.id ? '编辑楼层' : '新增楼层'" width="min(520px, calc(100vw - 32px))">
      <el-form ref="floorFormRef" :model="floorForm" :rules="floorRules" label-width="96px">
        <el-form-item label="楼层号" prop="floorNumber"><el-input-number v-model="floorForm.floorNumber" style="width:100%" /></el-form-item>
        <el-form-item label="楼层名称"><el-input v-model.trim="floorForm.floorName" /></el-form-item>
        <el-form-item label="总面积"><el-input-number v-model="floorForm.totalArea" :min="0" :precision="2" style="width:100%" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="floorForm.status" style="width:100%">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明"><el-input v-model="floorForm.description" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="floorDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingFloor" @click="saveFloor">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="batchDialogVisible" title="批量生成楼层" width="min(520px, calc(100vw - 32px))">
      <el-form :model="batchForm" label-width="110px">
        <el-form-item label="地下层范围">
          <div class="range-inputs">
            <el-input-number v-model="batchForm.basementStart" :min="0" />
            <span>至</span>
            <el-input-number v-model="batchForm.basementEnd" :min="0" />
          </div>
        </el-form-item>
        <el-form-item label="地上层范围">
          <div class="range-inputs">
            <el-input-number v-model="batchForm.aboveStart" :min="1" />
            <span>至</span>
            <el-input-number v-model="batchForm.aboveEnd" :min="1" />
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="generatingFloors" @click="generateFloors">生成</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" :title="detailTitle" size="min(460px, 92vw)">
      <el-descriptions v-if="detailRecord" :column="1" border>
        <el-descriptions-item v-for="item in detailItems" :key="item.label" :label="item.label">
          {{ item.value }}
        </el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </main>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MoreFilled, Plus, Refresh, Search } from '@element-plus/icons-vue'
import RoomList from '../building/RoomList.vue'
import request from '../../utils/request'
import { hasPermission } from '../../utils/permission'
import { readPage } from '../../utils/pagination'
import { createLatestRequestGuard } from '../../utils/requestLifecycle'
import { loadAllWorkspaceRecords } from '../../utils/workspaceExport'

const route = useRoute()
const router = useRouter()
const buildings = ref([])
const floors = ref([])
const roomCountByBuilding = ref(new Map())
const roomCountByFloor = ref(new Map())
const selectedBuildingId = ref(null)
const selectedFloorId = ref(null)
const buildingKeyword = ref('')
const floorKeyword = ref('')
const loadingBuildings = ref(false)
const loadingFloors = ref(false)
const savingBuilding = ref(false)
const savingFloor = ref(false)
const generatingFloors = ref(false)
const navigationCollapsed = ref(false)
const buildingDialogVisible = ref(false)
const floorDialogVisible = ref(false)
const batchDialogVisible = ref(false)
const detailVisible = ref(false)
const detailRecord = ref(null)
const detailType = ref('')
const buildingFormRef = ref(null)
const floorFormRef = ref(null)
const buildingRequest = createLatestRequestGuard()
const floorRequest = createLatestRequestGuard()
let pageActive = true

const canViewBuildings = computed(() => hasPermission('building:view') || hasPermission('building:*'))
const canViewFloors = computed(() => hasPermission('floor:view') || hasPermission('floor:*'))
const canViewRooms = computed(() => hasPermission('room:view') || hasPermission('room:*'))
const showFloorNavigation = computed(() => canViewBuildings.value && canViewFloors.value)
const visibleNavigationColumns = computed(() => navigationCollapsed.value
  ? 0
  : Number(canViewBuildings.value) + Number(showFloorNavigation.value))
const spaceExplorerClass = computed(() => ({
  'space-explorer--collapsed': visibleNavigationColumns.value === 0,
  'space-explorer--single-navigation': visibleNavigationColumns.value === 1
}))
const selectedBuilding = computed(() => buildings.value.find(item => Number(item.id) === Number(selectedBuildingId.value)) || null)
const filteredBuildings = computed(() => {
  const keyword = buildingKeyword.value.toLowerCase()
  if (!keyword) return buildings.value
  return buildings.value.filter(item => [item.buildingName, item.buildingCode, item.address]
    .some(value => String(value || '').toLowerCase().includes(keyword)))
})
const filteredFloors = computed(() => {
  const keyword = floorKeyword.value.toLowerCase()
  if (!keyword) return floors.value
  return floors.value.filter(item => [item.floorName, item.floorCode, item.floorNumber]
    .some(value => String(value ?? '').toLowerCase().includes(keyword)))
})

const buildingForm = reactive(defaultBuildingForm())
const floorForm = reactive(defaultFloorForm())
const batchForm = reactive({ basementStart: 2, basementEnd: 1, aboveStart: 1, aboveEnd: 9 })
const buildingRules = { buildingName: [{ required: true, message: '请输入楼宇名称', trigger: 'blur' }] }
const floorRules = { floorNumber: [{ required: true, message: '请输入楼层号', trigger: 'change' }] }

const detailTitle = computed(() => detailType.value === 'building' ? '楼宇详情' : '楼层详情')
const detailItems = computed(() => {
  if (!detailRecord.value) return []
  if (detailType.value === 'building') {
    return [
      { label: '楼宇名称', value: detailRecord.value.buildingName || '-' },
      { label: '楼宇编号', value: detailRecord.value.buildingCode || '-' },
      { label: '地址', value: detailRecord.value.address || '-' },
      { label: '状态', value: statusLabel(detailRecord.value.status) },
      { label: '楼层数', value: floorCount(detailRecord.value) },
      { label: '房间数', value: roomCountByBuilding.value.get(Number(detailRecord.value.id)) ?? '未统计' },
      { label: '说明', value: detailRecord.value.description || '-' }
    ]
  }
  return [
    { label: '楼层名称', value: floorLabel(detailRecord.value) },
    { label: '楼层编号', value: detailRecord.value.floorCode || '-' },
    { label: '楼层号', value: detailRecord.value.floorNumber ?? '-' },
    { label: '房间数', value: roomCountByFloor.value.get(Number(detailRecord.value.id)) ?? '未统计' },
    { label: '总面积', value: areaLabel(detailRecord.value.totalArea) },
    { label: '状态', value: statusLabel(detailRecord.value.status) },
    { label: '说明', value: detailRecord.value.description || '-' }
  ]
})

function defaultBuildingForm() {
  return { id: null, buildingName: '', buildingCode: '', address: '', totalFloors: 1, status: 'ACTIVE', description: '' }
}

function defaultFloorForm() {
  return { id: null, floorNumber: 1, floorName: '', totalArea: 0, status: 'ACTIVE', description: '' }
}

function queryId(value) {
  const id = Number(value)
  return Number.isFinite(id) && id > 0 ? id : null
}

function statusLabel(status) {
  return ['ACTIVE', 'ENABLED'].includes(status) ? '启用' : '停用'
}

function statusClass(status) {
  return ['ACTIVE', 'ENABLED'].includes(status) ? 'is-enabled' : 'is-disabled'
}

function floorLabel(floor) {
  return floor?.floorName || `${floor?.floorNumber ?? '-'}F`
}

function floorCount(building) {
  const liveCount = floors.value.filter(item => Number(item.buildingId) === Number(building.id)).length
  return Number(building.totalFloors ?? liveCount ?? 0)
}

function areaLabel(value) {
  const number = Number(value)
  return Number.isFinite(number) && number > 0 ? `${number.toLocaleString('zh-CN')}㎡` : '未设置面积'
}

async function syncUrl() {
  const nextQuery = { ...route.query }
  if (selectedBuildingId.value) nextQuery.buildingId = String(selectedBuildingId.value)
  else delete nextQuery.buildingId
  if (selectedFloorId.value) nextQuery.floorId = String(selectedFloorId.value)
  else delete nextQuery.floorId
  await router.replace({ path: '/assets/spaces', query: nextQuery })
}

async function loadBuildings() {
  if (!canViewBuildings.value) return
  const requestId = buildingRequest.start()
  loadingBuildings.value = true
  try {
    const data = await request.get('/buildings', { silent: true })
    if (!pageActive || !buildingRequest.isCurrent(requestId)) return
    buildings.value = readPage(data).records
  } catch (error) {
    if (!pageActive || !buildingRequest.isCurrent(requestId)) return
    buildings.value = []
    ElMessage.error(error?.response?.data?.message || '楼宇列表加载失败')
  } finally {
    if (pageActive && buildingRequest.isCurrent(requestId)) loadingBuildings.value = false
  }
}

async function loadFloors(buildingId) {
  floors.value = []
  if (!canViewFloors.value || !buildingId) return
  const requestId = floorRequest.start()
  loadingFloors.value = true
  try {
    const data = await request.get('/floors', { params: { building_id: buildingId }, silent: true })
    if (!pageActive || !floorRequest.isCurrent(requestId)) return
    floors.value = readPage(data).records
  } catch (error) {
    if (!pageActive || !floorRequest.isCurrent(requestId)) return
    floors.value = []
    ElMessage.error(error?.response?.data?.message || '楼层列表加载失败')
  } finally {
    if (pageActive && floorRequest.isCurrent(requestId)) loadingFloors.value = false
  }
}

async function loadRoomCounts() {
  if (!canViewRooms.value) return
  try {
    const rooms = await loadAllWorkspaceRecords(({ page, pageSize }) => request.get('/rooms', {
      params: { page, pageSize },
      silent: true
    }), { pageSize: 100, maxRecords: 10000 })
    if (!pageActive) return
    const buildingsMap = new Map()
    const floorsMap = new Map()
    rooms.forEach(room => {
      const buildingId = Number(room.buildingId)
      const floorId = Number(room.floorId)
      if (buildingId) buildingsMap.set(buildingId, (buildingsMap.get(buildingId) || 0) + 1)
      if (floorId) floorsMap.set(floorId, (floorsMap.get(floorId) || 0) + 1)
    })
    roomCountByBuilding.value = buildingsMap
    roomCountByFloor.value = floorsMap
  } catch (error) {
    roomCountByBuilding.value = new Map()
    roomCountByFloor.value = new Map()
  }
}

async function selectBuilding(id, preferredFloorId = null) {
  selectedBuildingId.value = queryId(id)
  selectedFloorId.value = null
  await loadFloors(selectedBuildingId.value)
  const requestedFloorId = queryId(preferredFloorId)
  if (requestedFloorId && floors.value.some(item => Number(item.id) === requestedFloorId)) {
    selectedFloorId.value = requestedFloorId
  }
  await syncUrl()
}

async function selectFloor(id) {
  selectedFloorId.value = queryId(id)
  await syncUrl()
}

async function refreshAll() {
  const currentBuildingId = selectedBuildingId.value
  const currentFloorId = selectedFloorId.value
  await Promise.all([loadBuildings(), loadRoomCounts()])
  if (currentBuildingId && buildings.value.some(item => Number(item.id) === Number(currentBuildingId))) {
    await selectBuilding(currentBuildingId, currentFloorId)
  } else if (buildings.value.length) {
    await selectBuilding(buildings.value[0].id)
  }
}

function openBuildingDialog(building = null) {
  Object.assign(buildingForm, defaultBuildingForm(), building ? {
    id: building.id,
    buildingName: building.buildingName || '',
    buildingCode: building.buildingCode || '',
    address: building.address || '',
    totalFloors: building.totalFloors ?? 1,
    status: building.status || 'ACTIVE',
    description: building.description || ''
  } : {})
  buildingFormRef.value?.clearValidate()
  buildingDialogVisible.value = true
}

async function saveBuilding() {
  await buildingFormRef.value?.validate()
  savingBuilding.value = true
  try {
    const payload = { ...buildingForm }
    delete payload.id
    if (buildingForm.id) await request.put(`/buildings/${buildingForm.id}`, payload)
    else await request.post('/buildings', payload)
    ElMessage.success(buildingForm.id ? '楼宇更新成功' : '楼宇新增成功')
    buildingDialogVisible.value = false
    const preferredId = buildingForm.id || selectedBuildingId.value
    await loadBuildings()
    if (preferredId) await selectBuilding(preferredId, selectedFloorId.value)
  } finally {
    savingBuilding.value = false
  }
}

async function deleteBuilding(building) {
  await ElMessageBox.confirm(`确定删除楼宇「${building.buildingName}」吗？已有楼层或房间时后端会拒绝删除。`, '删除确认', { type: 'warning' })
  await request.delete(`/buildings/${building.id}`)
  ElMessage.success('楼宇删除成功')
  await refreshAll()
}

function handleBuildingCommand(command, building) {
  if (command === 'detail') showDetail('building', building)
  else if (command === 'edit') openBuildingDialog(building)
  else if (command === 'delete') deleteBuilding(building)
}

function openFloorDialog(floor = null) {
  if (!selectedBuildingId.value) return
  Object.assign(floorForm, defaultFloorForm(), floor ? {
    id: floor.id,
    floorNumber: floor.floorNumber ?? 1,
    floorName: floor.floorName || '',
    totalArea: floor.totalArea ?? 0,
    status: floor.status || 'ACTIVE',
    description: floor.description || ''
  } : {})
  floorFormRef.value?.clearValidate()
  floorDialogVisible.value = true
}

async function saveFloor() {
  await floorFormRef.value?.validate()
  savingFloor.value = true
  try {
    const payload = { ...floorForm, buildingId: selectedBuildingId.value }
    delete payload.id
    if (floorForm.id) await request.put(`/floors/${floorForm.id}`, payload)
    else await request.post('/floors', payload)
    ElMessage.success(floorForm.id ? '楼层更新成功' : '楼层新增成功')
    floorDialogVisible.value = false
    await loadFloors(selectedBuildingId.value)
    await loadRoomCounts()
  } finally {
    savingFloor.value = false
  }
}

async function deleteFloor(floor) {
  await ElMessageBox.confirm(`确定删除楼层「${floorLabel(floor)}」吗？系统会保留历史数据。`, '删除确认', { type: 'warning' })
  await request.delete(`/floors/${floor.id}`)
  ElMessage.success('楼层删除成功')
  if (Number(selectedFloorId.value) === Number(floor.id)) selectedFloorId.value = null
  await loadFloors(selectedBuildingId.value)
  await loadRoomCounts()
  await syncUrl()
}

function handleFloorCommand(command, floor) {
  if (command === 'detail') showDetail('floor', floor)
  else if (command === 'edit') openFloorDialog(floor)
  else if (command === 'delete') deleteFloor(floor)
}

function handleFloorCreateCommand(command) {
  if (command === 'create') openFloorDialog()
  else if (command === 'batch') batchDialogVisible.value = true
}

async function generateFloors() {
  generatingFloors.value = true
  try {
    await request.post('/floors/batch-generate', {
      buildingId: selectedBuildingId.value,
      ...batchForm,
      status: 'ACTIVE'
    })
    ElMessage.success('楼层批量生成完成')
    batchDialogVisible.value = false
    await loadFloors(selectedBuildingId.value)
  } finally {
    generatingFloors.value = false
  }
}

function showDetail(type, record) {
  detailType.value = type
  detailRecord.value = record
  detailVisible.value = true
}

onMounted(async () => {
  await Promise.all([loadBuildings(), loadRoomCounts()])
  const requestedBuildingId = queryId(route.query.buildingId)
  const initialBuildingId = requestedBuildingId && buildings.value.some(item => Number(item.id) === requestedBuildingId)
    ? requestedBuildingId
    : buildings.value[0]?.id || null
  if (initialBuildingId) await selectBuilding(initialBuildingId, route.query.floorId)
})

onBeforeUnmount(() => {
  pageActive = false
  buildingRequest.dispose()
  floorRequest.dispose()
})
</script>

<style scoped>
.asset-page {
  min-width: 0;
  color: var(--dhic-text-primary);
}

.asset-page__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
}

.asset-page__header h1,
.space-column__header h2 {
  margin: 0;
  letter-spacing: 0;
}

.asset-page__header h1 {
  font-size: 20px;
  font-weight: 600;
  line-height: 28px;
}

.asset-page__header p {
  margin: 4px 0 0;
  color: var(--dhic-text-secondary);
  font-size: 13px;
}

.asset-page__header-actions,
.range-inputs {
  display: flex;
  align-items: center;
  gap: 8px;
}

.space-explorer {
  display: grid;
  grid-template-columns: 240px 240px minmax(0, 1fr);
  width: 100%;
  max-width: 100%;
  min-width: 0;
  min-height: calc(100vh - var(--dhic-header-height) - (var(--dhic-page-padding) * 2) - 68px);
  overflow: hidden;
  box-sizing: border-box;
  border: 1px solid var(--dhic-border-color);
  border-radius: var(--dhic-radius-large);
  background: var(--dhic-surface);
  box-shadow: var(--dhic-shadow-card);
}

.space-explorer.space-explorer--collapsed {
  grid-template-columns: minmax(0, 1fr);
}

.space-explorer.space-explorer--single-navigation {
  grid-template-columns: 240px minmax(0, 1fr);
}

.space-column {
  display: flex;
  min-width: 0;
  flex-direction: column;
  border-right: 1px solid var(--dhic-border-color-light);
  background: #fff;
}

.space-column__header {
  display: flex;
  min-height: 56px;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--dhic-border-color-light);
}

.space-column__header h2 {
  font-size: 14px;
  line-height: 20px;
}

.space-column__header span {
  display: block;
  max-width: 170px;
  overflow: hidden;
  color: var(--dhic-text-muted, #909399);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.space-column__search {
  padding: 10px 12px;
  border-bottom: 1px solid var(--dhic-border-color, #e4e7ed);
}

.space-column__list {
  min-height: 180px;
  overflow-y: auto;
  overscroll-behavior: contain;
}

.space-list-item {
  position: relative;
  display: grid;
  width: 100%;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 4px 8px;
  padding: 10px 10px 10px 14px;
  border: 0;
  border-bottom: 1px solid #f0f2f5;
  background: #fff;
  color: inherit;
  cursor: pointer;
  text-align: left;
}

.space-list-item::before {
  position: absolute;
  inset: 0 auto 0 0;
  width: 3px;
  background: transparent;
  content: '';
}

.space-list-item:hover {
  background: #fafbfc;
}

.space-list-item:focus-visible {
  z-index: 1;
  outline: 2px solid var(--el-color-primary-light-3);
  outline-offset: -2px;
}

.space-list-item.is-active {
  background: var(--dhic-brand-red-light);
}

.space-list-item.is-active::before {
  background: var(--el-color-primary);
}

.space-list-item__main {
  min-width: 0;
}

.space-list-item__main strong,
.space-list-item__main small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.space-list-item__main strong {
  font-size: 13px;
  font-weight: 600;
  line-height: 20px;
}

.space-list-item__main small,
.space-list-item__stats {
  color: var(--dhic-text-muted, #909399);
  font-size: 11px;
  line-height: 18px;
}

.space-list-item__stats {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
  white-space: nowrap;
}

.space-list-item__status {
  justify-self: start;
  padding: 1px 6px;
  border-radius: 3px;
  font-size: 11px;
}

.space-list-item__status.is-enabled {
  background: var(--dhic-success-light);
  color: #0f8153;
}

.space-list-item__status.is-disabled {
  background: #f4f4f5;
  color: #73767a;
}

.space-list-item :deep(.el-dropdown) {
  position: absolute;
  right: 4px;
  bottom: 4px;
}

.room-panel {
  min-width: 0;
  overflow: hidden;
  background: var(--dhic-page-bg);
}

.room-panel__empty {
  display: grid;
  min-height: 480px;
  place-items: center;
}

.room-panel__permission-note {
  margin: 12px 12px 0;
}

@media (max-width: 1280px) {
  .space-explorer {
    grid-template-columns: 210px 220px minmax(0, 1fr);
  }

  .space-explorer.space-explorer--single-navigation {
    grid-template-columns: 220px minmax(0, 1fr);
  }
}

@media (max-width: 1024px) {
  .asset-page__header {
    flex-wrap: wrap;
  }

  .space-explorer {
    grid-template-columns: 170px 180px minmax(0, 1fr);
    overflow-x: hidden;
  }

  .space-explorer.space-explorer--single-navigation {
    grid-template-columns: minmax(164px, 0.8fr) minmax(0, 3.2fr);
  }
}
</style>

<style scoped>
/* Reference-style density and hierarchy for the space explorer. */
.spaces-page {
  gap: 12px;
}

.spaces-page > .asset-page__header {
  position: relative;
  min-height: 56px;
  padding: 4px 0 4px 16px;
  border-left: 3px solid var(--dhic-brand-red);
}

.spaces-page > .asset-page__header h1,
.spaces-page > .asset-page__header h2 {
  margin-bottom: 4px;
}

.space-explorer {
  grid-template-columns: minmax(230px, 0.8fr) minmax(210px, 0.72fr) minmax(0, 2.8fr);
  min-height: calc(100vh - var(--dhic-header-height) - 112px);
  overflow: hidden;
  border: 1px solid var(--dhic-border-color);
  border-radius: var(--dhic-radius-large);
  background: var(--dhic-card-bg);
  box-shadow: var(--dhic-shadow-card);
}

.space-column {
  min-width: 0;
  background: #f8f9fb;
  border-right: 1px solid var(--dhic-border-color);
}

.space-column__header {
  min-height: 54px;
  padding: 12px 14px 10px;
  background: var(--dhic-card-bg);
  border-bottom: 1px solid var(--dhic-border-color-light);
}

.space-column__search {
  padding: 10px 12px;
  background: var(--dhic-card-bg);
  border-bottom: 1px solid var(--dhic-border-color-light);
}

.space-column__list {
  padding: 8px;
}

.space-list-item {
  margin-bottom: 7px;
  padding: 11px 10px;
  overflow: hidden;
  border: 1px solid var(--dhic-border-color-light);
  border-left: 3px solid transparent;
  border-radius: var(--dhic-radius-base);
  background: var(--dhic-card-bg);
  box-shadow: 0 1px 2px rgba(31, 35, 41, 0.03);
}

.space-list-item:hover {
  border-color: #dddfe4;
  background: #fffafa;
}

.space-column:first-child .space-list-item.is-active {
  color: #fff;
  border-color: #3c3f44;
  border-left-color: var(--dhic-brand-red);
  background: #3c3f44;
  box-shadow: 0 5px 14px rgba(31, 35, 41, 0.16);
}

.space-column:first-child .space-list-item.is-active .space-list-item__main,
.space-column:first-child .space-list-item.is-active .space-list-item__stats {
  color: #fff;
}

.space-column:first-child .space-list-item.is-active .space-list-item__stats {
  opacity: 0.76;
}

.space-column:first-child .space-list-item.is-active .space-list-item__status {
  color: #354052;
  background: #f7f8fa;
}

.space-column:first-child .space-list-item.is-active :deep(.el-button) {
  color: rgba(255, 255, 255, 0.9);
}

.space-column:nth-child(2) .space-list-item.is-active {
  border-color: #ffd7d2;
  border-left-color: var(--dhic-brand-red);
  background: var(--dhic-brand-red-light);
}

.room-panel {
  min-width: 0;
  background: var(--dhic-card-bg);
}

.room-panel :deep(.page-container--embedded) {
  padding: 0;
}

.room-panel :deep(.data-workspace) {
  border: 0;
  border-radius: 0;
  box-shadow: none;
}

.room-panel :deep(.workspace-header) {
  min-height: 62px;
  padding: 12px 16px;
}

.room-panel :deep(.workspace-metrics) {
  padding: 10px 16px 8px;
}

.room-panel :deep(.list-summary-grid) {
  gap: 10px;
}

.room-panel :deep(.list-summary-card) {
  min-height: 86px;
  padding: 12px 14px;
}

.room-panel :deep(.workspace-toolbar) {
  margin: 0 16px 10px;
}

.room-panel :deep(.workspace-grid) {
  margin: 0 16px 16px;
}

@media (max-width: 1366px) {
  .space-explorer {
    grid-template-columns: minmax(210px, 0.78fr) minmax(195px, 0.7fr) minmax(0, 2.65fr);
  }

  .space-list-item {
    padding: 10px 9px;
  }

  .room-panel :deep(.workspace-header) {
    padding-inline: 14px;
  }

  .room-panel :deep(.workspace-metrics) {
    padding-inline: 14px;
  }

  .room-panel :deep(.workspace-toolbar),
  .room-panel :deep(.workspace-grid) {
    margin-inline: 14px;
  }
}
</style>
