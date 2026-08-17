<template>
  <main class="page-container" :class="{ 'page-container--embedded': props.embedded }">
    <DataWorkspace
      :workspace-config="parkingSpaceWorkspaceConfig"
      :views="workspaceViews"
      :active-view-id="activeViewId"
      :view-dirty="isViewDirty"
      :filter-logic="tablePrefs.filterLogic"
      :filter-logic-visible="tablePrefs.filters.length > 1"
      :filter-items="activeFilterItems"
      :query-notices="queryNotices"
      :metric-data="summary"
      @change-view="switchParkingView"
      @create-view="createTableView"
      @duplicate-view="duplicateTableView"
      @rename-view="renameTableView"
      @delete-view="deleteTableView"
      @save-view="saveParkingView"
      @remove-filter="removeViewFilter"
    >
      <template #header-actions>
        <el-button v-if="hasPermission('parking:create')" type="primary" :icon="Plus" @click="openDialog()">
          {{ parkingSpaceWorkspaceConfig.primaryAction.label }}
        </el-button>
      </template>

      <template #toolbar>
        <DhicDataToolbar
          :workspace-config="parkingSpaceWorkspaceConfig"
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
          @save-field-config="saveParkingView"
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
          :data-mode="parkingSpaceWorkspaceConfig.query.dataMode"
          :column-width="columnWidth"
          :column-fixed="columnFixed"
          :is-column-sortable="isColumnSortable"
          :show-overflow-tooltip="column => ['partyNameSnapshot', 'remark'].includes(column.key)"
          :row-class-name="spaceRowClassName"
          :operation-column="{ width: 175, fixed: 'right', align: 'left' }"
          @column-resize="handleHeaderDragEnd"
          @sort-change="handleTableSortChange"
          @retry="loadWorkspaceData"
          aria-label="车位管理数据表格"
        >
          <template #operations="{ row }">
            <div class="action-cell">
              <el-button v-if="hasPermission('parking:create') && row.status === 'AVAILABLE'" link type="primary" @click="openBind(row)">绑定</el-button>
              <el-button v-else-if="hasPermission('parking:create') && row.status === 'OCCUPIED'" link type="warning" @click="release(row)">释放</el-button>
              <el-button v-else-if="hasPermission('parking:update') && !['AVAILABLE', 'OCCUPIED'].includes(row.status)" link type="success" @click="enable(row)">启用</el-button>
              <el-dropdown trigger="click" @command="command => handleSpaceCommand(command, row)">
                <el-button link type="primary">更多</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item v-if="hasPermission('parking:update')" command="edit">编辑</el-dropdown-item>
                    <el-dropdown-item
                      v-if="hasPermission('bill:view') || hasPermission('bill:*')"
                      command="bills"
                      :disabled="row.status !== 'OCCUPIED'"
                    >前往停车财务账单</el-dropdown-item>
                    <el-dropdown-item
                      v-if="hasPermission('parking:update')"
                      command="monthly-fee"
                      :disabled="row.status !== 'OCCUPIED' || !row.assignmentId"
                    >调整月租费</el-dropdown-item>
                    <el-dropdown-item v-if="hasPermission('parking:delete')" command="delete" :disabled="row.status !== 'AVAILABLE'">删除</el-dropdown-item>
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
            @current-change="loadWorkspaceData"
            @size-change="handleSizeChange"
          />
        </div>
      </template>
    </DataWorkspace>

    <el-dialog v-model="spaceVisible" :title="spaceForm.id ? '编辑车位' : '新增车位'" width="520px" destroy-on-close>
      <el-form ref="spaceFormRef" :model="spaceForm" :rules="spaceRules" label-width="100px">
        <el-form-item label="车位编号" prop="spaceNo">
          <el-input v-model.trim="spaceForm.spaceNo" placeholder="如 B1-004" />
        </el-form-item>
        <el-form-item label="区域" prop="area">
          <el-select v-model="spaceForm.area" placeholder="请选择区域" style="width: 100%">
            <el-option v-for="area in areaOptions" :key="area" :label="area" :value="area" />
          </el-select>
        </el-form-item>
        <el-form-item label="楼层/位置" prop="floor">
          <el-select v-model="spaceForm.floor" placeholder="请选择楼层/位置" style="width: 100%">
            <el-option v-for="option in locationOptions" :key="option" :label="option" :value="option" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <div class="type-select-line">
            <el-select v-model="spaceForm.type" style="width: 100%">
              <el-option v-for="option in spaceTypeOptions" :key="option.value" :label="option.label" :value="option.value" />
            </el-select>
            <el-button v-if="hasPermission('parking:create')" :icon="Plus" circle @click="openTypeDialog" />
          </div>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="spaceForm.sortOrder" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="月租标准">
          <el-input-number v-model="spaceForm.monthlyRate" :min="0.01" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="spaceForm.remark" type="textarea" :rows="3" />
        </el-form-item>
        <CustomFieldEditor
          v-if="activeCustomFields.length"
          v-model="spaceForm.customFields"
          :columns="activeCustomFields"
        />
      </el-form>
      <template #footer>
        <el-button @click="spaceVisible = false">取消</el-button>
        <el-button v-if="spaceForm.id ? hasPermission('parking:update') : hasPermission('parking:create')" type="primary" :loading="saving" @click="saveSpace">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="bindVisible" title="绑定车位" width="560px" destroy-on-close>
      <el-form ref="bindFormRef" :model="bindForm" :rules="bindRules" label-width="110px">
        <el-form-item label="车位编号">
          <el-input :model-value="currentSpace?.spaceNo || ''" disabled />
        </el-form-item>
        <el-form-item label="租户" prop="partyId">
          <el-select v-model="bindForm.partyId" filterable placeholder="请选择租户" style="width: 100%" @change="loadTenantContacts">
            <el-option v-for="tenant in tenants" :key="tenant.id" :label="tenantLabel(tenant)" :value="tenant.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="租户联系人">
          <el-select v-model="bindForm.tenantContactId" clearable filterable placeholder="可选，绑定到具体员工" style="width: 100%">
            <el-option v-for="contact in tenantContacts" :key="contact.id" :label="`${contact.name} ${contact.phone || ''}`" :value="contact.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="车牌号" prop="plateNo">
          <el-input v-model.trim="bindForm.plateNo" placeholder="可选，活动车位可不绑定车牌" />
        </el-form-item>
        <el-form-item label="车辆类型">
          <el-select v-model="bindForm.vehicleType" style="width: 100%">
            <el-option label="小型汽车" value="CAR" />
            <el-option label="货车" value="TRUCK" />
            <el-option label="新能源车" value="NEW_ENERGY" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始日期" prop="startDate">
          <el-date-picker v-model="bindForm.startDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束日期">
          <el-date-picker v-model="bindForm.endDate" type="date" value-format="YYYY-MM-DD" clearable style="width: 100%" />
        </el-form-item>
        <el-form-item label="收费类型" prop="billingType">
          <el-select v-model="bindForm.billingType" disabled style="width: 100%">
            <el-option label="月租" value="monthly" />
          </el-select>
        </el-form-item>
        <el-form-item label="月租金额" prop="monthlyFee">
          <el-input-number v-model="bindForm.monthlyFee" :min="0.01" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="bindForm.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="bindVisible = false">取消</el-button>
        <el-button v-if="hasPermission('parking:create')" type="primary" :loading="binding" @click="submitBind">确定绑定</el-button>
      </template>
    </el-dialog>
    <el-dialog
      v-model="feeVisible"
      title="编辑停车月费"
      width="min(440px, calc(100vw - 32px))"
      destroy-on-close
    >
      <el-form ref="feeFormRef" :model="feeForm" :rules="feeRules" label-width="100px">
        <el-form-item label="停车位">
          <el-input :model-value="currentSpace?.spaceNo || ''" disabled />
        </el-form-item>
        <el-form-item label="月租金额" prop="monthlyFee">
          <el-input-number
            v-model="feeForm.monthlyFee"
            :min="0.01"
            :precision="2"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="feeVisible = false">取消</el-button>
        <el-button
          v-if="hasPermission('parking:update')"
          type="primary"
          :loading="feeSaving"
          @click="saveMonthlyFee"
        >
          保存
        </el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="typeVisible" title="新增车位类型" width="420px" destroy-on-close>
      <el-form ref="typeFormRef" :model="typeForm" :rules="typeRules" label-width="90px">
        <el-form-item label="类型名称" prop="label">
          <el-input v-model.trim="typeForm.label" placeholder="如 货车车位、专用车位、月租车位" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="typeVisible = false">取消</el-button>
        <el-button v-if="hasPermission('parking:create')" type="primary" :loading="typeSaving" @click="saveSpaceType">保存</el-button>
      </template>
    </el-dialog>
  </main>
