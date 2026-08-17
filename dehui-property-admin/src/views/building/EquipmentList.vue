<template>
  <div class="page-container">
      <DataWorkspace
        :workspace-config="equipmentWorkspaceConfig"
        :views="workspaceViews"
        :active-view-id="activeViewId"
        :view-dirty="isViewDirty"
        :filter-logic="tablePrefs.filterLogic"
        :filter-logic-visible="tablePrefs.filters.length > 1"
        :filter-items="activeFilterItems"
        :query-notices="queryNotices"
        :selected-count="selectedRows.length"
        :metric-data="equipmentMetricData"
        :metric-states="equipmentMetricStates"
        @change-view="switchEquipmentView"
        @create-view="createTableView"
        @duplicate-view="duplicateTableView"
        @rename-view="renameTableView"
        @delete-view="deleteTableView"
        @save-view="saveEquipmentView"
        @remove-filter="removeEquipmentViewFilter"
        @batch-action="runEquipmentBatchAction"
        @clear-selection="clearEquipmentSelection"
      >
        <template #header-actions>
          <el-button v-if="hasPermission(equipmentPermissions.create)" type="primary" :icon="Plus" @click="openDialog()">
            {{ equipmentWorkspaceConfig.primaryAction.label }}
          </el-button>
        </template>

        <template #toolbar>
          <DhicDataToolbar
            :workspace-config="equipmentWorkspaceConfig"
            allow-custom-fields
            allow-column-layout
            field-config-saveable
            :view-name="activeViewName"
            :field-config-dirty="isViewDirty"
            :columns="orderedColumns"
            :visible-columns="tablePrefs.visibleColumns"
            :column-order="tablePrefs.columnOrder"
            :fixed-columns="tablePrefs.fixedColumns"
            :custom-columns="tablePrefs.customColumns"
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
            @update:custom-columns="updateCustomColumns"
            @update:filters="updateEquipmentFilters"
            @update:filter-logic="updateFilterLogic"
            @update:sort-key="updateSortKey"
            @update:sort-order="updateSortOrder"
            @update:sorts="updateSorts"
            @update:group-key="updateGroupKey"
            @update:groups="updateGroups"
            @update:row-density="updateRowDensity"
            @reset-view="resetEquipmentView"
            @save-field-config="saveEquipmentView"
            @workspace-action="handleEquipmentExport"
            @workspace-file="handleImportEquipmentFile"
          >
            <template #right>
              <el-input
                v-model.trim="query.keyword"
                clearable
                :prefix-icon="Search"
                class="workspace-quick-search"
                placeholder="搜索设备名称、编号、位置"
                aria-label="快速搜索设备"
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
            ref="equipmentTableRef"
            :loading="loading"
            :error-text="listErrorText"
            retryable
            :rows="tableRows"
            :columns="visibleOrderedColumns"
            row-key="id"
            :selected-row-keys="selectedRowKeys"
            show-selection
            :selectable="selectableEquipmentRow"
            :current-page="pagination.currentPage"
            :page-size="pagination.pageSize"
            :index-width="80"
            index-fixed="left"
            :density="tablePrefs.rowDensity"
            :empty-text="tableEmptyText"
            :sort-state="activeTableSort"
            :data-mode="equipmentWorkspaceConfig.query.dataMode"
            :row-class-name="tableRowClassName"
            :column-width="columnWidth"
            :column-fixed="columnFixed"
            :is-column-sortable="isColumnSortable"
            :show-overflow-tooltip="column => column.key === 'remark'"
            :can-edit-cell="canEditEquipmentCell"
            :cell-saving-key="inlineSavingCell"
            :cell-key="inlineCellKey"
            :edit-context-key="activeViewId"
            :operation-column="{ width: 176, fixed: 'right', align: 'left' }"
            @selection-change="handleSelectionChange"
            @column-resize="handleHeaderDragEnd"
            @sort-change="handleTableSortChange"
            @cell-save="handleEquipmentCellSave"
            @retry="load"
            aria-label="设备台账数据表格"
          >
            <template #operations="{ row }">
              <el-button v-if="hasPermission(equipmentPermissions.update)" size="small" type="primary" @click="edit(row)">编辑</el-button>
              <el-dropdown
                v-if="equipmentSecondaryActions(row).length"
                trigger="click"
                popper-class="equipment-row-actions-menu"
                @command="action => runEquipmentSecondaryAction(row, action)"
              >
                <el-button size="small" :icon="MoreFilled">更多</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item
                      v-for="action in equipmentSecondaryActions(row)"
                      :key="action.id"
                      :command="action"
                      :divided="action.divided"
                      :class="{ 'equipment-danger-action': action.tone === 'danger' }"
                    >
                      {{ action.label }}
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
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

    <el-dialog v-model="visible" :title="form.id ? '编辑设备' : '新增设备'" width="620px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="设备名称" prop="equipmentName">
          <el-input v-model.trim="form.equipmentName" placeholder="请输入设备名称" />
        </el-form-item>
        <el-form-item label="设备编号">
          <el-input v-model.trim="form.equipmentCode" placeholder="如 EQ-001" />
        </el-form-item>
        <el-form-item label="设备类型">
          <el-select v-model="form.equipmentType" filterable allow-create placeholder="请选择设备类型" style="width: 100%">
            <el-option
              v-for="option in equipmentTypeSelectOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="设备状态">
          <el-select v-model="form.status" placeholder="请选择设备状态" style="width: 100%">
            <el-option
              v-for="option in statusOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="位置">
          <el-input v-model.trim="form.location" placeholder="如 1F大厅 / B1设备房" />
        </el-form-item>
        <el-form-item label="所属楼宇">
          <el-select v-model="form.buildingId" clearable style="width: 100%" @change="onEquipmentBuildingChange">
            <el-option v-for="item in buildings" :key="item.id" :label="item.buildingName || item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="所属楼层">
          <el-select v-model="form.floorId" clearable :disabled="!form.buildingId" style="width: 100%" @change="onEquipmentFloorChange">
            <el-option v-for="item in formFloors" :key="item.id" :label="item.floorName || `${item.floorNumber}F`" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="所属房间">
          <el-select v-model="form.roomId" clearable :disabled="!form.floorId" style="width: 100%">
            <el-option v-for="item in formRooms" :key="item.id" :label="item.roomNumber || item.roomName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="厂家">
          <el-input v-model.trim="form.manufacturer" />
        </el-form-item>
        <el-form-item label="型号">
          <el-input v-model.trim="form.model" />
        </el-form-item>
        <el-form-item label="序列号">
          <el-input v-model.trim="form.serialNo" />
        </el-form-item>
        <el-form-item label="安装日期">
          <el-date-picker
            v-model="form.installDate"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="责任部门">
          <el-input v-model.trim="form.responsibleDepartment" />
        </el-form-item>
        <el-form-item label="使用年限">
          <el-input-number v-model="form.serviceLifeYears" :min="0" :precision="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="设备图片">
          <CustomFieldValueInput
            v-model="form.imageFileIds"
            :field="{ type: 'image', workspaceKey: 'equipment', maxFiles: 9 }"
          />
        </el-form-item>
        <el-form-item label="设备附件">
          <CustomFieldValueInput
            v-model="form.attachmentFileIds"
            :field="{ type: 'attachment', workspaceKey: 'equipment', maxFiles: 10 }"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model.trim="form.remark" type="textarea" :rows="3" />
        </el-form-item>
        <CustomFieldEditor
          v-if="activeCustomFields.length"
          v-model="form.customFields"
          :columns="activeCustomFields"
        />
      </el-form>

      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button v-if="form.id ? hasPermission(equipmentPermissions.update) : hasPermission(equipmentPermissions.create)" type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="batchEditVisible" :title="`批量编辑 ${selectedRows.length} 台设备`" width="500px">
      <el-form label-width="90px">
        <el-form-item label="修改字段" required>
          <el-select v-model="batchEditForm.field" style="width: 100%" @change="batchEditForm.value = ''">
            <el-option
              v-for="field in batchEditableFields"
              :key="field.key"
              :label="field.label"
              :value="field.key"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="activeBatchEditField?.label || '字段值'" required>
          <el-select
            v-if="activeBatchEditField?.type === 'select'"
            v-model="batchEditForm.value"
            style="width: 100%"
          >
            <el-option
              v-for="option in activeBatchEditField.options"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
          <el-date-picker
            v-else-if="activeBatchEditField?.type === 'date'"
            v-model="batchEditForm.value"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
          <el-input
            v-else
            v-model.trim="batchEditForm.value"
            :type="activeBatchEditField?.type === 'textarea' ? 'textarea' : 'text'"
            :rows="activeBatchEditField?.type === 'textarea' ? 3 : undefined"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchEditVisible = false">取消</el-button>
        <el-button type="primary" :loading="batchEditing" @click="submitBatchEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MoreFilled, Plus, RefreshLeft, Search } from '@element-plus/icons-vue'
