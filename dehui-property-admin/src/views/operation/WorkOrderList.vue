<template>
  <div class="page-container">
    <DataWorkspace
      :workspace-config="workOrderWorkspaceConfig"
      :views="workspaceViews"
      :active-view-id="activeViewId"
      :view-dirty="isViewDirty"
      :filter-logic="tablePrefs.filterLogic"
      :filter-logic-visible="tablePrefs.filters.length > 1"
      :filter-items="activeFilterItems"
      :query-notices="queryNotices"
      :selected-count="selectedRows.length"
      :metric-data="workOrderMetricData"
      :metric-states="workOrderMetricStates"
      @change-view="switchWorkOrderView"
      @create-view="createTableView"
      @duplicate-view="duplicateTableView"
      @rename-view="renameTableView"
      @delete-view="deleteTableView"
      @save-view="saveWorkOrderView"
      @remove-filter="removeWorkOrderViewFilter"
      @batch-action="runWorkOrderBatchAction"
      @clear-selection="clearWorkOrderSelection"
    >
      <template #header-actions>
        <el-button v-if="hasPermission('custom-field:manage')" @click="moduleSettingsVisible = true">
          模块字段配置
        </el-button>
        <el-button v-if="hasPermission(workOrderPermissions.create)" type="primary" :icon="Plus" @click="openDialog">
          {{ workOrderWorkspaceConfig.primaryAction.label }}
        </el-button>
      </template>

      <template #toolbar>
        <DhicDataToolbar
          :workspace-config="workOrderWorkspaceConfig"
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
          @update:filters="updateWorkOrderFilters"
          @update:filter-logic="updateFilterLogic"
          @update:sort-key="updateSortKey"
          @update:sort-order="updateSortOrder"
          @update:sorts="updateSorts"
          @update:group-key="updateGroupKey"
          @update:groups="updateGroups"
          @update:row-density="updateRowDensity"
          @reset-view="resetWorkOrderView"
          @save-field-config="saveWorkOrderView"
          @workspace-action="handleWorkOrderTransferAction"
        >
          <template #right>
            <el-input
              v-model.trim="query.keyword"
              clearable
              :prefix-icon="Search"
              class="workspace-quick-search"
              placeholder="搜索工单号、标题、位置"
              aria-label="快速搜索工单"
              @keyup.enter="search"
              @clear="search"
            />
            <el-tooltip content="清除快速查询" placement="bottom">
              <el-button text circle :icon="RefreshLeft" aria-label="清除快速查询" @click="resetQuery" />
            </el-tooltip>
            <span class="view-hint">当前页 {{ resultScope.loadedRecords }} 条</span>
          </template>
        </DhicDataToolbar>
      </template>

      <template #grid>
        <WorkspaceDataGrid
          ref="workOrderTableRef"
          :loading="loading"
          :error-text="listErrorText"
          retryable
          :rows="tableRows"
          :columns="visibleOrderedColumns"
          row-key="id"
          :selected-row-keys="selectedRowKeys"
          show-selection
          :selectable="selectableWorkOrderRow"
          :current-page="pagination.currentPage"
          :page-size="pagination.pageSize"
          :index-width="72"
          index-fixed="left"
          :density="tablePrefs.rowDensity"
          :empty-text="tableEmptyText"
          :sort-state="activeTableSort"
          :data-mode="workOrderWorkspaceConfig.query.dataMode"
          :row-class-name="workOrderRowClassName"
          :column-width="columnWidth"
          :column-fixed="columnFixed"
          :is-column-sortable="isColumnSortable"
          :show-overflow-tooltip="column => ['title', 'location', 'remark'].includes(column.key)"
          :can-edit-cell="canEditWorkOrderCell"
          :cell-saving-key="inlineSavingCell"
          :cell-key="inlineCellKey"
          :edit-context-key="activeViewId"
          :operation-column="{ width: 250, fixed: 'right', align: 'center' }"
          @selection-change="handleSelectionChange"
          @column-resize="handleHeaderDragEnd"
          @sort-change="handleTableSortChange"
          @cell-save="handleWorkOrderCellSave"
          @retry="load"
          aria-label="工单管理数据表格"
        >
          <template #cell-extra-status="{ row }">
            <el-tag v-if="row.slaOverdue" type="danger" size="small">超时</el-tag>
          </template>

          <template #cell-handler="{ row, field }">
            <div class="relation-action-cell">
              <CellRenderer :field="field" :row="row" />
              <el-button v-if="canAssignWorkOrders" link type="primary" @click="assign(row)">分配</el-button>
            </div>
          </template>

          <template #operations="{ row }">
            <div class="action-cell">
              <el-button link type="primary" @click="openDetail(row)">详情</el-button>
              <el-button
                v-if="hasPermission(workOrderPermissions.update)"
                link
                type="primary"
                @click="openEdit(row)"
              >编辑</el-button>
              <el-button
                v-if="primaryWorkOrderAction(row)"
                size="small"
                :type="primaryWorkOrderAction(row).type"
                @click="runPrimaryWorkOrderAction(row)"
              >
                {{ primaryWorkOrderAction(row).label }}
              </el-button>
              <el-dropdown v-if="secondaryWorkOrderActions(row).length" trigger="click" @command="command => handleWorkOrderCommand(command, row)">
                <el-button size="small">更多</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item
                      v-for="action in secondaryWorkOrderActions(row)"
                      :key="action.id"
                      :command="action.id"
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

      <template #footer>
        <div class="pagination-wrapper">
          <el-pagination
            v-model:current-page="pagination.currentPage"
            v-model:page-size="pagination.pageSize"
            :total="pagination.total"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            @current-change="load"
            @size-change="handleSizeChange"
          />
        </div>
      </template>
    </DataWorkspace>

    <el-dialog v-model="visible" :title="editingId ? '编辑工单' : '新增工单'" width="560px">
      <el-form :model="form" label-width="92px">
        <ModuleSchemaForm
          ref="schemaFormRef"
          module-key="workorders"
          :fields="schemaFields"
          :values="form"
          @update:values="schemaPayload = $event"
        />
        <template v-if="false">
        <el-form-item label="报修人">
          <el-input :model-value="currentUsername" disabled/>
        </el-form-item>

        <el-form-item label="标题">
          <el-input v-model="form.title" placeholder="例如：三楼空调不制冷"/>
        </el-form-item>

        <el-form-item label="描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            placeholder="请描述现象、影响范围、出现时间等"
          />
        </el-form-item>

        <el-form-item label="位置">
          <el-input v-model="form.location" placeholder="例如：三楼 308 / B1 车库入口"/>
        </el-form-item>

        <el-form-item label="关联租户">
          <el-select
            v-model="form.tenantId"
            class="form-select"
            clearable
            filterable
            placeholder="可选，涉及收费时建议选择"
          >
            <el-option
              v-for="tenant in tenants"
              :key="tenant.id"
              :label="tenantLabel(tenant)"
              :value="tenant.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="工单类型">
          <el-select v-model="form.orderType" class="form-select" @change="resetCategory">
            <el-option
              v-for="item in orderTypeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="问题类别">
          <el-select v-model="form.category" class="form-select" placeholder="请选择问题类别">
            <el-option
              v-for="item in currentCategoryOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="优先级">
          <el-select v-model="form.priority" class="form-select">
            <el-option
              v-for="item in priorityOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        </template>
        <el-form-item label="关联租户">
          <el-select v-model="form.tenantId" class="form-select" clearable filterable>
            <el-option v-for="tenant in tenants" :key="tenant.id" :label="tenantLabel(tenant)" :value="tenant.id" />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="visible=false">取消</el-button>
        <el-button
          v-if="editingId ? hasPermission(workOrderPermissions.update) : hasPermission(workOrderPermissions.create)"
          type="primary"
          @click="save"
        >保存</el-button>
      </template>
    </el-dialog>

    <WorkOrderDetailDialog
      v-model="detailVisible"
      :fields="detailSchemaFields"
      :values="detailValues"
    />

    <el-dialog
      v-model="assignVisible"
      :title="batchAssignMode ? '批量分配工单' : '派单'"
      width="460px"
    >
      <el-form :model="assignForm" label-width="92px">
        <el-form-item label="工单">
          <el-input :model-value="assignForm.orderTitle" disabled/>
        </el-form-item>
        <el-form-item label="维修人员">
          <el-select
            v-model="assignForm.handlerId"
            class="form-select"
            filterable
            placeholder="请选择维修人员"
          >
            <el-option
              v-for="user in activeUsers"
              :key="user.id"
              :label="userLabel(user)"
              :value="user.id"
            />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="assignVisible=false">取消</el-button>
        <el-button v-if="canAssignWorkOrders" type="primary" @click="confirmAssign">
          {{ batchAssignMode ? '确认批量分配' : '确认派单' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="completeVisible" title="完成工单" width="520px">
      <el-form :model="completeForm" label-width="92px">
        <el-form-item label="工单">
          <el-input :model-value="completeForm.orderTitle" disabled/>
        </el-form-item>
        <el-form-item label="处理结果">
          <el-input
            v-model="completeForm.handlingResult"
            type="textarea"
            :rows="4"
            placeholder="请填写维修处理情况、处理结果或后续建议"
          />
        </el-form-item>
        <el-form-item label="是否收费">
          <el-switch
            v-model="completeForm.billable"
            active-text="向租户收费"
            inactive-text="不收费"
          />
        </el-form-item>
        <template v-if="completeForm.billable">
          <el-form-item label="关联租户">
            <el-input :model-value="tenantText(completeForm.tenantId)" disabled/>
          </el-form-item>
          <el-form-item label="收费金额">
            <el-input-number
              v-model="completeForm.chargeAmount"
              :min="0"
              :precision="2"
              :step="50"
              class="form-select"
            />
          </el-form-item>
          <el-form-item label="收费说明">
            <el-input
              v-model="completeForm.chargeRemark"
              type="textarea"
              :rows="2"
              placeholder="例如：装修垃圾清运费 / 人为损坏维修材料费"
            />
          </el-form-item>
        </template>
      </el-form>

      <template #footer>
        <el-button @click="completeVisible=false">取消</el-button>
        <el-button v-if="canCompleteWorkOrders" type="primary" @click="confirmComplete">确认完成</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="statusDialogVisible" title="调整工单状态" width="520px">
      <el-form :model="statusForm" label-width="92px">
        <el-form-item label="工单">
          <el-input :model-value="statusForm.orderTitle" disabled/>
        </el-form-item>
        <el-form-item label="目标状态">
          <el-select v-model="statusForm.status" class="form-select" placeholder="请选择状态">
            <el-option
              v-for="item in allowedStatusChangeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="处理人">
          <el-select
            v-model="statusForm.handlerId"
            class="form-select"
            clearable
            filterable
            placeholder="退回待派单可留空"
          >
            <el-option
              v-for="user in activeUsers"
              :key="user.id"
              :label="userLabel(user)"
              :value="user.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="调整说明">
          <el-input
            v-model="statusForm.remark"
            type="textarea"
            :rows="3"
            placeholder="例如：返修退回处理中、重新委派维修师傅"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="statusDialogVisible=false">取消</el-button>
        <el-button v-if="canChangeWorkOrderStatus" type="primary" @click="confirmStatusChange">保存</el-button>
      </template>
    </el-dialog>

    <ModuleFieldSettingsDialog v-model="moduleSettingsVisible" module-key="workorders" />
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, RefreshLeft, Search } from '@element-plus/icons-vue'
import request from '../../utils/request'
import { createPagination, pageParams, readPage, resetToFirstPage } from '../../utils/pagination'
import { hasPermission } from '../../utils/permission'
import { buildCsvText } from '../../utils/csvExport'
import { customFieldRecordDisplayValue } from '../../utils/customFieldValues'
import { loadAllWorkspaceRecords } from '../../utils/workspaceExport'
import { downloadBlob } from '../../utils/downloadBlob'
import { loadListTotalResult, loadListTotalsDetailed } from '../../utils/listSummary'
import { useDhicTableView } from '../../composables/useDhicTableView'
import {
  useWorkspaceListRequestState,
  useWorkspaceMetricState
} from '../../composables/workspace/useWorkspaceRequestState'
import CellRenderer from '../../components/CellRenderer.vue'
import DataWorkspace from '../../components/DataWorkspace.vue'
import DhicDataToolbar from '../../components/DhicDataToolbar.vue'
import WorkspaceDataGrid from '../../components/workspace/WorkspaceDataGrid.vue'
import ModuleSchemaForm from '../../components/schema/ModuleSchemaForm.vue'
import ModuleFieldSettingsDialog from '../../components/schema/ModuleFieldSettingsDialog.vue'
import WorkOrderDetailDialog from '../../components/workorder/WorkOrderDetailDialog.vue'
import { getModuleFieldSchema } from '../../api/customFields.js'
import { normalizeModuleSchema } from '../../utils/moduleFieldSchema.js'
import {
  createWorkOrderWorkspaceConfig,
  workOrderCategoryFilterOptions as categoryFilterOptions,
  workOrderCategoryOptions as categoryOptions,
  workOrderPriorityOptions as priorityOptions,
  workOrderTypeOptions as orderTypeOptions
} from '../../workspaceConfigs/workorder'
import { workspaceTableOptions } from '../../workspaceConfigs/workspaceConfig'
const list = ref([])
const users = ref([])
const tenants = ref([])
const inlineSavingCell = ref('')
const visible = ref(false)
const editingId = ref(null)
const detailVisible = ref(false)
const detailSchemaFields = ref([])
const detailValues = ref({})
const moduleSettingsVisible = ref(false)
const schemaFormRef = ref(null)
const schemaFields = ref([])
let schemaPayload = {}
const assignVisible = ref(false)
const batchAssignMode = ref(false)
const completeVisible = ref(false)
const statusDialogVisible = ref(false)
const workOrderTableRef = ref(null)
let pageActive = true
const currentUsername = localStorage.getItem('username') || '当前用户'
const currentUserId = Number(localStorage.getItem('userId'))
const workOrderWorkspaceConfig = createWorkOrderWorkspaceConfig({
  currentUserId,
  handlerOptions: () => handlerFilterOptions.value,
  tenantOptions: () => tenantFilterOptions.value,
  reporterValue: reporterCellValue,
  tenantValue: tenantCellValue,
  handlerValue: handlerCellValue
})
const workOrderPermissions = workOrderWorkspaceConfig.permissions
const tableColumns = workOrderWorkspaceConfig.fields
const statusOptions = workOrderWorkspaceConfig.statuses
const statusChangeOptions = workOrderWorkspaceConfig.statusTransitions
const statusMeta = Object.fromEntries(statusOptions.map(option => [option.value, {
  label: option.label,
  className: option.className,
  type: option.tone
}]))
const canAssignWorkOrders = hasPermission(workOrderPermissions.assign)
const canCompleteWorkOrders = hasPermission(workOrderPermissions.complete)
const canCloseWorkOrders = hasPermission(workOrderPermissions.close)
const canStartWorkOrders = hasPermission(workOrderPermissions.update)
const canGenerateWorkOrderBill = hasPermission(workOrderPermissions.generateBill)
const canChangeWorkOrderStatus = hasPermission(workOrderPermissions.update)
const canViewTenants = hasPermission('tenant:view')
const pagination = reactive(createPagination(20))
const workOrderMetrics = useWorkspaceMetricState([
  'total',
  'filtered',
  'pending',
  'assigned',
  'processing',
  'finished'
])
const workOrderMetricData = workOrderMetrics.values
const workOrderMetricStates = workOrderMetrics.states
const query = reactive({
  keyword: '',
  category: '',
  tenantKeyword: '',
  handlerId: '',
  status: '',
  dateRange: []
})

const allowedStatusChangeOptions = computed(() => statusChangeOptions.filter(item => {
  if (item.value === 'ASSIGNED') return canAssignWorkOrders
  if (item.value === 'COMPLETED') return canCompleteWorkOrders
  if (item.value === 'CLOSED') return canCloseWorkOrders
  return canChangeWorkOrderStatus
}))

const form = reactive({
  title:'',
  description:'',
  location:'',
  tenantId:null,
  orderType:'REPAIR',
  category:'OTHER',
  problemCategory:'OTHER',
  priority:'MEDIUM'
})

const assignForm = reactive({
  orderId: null,
  orderTitle: '',
  handlerId: null
})

const completeForm = reactive({
  orderId: null,
  orderTitle: '',
  tenantId: null,
  handlingResult: '',
  billable: false,
  chargeAmount: 0,
  chargeRemark: ''
})

const statusForm = reactive({
  orderId: null,
  orderTitle: '',
  status: 'SUBMITTED',
  handlerId: null,
  remark: ''
})

const currentCategoryOptions = computed(() => categoryOptions[form.orderType] || [])
const activeUsers = computed(() => users.value)
const handlerFilterOptions = computed(() => activeUsers.value.map(user => ({ label: userLabel(user), value: user.id })))
const tenantFilterOptions = computed(() => tenants.value.map(tenant => ({ label: tenantLabel(tenant), value: tenant.id })))

const optionText = (options, value) => options.find(item => item.value === value)?.label || value || '-'
const orderTypeText = (value) => optionText(orderTypeOptions, value)
const statusText = (value) => statusMeta[value]?.label || value || '-'
const statusTagClass = (value) => ['status-tag', statusMeta[value]?.className || 'status-default']
const statusTagType = (value) => statusMeta[value]?.type || 'info'
const categoryText = (value) => {
  const mobileCategoryOptions = [
    { label: '水路', value: 'WATER' },
    { label: '电路', value: 'ELECTRIC' },
    { label: '空调', value: 'AIR_CONDITIONER' },
    { label: '门窗', value: 'DOOR_WINDOW' },
    { label: '网络', value: 'NETWORK' },
    { label: '保洁', value: 'CLEANING' },
    { label: '其他', value: 'OTHER' }
  ]
  const options = categoryFilterOptions.concat(mobileCategoryOptions)
  return optionText(options, value)
}
const priorityText = (value) => optionText(priorityOptions, value)

const formatDateTime = (value)=>{
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

const reporterText = (row)=>{
  const name = String(row.reporterName || '').trim()
  if (name) return name
  if (row.reporterId === currentUserId) return currentUsername
  if (row.reporterId) return `用户ID ${row.reporterId}`
  return row.mobileUserId ? '小程序用户' : '后台创建'
}

const userLabel = (user)=>{
  const name = user.realName || user.username || `用户${user.id}`
  return user.phone ? `${name}（${user.phone}）` : name
}

const tenantLabel = (tenant)=>{
  const name = tenant.tenantName || tenant.tenantCode || `租户${tenant.id}`
  return tenant.contactPerson ? `${name}（${tenant.contactPerson}）` : name
}

const tenantText = (tenantId)=>{
  if (!tenantId) return '-'
  const tenant = tenants.value.find(item => Number(item.id) === Number(tenantId))
  return tenant ? tenantLabel(tenant) : `租户ID ${tenantId}`
}

const handlerText = (handlerId)=>{
  if (!handlerId) return '-'
  const user = users.value.find(item => Number(item.id) === Number(handlerId))
  return user ? userLabel(user) : `用户ID ${handlerId}`
}

function reporterCellValue(row) {
  return {
    id: row.reporterId || row.mobileUserId || reporterText(row),
    name: reporterText(row)
  }
}

function tenantCellValue(row) {
  if (!row.tenantId && !row.tenantName) return null
  return {
    id: row.tenantId || row.tenantName,
    label: row.tenantName || tenantText(row.tenantId),
    entityType: 'tenant'
  }
}

function handlerCellValue(row) {
  if (!row.handlerId && !row.handlerName) return null
  return {
    id: row.handlerId || row.handlerName,
    name: row.handlerName || handlerText(row.handlerId)
  }
}

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
  selectedRows,
  selectedRowKeys,
  handleSelectionChange,
  clearSelection: clearWorkspaceSelection,
  sortOptions,
  groupOptions,
  filterOptions,
  filterLogicOptions,
  serverQueryParams,
  queryNotices,
  queryRequestGuard: listRequestGuard,
  debouncedQueryTask: debouncedViewQueryLoad,
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
} = useDhicTableView(workspaceTableOptions(workOrderWorkspaceConfig, {
  records: () => list.value,
  serverTotal: () => pagination.total,
  queryLifecycle: {
    task: () => {
      resetToFirstPage(pagination)
      load()
    }
  },
  selection: {
    rowKey: 'id',
    selectable: selectableWorkOrderRow
  },
  filters: () => query.keyword ? [{ label: '关键词', value: query.keyword }] : [],
  groupLabel: value => {
    if (statusMeta[value]) return statusText(value)
    if (priorityOptions.some(item => item.value === value)) return priorityText(value)
    if (orderTypeOptions.some(item => item.value === value)) return orderTypeText(value)
    if (categoryFilterOptions.some(item => item.value === value)) return categoryText(value)
    return value || '未填写'
  }
}))

const canOperateAssignedWorkOrder = (row)=>{
  return canCompleteWorkOrders
}

const primaryWorkOrderAction = (row) => {
  if (canAssignWorkOrders && (row.status === 'CREATED' || row.status === 'SUBMITTED')) return { id: 'assign', label: '派单', type: 'primary' }
  if (canStartWorkOrders && row.status === 'ASSIGNED') return { id: 'start', label: '开始', type: 'primary' }
  if (canOperateAssignedWorkOrder(row) && row.status === 'PROCESSING') return { id: 'complete', label: '完成', type: 'success' }
  if (canGenerateWorkOrderBill && row.status === 'COMPLETED' && row.billable && !row.billId) return { id: 'bill', label: '生成账单', type: 'warning' }
  if (canCloseWorkOrders && row.status === 'COMPLETED') return { id: 'close', label: '关闭', type: 'info' }
  return null
}

function secondaryWorkOrderActions(row) {
  const actions = []
  if (canChangeWorkOrderStatus && !['CLOSED', 'CANCELLED'].includes(row.status)) {
    actions.push({ id: 'adjust', label: '调整状态' })
  }
  if (canGenerateWorkOrderBill && row.status === 'COMPLETED' && row.billable && !row.billId) {
    actions.push({ id: 'bill', label: '生成账单' })
  }
  if (canCloseWorkOrders && row.status === 'COMPLETED') {
    actions.push({ id: 'close', label: '关闭' })
  }
  const primaryId = primaryWorkOrderAction(row)?.id
  return actions.filter(action => action.id !== primaryId)
}

const runPrimaryWorkOrderAction = (row) => {
  const action = primaryWorkOrderAction(row)?.label
  if (action === '派单') assign(row)
  else if (action === '开始') start(row)
  else if (action === '完成') complete(row)
  else if (action === '生成账单') generateBill(row)
  else if (action === '关闭') close(row)
}

const handleWorkOrderCommand = (command, row) => {
  if (command === 'adjust') adjustStatus(row)
  else if (command === 'bill') generateBill(row)
  else if (command === 'close') close(row)
}

const workOrderRowClassName = ({row}) => {
  if (row.slaOverdue) return 'sla-overdue-row'
  if (row.status === 'COMPLETED') return 'row-done'
  if (row.status === 'CLOSED') return 'row-muted'
  return ''
}

function clearWorkOrderSelection() {
  workOrderTableRef.value?.clearSelection()
  clearWorkspaceSelection()
}

function runWorkOrderBatchAction(actionId) {
  if (actionId === 'assign') openBatchAssign()
  else if (actionId === 'export') batchExportWorkOrders()
  else if (actionId === 'close') batchCloseWorkOrders()
}

function handleWorkOrderTransferAction(actionId) {
  if (actionId === 'export-current') exportCurrentPageWorkOrders()
}

function selectableWorkOrderRow(row) {
  return !row?.__group
}

async function switchWorkOrderView(viewId) {
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
  clearWorkOrderSelection()
}

function saveWorkOrderView() {
  saveCurrentView()
  ElMessage.success(`视图“${activeViewName.value}”已保存`)
}

function removeWorkOrderViewFilter(index) {
  removeViewFilter(index)
}

function updateWorkOrderFilters(filters) {
  updateFilters(filters)
}

function resetWorkOrderView() {
  resetTableView()
  clearWorkOrderSelection()
}

const search = () => {
  resetToFirstPage(pagination)
  load()
}

const resetQuery = () => {
  Object.assign(query, {
    keyword: '',
    category: '',
    tenantKeyword: '',
    handlerId: '',
    status: '',
    dateRange: []
  })
  search()
}

const load = async ()=>{
  const requestId = listRequestGuard.start()
  const params = buildQueryParams()
  const requestContext = workOrderListState.begin({ viewId: activeViewId.value, params })
  const metricRevision = workOrderMetrics.begin()
  const listTask = request.get('/workorders', { params, silent: true }).then(
    value => applyWorkOrderListResult(requestId, requestContext, metricRevision, { status: 'fulfilled', value }),
    reason => applyWorkOrderListResult(requestId, requestContext, metricRevision, { status: 'rejected', reason })
  )
  const metricsTask = Promise.allSettled([
    loadListTotalResult('/workorders'),
    fetchSummary()
  ]).then(([totalResult, statsResult]) => {
    if (!listRequestGuard.isCurrent(requestId) || !workOrderListState.isCurrent(requestContext)) return
    applyWorkOrderTotalMetric(metricRevision, totalResult)
    applyWorkOrderSummaryMetrics(metricRevision, statsResult)
  })

  await Promise.allSettled([listTask, metricsTask])
}

function applyWorkOrderListResult(requestId, requestContext, metricRevision, result) {
  if (!listRequestGuard.isCurrent(requestId) || !workOrderListState.isCurrent(requestContext)) return
  if (result.status === 'fulfilled') {
    const page = readPage(result.value)
    list.value = page.records
    pagination.total = page.total
    clearWorkspaceSelection()
    workOrderListState.succeed(requestContext)
    workOrderMetrics.resolve(metricRevision, 'filtered', page.total)
  } else {
    workOrderListState.fail(requestContext, '工单列表加载失败，请稍后重试')
    workOrderMetrics.reject(metricRevision, 'filtered')
  }
}

function buildQueryParams(overrides = {}) {
  return {
    ...pageParams(pagination),
    ...buildSummaryParams(),
    status: serverQueryParams.value.status || query.status || undefined,
    sorts: serverQueryParams.value.sorts || undefined,
    groupBy: serverQueryParams.value.groupBy || undefined,
    ...overrides
  }
}

function buildSummaryParams(overrides = {}) {
  return {
    keyword: serverQueryParams.value.keyword || query.keyword || undefined,
    category: serverQueryParams.value.category || query.category || undefined,
    tenantId: serverQueryParams.value.tenantId || undefined,
    tenantKeyword: query.tenantKeyword || undefined,
    handlerId: serverQueryParams.value.handlerId || query.handlerId || undefined,
    startTime: serverQueryParams.value.startTime || query.dateRange?.[0] || undefined,
    endTime: serverQueryParams.value.endTime || query.dateRange?.[1] || undefined,
    customFilters: serverQueryParams.value.customFilters || undefined,
    ...overrides
  }
}

watch(() => JSON.stringify(serverQueryParams.value), () => debouncedViewQueryLoad.schedule())

function fetchSummary() {
  return loadListTotalsDetailed([
    { key: 'created', url: '/workorders', params: buildSummaryParams({ status: 'CREATED' }) },
    { key: 'submitted', url: '/workorders', params: buildSummaryParams({ status: 'SUBMITTED' }) },
    { key: 'assigned', url: '/workorders', params: buildSummaryParams({ status: 'ASSIGNED' }) },
    { key: 'processing', url: '/workorders', params: buildSummaryParams({ status: 'PROCESSING' }) },
    { key: 'completed', url: '/workorders', params: buildSummaryParams({ status: 'COMPLETED' }) },
    { key: 'closed', url: '/workorders', params: buildSummaryParams({ status: 'CLOSED' }) }
  ])
}

const workOrderListState = useWorkspaceListRequestState({
  hasResults: () => list.value.length > 0,
  clearResults: () => {
    list.value = []
    pagination.total = 0
    clearWorkspaceSelection()
  }
})
const loading = computed(() => workOrderListState.status.value === 'loading')
const listErrorText = workOrderListState.errorText

function applyWorkOrderTotalMetric(revision, result) {
  const detail = result.status === 'fulfilled' ? result.value : null
  if (detail?.ok) workOrderMetrics.resolve(revision, 'total', detail.value)
  else workOrderMetrics.reject(revision, 'total')
}

function applyWorkOrderSummaryMetrics(revision, result) {
  const details = result.status === 'fulfilled' ? result.value : {}
  resolveCombinedWorkOrderMetric(revision, 'pending', details, ['created', 'submitted'])
  resolveCombinedWorkOrderMetric(revision, 'finished', details, ['completed', 'closed'])
  for (const key of ['assigned', 'processing']) {
    if (details[key]?.ok) workOrderMetrics.resolve(revision, key, details[key].value)
    else workOrderMetrics.reject(revision, key)
  }
}

function resolveCombinedWorkOrderMetric(revision, metricKey, details, sourceKeys) {
  if (sourceKeys.every(key => details[key]?.ok)) {
    const value = sourceKeys.reduce((total, key) => total + Number(details[key].value || 0), 0)
    workOrderMetrics.resolve(revision, metricKey, value)
  } else {
    workOrderMetrics.reject(revision, metricKey)
  }
}

const handleSizeChange = () => {
  resetToFirstPage(pagination)
  load()
}

const loadUsers = async ()=>{
  if (!canAssignWorkOrders) {
    users.value = []
    return
  }
  const data = await request.get('/workorders/assignable-users')
  if (pageActive) users.value = data || []
}

const loadTenants = async ()=>{
  if (!canViewTenants) {
    tenants.value = []
    return
  }
  const data = await request.get('/tenant/list')
  if (pageActive) tenants.value = data || []
}

const resetCategory = ()=>{
  form.category = currentCategoryOptions.value[0]?.value || ''
}

const openDialog = async ()=>{
  editingId.value = null
  form.title=''
  form.description=''
  form.location=''
  form.tenantId=null
  form.orderType='REPAIR'
  form.category='OTHER'
  form.problemCategory='OTHER'
  form.priority='NORMAL'
  if (!schemaFields.value.length) {
    schemaFields.value = normalizeModuleSchema(await getModuleFieldSchema('workorders', 'adminForm'))
  }
  visible.value = true
}

const openEdit = async (row) => {
  if (!row?.id) return
  if (!schemaFields.value.length) {
    schemaFields.value = normalizeModuleSchema(await getModuleFieldSchema('workorders', 'adminForm'))
  }
  const detail = await request.get(`/workorders/${row.id}`, { silent: true })
  editingId.value = row.id
  Object.assign(form, {
    ...detail,
    problemCategory: detail.problemCategory || detail.category || 'OTHER',
    tenantId: detail.tenantId ?? null,
    customFields: detail.customFields || {}
  })
  visible.value = true
}

const openDetail = async (row) => {
  if (!row?.id) return
  if (!detailSchemaFields.value.length) {
    detailSchemaFields.value = normalizeModuleSchema(await getModuleFieldSchema('workorders', 'adminDetail'))
  }
  detailValues.value = await request.get(`/workorders/${row.id}`, { silent: true })
  detailVisible.value = true
}

const save = async ()=>{
  if (!schemaFormRef.value?.validate()) return
  schemaPayload = schemaFormRef.value.getPayload()

  const payload = {
    ...schemaPayload,
    tenantId: form.tenantId ? Number(form.tenantId) : null,
  }
  if (editingId.value) await request.put(`/workorders/${editingId.value}`, payload)
  else await request.post('/workorders', payload)

  ElMessage.success(editingId.value ? '工单更新成功' : '工单创建成功')
  visible.value = false
  editingId.value = null
  load()
}

function inlineCellKey(row, column) {
  return `${row.id}:${column.key}`
}

async function saveInlineWorkOrderCell(row, column, value) {
  if (column.key !== 'status' || value === row.status) return { success: true }
  if (!canUseWorkOrderStatus(value)) return { success: false, message: '当前账号没有执行该状态操作的权限' }
  if (value === 'ASSIGNED' && !row.handlerId) {
    adjustStatus(row)
    statusForm.status = 'ASSIGNED'
    ElMessage.warning('派单状态需要同时指定负责人')
    return { success: false, message: '派单状态需要同时指定负责人' }
  }

  inlineSavingCell.value = inlineCellKey(row, column)
  try {
    await request.patch(`/workorders/${row.id}/status`, {
      status: value,
      handlerId: row.handlerId || null,
      remark: '数据工作台状态调整'
    })
    ElMessage.success('工单状态已更新')
    await load()
    return { success: true }
  } catch (_error) {
    const message = '工单状态更新失败，请稍后重试'
    ElMessage.error(message)
    return { success: false, message }
  } finally {
    inlineSavingCell.value = ''
  }
}

function canEditWorkOrderCell(_row, field) {
  return field.key === 'status' && canChangeWorkOrderStatus
}

async function handleWorkOrderCellSave({ row, field, value, complete }) {
  complete(await saveInlineWorkOrderCell(row, field, value))
}

function canUseWorkOrderStatus(status, showMessage = true) {
  const permission = status === 'ASSIGNED'
    ? canAssignWorkOrders
    : status === 'COMPLETED'
      ? canCompleteWorkOrders
      : status === 'CLOSED'
        ? canCloseWorkOrders
        : canChangeWorkOrderStatus

  if (!permission && showMessage) ElMessage.warning('当前账号没有执行该状态操作的权限')
  return permission
}

async function exportCurrentPageWorkOrders() {
  try {
    const rows = await loadAllWorkspaceRecords(({ page, pageSize }) => request.get('/workorders', {
      params: buildQueryParams({ page, pageSize }),
      silent: true
    }))
    if (!rows.length) {
      ElMessage.warning('当前查询没有可导出的工单')
      return
    }
    exportWorkOrderRows(rows, `工单管理-当前查询-${localDateText(new Date())}.csv`)
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || '工单导出失败')
  }
}

