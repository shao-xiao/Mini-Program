<template>
  <div class="page-container" :class="{ 'page-container--embedded': embedded }">
    <DataWorkspace
      :workspace-config="roomWorkspaceConfig"
      :views="workspaceViews"
      :active-view-id="activeViewId"
      :view-dirty="isViewDirty"
      :filter-logic="tablePrefs.filterLogic"
      :filter-logic-visible="tablePrefs.filters.length > 1"
      :filter-items="activeFilterItems"
      :query-notices="queryNotices"
      :metric-data="roomMetricData"
      :metric-states="roomMetricStates"
      @change-view="switchRoomView"
      @create-view="createTableView"
      @duplicate-view="duplicateTableView"
      @rename-view="renameTableView"
      @delete-view="deleteTableView"
      @save-view="saveRoomView"
      @remove-filter="removeViewFilter"
    >
      <template #header-actions>
        <el-button
          v-if="hasPermission(roomPermissions.create)"
          type="primary"
          :icon="Plus"
          :disabled="!query.buildingId"
          @click="openCreateDialog"
        >
          {{ roomWorkspaceConfig.primaryAction.label }}
        </el-button>
      </template>

      <template #toolbar>
        <DhicDataToolbar
          :workspace-config="roomWorkspaceConfig"
          allow-column-layout
          field-config-saveable
          :view-name="activeViewName"
          :field-config-dirty="isViewDirty"
          :columns="orderedColumns"
          :visible-columns="tablePrefs.visibleColumns"
          :column-order="tablePrefs.columnOrder"
          :fixed-columns="tablePrefs.fixedColumns"
          :filter-options="filterOptions"
          :filters="tablePrefs.filters"
          :filter-logic="tablePrefs.filterLogic"
          :filter-logics="filterLogicOptions"
          :sort-options="sortOptions"
          :sorts="tablePrefs.sorts"
          :sort-key="tablePrefs.sortKey"
          :sort-order="tablePrefs.sortOrder"
          :sort-count="sortCount"
          :group-options="groupOptions"
          :groups="tablePrefs.groups"
          :group-key="tablePrefs.groupKey"
          :group-count="groupCount"
          :row-density="tablePrefs.rowDensity"
          :active-filter-count="activeFilterCount"
          :filter-items="activeFilterItems"
          @update:visible-columns="updateVisibleColumns"
          @update:column-order="updateColumnOrder"
          @update:fixed-columns="updateFixedColumns"
          @update:filters="updateRoomFilters"
          @update:filter-logic="updateFilterLogic"
          @update:sort-key="updateSortKey"
          @update:sort-order="updateSortOrder"
          @update:sorts="updateSorts"
          @update:group-key="updateGroupKey"
          @update:groups="updateGroups"
          @update:row-density="updateRowDensity"
          @reset-view="resetRoomView"
          @save-field-config="saveRoomView"
        >
          <template #right>
            <template v-if="!embedded">
              <span class="selector-label">楼宇</span>
              <el-select
                v-model="query.buildingId"
                class="location-selector"
                filterable
                :loading="buildingLoading"
                placeholder="请选择楼宇"
                aria-label="选择房间所属楼宇"
                @change="onBuildingChange"
              >
                <el-option
                  v-for="item in buildings"
                  :key="item.id"
                  :label="item.buildingName"
                  :value="item.id"
                />
              </el-select>
              <span class="selector-label">楼层</span>
              <el-select
                v-model="query.floorId"
                class="location-selector"
                clearable
                filterable
                :disabled="!query.buildingId"
                placeholder="全部楼层"
                aria-label="筛选房间楼层"
                @change="searchRooms"
              >
                <el-option
                  v-for="item in floors"
                  :key="item.id"
                  :label="formatFloorLabel(item)"
                  :value="item.id"
                />
              </el-select>
            </template>
            <span v-else class="selector-context">{{ embeddedLocationLabel }}</span>
            <el-tooltip content="刷新房间列表" placement="bottom">
              <el-button
                text
                circle
                :icon="Refresh"
                aria-label="刷新房间列表"
                @click="searchRooms"
              />
            </el-tooltip>
            <span class="view-hint">
              当前页 {{ resultScope.loadedRecords }} 条 / 共 {{ resultScope.serverTotal }} 条
            </span>
          </template>
        </DhicDataToolbar>
      </template>

      <template #grid>
        <WorkspaceDataGrid
          :loading="loading"
          :error-text="listErrorText"
          retryable
          :rows="tableRows"
          :columns="visibleOrderedColumns"
          row-key="id"
          :current-page="pagination.currentPage"
          :page-size="pagination.pageSize"
          :index-width="72"
          :density="tablePrefs.rowDensity"
          :empty-text="query.buildingId || embedded ? tableEmptyText : '请先选择楼宇'"
          :sort-state="activeTableSort"
          :data-mode="roomWorkspaceConfig.query.dataMode"
          :column-width="columnWidth"
          :column-fixed="columnFixed"
          :is-column-sortable="isColumnSortable"
          :show-overflow-tooltip="column => column.key === 'description'"
          :operation-column="{ width: 156, fixed: 'right', align: 'left' }"
          @column-resize="handleHeaderDragEnd"
          @sort-change="handleTableSortChange"
          @retry="loadRooms"
          aria-label="房间管理数据表格"
        >
          <template #operations="{ row }">
            <div class="row-actions">
              <el-button
                v-if="hasPermission(roomPermissions.update)"
                size="small"
                type="primary"
                @click="openEditDialog(row)"
              >
                编辑
              </el-button>
              <el-button
                v-if="hasPermission(roomPermissions.delete)"
                size="small"
                type="danger"
                @click="deleteRoom(row)"
              >
                删除
              </el-button>
            </div>
          </template>
        </WorkspaceDataGrid>
      </template>

      <template #footer>
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
      </template>
    </DataWorkspace>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑房间' : '新增房间'" width="min(560px, calc(100vw - 32px))">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="楼宇" prop="buildingId">
          <el-select v-model="form.buildingId" style="width: 100%" placeholder="请选择楼宇" @change="onFormBuildingChange">
            <el-option v-for="item in buildings" :key="item.id" :label="item.buildingName" :value="item.id" />
          </el-select>
        </el-form-item>

        <el-form-item label="楼层" prop="floorId">
          <el-select v-model="form.floorId" style="width: 100%" placeholder="请选择楼层">
            <el-option v-for="item in formFloors" :key="item.id" :label="formatFloorLabel(item)" :value="item.id" />
          </el-select>
        </el-form-item>

        <el-form-item label="房间号" prop="roomNumber">
          <el-input v-model="form.roomNumber" />
        </el-form-item>

        <el-form-item label="房间名称">
          <el-input v-model="form.roomName" />
        </el-form-item>

        <el-form-item label="面积">
          <el-input-number v-model="form.area" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="物业费计费面积" required>
          <el-input-number v-model="form.propertyFeeArea" :min="0" :precision="2" style="width: 100%" />
          <div class="form-tip">物业费计划以此面积形成计费依据，账单会保存面积快照。</div>
        </el-form-item>

        <el-form-item label="类型">
          <el-select v-model="form.roomType" style="width: 100%">
            <el-option v-for="item in roomTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>

        <el-form-item v-if="form.id" label="出租状态">
          <el-input :model-value="roomStatusOptions.find(item => item.value === form.status)?.label || form.status" disabled />
          <div class="form-tip">出租状态由合同入住、退租流程统一维护。</div>
        </el-form-item>

        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button
          v-if="form.id ? hasPermission(roomPermissions.update) : hasPermission(roomPermissions.create)"
          type="primary"
          :loading="saving"
          @click="saveRoom"
        >
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import DataWorkspace from '../../components/DataWorkspace.vue'
import DhicDataToolbar from '../../components/DhicDataToolbar.vue'
import WorkspaceDataGrid from '../../components/workspace/WorkspaceDataGrid.vue'
import { useDhicTableView } from '../../composables/useDhicTableView'
import {
  useWorkspaceListRequestState,
  useWorkspaceMetricState
} from '../../composables/workspace/useWorkspaceRequestState'
import request from '../../utils/request'
import { hasPermission } from '../../utils/permission'
import { createPagination, pageParams, readPage, resetToFirstPage } from '../../utils/pagination'
import { loadListTotalResult, loadListTotalsDetailed } from '../../utils/listSummary'
import {
  roomStatusOptions,
  roomTypeOptions,
  roomWorkspaceConfig
} from '../../workspaceConfigs/buildingWorkspaceConfig'
import { workspaceTableOptions } from '../../workspaceConfigs/workspaceConfig'