import request from '../../utils/request'
import { hasPermission } from '../../utils/permission'
import { createPagination, pageParams, readPage, resetToFirstPage } from '../../utils/pagination'
import { downloadBlob } from '../../utils/downloadBlob'
import { buildCsvText } from '../../utils/csvExport'
import { loadAllWorkspaceRecords } from '../../utils/workspaceExport'
import { buildEquipmentImportRows } from '../../utils/equipmentImportMapping'
import { buildEquipmentUpdatePayload } from '../../utils/equipmentEdit'
import {
  buildCustomFieldForm,
  formatCustomFieldValue
} from '../../utils/customFieldValues'
import jszipScriptUrl from 'jszip/dist/jszip.min.js?url'

import { loadListTotalResult, loadListTotalsDetailed } from '../../utils/listSummary'
import { useDhicTableView } from '../../composables/useDhicTableView'
import {
  useWorkspaceListRequestState,
  useWorkspaceMetricState
} from '../../composables/workspace/useWorkspaceRequestState'
import CustomFieldEditor from '../../components/CustomFieldEditor.vue'
import CustomFieldValueInput from '../../components/workspace/CustomFieldValueInput.vue'
import DataWorkspace from '../../components/DataWorkspace.vue'
import DhicDataToolbar from '../../components/DhicDataToolbar.vue'
import WorkspaceDataGrid from '../../components/workspace/WorkspaceDataGrid.vue'
import { createEquipmentWorkspaceConfig, equipmentTypeOptions } from '../../workspaceConfigs/equipment'
import { workspaceTableOptions } from '../../workspaceConfigs/workspaceConfig'
let jsZipLoadPromise = null

