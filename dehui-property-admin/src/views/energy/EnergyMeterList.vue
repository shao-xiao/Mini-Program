<template>
  <div class="page-container" :class="{ 'page-container--embedded': props.embedded }">
    <DataWorkspace
      :workspace-config="energyMeterWorkspaceConfig"
      :views="workspaceViews"
      :active-view-id="activeViewId"
      :view-dirty="isViewDirty"
      :filter-logic="tablePrefs.filterLogic"
      :filter-logic-visible="tablePrefs.filters.length > 1"
      :filter-items="activeFilterItems"
      :query-notices="queryNotices"
      :metric-data="energyMeterMetricData"
      :metric-states="energyMeterMetricStates"
      @change-view="switchEnergyMeterView"
      @create-view="createTableView"
      @duplicate-view="duplicateTableView"
      @rename-view="renameTableView"
      @delete-view="deleteTableView"
      @save-view="saveEnergyMeterView"
      @remove-filter="removeEnergyMeterViewFilter"
    >
      <template #header-actions>
        <el-button v-if="!props.embedded && hasPermission(energyMeterReadingsPermission)" @click="router.push('/energy/records')">
          {{ energyMeterDataActions.readings.label }}
        </el-button>
        <el-button v-if="hasPermission(energyMeterPermissions.create)" type="primary" :icon="Plus" @click="openDialog()">
          {{ energyMeterWorkspaceConfig.primaryAction.label }}
        </el-button>
      </template>

      <template #toolbar>
        <DhicDataToolbar
          :workspace-config="energyMeterWorkspaceConfig"
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
          @update:filters="updateEnergyMeterFilters"
          @update:filter-logic="updateFilterLogic"
          @update:sort-key="updateSortKey"
          @update:sort-order="updateSortOrder"
          @update:sorts="updateSorts"
          @update:group-key="updateGroupKey"
          @update:groups="updateGroups"
          @update:row-density="updateRowDensity"
          @reset-view="resetEnergyMeterView"
          @save-field-config="saveEnergyMeterView"
        >
          <template #right>
            <el-input
              v-model.trim="query.keyword"
              clearable
              :prefix-icon="Search"
              class="workspace-quick-search"
              placeholder="搜索表号、名称、位置或租户"
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
          :loading="loading"
          :error-text="listErrorText"
          retryable
          :rows="tableRows"
          :columns="visibleOrderedColumns"
          row-key="id"
          :current-page="pagination.currentPage"
          :page-size="pagination.pageSize"
          :index-width="80"
          :density="tablePrefs.rowDensity"
          :empty-text="tableEmptyText"
          :sort-state="activeTableSort"
          :data-mode="energyMeterWorkspaceConfig.query.dataMode"
          :column-width="columnWidth"
          :column-fixed="columnFixed"
          :is-column-sortable="isColumnSortable"
          :show-overflow-tooltip="column => ['location', 'remark'].includes(column.key)"
          :operation-column="{ width: 250, fixed: 'right', align: 'left' }"
          @column-resize="handleHeaderDragEnd"
          @sort-change="handleTableSortChange"
          @retry="load"
          aria-label="表具管理数据表格"
        >
          <template #operations="{ row }">
            <el-button v-if="hasPermission(energyMeterPermissions.update)" size="small" @click="openBindings(row)">计量归属</el-button>
            <el-button v-if="hasPermission(energyMeterPermissions.update)" size="small" type="primary" @click="openDialog(row)">编辑</el-button>
            <el-button v-if="hasPermission(energyMeterPermissions.delete)" size="small" type="danger" @click="remove(row)">删除</el-button>
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

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑表具' : '新增表具'" width="680px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <div class="form-grid">
          <el-form-item label="表具编号" prop="meterNo">
            <el-input v-model.trim="form.meterNo" placeholder="如 EL-B1-001" />
          </el-form-item>
          <el-form-item label="表具名称" prop="meterName">
            <el-input v-model.trim="form.meterName" placeholder="如 B1 总电表" />
          </el-form-item>
          <el-form-item label="类型" prop="meterType">
            <el-select v-model="form.meterType" style="width: 100%" @change="syncDefaultUnit">
              <el-option v-for="item in meterTypes" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="使用状态" prop="status">
            <el-select v-model="form.status" style="width: 100%">
              <el-option label="启用" value="ENABLED" />
              <el-option label="停用" value="DISABLED" />
            </el-select>
          </el-form-item>
          <el-form-item label="单位" prop="unit">
            <el-input v-model.trim="form.unit" placeholder="如 kWh / m³" />
          </el-form-item>
          <el-form-item label="倍率" prop="multiplier">
            <el-input-number v-model="form.multiplier" :min="0.0001" :precision="4" :step="1" style="width: 100%" />
          </el-form-item>
          <el-form-item label="初始读数">
            <el-input-number v-model="form.initialReading" :min="0" :precision="2" style="width: 100%" />
          </el-form-item>
          <el-form-item label="表具投运日期" class="commissioning-date-field">
            <el-date-picker v-model="form.commissioningDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            <div class="field-help">表具开始计量的日期；新增时将作为首条计量归属开始日期。</div>
          </el-form-item>
        </div>

        <el-form-item label="楼宇">
          <el-select v-model="form.buildingId" clearable placeholder="请选择楼宇" style="width: 100%" @change="onFormBuildingChange">
            <el-option v-for="item in buildings" :key="item.id" :label="item.buildingName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="楼层">
          <el-select v-model="form.floorId" clearable filterable placeholder="请选择楼层" style="width: 100%" :disabled="!form.buildingId" @change="onFormFloorChange">
            <el-option v-for="item in formFloors" :key="item.id" :label="formatFloor(item)" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="房间">
          <el-select v-model="form.roomId" clearable filterable placeholder="请选择房间" style="width: 100%" :disabled="!form.buildingId">
            <el-option v-for="item in formRooms" :key="item.id" :label="formatRoom(item)" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="位置说明">
          <el-input v-model.trim="form.locationDescription" placeholder="标准位置之外的补充描述" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model.trim="form.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button v-if="form.id ? hasPermission(energyMeterPermissions.update) : hasPermission(energyMeterPermissions.create)" type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="bindingVisible" :title="`${bindingMeter?.meterNo || ''} 计量归属历史`" size="640px">
      <section class="attribution-summary">
        <div>
          <div class="attribution-summary__eyebrow">当前计量归属</div>
          <strong class="attribution-summary__title">{{ attributionSummary(currentAttribution) }}</strong>
          <div class="attribution-summary__meta">
            <span>归属开始：{{ currentAttribution?.effectiveFrom || '-' }}</span>
            <span>表具投运：{{ bindingMeter?.commissioningDate || '-' }}</span>
          </div>
        </div>
        <el-button
          v-if="!bindingEditorVisible"
          type="primary"
          plain
          @click="startAttributionEditor"
        >
          {{ currentAttribution ? '变更计量归属' : '设置初始计量归属' }}
        </el-button>
      </section>
      <el-alert
        title="新增表具会自动建立初始计量归属；只有租户、房间或公共区域实际变更时才新增历史记录。"
        type="info"
        :closable="false"
        show-icon
      />
      <el-table :data="bindings" v-loading="bindingLoading" style="margin-top: 16px">
        <el-table-column label="归属类型" width="130">
          <template #default="{ row }">{{ attributionTypeLabel(row.subjectType) }}</template>
        </el-table-column>
        <el-table-column prop="tenantName" label="租户" min-width="140" />
        <el-table-column prop="roomCode" label="房间" width="100" />
        <el-table-column prop="effectiveFrom" label="归属开始" width="120" />
        <el-table-column prop="effectiveTo" label="归属结束" width="120">
          <template #default="{ row }">{{ row.effectiveTo || '至今' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">{{ attributionStatusLabel(row) }}</template>
        </el-table-column>
      </el-table>
      <template v-if="bindingEditorVisible">
        <el-divider>{{ currentAttribution ? '变更计量归属' : '设置初始计量归属' }}</el-divider>
        <el-form :model="bindingForm" label-width="140px">
        <el-form-item label="归属类型" required>
          <el-select v-model="bindingForm.subjectType" style="width: 100%">
            <el-option label="物业公共计量" value="PROPERTY" />
            <el-option label="租户" value="TENANT" />
            <el-option label="房间" value="ROOM" />
            <el-option label="公共区域" value="PUBLIC_AREA" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="bindingForm.subjectType === 'TENANT'" label="租户" required>
          <el-select v-model="bindingForm.tenantId" filterable style="width: 100%">
            <el-option v-for="item in tenants" :key="item.id" :label="tenantLabel(item)" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="房间">
          <el-select v-model="bindingForm.roomId" clearable filterable style="width: 100%">
            <el-option v-for="item in formRooms" :key="item.id" :label="formatRoom(item)" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="计量归属开始日期" required>
          <el-date-picker
            v-model="bindingForm.effectiveFrom"
            type="date"
            value-format="YYYY-MM-DD"
            :disabled-date="disableAttributionStartDate"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="bindingForm.remark" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item>
          <el-button @click="cancelAttributionEditor">取消</el-button>
          <el-button type="primary" :loading="bindingSaving" @click="saveBinding">
            {{ currentAttribution ? '确认变更计量归属' : '保存初始计量归属' }}
          </el-button>
        </el-form-item>
      </el-form>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, RefreshLeft, Search } from '@element-plus/icons-vue'