const props = defineProps({
  embedded: { type: Boolean, default: false },
  buildingId: { type: [Number, String], default: null },
  floorId: { type: [Number, String], default: null }
})

const embedded = computed(() => props.embedded)

const buildings = ref([])
const floors = ref([])
const formFloors = ref([])
const rooms = ref([])
const buildingLoading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const formRef = ref(null)
const pagination = reactive(createPagination(20))
const query = reactive({ buildingId: null, floorId: null })
const form = reactive(defaultForm())
const roomPermissions = roomWorkspaceConfig.permissions
const roomMetricKeys = ['total', 'filtered', 'available', 'rented', 'unavailable']
const roomMetrics = useWorkspaceMetricState(roomMetricKeys)
const roomMetricData = roomMetrics.values
const roomMetricStates = roomMetrics.states
let pageActive = true
let queryFloorRevision = 0

const embeddedLocationLabel = computed(() => {
  const building = buildings.value.find(item => Number(item.id) === Number(query.buildingId))
  const floor = floors.value.find(item => Number(item.id) === Number(query.floorId))
  if (!building) return '全部房间'
  return floor
    ? `${building.buildingName || '未命名楼宇'} / ${formatFloorLabel(floor)}`
    : `${building.buildingName || '未命名楼宇'} / 全部楼层`
})