function batchExportWorkOrders() {
  const rows = selectedRows.value.filter(selectableWorkOrderRow)
  if (!rows.length) {
    ElMessage.warning('请先选择要导出的工单')
    return
  }
  exportWorkOrderRows(rows, `工单管理-已选-${localDateText(new Date())}.csv`)
}

function exportWorkOrderRows(rows, fileName) {
  const columns = [
    { label: '序号', valueGetter: (_row, index) => index + 1 },
    ...visibleOrderedColumns.value.map(column => ({
      label: column.label,
      valueGetter: row => workOrderExportValue(row, column)
    }))
  ]
  const csv = buildCsvText(rows, columns)
  downloadBlob(new Blob(['\uFEFF', csv], { type: 'text/csv;charset=utf-8' }), fileName)
  ElMessage.success(`已导出 ${rows.length} 条工单`)
}

function workOrderExportValue(row, column) {
  if (column.custom) return customFieldRecordDisplayValue(row, column)
  if (column.key === 'status') return statusText(row.status)
  if (column.key === 'priority') return priorityText(row.priority)
  if (column.key === 'category') return categoryText(row.category)
  if (column.key === 'orderType') return orderTypeText(row.orderType)
  if (column.key === 'reporter') return reporterText(row)
  if (column.key === 'tenant') return row.tenantName || tenantText(row.tenantId)
  if (column.key === 'handler') return row.handlerName || handlerText(row.handlerId)
  if (['createdTime', 'updatedTime'].includes(column.key)) return formatDateTime(row[column.key])
  return row[column.key] ?? '-'
}