const query = reactive({
  keyword: '',
  equipmentType: '',
  status: ''
})

const list = ref([])
const saving = ref(false)
const batchEditing = ref(false)
const inlineSavingCell = ref('')
const visible = ref(false)
const batchEditVisible = ref(false)
const batchEditForm = reactive({ field: 'status', value: '' })
const pagination = reactive(createPagination(20))
const equipmentMetrics = useWorkspaceMetricState([
  'total',
  'filtered',
  'normal',
  'maintenance',
  'fault',
  'disabled'
])
const equipmentMetricData = equipmentMetrics.values
const equipmentMetricStates = equipmentMetrics.states
const formRef = ref(null)
const equipmentTableRef = ref(null)
const buildings = ref([])
const formFloors = ref([])
const formRooms = ref([])

const equipmentWorkspaceConfig = createEquipmentWorkspaceConfig({
  typeOptions: () => equipmentTypeSelectOptions.value
})
const equipmentPermissions = equipmentWorkspaceConfig.permissions
const tableColumns = equipmentWorkspaceConfig.fields
const statusOptions = equipmentWorkspaceConfig.statuses

const equipmentTypeSelectOptions = computed(() => {
  const options = [...equipmentTypeOptions]
  const existingValues = [
    ...list.value.map(item => item.equipmentType),
    form.equipmentType,
    query.equipmentType
  ].filter(Boolean)

  existingValues.forEach(value => {
    const normalizedValue = String(value)
    if (!options.some(option => option.value === normalizedValue)) {
      options.push({ label: normalizedValue, value: normalizedValue })
    }
  })
  return options
})

const batchEditableFields = computed(() => [
  { key: 'equipmentType', label: '设备类型', type: 'select', options: equipmentTypeSelectOptions.value },
  { key: 'status', label: '设备状态', type: 'select', options: statusOptions },
  { key: 'location', label: '位置', type: 'text' },
  { key: 'manufacturer', label: '厂家', type: 'text' },
  { key: 'model', label: '型号', type: 'text' },
  { key: 'installDate', label: '安装日期', type: 'date' },
  { key: 'remark', label: '备注', type: 'textarea' }
])
const activeBatchEditField = computed(() => batchEditableFields.value.find(field => field.key === batchEditForm.field))

const defaultForm = () => ({
  id: null,
  equipmentName: '',
  equipmentCode: '',
  equipmentType: 'ELEVATOR',
  status: 'NORMAL',
  location: '',
  buildingId: null,
  floorId: null,
  roomId: null,
  manufacturer: '',
  model: '',
  serialNo: '',
  installDate: '',
  responsibleDepartment: '',
  serviceLifeYears: null,
  imageFileIds: [],
  attachmentFileIds: [],
  remark: '',
  customFields: {}
})

const form = reactive(defaultForm())

const rules = {
  equipmentName: [{ required: true, message: '请输入设备名称', trigger: 'blur' }]
}
const {
  workspaceViews,
  activeViewId,
  activeViewName,
  isViewDirty,
  tablePrefs,
  activeCustomFields,
  saveCustomFieldRecord,
  allColumns,
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
  isColumnVisible,
  updateVisibleColumns,
  updateColumnOrder,
  updateFixedColumns,
  columnWidth,
  columnFixed,
  handleHeaderDragEnd,
  handleTableSortChange,
  isColumnSortable,
  updateCustomColumns,
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
} = useDhicTableView(workspaceTableOptions(equipmentWorkspaceConfig, {
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
    selectable: selectableEquipmentRow
  },
  filters: () => {
    const items = []
    const viewFilterKeys = new Set((tablePrefs.filters || []).map(filter => filter.key))
    if (query.keyword && !viewFilterKeys.has('keyword')) items.push({ label: '关键词', value: query.keyword })
    if (query.equipmentType && !viewFilterKeys.has('equipmentType')) items.push({ label: '设备类型', value: equipmentTypeText(query.equipmentType) })
    if (query.status && !viewFilterKeys.has('status')) items.push({ label: '状态', value: statusText(query.status) })
    return items
  },
  groupLabel: value => {
    if (equipmentTypeOptions.some(item => item.value === value)) return equipmentTypeText(value)
    if (statusOptions.some(item => item.value === value)) return statusText(value)
    if (value === '__EMPTY__') return '未填写'
    return value || '未填写'
  }
}))

