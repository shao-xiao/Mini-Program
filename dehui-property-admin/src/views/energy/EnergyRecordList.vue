<template>
  <div class="page-container energy-record-page" :class="{ 'energy-record-page--embedded': props.embedded }">
    <DataWorkspace
      :workspace-config="energyReadingWorkspaceConfig"
      :views="workspaceViews"
      :active-view-id="activeViewId"
      :view-dirty="isViewDirty"
      :filter-logic="tablePrefs.filterLogic"
      :filter-logic-visible="tablePrefs.filters.length > 1"
      :filter-items="activeFilterItems"
      :query-notices="queryNotices"
      :metric-data="energyReadingMetricData"
      :metric-states="energyReadingMetricStates"
      @change-view="switchEnergyReadingView"
      @create-view="createTableView"
      @duplicate-view="duplicateTableView"
      @rename-view="renameTableView"
      @delete-view="deleteTableView"
      @save-view="saveEnergyReadingView"
      @remove-filter="removeEnergyReadingViewFilter"
    >
      <template #header-actions>
        <el-button v-if="!props.embedded && hasPermission(energyReadingMetersPermission)" @click="router.push('/energy/meters')">
          {{ energyReadingDataActions.meters.label }}
        </el-button>
        <el-button
          v-if="hasPermission(energyReadingPermissions.create)"
          type="primary"
          :icon="Plus"
          @click="openCreateDialog"
        >
          {{ energyReadingWorkspaceConfig.primaryAction.label }}
        </el-button>
      </template>

      <template #toolbar>
        <DhicDataToolbar
          :workspace-config="energyReadingWorkspaceConfig"
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
          @update:filters="updateEnergyReadingFilters"
          @update:filter-logic="updateFilterLogic"
          @update:sort-key="updateSortKey"
          @update:sort-order="updateSortOrder"
          @update:sorts="updateSorts"
          @update:group-key="updateGroupKey"
          @update:groups="updateGroups"
          @update:row-density="updateRowDensity"
          @reset-view="resetEnergyReadingView"
          @save-field-config="saveEnergyReadingView"
          @workspace-action="handleEnergyReadingTransferAction"
          @workspace-file="handleImportReadingFile"
        >
          <template #data-actions>
            <el-button v-if="hasPermission(energyReadingPermissions.price)" @click="openPriceDialog">
              {{ energyReadingDataActions.price.label }}
            </el-button>
            <el-button v-if="hasPermission(energyReadingPermissions.create)" @click="openMonthlyTaskDialog">
              {{ energyReadingDataActions.monthlyTask.label }}
            </el-button>
          </template>
          <template #right>
            <el-input
              v-model.trim="queryForm.meterNo"
              clearable
              :prefix-icon="Search"
              class="workspace-quick-search"
              placeholder="搜索表具编号"
              @keyup.enter="handleSearch"
              @clear="handleSearch"
            />
            <el-tooltip content="清除快速查询" placement="bottom">
              <el-button text circle :icon="RefreshLeft" aria-label="清除快速查询" @click="resetSearch" />
            </el-tooltip>
            <span class="view-hint">当前页 {{ resultScope.loadedRecords }} 条</span>
          </template>
        </DhicDataToolbar>

        <div class="period-toolbar">
          <div class="period-toolbar-main">
            <span class="period-label">当前账期</span>
            <strong>{{ activePeriodLabel }}</strong>
            <span class="period-scope-reminder" role="status" aria-live="polite">
              <template v-if="isViewingAllPeriods">当前为全部历史，列表包含多个账期</template>
              <template v-else>当前仅显示 {{ activePeriodLabel }} 账期</template>
            </span>
          </div>
          <div class="period-actions">
            <el-button
              size="small"
              :type="isViewingPreviousMonth ? 'primary' : 'default'"
              :plain="isViewingPreviousMonth"
              :aria-pressed="isViewingPreviousMonth"
              @click="shiftQueryPeriod(-1)"
            >
              上月
            </el-button>
            <el-button
              size="small"
              :type="isViewingCurrentMonth ? 'primary' : 'default'"
              :plain="isViewingCurrentMonth"
              :aria-pressed="isViewingCurrentMonth"
              @click="setQueryPeriod(currentMonth())"
            >
              本月
            </el-button>
            <el-button
              size="small"
              :type="isViewingNextMonth ? 'primary' : 'default'"
              :plain="isViewingNextMonth"
              :aria-pressed="isViewingNextMonth"
              @click="shiftQueryPeriod(1)"
            >
              下月
            </el-button>
            <el-button
              size="small"
              :type="isViewingAllPeriods ? 'primary' : 'default'"
              :plain="isViewingAllPeriods"
              :aria-pressed="isViewingAllPeriods"
              @click="clearQueryPeriod"
            >
              全部历史
            </el-button>
          </div>
        </div>
      </template>

      <template #grid>
        <div ref="tableWrapRef" class="energy-record-table-wrap">
          <WorkspaceDataGrid
            :loading="loading"
            :error-text="listErrorText"
            retryable
            :rows="tableRows"
            :columns="visibleOrderedColumns"
            row-key="id"
            :height="tableHeight"
            :current-page="pagination.currentPage"
            :page-size="pagination.pageSize"
            :index-width="72"
            :density="tablePrefs.rowDensity"
            :empty-text="tableEmptyText"
            :sort-state="activeTableSort"
            :data-mode="energyReadingWorkspaceConfig.query.dataMode"
            table-class="record-table"
            :row-class-name="recordRowClassName"
            :column-width="columnWidth"
            :column-fixed="columnFixed"
            :is-column-sortable="isColumnSortable"
            :show-overflow-tooltip="column => ['buildingName', 'roomName', 'tenantName', 'remark'].includes(column.key)"
            :operation-column="{ width: 156, fixed: 'right', align: 'center' }"
            @column-resize="handleHeaderDragEnd"
            @sort-change="handleTableSortChange"
            @retry="loadRecords"
            aria-label="抄表管理数据表格"
          >
            <template #operations="{ row }">
              <div class="action-inline">
                <el-button
                  v-if="props.allowBillingActions && row.billStatus === 'NOT_GENERATED' && hasPermission(energyReadingPermissions.generateBill)"
                  size="small"
                  type="primary"
                  @click="generateBill(row)"
                >
                  确认费用依据
                </el-button>
                <el-button
                  v-else-if="props.allowBillingActions && row.billStatus === 'GENERATED' && hasPermission(energyReadingPermissions.update)"
                  size="small"
                  type="success"
                  @click="markPosted(row)"
                >
                  标记入账
                </el-button>
                <el-button v-else-if="row.billId && canViewBills" size="small" @click="viewBill(row)">查看账单</el-button>
                <el-button v-else-if="hasPermission(energyReadingPermissions.update) && row.billStatus === 'NOT_GENERATED'" size="small" @click="openEditDialog(row)">编辑</el-button>
                <el-dropdown trigger="click" @command="command => handleRowCommand(command, row)">
                  <el-button size="small">更多</el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item v-if="canViewBills" command="view" :disabled="!row.billId">查看账单</el-dropdown-item>
                      <el-dropdown-item v-if="hasPermission(energyReadingPermissions.update)" command="edit" :disabled="row.billStatus !== 'NOT_GENERATED'">编辑</el-dropdown-item>
                      <el-dropdown-item v-if="props.allowBillingActions && hasPermission(energyReadingPermissions.update)" command="withdraw" :disabled="!row.billId || row.billStatus === 'NOT_GENERATED'">撤回账单</el-dropdown-item>
                      <el-dropdown-item v-if="hasPermission(energyReadingPermissions.delete)" command="delete" :disabled="row.billStatus !== 'NOT_GENERATED'">删除</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </template>
          </WorkspaceDataGrid>
        </div>
      </template>

      <template #footer>
        <div class="pagination-wrapper">
          <el-pagination
            v-model:current-page="pagination.currentPage"
            v-model:page-size="pagination.pageSize"
            :total="pagination.total"
            :page-sizes="[20, 50, 100, 200]"
            layout="total, sizes, prev, pager, next, jumper"
            @current-change="loadRecords"
            @size-change="handleSizeChange"
          />
        </div>
      </template>
    </DataWorkspace>

    <el-dialog v-model="priceDialogVisible" title="设置账期单价" width="560px" destroy-on-close>
      <el-form :model="priceForm" label-width="110px">
        <el-form-item label="账期" required>
          <el-date-picker
            v-model="priceForm.periodMonth"
            type="month"
            value-format="YYYY-MM"
            style="width: 100%"
            @change="loadPriceSettings"
          />
        </el-form-item>
        <div class="price-grid">
          <el-form-item label="电表单价" required>
            <el-input-number v-model="priceForm.electricPrice" :min="0" :precision="4" :step="0.1" style="width: 100%" />
          </el-form-item>
          <el-form-item label="水表单价" required>
            <el-input-number v-model="priceForm.waterPrice" :min="0" :precision="4" :step="0.1" style="width: 100%" />
          </el-form-item>
          <el-form-item label="燃气单价" required>
            <el-input-number v-model="priceForm.gasPrice" :min="0" :precision="4" :step="0.1" style="width: 100%" />
          </el-form-item>
        </div>
        <div class="dialog-tip">
          保存后会同步更新该账期内未生成账单的抄表金额；已生成或已入账的账单不会被自动改动，需要先撤回后再调整。
        </div>
      </el-form>

      <template #footer>
        <el-button @click="priceDialogVisible = false">取消</el-button>
        <el-button v-if="hasPermission(energyReadingPermissions.price)" type="primary" :loading="priceSaving" @click="submitPriceSettings">保存单价</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="taskDialogVisible" title="生成本月抄表任务" width="560px" destroy-on-close>
      <el-form :model="taskForm" label-width="110px">
        <el-form-item label="账期" required>
          <el-date-picker v-model="taskForm.periodMonth" type="month" value-format="YYYY-MM" style="width: 100%" />
        </el-form-item>
        <el-form-item label="抄表日期" required>
          <el-date-picker v-model="taskForm.readingDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="能耗类型">
          <el-select v-model="taskForm.meterType" clearable placeholder="全部类型" style="width: 100%">
            <el-option v-for="item in meterTypes" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="楼宇">
          <el-select v-model="taskForm.buildingId" clearable placeholder="全部楼宇" style="width: 100%" @change="onTaskBuildingChange">
            <el-option v-for="item in buildings" :key="item.id" :label="item.buildingName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="楼层">
          <el-select v-model="taskForm.floorId" clearable placeholder="全部楼层" style="width: 100%" :disabled="!taskForm.buildingId">
            <el-option v-for="item in taskFloors" :key="item.id" :label="formatFloor(item)" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="默认单价">
          <el-input-number v-model="taskForm.unitPrice" :min="0" :precision="4" :step="0.1" style="width: 100%" />
          <div class="field-tip">没有设置账期单价时使用此兜底值</div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="taskDialogVisible = false">取消</el-button>
        <el-button v-if="hasPermission(energyReadingPermissions.create)" type="primary" :loading="taskSaving" @click="submitMonthlyTask">生成任务</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑抄表' : '新增抄表'" width="680px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="能耗类型">
          <el-segmented
            v-model="form.meterType"
            :options="meterTypeOptions"
            :disabled="Boolean(form.id)"
            @change="handleDialogMeterTypeChange"
          />
        </el-form-item>
        <el-form-item label="表具" prop="meterId">
          <el-select
            v-model="form.meterId"
            filterable
            remote
            reserve-keyword
            :remote-method="searchMeters"
            :loading="meterLoading"
            style="width: 100%"
            placeholder="输入表号、表名、房间或租户搜索"
            @change="handleMeterChange"
          >
            <el-option
              v-for="meter in meters"
              :key="meter.id"
              :label="meterOptionLabel(meter)"
              :value="meter.id"
            >
              <div class="meter-option">
                <strong>{{ meter.meterNo }} · {{ meter.meterName || meterTypeText(meter.meterType) }}</strong>
                <span>{{ meterTypeText(meter.meterType) }} / {{ meter.roomName || '-' }} / {{ meter.tenantName || '-' }}</span>
              </div>
            </el-option>
          </el-select>
        </el-form-item>

        <div class="meter-info">
          <span>上期读数：{{ formatNumber(lastInfo.previousReading) }}</span>
          <span>单位：{{ lastInfo.unit || '-' }}</span>
          <span>倍率：{{ lastInfo.multiplier || '-' }}</span>
          <span>房间：{{ lastInfo.roomName || '-' }}</span>
          <span>租户：{{ lastInfo.tenantName || '-' }}</span>
        </div>

        <el-alert
          v-if="duplicateReading"
          :title="duplicateReadingMessage"
          type="warning"
          show-icon
          :closable="false"
          class="reading-duplicate-alert"
        />

        <div class="form-grid">
          <el-form-item label="账期" prop="periodMonth">
            <el-date-picker
              v-model="form.periodMonth"
              type="month"
              value-format="YYYY-MM"
              placeholder="选择账期"
              style="width: 100%"
              @change="handlePeriodMonthChange"
            />
          </el-form-item>
          <el-form-item label="抄表日期" prop="readingDate">
            <el-date-picker
              v-model="form.readingDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="请选择抄表日期"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="本次读数" prop="currentReading">
            <el-input-number v-model="form.currentReading" :min="0" :precision="2" :step="1" style="width: 100%" />
          </el-form-item>
          <el-form-item label="单价" prop="unitPrice">
            <el-input-number v-model="form.unitPrice" :min="0" :precision="4" :step="0.1" style="width: 100%" />
          </el-form-item>
        </div>

        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="抄表来源">
          <el-select v-model="form.source" style="width: 100%">
            <el-option label="人工抄表" value="MANUAL" />
            <el-option label="批量导入" value="IMPORT" />
            <el-option label="自动采集" value="AUTO" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="Number(form.currentReading) < Number(lastInfo.previousReading)" label="读数回退原因" required>
          <el-input v-model="form.resetReason" type="textarea" :rows="2" placeholder="例如更换表具、表具归零" />
        </el-form-item>
        <el-form-item v-if="form.id" label="修改原因" required>
          <el-input v-model="form.changeReason" type="textarea" :rows="2" placeholder="历史读数修改会写入审计记录" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button
          v-if="form.id ? hasPermission(energyReadingPermissions.update) : hasPermission(energyReadingPermissions.create)"
          type="primary"
          :loading="saving"
          :disabled="Boolean(duplicateReading)"
          @click="submitForm"
        >保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, RefreshLeft, Search } from '@element-plus/icons-vue'
