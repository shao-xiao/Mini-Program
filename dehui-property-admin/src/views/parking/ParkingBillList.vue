<template>
  <div class="page-container">
    <DataWorkspace
      :workspace-config="parkingBillWorkspaceConfig"
      :views="workspaceViews"
      :active-view-id="activeViewId"
      :view-dirty="isViewDirty"
      :filter-logic="tablePrefs.filterLogic"
      :filter-logic-visible="tablePrefs.filters.length > 1"
      :filter-items="activeFilterItems"
      :query-notices="queryNotices"
      :metric-data="parkingBillMetricData"
      :metric-states="parkingBillMetricStates"
      @change-view="switchParkingBillView"
      @create-view="createTableView"
      @duplicate-view="duplicateTableView"
      @rename-view="renameTableView"
      @delete-view="deleteParkingBillView"
      @save-view="saveParkingBillView"
      @remove-filter="removeParkingBillViewFilter"
    >
      <template #header-actions>
        <el-button
          v-if="hasPermission(parkingBillPermissions.add)"
          type="primary"
          :icon="Plus"
          @click="openCreate"
        >
          {{ parkingBillWorkspaceConfig.primaryAction.label }}
        </el-button>
      </template>

      <template #toolbar>
        <DhicDataToolbar
          :workspace-config="parkingBillWorkspaceConfig"
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
          @update:filters="updateParkingBillFilters"
          @update:filter-logic="updateFilterLogic"
          @update:sort-key="updateSortKey"
          @update:sort-order="updateSortOrder"
          @update:sorts="updateSorts"
          @update:group-key="updateGroupKey"
          @update:groups="updateGroups"
          @update:row-density="updateRowDensity"
          @reset-view="resetParkingBillView"
          @save-field-config="saveParkingBillView"
        >
          <template #right>
            <el-input
              v-model="quickKeyword"
              clearable
              :prefix-icon="Search"
              class="workspace-quick-search"
              placeholder="搜索账单、车位、车牌或租户"
              aria-label="快速搜索停车账单"
              @input="queueQuickKeywordFilter"
              @keyup.enter="applyQuickKeywordNow"
              @clear="applyQuickKeywordNow"
            />
            <el-date-picker
              v-if="hasPermission(parkingBillPermissions.sync)"
              v-model="generationMonth"
              type="month"
              value-format="YYYY-MM"
              class="generation-month"
              placeholder="生成账期"
              aria-label="停车账单生成账期"
            />
            <el-button
              v-if="hasPermission(parkingBillPermissions.sync)"
              :loading="syncing"
              :icon="Refresh"
              @click="syncBills"
            >
              生成并同步
            </el-button>
            <span class="view-hint">
              全部已加载 {{ resultScope.loadedRecords }} 条，当前视图 {{ resultScope.visibleRecords }} 条
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
          index-fixed="left"
          :density="tablePrefs.rowDensity"
          :empty-text="tableEmptyText"
          :sort-state="activeTableSort"
          :data-mode="parkingBillWorkspaceConfig.query.dataMode"
          :row-class-name="parkingBillRowClassName"
          :column-width="columnWidth"
          :column-fixed="columnFixed"
          :is-column-sortable="isColumnSortable"
          :show-overflow-tooltip="column => ['partyNameSnapshot', 'remark'].includes(column.key)"
          :operation-column="{ width: 156, fixed: 'right', align: 'left' }"
          @column-resize="handleHeaderDragEnd"
          @sort-change="handleTableSortChange"
          @retry="load"
          aria-label="停车账单数据表格"
        >
          <template #operations="{ row }">
            <div class="row-actions">
              <el-button
                v-if="parkingBillActions(row).pay"
                size="small"
                type="primary"
                @click="pay(row)"
              >
                收款
              </el-button>
              <el-dropdown
                v-if="parkingBillSecondaryActions(row).length"
                trigger="click"
                popper-class="parking-bill-row-actions-menu"
                @command="action => runParkingBillSecondaryAction(row, action)"
              >
                <el-button size="small" :icon="MoreFilled" aria-label="更多停车账单操作">更多</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item
                      v-for="action in parkingBillSecondaryActions(row)"
                      :key="action.id"
                      :command="action"
                      :class="{ 'parking-bill-danger-action': action.tone === 'danger' }"
                    >
                      {{ action.label }}
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </WorkspaceDataGrid>
      </template>
    </DataWorkspace>

    <el-dialog
      v-model="createVisible"
      title="新增停车账单"
      width="min(560px, calc(100vw - 32px))"
      destroy-on-close
      @closed="resetCreateForm"
    >
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="100px">
        <el-form-item label="停车分配" prop="assignmentId">
          <el-select
            v-model="createForm.assignmentId"
            filterable
            :loading="assignmentLoading"
            placeholder="请选择已绑定的车位"
            style="width: 100%"
          >
            <el-option
              v-for="item in assignmentOptions"
              :key="item.assignmentId"
              :label="assignmentOptionLabel(item)"
              :value="item.assignmentId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="账期" prop="billingMonth">
          <el-date-picker
            v-model="createForm.billingMonth"
            type="month"
            value-format="YYYY-MM"
            placeholder="请选择收费所属月份"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="账单金额" prop="amount">
          <el-input-number
            v-model="createForm.amount"
            :min="0.01"
            :precision="2"
            :step="100"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="到期日期" prop="dueDate">
          <el-date-picker
            v-model="createForm.dueDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="请选择到期日期"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model.trim="createForm.remark" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button
          v-if="hasPermission(parkingBillPermissions.add)"
          type="primary"
          :loading="creating"
          @click="submitCreate"
        >
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MoreFilled, Plus, Refresh, Search } from '@element-plus/icons-vue'
import {
  createParkingBill,
  listParkingBillAssignments,
  listParkingBills,
  payParkingBill,
  syncParkingBills,
  voidParkingBill
} from '../../api/parking'
import DataWorkspace from '../../components/DataWorkspace.vue'
import DhicDataToolbar from '../../components/DhicDataToolbar.vue'
import WorkspaceDataGrid from '../../components/workspace/WorkspaceDataGrid.vue'
import { useDhicTableView } from '../../composables/useDhicTableView'
import {
  useWorkspaceListRequestState,
  useWorkspaceMetricState
} from '../../composables/workspace/useWorkspaceRequestState'
import { hasPermission } from '../../utils/permission'
import {
  buildParkingBillMetricSnapshot,
  parkingBillBusinessStatus,
  parkingBillBusinessStatusOptions,
  parkingBillPaymentStatus,
  parkingBillPayStatusOptions,
  parkingBillRowActions,
  parkingBillWorkspaceConfig
} from '../../workspaceConfigs/parkingBillWorkspaceConfig'
import { workspaceTableOptions } from '../../workspaceConfigs/workspaceConfig'