const assign = (row)=>{
  batchAssignMode.value = false
  assignForm.orderId = row.id
  assignForm.orderTitle = `${row.orderNumber} ${row.title}`
  assignForm.handlerId = row.handlerId || null
  assignVisible.value = true
}

function openBatchAssign() {
  const selected = selectedRows.value.filter(selectableWorkOrderRow)
  const rows = selected.filter(row => ['CREATED', 'SUBMITTED'].includes(row.status))
  if (!rows.length) {
    ElMessage.warning('请选择待处理工单进行批量分配')
    return
  }
  batchAssignMode.value = true
  assignForm.orderId = null
  assignForm.orderTitle = `可分配 ${rows.length} 条${selected.length > rows.length ? `，跳过 ${selected.length - rows.length} 条其他状态` : ''}`
  assignForm.handlerId = null
  assignVisible.value = true
}

const confirmAssign = async()=>{
  if (!assignForm.handlerId) {
    ElMessage.warning('请选择维修人员')
    return
  }

  if (batchAssignMode.value) {
    const rows = selectedRows.value.filter(row => selectableWorkOrderRow(row) && ['CREATED', 'SUBMITTED'].includes(row.status))
    const results = await Promise.allSettled(rows.map(row => request.patch(`/workorders/${row.id}/assign`, {
      handlerId: Number(assignForm.handlerId)
    })))
    const successCount = results.filter(result => result.status === 'fulfilled').length
    const failureCount = results.length - successCount
    if (failureCount === 0) ElMessage.success(`已分配 ${successCount} 条工单`)
    else if (successCount > 0) ElMessage.warning(`已分配 ${successCount} 条，失败 ${failureCount} 条`)
    else ElMessage.error('批量分配失败')
  } else {
    await request.patch(`/workorders/${assignForm.orderId}/assign`, {handlerId:Number(assignForm.handlerId)})
    ElMessage.success('已派单')
  }
  assignVisible.value = false
  clearWorkOrderSelection()
  await load()
}