import request from '../../utils/request'
import { downloadBlob } from '../../utils/downloadBlob'
import { findDuplicateEnergyReading } from '../../utils/energyReadingDraft'
import { hasPermission } from '../../utils/permission'
import { requestErrorMessage } from '../../utils/requestError'
import { pageParams, readPage } from '../../utils/pagination'
import { loadListTotalResult } from '../../utils/listSummary'
import { useDhicTableView } from '../../composables/useDhicTableView'
import {
  useWorkspaceListRequestState,
  useWorkspaceMetricState
} from '../../composables/workspace/useWorkspaceRequestState'
import DataWorkspace from '../../components/DataWorkspace.vue'
import DhicDataToolbar from '../../components/DhicDataToolbar.vue'
import WorkspaceDataGrid from '../../components/workspace/WorkspaceDataGrid.vue'
import {
  createEnergyReadingWorkspaceConfig,
  energyBillStatusOptions,
  energyMeterTypeOptions
} from '../../workspaceConfigs/energyWorkspaceConfig'
import { workspacePermission, workspaceTableOptions } from '../../workspaceConfigs/workspaceConfig'
import {
  batchConfirmEnergyChargeBases,
  createEnergyReading,
  deleteEnergyReading,
  downloadEnergyReadingTemplate,
  confirmEnergyChargeBasis,
  generateMonthlyEnergyReadings,
  getEnergyStats,
  getEnergyPriceSettings,
  getEnergyMeters,
  getLastReading,
  importEnergyReadingValues,
  markEnergyPosted,
  saveEnergyPriceSettings,
  withdrawEnergyBill,
  updateEnergyReading
} from '../../api/energy'