function equipmentTypeText(value) {
  return equipmentTypeOptions.find(item => item.value === value)?.label || value || '-'
}

function statusText(value) {
  return statusOptions.find(item => item.value === value)?.label || value || '-'
}

function equipmentSecondaryActions(row) {
  const actions = []
  if (hasPermission(equipmentPermissions.update)) {
    statusOptions
      .filter(option => option.value !== row.status)
      .forEach(option => actions.push({
        id: `status:${option.value}`,
        type: 'status',
        value: option.value,
        label: `状态改为${option.label}`
      }))
  }
  if (hasPermission(equipmentPermissions.delete)) {
    actions.push({
      id: 'delete',
      type: 'delete',
      label: '删除设备',
      tone: 'danger',
      divided: actions.length > 0
    })
  }
  return actions
}

function runEquipmentSecondaryAction(row, action) {
  if (action?.type === 'status' && hasPermission(equipmentPermissions.update)) {
    updateStatus(row, action.value)
    return
  }
  if (action?.type === 'delete' && hasPermission(equipmentPermissions.delete)) {
    deleteEquipment(row)
  }
}

function customFieldValue(row, column) {
  return formatCustomFieldValue(row?.customFields?.[column.key] ?? column.defaultValue, column)
}

function equipmentColumnText(row, column) {
  const value = row?.[column.key]
  return value === undefined || value === null || value === '' ? '-' : value
}

function clearEquipmentSelection() {
  equipmentTableRef.value?.clearSelection()
  clearWorkspaceSelection()
}

function runEquipmentBatchAction(actionId) {
  if (actionId === 'edit') openBatchEdit()
  else if (actionId === 'export') batchExportEquipment()
  else if (actionId === 'delete') batchDeleteEquipment()
}

async function switchEquipmentView(viewId) {
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
  clearEquipmentSelection()
}

function saveEquipmentView() {
  saveCurrentView()
  ElMessage.success(`视图“${activeViewName.value}”已保存`)
}

function removeEquipmentViewFilter(index) {
  removeViewFilter(index)
}

function updateEquipmentFilters(filters) {
  updateFilters(filters)
}

function resetEquipmentView() {
  resetTableView()
  clearEquipmentSelection()
}

function tableRowClassName({ row }) {
  if (row.status === 'FAULT') return 'row-danger'
  if (row.status === 'MAINTENANCE') return 'row-warning'
  if (row.status === 'DISABLED') return 'row-muted'
  return ''
}

function buildQueryParams(overrides = {}) {
  const params = {
    ...pageParams(pagination)
  }
  const keyword = serverQueryParams.value.keyword || query.keyword
  const equipmentType = serverQueryParams.value.equipmentType || query.equipmentType
  const status = serverQueryParams.value.status || query.status
  if (keyword) params.keyword = keyword
  if (equipmentType) params.equipmentType = equipmentType
  if (status) params.status = status
  if (serverQueryParams.value.customFilters) params.customFilters = serverQueryParams.value.customFilters
  if (serverQueryParams.value.sorts) params.sorts = serverQueryParams.value.sorts
  if (serverQueryParams.value.groupBy) params.groupBy = serverQueryParams.value.groupBy
  return { ...params, ...overrides }
}

watch(() => JSON.stringify(serverQueryParams.value), () => debouncedViewQueryLoad.schedule())

function buildSummaryParams(overrides = {}) {
  return {
    keyword: serverQueryParams.value.keyword || query.keyword || undefined,
    equipmentType: serverQueryParams.value.equipmentType || query.equipmentType || undefined,
    status: serverQueryParams.value.status || query.status || undefined,
    customFilters: serverQueryParams.value.customFilters || undefined,
    ...overrides
  }
}

function fetchSummary() {
  return loadListTotalsDetailed([
    { key: 'normal', url: '/equipments', params: buildSummaryParams({ status: 'NORMAL' }) },
    { key: 'maintenance', url: '/equipments', params: buildSummaryParams({ status: 'MAINTENANCE' }) },
    { key: 'fault', url: '/equipments', params: buildSummaryParams({ status: 'FAULT' }) },
    { key: 'disabled', url: '/equipments', params: buildSummaryParams({ status: 'DISABLED' }) }
  ])
}

const equipmentListState = useWorkspaceListRequestState({
  hasResults: () => list.value.length > 0,
  clearResults: () => {
    list.value = []
    pagination.total = 0
    clearWorkspaceSelection()
  }
})
const loading = computed(() => equipmentListState.status.value === 'loading')
const listErrorText = equipmentListState.errorText

function resetForm() {
  Object.assign(form, defaultForm())
  formRef.value?.clearValidate()
}

function search() {
  resetToFirstPage(pagination)
  load()
}

function resetQuery() {
  query.keyword = ''
  query.equipmentType = ''
  query.status = ''
  resetToFirstPage(pagination)
  load()
}