async function batchCloseWorkOrders() {
  const selected = selectedRows.value.filter(selectableWorkOrderRow)
  const rows = selected.filter(row => row.status === 'COMPLETED')
  if (!rows.length) {
    ElMessage.warning('所选工单中没有可关闭的已完成工单')
    return
  }

  const skipped = selected.length - rows.length
  try {
    await ElMessageBox.confirm(
      `确认关闭 ${rows.length} 条已完成工单${skipped ? `，并跳过 ${skipped} 条其他状态工单` : ''}？`,
      '批量关闭工单',
      { type: 'warning', confirmButtonText: '关闭', cancelButtonText: '取消' }
    )
    const results = await Promise.allSettled(rows.map(row => request.patch(`/workorders/${row.id}/close`)))
    const successCount = results.filter(result => result.status === 'fulfilled').length
    const failureCount = results.length - successCount
    if (failureCount === 0) ElMessage.success(`已关闭 ${successCount} 条工单`)
    else if (successCount > 0) ElMessage.warning(`已关闭 ${successCount} 条，失败 ${failureCount} 条`)
    else ElMessage.error('批量关闭失败')
    clearWorkOrderSelection()
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error?.response?.data?.message || '批量关闭失败')
    }
  }
}

const start = async(row)=>{
  await request.patch(`/workorders/${row.id}/start`)
  ElMessage.success('已开始')
  load()
}

