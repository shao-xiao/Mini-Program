<template>
  <div class="page-container">
    <DataWorkspace
      :workspace-config="contractWorkspaceConfig"
      :views="workspaceViews"
      :active-view-id="activeViewId"
      :view-dirty="isViewDirty"
      :filter-logic="tablePrefs.filterLogic"
      :filter-logic-visible="tablePrefs.filters.length > 1"
      :filter-items="activeFilterItems"
      :query-notices="queryNotices"
      :metric-data="contractMetricData"
      @change-view="switchContractView"
      @create-view="createTableView"
      @duplicate-view="duplicateTableView"
      @rename-view="renameTableView"
      @delete-view="deleteTableView"
      @save-view="saveContractView"
      @remove-filter="removeViewFilter"
    >
      <template #header-actions>
        <div class="header-actions">
          <el-button
            v-if="hasPermission(contractPermissions.create)"
            type="primary"
            :icon="Plus"
            @click="openCreateDialog"
          >
            {{ contractWorkspaceConfig.primaryAction.label }}
          </el-button>
        </div>
      </template>

      <template #toolbar>
        <DhicDataToolbar
          :workspace-config="contractWorkspaceConfig"
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
          @update:filters="updateFilters"
          @update:filter-logic="updateFilterLogic"
          @update:sort-key="updateSortKey"
          @update:sort-order="updateSortOrder"
          @update:sorts="updateSorts"
          @update:group-key="updateGroupKey"
          @update:groups="updateGroups"
          @update:row-density="updateRowDensity"
          @reset-view="resetTableView"
          @save-field-config="saveContractView"
        >
          <template #right>
            <span class="view-hint">
              全部已加载 {{ resultScope.loadedRecords }} 份，当前视图 {{ resultScope.visibleRecords }} 份
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
          :index-width="72"
          :density="tablePrefs.rowDensity"
          :empty-text="tableEmptyText"
          :sort-state="activeTableSort"
          :data-mode="contractWorkspaceConfig.query.dataMode"
          :column-width="columnWidth"
          :column-fixed="columnFixed"
          :is-column-sortable="isColumnSortable"
          :show-overflow-tooltip="column => ['tenantName', 'roomSummary'].includes(column.key)"
          :operation-column="{ width: 390, fixed: 'right', align: 'center' }"
          @column-resize="handleHeaderDragEnd"
          @sort-change="handleTableSortChange"
          @retry="loadContracts"
          aria-label="合同管理数据表格"
        >
          <template #operations="{ row }">
            <div class="action-buttons">
              <el-button v-if="hasPermission('contract:update')" size="small" @click="openEditDialog(row)">编辑</el-button>
              <el-button v-if="hasPermission('contract:update')" size="small" type="primary" plain @click="selectContractFile(row)">上传电子合同</el-button>
              <el-button size="small" :disabled="!hasContractFile(row)" @click="previewElectronicContract(row)">查看电子合同</el-button>
              <el-button v-if="hasPermission('contract:update')" size="small" :disabled="row.status !== 'DRAFT'" @click="submitContract(row)">提交审批</el-button>
              <el-button v-if="hasPermission('contract:approve')" size="small" type="primary" plain :disabled="!['PENDING', 'PENDING_APPROVAL'].includes(row.status)" @click="approveContract(row)">审批通过</el-button>
              <el-button v-if="hasPermission('contract:activate')" size="small" :disabled="row.status !== 'SUSPENDED'" @click="activate(row)">恢复生效</el-button>
              <el-button v-if="hasPermission('lease:checkin')" size="small" :disabled="row.status !== 'ACTIVE'" @click="checkIn(row)">入驻</el-button>
              <el-button v-if="hasPermission('contract:terminate')" size="small" type="danger" :disabled="!['ACTIVE', 'SUSPENDED'].includes(row.status)" @click="terminate(row)">终止</el-button>
              <el-button v-if="hasPermission('contract:cancel')" size="small" :disabled="!['DRAFT', 'PENDING', 'PENDING_APPROVAL'].includes(row.status)" @click="cancelContract(row)">作废</el-button>
            </div>
          </template>
        </WorkspaceDataGrid>
      </template>
    </DataWorkspace>

    <input
      ref="contractFileInputRef"
      type="file"
      accept="application/pdf"
      class="hidden-file-input"
      @change="handleContractFileChange"
    />

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑合同' : '新增合同'" width="760px" class="contract-dialog">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <div class="form-section-title">合同信息</div>
        <div class="form-grid">
          <el-form-item label="合同编号" prop="contractNumber">
            <el-input v-model="form.contractNumber" placeholder="不填则由后端自动生成" />
          </el-form-item>

        </div>

        <div class="form-section-title">租户信息</div>
        <div class="form-grid">
          <el-form-item label="租户" prop="tenantId">
            <el-select
              v-model="form.tenantId"
              filterable
              style="width:100%"
              placeholder="请选择已建档租户"
              @change="handleTenantChange"
            >
              <el-option
                v-for="tenant in tenants"
                :key="tenant.id"
                :label="tenant.tenantName || tenant.name || `租户 #${tenant.id}`"
                :value="tenant.id"
              />
            </el-select>
            <div class="form-tip">新租户请先在“租户管理”完成建档，合同只保存租户关联。</div>
          </el-form-item>

          <el-form-item label="联系人" prop="contactPerson">
            <el-input v-model="form.contactPerson" disabled />
          </el-form-item>

          <el-form-item label="联系电话" prop="contactPhone">
            <el-input v-model="form.contactPhone" disabled />
          </el-form-item>

          <el-form-item label="邮箱" prop="contactEmail">
            <el-input v-model="form.contactEmail" disabled />
          </el-form-item>
        </div>

        <div class="form-section-title">房间与周期</div>
        <div class="form-grid location-grid">
          <el-form-item label="楼宇" prop="buildingIds">
            <el-select
              v-model="form.buildingIds"
              multiple
              filterable
              collapse-tags
              collapse-tags-tooltip
              placeholder="请选择楼宇"
              style="width:100%"
              @change="handleBuildingIdsChange"
            >
              <el-option v-for="building in buildings" :key="building.id" :label="buildingLabel(building)" :value="building.id" />
            </el-select>
          </el-form-item>

          <el-form-item label="楼层" prop="floorIds">
            <el-select
              v-model="form.floorIds"
              multiple
              filterable
              collapse-tags
              collapse-tags-tooltip
              placeholder="请选择楼层"
              style="width:100%"
              @change="handleFloorIdsChange"
            >
              <el-option v-for="floor in availableFloors" :key="floor.id" :label="floorLabel(floor)" :value="floor.id" />
            </el-select>
          </el-form-item>

          <el-form-item label="房间" prop="roomIds">
            <el-select
              v-model="form.roomIds"
              multiple
              filterable
              collapse-tags
              collapse-tags-tooltip
              placeholder="请选择房间"
              style="width:100%"
            >
              <el-option v-for="room in availableRooms" :key="room.id" :label="roomLabel(room)" :value="room.id" />
            </el-select>
          </el-form-item>
        </div>

        <div class="form-grid period-grid">
          <el-form-item label="开始日期" prop="startDate">
            <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
          </el-form-item>

          <el-form-item label="结束日期" prop="endDate">
            <el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
          </el-form-item>
        </div>

        <div class="form-section-title">合同金额</div>
        <div class="form-grid fee-grid">
          <el-form-item label="月租金额" prop="rentAmount">
            <el-input-number v-model="form.rentAmount" :min="0" :precision="2" style="width:100%" />
          </el-form-item>

          <el-form-item label="押金" prop="depositAmount">
            <el-input-number v-model="form.depositAmount" :min="0" :precision="2" style="width:100%" />
          </el-form-item>

        </div>

        <el-form-item label="备注" prop="remark" class="remark-item">
          <el-input v-model="form.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button v-if="form.id ? hasPermission('contract:update') : hasPermission('contract:create')" type="primary" @click="saveContract">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import DataWorkspace from '../../components/DataWorkspace.vue'