import {
  createEnergyMeter,
  createEnergyMeterBinding,
  deleteEnergyMeter,
  getEnergyMeters,
  listEnergyMeterBindings,
  replaceEnergyMeterBinding,
  updateEnergyMeter
} from '../../api/energy'
import request from '../../utils/request'
import { hasPermission } from '../../utils/permission'
import { createPagination, pageParams, readPage, resetToFirstPage } from '../../utils/pagination'
import { loadListTotalResult, loadListTotalsDetailed } from '../../utils/listSummary'
import { loadAllWorkspaceRecords } from '../../utils/workspaceExport'
import { useDhicTableView } from '../../composables/useDhicTableView'
import {
  useWorkspaceListRequestState,
  useWorkspaceMetricState
} from '../../composables/workspace/useWorkspaceRequestState'
import DataWorkspace from '../../components/DataWorkspace.vue'
import DhicDataToolbar from '../../components/DhicDataToolbar.vue'
import WorkspaceDataGrid from '../../components/workspace/WorkspaceDataGrid.vue'
import {
  createEnergyMeterWorkspaceConfig,
  energyMeterTypeOptions
} from '../../workspaceConfigs/energyWorkspaceConfig'
import { workspacePermission, workspaceTableOptions } from '../../workspaceConfigs/workspaceConfig'
import {
  currentOpenAttribution,
  validateAttributionDraft
} from '../../utils/energyMeterAttribution'