const props = defineProps({
  embedded: { type: Boolean, default: false },
  allowBillingActions: { type: Boolean, default: true },
  subjectTenantId: { type: [Number, String], default: null },
  subjectScope: { type: String, default: '' }
})

const router = useRouter()
const canViewBills = computed(() => hasPermission('bill:view') || hasPermission('bill:*'))
const saving = ref(false)
const taskSaving = ref(false)
const priceSaving = ref(false)
const meterLoading = ref(false)
const dialogVisible = ref(false)
const taskDialogVisible = ref(false)
const priceDialogVisible = ref(false)
const formRef = ref(null)
const tableWrapRef = ref(null)
const records = ref([])
const tableHeight = ref(520)
const meters = ref([])
const buildings = ref([])
const taskFloors = ref([])
const energyReadingMetrics = useWorkspaceMetricState(['total', 'filtered', 'abnormal', 'usage', 'amount'])

const energyReadingWorkspaceConfig = createEnergyReadingWorkspaceConfig({
  buildingOptions: () => buildings.value.map(item => ({
    label: item.buildingName || item.name || `楼宇${item.id}`,
    value: item.id
  })),
  billingEnabled: props.allowBillingActions
})
const energyReadingPermissions = energyReadingWorkspaceConfig.permissions
const energyReadingActionMap = Object.fromEntries(energyReadingWorkspaceConfig.dataActions.map(action => [action.id, action]))
const energyReadingDataActions = {
  meters: energyReadingActionMap.meters,
  price: energyReadingActionMap.price,
  monthlyTask: energyReadingActionMap['monthly-task']
}
const energyReadingMetersPermission = workspacePermission(
  energyReadingWorkspaceConfig,
  energyReadingDataActions.meters.permission
)
const meterTypes = energyMeterTypeOptions
const meterTypeOptions = [
  { label: '全部', value: '' },
  ...meterTypes
]
const billStatuses = energyBillStatusOptions