import DhicDataToolbar from '../../components/DhicDataToolbar.vue'
import WorkspaceDataGrid from '../../components/workspace/WorkspaceDataGrid.vue'
import { useDhicTableView } from '../../composables/useDhicTableView'
import { useWorkspaceListRequestState } from '../../composables/workspace/useWorkspaceRequestState'
import request from '../../utils/request'
import { downloadBlob } from '../../utils/downloadBlob'
import { hasPermission } from '../../utils/permission'
import { readPage } from '../../utils/pagination'
import {
  buildContractMetricSnapshot,
  contractStatusOptions,
  contractWorkspaceConfig,
  normalizeContractWorkspaceRecord
} from '../../workspaceConfigs/contractWorkspaceConfig'
import { workspaceTableOptions } from '../../workspaceConfigs/workspaceConfig'

const contracts = ref([])
const dialogVisible = ref(false)
const formRef = ref()
const buildings = ref([])
const tenants = ref([])
const rooms = ref([])
const floors = ref([])
const contractFileInputRef = ref(null)
const currentContractFile = ref(null)
const contractPermissions = contractWorkspaceConfig.permissions
const listState = useWorkspaceListRequestState({
  hasResults: () => contracts.value.length > 0,
  clearResults: () => {
    contracts.value = []
  },
  staleMessage: '合同列表刷新失败，当前显示上次成功数据'
})
const loading = computed(() => listState.status.value === 'loading')
const listErrorText = listState.errorText