</template>

<script setup>
import { onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import {
  bindParkingSpace,
  createParkingSpaceType,
  createParkingSpace,
  deleteParkingSpace,
  listParkingSpaceTypes,
  listParkingSpaces,
  releaseParkingSpace,
  updateParkingAssignmentMonthlyFee,
  updateParkingSpace,
  updateParkingSpaceStatus
} from '../../api/parking'
import request from '../../utils/request'
import { hasPermission } from '../../utils/permission'
import { createPagination, pageParams, readPage, resetToFirstPage } from '../../utils/pagination'
import { createLatestRequestGuard } from '../../utils/requestLifecycle'
import { buildCustomFieldForm } from '../../utils/customFieldValues'
import { useDhicTableView } from '../../composables/useDhicTableView'
import CustomFieldEditor from '../../components/CustomFieldEditor.vue'
import DataWorkspace from '../../components/DataWorkspace.vue'
import DhicDataToolbar from '../../components/DhicDataToolbar.vue'
import WorkspaceDataGrid from '../../components/workspace/WorkspaceDataGrid.vue'
import {
  parkingSpaceStatusOptions,
  parkingSpaceTypeOptions as parkingWorkspaceTypeOptions,
  parkingSpaceWorkspaceConfig,
  workspaceOptionLabel
} from '../../workspaceConfigs/parkingMeetingWorkspaceConfig'
import { workspaceTableOptions } from '../../workspaceConfigs/workspaceConfig'

const props = defineProps({
  embedded: { type: Boolean, default: false }
})

const router = useRouter()
const list = ref([])
const tenants = ref([])
const tenantContacts = ref([])
const loading = ref(false)
const loadError = ref('')
const saving = ref(false)
const binding = ref(false)
const feeSaving = ref(false)
const typeSaving = ref(false)
const spaceVisible = ref(false)
const bindVisible = ref(false)
const feeVisible = ref(false)
const typeVisible = ref(false)
const spaceFormRef = ref(null)
const bindFormRef = ref(null)
const feeFormRef = ref(null)
const typeFormRef = ref(null)
const currentSpace = ref(null)
const pagination = reactive(createPagination(20))
const summary = reactive(emptySummary())
const summaryRequestGuard = createLatestRequestGuard()
const areaOptions = ['A', 'B', 'C', 'D']
const locationOptions = ['B1', 'B2', '地面']
const defaultSpaceTypeOptions = parkingWorkspaceTypeOptions
const spaceTypeOptions = ref([...defaultSpaceTypeOptions])
const spaceForm = reactive({ id: null, spaceNo: '', area: 'A', floor: '', type: 'NORMAL', monthlyRate: null, sortOrder: 0, remark: '', customFields: {} })
const bindForm = reactive({
  partyType: 'tenant',
  partyId: null,
  tenantContactId: null,
  plateNo: '',
  vehicleType: 'CAR',
  startDate: new Date().toISOString().slice(0, 10),
  endDate: null,
  billingType: 'monthly',
  monthlyFee: null,
  remark: ''
})
const feeForm = reactive({ monthlyFee: null })
const typeForm = reactive({ label: '' })

const spaceRules = {
  spaceNo: [{ required: true, message: '请输入车位编号', trigger: 'blur' }],
  area: [{ required: true, message: '请选择区域', trigger: 'change' }],
  floor: [{ required: true, message: '请选择楼层/位置', trigger: 'change' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }]
}

const bindRules = {
  partyType: [{ required: true, message: '请选择使用方类型', trigger: 'change' }],
  partyId: [{ required: true, message: '请选择租户', trigger: 'change' }],
  partyName: [{ required: true, message: '请输入使用方名称', trigger: 'blur' }],
  startDate: [{ required: true, message: '请选择开始日期', trigger: 'change' }],
  billingType: [{ required: true, message: '请选择收费类型', trigger: 'change' }],
  monthlyFee: [{
    validator: (_rule, value, callback) => {
      if (!value || value <= 0) callback(new Error('月租金额必须大于0'))
      else callback()
    },
    trigger: 'change'
  }]
}
const feeRules = {
  monthlyFee: [{
    validator: (_rule, value, callback) => {
      if (!value || value <= 0) callback(new Error('月租金额必须大于0'))
      else callback()
    },
    trigger: 'change'
  }]
}

const typeRules = {
  label: [{ required: true, message: '请输入类型名称', trigger: 'blur' }]
}
const {
  workspaceViews,
  activeViewId,
  activeViewName,
  isViewDirty,
  tablePrefs,
  activeCustomFields,
  saveCustomFieldRecord,
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
  debouncedQueryTask: debouncedParkingQuery,
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
} = useDhicTableView(workspaceTableOptions(parkingSpaceWorkspaceConfig, {
  records: () => list.value,
  serverTotal: () => pagination.total,
  queryLifecycle: {
    task: () => {
      resetToFirstPage(pagination)
      loadWorkspaceData()
    }
  },
  groupLabel: value => workspaceOptionLabel(
    [...parkingSpaceStatusOptions, ...parkingWorkspaceTypeOptions],
    value
  )
}))

watch(() => JSON.stringify(serverQueryParams.value), () => debouncedParkingQuery.schedule())

function emptySummary() {
  return { totalCount: 0, availableCount: 0, occupiedCount: 0, unavailableCount: 0 }
}

function requestErrorMessage(error, fallback) {
  return error?.response?.data?.message || error?.message || error?.msg || fallback
}

function summaryFilterParams() {
  const { page: _page, pageSize: _pageSize, sorts: _sorts, groupBy: _groupBy, ...filters } = serverQueryParams.value
  return filters
}

async function switchParkingView(viewId) {
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

function saveParkingView() {
  saveCurrentView()
  ElMessage.success(`视图“${activeViewName.value}”已保存`)
}

async function load() {
  const requestId = listRequestGuard.start()
  loading.value = true
  loadError.value = ''
  try {
    const data = await listParkingSpaces({ ...serverQueryParams.value, ...pageParams(pagination) })
    if (!listRequestGuard.isCurrent(requestId)) return
    const page = readPage(data)
    if (!listRequestGuard.isCurrent(requestId)) return
    list.value = page.records
    pagination.total = page.total
  } catch (error) {
    if (!listRequestGuard.isCurrent(requestId)) return
    list.value = []
    pagination.total = 0
    loadError.value = requestErrorMessage(error, '车位数据加载失败')
  } finally {
    if (listRequestGuard.isCurrent(requestId)) loading.value = false
  }
}

async function loadSummary() {
  const requestId = summaryRequestGuard.start()
  try {
    const data = await request.get('/parking/spaces/summary', {
      params: summaryFilterParams(),
      silent: true
    })
    if (summaryRequestGuard.isCurrent(requestId)) Object.assign(summary, emptySummary(), data || {})
  } catch {
    if (summaryRequestGuard.isCurrent(requestId)) Object.assign(summary, emptySummary())
  }
}

async function loadWorkspaceData() {
  await Promise.all([load(), loadSummary()])
}

async function handleSizeChange() {
  resetToFirstPage(pagination)
  await loadWorkspaceData()
}

async function loadTenants() {
  try {
    const data = await request.get('/tenant/list', { silent: true })
    tenants.value = readPage(data).records
  } catch {
    tenants.value = []
  }
}

async function loadSpaceTypes() {
  try {
    const options = await listParkingSpaceTypes()
    spaceTypeOptions.value = Array.isArray(options) && options.length ? options : [...defaultSpaceTypeOptions]
  } catch (error) {
    console.warn('parking space type options are unavailable, fallback to defaults', error)
    spaceTypeOptions.value = [...defaultSpaceTypeOptions]
  }
}

function openDialog(row) {
  Object.assign(spaceForm, {
    id: row?.id || null,
    spaceNo: row?.spaceNo || row?.spaceCode || '',
    area: row?.area || 'A',
    floor: normalizeLocation(row?.floor || row?.location),
    type: normalizeSpaceType(row?.type || row?.spaceType),
    monthlyRate: row?.monthlyRate ?? null,
    sortOrder: row?.sortOrder || 0,
    remark: row?.remark || '',
    customFields: buildCustomFieldForm(activeCustomFields.value, row?.customFields || {})
  })
  spaceVisible.value = true
}

async function saveSpace() {
  await spaceFormRef.value.validate()
  saving.value = true
  try {
    const { customFields: _customFields, ...payload } = { ...spaceForm }
    if (spaceForm.id) {
      await updateParkingSpace(spaceForm.id, payload)
      if (activeCustomFields.value.length) {
        const result = await saveCustomFieldRecord(
          { id: spaceForm.id, customFields: spaceForm.customFields },
          spaceForm.customFields
        )
        if (result.success === false) throw new Error(result.message || '自定义字段保存失败')
      }
      ElMessage.success('保存成功')
    } else {
      await createParkingSpace(payload)
      ElMessage.success('新增成功')
      if (activeCustomFields.value.length) {
        ElMessage.info('扩展字段已创建，请保存主记录后再次编辑该车位填写扩展字段')
      }
    }
    spaceVisible.value = false
    await loadWorkspaceData()
  } finally {
    saving.value = false
  }
}

function openTypeDialog() {
  typeForm.label = ''
  typeVisible.value = true
}

async function saveSpaceType() {
  await typeFormRef.value.validate()
  typeSaving.value = true
  try {
    await createParkingSpaceType({ label: typeForm.label })
    await loadSpaceTypes()
    const created = spaceTypeOptions.value.find(option => option.label === typeForm.label)
    if (created) {
      spaceForm.type = created.value
    }
    typeVisible.value = false
    ElMessage.success('车位类型已新增')
  } finally {
    typeSaving.value = false
  }
}

function openBind(row) {
  currentSpace.value = row
  Object.assign(bindForm, {
    partyType: 'tenant',
    partyId: null,
    tenantContactId: null,
    plateNo: '',
    vehicleType: 'CAR',
    startDate: new Date().toISOString().slice(0, 10),
    endDate: null,
    billingType: 'monthly',
    monthlyFee: Number(row.monthlyRate) > 0 ? Number(row.monthlyRate) : null,
    remark: ''
  })
  bindVisible.value = true
}

async function loadTenantContacts(tenantId) {
  bindForm.tenantContactId = null
  tenantContacts.value = []
  if (!tenantId) return
  const response = await request.get(`/tenant/${tenantId}/contacts`, { silent: true })
  tenantContacts.value = readPage(response).records
}

async function submitBind() {
  await bindFormRef.value.validate()
  binding.value = true
  try {
    await bindParkingSpace(currentSpace.value.id, { ...bindForm })
    ElMessage.success('绑定成功')
    bindVisible.value = false
    await loadWorkspaceData()
  } finally {
    binding.value = false
  }
}

async function release(row) {
  await ElMessageBox.confirm(`确定释放车位「${row.spaceNo}」吗？`, '释放确认', { type: 'warning' })
  await releaseParkingSpace(row.id, { endDate: new Date().toISOString().slice(0, 10) })
  ElMessage.success('已释放')
  await loadWorkspaceData()
}

async function enable(row) {
  await updateParkingSpaceStatus(row.id, 'AVAILABLE')
  ElMessage.success('已启用')
  await loadWorkspaceData()
}

async function remove(row) {
  await ElMessageBox.confirm(`确定删除车位「${row.spaceNo}」吗？`, '删除确认', { type: 'warning' })
  await deleteParkingSpace(row.id)
  ElMessage.success('已删除')
  await loadWorkspaceData()
}

function viewBills() {
  router.push({ path: '/bills', query: { billType: 'PARKING' } })
}

function openMonthlyFee(row) {
  currentSpace.value = row
  feeForm.monthlyFee = Number(row.monthlyFee) > 0 ? Number(row.monthlyFee) : null
  feeVisible.value = true
}

async function saveMonthlyFee() {
  await feeFormRef.value.validate()
  feeSaving.value = true
  try {
    await updateParkingAssignmentMonthlyFee(currentSpace.value.assignmentId, feeForm.monthlyFee)
    ElMessage.success('停车月费已更新')
    feeVisible.value = false
    await loadWorkspaceData()
  } finally {
    feeSaving.value = false
  }
}

function handleSpaceCommand(command, row) {
  if (command === 'edit') {
    openDialog(row)
  } else if (command === 'monthly-fee') {
    openMonthlyFee(row)
  } else if (command === 'bills') {
    viewBills(row)
  } else if (command === 'delete') {
    remove(row)
  }
}

function tenantLabel(tenant) {
  const name = tenant.tenantName || tenant.tenantCode || `租户${tenant.id}`
  return tenant.contactPerson ? `${name}（${tenant.contactPerson}）` : name
}

function normalizeSpaceType(type) {
  if (type === 'FIXED' || type === 'VIP' || !type) return 'NORMAL'
  if (type === 'TEMP') return 'TEMPORARY'
  return type
}

function normalizeLocation(value) {
  if (!value) return ''
  const text = String(value)
  if (locationOptions.includes(text)) return text
  if (text.includes('B1')) return 'B1'
  if (text.includes('B2')) return 'B2'
  if (text.includes('地面')) return '地面'
  return ''
}

function spaceRowClassName({ row }) {
  if (row.__group) return 'view-group-row'
  if (row.status === 'OCCUPIED') return 'row-occupied'
  if (row.status === 'MAINTENANCE') return 'row-warning'
  if (row.status === 'DISABLED') return 'row-muted'
  return ''
}

onMounted(async () => {
  await loadSpaceTypes()
  await Promise.all([loadWorkspaceData(), loadTenants()])
})

onBeforeUnmount(() => {
  listRequestGuard.dispose()
  summaryRequestGuard.dispose()
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

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.toolbar {
  margin-bottom: 12px;
}

.muted {
  color: #909399;
}

.primary-cell,
.action-cell {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.primary-title {
  color: #1f2d3d;
  font-weight: 700;
}

.primary-subtitle,
.cell-muted {
  color: #909399;
  font-size: 12px;
}

.meta-line {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.meta-line span {
  padding: 1px 6px;
  border-radius: 4px;
  background: #f4f6f8;
  color: #606266;
  font-size: 12px;
}

.action-cell {
  align-items: center;
}

.type-select-line {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 32px;
  gap: 8px;
  width: 100%;
}

:deep(.row-occupied) {
  --el-table-tr-bg-color: #f7fbff;
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
</style>