const queryForm = reactive({
  meterType: '',
  periodMonth: '',
  meterNo: '',
  buildingId: null,
  floorId: null,
  roomId: null,
  billStatus: '',
  abnormalFlag: ''
})

const subjectQueryParams = computed(() => {
  if (props.subjectScope === 'property') return { tenantScope: 'PROPERTY' }
  const tenantId = Number(props.subjectTenantId)
  return Number.isFinite(tenantId) && tenantId > 0 ? { tenantId } : {}
})

const pagination = reactive({
  currentPage: 1,
  pageSize: 20,
  total: 0
})

const activePeriodValue = computed(() => serverQueryParams.value.periodMonth || queryForm.periodMonth || '')
const activePeriodLabel = computed(() => activePeriodValue.value || '全部历史账期')
const isViewingAllPeriods = computed(() => !activePeriodValue.value)
const isViewingPreviousMonth = computed(() => activePeriodValue.value === addMonths(currentMonth(), -1))
const isViewingCurrentMonth = computed(() => activePeriodValue.value === currentMonth())
const isViewingNextMonth = computed(() => activePeriodValue.value === addMonths(currentMonth(), 1))
const energyReadingMetricData = energyReadingMetrics.values
const energyReadingMetricStates = energyReadingMetrics.states

const form = reactive(defaultForm())
const taskForm = reactive(defaultTaskForm())
const priceForm = reactive(defaultPriceForm())
const lastInfo = reactive({
  previousReading: 0,
  unit: '',
  multiplier: '',
  roomName: '',
  tenantName: ''
})
const duplicateReading = computed(() => findDuplicateEnergyReading(records.value, form))
const duplicateReadingMessage = computed(() => {
  const existing = duplicateReading.value
  if (!existing) return ''

  const period = form.periodMonth || existing.periodMonth
  if (existing.billStatus === 'NOT_GENERATED') {
    return `该表具 ${period} 账期已有抄表记录，请关闭弹窗并编辑原记录。`
  }
  return `该表具 ${period} 账期已有已关联账单的抄表记录，不能重复新增；请前往财务账单处理后再修改。`
})
let tableResizeObserver = null
let pageActive = true

const rules = {
  meterId: [{ required: true, message: '请选择表具', trigger: 'change' }],
  periodMonth: [{ required: true, message: '请选择账期', trigger: 'change' }],
  readingDate: [{ required: true, message: '请选择抄表日期', trigger: 'change' }],
  currentReading: [{ required: true, message: '请输入本次读数', trigger: 'blur' }],
  unitPrice: [{ required: true, message: '请输入单价', trigger: 'blur' }]
}

const {
  workspaceViews,
  activeViewId,
  activeViewName,
  isViewDirty,
  tablePrefs,
  activeCustomFields,
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
} = useDhicTableView(workspaceTableOptions(energyReadingWorkspaceConfig, {
  records: () => records.value,
  serverTotal: () => pagination.total,
  queryLifecycle: {
    task: () => {
      pagination.currentPage = 1
      loadRecords()
    }
  },
  filters: () => [
    queryForm.periodMonth ? { label: '账期', value: queryForm.periodMonth } : null,
    queryForm.meterNo ? { label: '表具编号', value: queryForm.meterNo } : null
  ].filter(Boolean),
  groupLabel: value => {
    const option = [
      ...energyMeterTypeOptions,
      ...energyBillStatusOptions,
      ...energyReadingWorkspaceConfig.statuses
    ].find(item => item.value === value)
    return option?.label || value || '未填写'
  }
}))