const complete = (row)=>{
  completeForm.orderId = row.id
  completeForm.orderTitle = `${row.orderNumber} ${row.title}`
  completeForm.tenantId = row.tenantId || null
  completeForm.handlingResult = row.handlingResult || ''
  completeForm.billable = Boolean(row.billable)
  completeForm.chargeAmount = Number(row.chargeAmount || 0)
  completeForm.chargeRemark = row.chargeRemark || ''
  completeVisible.value = true
}

const adjustStatus = (row)=>{
  statusForm.orderId = row.id
  statusForm.orderTitle = `${row.orderNumber} ${row.title}`
  statusForm.status = row.status === 'CREATED' ? 'SUBMITTED' : row.status
  statusForm.handlerId = row.handlerId || row.assigneeId || null
  statusForm.remark = ''
  statusDialogVisible.value = true
}

const confirmComplete = async()=>{
  if (!completeForm.handlingResult.trim()) {
    ElMessage.warning('请填写处理结果')
    return
  }
  if (completeForm.billable && !completeForm.tenantId) {
    ElMessage.warning('向租户收费的工单需先关联租户')
    return
  }
  if (completeForm.billable && Number(completeForm.chargeAmount) <= 0) {
    ElMessage.warning('请输入大于0的收费金额')
    return
  }
  await request.patch(`/workorders/${completeForm.orderId}/complete`, {
    result: completeForm.handlingResult,
    handlingResult: completeForm.handlingResult,
    billable: completeForm.billable,
    chargeAmount: completeForm.billable ? Number(completeForm.chargeAmount) : null,
    chargeRemark: completeForm.billable ? completeForm.chargeRemark : null
  })
  ElMessage.success('已完成')
  completeVisible.value = false
  load()
}