const {
  workspaceViews,
  activeViewId,
  activeViewName,
  isViewDirty,
  tablePrefs,
  orderedColumns,
  visibleOrderedColumns,
  visibleRecords,
  tableRows,
  tableEmptyText,
  sortOptions,
  groupOptions,
  filterOptions,
  filterLogicOptions,
  queryNotices,
  queryRequestGuard: listRequestGuard,
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
} = useDhicTableView(workspaceTableOptions(contractWorkspaceConfig, {
  records: () => contracts.value,
  serverTotal: () => contracts.value.length,
  groupLabel: value => contractStatusOptions.find(option => option.value === value)?.label || value || '未填写'
}))

const contractMetricData = computed(() => buildContractMetricSnapshot(contracts.value, visibleRecords.value))

const form = reactive({
  id: null,
  version: null,
  contractNumber: '',
  tenantId: '',
  tenantName: '',
  contactPerson: '',
  contactPhone: '',
  contactEmail: '',
  buildingIds: [],
  floorIds: [],
  roomIds: [],
  leaseId: '',
  startDate: '',
  endDate: '',
  rentAmount: 0,
  depositAmount: 0,
  remark: '',
  status: 'DRAFT'
})

const rules = {
  tenantId: [{ required: true, message: '请选择租户', trigger: 'change' }],
  buildingIds: [{ type: 'array', required: true, message: '请选择楼宇', trigger: 'change' }],
  roomIds: [{ type: 'array', required: true, message: '请选择房间', trigger: 'change' }]
}

const availableFloors = computed(() => {
  const selectedBuildingIds = form.buildingIds.map(Number)
  return floors.value.filter(item => {
    return selectedBuildingIds.length ? selectedBuildingIds.includes(Number(item.buildingId)) : true
  })
})

const availableRooms = computed(() => {
  const selectedBuildingIds = form.buildingIds.map(Number)
  const selectedFloorIds = form.floorIds.map(Number)
  const selectedRoomIds = form.roomIds.map(Number)
  return rooms.value.filter(item => {
    const sameBuilding = selectedBuildingIds.length ? selectedBuildingIds.includes(Number(item.buildingId)) : true
    const sameFloor = selectedFloorIds.length ? selectedFloorIds.includes(Number(item.floorId)) : true
    const selectableStatus = ['AVAILABLE', 'VACANT'].includes(item.status) || selectedRoomIds.includes(Number(item.id))
    return sameBuilding && sameFloor && selectableStatus
  })
})