async function switchEnergyReadingView(viewId) {
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

function saveEnergyReadingView() {
  saveCurrentView()
  ElMessage.success(`视图“${activeViewName.value}”已保存`)
}

function removeEnergyReadingViewFilter(index) {
  removeViewFilter(index)
}

function updateEnergyReadingFilters(filters) {
  updateFilters(filters)
}

function resetEnergyReadingView() {
  resetTableView()
}

function defaultForm() {
  return {
    id: null,
    meterType: '',
    meterId: null,
    periodMonth: currentMonth(),
    readingDate: currentDate(),
    currentReading: 0,
    unitPrice: 1,
    source: 'MANUAL',
    resetReason: '',
    changeReason: '',
    remark: ''
  }
}

function defaultTaskForm() {
  return {
    periodMonth: currentMonth(),
    readingDate: currentDate(),
    meterType: '',
    buildingId: null,
    floorId: null,
    roomId: null,
    tenantId: null,
    unitPrice: 1
  }
}

function defaultPriceForm() {
  return {
    periodMonth: currentMonth(),
    electricPrice: 1,
    waterPrice: 1,
    gasPrice: 1
  }
}

function currentDate() {
  return new Date().toISOString().slice(0, 10)
}

function currentMonth() {
  return new Date().toISOString().slice(0, 7)
}

function updateTableHeight() {
  if (typeof window === 'undefined') return
  const wrapHeight = tableWrapRef.value ? tableWrapRef.value.clientHeight : 0
  tableHeight.value = Math.max(360, Math.floor(wrapHeight || window.innerHeight - 360))
}

function addMonths(periodMonth, offset) {
  const basePeriod = periodMonth || currentMonth()
  const [year, month] = basePeriod.split('-').map(Number)
  const date = new Date(year, month - 1 + offset, 1)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
}

function setQueryPeriod(periodMonth) {
  queryForm.periodMonth = periodMonth
  handleFilterChange()
}

function shiftQueryPeriod(offset) {
  setQueryPeriod(addMonths(queryForm.periodMonth, offset))
}

function clearQueryPeriod() {
  queryForm.periodMonth = ''
  handleFilterChange()
}

function cleanPayload(payload) {
  return Object.fromEntries(
    Object.entries(payload).filter(([, value]) => value !== '' && value !== null && value !== undefined)
  )
}

function readingFilterValue(key, fallback) {
  return Object.prototype.hasOwnProperty.call(serverQueryParams.value, key)
    ? serverQueryParams.value[key]
    : fallback
}

function readingQueryParams() {
  return {
    meterType: readingFilterValue('meterType', queryForm.meterType) || undefined,
    periodMonth: readingFilterValue('periodMonth', queryForm.periodMonth) || undefined,
    meterNo: readingFilterValue('meterNo', queryForm.meterNo) || undefined,
    buildingId: readingFilterValue('buildingId', queryForm.buildingId) || undefined,
    floorId: queryForm.floorId || undefined,
    roomId: queryForm.roomId || undefined,
    billStatus: readingFilterValue('billStatus', queryForm.billStatus) || undefined,
    abnormalFlag: readingFilterValue('abnormalFlag', queryForm.abnormalFlag) === ''
      ? undefined
      : readingFilterValue('abnormalFlag', queryForm.abnormalFlag),
    ...subjectQueryParams.value
  }
}

function batchScopeParams() {
  const { billStatus, abnormalFlag, ...scope } = readingQueryParams()
  return scope
}

async function loadRecords() {
  const requestId = listRequestGuard.start()
  const params = readingQueryParams()
  const pagedParams = {
    ...params,
    ...pageParams(pagination),
    sorts: serverQueryParams.value.sorts || undefined,
    groupBy: serverQueryParams.value.groupBy || undefined
  }
  const requestContext = energyReadingListState.begin({ viewId: activeViewId.value, params: pagedParams })
  const metricRevision = energyReadingMetrics.begin()
  const listTask = request.get('/energy/readings', { params: pagedParams, silent: true }).then(
    value => applyEnergyReadingListResult(requestId, requestContext, metricRevision, { status: 'fulfilled', value }),
    reason => applyEnergyReadingListResult(requestId, requestContext, metricRevision, { status: 'rejected', reason })
  )
  const metricsTask = Promise.allSettled([
    getEnergyStats(params, { silent: true }),
    loadListTotalResult('/energy/readings', subjectQueryParams.value)
  ]).then(([statsResult, totalResult]) => {
    if (!listRequestGuard.isCurrent(requestId) || !energyReadingListState.isCurrent(requestContext)) return
    applyEnergyReadingTotalMetric(metricRevision, totalResult)
    applyEnergyReadingStats(metricRevision, statsResult)
  })

  await Promise.allSettled([listTask, metricsTask])
}

function applyEnergyReadingListResult(requestId, requestContext, metricRevision, result) {
  if (!listRequestGuard.isCurrent(requestId) || !energyReadingListState.isCurrent(requestContext)) return
  if (result.status === 'fulfilled') {
    const page = readPage(result.value)
    records.value = page.records
    pagination.total = page.total
    energyReadingListState.succeed(requestContext)
    energyReadingMetrics.resolve(metricRevision, 'filtered', page.total)
  } else {
    energyReadingListState.fail(requestContext, '抄表记录加载失败，请稍后重试')
    energyReadingMetrics.reject(metricRevision, 'filtered')
  }
}

const energyReadingListState = useWorkspaceListRequestState({
  hasResults: () => records.value.length > 0,
  clearResults: () => {
    records.value = []
    pagination.total = 0
  }
})
const loading = computed(() => energyReadingListState.status.value === 'loading')
const listErrorText = energyReadingListState.errorText

function applyEnergyReadingTotalMetric(revision, result) {
  const detail = result.status === 'fulfilled' ? result.value : null
  if (detail?.ok) energyReadingMetrics.resolve(revision, 'total', detail.value)
  else energyReadingMetrics.reject(revision, 'total')
}

function applyEnergyReadingStats(revision, result) {
  if (result.status !== 'fulfilled') {
    for (const key of ['abnormal', 'usage', 'amount']) energyReadingMetrics.reject(revision, key)
    return
  }
  const stats = result.value || {}
  energyReadingMetrics.resolve(revision, 'abnormal', Number(stats.abnormalCount || 0))
  energyReadingMetrics.resolve(
    revision,
    'usage',
    Number(stats.electricUsage || 0) + Number(stats.waterUsage || 0) + Number(stats.gasUsage || 0)
  )
  energyReadingMetrics.resolve(revision, 'amount', Number(stats.totalAmount || 0))
}

async function loadMeters() {
  await searchMeters('')
}

async function searchMeters(keyword = '') {
  meterLoading.value = true
  try {
    const data = await getEnergyMeters({
      keyword: keyword || undefined,
      meterType: form.meterType || undefined,
      status: 'ENABLED',
      ...subjectQueryParams.value,
      page: 1,
      pageSize: 50
    })
    if (pageActive) meters.value = readPage(data).records
  } finally {
    if (pageActive) meterLoading.value = false
  }
}

async function loadBuildings() {
  if (!hasPermission('building:view') && !hasPermission('building:*')) {
    buildings.value = []
    return
  }
  try {
    const data = await request.get('/buildings', { silent: true })
    if (pageActive) buildings.value = readPage(data).records
  } catch {
    if (pageActive) buildings.value = []
  }
}

async function loadFloors(buildingId) {
  if (!buildingId) return []
  return readPage(await request.get('/floors', { params: { building_id: buildingId } })).records
}

async function onTaskBuildingChange() {
  taskForm.floorId = null
  taskForm.roomId = null
  taskFloors.value = await loadFloors(taskForm.buildingId)
}

function handleSearch() {
  pagination.currentPage = 1
  loadRecords()
}

function handleFilterChange() {
  handleSearch()
}

function resetSearch() {
  queryForm.meterType = ''
  queryForm.periodMonth = ''
  queryForm.meterNo = ''
  queryForm.buildingId = null
  queryForm.floorId = null
  queryForm.roomId = null
  queryForm.billStatus = ''
  queryForm.abnormalFlag = ''
  handleSearch()
}

watch(() => JSON.stringify(serverQueryParams.value), () => debouncedViewQueryLoad.schedule())

watch(
  () => [props.subjectScope, props.subjectTenantId],
  () => {
    if (!props.embedded || !pageActive) return
    pagination.currentPage = 1
    loadMeters()
    loadRecords()
  }
)

function handleSizeChange() {
  pagination.currentPage = 1
  loadRecords()
}

async function openPriceDialog() {
  Object.assign(priceForm, {
    ...defaultPriceForm(),
    periodMonth: queryForm.periodMonth || currentMonth()
  })
  priceDialogVisible.value = true
  await loadPriceSettings()
}

async function loadPriceSettings() {
  if (!priceForm.periodMonth) return
  const data = await getEnergyPriceSettings({ periodMonth: priceForm.periodMonth })
  Object.assign(priceForm, {
    periodMonth: data.periodMonth || priceForm.periodMonth,
    electricPrice: Number(data.electricPrice || 0),
    waterPrice: Number(data.waterPrice || 0),
    gasPrice: Number(data.gasPrice || 0)
  })
}

async function submitPriceSettings() {
  if (!priceForm.periodMonth) {
    ElMessage.warning('请选择账期')
    return
  }
  priceSaving.value = true
  try {
    const data = await saveEnergyPriceSettings(cleanPayload(priceForm))
    priceDialogVisible.value = false
    queryForm.periodMonth = priceForm.periodMonth
    pagination.currentPage = 1
    ElMessage.success(`已保存 ${data.savedCount || 0} 类单价，同步更新 ${data.updatedReadingCount || 0} 条未生成账单记录`)
    await loadRecords()
  } finally {
    priceSaving.value = false
  }
}

async function openMonthlyTaskDialog() {
  const defaults = defaultTaskForm()
  Object.assign(taskForm, {
    ...defaults,
    periodMonth: queryForm.periodMonth || defaults.periodMonth,
    meterType: queryForm.meterType || '',
    buildingId: queryForm.buildingId || null,
    floorId: queryForm.floorId || null,
    roomId: queryForm.roomId || null
  })
  taskFloors.value = taskForm.buildingId ? await loadFloors(taskForm.buildingId) : []
  taskDialogVisible.value = true
}

async function submitMonthlyTask() {
  if (!taskForm.periodMonth) {
    ElMessage.warning('请选择账期')
    return
  }

  taskSaving.value = true
  try {
    const data = await generateMonthlyEnergyReadings(cleanPayload({
      ...taskForm,
      ...subjectQueryParams.value
    }))
    taskDialogVisible.value = false
    queryForm.periodMonth = taskForm.periodMonth
    queryForm.meterType = taskForm.meterType
    queryForm.buildingId = taskForm.buildingId
    queryForm.floorId = taskForm.floorId
    queryForm.roomId = taskForm.roomId
    queryForm.billStatus = 'NOT_GENERATED'
    queryForm.abnormalFlag = false
    pagination.currentPage = 1
    ElMessage.success(`已生成 ${data.createdCount || 0} 条待抄表任务，跳过 ${data.skippedExistingCount || 0} 条已有记录`)
    await loadRecords()
  } finally {
    taskSaving.value = false
  }
}

function openCreateDialog() {
  Object.assign(form, defaultForm())
  Object.assign(lastInfo, { previousReading: 0, unit: '', multiplier: '', roomName: '', tenantName: '' })
  searchMeters('')
  dialogVisible.value = true
}

function openEditDialog(row) {
  Object.assign(form, {
    id: row.id,
    meterType: row.meterType || '',
    meterId: row.meterId,
    periodMonth: row.periodMonth,
    readingDate: row.readingDate,
    currentReading: Number(row.currentReading || 0),
    unitPrice: Number(row.unitPrice || 0),
    source: row.source || 'MANUAL',
    resetReason: row.resetReason || '',
    changeReason: '',
    remark: row.remark || ''
  })
  Object.assign(lastInfo, {
    previousReading: row.previousReading,
    unit: row.unit,
    multiplier: row.multiplier,
    roomName: row.roomName,
    tenantName: row.tenantName
  })
  if (row.meterId && !meters.value.some(item => item.id === row.meterId)) {
    meters.value = [{
      id: row.meterId,
      meterNo: row.meterNo,
      meterName: row.meterName,
      meterType: row.meterType,
      roomName: row.roomName,
      tenantName: row.tenantName
    }, ...meters.value]
  }
  dialogVisible.value = true
}

function handleDialogMeterTypeChange() {
  form.meterId = null
  Object.assign(lastInfo, { previousReading: 0, unit: '', multiplier: '', roomName: '', tenantName: '' })
  searchMeters('')
}

async function handleMeterChange(meterId) {
  if (!meterId) return
  const data = await getLastReading(meterId, { periodMonth: form.periodMonth })
  Object.assign(lastInfo, data)
}

async function handlePeriodMonthChange() {
  if (!form.meterId) return
  await handleMeterChange(form.meterId)
}

async function submitForm() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  if (duplicateReading.value) {
    ElMessage.warning(duplicateReadingMessage.value)
    return
  }

  saving.value = true
  try {
    const payload = {
      meterId: form.meterId,
      periodMonth: form.periodMonth,
      readingDate: form.readingDate,
      currentReading: form.currentReading,
      unitPrice: form.unitPrice,
      source: form.source,
      resetReason: form.resetReason || undefined,
      changeReason: form.changeReason || undefined,
      remark: form.remark
    }
    if (form.id) {
      await updateEnergyReading(form.id, payload)
      ElMessage.success('抄表记录已更新')
    } else {
      await createEnergyReading(payload)
      ElMessage.success('抄表记录已保存')
    }
    dialogVisible.value = false
    await loadRecords()
  } catch (error) {
    if (!error?.requestMessageShown) {
      ElMessage.error(requestErrorMessage(error, '抄表记录保存失败'))
    }
  } finally {
    saving.value = false
  }
}