const roomListState = useWorkspaceListRequestState({
  hasResults: () => rooms.value.length > 0,
  clearResults: () => {
    rooms.value = []
    pagination.total = 0
  },
  staleMessage: '房间列表刷新失败，当前显示上次成功数据'
})
const loading = computed(() => roomListState.status.value === 'loading')
const listErrorText = roomListState.errorText

const {
  workspaceViews,
  activeViewId,
  activeViewName,
  isViewDirty,
  tablePrefs,
  orderedColumns,
  visibleOrderedColumns,
  tableRows,
  tableEmptyText,
  sortOptions,
  groupOptions,
  filterOptions,
  filterLogicOptions,
  serverQueryParams,
  queryNotices,
  queryRequestGuard: roomRequestGuard,
  debouncedQueryTask: debouncedRoomQuery,
  disposeQueryLifecycle,
  resultScope,
  activeFilterItems,
  activeFilterCount,
  groupCount,
  sortCount,
  activeTableSort,
  updateVisibleColumns,
  updateColumnOrder,
  updateFixedColumns,
  columnWidth,
  columnFixed,
  handleHeaderDragEnd,
  handleTableSortChange,
  isColumnSortable,
  updateFilterLogic,
  updateFilters,
  updateSortKey,
  updateSortOrder,
  updateSorts,
  updateGroupKey,
  updateGroups,
  updateRowDensity,
  resetTableView,
  switchTableView,
  createTableView,
  renameTableView,
  duplicateTableView,
  deleteTableView,
  saveCurrentView,
  discardCurrentViewChanges,
  removeViewFilter
} = useDhicTableView(workspaceTableOptions(roomWorkspaceConfig, {
  records: () => rooms.value,
  serverTotal: () => pagination.total,
  queryLifecycle: {
    task: () => {
      resetToFirstPage(pagination)
      loadRooms()
    }
  },
  groupLabel: value => roomStatusOptions.find(option => option.value === value)?.label || value || '未填写'
}))

