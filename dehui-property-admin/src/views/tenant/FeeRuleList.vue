<template>
  <main class="page-container" aria-labelledby="fee-rule-title">
    <DataWorkspace
      :workspace-config="feeRuleWorkspaceConfig"
      :views="workspaceViews"
      :active-view-id="activeViewId"
      :view-dirty="isViewDirty"
      :filter-logic="tablePrefs.filterLogic"
      :filter-logic-visible="tablePrefs.filters.length > 1"
      :filter-items="activeFilterItems"
      :query-notices="queryNotices"
      :metric-data="summary"
      @change-view="switchFeeRuleView"
      @create-view="createTableView"
      @duplicate-view="duplicateTableView"
      @rename-view="renameTableView"
      @delete-view="deleteTableView"
      @save-view="saveFeeRuleView"
      @remove-filter="removeViewFilter"
    >
      <template #header-actions>
        <el-button
          v-if="hasPermission('fee-rule:create')"
          type="primary"
          :icon="Plus"
          @click="openCreateDialog"
        >
          {{ feeRuleWorkspaceConfig.primaryAction.label }}
        </el-button>
      </template>

      <template #toolbar>
        <DhicDataToolbar
          :workspace-config="feeRuleWorkspaceConfig"
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
          @save-field-config="saveFeeRuleView"
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
          :data-mode="feeRuleWorkspaceConfig.query.dataMode"
          :column-width="columnWidth"
          :column-fixed="columnFixed"
          :is-column-sortable="isColumnSortable"
          :show-overflow-tooltip="column => column.key === 'description'"
          :operation-column="{ width: 210, fixed: 'right', align: 'left' }"
          @column-resize="handleHeaderDragEnd"
          @sort-change="handleTableSortChange"
          @retry="loadWorkspaceData"
          aria-label="收费规则数据表格"
        >
          <template #cell-currentVersionNo="{ row }">
            <el-button link type="primary" @click="openVersions(row)">V{{ row.currentVersionNo || 1 }}</el-button>
          </template>
          <template #operations="{ row }">
            <div class="action-buttons">
              <el-button v-if="hasPermission('fee-rule:update')" link type="primary" @click="openEditDialog(row)">编辑</el-button>
              <el-button
                v-if="hasPermission('fee-rule:activate') && row.currentVersionId !== row.activeVersionId"
                link
                type="success"
                @click="activateRule(row)"
              >生效</el-button>
              <el-button
                v-if="hasPermission(feeRulePermissions.update) && row.status === 'ACTIVE'"
                link
                type="warning"
                @click="disableRule(row)"
              >停用</el-button>
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
            @current-change="loadRules"
            @size-change="handleSizeChange"
          />
        </div>
      </template>
    </DataWorkspace>

    <el-dialog
      v-model="dialogVisible"
      :title="editingRule ? '编辑收费规则' : '新建收费规则'"
      width="720px"
      destroy-on-close
      @closed="resetForm"
    >
      <el-alert
        class="dialog-tip"
        title="已生效版本不会被覆盖；编辑保存后将形成新的规则版本。"
        type="info"
        show-icon
        :closable="false"
      />
      <el-form ref="formRef" :model="form" :rules="formRules" label-position="top">
        <div class="form-grid">
          <el-form-item label="规则编号" prop="ruleCode">
            <el-input
              v-model="form.ruleCode"
              :disabled="Boolean(editingRule)"
              maxlength="64"
              placeholder="例如 PROPERTY_STANDARD"
            />
          </el-form-item>
          <el-form-item label="规则名称" prop="ruleName">
            <el-input v-model="form.ruleName" maxlength="128" placeholder="请输入规则名称" />
          </el-form-item>
          <el-form-item label="费用类型" prop="feeType">
            <el-select v-model="form.feeType" class="full-width">
              <el-option v-for="option in feeTypeOptions" :key="option.value" v-bind="option" />
            </el-select>
          </el-form-item>
          <el-form-item label="计价方式" prop="calculationType">
            <el-select
              v-model="form.calculationType"
              class="full-width"
              @change="handleCalculationTypeChange"
            >
              <el-option
                v-for="option in calculationTypeOptions"
                :key="option.value"
                :label="option.disabled ? `${option.label}（待业务参数）` : option.label"
                :value="option.value"
                :disabled="option.disabled"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="计费周期" prop="billingCycle">
            <el-select v-model="form.billingCycle" class="full-width">
              <el-option
                v-for="option in cycleOptions"
                :key="option.value"
                :label="option.label"
                :value="option.value"
                :disabled="!cycleAllowed(option.value)"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="币种" prop="currency">
            <el-select v-model="form.currency" class="full-width">
              <el-option label="人民币（CNY）" value="CNY" />
            </el-select>
          </el-form-item>
          <el-form-item label="生效日期" prop="effectiveFrom">
            <el-date-picker
              v-model="form.effectiveFrom"
              type="date"
              value-format="YYYY-MM-DD"
              class="full-width"
            />
          </el-form-item>
          <el-form-item label="失效日期" prop="effectiveTo">
            <el-date-picker
              v-model="form.effectiveTo"
              type="date"
              value-format="YYYY-MM-DD"
              class="full-width"
              clearable
            />
          </el-form-item>
          <el-form-item v-if="usesFixedAmount(form.calculationType)" label="固定金额" prop="fixedAmount">
            <el-input-number v-model="form.fixedAmount" :min="0.01" :precision="2" class="full-width" />
          </el-form-item>
          <el-form-item v-else label="单位价格" prop="unitPrice">
            <el-input-number v-model="form.unitPrice" :min="0.01" :precision="2" class="full-width" />
          </el-form-item>
        </div>
        <el-form-item label="规则说明">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="说明计费口径和适用范围"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button
          v-if="hasPermission(editingRule ? feeRulePermissions.update : feeRulePermissions.create)"
          type="primary"
          :loading="saving"
          @click="submitForm"
        >
          保存
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="versionDialogVisible" title="规则版本记录" width="760px">
      <WorkspaceDataGrid
        :loading="versionLoading"
        :rows="versions"
        :columns="versionColumns"
        row-key="id"
        :show-index="false"
        empty-text="暂无版本记录"
        aria-label="收费规则版本记录"
      />
    </el-dialog>
  </main>