async function load() {
  const requestId = listRequestGuard.start()
  const params = buildQueryParams()
  const requestContext = equipmentListState.begin({ viewId: activeViewId.value, params })
  const metricRevision = equipmentMetrics.begin()
  const listPromise = request.get('/equipments', { params, silent: true }).then(data => {
    const page = readPage(data)
    return {
      page,
      records: page.records
    }
  })
  const listTask = Promise.resolve(listPromise).then(
    result => applyEquipmentListResult(requestId, requestContext, metricRevision, { status: 'fulfilled', value: result }),
    reason => applyEquipmentListResult(requestId, requestContext, metricRevision, { status: 'rejected', reason })
  )
  const metricsTask = Promise.allSettled([
    loadListTotalResult('/equipments'),
    fetchSummary()
  ]).then(([totalResult, statsResult]) => {
    if (!listRequestGuard.isCurrent(requestId) || !equipmentListState.isCurrent(requestContext)) return
    applyEquipmentTotalMetric(metricRevision, totalResult)
    applyEquipmentSummaryMetrics(metricRevision, statsResult)
  })

  await Promise.allSettled([listTask, metricsTask])
}

function applyEquipmentListResult(requestId, requestContext, metricRevision, result) {
  if (!listRequestGuard.isCurrent(requestId) || !equipmentListState.isCurrent(requestContext)) return
  if (result.status === 'fulfilled') {
    list.value = result.value.records
    pagination.total = result.value.page.total
    clearWorkspaceSelection()
    equipmentListState.succeed(requestContext)
    equipmentMetrics.resolve(metricRevision, 'filtered', result.value.page.total)
  } else {
    equipmentListState.fail(requestContext, '设备列表加载失败，请稍后重试')
    equipmentMetrics.reject(metricRevision, 'filtered')
  }
}

function applyEquipmentTotalMetric(revision, result) {
  const detail = result.status === 'fulfilled' ? result.value : null
  if (detail?.ok) equipmentMetrics.resolve(revision, 'total', detail.value)
  else equipmentMetrics.reject(revision, 'total')
}

function applyEquipmentSummaryMetrics(revision, result) {
  const details = result.status === 'fulfilled' ? result.value : {}
  for (const key of ['normal', 'maintenance', 'fault', 'disabled']) {
    if (details[key]?.ok) equipmentMetrics.resolve(revision, key, details[key].value)
    else equipmentMetrics.reject(revision, key)
  }
}

function handleSizeChange() {
  resetToFirstPage(pagination)
  load()
}

function openDialog(row = null) {
  resetForm()
  if (row) {
    edit(row)
    return
  }
  visible.value = true
}

function edit(row) {
  Object.assign(form, {
    id: row.id,
    equipmentName: row.equipmentName || '',
    equipmentCode: row.equipmentCode || '',
    equipmentType: row.equipmentType || 'ELEVATOR',
    status: row.status || 'NORMAL',
    location: row.location || '',
    buildingId: row.buildingId || null,
    floorId: row.floorId || null,
    roomId: row.roomId || null,
    manufacturer: row.manufacturer || '',
    model: row.model || '',
    serialNo: row.serialNo || '',
    installDate: row.installDate || '',
    responsibleDepartment: row.responsibleDepartment || '',
    serviceLifeYears: row.serviceLifeYears ?? null,
    imageFileIds: row.imageFileIds || [],
    attachmentFileIds: row.attachmentFileIds || [],
    remark: row.remark || '',
    customFields: buildCustomFieldForm(activeCustomFields.value, row.customFields || {})
  })
  loadEquipmentLocationOptions()
  formRef.value?.clearValidate()
  visible.value = true
}

async function loadEquipmentLocationOptions() {
  if (!buildings.value.length) {
    const response = await request.get('/buildings', { silent: true })
    buildings.value = response || []
  }
  if (form.buildingId) {
    const floors = await request.get('/floors', { params: { building_id: form.buildingId }, silent: true })
    formFloors.value = floors || []
  } else {
    formFloors.value = []
  }
  if (form.floorId) {
    const rooms = await request.get('/rooms', {
      params: { building_id: form.buildingId, floor_id: form.floorId, page: 1, pageSize: 500 },
      silent: true
    })
    formRooms.value = readPage(rooms).records
  } else {
    formRooms.value = []
  }
}

async function onEquipmentBuildingChange() {
  form.floorId = null
  form.roomId = null
  await loadEquipmentLocationOptions()
}

async function onEquipmentFloorChange() {
  form.roomId = null
  await loadEquipmentLocationOptions()
}

async function save() {
  await formRef.value.validate()

  saving.value = true
  try {
    const { customFields, ...payload } = { ...form }
    delete payload.id

    if (form.id) {
      await request.put(`/equipments/${form.id}`, payload)
      if (activeCustomFields.value.length) {
        const result = await saveCustomFieldRecord({ id: form.id, customFields }, customFields)
        if (result.success === false) throw new Error(result.message || '自定义字段保存失败')
      }
      ElMessage.success('设备保存成功')
    } else {
      await request.post('/equipments', payload)
      ElMessage.success('设备新增成功')
    }

    visible.value = false
    resetForm()
    await load()
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '设备保存失败')
  } finally {
    saving.value = false
  }
}