const route = useRoute()
const bills = ref([])
const quickKeyword = ref('')
const generationMonth = ref(currentMonth())
const syncing = ref(false)
const createVisible = ref(false)
const creating = ref(false)
const assignmentLoading = ref(false)
const assignmentOptions = ref([])
const createFormRef = ref()
const createForm = reactive({
  assignmentId: null,
  billingMonth: '',
  amount: null,
  dueDate: '',
  remark: ''
})
const createRules = {
  assignmentId: [{ required: true, message: '请选择停车分配记录', trigger: 'change' }],
  billingMonth: [{ required: true, message: '请选择收费账期', trigger: 'change' }],
  amount: [{ required: true, message: '请输入账单金额', trigger: 'blur' }],
  dueDate: [{ required: true, message: '请选择到期日期', trigger: 'change' }]
}
const parkingBillPermissions = parkingBillWorkspaceConfig.permissions
const metricKeys = ['total', 'filtered', 'unpaid', 'receivableAmount', 'paid', 'voided']
const parkingBillMetrics = useWorkspaceMetricState(metricKeys)
const parkingBillMetricData = parkingBillMetrics.values
const parkingBillMetricStates = parkingBillMetrics.states
const parkingBillListState = useWorkspaceListRequestState({
  hasResults: () => bills.value.length > 0,
  clearResults: () => {
    bills.value = []
  },
  staleMessage: '停车账单刷新失败，当前显示上次成功数据'
})
const loading = computed(() => parkingBillListState.status.value === 'loading')
const listErrorText = parkingBillListState.errorText

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
  debouncedQueryTask: debouncedQuickKeyword,
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
} = useDhicTableView(workspaceTableOptions(parkingBillWorkspaceConfig, {
  records: () => bills.value,
  serverTotal: () => bills.value.length,
  queryLifecycle: {
    task: () => applyQuickKeywordFilter(quickKeyword.value),
    delay: 300
  },
  groupLabel: parkingBillGroupLabel
}))

watch(visibleRecords, () => {
  if (parkingBillListState.status.value !== 'success') return
  parkingBillMetrics.resolve(parkingBillMetrics.revision.value, 'filtered', visibleRecords.value.length)
})

watch(
  () => route.query.keyword,
  () => {
    quickKeyword.value = routeKeyword()
    applyQuickKeywordNow()
  }
)

onMounted(async () => {
  const keyword = routeKeyword()
  quickKeyword.value = keyword || keywordFilterValue(tablePrefs.filters)
  if (keyword) applyQuickKeywordFilter(keyword)
  await load()
})

onBeforeUnmount(disposeQueryLifecycle)

