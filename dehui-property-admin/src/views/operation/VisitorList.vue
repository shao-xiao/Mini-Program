<template>
  <main class="page-container">
    <DataWorkspace
      :workspace-config="visitorWorkspaceConfig"
      :views="workspaceViews"
      :active-view-id="activeViewId"
      :view-dirty="isViewDirty"
      :filter-logic="tablePrefs.filterLogic"
      :filter-logic-visible="tablePrefs.filters.length > 1"
      :filter-items="activeFilterItems"
      :query-notices="queryNotices"
      :metric-data="visitorMetricData"
      @change-view="switchVisitorView"
      @create-view="createTableView"
      @duplicate-view="duplicateTableView"
      @rename-view="renameTableView"
      @delete-view="deleteTableView"
      @save-view="saveVisitorView"
      @remove-filter="removeViewFilter"
    >
      <template #header-actions>
        <el-button v-if="hasPermission('custom-field:manage')" @click="moduleSettingsVisible = true">
          模块字段配置
        </el-button>
        <el-button v-if="hasPermission('visitor:create')" type="primary" :icon="Plus" @click="openDialog">
          {{ visitorWorkspaceConfig.primaryAction.label }}
        </el-button>
      </template>

      <template #toolbar>
        <DhicDataToolbar
          :workspace-config="visitorWorkspaceConfig"
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
          @save-field-config="saveVisitorView"
        >
          <template #right>
            <el-button :icon="Refresh" @click="loadVisitors">刷新</el-button>
            <span class="view-hint">已加载 {{ resultScope.loadedRecords }} 条，当前视图 {{ resultScope.visibleRecords }} 条</span>
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
          :density="tablePrefs.rowDensity"
          :empty-text="tableEmptyText"
          :sort-state="activeTableSort"
          :data-mode="visitorWorkspaceConfig.query.dataMode"
          :column-width="columnWidth"
          :column-fixed="columnFixed"
          :is-column-sortable="isColumnSortable"
          :show-overflow-tooltip="column => ['remark', 'tenantName'].includes(column.key)"
          :operation-column="{ width: 330, fixed: 'right', align: 'left' }"
          @column-resize="handleHeaderDragEnd"
          @sort-change="handleTableSortChange"
          @retry="loadVisitors"
          aria-label="访客管理数据表格"
        >
          <template #operations="{ row }">
            <div class="row-actions">
              <el-button link type="primary" @click="openDetail(row)">详情</el-button>
              <el-button v-if="hasPermission('visitor:update')" link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button
                v-if="hasPermission('visitor:update') && row.status === 'PENDING'"
                link
                type="success"
                @click="reviewVisitor(row, 'approve')"
              >通过</el-button>
              <el-button
                v-if="hasPermission('visitor:update') && row.status === 'PENDING'"
                link
                type="danger"
                @click="reviewVisitor(row, 'reject')"
              >驳回</el-button>
              <el-button
                v-if="hasPermission('visitor:update') && row.status === 'REGISTERED'"
                link
                type="primary"
                @click="openQrDialog(row)"
              >{{ row.qrCodeFileId ? '更换二维码' : '上传二维码' }}</el-button>
              <el-button
                v-if="hasPermission('visitor:update') && row.status === 'REGISTERED'"
                link
                type="success"
                @click="enter(row)"
              >入场</el-button>
              <el-button
                v-if="hasPermission('visitor:update') && row.status === 'ENTERED'"
                link
                type="warning"
                @click="leave(row)"
              >离场</el-button>
              <el-button
                v-if="hasPermission('visitor:update') && ['PENDING', 'REGISTERED'].includes(row.status)"
                link
                type="danger"
                @click="cancel(row)"
              >取消</el-button>
            </div>
          </template>
        </WorkspaceDataGrid>
      </template>
    </DataWorkspace>

    <el-dialog v-model="visible" :title="editingId ? '编辑访客' : '访客登记'" width="620px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <ModuleSchemaForm
          ref="schemaFormRef"
          module-key="visitors"
          :fields="schemaFields"
          :values="form"
          @update:values="schemaPayload = $event"
        />
        <template v-if="false">
        <el-form-item label="访客姓名" prop="visitorName">
          <el-input v-model="form.visitorName" />
        </el-form-item>

        <el-form-item label="手机号" prop="visitorPhone">
          <el-input v-model="form.visitorPhone" />
        </el-form-item>

        <el-form-item label="身份证号">
          <el-input v-model="form.idCardNo" />
        </el-form-item>

        <el-form-item label="车牌号">
          <el-input v-model="form.carPlateNo" placeholder="可选" />
        </el-form-item>

        <el-form-item label="租户ID">
          <el-input-number v-model="form.tenantId" :min="1" style="width: 100%" />
        </el-form-item>

        <el-form-item label="被访人" prop="visitedPerson">
          <el-input v-model="form.visitedPerson" />
        </el-form-item>

        <el-form-item label="来访事由" prop="visitReason">
          <el-input v-model="form.visitReason" />
        </el-form-item>

        <el-form-item label="来访时间" prop="visitTime">
          <el-date-picker
            v-model="form.visitTime"
            type="datetime"
            format="YYYY-MM-DD HH:mm"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" />
        </el-form-item>
        </template>
        <el-form-item label="关联租户">
          <el-select v-model="form.tenantId" clearable filterable style="width: 100%">
            <el-option v-for="tenant in tenants" :key="tenant.id" :label="tenant.tenantName || tenant.name" :value="tenant.id" />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button
          v-if="editingId ? hasPermission('visitor:update') : hasPermission('visitor:create')"
          type="primary"
          :loading="saving"
          @click="save"
        >保存</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="detailVisible" title="访客详情" width="760px">
      <el-descriptions v-if="detailValues.id" class="review-summary" :column="2" border>
        <el-descriptions-item label="审核状态">{{ visitorStatusLabel(detailValues.status) }}</el-descriptions-item>
        <el-descriptions-item label="门禁二维码">
          <span>{{ detailValues.qrCodeFileId ? '已上传' : (detailValues.status === 'REGISTERED' ? '待上传' : '—') }}</span>
          <el-button
            v-if="hasPermission('visitor:update') && detailValues.status === 'REGISTERED'"
            class="detail-qr-action"
            link
            type="primary"
            @click="openQrDialog(detailValues)"
          >{{ detailValues.qrCodeFileId ? '更换' : '上传' }}</el-button>
        </el-descriptions-item>
        <el-descriptions-item label="审核时间">{{ detailValues.reviewedAt || '—' }}</el-descriptions-item>
        <el-descriptions-item label="审核意见">{{ detailValues.reviewRemark || '—' }}</el-descriptions-item>
      </el-descriptions>
      <ModuleSchemaDetail :fields="detailSchemaFields" :values="detailValues" />
    </el-dialog>
    <ModuleFieldSettingsDialog v-model="moduleSettingsVisible" module-key="visitors" />
    <VisitorQrCodeDialog
      v-model="qrDialogVisible"
      :visitor="qrVisitor"
      @saved="handleQrSaved"
    />
  </main>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import DataWorkspace from '../../components/DataWorkspace.vue'