const rules = {
  buildingId: [{ required: true, message: '请选择楼宇', trigger: 'change' }],
  floorId: [{ required: true, message: '请选择楼层', trigger: 'change' }],
  roomNumber: [{ required: true, message: '请输入房间号', trigger: 'blur' }]
}

watch(() => JSON.stringify(serverQueryParams.value), () => debouncedRoomQuery.schedule())

function defaultForm() {
  return {
    id: null,
    buildingId: null,
    floorId: null,
    roomNumber: '',
    roomName: '',
    area: 0,
    propertyFeeArea: 0,
    roomType: 'OFFICE',
    status: 'VACANT',
    description: ''
  }
}

function resetForm() {
  Object.assign(form, defaultForm())
  formFloors.value = []
  formRef.value?.clearValidate()
}

function formatFloorLabel(floor) {
  return floor.floorName || `${floor.floorNumber}F`
}

function normalizedRoomStatus(status) {
  return status === 'AVAILABLE' ? 'VACANT' : (status || 'VACANT')
}

async function switchRoomView(viewId) {
  if (viewId === activeViewId.value) return
  if (isViewDirty.value) {
    try {
      await ElMessageBox.confirm('当前视图有未保存的调整，是否保存后切换？', '切换视图', {
        distinguishCancelAndClose: true,
        confirmButtonText: '保存并切换',
        cancelButtonText: '放弃调整'
      })
      saveCurrentView()
    } catch (action) {
      if (action === 'cancel') discardCurrentViewChanges()
      else return
    }
  }
  switchTableView(viewId)
}

function saveRoomView() {
  saveCurrentView()
  ElMessage.success(`视图“${activeViewName.value}”已保存`)
}

function updateRoomFilters(filters) {
  updateFilters(filters)
}

function resetRoomView() {
  resetTableView()
}

async function loadBuildings() {
  buildingLoading.value = true
  try {
    const data = await request.get('/buildings', { silent: true })
    if (!pageActive) return
    buildings.value = readPage(data).records
  } catch (error) {
    if (!pageActive) return
    buildings.value = []
    ElMessage.error(error?.response?.data?.message || '楼宇列表加载失败')
  } finally {
    if (pageActive) buildingLoading.value = false
  }
}

async function fetchFloors(buildingId) {
  if (!buildingId || (!hasPermission('floor:view') && !hasPermission('floor:*'))) return []
  const data = await request.get('/floors', {
    params: { building_id: buildingId },
    silent: true
  })
  return readPage(data).records
}

async function loadQueryFloors(preferredFloorId = null) {
  const revision = ++queryFloorRevision
  if (!query.buildingId) {
    floors.value = []
    query.floorId = null
    return
  }
  try {
    const records = await fetchFloors(query.buildingId)
    if (!pageActive || revision !== queryFloorRevision) return
    floors.value = records
    const matched = preferredFloorId && records.some(item => Number(item.id) === Number(preferredFloorId))
    query.floorId = matched ? preferredFloorId : null
  } catch (error) {
    if (!pageActive || revision !== queryFloorRevision) return
    floors.value = []
    query.floorId = null
    ElMessage.error(error?.response?.data?.message || '楼层列表加载失败')
  }
}

async function loadFormFloors(buildingId, preferredFloorId = null) {
  try {
    formFloors.value = await fetchFloors(buildingId)
    const matched = preferredFloorId && formFloors.value.some(item => Number(item.id) === Number(preferredFloorId))
    form.floorId = matched ? preferredFloorId : (formFloors.value[0]?.id || null)
  } catch (error) {
    formFloors.value = []
    form.floorId = null
    ElMessage.error(error?.response?.data?.message || '楼层列表加载失败')
  }
}