</template>

<script setup>
import { onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
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
import {
  billingCycleOptions,
  calculationTypeOptions,
  feeRuleStatusOptions,
  feeRuleWorkspaceConfig,
  feeTypeOptions
} from '../../workspaceConfigs/leasingWorkspaceConfig'
import { workspaceTableOptions } from '../../workspaceConfigs/workspaceConfig'

const supportedCalculationOptions = calculationTypeOptions.filter(option => !option.disabled)
const cycleOptions = billingCycleOptions
const statusOptions = feeRuleStatusOptions
const feeRulePermissions = feeRuleWorkspaceConfig.permissions

const rules = ref([])
const loading = ref(false)
const saving = ref(false)
const loadError = ref('')
const pagination = reactive(createPagination(20))
const summaryRequestGuard = createLatestRequestGuard()
const summary = reactive(emptySummary())

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
  debouncedQueryTask: debouncedFeeRuleQuery,
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
} = useDhicTableView(workspaceTableOptions(feeRuleWorkspaceConfig, {
  records: () => rules.value,
  serverTotal: () => pagination.total,
  queryLifecycle: {
    task: () => {
      resetToFirstPage(pagination)
      loadWorkspaceData()
    }
  },
  groupLabel: value => statusOptions.find(option => option.value === value)?.label || value || '未填写'
}))

watch(() => JSON.stringify(serverQueryParams.value), () => debouncedFeeRuleQuery.schedule())

const dialogVisible = ref(false)
const editingRule = ref(null)
const formRef = ref()
const form = reactive(defaultForm())
const formRules = {
  ruleCode: [{ required: true, message: '请输入规则编号', trigger: 'blur' }],
  ruleName: [{ required: true, message: '请输入规则名称', trigger: 'blur' }],
  feeType: [{ required: true, message: '请选择费用类型', trigger: 'change' }],
  calculationType: [{ required: true, message: '请选择计价方式', trigger: 'change' }],
  billingCycle: [{ required: true, message: '请选择计费周期', trigger: 'change' }],
  effectiveFrom: [{ required: true, message: '请选择生效日期', trigger: 'change' }],
  currency: [{ required: true, message: '请选择币种', trigger: 'change' }]
}

const versionDialogVisible = ref(false)
const versionLoading = ref(false)
const versions = ref([])
const versionColumns = [
  { key: 'versionNo', label: '版本', type: 'number', width: 80, align: 'center' },
  { key: 'status', label: '状态', type: 'status', options: statusOptions, width: 100 },
  { key: 'calculationType', label: '计价方式', type: 'select', options: calculationTypeOptions, minWidth: 150 },
  { key: 'effectiveFrom', label: '生效日期', type: 'date', width: 120 },
  { key: 'effectiveTo', label: '失效日期', type: 'date', width: 120 },
  { key: 'activatedAt', label: '生效时间', type: 'date', format: 'datetime', minWidth: 170 }
]