import DhicDataToolbar from '../../components/DhicDataToolbar.vue'
import WorkspaceDataGrid from '../../components/workspace/WorkspaceDataGrid.vue'
import ModuleSchemaForm from '../../components/schema/ModuleSchemaForm.vue'
import ModuleSchemaDetail from '../../components/schema/ModuleSchemaDetail.vue'
import ModuleFieldSettingsDialog from '../../components/schema/ModuleFieldSettingsDialog.vue'
import VisitorQrCodeDialog from '../../components/visitor/VisitorQrCodeDialog.vue'
import { getModuleFieldSchema } from '../../api/customFields.js'
import { normalizeModuleSchema } from '../../utils/moduleFieldSchema.js'
import { useDhicTableView } from '../../composables/useDhicTableView'
import { useWorkspaceListRequestState } from '../../composables/workspace/useWorkspaceRequestState'
import request from '../../utils/request'
import { readPage } from '../../utils/pagination'
import { hasPermission } from '../../utils/permission'
import {
  buildVisitorMetricSnapshot,
  normalizeVisitorWorkspaceRecord,
  visitorStatusOptions,
  visitorWorkspaceConfig
} from '../../workspaceConfigs/operationWorkspaceConfig'
import { workspaceTableOptions } from '../../workspaceConfigs/workspaceConfig'