function resetForm() {
  Object.assign(form, {
    id: null,
    version: null,
    contractNumber: '',
    tenantId: '',
    tenantName: '',
    contactPerson: '',
    contactPhone: '',
    contactEmail: '',
    buildingIds: [],
    floorIds: [],
    roomIds: [],
    leaseId: '',
    startDate: '',
    endDate: '',
    rentAmount: 0,
    depositAmount: 0,
    remark: '',
    status: 'DRAFT'
  })
  formRef.value?.clearValidate()
}

async function switchContractView(viewId) {
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

function saveContractView() {
  saveCurrentView()
  ElMessage.success(`视图“${activeViewName.value}”已保存`)
}

async function loadContracts() {
  const requestId = listRequestGuard.start()
  const requestContext = listState.begin({ resource: '/contracts' })
  try {
    const data = await request.get('/contracts', { silent: true })
    if (!listRequestGuard.isCurrent(requestId) || !listState.isCurrent(requestContext)) return
    contracts.value = readPage(data).records.map(normalizeContractWorkspaceRecord)
    listState.succeed(requestContext)
  } catch (error) {
    if (!listRequestGuard.isCurrent(requestId) || !listState.isCurrent(requestContext)) return
    listState.fail(requestContext, error?.response?.data?.message || '合同列表加载失败，请稍后重试')
  }
}

async function loadTenants() {
  const data = await request.get('/tenant/list')
  tenants.value = readPage(data).records
}

async function loadRooms() {
  const buildingsData = await request.get('/buildings')
  buildings.value = readPage(buildingsData).records

  const loadedFloors = []
  const loadedRooms = []
  for (const building of buildings.value) {
    const floorsData = await request.get(`/buildings/${building.id}/floors`)
    const buildingFloors = readPage(floorsData).records.map(floor => ({
      ...floor,
      buildingId: floor.buildingId || building.id,
      buildingName: building.buildingName
    }))
    loadedFloors.push(...buildingFloors)

    for (const floor of buildingFloors) {
      const data = await request.get(`/buildings/${building.id}/floors/${floor.id}/rooms`)
      loadedRooms.push(...readPage(data).records.map(room => ({
        ...room,
        buildingId: room.buildingId || building.id,
        buildingName: building.buildingName,
        floorId: room.floorId || floor.id,
        floorName: floor.floorName,
        floorNumber: floor.floorNumber
      })))
    }
  }
  floors.value = loadedFloors
  rooms.value = loadedRooms
}

function openCreateDialog() {
  resetForm()
  dialogVisible.value = true
}

function openEditDialog(row) {
  resetForm()
  const rowRoomIds = Array.isArray(row.roomIds) && row.roomIds.length ? row.roomIds : (row.roomId ? [row.roomId] : [])
  const selectedRooms = rooms.value.filter(item => rowRoomIds.map(Number).includes(Number(item.id)))
  const rowBuildingIds = [...new Set(selectedRooms.map(item => item.buildingId).filter(Boolean))]
  const rowFloorIds = Array.isArray(row.floorIds) && row.floorIds.length
    ? row.floorIds
    : [...new Set(selectedRooms.map(item => item.floorId).filter(Boolean))]
  Object.assign(form, {
    id: row.id,
    version: row.version ?? null,
    contractNumber: row.contractNumber || row.contractNo || row.code || '',
    tenantId: row.tenantId || '',
    tenantName: row.tenantName || tenantDisplayName(row.tenantId),
    contactPerson: row.contactPerson || '',
    contactPhone: row.contactPhone || '',
    contactEmail: row.contactEmail || '',
    buildingIds: rowBuildingIds.map(Number),
    floorIds: rowFloorIds.map(Number),
    roomIds: rowRoomIds.map(Number),
    startDate: row.startDate || '',
    endDate: row.endDate || '',
    rentAmount: Number(row.rentAmount || 0),
    depositAmount: Number(row.depositAmount || 0),
    remark: row.remark || '',
    status: row.status || 'DRAFT'
  })
  dialogVisible.value = true
}

function handleBuildingIdsChange() {
  const selectedBuildingIds = form.buildingIds.map(Number)
  if (!selectedBuildingIds.length) {
    form.floorIds = []
    form.roomIds = []
    return
  }
  form.floorIds = form.floorIds.filter(floorId => {
    const floor = floors.value.find(item => Number(item.id) === Number(floorId))
    return floor && selectedBuildingIds.includes(Number(floor.buildingId))
  })
  form.roomIds = form.roomIds.filter(roomId => {
    const room = rooms.value.find(item => Number(item.id) === Number(roomId))
    return room && selectedBuildingIds.includes(Number(room.buildingId))
  })
}

function handleFloorIdsChange() {
  const selectedFloorIds = form.floorIds.map(Number)
  if (!selectedFloorIds.length) return
  form.roomIds = form.roomIds.filter(roomId => {
    const room = rooms.value.find(item => Number(item.id) === Number(roomId))
    return room && selectedFloorIds.includes(Number(room.floorId))
  })
}

function tenantDisplayName(tenantId) {
  const tenant = tenants.value.find(item => Number(item.id) === Number(tenantId))
  return tenant ? tenant.tenantName : `租户ID:${tenantId || '-'}`
}

function handleTenantChange(tenantId) {
  const tenant = tenants.value.find(item => Number(item.id) === Number(tenantId))
  form.tenantName = tenant?.tenantName || tenant?.name || ''
  form.contactPerson = tenant?.contactPerson || ''
  form.contactPhone = tenant?.contactPhone || ''
  form.contactEmail = tenant?.contactEmail || ''
}

function buildingLabel(building) {
  return building.buildingName || building.name || building.buildingCode || `楼宇ID:${building.id}`
}

function floorLabel(floor) {
  return floor.floorName || floor.floorNumber || `${floor.floorNo || floor.id}层`
}

function roomLabel(room) {
  const areaText = room.area ? `｜${room.area}㎡` : ''
  const roomText = room.roomNumber || room.roomName || '未命名房间'
  return `${roomText}${areaText}`
}

function roomDisplayName(roomId) {
  if (!roomId) return '-'
  const room = rooms.value.find(item => Number(item.id) === Number(roomId))
  if (!room) return `房间ID:${roomId}`
  const areaText = room.area ? ` / ${room.area}㎡` : ''
  return `${room.roomNumber || room.roomName || '未命名房间'}${areaText}`
}

function payload() {
  const requestBody = {
    contractNo: form.contractNumber.trim() || undefined,
    tenantId: Number(form.tenantId),
    roomIds: form.roomIds.map(Number),
    startDate: form.startDate,
    endDate: form.endDate,
    effectiveDate: form.startDate,
    currency: 'CNY',
    rentAmount: form.rentAmount,
    depositAmount: form.depositAmount,
    remark: form.remark || undefined
  }
  if (form.id) {
    requestBody.version = Number(form.version)
    requestBody.changeReason = '后台合同编辑'
  }
  return requestBody
}

async function saveContract() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  if (form.id) {
    await request.put(`/contracts/${form.id}`, payload())
    ElMessage.success('保存成功')
  } else {
    await request.post('/contracts', payload())
    ElMessage.success('创建成功')
  }

  dialogVisible.value = false
  resetForm()
  await Promise.all([loadContracts(), loadTenants(), loadRooms()])
}