const confirmStatusChange = async()=>{
  if (!statusForm.status) {
    ElMessage.warning('请选择目标状态')
    return
  }
  if (!canUseWorkOrderStatus(statusForm.status)) return
  if (statusForm.status === 'ASSIGNED' && !statusForm.handlerId) {
    ElMessage.warning('派单状态必须指定维修人员')
    return
  }
  await request.patch(`/workorders/${statusForm.orderId}/status`, {
    status: statusForm.status,
    handlerId: statusForm.handlerId ? Number(statusForm.handlerId) : null,
    remark: statusForm.remark
  })
  ElMessage.success('工单状态已更新')
  statusDialogVisible.value = false
  load()
}

const close = async(row)=>{
  await request.patch(`/workorders/${row.id}/close`)
  ElMessage.success('已关闭')
  load()
}

const generateBill = async(row)=>{
  await request.post(`/workorders/${row.id}/generate-bill`)
  ElMessage.success('账单已生成')
  load()
}

onMounted(()=>{
  load()
  loadUsers()
  loadTenants()
})

onBeforeUnmount(() => {
  pageActive = false
  disposeQueryLifecycle()
})
</script>

<style scoped>
.page-container{
  padding: 0;
}

.page-container :deep(.data-workspace) {
  border-color: #dfe4eb;
  border-radius: 10px;
  box-shadow: 0 10px 28px rgba(31, 45, 61, 0.06);
}