async function deleteRecord(row) {
  await ElMessageBox.confirm(`确定删除表号 ${row.meterNo} 的 ${row.periodMonth} 抄表记录吗？`, '删除确认', { type: 'warning' })
  await deleteEnergyReading(row.id)
  ElMessage.success('已删除')
  await loadRecords()
}

async function generateBill(row) {
  await confirmEnergyChargeBasis(row.id)
  ElMessage.success('能耗费用依据已确认，财务可统一生成账单')
  await loadRecords()
}

async function markPosted(row) {
  await markEnergyPosted(row.id)
  ElMessage.success('已标记入账并发布到租户端')
  await loadRecords()
}

async function withdrawBill(row) {
  const result = await ElMessageBox.prompt(
    `撤回后，表号 ${row.meterNo} 的 ${row.periodMonth} 账单会作废，抄表记录回到未生成状态。请输入撤回原因：`,
    '撤回账单',
    {
      confirmButtonText: '确认撤回',
      cancelButtonText: '取消',
      inputPlaceholder: '例如：读数录入错误，需重新核对',
      inputValue: '数据错误，撤回后重新生成',
      type: 'warning'
    }
  )
  await withdrawEnergyBill(row.id, { reason: result.value || '数据错误，撤回后重新生成' })
  ElMessage.success('账单已撤回，可修改读数或单价后重新生成')
  await loadRecords()
}