function queueQuickKeywordFilter() {
  debouncedQuickKeyword.schedule()
}

function applyQuickKeywordNow() {
  debouncedQuickKeyword.cancel()
  applyQuickKeywordFilter(quickKeyword.value)
}

function applyQuickKeywordFilter(value) {
  const keyword = String(value || '').trim()
  const nextFilters = tablePrefs.filters.filter(filter => filter.key !== 'keyword')
  if (keyword) nextFilters.unshift({ key: 'keyword', operator: 'contains', value: keyword })
  updateFilters(nextFilters)
}

function keywordFilterValue(filters = tablePrefs.filters) {
  return String(filters.find(filter => filter.key === 'keyword')?.value || '')
}

function syncQuickKeywordFromView(filters = tablePrefs.filters) {
  quickKeyword.value = keywordFilterValue(filters)
}

function updateParkingBillFilters(filters) {
  updateFilters(filters)
  syncQuickKeywordFromView(filters)
}

function removeParkingBillViewFilter(index) {
  removeViewFilter(index)
  syncQuickKeywordFromView()
}

function resetParkingBillView() {
  resetTableView()
  syncQuickKeywordFromView()
}

async function switchParkingBillView(viewId) {
  if (viewId === activeViewId.value) return
  if (isViewDirty.value) {
    try {
      await ElMessageBox.confirm(
        '当前视图有未保存的调整，是否保存后切换？',
        '切换视图',
        {
          distinguishCancelAndClose: true,
          confirmButtonText: '保存并切换',
          cancelButtonText: '放弃调整'
        }
      )
      saveCurrentView()
    } catch (action) {
      if (action === 'cancel') discardCurrentViewChanges()
      else return
    }
  }
  switchTableView(viewId)
  syncQuickKeywordFromView()
}

function deleteParkingBillView(viewId) {
  deleteTableView(viewId)
  syncQuickKeywordFromView()
}

function saveParkingBillView() {
  saveCurrentView()
  ElMessage.success(`视图“${activeViewName.value}”已保存`)
}

async function load() {
  const requestId = listRequestGuard.start()
  const requestContext = parkingBillListState.begin({ resource: '/parking/bills' })
  const metricRevision = parkingBillMetrics.begin()

  try {
    const data = await listParkingBills()
    if (!listRequestGuard.isCurrent(requestId) || !parkingBillListState.isCurrent(requestContext)) return
    bills.value = parkingBillRecords(data)
    parkingBillListState.succeed(requestContext)
    resolveParkingBillMetrics(metricRevision)
  } catch (error) {
    if (!listRequestGuard.isCurrent(requestId) || !parkingBillListState.isCurrent(requestContext)) return
    parkingBillListState.fail(requestContext, '停车账单加载失败，请稍后重试')
    parkingBillMetrics.rejectAll(metricRevision)
  }
}

function resolveParkingBillMetrics(revision) {
  const snapshot = buildParkingBillMetricSnapshot(bills.value, visibleRecords.value)
  metricKeys.forEach(key => parkingBillMetrics.resolve(revision, key, snapshot[key]))
}

function parkingBillRecords(data) {
  if (Array.isArray(data)) return data
  if (Array.isArray(data?.records)) return data.records
  return []
}

function parkingBillActions(row) {
  return parkingBillRowActions(row, {
    canPay: hasPermission(parkingBillPermissions.pay),
    canUpdate: hasPermission(parkingBillPermissions.update)
  })
}

function parkingBillSecondaryActions(row) {
  const actions = parkingBillActions(row)
  return actions.void
    ? [{ id: 'void', label: '作废账单', tone: 'danger' }]
    : []
}

function runParkingBillSecondaryAction(row, action) {
  if (action?.id === 'void' && parkingBillActions(row).void) voidBill(row)
}

function parkingBillRowClassName({ row }) {
  if (['VOID', 'CANCELLED', 'DISABLED'].includes(parkingBillBusinessStatus(row))) return 'row-muted'
  if (parkingBillPaymentStatus(row) === 'PAID') return 'row-done'
  return ''
}

function parkingBillGroupLabel(value) {
  const options = [...parkingBillPayStatusOptions, ...parkingBillBusinessStatusOptions]
  return options.find(option => String(option.value) === String(value))?.label || value || '未填写'
}

async function openCreate() {
  resetCreateForm()
  createVisible.value = true
  assignmentLoading.value = true
  try {
    assignmentOptions.value = await listParkingBillAssignments()
    if (!assignmentOptions.value.length) {
      ElMessage.warning('当前没有可用于开账的有效停车分配记录')
    }
  } finally {
    assignmentLoading.value = false
  }
}