const list = ref([])
const tenants = ref([])
const saving = ref(false)
const visible = ref(false)
const editingId = ref(null)
const detailVisible = ref(false)
const detailSchemaFields = ref([])
const detailValues = ref({})
const moduleSettingsVisible = ref(false)
const qrDialogVisible = ref(false)
const qrVisitor = ref(null)
const formRef = ref(null)
const schemaFormRef = ref(null)
const schemaFields = ref([])
let schemaPayload = {}
const listState = useWorkspaceListRequestState({
  hasResults: () => list.value.length > 0,
  clearResults: () => {
    list.value = []
  },
  staleMessage: '访客记录刷新失败，当前显示上次成功数据'
})
const loading = computed(() => listState.status.value === 'loading')
const listErrorText = listState.errorText
const tenantById = computed(() => new Map(tenants.value.map(tenant => [Number(tenant.id), tenant.tenantName || tenant.name])))
const visitorRows = computed(() => list.value.map(row => normalizeVisitorWorkspaceRecord(row, tenantById.value)))

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
} = useDhicTableView(workspaceTableOptions(visitorWorkspaceConfig, {
  records: () => visitorRows.value,
  serverTotal: () => visitorRows.value.length,
  groupLabel: value => visitorStatusOptions.find(option => option.value === value)?.label || value || '未填写'
}))

const visitorMetricData = computed(() => buildVisitorMetricSnapshot(visitorRows.value, visibleRecords.value))

const defaultForm = () => ({
  visitorName: '',
  visitorPhone: '',
  companyName: '',
  plateNumber: '',
  visitDate: '',
  visitTime: '',
  idCardNo: '',
  carPlateNo: '',
  tenantId: null,
  visitedPerson: '',
  visitReason: '',
  visitTime: '',
  remark: ''
})

const form = reactive(defaultForm())

const rules = {
  visitorName: [{ required: true, message: '请输入访客姓名', trigger: 'blur' }],
  visitorPhone: [{ required: true, message: '请输入手机号', trigger: 'blur' }],
  visitedPerson: [{ required: true, message: '请输入被访人', trigger: 'blur' }],
  visitReason: [{ required: true, message: '请输入来访事由', trigger: 'blur' }],
  visitTime: [{ required: true, message: '请选择来访时间', trigger: 'change' }]
}

function resetForm() {
  editingId.value = null
  Object.assign(form, defaultForm())
  formRef.value?.clearValidate()
}