const props = defineProps({
  embedded: { type: Boolean, default: false },
  subjectTenantId: { type: [Number, String], default: null },
  subjectScope: { type: String, default: '' }
})

const router = useRouter()
const saving = ref(false)
const dialogVisible = ref(false)
const formRef = ref(null)
const bindingVisible = ref(false)
const bindingLoading = ref(false)
const bindingSaving = ref(false)
const bindingEditorVisible = ref(false)
const bindingMeter = ref(null)
const bindings = ref([])
const currentAttribution = computed(() => currentOpenAttribution(bindings.value))
const list = ref([])
const buildings = ref([])
const formFloors = ref([])
const formRooms = ref([])
const tenants = ref([])
let pageActive = true
const pagination = reactive(createPagination(20))
const energyMeterMetrics = useWorkspaceMetricState([
  'total',
  'filtered',
  'electric',
  'water',
  'gas',
  'enabled',
  'disabled'
])

const energyMeterWorkspaceConfig = createEnergyMeterWorkspaceConfig({
  buildingOptions: () => buildings.value.map(item => ({
    label: item.buildingName || item.name || `楼宇${item.id}`,
    value: item.id
  }))
})
const energyMeterPermissions = energyMeterWorkspaceConfig.permissions
const energyMeterDataActions = Object.fromEntries(energyMeterWorkspaceConfig.dataActions.map(action => [action.id, action]))
const energyMeterReadingsPermission = workspacePermission(
  energyMeterWorkspaceConfig,
  energyMeterDataActions.readings.permission
)
const meterTypes = energyMeterTypeOptions
const tableColumns = energyMeterWorkspaceConfig.fields
const energyMeterMetricData = energyMeterMetrics.values
const energyMeterMetricStates = energyMeterMetrics.states

