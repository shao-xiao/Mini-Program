<template>
  <main class="page-container" aria-labelledby="billing-plan-title">
    <DataWorkspace
      :workspace-config="billingPlanWorkspaceConfig"
      :views="workspaceViews"
      :active-view-id="activeViewId"
      :view-dirty="isViewDirty"
      :filter-logic="tablePrefs.filterLogic"
      :filter-logic-visible="tablePrefs.filters.length > 1"
      :filter-items="activeFilterItems"
      :query-notices="queryNotices"
      :metric-data="summary"
      @change-view="switchBillingPlanView"
      @create-view="createTableView"
      @duplicate-view="duplicateTableView"
      @rename-view="renameTableView"
      @delete-view="deleteTableView"
      @save-view="saveBillingPlanView"
      @remove-filter="removeViewFilter"
    >
      <template #header-actions>
        <el-button
          v-if="hasPermission('billing-plan:create')"
          type="primary"
          :icon="Plus"
          @click="openCreateDialog"
        >
          {{ billingPlanWorkspaceConfig.primaryAction.label }}
        </el-button>
      </template>

      <template #toolbar>
        <DhicDataToolbar
          :workspace-config="billingPlanWorkspaceConfig"
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
          @save-field-config="saveBillingPlanView"
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
          :data-mode="billingPlanWorkspaceConfig.query.dataMode"
          :column-width="columnWidth"
          :column-fixed="columnFixed"
          :is-column-sortable="isColumnSortable"
          :operation-column="{ width: 210, fixed: 'right', align: 'left' }"
          @column-resize="handleHeaderDragEnd"
          @sort-change="handleTableSortChange"
          @retry="loadWorkspaceData"
          aria-label="计费计划数据表格"
        >
          <template #operations="{ row }">
            <div class="action-buttons">
              <el-button
                v-if="hasPermission('billing-plan:update') && row.status === 'DRAFT'"
                link
                type="primary"
                @click="openEditDialog(row)"
              >编辑</el-button>
              <el-button
                v-if="hasPermission('billing-plan:update') && row.status === 'DRAFT'"
                link
                type="success"
                @click="activatePlan(row)"
              >生效</el-button>
              <el-button
                v-if="hasPermission('billing-plan:terminate') && row.status === 'ACTIVE'"
                link
                type="danger"
                @click="terminatePlan(row)"
              >终止</el-button>
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
            @current-change="loadPlans"
            @size-change="handleSizeChange"
          />
        </div>
      </template>
    </DataWorkspace>

    <el-dialog
      v-model="dialogVisible"
      :title="editingPlan ? '编辑计费计划' : '新建计费计划'"
      width="900px"
      destroy-on-close
      @closed="resetForm"
    >
      <el-alert
        class="dialog-tip"
        title="规则项绑定具体生效版本。后续规则升级不会改写该计划已经生成的账单快照。"
        type="info"
        show-icon
        :closable="false"
      />
      <el-form
        ref="formRef"
        v-loading="referenceLoading"
        :model="form"
        :rules="formRules"
        label-position="top"
      >
        <section aria-labelledby="plan-basic-title">
          <h2 id="plan-basic-title" class="section-title">基本信息</h2>
          <div class="form-grid">
            <el-form-item label="计划编号" prop="planNo">
              <el-input
                v-model="form.planNo"
                :disabled="Boolean(editingPlan)"
                maxlength="64"
                placeholder="例如 PLAN-2026-001"
              />
            </el-form-item>
            <el-form-item label="计划名称" prop="planName">
              <el-input v-model="form.planName" maxlength="128" placeholder="请输入计划名称" />
            </el-form-item>
            <el-form-item label="租户" prop="tenantId">
              <el-select
                v-model="form.tenantId"
                class="full-width"
                filterable
                placeholder="选择租户"
                @change="handleTenantChange"
              >
                <el-option
                  v-for="tenant in tenants"
                  :key="tenant.id"
                  :label="tenant.tenantName || `租户 #${tenant.id}`"
                  :value="tenant.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="合同" prop="contractId">
              <el-select
                v-model="form.contractId"
                class="full-width"
                filterable
                placeholder="选择生效合同"
                @change="handleContractChange"
              >
                <el-option
                  v-for="contract in selectableContracts"
                  :key="contract.id"
                  :label="`${contract.contractNo || contract.contractNumber || `合同 #${contract.id}`} · ${contract.tenantName || `租户 #${contract.tenantId}`}`"
                  :value="contract.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="租约（可选）" prop="leaseId">
              <el-select
                v-model="form.leaseId"
                class="full-width"
                clearable
                filterable
                placeholder="未指定时按合同计费"
                @change="handleLeaseChange"
              >
                <el-option
                  v-for="lease in selectableLeases"
                  :key="lease.id"
                  :label="lease.leaseNo || `租约 #${lease.id}`"
                  :value="lease.id"
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
              />
            </el-form-item>
            <el-form-item label="计费周期" prop="billingCycle">
              <el-select v-model="form.billingCycle" class="full-width">
                <el-option v-for="option in cycleOptions" :key="option.value" v-bind="option" />
              </el-select>
            </el-form-item>
            <el-form-item label="提前生成天数" prop="billingLeadDays">
              <el-input-number
                v-model="form.billingLeadDays"
                :min="0"
                :max="365"
                class="full-width"
              />
            </el-form-item>
            <el-form-item label="到期规则" prop="dueRule">
              <el-select v-model="form.dueRule" class="full-width">
                <el-option v-for="option in dueRuleOptions" :key="option.value" v-bind="option" />
              </el-select>
            </el-form-item>
          </div>
        </section>

        <section aria-labelledby="plan-items-title">
          <div class="section-heading">
            <div>
              <h2 id="plan-items-title" class="section-title">收费规则项</h2>
              <p class="section-help">金额最终由服务端根据规则版本计算，页面输入只作为明确 DTO 参数。</p>
            </div>
            <el-button type="primary" plain @click="addItem">添加规则项</el-button>
          </div>

          <div v-if="form.items.length" class="item-list">
            <article v-for="(item, index) in form.items" :key="item._key" class="plan-item">
              <div class="item-heading">
                <strong>规则项 {{ index + 1 }}</strong>
                <el-button
                  type="danger"
                  link
                  :disabled="form.items.length === 1"
                  :aria-label="`删除规则项 ${index + 1}`"
                  @click="removeItem(index)"
                >
                  删除
                </el-button>
              </div>
              <div class="item-grid">
                <el-form-item label="生效规则版本" required>
                  <el-select
                    v-model="item.feeRuleVersionId"
                    class="full-width"
                    filterable
                    placeholder="选择规则"
                    @change="applyRuleDefaults(item)"
                  >
                    <el-option
                      v-for="rule in selectableFeeRules"
                      :key="rule.activeVersionId"
                      :label="`${rule.ruleCode} · ${rule.ruleName} · V${rule.activeVersionNo}`"
                      :value="rule.activeVersionId"
                    />
                  </el-select>
                </el-form-item>
                <el-form-item label="费用项名称" required>
                  <el-input v-model="item.itemName" maxlength="128" placeholder="账单明细名称" />
                </el-form-item>
                <el-form-item :label="quantityLabel(item)">
                  <el-input-number v-model="item.quantity" :min="0.01" :precision="4" class="full-width" />
                </el-form-item>
                <el-form-item label="单位价格">
                  <el-input-number v-model="item.unitPrice" :min="0.01" :precision="2" class="full-width" />
                </el-form-item>
                <el-form-item label="固定金额">
                  <el-input-number v-model="item.fixedAmount" :min="0.01" :precision="2" class="full-width" />
                </el-form-item>
                <el-form-item label="排序">
                  <el-input-number v-model="item.sortOrder" :min="0" :max="9999" class="full-width" />
                </el-form-item>
              </div>
            </article>
          </div>
          <el-empty v-else :image-size="64" description="请至少添加一个收费规则项" />
        </section>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button
          v-if="hasPermission(editingPlan ? billingPlanPermissions.update : billingPlanPermissions.create)"
          type="primary"
          :loading="saving"
          @click="submitForm"
        >
          保存
        </el-button>
      </template>
    </el-dialog>
  </main>
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
import {
  billingCycleOptions,
  billingPlanStatusOptions,
  createBillingPlanWorkspaceConfig,
  dueRuleOptions
} from '../../workspaceConfigs/leasingWorkspaceConfig'
import { workspaceTableOptions } from '../../workspaceConfigs/workspaceConfig'