async function onBuildingChange() {
  resetToFirstPage(pagination)
  rooms.value = []
  query.floorId = null
  await loadQueryFloors()
  await loadRooms()
}

async function onFormBuildingChange() {
  form.floorId = null
  await loadFormFloors(form.buildingId)
}

function searchRooms() {
  resetToFirstPage(pagination)
  loadRooms()
}

function roomListParams() {
  return {
    building_id: query.buildingId || undefined,
    floor_id: query.floorId || undefined,
    ...serverQueryParams.value,
    ...pageParams(pagination)
  }
}

function roomSummaryParams(overrides = {}) {
  return {
    building_id: query.buildingId || undefined,
    floor_id: query.floorId || undefined,
    ...overrides
  }
}

async function loadRooms() {
  if (!query.buildingId && !props.embedded) {
    const context = roomListState.begin({ buildingId: null })
    roomListState.succeed(context)
    const metricRevision = roomMetrics.begin()
    roomMetricKeys.forEach(key => roomMetrics.resolve(metricRevision, key, 0))
    return
  }

  const requestId = roomRequestGuard.start()
  const params = roomListParams()
  const requestContext = roomListState.begin({ viewId: activeViewId.value, params })
  const metricRevision = roomMetrics.begin()

  const listTask = request.get('/rooms', { params, silent: true }).then(
    value => applyRoomListResult(requestId, requestContext, metricRevision, { status: 'fulfilled', value }),
    reason => applyRoomListResult(requestId, requestContext, metricRevision, { status: 'rejected', reason })
  )
  const metricsTask = loadRoomMetrics(requestId, requestContext, metricRevision)
  await Promise.allSettled([listTask, metricsTask])
}

function applyRoomListResult(requestId, requestContext, metricRevision, result) {
  if (!roomRequestGuard.isCurrent(requestId) || !roomListState.isCurrent(requestContext)) return
  if (result.status === 'fulfilled') {
    const page = readPage(result.value)
    rooms.value = page.records
    pagination.total = page.total
    roomListState.succeed(requestContext)
    roomMetrics.resolve(metricRevision, 'filtered', page.total)
  } else {
    rooms.value = []
    pagination.total = 0
    roomListState.fail(requestContext, result.reason?.response?.data?.message || '房间列表加载失败，请稍后重试')
    roomMetrics.reject(metricRevision, 'filtered')
  }
}

async function loadRoomMetrics(requestId, requestContext, revision) {
  const [totalResult, statusResults] = await Promise.all([
    loadListTotalResult('/rooms', roomSummaryParams()),
    loadListTotalsDetailed([
      { key: 'available', url: '/rooms', params: roomSummaryParams({ status: 'VACANT' }) },
      { key: 'rented', url: '/rooms', params: roomSummaryParams({ status: 'RENTED' }) },
      { key: 'reserved', url: '/rooms', params: roomSummaryParams({ status: 'RESERVED' }) },
      { key: 'renovating', url: '/rooms', params: roomSummaryParams({ status: 'RENOVATING' }) },
      { key: 'disabled', url: '/rooms', params: roomSummaryParams({ status: 'DISABLED' }) }
    ])
  ])
  if (!roomRequestGuard.isCurrent(requestId) || !roomListState.isCurrent(requestContext)) return

  if (totalResult.ok) roomMetrics.resolve(revision, 'total', totalResult.value)
  else roomMetrics.reject(revision, 'total')

  for (const key of ['available', 'rented']) {
    if (statusResults[key]?.ok) roomMetrics.resolve(revision, key, statusResults[key].value)
    else roomMetrics.reject(revision, key)
  }

  const unavailableParts = ['reserved', 'renovating', 'disabled'].map(key => statusResults[key])
  if (unavailableParts.every(result => result?.ok)) {
    roomMetrics.resolve(
      revision,
      'unavailable',
      unavailableParts.reduce((total, result) => total + Number(result.value || 0), 0)
    )
  } else {
    roomMetrics.reject(revision, 'unavailable')
  }
}