async function exportEquipmentTemplate() {
  const blob = await request.get('/equipments/template', { responseType: 'blob' })
  downloadBlob(blob, '设备台账导入模板.csv')
  ElMessage.success('模板下载已开始')
}

async function handleEquipmentExport(command) {
  if (command === 'export-template') {
    await exportEquipmentTemplate()
    return
  }
  if (command === 'export-current') exportCurrentPageEquipment()
}

async function exportCurrentPageEquipment() {
  try {
    const rows = await loadAllWorkspaceRecords(({ page, pageSize }) => request.get('/equipments', {
      params: buildQueryParams({ page, pageSize }),
      silent: true
    }))
    if (!rows.length) {
      ElMessage.warning('当前查询没有可导出的设备')
      return
    }
    exportEquipmentRows(rows, `设备台账-当前查询-${localDateStamp()}.csv`)
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || '设备导出失败')
  }
}

async function handleImportEquipmentFile({ actionId, file }) {
  if (actionId !== 'import' || !file) return

  try {
    const rows = (await parseEquipmentImportFile(file)).filter(row => row.some(cell => String(cell || '').trim()))
    if (rows.length < 2) {
      ElMessage.warning('导入文件没有可读取的数据')
      return
    }

    const importRows = buildEquipmentImportRows(rows)
    if (!importRows.length) {
      ElMessage.warning('没有找到可导入的设备数据')
      return
    }

    const data = await request.post('/equipments/import', { rows: importRows })
    const insertedCount = data.insertedCount ?? data.successCount ?? 0
    const updatedCount = data.updatedCount || 0
    const restoredCount = data.restoredCount || 0
    const skippedCount = data.skippedCount || 0
    const failureCount = data.failureCount || 0
    ElMessage.success(`导入完成：新增 ${insertedCount} 条，更新 ${updatedCount} 条，恢复 ${restoredCount} 条，跳过 ${skippedCount} 条，失败 ${failureCount} 条。`)
    const errors = Array.isArray(data.errors) ? data.errors : data.failures
    if (data.failureCount > 0 && Array.isArray(errors) && errors.length) {
      ElMessage.warning(`首条失败：${errors[0].message}`)
    }
    await load()
  } catch (error) {
    ElMessage.error(error?.message || error?.response?.data?.message || '导入设备失败')
  }
}

async function parseEquipmentImportFile(file) {
  if (isLegacyExcelFile(file)) {
    throw new Error('暂不支持旧版 .xls 文件，请在 Excel 或 WPS 中另存为 .xlsx 或 CSV 后再导入')
  }
  if (isXlsxFile(file)) {
    return parseXlsx(await file.arrayBuffer())
  }
  return parseCsv(await file.text())
}

function isXlsxFile(file) {
  const name = String(file?.name || '').toLowerCase()
  const type = String(file?.type || '').toLowerCase()
  return name.endsWith('.xlsx') || type.includes('spreadsheetml')
}

function isLegacyExcelFile(file) {
  const name = String(file?.name || '').toLowerCase()
  return name.endsWith('.xls')
}

async function loadJsZip() {
  if (window.JSZip) return window.JSZip
  if (!jsZipLoadPromise) {
    jsZipLoadPromise = new Promise((resolve, reject) => {
      const script = document.createElement('script')
      script.src = jszipScriptUrl
      script.async = true
      script.dataset.dhicJszip = 'true'
      script.onload = () => {
        if (window.JSZip) {
          resolve(window.JSZip)
          return
        }
        jsZipLoadPromise = null
        reject(new Error('Excel 解析组件加载失败，请刷新页面后重试'))
      }
      script.onerror = () => {
        jsZipLoadPromise = null
        reject(new Error('Excel 解析组件加载失败，请刷新页面后重试'))
      }
      document.head.appendChild(script)
    })
  }
  return jsZipLoadPromise
}

async function parseXlsx(buffer) {
  const JSZip = await loadJsZip()
  const zip = await JSZip.loadAsync(buffer)
  const [sheetPaths, sharedStrings] = await Promise.all([
    worksheetPaths(zip),
    readSharedStrings(zip)
  ])
  const sheetRows = await Promise.all(sheetPaths.map(async sheetPath => {
    const sheetFile = zip.file(sheetPath)
    if (!sheetFile) return []

    const document = parseXml(await sheetFile.async('text'))
    return Array.from(document.getElementsByTagName('row')).map(rowNode => {
      const row = []
      Array.from(rowNode.getElementsByTagName('c')).forEach((cell, index) => {
        const columnIndex = cellColumnIndex(cell.getAttribute('r'))
        row[columnIndex >= 0 ? columnIndex : index] = readXlsxCellValue(cell, sharedStrings)
      })
      return row.map(value => String(value ?? '').trim())
    })
  }))
  return sheetRows.flat()
}