function viewBill(row) {
  if (!row.billId || !row.billNumber) {
    ElMessage.warning('该记录尚未生成账单')
    return
  }
  router.push({ path: '/bills', query: { keyword: row.billNumber } })
}

function handleRowCommand(command, row) {
  if (command === 'view') {
    viewBill(row)
  } else if (command === 'edit') {
    openEditDialog(row)
  } else if (command === 'withdraw') {
    withdrawBill(row)
  } else if (command === 'delete') {
    deleteRecord(row)
  }
}

function recordRowClassName({ row }) {
  if (row.abnormalFlag) return 'energy-row-abnormal'
  if (row.billStatus === 'POSTED') return 'energy-row-posted'
  if (row.billStatus === 'GENERATED') return 'energy-row-generated'
  return ''
}

async function exportReadingTemplate() {
  const filename = `抄表导入模板-${queryForm.periodMonth || currentMonth()}.csv`
  let saveHandle = null
  if (window.showSaveFilePicker) {
    try {
      saveHandle = await window.showSaveFilePicker({
        suggestedName: filename,
        types: [{
          description: 'CSV 文件',
          accept: { 'text/csv': ['.csv'] }
        }]
      })
    } catch (error) {
      if (error?.name === 'AbortError') {
        return
      }
    }
  }

  const blob = await downloadEnergyReadingTemplate(cleanPayload(readingQueryParams()))
  if (saveHandle) {
    const writable = await saveHandle.createWritable()
    await writable.write(blob)
    await writable.close()
    ElMessage.success('模板已保存')
    return
  }

  downloadBlob(blob, filename)
  ElMessage.success('模板下载已开始')
}

function handleEnergyReadingTransferAction(actionId) {
  if (actionId === 'export-template') exportReadingTemplate()
  else if (props.allowBillingActions && actionId === 'batch-bill') batchGenerateBills()
}

async function handleImportReadingFile({ actionId, file }) {
  if (actionId !== 'import' || !file) return

  const text = await file.text()
    const rows = parseCsv(text).filter(row => row.some(cell => String(cell || '').trim()))
    if (rows.length < 2) {
      ElMessage.warning('导入文件没有可读取的数据')
      return
    }

    const headers = rows[0].map(header => String(header || '').trim().replace(/^\ufeff/, ''))
    const mappedRows = rows.slice(1).map(cells => mapReadingImportRow(headers, cells))
    const readings = mappedRows.filter(row => row.meterNo && row.currentReading !== '')
    if (!readings.length) {
      ElMessage.warning('没有找到已填写本次读数的表具')
      return
    }

    const data = await importEnergyReadingValues({
      periodMonth: queryForm.periodMonth || readings[0].periodMonth || currentMonth(),
      readings
    })
    ElMessage.success(`导入成功 ${data.successCount || 0} 条，失败 ${data.failureCount || 0} 条`)
    if (data.failureCount > 0 && Array.isArray(data.failures) && data.failures.length) {
      ElMessage.warning(`${data.failures[0].meterNo}：${data.failures[0].message}`)
    }
  await loadRecords()
}

async function batchGenerateBills() {
  await ElMessageBox.confirm('将按当前筛选范围确认能耗费用依据，账单仍由财务统一生成。确认继续吗？', '批量确认费用依据', { type: 'warning' })
  const data = await batchConfirmEnergyChargeBases(cleanPayload(batchScopeParams()))
  ElMessage.success(`已确认 ${data.successCount || 0} 条费用依据，失败 ${data.failureCount || 0} 条`)
  if (data.failureCount > 0 && Array.isArray(data.failures) && data.failures.length) {
    ElMessage.warning(`首条失败：${data.failures[0].message}`)
  }
  await loadRecords()
}

function parseCsv(text) {
  const rows = []
  let row = []
  let cell = ''
  let quoted = false
  for (let index = 0; index < text.length; index += 1) {
    const char = text[index]
    const next = text[index + 1]
    if (char === '"' && quoted && next === '"') {
      cell += '"'
      index += 1
    } else if (char === '"') {
      quoted = !quoted
    } else if (char === ',' && !quoted) {
      row.push(cell)
      cell = ''
    } else if ((char === '\n' || char === '\r') && !quoted) {
      if (char === '\r' && next === '\n') {
        index += 1
      }
      row.push(cell)
      rows.push(row)
      row = []
      cell = ''
    } else {
      cell += char
    }
  }
  row.push(cell)
  rows.push(row)
  return rows
}

function rowValue(row, ...keys) {
  for (const key of keys) {
    const value = row[key]
    if (value !== undefined && value !== null && String(value).trim() !== '') {
      return String(value).trim()
    }
  }
  return ''
}

function mapReadingImportRow(headers, cells) {
  const row = Object.fromEntries(headers.map((header, index) => [header, cells[index] ?? '']))
  const mapped = {
    periodMonth: normalizeImportMonth(rowValue(row, '账期', 'periodMonth') || fallbackReadingImportValue(cells, 3)),
    meterNo: rowValue(row, '表号', 'meterNo', '表具编号') || fallbackReadingImportValue(cells, 0),
    currentReading: rowValue(row, '本次读数', 'currentReading') || fallbackReadingImportValue(cells, 6),
    readingDate: normalizeImportDate(rowValue(row, '抄表日期', 'readingDate') || fallbackReadingImportValue(cells, 4)),
    unitPrice: rowValue(row, '单价', 'unitPrice') || fallbackReadingImportValue(cells, 7),
    remark: rowValue(row, '备注', 'remark') || fallbackReadingImportValue(cells, 10)
  }
  activeCustomFields.value.forEach(field => {
    const value = rowValue(row, field.fieldCode, field.fieldName)
    if (value !== '') mapped[field.fieldCode] = value
  })
  return mapped
}