.page-container :deep(.workspace-header) {
  min-height: 72px;
  padding: 12px 16px;
  border-bottom-color: #e8ecf2;
  background:
    linear-gradient(90deg, rgba(217, 48, 37, 0.035), transparent 260px),
    #fff;
}

.page-container :deep(.workspace-title-block h2) {
  font-size: 19px;
  line-height: 1.25;
}

.page-container :deep(.workspace-title-block p) {
  margin-top: 5px;
  color: #8490a2;
}

.page-container :deep(.workspace-view-manager) {
  padding: 4px 6px;
  border: 1px solid #edf0f4;
  border-radius: 7px;
  background: #fafbfc;
}

.page-container :deep(.workspace-metrics) {
  padding: 10px 12px 0;
}

.page-container :deep(.list-summary-grid) {
  gap: 8px;
  margin-bottom: 10px;
}

.page-container :deep(.list-summary-card) {
  min-height: 70px;
  padding: 9px 12px 9px 14px;
  border-radius: 7px;
  box-shadow: none;
}

.page-container :deep(.list-summary-card::before) {
  top: 10px;
  bottom: 10px;
}

.page-container :deep(.summary-value) {
  margin: 3px 0 1px;
  font-size: 21px;
}

.page-container :deep(.workspace-toolbar) {
  padding: 0 12px;
}