async function worksheetPaths(zip) {
  const workbookXml = await zip.file('xl/workbook.xml')?.async('text')
  const relsXml = await zip.file('xl/_rels/workbook.xml.rels')?.async('text')
  if (workbookXml && relsXml) {
    const workbook = parseXml(workbookXml)
    const relationships = parseXml(relsXml)
    const relationshipById = new Map(Array.from(relationships.getElementsByTagName('Relationship'))
      .map(item => [item.getAttribute('Id'), item.getAttribute('Target')]))
    const paths = Array.from(workbook.getElementsByTagName('sheet'))
      .map(sheet => relationshipById.get(sheet.getAttribute('r:id')))
      .filter(Boolean)
      .map(target => {
        if (target.startsWith('/')) return target.slice(1)
        if (target.startsWith('xl/')) return target
        return `xl/${target}`
      })
    if (paths.length) return paths
  }
  return ['xl/worksheets/sheet1.xml']
}

async function readSharedStrings(zip) {
  const file = zip.file('xl/sharedStrings.xml')
  if (!file) return []
  const document = parseXml(await file.async('text'))
  return Array.from(document.getElementsByTagName('si')).map(item =>
    Array.from(item.getElementsByTagName('t')).map(text => text.textContent || '').join('')
  )
}

function parseXml(xml) {
  return new DOMParser().parseFromString(xml, 'application/xml')
}

function readXlsxCellValue(cell, sharedStrings) {
  const type = cell.getAttribute('t')
  if (type === 'inlineStr') {
    return Array.from(cell.getElementsByTagName('t')).map(text => text.textContent || '').join('')
  }
  const rawValue = cell.getElementsByTagName('v')[0]?.textContent || ''
  if (type === 's') {
    return sharedStrings[Number(rawValue)] ?? ''
  }
  return rawValue
}