const query = reactive({
  keyword: '',
  meterType: '',
  status: '',
  buildingId: null
})

const subjectQueryParams = computed(() => {
  if (props.subjectScope === 'property') return { tenantScope: 'PROPERTY' }
  const tenantId = Number(props.subjectTenantId)
  return Number.isFinite(tenantId) && tenantId > 0 ? { tenantId } : {}
})

const defaultForm = () => ({
  id: null,
  meterNo: '',
  meterName: '',
  meterType: 'ELECTRIC',
  buildingId: null,
  floorId: null,
  roomId: null,
  tenantId: null,
  locationDescription: '',
  unit: 'kWh',
  multiplier: 1,
  initialReading: 0,
  commissioningDate: null,
  status: 'ENABLED',
  remark: ''
})

const form = reactive(defaultForm())
const bindingForm = reactive({
  subjectType: 'PROPERTY',
  tenantId: null,
  roomId: null,
  effectiveFrom: new Date().toISOString().slice(0, 10),
  remark: ''
})

const rules = {
  meterNo: [{ required: true, message: '请输入表具编号', trigger: 'blur' }],
  meterName: [{ required: true, message: '请输入表具名称', trigger: 'blur' }],
  meterType: [{ required: true, message: '请选择类型', trigger: 'change' }],
  unit: [{ required: true, message: '请输入单位', trigger: 'blur' }],
  multiplier: [{ required: true, message: '请输入倍率', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
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
} = useDhicTableView(workspaceTableOptions(energyMeterWorkspaceConfig, {
  records: () => list.value,
  serverTotal: () => pagination.total,
  queryLifecycle: {
    task: () => {
      resetToFirstPage(pagination)
      load()
    }
  },
  filters: () => query.keyword ? [{ label: '关键词', value: query.keyword }] : [],
  groupLabel: value => {
    const option = [...energyMeterTypeOptions, ...energyMeterWorkspaceConfig.statuses]
      .find(item => item.value === value)
    return option?.label || value || '未填写'
  }
}))

async function switchEnergyMeterView(viewId) {
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

function saveEnergyMeterView() {
  saveCurrentView()
  ElMessage.success(`视图“${activeViewName.value}”已保存`)
}

function removeEnergyMeterViewFilter(index) {
  removeViewFilter(index)
}

function updateEnergyMeterFilters(filters) {
  updateFilters(filters)
}

function resetEnergyMeterView() {
  resetTableView()
}

async function load() {
  const requestId = listRequestGuard.start()
  const params = {
    keyword: serverQueryParams.value.keyword || query.keyword || undefined,
    meterType: serverQueryParams.value.meterType || query.meterType || undefined,
    status: serverQueryParams.value.status || query.status || undefined,
    buildingId: serverQueryParams.value.buildingId || query.buildingId || undefined,
    sorts: serverQueryParams.value.sorts || undefined,
    groupBy: serverQueryParams.value.groupBy || undefined,
    ...subjectQueryParams.value,
    ...pageParams(pagination)
  }
  const requestContext = energyMeterListState.begin({ viewId: activeViewId.value, params })
  const metricRevision = energyMeterMetrics.begin()
  const listTask = getEnergyMeters(params, { silent: true }).then(
    value => applyEnergyMeterListResult(requestId, requestContext, metricRevision, { status: 'fulfilled', value }),
    reason => applyEnergyMeterListResult(requestId, requestContext, metricRevision, { status: 'rejected', reason })
  )
  const metricsTask = Promise.allSettled([
    loadListTotalResult('/energy/meters', subjectQueryParams.value),
    fetchSummary()
  ]).then(([totalResult, statsResult]) => {
    if (!listRequestGuard.isCurrent(requestId) || !energyMeterListState.isCurrent(requestContext)) return
    applyEnergyMeterTotalMetric(metricRevision, totalResult)
    applyEnergyMeterSummaryMetrics(metricRevision, statsResult)
  })

  await Promise.allSettled([listTask, metricsTask])
}

function applyEnergyMeterListResult(requestId, requestContext, metricRevision, result) {
  if (!listRequestGuard.isCurrent(requestId) || !energyMeterListState.isCurrent(requestContext)) return
  if (result.status === 'fulfilled') {
    const page = readPage(result.value)
    list.value = page.records
    pagination.total = page.total
    energyMeterListState.succeed(requestContext)
    energyMeterMetrics.resolve(metricRevision, 'filtered', page.total)
  } else {
    energyMeterListState.fail(requestContext, '表具列表加载失败，请稍后重试')
    energyMeterMetrics.reject(metricRevision, 'filtered')
  }
}

function buildMeterSummaryParams(overrides = {}) {
  return {
    keyword: serverQueryParams.value.keyword || query.keyword || undefined,
    status: serverQueryParams.value.status || query.status || undefined,
    buildingId: serverQueryParams.value.buildingId || query.buildingId || undefined,
    ...subjectQueryParams.value,
    ...overrides
  }
}

function fetchSummary() {
  return loadListTotalsDetailed([
    { key: 'electric', url: '/energy/meters', params: buildMeterSummaryParams({ meterType: 'ELECTRIC' }) },
    { key: 'water', url: '/energy/meters', params: buildMeterSummaryParams({ meterType: 'WATER' }) },
    { key: 'gas', url: '/energy/meters', params: buildMeterSummaryParams({ meterType: 'GAS' }) },
    { key: 'enabled', url: '/energy/meters', params: buildMeterSummaryParams({ meterType: serverQueryParams.value.meterType || query.meterType || undefined, status: 'ENABLED' }) },
    { key: 'disabled', url: '/energy/meters', params: buildMeterSummaryParams({ meterType: serverQueryParams.value.meterType || query.meterType || undefined, status: 'DISABLED' }) }
  ])
}

const energyMeterListState = useWorkspaceListRequestState({
  hasResults: () => list.value.length > 0,
  clearResults: () => {
    list.value = []
    pagination.total = 0
  }
})
const loading = computed(() => energyMeterListState.status.value === 'loading')
const listErrorText = energyMeterListState.errorText

function applyEnergyMeterTotalMetric(revision, result) {
  const detail = result.status === 'fulfilled' ? result.value : null
  if (detail?.ok) energyMeterMetrics.resolve(revision, 'total', detail.value)
  else energyMeterMetrics.reject(revision, 'total')
}

function applyEnergyMeterSummaryMetrics(revision, result) {
  const details = result.status === 'fulfilled' ? result.value : {}
  for (const key of ['electric', 'water', 'gas', 'enabled', 'disabled']) {
    if (details[key]?.ok) energyMeterMetrics.resolve(revision, key, details[key].value)
    else energyMeterMetrics.reject(revision, key)
  }
}

async function loadBaseData() {
  const [buildingResult, tenantResult] = await Promise.allSettled([
    hasPermission('building:view') || hasPermission('building:*')
      ? request.get('/buildings', { silent: true })
      : Promise.resolve([]),
    hasPermission('tenant:view') || hasPermission('tenant:*')
      ? request.get('/tenant/list', { silent: true })
      : Promise.resolve([])
  ])
  if (!pageActive) return
  buildings.value = buildingResult.status === 'fulfilled' ? readPage(buildingResult.value).records : []
  tenants.value = tenantResult.status === 'fulfilled' ? readPage(tenantResult.value).records : []
}

async function loadFloors(buildingId) {
  if (!buildingId) return []
  return readPage(await request.get('/floors', { params: { building_id: buildingId } })).records
}

async function loadRooms(buildingId, floorId) {
  if (!buildingId) return []
  return readPage(await request.get('/rooms', {
    params: {
      building_id: buildingId,
      floor_id: floorId || undefined,
      page: 1,
      pageSize: 100
    }
  })).records
}

function search() {
  resetToFirstPage(pagination)
  load()
}

function resetQuery() {
  query.keyword = ''
  query.meterType = ''
  query.status = ''
  query.buildingId = null
  resetToFirstPage(pagination)
  load()
}

watch(() => JSON.stringify(serverQueryParams.value), () => debouncedViewQueryLoad.schedule())

watch(
  () => [props.subjectScope, props.subjectTenantId],
  () => {
    if (!props.embedded || !pageActive) return
    resetToFirstPage(pagination)
    load()
  }
)

function handleSizeChange() {
  resetToFirstPage(pagination)
  load()
}

function positiveId(value) {
  const id = Number(value)
  return Number.isFinite(id) && id > 0 ? id : null
}

function tenantRoomLocation(room = {}) {
  return {
    buildingId: positiveId(room.buildingId ?? room.building_id ?? room.building?.id),
    floorId: positiveId(room.floorId ?? room.floor_id ?? room.floor?.id),
    roomId: positiveId(room.roomId ?? room.room_id ?? room.id)
  }
}

function preferredTenantRoom(records = []) {
  const closedStatuses = new Set(['TERMINATED', 'CANCELLED', 'EXPIRED', 'CHECKED_OUT'])
  return [...records]
    .filter(room => tenantRoomLocation(room).roomId)
    .sort((left, right) => {
      const score = room => {
        const statuses = [room.occupancyStatus, room.leaseStatus, room.contractStatus, room.status]
          .map(value => String(value || '').toUpperCase())
        if (statuses.some(status => closedStatuses.has(status))) return -100
        if (statuses.includes('ACTIVE')) return 20
        if (statuses.some(status => ['PARTIALLY_ACTIVE', 'RENEWING', 'PENDING_CHECKIN'].includes(status))) return 10
        return 0
      }
      return score(right) - score(left)
    })[0] || null
}

async function applyTenantLocationDefaults() {
  if (props.subjectScope !== 'tenant') return
  const tenantId = positiveId(props.subjectTenantId)
  if (!tenantId) return
  form.tenantId = tenantId
  try {
    const response = await request.get(`/tenants/${tenantId}/archive/spaces`, {
      params: { page: 1, pageSize: 100 },
      silent: true
    })
    const rooms = readPage(response?.rooms || response || []).records
    const preferredRoom = preferredTenantRoom(rooms)
    if (!preferredRoom) {
      ElMessage.warning('该租户暂无关联房间，请手动选择楼宇、楼层和房间')
      return
    }
    const preferredRoomId = tenantRoomLocation(preferredRoom).roomId
    const allRooms = await loadAllWorkspaceRecords(
      ({ page, pageSize }) => request.get('/rooms', {
        params: { page, pageSize },
        silent: true
      }),
      { pageSize: 100 }
    )
    const resolvedRoom = allRooms.find(room => positiveId(room.id) === preferredRoomId)
    const location = tenantRoomLocation(resolvedRoom)
    if (!location.buildingId || !location.floorId || !location.roomId) {
      ElMessage.warning('未能解析租户关联房间，请手动选择楼宇、楼层和房间')
      return
    }
    form.buildingId = location.buildingId
    formFloors.value = await loadFloors(location.buildingId)
    form.floorId = location.floorId
    formRooms.value = await loadRooms(location.buildingId, location.floorId)
    form.roomId = formRooms.value.some(room => positiveId(room.id) === location.roomId)
      ? location.roomId
      : null
    if (!form.roomId) {
      ElMessage.warning('租户关联房间已失效，请手动选择房间')
    }
  } catch (error) {
    ElMessage.warning(error?.response?.data?.message || '租户关联空间加载失败，请手动选择楼宇、楼层和房间')
  }
}
async function openDialog(row = null) {
  Object.assign(form, defaultForm(), {
    tenantId: props.subjectScope === 'tenant' ? positiveId(props.subjectTenantId) : null
  })
  formFloors.value = []
  formRooms.value = []
  if (row) {
    Object.assign(form, {
      id: row.id,
      meterNo: row.meterNo || '',
      meterName: row.meterName || '',
      meterType: row.meterType || 'ELECTRIC',
      buildingId: row.buildingId || null,
      floorId: row.floorId || null,
      roomId: row.roomId || null,
      tenantId: row.tenantId || form.tenantId,
      locationDescription: row.locationDescription || '',
      unit: row.unit || defaultUnit(row.meterType),
      multiplier: Number(row.multiplier || 1),
      initialReading: Number(row.initialReading || 0),
      commissioningDate: row.commissioningDate || null,
      status: row.status || 'ENABLED',
      remark: row.remark || ''
    })
    formFloors.value = await loadFloors(form.buildingId)
    formRooms.value = await loadRooms(form.buildingId, form.floorId)
  } else {
    await applyTenantLocationDefaults()
  }
  formRef.value?.clearValidate()
  dialogVisible.value = true
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

function syncDefaultUnit() {
  if (!form.unit || ['kWh', 'm³'].includes(form.unit)) {
    form.unit = defaultUnit(form.meterType)
  }
}

async function save() {
  await formRef.value.validate()
  saving.value = true
  try {
    const payload = { ...form }
    delete payload.id
    if (form.id) {
      await updateEnergyMeter(form.id, payload)
      ElMessage.success('表具已保存')
    } else {
      const initialSubjectType = props.subjectScope === 'tenant'
        ? 'TENANT'
        : (props.subjectScope === 'property' ? 'PROPERTY' : null)
      if (initialSubjectType) {
        Object.assign(payload, {
          subjectType: initialSubjectType,
          tenantId: initialSubjectType === 'TENANT' ? positiveId(props.subjectTenantId) : null,
          effectiveFrom: form.commissioningDate || currentDate()
        })
      }
      await createEnergyMeter(payload)
      ElMessage.success(initialSubjectType ? '表具及计量主体绑定已新增' : '表具已新增')
    }
    dialogVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

function currentDate() {
  const today = new Date()
  const year = today.getFullYear()
  const month = String(today.getMonth() + 1).padStart(2, '0')
  const day = String(today.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

async function remove(row) {
  await ElMessageBox.confirm(`确定删除表具「${row.meterNo}」吗？已有抄表记录的表具不会被删除。`, '删除确认', { type: 'warning' })
  await deleteEnergyMeter(row.id)
  ElMessage.success('表具已删除')
  if (list.value.length === 1 && pagination.currentPage > 1) {
    pagination.currentPage -= 1
  }
  await load()
}

async function openBindings(row) {
  bindingMeter.value = row
  bindingVisible.value = true
  bindingLoading.value = true
  try {
    formRooms.value = await loadRooms(row.buildingId, row.floorId)
    const response = await listEnergyMeterBindings(row.id)
    bindings.value = response?.data || response || []
    bindingEditorVisible.value = bindings.value.length === 0
    resetAttributionDraft()
  } finally {
    bindingLoading.value = false
  }
}

async function saveBinding() {
  if (!bindingMeter.value) return
  const validationMessage = validateAttributionDraft({
    current: currentAttribution.value,
    commissioningDate: bindingMeter.value.commissioningDate,
    draft: bindingForm
  })
  if (validationMessage) {
    ElMessage.warning(validationMessage)
    return
  }
  if (bindingForm.subjectType === 'TENANT' && !bindingForm.tenantId) {
    ElMessage.warning('请选择租户')
    return
  }
  bindingSaving.value = true
  try {
    const payload = {
      ...bindingForm,
      tenantId: bindingForm.subjectType === 'TENANT' ? bindingForm.tenantId : null
    }
    if (currentAttribution.value) await replaceEnergyMeterBinding(bindingMeter.value.id, payload)
    else await createEnergyMeterBinding(bindingMeter.value.id, payload)
    ElMessage.success(currentAttribution.value ? '计量归属已变更，历史记录保持不变' : '初始计量归属已设置')
    await openBindings(bindingMeter.value)
    await load()
  } finally {
    bindingSaving.value = false
  }
}

function resetAttributionDraft() {
  const current = currentAttribution.value
  Object.assign(bindingForm, {
    subjectType: current?.subjectType || (bindingMeter.value?.tenantId ? 'TENANT' : 'PROPERTY'),
    tenantId: current?.tenantId || bindingMeter.value?.tenantId || null,
    roomId: current?.roomId || bindingMeter.value?.roomId || null,
    effectiveFrom: current ? '' : (bindingMeter.value?.commissioningDate || currentDate()),
    remark: ''
  })
}

function startAttributionEditor() {
  resetAttributionDraft()
  bindingEditorVisible.value = true
}

function cancelAttributionEditor() {
  resetAttributionDraft()
  bindingEditorVisible.value = bindings.value.length === 0
}

function disableAttributionStartDate(date) {
  const candidate = dateParts(date)
  if (bindingMeter.value?.commissioningDate && candidate < bindingMeter.value.commissioningDate) return true
  return Boolean(currentAttribution.value?.effectiveFrom && candidate <= currentAttribution.value.effectiveFrom)
}

function dateParts(date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function attributionTypeLabel(subjectType) {
  return {
    PROPERTY: '物业公共计量',
    TENANT: '租户',
    ROOM: '房间',
    PUBLIC_AREA: '公共区域'
  }[subjectType] || subjectType || '-'
}

function attributionStatusLabel(attribution) {
  return attribution?.status === 'ACTIVE' && !attribution?.effectiveTo ? '当前归属' : '历史归属'
}

function attributionSummary(attribution) {
  if (!attribution) return '尚未设置'
  const parts = [attributionTypeLabel(attribution.subjectType)]
  if (attribution.tenantName) parts.push(attribution.tenantName)
  if (attribution.roomCode) parts.push(attribution.roomCode)
  return parts.join(' · ')
}

function tenantLabel(tenant) {
  return tenant.tenantName || tenant.name || `租户${tenant.id}`
}

function formatFloor(floor) {
  return floor.floorName || `${floor.floorNumber}F`
}

function formatRoom(room) {
  return room.roomName || room.roomNumber || `房间${room.id}`
}

function defaultUnit(type) {
  return type === 'ELECTRIC' ? 'kWh' : 'm³'
}

onMounted(async () => {
  await Promise.all([loadBaseData(), load()])
})

onBeforeUnmount(() => {
  pageActive = false
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

.workspace-quick-search {
  width: 260px;
}

.view-hint {
  color: #7b8494;
  font-size: 12px;
  white-space: nowrap;
}

:deep(.density-compact .el-table__cell) {
  padding: 4px 0;
}

:deep(.density-comfortable .el-table__cell) {
  padding: 12px 0;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 12px;
}

.field-help {
  margin-top: 5px;
  color: var(--dhic-text-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.commissioning-date-field {
  align-self: start;
}

.attribution-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 14px;
  padding: 14px 16px;
  border: 1px solid var(--dhic-border-color-light);
  border-radius: var(--dhic-radius-large);
  background: var(--dhic-info-light);
}

.attribution-summary__eyebrow {
  margin-bottom: 4px;
  color: var(--dhic-text-secondary);
  font-size: 12px;
}

.attribution-summary__title {
  display: block;
  color: var(--dhic-text-primary);
  font-size: 16px;
  line-height: 1.5;
}

.attribution-summary__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 16px;
  margin-top: 4px;
  color: var(--dhic-text-secondary);
  font-size: 12px;
}
</style>
