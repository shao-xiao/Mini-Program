<template>
  <div class="page-container">
    <DataWorkspace
      :workspace-config="leaseWorkspaceConfig"
      :views="workspaceViews"
      :active-view-id="activeViewId"
      :view-dirty="isViewDirty"
      :filter-logic="tablePrefs.filterLogic"
      :filter-logic-visible="tablePrefs.filters.length > 1"
      :filter-items="activeFilterItems"
      :query-notices="queryNotices"
      :metric-data="leaseMetricData"
      @change-view="switchLeaseView"
      @create-view="createTableView"
      @duplicate-view="duplicateTableView"
      @rename-view="renameTableView"
      @delete-view="deleteTableView"
      @save-view="saveLeaseView"
      @remove-filter="removeViewFilter"
    >
      <template #header-actions>
        <el-button
          v-if="hasPermission('lease:create')"
          type="primary"
          :icon="Plus"
          @click="openCreateDialog"
        >
          {{ leaseWorkspaceConfig.primaryAction.label }}
        </el-button>
      </template>

      <template #toolbar>
        <DhicDataToolbar
          :workspace-config="leaseWorkspaceConfig"
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
          @save-field-config="saveLeaseView"
        >
          <template #right>
            <el-button :icon="Refresh" @click="loadWorkspaceData">刷新</el-button>
            <span class="view-hint">当前页 {{ resultScope.loadedRecords }} 条 / 共 {{ resultScope.serverTotal }} 条</span>
          </template>
        </DhicDataToolbar>
      </template>

      <template #grid>
        <WorkspaceDataGrid
          :loading="loading"
          :error-text="loadError"
          retryable
          :rows="tableRows"
          :columns="visibleOrderedColumns"
          row-key="id"
          :current-page="pagination.currentPage"
          :page-size="pagination.pageSize"
          :density="tablePrefs.rowDensity"
          :empty-text="tableEmptyText"
          :sort-state="activeTableSort"
          :data-mode="leaseWorkspaceConfig.query.dataMode"
          :column-width="columnWidth"
          :column-fixed="columnFixed"
          :is-column-sortable="isColumnSortable"
          :show-overflow-tooltip="column => ['roomNames'].includes(column.key)"
          :operation-column="{ width: 188, fixed: 'right', align: 'left' }"
          @column-resize="handleHeaderDragEnd"
          @sort-change="handleTableSortChange"
          @retry="loadWorkspaceData"
          aria-label="租约履约数据表格"
        >
          <template #cell-roomNames="{ row }">{{ roomSummary(row) }}</template>
          <template #operations="{ row }">
            <div class="action-buttons">
              <el-button
                v-if="hasPermission('lease:checkin')"
                size="small"
                :disabled="!canCheckIn(row)"
                @click="checkin(row)"
              >
                办理入住
              </el-button>
              <el-button
                v-if="hasPermission('lease:checkout')"
                size="small"
                type="danger"
                plain
                :disabled="!canCheckOut(row)"
                @click="checkout(row)"
              >
                办理退租
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
            @current-change="loadLeases"
            @size-change="handleSizeChange"
          />
        </div>
      </template>
    </DataWorkspace>

    <el-dialog
      v-model="createDialogVisible"
      title="新建租约"
      width="620px"
      destroy-on-close
      @closed="resetCreateForm"
    >
      <el-alert
        class="dialog-tip"
        title="租约必须基于已存在的合同创建；租户和房间均以后端合同数据为准。"
        type="info"
        show-icon
        :closable="false"
      />
      <el-form
        ref="createFormRef"
        v-loading="contractLoading"
        :model="createForm"
        :rules="createRules"
        label-position="top"
      >
        <el-form-item label="合同" prop="contractId">
          <el-select
            v-model="createForm.contractId"
            filterable
            class="full-width"
            placeholder="选择待建立租约的合同"
            @change="handleContractChange"
          >
            <el-option
              v-for="contract in pendingContracts"
              :key="contract.id"
              :label="contractLabel(contract)"
              :value="contract.id"
            />
          </el-select>
        </el-form-item>

        <div class="form-grid">
          <el-form-item label="计划开始日期" prop="plannedStartDate">
            <el-date-picker
              v-model="createForm.plannedStartDate"
              type="date"
              value-format="YYYY-MM-DD"
              class="full-width"
            />
          </el-form-item>
          <el-form-item label="计划结束日期" prop="plannedEndDate">
            <el-date-picker
              v-model="createForm.plannedEndDate"
              type="date"
              value-format="YYYY-MM-DD"
              class="full-width"
            />
          </el-form-item>
        </div>

        <el-form-item label="合同房间" prop="roomIds">
          <div v-if="selectedContractRooms.length" class="room-chip-list">
            <el-tag v-for="room in selectedContractRooms" :key="room.id" effect="plain">
              {{ room.name }}
            </el-tag>
          </div>
          <el-empty v-else :image-size="56" description="所选合同暂无可用房间" />
        </el-form-item>

        <el-form-item label="备注">
          <el-input
            v-model="createForm.remark"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="可填写履约说明"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button
          v-if="hasPermission(leasePermissions.create)"
          type="primary"
          :loading="saving"
          @click="createLease"
        >
          创建租约
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="lifecycleDialogVisible"
      :title="lifecycleMode === 'checkin' ? '办理入住' : '办理退租'"
      width="560px"
      destroy-on-close
    >
      <el-alert
        class="dialog-tip"
        :title="`${lifecycleLease?.leaseNo || '当前租约'}：请选择本次处理的房间`"
        :type="lifecycleMode === 'checkin' ? 'info' : 'warning'"
        show-icon
        :closable="false"
      />
      <el-form :model="lifecycleForm" label-position="top">
        <el-form-item v-if="lifecycleRoomStatusesKnown" label="处理房间" required>
          <el-checkbox-group v-model="lifecycleForm.roomIds" class="room-checkbox-list">
            <el-checkbox
              v-for="room in lifecycleRooms"
              :key="room.id"
              :value="room.id"
            >
              {{ room.name }}
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-alert
          v-else
          class="dialog-tip"
          title="当前响应未包含房间级履约状态，本次将由后端选择全部可办理房间。"
          type="warning"
          show-icon
          :closable="false"
        />
        <el-form-item label="发生时间">
          <el-date-picker
            v-model="lifecycleForm.occurredAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            class="full-width"
            placeholder="不填则以后端接收时间为准"
          />
        </el-form-item>
        <el-form-item :label="lifecycleMode === 'checkin' ? '入住说明' : '退租原因'">
          <el-input
            v-model="lifecycleForm.reason"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="lifecycleDialogVisible = false">取消</el-button>
        <el-button
          :type="lifecycleMode === 'checkin' ? 'primary' : 'danger'"
          :loading="saving"
          @click="submitLifecycleAction"
        >
          确认{{ lifecycleMode === 'checkin' ? '入住' : '退租' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import DataWorkspace from '../../components/DataWorkspace.vue'
import DhicDataToolbar from '../../components/DhicDataToolbar.vue'
import WorkspaceDataGrid from '../../components/workspace/WorkspaceDataGrid.vue'
import { useDhicTableView } from '../../composables/useDhicTableView'
import request from '../../utils/request'
import { hasPermission } from '../../utils/permission'
import { createPagination, pageParams, readPage, resetToFirstPage } from '../../utils/pagination'
import { createLatestRequestGuard } from '../../utils/requestLifecycle'
import { createLeaseWorkspaceConfig, leaseStatusOptions } from '../../workspaceConfigs/leasingWorkspaceConfig'
import { workspaceTableOptions } from '../../workspaceConfigs/workspaceConfig'

const statusOptions = leaseStatusOptions
const tenantOptions = reactive([])
const leaseWorkspaceConfig = createLeaseWorkspaceConfig({ tenantOptions })
const leasePermissions = leaseWorkspaceConfig.permissions

const leases = ref([])
const loading = ref(false)
const saving = ref(false)
const loadError = ref('')
const pagination = reactive(createPagination(20))
const summaryRequestGuard = createLatestRequestGuard()

const summary = reactive(emptySummary())
const leaseMetricData = computed(() => ({
  ...summary,
  activeTotal: summary.active + summary.partiallyActive
}))

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
  queryRequestGuard: listRequestGuard,
  debouncedQueryTask: debouncedLeaseQuery,
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
} = useDhicTableView(workspaceTableOptions(leaseWorkspaceConfig, {
  records: () => leases.value,
  serverTotal: () => pagination.total,
  queryLifecycle: {
    task: () => {
      resetToFirstPage(pagination)
      loadWorkspaceData()
    }
  },
  groupLabel: value => statusOptions.find(option => option.value === value)?.label || value || '未填写'
}))

watch(() => JSON.stringify(serverQueryParams.value), () => debouncedLeaseQuery.schedule())

const createDialogVisible = ref(false)
const createFormRef = ref()
const contractLoading = ref(false)
const pendingContracts = ref([])
const createForm = reactive(defaultCreateForm())
const createRules = {
  contractId: [{ required: true, message: '请选择合同', trigger: 'change' }],
  plannedStartDate: [{ required: true, message: '请选择计划开始日期', trigger: 'change' }],
  plannedEndDate: [{ required: true, message: '请选择计划结束日期', trigger: 'change' }],
  roomIds: [{ type: 'array', required: true, min: 1, message: '合同至少需要一个房间', trigger: 'change' }]
}

const selectedContractRooms = computed(() => {
  const contract = selectedContract.value
  if (!contract) return []
  return roomOptions(contract.roomIds, contract.roomNames)
})

const selectedContract = computed(() => pendingContracts.value.find(
  contract => Number(contract.id) === Number(createForm.contractId)
))

const lifecycleDialogVisible = ref(false)
const lifecycleMode = ref('checkin')
const lifecycleLease = ref(null)
const lifecycleForm = reactive({ roomIds: [], occurredAt: '', reason: '' })
const lifecycleRoomStatusesKnown = computed(() => {
  const roomIds = lifecycleLease.value?.roomIds
  const statuses = lifecycleLease.value?.roomStatuses
  return Array.isArray(roomIds)
    && roomIds.length > 0
    && Array.isArray(statuses)
    && statuses.length === roomIds.length
})
const lifecycleRooms = computed(() => {
  if (!lifecycleRoomStatusesKnown.value) return []
  const eligibleStatus = lifecycleMode.value === 'checkin' ? 'PENDING_CHECKIN' : 'ACTIVE'
  return roomOptions(
    lifecycleLease.value?.roomIds,
    lifecycleLease.value?.roomNames,
    lifecycleLease.value?.roomStatuses
  ).filter(room => room.status === eligibleStatus)
})

function emptySummary() {
  return {
    total: 0,
    pendingCheckin: 0,
    partiallyActive: 0,
    active: 0,
    partiallyTerminated: 0,
    terminated: 0
  }
}

function defaultCreateForm() {
  return {
    contractId: null,
    plannedStartDate: '',
    plannedEndDate: '',
    roomIds: [],
    remark: ''
  }
}

function numberValue(value, fallback = 0) {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : Number(fallback || 0)
}

function roomOptions(ids, names, statuses = []) {
  const roomIds = Array.isArray(ids) ? ids : []
  const roomNames = Array.isArray(names) ? names : []
  const roomStatuses = Array.isArray(statuses) ? statuses : []
  return roomIds.map((id, index) => ({
    id: Number(id),
    name: roomNames[index] || `房间 #${id}`,
    status: roomStatuses[index]
  }))
}

function roomSummary(row) {
  const names = Array.isArray(row.roomNames) ? row.roomNames.filter(Boolean) : []
  if (names.length) return names.join('、')
  const ids = Array.isArray(row.roomIds) ? row.roomIds : []
  return ids.length ? ids.map(id => `房间 #${id}`).join('、') : '-'
}

function canCheckIn(row) {
  return ['PENDING_CHECKIN', 'PARTIALLY_ACTIVE', 'PARTIALLY_TERMINATED'].includes(row.status)
}

function canCheckOut(row) {
  return ['ACTIVE', 'PARTIALLY_ACTIVE', 'PARTIALLY_TERMINATED'].includes(row.status)
    && numberValue(row.activeRoomCount, 0) > 0
}

function requestErrorMessage(error, fallback) {
  return error?.response?.data?.message || error?.message || error?.msg || fallback
}

async function switchLeaseView(viewId) {
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

function saveLeaseView() {
  saveCurrentView()
  ElMessage.success(`视图“${activeViewName.value}”已保存`)
}

function summaryFilterParams() {
  const params = serverQueryParams.value
  return {
    keyword: params.keyword,
    status: params.status,
    tenantId: params.tenantId,
    contractId: params.contractId
  }
}

async function loadLeases() {
  const requestId = listRequestGuard.start()
  loading.value = true
  loadError.value = ''
  try {
    const data = await request.get('/leases/search', {
      params: {
        ...serverQueryParams.value,
        ...pageParams(pagination),
      },
      silent: true
    })
    if (!listRequestGuard.isCurrent(requestId)) return

    const page = readPage(data)
    leases.value = page.records
    pagination.total = page.total
  } catch (error) {
    if (!listRequestGuard.isCurrent(requestId)) return
    leases.value = []
    pagination.total = 0
    loadError.value = requestErrorMessage(error, '租约列表加载失败')
  } finally {
    if (listRequestGuard.isCurrent(requestId)) loading.value = false
  }
}

async function loadSummary() {
  const requestId = summaryRequestGuard.start()
  try {
    const data = await request.get('/leases/summary', {
      params: summaryFilterParams(),
      silent: true
    })
    if (!summaryRequestGuard.isCurrent(requestId)) return
    Object.assign(summary, emptySummary(), data || {})
  } catch {
    if (summaryRequestGuard.isCurrent(requestId)) Object.assign(summary, emptySummary())
  }
}

async function loadWorkspaceData() {
  await Promise.all([loadLeases(), loadSummary()])
}

async function searchLeases() {
  resetToFirstPage(pagination)
  await loadWorkspaceData()
}

async function handleSizeChange() {
  resetToFirstPage(pagination)
  await loadLeases()
}

function readRecords(data) {
  if (Array.isArray(data)) return data
  return readPage(data).records
}

async function loadPendingContracts() {
  contractLoading.value = true
  try {
    const data = await request.get('/contracts/pending-checkin', { silent: true })
    pendingContracts.value = readRecords(data)
  } catch (error) {
    pendingContracts.value = []
    ElMessage.error(requestErrorMessage(error, '待建立租约的合同加载失败'))
  } finally {
    contractLoading.value = false
  }
}

async function loadTenantOptions() {
  try {
    const data = await request.get('/tenant/list', { silent: true })
    tenantOptions.splice(0, tenantOptions.length, ...readRecords(data).map(tenant => ({
      label: tenant.tenantName || tenant.name || `租户 #${tenant.id}`,
      value: tenant.id
    })))
  } catch {
    tenantOptions.splice(0)
  }
}

function contractLabel(contract) {
  const contractNo = contract.contractNo || contract.contractNumber || `合同 #${contract.id}`
  const tenantName = contract.tenantName || `租户 #${contract.tenantId}`
  return `${contractNo} · ${tenantName}`
}

function handleContractChange(contractId) {
  const contract = pendingContracts.value.find(item => Number(item.id) === Number(contractId))
  createForm.plannedStartDate = contract?.plannedStartDate || contract?.startDate || ''
  createForm.plannedEndDate = contract?.plannedEndDate || contract?.endDate || ''
  createForm.roomIds = Array.isArray(contract?.roomIds)
    ? contract.roomIds.map(Number)
    : contract?.roomId
      ? [Number(contract.roomId)]
      : []
}

async function openCreateDialog() {
  resetCreateForm()
  createDialogVisible.value = true
  await loadPendingContracts()
}

function resetCreateForm() {
  Object.assign(createForm, defaultCreateForm())
  createFormRef.value?.clearValidate()
}

async function createLease() {
  const valid = await createFormRef.value?.validate().catch(() => false)
  if (!valid) return
  if (createForm.plannedEndDate < createForm.plannedStartDate) {
    ElMessage.warning('计划结束日期不能早于开始日期')
    return
  }

  saving.value = true
  try {
    await request.post('/leases', {
      contractId: Number(createForm.contractId),
      roomIds: createForm.roomIds.map(Number),
      plannedStartDate: createForm.plannedStartDate,
      plannedEndDate: createForm.plannedEndDate,
      remark: createForm.remark.trim() || undefined
    })
    ElMessage.success('租约创建成功')
    createDialogVisible.value = false
    await searchLeases()
  } finally {
    saving.value = false
  }
}

function openLifecycleDialog(mode, row) {
  lifecycleMode.value = mode
  lifecycleLease.value = row
  lifecycleForm.roomIds = []
  lifecycleForm.occurredAt = ''
  lifecycleForm.reason = ''
  lifecycleDialogVisible.value = true
  lifecycleForm.roomIds = lifecycleRooms.value.map(room => room.id)
}

function checkin(row) {
  openLifecycleDialog('checkin', row)
}

function checkout(row) {
  openLifecycleDialog('checkout', row)
}

async function submitLifecycleAction() {
  if (!lifecycleLease.value) return
  if (lifecycleRoomStatusesKnown.value && lifecycleForm.roomIds.length === 0) {
    ElMessage.warning('请至少选择一个房间')
    return
  }

  const payload = {
    roomIds: lifecycleRoomStatusesKnown.value
      ? lifecycleForm.roomIds.map(Number)
      : undefined,
    occurredAt: lifecycleForm.occurredAt || undefined,
    reason: lifecycleForm.reason.trim() || undefined,
    version: lifecycleLease.value.version
  }

  saving.value = true
  try {
    if (lifecycleMode.value === 'checkin') {
      await request.post(`/leases/${lifecycleLease.value.id}/check-in`, payload)
      ElMessage.success('入住办理成功')
    } else {
      await request.post(`/leases/${lifecycleLease.value.id}/check-out`, payload)
      ElMessage.success('退租办理成功')
    }
    lifecycleDialogVisible.value = false
    await loadWorkspaceData()
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  Promise.all([loadWorkspaceData(), loadTenantOptions()])
})

onBeforeUnmount(() => {
  disposeQueryLifecycle()
  summaryRequestGuard.dispose()
})
</script>

<style scoped>
.page-container {
  padding: 20px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.page-title {
  margin: 0;
  color: #303133;
  font-size: 18px;
  font-weight: 600;
  line-height: 1.4;
}

.page-subtitle {
  margin: 4px 0 0;
  color: #909399;
  font-size: 13px;
  line-height: 1.4;
}

.filter-form {
  margin-bottom: 4px;
}

.keyword-input {
  width: 280px;
}

.status-select {
  width: 160px;
}

.load-alert,
.dialog-tip {
  margin-bottom: 16px;
}

.lease-table {
  width: 100%;
  font-size: 14px;
}

.primary-text {
  color: #303133;
  font-weight: 600;
  line-height: 22px;
  word-break: break-word;
}

.secondary-text {
  margin-top: 3px;
  color: #7b8494;
  font-size: 13px;
  line-height: 20px;
  word-break: break-word;
}

.room-summary {
  max-width: 100%;
  overflow: hidden;
  color: #303133;
  line-height: 22px;
  text-overflow: ellipsis;
  white-space: nowrap;
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

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
  overflow-x: auto;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.full-width {
  width: 100%;
}

.room-chip-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  min-height: 32px;
}

.room-checkbox-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 16px;
  width: 100%;
}

.room-checkbox-list :deep(.el-checkbox) {
  margin-right: 0;
}

@media (max-width: 768px) {
  .page-container {
    padding: 12px;
  }

  .card-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .keyword-input,
  .status-select {
    width: 100%;
  }

  .filter-form :deep(.el-form-item) {
    display: flex;
    margin-right: 0;
    width: 100%;
  }

  .filter-form :deep(.el-form-item__content) {
    flex: 1;
  }

  .form-grid,
  .room-checkbox-list {
    grid-template-columns: 1fr;
  }
}

@media (prefers-reduced-motion: reduce) {
  .lease-table :deep(*) {
    scroll-behavior: auto;
  }
}
</style>