async function switchVisitorView(viewId) {
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

function saveVisitorView() {
  saveCurrentView()
  ElMessage.success(`视图“${activeViewName.value}”已保存`)
}

async function loadVisitors() {
  const requestId = listRequestGuard.start()
  const requestContext = listState.begin({ resource: '/visitors' })
  try {
    const data = await request.get('/visitors', { silent: true })
    if (!listRequestGuard.isCurrent(requestId) || !listState.isCurrent(requestContext)) return
    list.value = readPage(data).records
    listState.succeed(requestContext)
  } catch (error) {
    if (!listRequestGuard.isCurrent(requestId) || !listState.isCurrent(requestContext)) return
    listState.fail(requestContext, error?.response?.data?.message || '访客记录加载失败，请稍后重试')
  }
}

async function loadTenants() {
  try {
    const data = await request.get('/tenant/list', { silent: true })
    tenants.value = readPage(data).records
  } catch {
    tenants.value = []
  }
}

async function openDialog() {
  resetForm()
  if (!schemaFields.value.length) {
    schemaFields.value = normalizeModuleSchema(await getModuleFieldSchema('visitors', 'adminForm'))
  }
  visible.value = true
}

async function openEdit(row) {
  resetForm()
  if (!schemaFields.value.length) {
    schemaFields.value = normalizeModuleSchema(await getModuleFieldSchema('visitors', 'adminForm'))
  }
  const detail = await request.get(`/visitors/${row.id}`, { silent: true })
  editingId.value = row.id
  Object.assign(form, {
    ...detail,
    tenantId: detail.tenantId ?? null,
    customFields: detail.customFields || {}
  })
  visible.value = true
}

async function openDetail(row) {
  if (!detailSchemaFields.value.length) {
    detailSchemaFields.value = normalizeModuleSchema(await getModuleFieldSchema('visitors', 'adminDetail'))
  }
  detailValues.value = await request.get(`/visitors/${row.id}`, { silent: true })
  detailVisible.value = true
}

function visitorStatusLabel(status) {
  return visitorStatusOptions.find(option => option.value === status)?.label || status || '未知'
}

async function reviewVisitor(row, action) {
  const approving = action === 'approve'
  try {
    const result = await ElMessageBox.prompt(
      approving ? '可填写审核意见；通过后即可上传门禁二维码。' : '请填写驳回原因，申请人将在小程序中看到该说明。',
      approving ? '审核通过' : '驳回预约',
      {
        confirmButtonText: approving ? '通过并继续' : '确认驳回',
        cancelButtonText: '取消',
        inputPlaceholder: approving ? '审核意见（选填）' : '驳回原因（必填）',
        inputValidator: value => approving || String(value || '').trim() ? true : '请填写驳回原因',
        type: approving ? 'success' : 'warning'
      }
    )
    const payload = { reviewRemark: String(result.value || '').trim() }
    if (approving) await request.patch(`/visitors/${row.id}/approve`, payload)
    else await request.patch(`/visitors/${row.id}/reject`, payload)
    ElMessage.success(approving ? '审核已通过，请上传门禁二维码' : '预约已驳回')
    await loadVisitors()
    if (approving) {
      qrVisitor.value = list.value.find(item => Number(item.id) === Number(row.id)) || {
        ...row,
        status: 'REGISTERED'
      }
      qrDialogVisible.value = true
    }
  } catch (error) {
    if (error !== 'cancel' && error !== 'close' && !error?.requestMessageShown) {
      ElMessage.error(error?.response?.data?.message || error?.message || '审核操作失败')
    }
  }
}

function openQrDialog(row) {
  qrVisitor.value = { ...row }
  qrDialogVisible.value = true
}

async function handleQrSaved() {
  const visitorId = qrVisitor.value?.id
  await loadVisitors()
  qrVisitor.value = list.value.find(item => Number(item.id) === Number(visitorId)) || qrVisitor.value
  if (detailValues.value?.id && Number(detailValues.value.id) === Number(visitorId)) {
    detailValues.value = await request.get(`/visitors/${visitorId}`, { silent: true })
  }
}

async function save() {
  if (!schemaFormRef.value?.validate()) return
  schemaPayload = schemaFormRef.value.getPayload()
  saving.value = true
  try {
    const payload = {
      ...schemaPayload,
      tenantId: form.tenantId,
    }
    if (editingId.value) await request.put(`/visitors/${editingId.value}`, payload)
    else await request.post('/visitors', payload)
    ElMessage.success(editingId.value ? '访客更新成功' : '访客登记成功')
    visible.value = false
    resetForm()
    await loadVisitors()
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '访客登记失败')
  } finally {
    saving.value = false
  }
}

async function enter(row) {
  await request.patch(`/visitors/${row.id}/enter`)
  ElMessage.success('访客已入场')
  await loadVisitors()
}

async function leave(row) {
  await request.patch(`/visitors/${row.id}/leave`)
  ElMessage.success('访客已离场')
  await loadVisitors()
}

async function cancel(row) {
  try {
    await ElMessageBox.confirm(`确定取消访客「${row.visitorName}」的来访登记吗？`, '取消确认', {
      type: 'warning'
    })
    await request.patch(`/visitors/${row.id}/cancel`)
    ElMessage.success('已取消')
    await loadVisitors()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error?.response?.data?.message || '取消失败')
  }
}

onMounted(async () => {
  await Promise.allSettled([loadTenants(), loadVisitors()])
})

onBeforeUnmount(() => {
  listRequestGuard.dispose()
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

.review-summary {
  margin-bottom: 18px;
}

.detail-qr-action {
  margin-left: 8px;
}
</style>