function cellColumnIndex(reference) {
  const letters = String(reference || '').match(/^[A-Z]+/i)?.[0] || ''
  if (!letters) return -1
  return letters.toUpperCase().split('').reduce((total, char) => total * 26 + char.charCodeAt(0) - 64, 0) - 1
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
      if (char === '\r' && next === '\n') index += 1
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

function selectableEquipmentRow(row) {
  return !row.__group && Boolean(row.id)
}

function openBatchEdit() {
  const rows = selectedRows.value.filter(selectableEquipmentRow)
  if (!rows.length) {
    ElMessage.warning('请先勾选要编辑的设备')
    return
  }
  batchEditForm.field = 'status'
  batchEditForm.value = ''
  batchEditVisible.value = true
}

async function submitBatchEdit() {
  const rows = selectedRows.value.filter(selectableEquipmentRow)
  const field = activeBatchEditField.value
  const value = batchEditForm.value
  if (!rows.length || !field) {
    ElMessage.warning('请选择要编辑的设备和字段')
    return
  }
  if (value === undefined || value === null || String(value).trim() === '') {
    ElMessage.warning(`请选择或填写${field.label}`)
    return
  }

  try {
    await ElMessageBox.confirm(
      `确定将已选 ${rows.length} 台设备的${field.label}统一修改吗？`,
      '批量编辑确认',
      { type: 'warning' }
    )
  } catch (error) {
    return
  }

  batchEditing.value = true
  try {
    const results = await Promise.allSettled(rows.map(row => updateEquipmentField(row, field.key, value)))
    const successCount = results.filter(result => result.status === 'fulfilled').length
    const failureCount = results.length - successCount

    if (failureCount === 0) {
      ElMessage.success(`已更新 ${successCount} 台设备`)
    } else if (successCount > 0) {
      ElMessage.warning(`已更新 ${successCount} 台设备，失败 ${failureCount} 台`)
    } else {
      ElMessage.error('批量编辑失败')
    }

    if (successCount > 0) {
      batchEditVisible.value = false
      clearEquipmentSelection()
      await load()
    }
  } finally {
    batchEditing.value = false
  }
}

function updateEquipmentField(row, field, value) {
  if (field === 'status') {
    return request.patch(`/equipments/${row.id}/status?status=${encodeURIComponent(value)}`)
  }

  return request.put(`/equipments/${row.id}`, buildEquipmentUpdatePayload(row, field, value))
}

function inlineCellKey(row, column) {
  return `${row.id}:${column.key}`
}

function canEditEquipmentCell(_row, column) {
  return hasPermission(equipmentPermissions.update)
    && column.editable === true
    && (!column.custom || column.platformCustom === true)
}

async function handleEquipmentCellSave({ row, field, value, complete }) {
  complete(await saveInlineCell(row, field, value))
}

async function saveInlineCell(row, column, value) {
  if (!hasPermission(equipmentPermissions.update) || column.editable !== true) {
    return { success: false, message: '无权编辑该字段' }
  }
  if (column.required && String(value ?? '').trim() === '') {
    ElMessage.warning(`${column.label}不能为空`)
    return { success: false, message: `${column.label}不能为空` }
  }

  inlineSavingCell.value = inlineCellKey(row, column)
  try {
    if (column.custom) {
      const nextCustomFields = { ...(row.customFields || {}), [column.key]: value }
      const result = await saveCustomFieldRecord(row, nextCustomFields)
      if (result.success === false) return result
    } else {
      await updateEquipmentField(row, column.key, value)
    }
    ElMessage.success(`${column.label}已更新`)
    await load()
    return { success: true }
  } catch (_error) {
    const message = `${column.label}更新失败，请稍后重试`
    ElMessage.error(message)
    return { success: false, message }
  } finally {
    inlineSavingCell.value = ''
  }
}

function batchExportEquipment() {
  const rows = selectedRows.value.filter(selectableEquipmentRow)
  if (!rows.length) {
    ElMessage.warning('请先勾选要导出的设备')
    return
  }

  exportEquipmentRows(rows, `设备台账-已选-${localDateStamp()}.csv`)
}

function exportEquipmentRows(rows, fileName) {
  const columns = [
    { label: '序号', valueGetter: (_row, index) => index + 1 },
    ...visibleOrderedColumns.value.map(column => ({
      label: column.label,
      valueGetter: row => equipmentExportValue(row, column)
    }))
  ]
  const csv = buildCsvText(rows, columns)
  const blob = new Blob(['\uFEFF', csv], { type: 'text/csv;charset=utf-8' })
  downloadBlob(blob, fileName)
  ElMessage.success(`已导出 ${rows.length} 台设备`)
}

function equipmentExportValue(row, column) {
  if (column.key === 'status') return statusText(row.status)
  if (column.key === 'equipmentType') return equipmentTypeText(row.equipmentType)
  if (column.custom) return customFieldValue(row, column)
  return equipmentColumnText(row, column)
}

function localDateStamp() {
  const date = new Date()
  return [
    date.getFullYear(),
    String(date.getMonth() + 1).padStart(2, '0'),
    String(date.getDate()).padStart(2, '0')
  ].join('-')
}

async function batchDeleteEquipment() {
  const rows = selectedRows.value.filter(selectableEquipmentRow)
  if (!rows.length) {
    ElMessage.warning('请先勾选要删除的设备')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确定删除已选 ${rows.length} 台设备吗？删除后会从设备台账中移除。`,
      '批量删除确认',
      { type: 'warning' }
    )

    const results = await Promise.allSettled(rows.map(row => request.delete(`/equipments/${row.id}`)))
    const successCount = results.filter(result => result.status === 'fulfilled').length
    const failureCount = results.length - successCount

    if (successCount > 0 && failureCount === 0) {
      ElMessage.success(`已删除 ${successCount} 台设备`)
    } else if (successCount > 0) {
      ElMessage.warning(`已删除 ${successCount} 台设备，失败 ${failureCount} 台`)
    } else {
      ElMessage.error('批量删除失败')
    }

    clearWorkspaceSelection()
    if (successCount >= list.value.length && pagination.currentPage > 1) {
      pagination.currentPage -= 1
    }
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error?.response?.data?.message || '批量删除失败')
    }
  }
}

async function updateStatus(row, status) {
  try {
    await ElMessageBox.confirm(
      `确定将设备「${row.equipmentName}」状态改为“${statusText(status)}”吗？`,
      '修改状态确认',
      { type: 'warning' }
    )

    await request.patch(`/equipments/${row.id}/status?status=${status}`)
    ElMessage.success('设备状态已更新')
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error?.response?.data?.message || '设备状态更新失败')
    }
  }
}

async function deleteEquipment(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除设备「${row.equipmentName}」吗？`,
      '删除确认',
      { type: 'warning' }
    )

    await request.delete(`/equipments/${row.id}`)
    ElMessage.success('设备已删除')
    if (list.value.length === 1 && pagination.currentPage > 1) {
      pagination.currentPage -= 1
    }
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error?.response?.data?.message || '设备删除失败')
    }
  }
}

onMounted(async () => {
  await Promise.all([load(), loadEquipmentLocationOptions()])
})

onBeforeUnmount(() => {
  disposeQueryLifecycle()
})
</script>

<style scoped>
.page-container {
  padding: 20px;
}

.view-hint {
  color: #7b8494;
  font-size: 12px;
  white-space: nowrap;
}

.workspace-quick-search {
  width: 250px;
}

.hidden-file-input {
  display: none;
}

:deep(.row-danger) {
  --el-table-tr-bg-color: #fff7f7;
}

:deep(.row-warning) {
  --el-table-tr-bg-color: #fffaf0;
}

:deep(.row-muted) {
  --el-table-tr-bg-color: #f7f8fa;
  color: #606266;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

@media (max-width: 720px) {
  .page-container {
    padding: 12px;
  }

  .workspace-quick-search {
    width: min(100%, 250px);
  }
}
</style>

<style>
.equipment-row-actions-menu .equipment-danger-action {
  color: var(--el-color-danger);
}
</style>