const statusOptions = billingPlanStatusOptions
const cycleOptions = billingCycleOptions
const tenantFilterOptions = reactive([])
const billingPlanWorkspaceConfig = createBillingPlanWorkspaceConfig({ tenantOptions: tenantFilterOptions })
const billingPlanPermissions = billingPlanWorkspaceConfig.permissions

const plans = ref([])
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
  debouncedQueryTask: debouncedBillingPlanQuery,
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
} = useDhicTableView(workspaceTableOptions(billingPlanWorkspaceConfig, {
  records: () => plans.value,
  serverTotal: () => pagination.total,
  queryLifecycle: {
    task: () => {
      resetToFirstPage(pagination)
      loadWorkspaceData()
    }
  },
  groupLabel: value => statusOptions.find(option => option.value === value)?.label || value || '未填写'
}))

watch(() => JSON.stringify(serverQueryParams.value), () => debouncedBillingPlanQuery.schedule())

const dialogVisible = ref(false)
const editingPlan = ref(null)
const formRef = ref()
let itemSequence = 0
const form = reactive(defaultForm())
const formRules = {
  planNo: [{ required: true, message: '请输入计划编号', trigger: 'blur' }],
  planName: [{ required: true, message: '请输入计划名称', trigger: 'blur' }],
  tenantId: [{ required: true, message: '请选择租户', trigger: 'change' }],
  contractId: [{ required: true, message: '请选择合同', trigger: 'change' }],
  effectiveFrom: [{ required: true, message: '请选择生效日期', trigger: 'change' }],
  effectiveTo: [{ required: true, message: '请选择失效日期', trigger: 'change' }],
  billingCycle: [{ required: true, message: '请选择计费周期', trigger: 'change' }],
  billingLeadDays: [{ required: true, message: '请输入提前生成天数', trigger: 'change' }],
  dueRule: [{ required: true, message: '请选择到期规则', trigger: 'change' }],
  currency: [{ required: true, message: '请选择币种', trigger: 'change' }]
}