function fallbackReadingImportValue(cells, index) {
  const value = cells[index]
  return value === undefined || value === null ? '' : String(value).trim()
}

function normalizeImportMonth(value) {
  const text = String(value || '').trim()
  const monthMatch = text.match(/^(\d{4})[-/](\d{1,2})/)
  if (monthMatch) {
    return `${monthMatch[1]}-${monthMatch[2].padStart(2, '0')}`
  }
  return text
}

function normalizeImportDate(value) {
  const text = String(value || '').trim()
  const dateMatch = text.match(/^(\d{4})[-/](\d{1,2})[-/](\d{1,2})$/)
  if (dateMatch) {
    return `${dateMatch[1]}-${dateMatch[2].padStart(2, '0')}-${dateMatch[3].padStart(2, '0')}`
  }
  return text
}

function meterOptionLabel(meter) {
  return `${meter.meterNo} | ${meter.meterName || meterTypeText(meter.meterType)} | ${meter.roomName || '-'} | ${meter.tenantName || '-'}`
}

function formatFloor(floor) {
  return floor.floorName || `${floor.floorNumber}F`
}

function formatNumber(value) {
  return Number(value || 0).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })
}

function meterTypeText(type) {
  return {
    ELECTRIC: '电',
    ELECTRICITY: '电',
    WATER: '水',
    GAS: '燃气'
  }[type] || type || '-'
}

onMounted(async () => {
  await nextTick()
  updateTableHeight()
  if (typeof ResizeObserver !== 'undefined' && tableWrapRef.value) {
    tableResizeObserver = new ResizeObserver(updateTableHeight)
    tableResizeObserver.observe(tableWrapRef.value)
  }
  window.addEventListener('resize', updateTableHeight)
  await Promise.allSettled([loadBuildings(), loadMeters()])
  if (pageActive) await loadRecords()
})

onBeforeUnmount(() => {
  pageActive = false
  disposeQueryLifecycle()
  window.removeEventListener('resize', updateTableHeight)
  tableResizeObserver?.disconnect()
})
</script>

<style scoped>
.page-container {
  padding: 0;
}

.energy-record-page {
  height: calc(100vh - 64px);
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  overflow: hidden;
  padding: 8px 18px 10px;
}

.energy-record-page--embedded {
  height: auto;
  min-height: 680px;
  overflow: visible;
  padding: 0;
}

.energy-record-page > :deep(.data-workspace) {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.energy-record-page :deep(.workspace-grid) {
  flex: 1 1 auto;
  min-height: 0;
  overflow: hidden;
}

.workspace-quick-search {
  width: 230px;
}

.view-hint {
  color: #7b8494;
  font-size: 12px;
  white-space: nowrap;
}

.period-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex: 0 0 auto;
  gap: 8px;
  margin: 0 0 4px;
  padding: 3px 8px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  background: #fafafa;
}

.period-toolbar-main {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  min-width: 0;
}

.period-label {
  color: #909399;
  font-size: 12px;
}

.period-toolbar-main strong {
  color: #1f2d3d;
  font-size: 13px;
}

.period-scope-reminder {
  display: inline-flex;
  align-items: center;
  min-height: 20px;
  padding: 0 7px;
  border: 1px solid #f3d19e;
  border-radius: 4px;
  background: #fdf6ec;
  color: #8a5a16;
  font-size: 11px;
  line-height: 18px;
  white-space: nowrap;
}

.period-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 3px;
}

.period-actions :deep(.el-button) {
  height: 24px;
  padding: 4px 8px;
}

.energy-record-table-wrap {
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.record-table {
  width: 100%;
  flex: 1 1 auto;
}

:deep(.record-table .el-table__header .el-table__cell) {
  height: 34px;
  padding: 3px 0;
}

:deep(.record-table .cell) {
  overflow: hidden;
  padding: 0 6px;
  font-size: 12px;
}

:deep(.record-table.density-compact .el-table__cell) {
  padding: 4px 0;
}

:deep(.record-table.density-default .el-table__cell) {
  padding: 8px 0;
}

:deep(.record-table.density-comfortable .el-table__cell) {
  padding: 12px 0;
}

.action-inline {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-wrap: nowrap;
  gap: 3px;
}

:deep(.record-table .el-tag) {
  height: 18px;
  padding: 0 5px;
  line-height: 16px;
  font-size: 10px;
}

:deep(.action-inline .el-button) {
  height: 22px;
  margin-left: 0;
  padding: 3px 6px;
  font-size: 11px;
  line-height: 1;
}

:deep(.energy-row-abnormal) {
  --el-table-tr-bg-color: #fff7f7;
}

:deep(.energy-row-generated) {
  --el-table-tr-bg-color: #f7fbff;
}

:deep(.energy-row-posted) {
  --el-table-tr-bg-color: #f7f8fa;
  color: #606266;
}

.pagination-wrapper {
  display: flex;
  flex: 0 0 auto;
  justify-content: flex-end;
  margin-top: 4px;
}

.meter-info {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 8px;
  margin: 0 0 18px 110px;
  color: #606266;
  font-size: 13px;
}

.reading-duplicate-alert {
  margin: -6px 0 16px 110px;
}

.price-grid {
  display: grid;
  gap: 4px;
}

.dialog-tip,
.field-tip {
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
}

.dialog-tip {
  margin: 2px 0 0 110px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 12px;
}

.meter-option {
  display: flex;
  flex-direction: column;
  line-height: 1.35;
}

.meter-option span {
  font-size: 12px;
  color: #909399;
}

@media (max-width: 1200px) {
  .period-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