async function submitCreate() {
  const valid = await createFormRef.value?.validate().catch(() => false)
  if (!valid) return

  creating.value = true
  try {
    await createParkingBill({ ...createForm })
    ElMessage.success('停车账单新增成功')
    createVisible.value = false
    await load()
  } finally {
    creating.value = false
  }
}

function resetCreateForm() {
  createForm.assignmentId = null
  createForm.billingMonth = generationMonth.value
  createForm.amount = null
  createForm.dueDate = monthEndDate(generationMonth.value)
  createForm.remark = ''
  createFormRef.value?.clearValidate()
}

function assignmentOptionLabel(item) {
  return [item.spaceNo, item.plateNo || '未登记车牌', item.tenantName || '未登记租户']
    .filter(Boolean)
    .join(' / ')
}

async function syncBills() {
  if (!generationMonth.value) {
    ElMessage.warning('请先选择生成账期')
    return
  }

  syncing.value = true
  try {
    const result = await syncParkingBills({ month: generationMonth.value })
    const summary = syncSummary(result)
    if (Number(result?.failureCount || result?.failedCount || 0) > 0) ElMessage.warning(summary)
    else if (Number(result?.skippedCount || 0) > 0) ElMessage.warning(summary)
    else ElMessage.success(summary)
    if (Array.isArray(result?.errors) && result.errors.length) {
      await ElMessageBox.alert(syncIssueText(result.errors), '停车账单同步明细', {
        confirmButtonText: '知道了'
      })
    }
    await load()
  } finally {
    syncing.value = false
  }
}

async function pay(row) {
  if (!parkingBillActions(row).pay) return
  try {
    await ElMessageBox.confirm(
      `确定收取停车账单「${row.billNo || row.code || '-'}」的款项 ${formatMoney(row.amount)} 吗？`,
      '确认收款',
      { type: 'warning' }
    )
    await payParkingBill(row.id)
    ElMessage.success('收款成功')
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error?.message || '收款失败')
    }
  }
}

async function voidBill(row) {
  if (!parkingBillActions(row).void) return
  try {
    await ElMessageBox.confirm(
      `确定作废停车账单「${row.billNo || row.code || '-'}」吗？`,
      '确认作废',
      { type: 'warning' }
    )
    await voidParkingBill(row.id)
    ElMessage.success('账单已作废')
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error?.message || '作废失败')
    }
  }
}

function formatMoney(value) {
  const amount = Number(value)
  if (!Number.isFinite(amount)) return '￥0.00'
  return new Intl.NumberFormat('zh-CN', {
    style: 'currency',
    currency: 'CNY',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  }).format(amount)
}

function syncSummary(result = {}) {
  const generated = Number(result.generatedCount || 0)
  const success = Number(result.successCount ?? result.syncedCount ?? generated)
  const skipped = Number(result.skippedCount || 0)
  const failed = Number(result.failureCount ?? result.failedCount ?? 0)
  if (success || skipped || failed) {
    return `成功 ${success} 条，跳过 ${skipped} 条，失败 ${failed} 条`
  }
  return `生成 ${generated} 条停车账单`
}

function syncIssueText(issues = []) {
  const visible = issues.slice(0, 8).map(item => (
    `分配 ${item.assignmentId || '-'}：${item.reason || '未提供原因'}`
  ))
  if (issues.length > visible.length) {
    visible.push(`另有 ${issues.length - visible.length} 条，请处理后重新同步。`)
  }
  return visible.join('\n')
}

function currentMonth() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

function monthEndDate(month) {
  const match = /^(\d{4})-(\d{2})$/.exec(month || '')
  if (!match) return ''
  const year = Number(match[1])
  const monthNumber = Number(match[2])
  const day = new Date(year, monthNumber, 0).getDate()
  return `${match[1]}-${match[2]}-${String(day).padStart(2, '0')}`
}

function routeKeyword() {
  return typeof route.query.keyword === 'string' ? route.query.keyword : ''
}
</script>

<style scoped>
.page-container {
  padding: 20px;
}

.workspace-quick-search {
  width: 238px;
}

.generation-month {
  width: 145px;
}

.view-hint {
  color: var(--dhic-text-muted);
  font-size: 12px;
  white-space: nowrap;
}

.row-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  min-height: 28px;
}

:deep(.row-done) {
  --el-table-tr-bg-color: #f6fcf8;
}

:deep(.row-muted) {
  --el-table-tr-bg-color: #f7f8fa;
  color: var(--dhic-text-muted);
}

@media (max-width: 1024px) {
  .workspace-quick-search {
    flex: 1 1 220px;
    width: auto;
  }

  .view-hint {
    flex-basis: 100%;
  }
}
</style>

<style>
.parking-bill-row-actions-menu .parking-bill-danger-action {
  color: var(--el-color-danger);
}
</style>