const referenceLoading = ref(false)
const tenants = ref([])
const contracts = ref([])
const leases = ref([])
const activeRules = ref([])

const selectableContracts = computed(() => contracts.value.filter(contract => (
  !form.tenantId || Number(contract.tenantId) === Number(form.tenantId)
)))
const selectableLeases = computed(() => leases.value.filter(lease => (
  (!form.tenantId || Number(lease.tenantId) === Number(form.tenantId))
  && (!form.contractId || Number(lease.contractId) === Number(form.contractId))
  && !['TERMINATED', 'CANCELLED'].includes(lease.status)
)))
const selectableFeeRules = computed(() => activeRules.value.filter(ruleSelectable))

function emptySummary() {
  return { totalCount: 0, draftCount: 0, activeCount: 0, terminatedCount: 0, estimatedPeriodAmount: 0 }
}

function defaultItem(source = {}) {
  itemSequence += 1
  return {
    _key: `plan-item-${itemSequence}`,
    feeRuleVersionId: source.feeRuleVersionId || null,
    itemName: source.itemName || '',
    quantity: source.quantity == null ? 1 : Number(source.quantity),
    unitPrice: source.unitPrice == null ? null : Number(source.unitPrice),
    fixedAmount: source.fixedAmount == null ? null : Number(source.fixedAmount),
    sortOrder: source.sortOrder == null ? 0 : Number(source.sortOrder)
  }
}

function defaultForm() {
  return {
    planNo: '',
    planName: '',
    tenantId: null,
    contractId: null,
    leaseId: null,
    effectiveFrom: '',
    effectiveTo: '',
    billingCycle: 'MONTHLY',
    billingLeadDays: 0,
    dueRule: 'PERIOD_END',
    currency: 'CNY',
    items: [defaultItem()]
  }
}