async function submitContract(row) {
  await request.post(`/contracts/${row.id}/submit`, {
    version: row.version,
    reason: '后台合同列表提交审批'
  })
  ElMessage.success('合同已提交审批')
  await loadContracts()
}

async function approveContract(row) {
  await request.post(`/contracts/${row.id}/approve`, {
    version: row.version,
    reason: '后台合同列表审批通过'
  })
  ElMessage.success('合同审批通过')
  await loadContracts()
}

async function activate(row) {
  await request.post(`/contracts/${row.id}/activate`, {
    version: row.version,
    reason: '后台合同列表恢复生效'
  })
  ElMessage.success('合同已恢复生效')
  await loadContracts()
}

async function checkIn(row) {
  await request.post(`/contracts/${row.id}/check-in`, { remark: '后台合同列表办理入驻' })
  ElMessage.success('入驻办理成功')
  await Promise.all([loadContracts(), loadRooms()])
}

async function terminate(row) {
  await request.post(`/contracts/${row.id}/terminate`, {
    version: row.version,
    reason: '后台终止合同'
  })
  ElMessage.success('已终止')
  await Promise.all([loadContracts(), loadRooms()])
}

async function cancelContract(row) {
  await request.post(`/contracts/${row.id}/cancel`, {
    version: row.version,
    reason: '后台作废合同'
  })
  ElMessage.success('已作废')
  await Promise.all([loadContracts(), loadRooms()])
}

