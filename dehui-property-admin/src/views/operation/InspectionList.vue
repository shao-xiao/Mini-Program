<template>
  <main class="page-container">
    <DataWorkspace
      :workspace-config="inspectionWorkspaceConfig"
      :views="workspaceViews"
      :active-view-id="activeViewId"
      :view-dirty="isViewDirty"
      :filter-logic="tablePrefs.filterLogic"
      :filter-logic-visible="tablePrefs.filters.length > 1"
      :filter-items="activeFilterItems"
      :query-notices="queryNotices"
      :metric-data="summary"
      @change-view="switchInspectionView"
      @create-view="createTableView"
      @duplicate-view="duplicateTableView"
      @rename-view="renameTableView"
      @delete-view="deleteTableView"
      @save-view="saveInspectionView"
      @remove-filter="removeViewFilter"
    >
      <template #header-actions>
        <el-button v-if="hasPermission('custom-field:manage')" @click="moduleSettingsVisible = true">
          模块字段配置
        </el-button>
        <el-button v-if="canCreateInspection" type="primary" :icon="Plus" @click="openDialog">
          {{ inspectionWorkspaceConfig.primaryAction.label }}
        </el-button>
        <el-button v-if="canManageInspectionType" @click="openInspectionTypeDialog">
          管理巡检类型
        </el-button>
      </template>

      <template #toolbar>
        <DhicDataToolbar
          :workspace-config="inspectionWorkspaceConfig"
          allow-column-layout
          allow-custom-fields
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
          @update:filters="updateFilters"
          @update:filter-logic="updateFilterLogic"
          @update:sort-key="updateSortKey"
          @update:sort-order="updateSortOrder"
          @update:sorts="updateSorts"
          @update:group-key="updateGroupKey"
          @update:groups="updateGroups"
          @update:row-density="updateRowDensity"
          @reset-view="resetTableView"
          @save-field-config="saveInspectionView"
        >
          <template #right>
            <el-button :icon="Refresh" @click="loadWorkspaceData">刷新</el-button>
            <span class="view-hint">当前记录 {{ resultScope.loadedRecords }} / {{ resultScope.serverTotal }} 条</span>
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
          :data-mode="inspectionWorkspaceConfig.query.dataMode"
          :column-width="columnWidth"
          :column-fixed="columnFixed"
          :is-column-sortable="isColumnSortable"
          :show-overflow-tooltip="column => ['problemDescription', 'actionTaken', 'remark'].includes(column.key)"
          :operation-column="{ width: 190, fixed: 'right', align: 'left' }"
          @column-resize="handleHeaderDragEnd"
          @sort-change="handleTableSortChange"
          @retry="loadWorkspaceData"
          aria-label="巡检记录数据表格"
        >
          <template #operations="{ row }">
            <div class="row-actions">
              <el-button link type="primary" @click="openDetail(row)">详情</el-button>
              <el-button v-if="hasPermission('inspection:update')" link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button
                v-if="hasPermission('inspection:update') && row.status === 'OPEN'"
                link
                type="success"
                @click="closeInspection(row)"
              >关闭</el-button>
              <el-button v-if="hasPermission('inspection:delete')" link type="danger" @click="deleteInspection(row)">删除</el-button>
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
            @current-change="loadInspections"
            @size-change="handleSizeChange"
          />
        </div>
      </template>
    </DataWorkspace>

    <el-dialog v-model="visible" :title="editingId ? '编辑巡检' : '新增巡检'" width="760px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <ModuleSchemaForm
          ref="schemaFormRef"
          module-key="inspections"
          :fields="schemaFieldsWithOptions"
          :values="form"
          @update:values="schemaPayload = $event"
        />
        <template v-if="false">
        <el-form-item label="巡检日期" prop="inspectionDate">
          <el-date-picker v-model="form.inspectionDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>

        <el-form-item label="巡检人" prop="inspector">
          <el-input v-model="form.inspector" />
        </el-form-item>

        <el-form-item label="巡检类型" prop="inspectionType">
          <el-select v-model="form.inspectionType" style="width: 100%">
            <el-option
              v-for="item in inspectionTypeSelectOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
            <el-option
              v-if="!inspectionTypeSelectOptions.some(item => item.value === form.inspectionType) && form.inspectionType"
              :label="inspectionTypeLabel(form.inspectionType)"
              :value="form.inspectionType"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="区域/点位" prop="area">
          <el-input v-model="form.area" placeholder="如 B1区域 / 1F走廊 / 设备间" />
        </el-form-item>

        <el-form-item label="巡检对象" prop="target">
          <el-input v-model="form.target" placeholder="如 消防设备 / 监控设备 / 出入口" />
        </el-form-item>

        <el-form-item label="结果" prop="result">
          <el-select v-model="form.result" style="width: 100%">
            <el-option v-for="item in inspectionResultOptions" :key="item.value" :label="`${item.label} ${item.value}`" :value="item.value" />
          </el-select>
        </el-form-item>

        <el-form-item label="问题描述" prop="problemDescription">
          <el-input v-model="form.problemDescription" type="textarea" :rows="3" placeholder="请填写问题描述，尽量写清楚" />
        </el-form-item>

        <el-form-item label="处理措施" prop="actionTaken">
          <el-input v-model="form.actionTaken" type="textarea" :rows="3" placeholder="请填写已执行的处理措施，并保存" />
        </el-form-item>

        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" />
        </el-form-item>

        <CustomFieldEditor
          v-if="activeCustomFields.length"
          v-model="form.customFields"
          :columns="activeCustomFields"
        />
        </template>
      </el-form>

      <template #footer>
          <el-button @click="visible = false">取消</el-button>
          <el-button
            v-if="canWriteInspection"
            type="primary"
            :loading="saving"
            @click="save"
          >保存</el-button>
      </template>
    </el-dialog>

    <InspectionDetailDialog
      v-model="detailVisible"
      :fields="detailSchemaFieldsWithOptions"
      :values="detailValues"
    />

    <el-dialog v-model="typeDialogVisible" title="巡检类型管理" width="960px">
      <el-form :model="typeForm" inline label-width="88px" class="type-form">
        <el-form-item label="编码">
           <el-input v-model="typeForm.code" maxlength="32" show-word-limit placeholder="如: DAILY" />
        </el-form-item>
        <el-form-item label="名称">
           <el-input v-model="typeForm.name" placeholder="类型名称" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="typeForm.sortOrder" :min="0" controls-position="right" />
        </el-form-item>
        <el-form-item label="说明">
           <el-input v-model="typeForm.description" placeholder="请输入说明" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="typeSubmitting" @click="addInspectionType">新增类型</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="inspectionTypes" size="small" border>
        <el-table-column prop="code" label="编码" width="130" />
        <el-table-column label="名称" min-width="160">
          <template #default="{ row }">
            <el-input v-model="row.name" :disabled="false" />
          </template>
        </el-table-column>
        <el-table-column label="说明" min-width="180">
          <template #default="{ row }">
            <el-input v-model="row.description" :disabled="false" />
          </template>
        </el-table-column>
        <el-table-column label="排序" width="130">
          <template #default="{ row }">
            <el-input-number v-model="row.sortOrder" :min="0" controls-position="right" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'">
              {{ row.status === 'ENABLED' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240">
          <template #default="{ row }">
            <el-button size="small" text type="primary" :loading="typeSubmitting" @click="saveInspectionType(row)">
              保存
            </el-button>
            <el-button
              size="small"
              text
              :type="row.status === 'ENABLED' ? 'warning' : 'success'"
              :loading="typeSubmitting"
              @click="toggleInspectionTypeStatus(row)"
            >
              {{ row.status === 'ENABLED' ? '停用' : '启用' }}
            </el-button>
            <el-button size="small" text type="danger" :loading="typeSubmitting" @click="removeInspectionType(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
    <ModuleFieldSettingsDialog v-model="moduleSettingsVisible" module-key="inspections" />
  </main>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import DataWorkspace from '../../components/DataWorkspace.vue'
import DhicDataToolbar from '../../components/DhicDataToolbar.vue'
import WorkspaceDataGrid from '../../components/workspace/WorkspaceDataGrid.vue'
import ModuleFieldSettingsDialog from '../../components/schema/ModuleFieldSettingsDialog.vue'
import { useDhicTableView } from '../../composables/useDhicTableView'
import request from '../../utils/request'
import { createPagination, pageParams, readPage, resetToFirstPage } from '../../utils/pagination'
import { hasPermission } from '../../utils/permission'
import { createLatestRequestGuard } from '../../utils/requestLifecycle'
import { buildCustomFieldForm, validateCustomFieldValue } from '../../utils/customFieldValues'
import { getModuleFieldSchema, uploadCustomFieldAttachment } from '../../api/customFields.js'
import { normalizeModuleSchema } from '../../utils/moduleFieldSchema.js'
import {
  inspectionResultOptions,
  inspectionStatusOptions,
  inspectionTypeOptions,
  inspectionWorkspaceConfig
} from '../../workspaceConfigs/operationWorkspaceConfig'
import { workspaceTableOptions } from '../../workspaceConfigs/workspaceConfig'
import CustomFieldEditor from '../../components/CustomFieldEditor.vue'
import ModuleSchemaForm from '../../components/schema/ModuleSchemaForm.vue'
import { inspectionEditValues } from '../../utils/inspectionEditValues'
import InspectionDetailDialog from '../../components/inspection/InspectionDetailDialog.vue'

const MAX_INSPECTION_ATTACHMENTS = 30

const list = ref([])
const loading = ref(false)
const saving = ref(false)
const loadError = ref('')
const visible = ref(false)
const detailVisible = ref(false)
const detailSchemaFields = ref([])
const detailValues = ref({})
const moduleSettingsVisible = ref(false)
const editingId = ref(null)
const formRef = ref(null)
const schemaFormRef = ref(null)
const schemaFields = ref([])
let schemaPayload = {}
const pagination = reactive(createPagination(20))
const summary = reactive(emptySummary())
const summaryRequestGuard = createLatestRequestGuard()

const typeDialogVisible = ref(false)
const typeSubmitting = ref(false)
const inspectionTypes = ref([])
const typeForm = reactive({
  code: '',
  name: '',
  description: '',
  sortOrder: 0,
  status: 'ENABLED'
})

const photoInputRef = ref(null)
const photoAttachments = ref([])
const photoUploadMessage = ref('')
const uploadingPhotos = ref(false)

const canCreateInspection = computed(() => hasPermission('inspection:create'))
const canUpdateInspection = computed(() => hasPermission('inspection:update'))
const canManageInspectionType = computed(() => hasPermission('inspection:update'))
const canWriteInspection = computed(() => editingId.value ? canUpdateInspection.value : canCreateInspection.value)

const canAddPhoto = computed(() => {
  const existingCount = photoAttachments.value.filter(item => item.status !== 'error').length
  return existingCount < MAX_INSPECTION_ATTACHMENTS
})

const inspectionTypeRecordMap = computed(() => {
  const map = new Map()
  inspectionTypes.value.forEach(item => map.set(item.code, item))
  return map
})

const inspectionTypeSelectOptions = computed(() => {
  return inspectionTypes.value
    .filter(item => item.status === 'ENABLED' && String(item.code || '').trim().length <= 32)
    .map(item => ({
      label: item.name || item.code,
      value: item.code
    }))
})

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
  activeCustomFields,
  saveCustomFieldRecord,
  sortOptions,
  groupOptions,
  filterOptions,
  filterLogicOptions,
  serverQueryParams,
  queryNotices,
  queryRequestGuard: listRequestGuard,
  debouncedQueryTask: debouncedInspectionQuery,
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
} = useDhicTableView(workspaceTableOptions(inspectionWorkspaceConfig, {
  records: () => list.value,
  serverTotal: () => pagination.total,
  queryLifecycle: {
    task: () => {
      resetToFirstPage(pagination)
      loadWorkspaceData()
    }
  },
  groupLabel: value => {
    if (inspectionTypeRecordMap.value.has(value)) {
      return inspectionTypeRecordMap.value.get(value)?.name
    }
    return inspectionTypeOptions.find(option => option.value === value)?.label
      || inspectionResultOptions.find(option => option.value === value)?.label
      || inspectionStatusOptions.find(option => option.value === value)?.label
      || value
      || '未知类型'
  }
}))

watch(() => JSON.stringify(serverQueryParams.value), () => debouncedInspectionQuery.schedule())

const defaultForm = () => ({
  inspectionDate: '',
  inspector: '',
  inspectionType: '',
  area: '',
  target: '',
  result: 'NORMAL',
  status: 'OPEN',
  problemDescription: '',
  actionTaken: '',
  remark: '',
  customFields: {}
})

const form = reactive(defaultForm())

function historicalInspectionTypeOption(value) {
  if (!value || inspectionTypeSelectOptions.value.some(option => option.value === value)) return null
  const record = inspectionTypeRecordMap.value.get(value)
  return {
    value,
    label: record?.name ? `${record.name}（已停用）` : `${value}（历史值）`
  }
}

function inspectionTypeOptionsWithHistorical(value) {
  const historicalOption = historicalInspectionTypeOption(value)
  return historicalOption
    ? [...inspectionTypeSelectOptions.value, historicalOption]
    : inspectionTypeSelectOptions.value
}

const currentInspectionTypeOption = computed(() => historicalInspectionTypeOption(form.inspectionType))
const schemaFieldsWithOptions = computed(() => schemaFields.value.map(field => (
  field.fieldKey === 'inspectionType'
    ? {
        ...field,
        options: currentInspectionTypeOption.value
          ? [...inspectionTypeSelectOptions.value, currentInspectionTypeOption.value]
          : inspectionTypeSelectOptions.value
      }
    : field
)))
const detailSchemaFieldsWithOptions = computed(() => detailSchemaFields.value.map(field => {
  if (field.fieldKey === 'inspectionType') {
    return { ...field, options: inspectionTypeOptionsWithHistorical(detailValues.value.inspectionType) }
  }
  if (field.fieldKey === 'result') {
    return { ...field, options: inspectionResultOptions }
  }
  if (field.fieldKey === 'status') {
    return { ...field, options: inspectionStatusOptions }
  }
  return field
}))

const rules = {
  inspectionDate: [{ required: true, message: '请选择巡检日期', trigger: 'change' }],
  inspector: [{ required: true, message: '请填写巡检人', trigger: 'blur' }],
  inspectionType: [{ required: true, message: '请选择巡检类型', trigger: 'change' }],
  area: [{ required: true, message: '请填写巡检区域', trigger: 'blur' }],
  target: [{ required: true, message: '请填写巡检对象', trigger: 'blur' }],
  result: [{ required: true, message: '请选择巡检结果', trigger: 'change' }]
}

function emptySummary() {
  return { totalCount: 0, normalCount: 0, abnormalCount: 0, openCount: 0, closedCount: 0 }
}

function requestErrorMessage(error, fallback) {
  return error?.response?.data?.message || error?.message || error?.msg || fallback
}

function inspectionTypeLabel(code) {
  if (!code) return ''
  return inspectionTypeRecordMap.value.get(code)?.name || code
}

const photoPreviewSources = computed(() => photoAttachments.value.map(item => item.previewUrl).filter(Boolean))

function syncPhotoSortOrder() {
  photoAttachments.value.forEach((item, index) => {
    item.sortOrder = index + 1
  })
}

function normalizeAttachmentUrl(url) {
  if (!url || typeof url !== 'string') return ''
  const text = url.trim()
  if (!text) return ''
  if (text.startsWith('/') || text.startsWith('http://') || text.startsWith('https://')) return text
  return `/${text}`
}

function normalizePhotoList(raw = []) {
  if (!Array.isArray(raw)) return []
  return raw
    .map((item, index) => ({
      localId: `db-${item?.id ?? index}-${Date.now()}`,
      id: item?.id ?? null,
      fileId: Number.isFinite(Number(item?.fileId)) ? Number(item.fileId) : null,
      name: item?.name || `现场照片-${index + 1}`,
      previewUrl: normalizeAttachmentUrl(item?.url),
      status: 'done',
      sortOrder: Number(item?.sortOrder ?? index + 1),
      progress: 100
    }))
    .filter(item => item.name)
}

function normalizeInspectionTypeRecord(raw) {
  const sortOrder = Number(raw?.sortOrder)
  const id = Number(raw?.id)
  return {
    id: Number.isFinite(id) ? id : Date.now(),
    code: String(raw?.code || '').trim(),
    name: String(raw?.name || '').trim(),
    description: raw?.description ? String(raw.description) : '',
    status: String(raw?.status || 'ENABLED').trim().toUpperCase() || 'ENABLED',
    sortOrder: Number.isFinite(sortOrder) ? sortOrder : 0
  }
}

function syncInspectionTypeOptions() {
  const records = inspectionTypes.value.filter(item => item.code)
  inspectionTypeOptions.length = 0
  records.forEach(item => inspectionTypeOptions.push({
    label: item.name || item.code,
    value: item.code
  }))
}

function isImageFile(file) {
  if (!file || !(file instanceof File)) return false
  if (file.type && file.type.startsWith('image/')) return true
  const name = String(file.name || '').toLowerCase()
  return name.endsWith('.jpg')
    || name.endsWith('.jpeg')
    || name.endsWith('.png')
    || name.endsWith('.gif')
    || name.endsWith('.webp')
    || name.endsWith('.bmp')
}

function nextPhotoLocalId() {
  return `local-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function resetForm() {
  editingId.value = null
  for (const key of Object.keys(form)) delete form[key]
  Object.assign(form, defaultForm())
  photoUploadMessage.value = ''
  photoAttachments.value = []
  formRef.value?.clearValidate()
}

function resetTypeForm() {
  typeForm.code = ''
  typeForm.name = ''
  typeForm.description = ''
  typeForm.sortOrder = 0
  typeForm.status = 'ENABLED'
}

function syncDialogCustomFields(seedValues = {}) {
  form.customFields = buildCustomFieldForm(activeCustomFields.value, seedValues)
}

function validateInspectionCustomFields() {
  if (!activeCustomFields.value.length) return true

  const messages = []
  activeCustomFields.value.forEach(field => {
    const message = validateCustomFieldValue(field, form.customFields[field.key])
    if (message) messages.push(message)
  })

  if (messages.length === 0) return true
  ElMessage.warning(messages.join('；'))
  return false
}

function summaryFilterParams() {
  const params = serverQueryParams.value
  return {
    inspector: params.inspector,
    target: params.target,
    inspectionType: params.inspectionType,
    result: params.result,
    status: params.status,
    startDate: params.startDate,
    endDate: params.endDate
  }
}

async function loadInspectionTypes() {
  try {
    const rows = await request.get('/inspection-types', { params: { includeDisabled: true }, silent: true })
    inspectionTypes.value = Array.isArray(rows) ? rows.map(normalizeInspectionTypeRecord) : []
    inspectionTypes.value.sort((a, b) => {
      if (a.sortOrder === b.sortOrder) return a.code.localeCompare(b.code)
      return a.sortOrder - b.sortOrder
    })
    syncInspectionTypeOptions()
  } catch (error) {
    inspectionTypes.value = []
    syncInspectionTypeOptions()
    inspectionTypeOptions.length = 0
    throw error
  }
}

async function loadInspections() {
  const requestId = listRequestGuard.start()
  loading.value = true
  loadError.value = ''
  try {
    const data = await request.get('/inspections', {
      params: { ...serverQueryParams.value, ...pageParams(pagination) },
      silent: true
    })
    if (!listRequestGuard.isCurrent(requestId)) return
    const page = readPage(data)
    list.value = page.records
    pagination.total = page.total
  } catch (error) {
    if (!listRequestGuard.isCurrent(requestId)) return
    list.value = []
    pagination.total = 0
    loadError.value = requestErrorMessage(error, '巡检记录加载失败')
  } finally {
    if (listRequestGuard.isCurrent(requestId)) loading.value = false
  }
}

async function loadSummary() {
  const requestId = summaryRequestGuard.start()
  try {
    const data = await request.get('/inspections/summary', {
      params: summaryFilterParams(),
      silent: true
    })
    if (summaryRequestGuard.isCurrent(requestId)) Object.assign(summary, emptySummary(), data || {})
  } catch {
    if (summaryRequestGuard.isCurrent(requestId)) Object.assign(summary, emptySummary())
  }
}

async function loadWorkspaceData() {
  await loadInspectionTypes()
  await Promise.all([loadInspections(), loadSummary()])
}

function syncDialogFromInspection(record = {}) {
  Object.assign(form, inspectionEditValues(record))
  photoAttachments.value = normalizePhotoList(record.attachments)
  syncPhotoSortOrder()
}

async function handleSizeChange() {
  resetToFirstPage(pagination)
  await loadInspections()
}

async function ensureSchema() {
  if (!schemaFields.value.length) {
    schemaFields.value = normalizeModuleSchema(await getModuleFieldSchema('inspections', 'adminForm'))
  }
}

async function openDialog() {
  resetForm()
  syncDialogCustomFields({})
  await ensureSchema()
  visible.value = true
}

async function openEdit(row) {
  resetForm()
  if (!row?.id) return
  try {
    await ensureSchema()
    const detail = await request.get(`/inspections/${row.id}`, { silent: true })
    editingId.value = detail?.id || row.id
    syncDialogFromInspection(detail || row)
    syncDialogCustomFields(detail?.customFields || row.customFields || {})
    visible.value = true
  } catch (error) {
    ElMessage.error(requestErrorMessage(error, '巡检详情加载失败'))
  }
}

function openInspectionTypeDialog() {
  if (!canManageInspectionType.value) return
  resetTypeForm()
  typeDialogVisible.value = true
  loadInspectionTypes()
}

function openPhotoPicker() {
  if (!canWriteInspection.value || !canAddPhoto.value || uploadingPhotos.value) return
  if (photoInputRef.value) {
    photoInputRef.value.value = ''
    photoInputRef.value.click()
  }
}

async function removePhotoAttachment(item) {
  if (!canWriteInspection.value || item.status === 'uploading') return
  const target = photoAttachments.value.find(record => record.localId === item.localId)
  if (!target) return

  if (target.id && target.fileId && editingId.value) {
    try {
      await request.delete(`/inspections/${editingId.value}/attachments/${target.id}`)
    } catch (error) {
      ElMessage.error(requestErrorMessage(error, '删除图片失败'))
      return
    }
  }

  photoAttachments.value = photoAttachments.value.filter(record => record.localId !== item.localId)
  syncPhotoSortOrder()
}

async function handlePhotoSelection(event) {
  if (!canWriteInspection.value || uploadingPhotos.value) return
  const files = Array.from(event.target?.files || [])
  event.target.value = ''
  if (!files.length) return

  const canAppendCount = MAX_INSPECTION_ATTACHMENTS - photoAttachments.value.filter(item => item.status !== 'error').length
  if (canAppendCount <= 0) {
    photoUploadMessage.value = `最多可上传 ${MAX_INSPECTION_ATTACHMENTS} 张照片`
    return
  }

  const selected = files.slice(0, canAppendCount)
  if (selected.length < files.length) {
    photoUploadMessage.value = `最多可上传 ${MAX_INSPECTION_ATTACHMENTS} 张照片，本次仅保留前 ${selected.length} 张`
  } else {
    photoUploadMessage.value = ''
  }

  uploadingPhotos.value = true
  for (let index = 0; index < selected.length; index += 1) {
    const file = selected[index]
    const localId = nextPhotoLocalId()
    const item = {
      localId,
      id: null,
      fileId: null,
      name: file?.name || `现场照片${index + 1}`,
      previewUrl: file ? URL.createObjectURL(file) : '',
      status: 'uploading',
      sortOrder: photoAttachments.value.length + 1,
      progress: 0,
      error: ''
    }
    photoAttachments.value.push(item)

    if (!isImageFile(file)) {
      item.status = 'error'
      item.error = '请上传图片文件'
      item.progress = 0
      continue
    }

    try {
      const uploaded = await uploadCustomFieldAttachment(file, {
        onUploadProgress(progress) {
          if (!progress?.total) return
          item.progress = Math.max(1, Math.round((progress.loaded / progress.total) * 100))
        }
      })
      const uploadedFileId = Number(uploaded?.id)
      if (!Number.isFinite(uploadedFileId)) {
        throw new Error('上传结果缺少文件ID')
      }
      item.id = uploaded.id
      item.fileId = uploadedFileId
      item.previewUrl = normalizeAttachmentUrl(uploaded.url || uploaded.storagePath || uploaded.storage_path)
      item.status = 'done'
      item.progress = 100
      item.error = ''
      syncPhotoSortOrder()
    } catch (error) {
      item.status = 'error'
      item.error = requestErrorMessage(error, '上传失败')
      item.progress = 0
    }
  }
  uploadingPhotos.value = false
  syncPhotoSortOrder()
}

function buildAttachmentPayload() {
  return photoAttachments.value
    .filter(item => item.status === 'done' && item.fileId)
    .sort((a, b) => a.sortOrder - b.sortOrder)
    .map((item, index) => ({
      fileId: item.fileId,
      sortOrder: index + 1
    }))
}

async function save() {
  if (!schemaFormRef.value?.validate()) return
  schemaPayload = schemaFormRef.value.getPayload()
  if (!canWriteInspection.value) return

  saving.value = true
  try {
    const payload = { ...schemaPayload }
    delete payload.id
    const attachments = buildAttachmentPayload()
    if (attachments.length > 0) {
      payload.attachments = attachments
    } else if (editingId.value) {
      payload.attachments = []
    }

    if (editingId.value) {
      await request.put(`/inspections/${editingId.value}`, payload)
      ElMessage.success('巡检记录更新成功')
    } else {
      await request.post('/inspections', payload)
      ElMessage.success('巡检记录创建成功')
    }
    visible.value = false
    resetForm()
    await loadWorkspaceData()
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '巡检记录保存失败')
  } finally {
    saving.value = false
  }
}

async function closeInspection(row) {
  try {
    await ElMessageBox.confirm(`确定关闭巡检记录 ${row.area} - ${row.target}？`, '确认关闭', { type: 'warning' })
    await request.post(`/inspections/${row.id}/close`)
    ElMessage.success('巡检记录关闭成功')
    await loadWorkspaceData()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error?.response?.data?.message || '关闭失败')
  }
}

async function openDetail(row) {
  if (!row?.id) return
  if (!detailSchemaFields.value.length) {
    detailSchemaFields.value = normalizeModuleSchema(await getModuleFieldSchema('inspections', 'adminDetail'))
  }
  detailValues.value = await request.get(`/inspections/${row.id}`, { silent: true })
  detailVisible.value = true
}

async function deleteInspection(row) {
  try {
    await ElMessageBox.confirm(`确定删除巡检记录 ${row.area} - ${row.target}？删除后将从列表移除`, '确认删除', { type: 'warning' })
    await request.delete(`/inspections/${row.id}`)
    ElMessage.success('巡检记录删除成功')
    await loadWorkspaceData()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error?.response?.data?.message || '删除失败')
  }
}

async function addInspectionType() {
  if (!canManageInspectionType.value) return
  if (!typeForm.code.trim() || !typeForm.name.trim()) {
    ElMessage.warning('编码和名称不能为空')
    return
  }
  typeSubmitting.value = true
  try {
    await request.post('/inspection-types', {
      code: typeForm.code.trim(),
      name: typeForm.name.trim(),
      description: (typeForm.description || '').trim(),
      status: typeForm.status,
      sortOrder: Number(typeForm.sortOrder) || 0
    })
    resetTypeForm()
    await loadInspectionTypes()
    ElMessage.success('巡检类型创建成功')
  } catch (error) {
    ElMessage.error(requestErrorMessage(error, '创建巡检类型失败'))
  } finally {
    typeSubmitting.value = false
  }
}

async function saveInspectionType(row) {
  if (!canManageInspectionType.value) return
  if (!row?.id || !row.name?.trim()) {
    ElMessage.warning('类型名称不能为空')
    return
  }
  typeSubmitting.value = true
  try {
    await request.put(`/inspection-types/${row.id}`, {
      code: row.code,
      name: String(row.name || '').trim(),
      description: String(row.description || '').trim(),
      status: row.status || 'ENABLED',
      sortOrder: Number(row.sortOrder) || 0
    })
    await loadInspectionTypes()
    ElMessage.success('巡检类型保存成功')
  } catch (error) {
    ElMessage.error(requestErrorMessage(error, '保存失败'))
  } finally {
    typeSubmitting.value = false
  }
}

async function toggleInspectionTypeStatus(row) {
  if (!canManageInspectionType.value || !row?.id) return
  typeSubmitting.value = true
  try {
    if (row.status === 'ENABLED') {
      await request.post(`/inspection-types/${row.id}/disable`)
    } else {
      await request.post(`/inspection-types/${row.id}/enable`)
    }
    await loadInspectionTypes()
  } catch (error) {
    ElMessage.error(requestErrorMessage(error, '状态切换失败'))
  } finally {
    typeSubmitting.value = false
  }
}

async function removeInspectionType(row) {
  if (!canManageInspectionType.value || !row?.id) return
  try {
    await ElMessageBox.confirm(`确认删除巡检类型 ${row.name || row.code}？`, '确认删除', { type: 'warning' })
    typeSubmitting.value = true
    await request.delete(`/inspection-types/${row.id}`)
    await loadInspectionTypes()
    ElMessage.success('巡检类型删除成功')
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(requestErrorMessage(error, '删除失败'))
    }
  } finally {
    typeSubmitting.value = false
  }
}

async function switchInspectionView(viewId) {
  if (viewId === activeViewId.value) return
  if (isViewDirty.value) {
    try {
      await ElMessageBox.confirm('当前视图有未保存的修改，是否放弃并切换？', '切换视图', {
        distinguishCancelAndClose: true,
        confirmButtonText: '保存并切换',
        cancelButtonText: '放弃修改'
      })
      saveCurrentView()
    } catch (action) {
      if (action === 'cancel') discardCurrentViewChanges()
      else return
    }
  }
  switchTableView(viewId)
}

function saveInspectionView() {
  saveCurrentView()
  ElMessage.success(`视图 ${activeViewName.value} 保存成功`)
}

watch(() => activeCustomFields.value, () => {
  if (!visible.value) return
  syncDialogCustomFields(form.customFields || {})
}, { deep: true })

onMounted(() => {
  loadWorkspaceData()
})

onBeforeUnmount(() => {
  listRequestGuard.dispose()
  summaryRequestGuard.dispose()
  disposeQueryLifecycle()
})
</script>

<style scoped>
.page-container {
  min-width: 0;
}

.view-hint {
  color: var(--dhic-text-secondary);
  font-size: 13px;
}

.row-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.row-actions :deep(.el-button) {
  margin-left: 0;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
}

.photo-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
}

.photo-hint {
  color: var(--dhic-text-secondary);
  font-size: 12px;
}

.photo-file-input {
  display: none;
}

.photo-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(130px, 1fr));
  gap: 12px;
}

.photo-item {
  border: 1px solid var(--dhic-border);
  border-radius: 8px;
  padding: 6px;
  display: grid;
  gap: 8px;
}

.photo-thumb {
  width: 100%;
  height: 120px;
  border-radius: 6px;
  overflow: hidden;
}

.photo-thumb-empty {
  width: 100%;
  height: 120px;
  border-radius: 6px;
  background: var(--dhic-bg-soft, #f5f7fa);
  color: var(--dhic-text-muted);
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.photo-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.photo-name {
  flex: 1;
  min-width: 0;
  font-size: 12px;
  color: var(--dhic-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.photo-state {
  display: flex;
}

.photo-error {
  margin: 0;
  color: var(--el-color-danger);
  font-size: 12px;
}

.type-form {
  margin-bottom: 12px;
}
</style>