function requestErrorMessage(error, fallback) {
  return error?.response?.data?.message || error?.message || error?.msg || fallback
}

async function switchBillingPlanView(viewId) {
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

function saveBillingPlanView() {
  saveCurrentView()
  ElMessage.success(`视图“${activeViewName.value}”已保存`)
}

function summaryFilterParams() {
  const params = serverQueryParams.value
  return {
    keyword: params.keyword,
    status: params.status,
    tenantId: params.tenantId,
    contractId: params.contractId,
    leaseId: params.leaseId
  }
}

async function loadPlans() {
  if (!hasPermission(billingPlanPermissions.view)) {
    plans.value = []
    pagination.total = 0
    return
  }
  const requestId = listRequestGuard.start()
  loading.value = true
  loadError.value = ''
  try {
    const data = await request.get('/billing-plans/search', {
      params: {
        ...serverQueryParams.value,
        ...pageParams(pagination),
      },
      silent: true
    })
    if (!listRequestGuard.isCurrent(requestId)) return
    const page = readPage(data)
    plans.value = page.records
    pagination.total = page.total
  } catch (error) {
    if (!listRequestGuard.isCurrent(requestId)) return
    plans.value = []
    pagination.total = 0
    loadError.value = requestErrorMessage(error, '计费计划列表加载失败')
  } finally {
    if (listRequestGuard.isCurrent(requestId)) loading.value = false
  }
}

async function loadSummary() {
  if (!hasPermission(billingPlanPermissions.view)) {
    Object.assign(summary, emptySummary())
    return
  }
  const requestId = summaryRequestGuard.start()
  try {
    const data = await request.get('/billing-plans/summary', { params: summaryFilterParams(), silent: true })
    if (summaryRequestGuard.isCurrent(requestId)) Object.assign(summary, emptySummary(), data || {})
  } catch {
    if (summaryRequestGuard.isCurrent(requestId)) Object.assign(summary, emptySummary())
  }
}

async function loadWorkspaceData() {
  await Promise.all([loadPlans(), loadSummary()])
}

async function handleSizeChange() {
  resetToFirstPage(pagination)
  await loadPlans()
}

function readRecords(data) {
  return Array.isArray(data) ? data : readPage(data).records
}

function syncTenantFilterOptions(records) {
  tenantFilterOptions.splice(0, tenantFilterOptions.length, ...records.map(tenant => ({
    label: tenant.tenantName || tenant.name || `租户 #${tenant.id}`,
    value: tenant.id
  })))
}

async function loadTenantFilterOptions() {
  try {
    const data = await request.get('/tenant/list', { silent: true })
    syncTenantFilterOptions(readRecords(data))
  } catch {
    tenantFilterOptions.splice(0)
  }
}

async function loadReferenceData() {
  referenceLoading.value = true
  const results = await Promise.allSettled([
    request.get('/tenant/list', { silent: true }),
    request.get('/contracts/search', { params: { page: 1, pageSize: 100, status: 'ACTIVE', sortField: 'updatedAt', sortDirection: 'desc' }, silent: true }),
    request.get('/leases/search', { params: { page: 1, pageSize: 100, sortField: 'updatedAt', sortDirection: 'desc' }, silent: true }),
    request.get('/fee-rules/search', { params: { page: 1, pageSize: 100, status: 'ACTIVE', sortField: 'ruleCode', sortDirection: 'asc' }, silent: true })
  ])
  const targets = [tenants, contracts, leases, activeRules]
  results.forEach((result, index) => {
    targets[index].value = result.status === 'fulfilled' ? readRecords(result.value) : []
  })
  syncTenantFilterOptions(tenants.value)
  referenceLoading.value = false
  if (results.some(result => result.status === 'rejected')) {
    ElMessage.warning('部分关联数据加载失败，请确认租户、合同、租约和收费规则查看权限')
  }
}

function resetForm() {
  editingPlan.value = null
  Object.assign(form, defaultForm())
  formRef.value?.clearValidate()
}

async function openCreateDialog() {
  resetForm()
  dialogVisible.value = true
  await loadReferenceData()
}

async function openEditDialog(row) {
  dialogVisible.value = true
  referenceLoading.value = true
  try {
    const [detail] = await Promise.all([
      request.get(`/billing-plans/${row.id}`, { silent: true }),
      loadReferenceData()
    ])
    editingPlan.value = detail || row
    Object.assign(form, defaultForm(), {
      planNo: detail.planNo,
      planName: detail.planName,
      tenantId: detail.tenantId,
      contractId: detail.contractId,
      leaseId: detail.leaseId || null,
      effectiveFrom: detail.effectiveFrom,
      effectiveTo: detail.effectiveTo || '',
      billingCycle: detail.billingCycle,
      billingLeadDays: Number(detail.billingLeadDays || 0),
      dueRule: detail.dueRule,
      currency: detail.currency || 'CNY',
      items: Array.isArray(detail.items) && detail.items.length
        ? detail.items.map(defaultItem)
        : [defaultItem()]
    })
  } catch (error) {
    dialogVisible.value = false
    ElMessage.error(requestErrorMessage(error, '计费计划详情加载失败'))
  } finally {
    referenceLoading.value = false
  }
}

function handleTenantChange() {
  form.contractId = null
  form.leaseId = null
}

function handleContractChange(contractId) {
  form.leaseId = null
  const contract = contracts.value.find(item => Number(item.id) === Number(contractId))
  form.effectiveFrom = contract?.effectiveDate || contract?.startDate || ''
  form.effectiveTo = contract?.endDate || ''
}

function handleLeaseChange(leaseId) {
  if (!leaseId) return
  const lease = leases.value.find(item => Number(item.id) === Number(leaseId))
  if (!lease) return
  form.effectiveFrom = lease.plannedStartDate || form.effectiveFrom
  form.effectiveTo = lease.plannedEndDate || form.effectiveTo
}

function addItem() {
  form.items.push(defaultItem({ sortOrder: form.items.length }))
}

function removeItem(index) {
  if (form.items.length > 1) form.items.splice(index, 1)
}

function selectedRule(item) {
  return activeRules.value.find(rule => Number(rule.activeVersionId) === Number(item.feeRuleVersionId))
}

function ruleSelectable(rule) {
  if (rule.billingCycle !== form.billingCycle) return false
  if ((rule.currency || 'CNY') !== form.currency) return false
  if (form.effectiveFrom && rule.effectiveFrom && form.effectiveFrom < rule.effectiveFrom) return false
  if (form.effectiveTo && rule.effectiveTo && form.effectiveTo > rule.effectiveTo) return false
  return true
}

function applyRuleDefaults(item) {
  const rule = selectedRule(item)
  if (!rule) return
  item.itemName = item.itemName || rule.ruleName
  item.fixedAmount = rule.fixedAmount == null ? null : Number(rule.fixedAmount)
  item.unitPrice = rule.unitPrice == null ? null : Number(rule.unitPrice)
}

function quantityLabel(item) {
  const calculationType = selectedRule(item)?.calculationType
  if (calculationType === 'AREA_UNIT_PRICE') return '计费面积'
  if (calculationType === 'QUANTITY_UNIT_PRICE') return '计费数量'
  return '数量'
}

function validateItems() {
  if (!form.items.length) return '请至少添加一个收费规则项'
  const versionIds = new Set()
  for (const [index, item] of form.items.entries()) {
    if (!item.feeRuleVersionId) return `规则项 ${index + 1} 未选择生效规则版本`
    if (versionIds.has(Number(item.feeRuleVersionId))) return '同一收费规则版本不能重复添加'
    versionIds.add(Number(item.feeRuleVersionId))
    const rule = selectedRule(item)
    if (!rule || !ruleSelectable(rule)) {
      return `规则项 ${index + 1} 与计划周期、币种或有效区间不一致`
    }
    if (!item.itemName.trim()) return `规则项 ${index + 1} 未填写费用项名称`
    if (!(Number(item.quantity) > 0)) return `规则项 ${index + 1} 的数量必须大于 0`
  }
  return ''
}

function buildPayload() {
  return {
    planName: form.planName.trim(),
    tenantId: Number(form.tenantId),
    contractId: Number(form.contractId),
    leaseId: form.leaseId ? Number(form.leaseId) : undefined,
    effectiveFrom: form.effectiveFrom,
    effectiveTo: form.effectiveTo || undefined,
    billingCycle: form.billingCycle,
    billingLeadDays: Number(form.billingLeadDays),
    dueRule: form.dueRule,
    currency: form.currency,
    items: form.items.map(item => ({
      feeRuleVersionId: Number(item.feeRuleVersionId),
      itemName: item.itemName.trim(),
      quantity: Number(item.quantity),
      unitPrice: item.unitPrice == null ? undefined : Number(item.unitPrice),
      fixedAmount: item.fixedAmount == null ? undefined : Number(item.fixedAmount),
      sortOrder: Number(item.sortOrder || 0)
    }))
  }
}

async function submitForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (form.effectiveTo && form.effectiveTo < form.effectiveFrom) {
    ElMessage.warning('失效日期不能早于生效日期')
    return
  }
  const itemError = validateItems()
  if (itemError) {
    ElMessage.warning(itemError)
    return
  }

  saving.value = true
  try {
    const payload = buildPayload()
    if (editingPlan.value) {
      await request.put(`/billing-plans/${editingPlan.value.id}`, {
        ...payload,
        version: editingPlan.value.version
      })
      ElMessage.success('计费计划已更新')
    } else {
      await request.post('/billing-plans', { planNo: form.planNo.trim(), ...payload })
      ElMessage.success('计费计划已创建')
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

async function activatePlan(row) {
  const reason = await actionReason('生效计费计划')
  if (!reason) return
  await request.post(`/billing-plans/${row.id}/activate`, { version: row.version, reason })
  ElMessage.success('计费计划已生效')
  await loadWorkspaceData()
}

async function terminatePlan(row) {
  const reason = await actionReason('终止计费计划')
  if (!reason) return
  await request.post(`/billing-plans/${row.id}/terminate`, { version: row.version, reason })
  ElMessage.success('计费计划已终止，历史账单不受影响')
  await loadWorkspaceData()
}

onMounted(() => {
  Promise.all([loadWorkspaceData(), loadTenantFilterOptions()])
})

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
.keyword-input { width: 260px; }
.filter-select { width: 150px; }
.id-filter { width: 130px; }
.load-alert, .dialog-tip { margin-bottom: 16px; }
.data-table { width: 100%; font-size: 14px; }
.primary-text { color: #303133; font-weight: 600; line-height: 22px; }
.secondary-text { margin-top: 2px; color: #7b8494; font-size: 12px; line-height: 18px; }
.money-text { color: #303133; font-variant-numeric: tabular-nums; font-weight: 600; }
.action-buttons { display: flex; flex-wrap: wrap; justify-content: center; gap: 8px; }
.action-buttons :deep(.el-button + .el-button) { margin-left: 0; }
.pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 16px; overflow-x: auto; }
.section-title { margin: 0 0 14px; color: #303133; font-size: 15px; font-weight: 600; }
.section-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin: 8px 0 12px; }
.section-heading .section-title { margin-bottom: 3px; }
.section-help { margin: 0; color: #7b8494; font-size: 12px; line-height: 18px; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 16px; }
.full-width { width: 100%; }
.item-list { display: grid; gap: 12px; }
.plan-item { padding: 14px 16px 2px; border: 1px solid #dcdfe6; border-radius: 4px; background: #fafafa; }
.item-heading { display: flex; align-items: center; justify-content: space-between; margin-bottom: 4px; color: #303133; }
.item-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 0 12px; }

@media (max-width: 768px) {
  .page-container { padding: 12px; }
  .page-header, .section-heading { align-items: flex-start; flex-direction: column; }
  .filter-form :deep(.el-form-item) { display: flex; margin-right: 0; width: 100%; }
  .filter-form :deep(.el-form-item__content) { flex: 1; }
  .keyword-input, .filter-select, .id-filter { width: 100%; }
  .form-grid, .item-grid { grid-template-columns: 1fr; }
}

@media (prefers-reduced-motion: reduce) {
  .data-table :deep(*) { scroll-behavior: auto; }
}
</style>