.page-container :deep(.dhic-data-toolbar) {
  min-height: 42px;
  margin-bottom: 8px;
  padding: 6px 8px;
  border-radius: 7px;
  box-shadow: none;
}

.page-container :deep(.workspace-grid) {
  border-top: 1px solid #edf0f4;
}

.page-container :deep(.workspace-data-grid .el-table__header-wrapper th.el-table__cell) {
  height: 42px;
  background: #f7f9fc;
  color: #4c596d;
  font-weight: 600;
}

.page-container :deep(.workspace-data-grid .el-table__body td.el-table__cell) {
  border-bottom-color: #edf0f4;
}

.page-container :deep(.workspace-footer) {
  padding: 10px 14px 12px;
  border-top: 1px solid #edf0f4;
  background: #fbfcfd;
}

.view-hint {
  color: #7b8494;
  font-size: 12px;
  white-space: nowrap;
}

.workspace-quick-search {
  width: 260px;
}

.relation-action-cell {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  min-width: 0;
}

.form-select{
  width:100%;
}

.action-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 4px 8px;
  min-height: 32px;
}

.action-cell :deep(.el-button + .el-button) {
  margin-left: 0;
}

:deep(.sla-overdue-row){
  background:#fff1f0;
}

:deep(.sla-overdue-row:hover > td.el-table__cell){
  background:#ffe7e6 !important;
}

:deep(.row-done) {
  --el-table-tr-bg-color: #f6fcf8;
}

:deep(.row-muted) {
  --el-table-tr-bg-color: #f7f8fa;
  color: #606266;
}

.workorder-status-cell {
  display: inline-flex;
  align-items: center;
  min-width: 0;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 0;
}

@media (max-width: 720px) {
  .page-container {
    padding: 0;
  }

  .workspace-quick-search {
    width: min(100%, 250px);
  }
}
</style>