function emptySummary() {
  return { totalCount: 0, draftCount: 0, activeCount: 0, disabledCount: 0 }
}

function defaultForm() {
  return {
    ruleCode: '',
    ruleName: '',
    feeType: 'RENT',
    calculationType: 'FIXED',
    billingCycle: 'MONTHLY',
    effectiveFrom: '',
    effectiveTo: '',
    fixedAmount: null,
    unitPrice: null,
    currency: 'CNY',
    description: ''
  }
}

function usesFixedAmount(calculationType) {
  return ['FIXED', 'ONE_TIME'].includes(calculationType)
}

function cycleAllowed(cycle) {
  return form.calculationType === 'ONE_TIME' ? cycle === 'ONE_TIME' : cycle !== 'ONE_TIME'
}

function handleCalculationTypeChange(calculationType) {
  if (calculationType === 'ONE_TIME') {
    form.billingCycle = 'ONE_TIME'
  } else if (form.billingCycle === 'ONE_TIME') {
    form.billingCycle = 'MONTHLY'
  }
}

function requestErrorMessage(error, fallback) {
  return error?.response?.data?.message || error?.message || error?.msg || fallback
}

async function switchFeeRuleView(viewId) {
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

function saveFeeRuleView() {
  saveCurrentView()
  ElMessage.success(`视图“${activeViewName.value}”已保存`)
}

function filterParams() {
  const params = serverQueryParams.value
  return {
    keyword: params.keyword,
    status: params.status,
    feeType: params.feeType,
    calculationType: params.calculationType
  }
}

async function loadRules() {
  if (!hasPermission(feeRulePermissions.view)) {
    rules.value = []
    pagination.total = 0
    return
  }
  const requestId = listRequestGuard.start()
  loading.value = true
  loadError.value = ''
  try {
    const data = await request.get('/fee-rules/search', {
      params: {
        ...serverQueryParams.value,
        ...pageParams(pagination),
      },
      silent: true
    })
    if (!listRequestGuard.isCurrent(requestId)) return
    const page = readPage(data)
    rules.value = page.records
    pagination.total = page.total
  } catch (error) {
    if (!listRequestGuard.isCurrent(requestId)) return
    rules.value = []
    pagination.total = 0
    loadError.value = requestErrorMessage(error, '收费规则列表加载失败')
  } finally {
    if (listRequestGuard.isCurrent(requestId)) loading.value = false
  }
}

async function loadSummary() {
  if (!hasPermission(feeRulePermissions.view)) {
    Object.assign(summary, emptySummary())
    return
  }
  const requestId = summaryRequestGuard.start()
  try {
    const data = await request.get('/fee-rules/summary', {
      params: filterParams(),
      silent: true
    })
    if (summaryRequestGuard.isCurrent(requestId)) Object.assign(summary, emptySummary(), data || {})
  } catch {
    if (summaryRequestGuard.isCurrent(requestId)) Object.assign(summary, emptySummary())
  }
}

async function loadWorkspaceData() {
  await Promise.all([loadRules(), loadSummary()])
}

async function searchRules() {
  resetToFirstPage(pagination)
  await loadWorkspaceData()
}

async function handleSizeChange() {
  resetToFirstPage(pagination)
  await loadRules()
}

function resetForm() {
  editingRule.value = null
  Object.assign(form, defaultForm())
  formRef.value?.clearValidate()
}

function openCreateDialog() {
  resetForm()
  dialogVisible.value = true
}

function openEditDialog(row) {
  editingRule.value = row
  const editingPublishedVersion = row.activeVersionId != null && row.currentVersionId === row.activeVersionId
  Object.assign(form, defaultForm(), {
    ruleCode: row.ruleCode,
    ruleName: row.ruleName,
    feeType: row.feeType,
    calculationType: row.calculationType,
    billingCycle: row.billingCycle,
    effectiveFrom: editingPublishedVersion ? '' : row.effectiveFrom,
    effectiveTo: row.effectiveTo || '',
    fixedAmount: row.fixedAmount == null ? null : Number(row.fixedAmount),
    unitPrice: row.unitPrice == null ? null : Number(row.unitPrice),
    currency: row.currency || 'CNY',
    description: row.description || ''
  })
  dialogVisible.value = true
}

function buildPayload() {
  const fixed = usesFixedAmount(form.calculationType)
  return {
    ruleName: form.ruleName.trim(),
    feeType: form.feeType,
    calculationType: form.calculationType,
    billingCycle: form.billingCycle,
    effectiveFrom: form.effectiveFrom,
    effectiveTo: form.effectiveTo || undefined,
    fixedAmount: fixed ? form.fixedAmount : undefined,
    unitPrice: fixed ? undefined : form.unitPrice,
    currency: form.currency,
    description: form.description.trim() || undefined
  }
}

async function submitForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (form.effectiveTo && form.effectiveTo < form.effectiveFrom) {
    ElMessage.warning('失效日期不能早于生效日期')
    return
  }
  const amountValue = usesFixedAmount(form.calculationType) ? form.fixedAmount : form.unitPrice
  if (!(Number(amountValue) > 0)) {
    ElMessage.warning(usesFixedAmount(form.calculationType) ? '固定金额必须大于 0' : '单位价格必须大于 0')
    return
  }

  saving.value = true
  try {
    const payload = buildPayload()
    if (editingRule.value) {
      await request.put(`/fee-rules/${editingRule.value.id}`, {
        ...payload,
        version: editingRule.value.version
      })
      ElMessage.success('新规则版本已保存')
    } else {
      await request.post('/fee-rules', { ruleCode: form.ruleCode.trim(), ...payload })
      ElMessage.success('收费规则已创建')
    }
    dialogVisible.value = false
    await loadWorkspaceData()
  } finally {
    saving.value = false
  }
}