function hasContractFile(row) {
  return row.hasContractFile === true || row.hasContractFile === 1 || Boolean(row.contractFileName)
}

function selectContractFile(row) {
  currentContractFile.value = row
  if (contractFileInputRef.value) {
    contractFileInputRef.value.value = ''
    contractFileInputRef.value.click()
  }
}

async function handleContractFileChange(event) {
  const file = event.target.files?.[0]
  const row = currentContractFile.value
  if (!file || !row) return

  if (file.type !== 'application/pdf' && !file.name.toLowerCase().endsWith('.pdf')) {
    ElMessage.error('只能上传 PDF 电子合同')
    return
  }

  const formData = new FormData()
  formData.append('file', file)
  try {
    await request.post(`/contracts/${row.id}/file`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    ElMessage.success('电子合同已上传')
    await loadContracts()
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || '电子合同上传失败')
  } finally {
    currentContractFile.value = null
    if (contractFileInputRef.value) contractFileInputRef.value.value = ''
  }
}

async function previewElectronicContract(row) {
  if (!hasContractFile(row)) {
    ElMessage.warning('请先上传电子合同')
    return
  }

  const previewWindow = window.open('', '_blank')
  try {
    const blob = await request.get(`/contracts/${row.id}/file/download`, {
      responseType: 'blob'
    })
    downloadBlob(blob, row.contractFileName || `${row.contractNumber || row.contractNo || row.code || '电子合同'}.pdf`, {
      previewWindow,
      download: false
    })
  } catch (error) {
    if (previewWindow) previewWindow.close()
    ElMessage.error(error?.response?.data?.message || error?.message || '电子合同打开失败')
  }
}

onMounted(async () => {
  await Promise.all([loadContracts(), loadTenants(), loadRooms()])
})
onBeforeUnmount(disposeQueryLifecycle)
</script>

<style scoped>
.page-container {
  padding: 20px;
}

.header-actions {
  display: flex;
  gap: 10px;
}

.view-hint {
  color: var(--dhic-text-muted);
  font-size: 12px;
  white-space: nowrap;
}

.hidden-file-input {
  display: none;
}

.action-buttons {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 6px;
}

.action-buttons :deep(.el-button + .el-button) {
  margin-left: 0;
}

.contract-dialog :deep(.el-dialog__body) {
  padding: 12px 28px 6px;
  max-height: min(72vh, 760px);
  overflow-y: auto;
}

.contract-dialog :deep(.el-dialog__footer) {
  padding: 14px 28px 22px;
  border-top: 1px solid #eef0f4;
}

.form-section-title {
  margin: 18px 0 12px;
  padding-left: 10px;
  border-left: 3px solid #d93025;
  color: #303133;
  font-size: 15px;
  font-weight: 600;
  line-height: 18px;
}

.form-section-title:first-child {
  margin-top: 4px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px 18px;
  align-items: start;
}

.location-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.period-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.fee-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.form-grid :deep(.el-form-item) {
  margin-bottom: 0;
}

.form-grid :deep(.el-form-item__label) {
  padding-bottom: 6px;
  color: #606266;
  font-weight: 500;
}

.form-tip {
  width: 100%;
  margin-top: 6px;
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
}

.remark-item {
  margin-top: 14px;
}

@media (max-width: 900px) {
  .location-grid,
  .fee-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 680px) {
  .form-grid,
  .location-grid,
  .period-grid,
  .fee-grid {
    grid-template-columns: 1fr;
  }
}
</style>