function handleSizeChange() {
  resetToFirstPage(pagination)
  loadRooms()
}

async function syncQueryLocation(buildingId, floorId) {
  query.buildingId = buildingId
  await loadQueryFloors(floorId)
}

async function openCreateDialog() {
  if (!query.buildingId) {
    ElMessage.warning('请先选择楼宇')
    return
  }
  resetForm()
  form.buildingId = query.buildingId
  await loadFormFloors(form.buildingId, query.floorId)
  dialogVisible.value = true
}

async function openEditDialog(row) {
  resetForm()
  Object.assign(form, {
    id: row.id,
    buildingId: row.buildingId || null,
    floorId: row.floorId || null,
    roomNumber: row.roomNumber || '',
    roomName: row.roomName || '',
    area: row.area || 0,
    propertyFeeArea: row.propertyFeeArea ?? row.area ?? 0,
    roomType: row.roomType || 'OFFICE',
    status: normalizedRoomStatus(row.status),
    description: row.description || ''
  })
  await loadFormFloors(form.buildingId, form.floorId)
  dialogVisible.value = true
}

async function saveRoom() {
  await formRef.value.validate()
  saving.value = true
  try {
    const payload = { ...form, buildingId: form.buildingId, floorId: form.floorId }
    if (form.id) {
      await request.put(`/rooms/${form.id}`, payload)
      ElMessage.success('更新成功')
    } else {
      await request.post('/rooms', payload)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    await syncQueryLocation(form.buildingId, form.floorId)
    await loadRooms()
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function deleteRoom(row) {
  try {
    await ElMessageBox.confirm(`确认软删除房间 ${row.roomNumber}？`, '提示', { type: 'warning' })
    await request.delete(`/rooms/${row.id}`)
    ElMessage.success('删除成功')
    await loadRooms()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error?.response?.data?.message || '删除失败')
    }
  }
}

watch(
  () => [props.buildingId, props.floorId],
  async ([buildingId, floorId], previous) => {
    if (!props.embedded || !pageActive || !previous) return
    const normalizedBuildingId = buildingId ? Number(buildingId) : null
    const normalizedFloorId = floorId ? Number(floorId) : null
    if (Number(query.buildingId || 0) === Number(normalizedBuildingId || 0)
      && Number(query.floorId || 0) === Number(normalizedFloorId || 0)) return
    query.buildingId = normalizedBuildingId
    await loadQueryFloors(normalizedFloorId)
    resetToFirstPage(pagination)
    await loadRooms()
  }
)

onMounted(async () => {
  if (!props.embedded || hasPermission('building:view') || hasPermission('building:*')) {
    await loadBuildings()
  }
  const requestedBuildingId = props.embedded && props.buildingId ? Number(props.buildingId) : null
  const hasRequestedBuilding = requestedBuildingId
    && buildings.value.some(item => Number(item.id) === requestedBuildingId)
  query.buildingId = hasRequestedBuilding ? requestedBuildingId : (props.embedded ? null : buildings.value[0]?.id || null)
  await loadQueryFloors(props.embedded && props.floorId ? Number(props.floorId) : null)
  await loadRooms()
})

onBeforeUnmount(() => {
  pageActive = false
  queryFloorRevision += 1
  disposeQueryLifecycle()
})
</script>

<style scoped>
.page-container {
  padding: 20px;
}

.page-container--embedded {
  padding: 0;
}

.location-selector {
  width: 170px;
}

.selector-label,
.view-hint {
  color: var(--dhic-text-muted);
  font-size: 12px;
  white-space: nowrap;
}

.selector-context {
  max-width: 280px;
  overflow: hidden;
  color: var(--dhic-text-secondary, #606266);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  min-height: 28px;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  padding-top: 12px;
}

@media (max-width: 1024px) {
  .location-selector {
    flex: 1 1 170px;
    width: auto;
  }

  .view-hint {
    flex-basis: 100%;
  }
}
</style>