async function actionReason(title) {
  try {
    const result = await ElMessageBox.prompt('请输入本次操作原因，便于审计追溯。', title, {
      inputPlaceholder: '操作原因',
      inputValidator: value => Boolean(value?.trim()) || '请输入操作原因',
      confirmButtonText: '确认',
      cancelButtonText: '取消'
    })
    return result.value.trim()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return null
    throw error
  }
}

async function activateRule(row) {
  const reason = await actionReason('生效收费规则')
  if (!reason) return
  await request.post(`/fee-rules/${row.id}/activate`, { version: row.version, reason })
  ElMessage.success('收费规则已生效')
  await loadWorkspaceData()
}

async function disableRule(row) {
  const reason = await actionReason('停用收费规则')
  if (!reason) return
  await request.post(`/fee-rules/${row.id}/disable`, { version: row.version, reason })
  ElMessage.success('收费规则已停用，历史版本仍保留')
  await loadWorkspaceData()
}

async function openVersions(row) {
  versionDialogVisible.value = true
  versionLoading.value = true
  versions.value = []
  try {
    const data = await request.get(`/fee-rules/${row.id}/versions`, { silent: true })
    versions.value = Array.isArray(data) ? data : readPage(data).records
  } catch (error) {
    versions.value = []
    ElMessage.error(requestErrorMessage(error, '规则版本加载失败'))
  } finally {
    versionLoading.value = false
  }
}

onMounted(loadWorkspaceData)

onBeforeUnmount(() => {
  disposeQueryLifecycle()
  summaryRequestGuard.dispose()
})
</script>

<style scoped>
.page-container { padding: 20px; }
.page-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.page-title { margin: 0; color: #303133; font-size: 18px; font-weight: 600; line-height: 1.4; }
.page-subtitle { margin: 4px 0 0; color: #7b8494; font-size: 13px; line-height: 1.5; }
.filter-form { margin-bottom: 4px; }
.keyword-input { width: 240px; }
.filter-select { width: 150px; }
.load-alert, .dialog-tip { margin-bottom: 16px; }
.data-table { width: 100%; font-size: 14px; }
.primary-text { color: #303133; font-weight: 600; line-height: 22px; }
.secondary-text { margin-top: 2px; color: #7b8494; font-size: 12px; line-height: 18px; }
.money-text { color: #303133; font-variant-numeric: tabular-nums; font-weight: 600; }
.action-buttons { display: flex; flex-wrap: wrap; justify-content: center; gap: 8px; }
.action-buttons :deep(.el-button + .el-button) { margin-left: 0; }
.pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 16px; overflow-x: auto; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 16px; }
.full-width { width: 100%; }

@media (max-width: 768px) {
  .page-container { padding: 12px; }
  .page-header { align-items: flex-start; flex-direction: column; }
  .filter-form :deep(.el-form-item) { display: flex; margin-right: 0; width: 100%; }
  .filter-form :deep(.el-form-item__content) { flex: 1; }
  .keyword-input, .filter-select { width: 100%; }
  .form-grid { grid-template-columns: 1fr; }
}

@media (prefers-reduced-motion: reduce) {
  .data-table :deep(*) { scroll-behavior: auto; }
}
</style>
