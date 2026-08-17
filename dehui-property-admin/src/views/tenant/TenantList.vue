<template>
  <div class="page-container">
    <DataWorkspace
      :workspace-config="tenantWorkspaceConfig"
      :views="workspaceViews"
      :active-view-id="activeViewId"
      :view-dirty="isViewDirty"
      :filter-logic="tablePrefs.filterLogic"
      :filter-logic-visible="tablePrefs.filters.length > 1"
      :filter-items="activeFilterItems"
      :query-notices="queryNotices"
      :metric-data="tenantMetricData"
      @change-view="switchTenantView"
      @create-view="createTableView"
      @duplicate-view="duplicateTableView"
      @rename-view="renameTableView"
      @delete-view="deleteTableView"
      @save-view="saveTenantView"
      @remove-filter="removeViewFilter"
    >
      <template #header-actions>
        <el-button
          v-if="hasPermission(tenantPermissions.create)"
          type="primary"
          :icon="Plus"
          @click="openCreateDialog"
        >
          {{ tenantWorkspaceConfig.primaryAction.label }}
        </el-button>
      </template>

      <template #toolbar>
        <DhicDataToolbar
          :workspace-config="tenantWorkspaceConfig"
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
          @save-field-config="saveTenantView"
        >
          <template #right>
            <span class="view-hint">
              全部已加载 {{ resultScope.loadedRecords }} 户，当前视图 {{ resultScope.visibleRecords }} 户
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
          :density="tablePrefs.rowDensity"
          :empty-text="tableEmptyText"
          :sort-state="activeTableSort"
          :data-mode="tenantWorkspaceConfig.query.dataMode"
          :column-width="columnWidth"
          :column-fixed="columnFixed"
          :is-column-sortable="isColumnSortable"
          :show-overflow-tooltip="column => ['email', 'creditCode'].includes(column.key)"
          :row-class-name="tenantRowClassName"
          :operation-column="{ width: 292, fixed: 'right', align: 'left' }"
          @column-resize="handleHeaderDragEnd"
          @sort-change="handleTableSortChange"
          @retry="loadTenants"
          aria-label="租户管理数据表格"
        >
          <template #cell-phone="{ row }">
            <span :class="{ 'invalid-contact': row.phone && !isValidContactPhone(row.phone) }">
              {{ contactText(row.phone, 'phone') }}
            </span>
          </template>
          <template #cell-email="{ row }">
            <span :class="{ 'invalid-contact': row.email && !isValidEmail(row.email) }">
              {{ contactText(row.email, 'email') }}
            </span>
          </template>
          <template #operations="{ row }">
            <el-button v-if="canEditTenant" size="small" @click="openEditDialog(row)">编辑</el-button>
            <el-button v-if="hasPermission(tenantPermissions.update)" size="small" type="primary" @click="openContactDialog(row)">联系人</el-button>
            <el-button size="small" @click="openOverview(row)">详情</el-button>
            <el-button v-if="hasPermission(tenantPermissions.delete)" size="small" type="danger" @click="deleteTenant(row)">删除</el-button>
          </template>
        </WorkspaceDataGrid>
      </template>
    </DataWorkspace>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑租户' : '新增租户'" width="560px">
      <el-form ref="tenantFormRef" :model="form" :rules="tenantRules" label-width="120px">
        <el-form-item label="租户名称" prop="tenantName">
          <el-input v-model="form.tenantName" />
        </el-form-item>

        <el-form-item label="联系人" prop="contactPerson">
          <el-input v-model="form.contactPerson" />
        </el-form-item>

        <el-form-item label="联系电话" prop="contactPhone">
          <el-input v-model="form.contactPhone" placeholder="例如 13800000000 或 021-88888888" />
        </el-form-item>

        <el-form-item label="邮箱" prop="contactEmail">
          <el-input v-model="form.contactEmail" placeholder="例如 name@example.com" />
        </el-form-item>

        <el-form-item label="营业执照" prop="businessLicense">
          <el-input v-model="form.businessLicense" />
        </el-form-item>

        <el-form-item label="状态" prop="status">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="正常" value="ACTIVE" />
            <el-option label="停用" value="INACTIVE" />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button v-if="form.id ? canEditTenant : hasPermission('tenant:create')" type="primary" @click="saveTenant">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="contactDialogVisible"
      :title="currentTenant ? `租户联系人 - ${currentTenant.tenantName}` : '租户联系人'"
      width="820px"
    >
      <div class="contact-toolbar">
        <el-button v-if="hasPermission('tenant:create')" type="primary" @click="openContactForm">新增联系人</el-button>
      </div>

      <el-table :data="contacts" border style="width: 100%">
        <el-table-column prop="name" label="姓名" min-width="120" />
        <el-table-column label="手机号" min-width="150">
          <template #default="{ row }">
            <span :class="{ 'invalid-contact': row.phone && !isValidContactPhone(row.phone) }">
              {{ contactText(row.phone, 'phone') }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="邮箱" min-width="180">
          <template #default="{ row }">
            <span :class="{ 'invalid-contact': row.email && !isValidEmail(row.email) }">
              {{ contactText(row.email, 'email') }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="联系人类型" min-width="120">
          <template #default="{ row }">{{ row.role === 'RESPONSIBLE' || row.isPrimary ? '租户负责人' : '租户员工' }}</template>
        </el-table-column>
        <el-table-column label="主联系人" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isPrimary" type="success">是</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="95" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">
              {{ row.status === 'ACTIVE' ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="290">
          <template #default="{ row }">
            <el-button v-if="hasPermission('tenant:create')" size="small" @click="editContact(row)">编辑</el-button>
            <el-button v-if="hasPermission('tenant:create')" size="small" type="danger" :disabled="row.status !== 'ACTIVE'" @click="deactivateContact(row)">
              停用
            </el-button>
            <el-button v-if="hasPermission('tenant:delete')" size="small" type="danger" @click="deleteContact(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="contactFormVisible" :title="contactForm.id ? '编辑联系人' : '新增联系人'" width="540px">
      <el-form ref="contactFormRef" :model="contactForm" :rules="contactRules" label-width="115px">
        <el-form-item label="姓名" prop="name">
          <el-input v-model="contactForm.name" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="contactForm.phone" placeholder="例如 13800000000" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="contactForm.email" placeholder="例如 name@example.com" />
        </el-form-item>
        <el-form-item label="联系人类型" prop="role">
          <el-select v-model="contactForm.role" style="width: 100%">
            <el-option label="租户负责人" value="RESPONSIBLE" />
            <el-option label="租户员工" value="EMPLOYEE" />
          </el-select>
        </el-form-item>
        <el-form-item label="主联系人" prop="isPrimary">
          <el-switch v-model="contactForm.isPrimary" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="contactForm.status" style="width: 100%">
            <el-option label="正常" value="ACTIVE" />
            <el-option label="停用" value="INACTIVE" />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="contactFormVisible = false">取消</el-button>
        <el-button v-if="hasPermission('tenant:create')" type="primary" @click="saveContact">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer
      v-model="overviewVisible"
      :title="overview?.tenant ? `租户详情 - ${overview.tenant.tenantName}` : '租户详情'"
      size="72%"
      destroy-on-close
    >
      <el-skeleton v-if="overviewLoading" :rows="8" animated />

      <template v-else-if="overview">
        <el-alert
          :title="overview.miniProgramVisibility?.message || '已维护且启用的联系人，可用于后续小程序绑定和账单查看。'"
          :type="overview.contacts?.length ? 'success' : 'warning'"
          show-icon
          :closable="false"
          class="overview-alert"
        />

        <el-tabs v-model="overviewTab">
          <el-tab-pane label="总览" name="summary">
            <div class="summary-grid">
              <div class="summary-card">
                <div class="summary-label">联系人数量</div>
                <div class="summary-value">{{ overview.contacts?.length || 0 }}</div>
              </div>
              <div class="summary-card">
                <div class="summary-label">已发布账单</div>
                <div class="summary-value">{{ overview.billSummary?.publishedCount || 0 }}</div>
              </div>
              <div class="summary-card">
                <div class="summary-label">待缴金额</div>
                <div class="summary-value">{{ money(overview.billSummary?.unpaidAmount) }}</div>
              </div>
              <div class="summary-card">
                <div class="summary-label">租户状态</div>
                <div class="summary-value">{{ overview.tenant.status || '-' }}</div>
              </div>
            </div>

            <el-descriptions :column="2" border>
              <el-descriptions-item label="租户名称">{{ overview.tenant.tenantName }}</el-descriptions-item>
              <el-descriptions-item label="状态">{{ overview.tenant.status || '-' }}</el-descriptions-item>
              <el-descriptions-item label="联系人">{{ overview.tenant.contactPerson || '-' }}</el-descriptions-item>
              <el-descriptions-item label="联系电话">
                <span :class="{ 'invalid-contact': overview.tenant.contactPhone && !isValidContactPhone(overview.tenant.contactPhone) }">
                  {{ contactText(overview.tenant.contactPhone, 'phone') }}
                </span>
              </el-descriptions-item>
              <el-descriptions-item label="邮箱">
                <span :class="{ 'invalid-contact': overview.tenant.contactEmail && !isValidEmail(overview.tenant.contactEmail) }">
                  {{ contactText(overview.tenant.contactEmail, 'email') }}
                </span>
              </el-descriptions-item>
              <el-descriptions-item label="营业执照">{{ overview.tenant.businessLicense || '-' }}</el-descriptions-item>
            </el-descriptions>
          </el-tab-pane>

          <el-tab-pane label="租户联系人" name="contacts">
            <div class="drawer-toolbar">
              <el-button v-if="hasPermission('tenant:update')" type="primary" @click="openContactDialog(overview.tenant)">维护联系人</el-button>
            </div>
            <el-table :data="overview.contacts || []" border>
              <el-table-column prop="name" label="姓名" />
              <el-table-column label="手机号">
                <template #default="{ row }">
                  <span :class="{ 'invalid-contact': row.phone && !isValidContactPhone(row.phone) }">
                    {{ contactText(row.phone, 'phone') }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="邮箱">
                <template #default="{ row }">
                  <span :class="{ 'invalid-contact': row.email && !isValidEmail(row.email) }">
                    {{ contactText(row.email, 'email') }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="联系人类型">
                <template #default="{ row }">{{ row.role === 'RESPONSIBLE' || row.isPrimary ? '租户负责人' : '租户员工' }}</template>
              </el-table-column>
              <el-table-column label="主联系人" width="90">
                <template #default="{ row }"><el-tag v-if="row.isPrimary" type="success">是</el-tag><span v-else>-</span></template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="合同与入驻" name="contracts">
            <el-table :data="overview.contracts || []" border>
              <el-table-column prop="contractNumber" label="合同编号" min-width="140" />
              <el-table-column prop="roomName" label="房间" min-width="120" />
              <el-table-column label="周期" min-width="190">
                <template #default="{ row }">{{ row.startDate || '-' }} 至 {{ row.endDate || '-' }}</template>
              </el-table-column>
              <el-table-column label="费用" min-width="160">
                <template #default="{ row }">租金 {{ money(row.rentAmount) }} / 物业 {{ money(row.propertyFeeAmount) }}</template>
              </el-table-column>
              <el-table-column label="状态" width="100">
                <template #default="{ row }">{{ row.statusText || row.status }}</template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="账单" name="bills">
            <el-alert
              title="待审核账单租户小程序不可见；审核发布后，已绑定该租户的联系人可在小程序账单中心查看。"
              type="info"
              show-icon
              :closable="false"
              class="overview-alert"
            />
            <el-table :data="overview.recentBills || []" border>
              <el-table-column prop="billNumber" label="账单编号" min-width="150" />
              <el-table-column label="类型" width="110">
                <template #default="{ row }">{{ row.billTypeText || row.billType }}</template>
              </el-table-column>
              <el-table-column label="账期" min-width="190">
                <template #default="{ row }">{{ row.periodStart || '-' }} 至 {{ row.periodEnd || '-' }}</template>
              </el-table-column>
              <el-table-column label="金额" width="120">
                <template #default="{ row }">{{ money(row.amount) }}</template>
              </el-table-column>
              <el-table-column label="审核" width="100">
                <template #default="{ row }">
                  <el-tag :type="row.auditStatus === 'APPROVED' ? 'success' : 'warning'">{{ row.auditStatusText }}</el-tag>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import DataWorkspace from '../../components/DataWorkspace.vue'
import DhicDataToolbar from '../../components/DhicDataToolbar.vue'
import WorkspaceDataGrid from '../../components/workspace/WorkspaceDataGrid.vue'
import { useDhicTableView } from '../../composables/useDhicTableView'
import { useWorkspaceListRequestState } from '../../composables/workspace/useWorkspaceRequestState'
import request from '../../utils/request'
import { readPage } from '../../utils/pagination'
import { hasPermission } from '../../utils/permission'
import {
  isValidContactPhone,
  isValidEmail,
  optionalContactPhoneRule,
  optionalEmailRule,
  validateContactPhone
} from '../../utils/contactValidation'
import {
  buildTenantMetricSnapshot,
  normalizeTenantWorkspaceRecord,
  tenantStatusOptions,
  tenantWorkspaceConfig
} from '../../workspaceConfigs/tenantWorkspaceConfig'
import { workspaceTableOptions } from '../../workspaceConfigs/workspaceConfig'

const tenants = ref([])
const dialogVisible = ref(false)
const contactDialogVisible = ref(false)
const contactFormVisible = ref(false)
const overviewVisible = ref(false)
const overviewLoading = ref(false)
const overviewTab = ref('summary')
const contacts = ref([])
const currentTenant = ref(null)
const overview = ref(null)
const tenantFormRef = ref()
const contactFormRef = ref()
const tenantPermissions = tenantWorkspaceConfig.permissions
const listState = useWorkspaceListRequestState({
  hasResults: () => tenants.value.length > 0,
  clearResults: () => {
    tenants.value = []
  },
  staleMessage: '租户列表刷新失败，当前显示上次成功数据'
})
const loading = computed(() => listState.status.value === 'loading')
const listErrorText = listState.errorText

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
} = useDhicTableView(workspaceTableOptions(tenantWorkspaceConfig, {
  records: () => tenants.value,
  serverTotal: () => tenants.value.length,
  groupLabel: value => tenantStatusOptions.find(option => option.value === value)?.label || value || '未填写'
}))

const tenantMetricData = computed(() => buildTenantMetricSnapshot(tenants.value, visibleRecords.value))

const form = reactive({
  id: null,
  tenantName: '',
  contactPerson: '',
  contactPhone: '',
  contactEmail: '',
  businessLicense: '',
  status: 'ACTIVE'
})

const contactForm = reactive({
  id: null,
  name: '',
  phone: '',
  email: '',
  role: 'EMPLOYEE',
  isPrimary: false,
  status: 'ACTIVE'
})

const requiredContactPhoneRule = {
  validator(rule, value, callback) {
    if (!String(value || '').trim()) {
      callback(new Error('请输入联系电话'))
      return
    }
    validateContactPhone(rule, value, callback)
  },
  trigger: 'blur'
}

const tenantRules = {
  tenantName: [{ required: true, message: '请输入租户名称', trigger: 'blur' }],
  contactPhone: [optionalContactPhoneRule],
  contactEmail: [optionalEmailRule]
}

const contactRules = {
  name: [{ required: true, message: '请输入联系人姓名', trigger: 'blur' }],
  phone: [requiredContactPhoneRule],
  email: [optionalEmailRule]
}

const canEditTenant = computed(() => (
  hasPermission(tenantPermissions.update) && hasPermission(tenantPermissions.create)
))

function resetForm() {
  Object.assign(form, {
    id: null,
    tenantName: '',
    contactPerson: '',
    contactPhone: '',
    contactEmail: '',
    businessLicense: '',
    status: 'ACTIVE'
  })
  tenantFormRef.value?.clearValidate()
}

function resetContactForm() {
  Object.assign(contactForm, {
    id: null,
    name: '',
    phone: '',
    email: '',
    role: 'EMPLOYEE',
    isPrimary: false,
    status: 'ACTIVE'
  })
  contactFormRef.value?.clearValidate()
}

async function switchTenantView(viewId) {
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

function saveTenantView() {
  saveCurrentView()
  ElMessage.success(`视图“${activeViewName.value}”已保存`)
}

async function loadTenants() {
  const requestId = listRequestGuard.start()
  const requestContext = listState.begin({ resource: '/tenant/list' })
  try {
    const data = await request.get('/tenant/list', { silent: true })
    if (!listRequestGuard.isCurrent(requestId) || !listState.isCurrent(requestContext)) return
    tenants.value = readPage(data).records.map(normalizeTenantWorkspaceRecord)
    listState.succeed(requestContext)
  } catch (error) {
    if (!listRequestGuard.isCurrent(requestId) || !listState.isCurrent(requestContext)) return
    listState.fail(requestContext, error?.response?.data?.message || '租户列表加载失败，请稍后重试')
  }
}

async function loadContacts() {
  if (!currentTenant.value) return
  const data = await request.get(`/tenant/${currentTenant.value.id}/contacts`)
  contacts.value = readPage(data).records
}

async function loadOverview(tenantId) {
  overviewLoading.value = true
  try {
    overview.value = await request.get(`/tenant/${tenantId}/overview`)
  } finally {
    overviewLoading.value = false
  }
}

function openCreateDialog() {
  resetForm()
  dialogVisible.value = true
}

function openEditDialog(row) {
  resetForm()
  Object.assign(form, {
    id: row.id,
    tenantName: row.tenantName || row.name || '',
    contactPerson: row.contactPerson ?? row.contactName ?? '',
    contactPhone: row.contactPhone ?? row.phone ?? '',
    contactEmail: row.contactEmail ?? row.email ?? '',
    businessLicense: row.businessLicense ?? row.creditCode ?? '',
    status: row.status || 'ACTIVE'
  })
  dialogVisible.value = true
}

async function openContactDialog(row) {
  currentTenant.value = row
  contactDialogVisible.value = true
  await loadContacts()
}

async function openOverview(row) {
  overview.value = null
  overviewTab.value = 'summary'
  overviewVisible.value = true
  await loadOverview(row.id)
}

function openContactForm() {
  resetContactForm()
  contactFormVisible.value = true
}

function editContact(row) {
  resetContactForm()
  Object.assign(contactForm, {
    id: row.id,
    name: row.name || '',
    phone: row.phone || '',
    email: row.email || '',
    role: row.role || (row.isPrimary ? 'RESPONSIBLE' : 'EMPLOYEE'),
    isPrimary: Boolean(row.isPrimary),
    status: row.status || 'ACTIVE'
  })
  contactFormVisible.value = true
}

async function saveTenant() {
  const valid = await tenantFormRef.value.validate().catch(() => false)
  if (!valid) return
  if (!isValidContactPhone(form.contactPhone)) {
    ElMessage.warning('请输入正确的联系电话，例如 13800000000 或 021-88888888')
    return
  }
  if (!isValidEmail(form.contactEmail)) {
    ElMessage.warning('请输入正确的邮箱地址，例如 name@example.com')
    return
  }

  await request.post('/tenant/save', { ...form })
  ElMessage.success('保存成功')

  dialogVisible.value = false
  await loadTenants()
  if (overviewVisible.value && form.id) {
    await loadOverview(form.id)
  }
}

async function deleteTenant(row) {
  await ElMessageBox.confirm(`确定删除租户“${row.tenantName || row.name}”吗？`, '删除确认', { type: 'warning' })
  await request.delete(`/tenants/${row.id}`)
  ElMessage.success('租户已删除')
  await loadTenants()
  if (overviewVisible.value && overview.value?.tenant?.id === row.id) {
    overviewVisible.value = false
    overview.value = null
  }
}

async function saveContact() {
  if (!currentTenant.value) return
  const valid = await contactFormRef.value.validate().catch(() => false)
  if (!valid) return
  if (!String(contactForm.phone || '').trim()) {
    ElMessage.warning('请输入联系电话')
    return
  }
  if (!isValidContactPhone(contactForm.phone)) {
    ElMessage.warning('请输入正确的联系电话，例如 13800000000 或 021-88888888')
    return
  }
  if (!isValidEmail(contactForm.email)) {
    ElMessage.warning('请输入正确的邮箱地址，例如 name@example.com')
    return
  }

  const data = await request.post(`/tenant/${currentTenant.value.id}/contacts`, {
    id: contactForm.id,
    name: contactForm.name,
    phone: contactForm.phone,
    email: contactForm.email,
    role: contactForm.role,
    isPrimary: contactForm.isPrimary,
    status: contactForm.status
  })
  contactFormVisible.value = false
  await loadContacts()

  ElMessage.success(data?.loginHint || '联系人已保存；该手机号可直接通过微信手机号快捷登录。')
  if (overviewVisible.value && currentTenant.value) {
    await loadOverview(currentTenant.value.id)
  }
}

async function deactivateContact(row) {
  await ElMessageBox.confirm(`确定停用联系人「${row.name}」吗？停用后无法绑定小程序。`, '停用确认', { type: 'warning' })
  await request.post(`/tenant/contacts/${row.id}/deactivate`)
  ElMessage.success('联系人已停用')
  await loadContacts()
  if (overviewVisible.value && currentTenant.value) {
    await loadOverview(currentTenant.value.id)
  }
}

async function deleteContact(row) {
  if (!currentTenant.value) return
  const contactName = row.name || row.phone || row.email || row.id
  await ElMessageBox.confirm(`确定删除联系人「${contactName}」吗？删除后该账号将不能继续用于小程序登录和绑定。`, '删除确认', { type: 'warning' })
  await request.delete(`/tenant/${currentTenant.value.id}/contacts/${row.id}`)
  ElMessage.success('联系人已删除')
  await loadContacts()
  if (overviewVisible.value && currentTenant.value) {
    await loadOverview(currentTenant.value.id)
  }
}

function contactText(value, type) {
  if (!value) return '-'
  const valid = type === 'phone' ? isValidContactPhone(value) : isValidEmail(value)
  return valid ? value : `${value}（格式异常）`
}

function money(value) {
  return Number(value || 0).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })
}

function tenantRowClassName({ row }) {
  if (row.__group) return 'view-group-row'
  if (['INACTIVE', 'DISABLED'].includes(row.status)) return 'row-muted'
  return ''
}

onMounted(() => {
  loadTenants()
})
onBeforeUnmount(disposeQueryLifecycle)
</script>

<style scoped>
.page-container {
  padding: 20px;
}

.view-hint {
  color: var(--dhic-text-muted);
  font-size: 12px;
  white-space: nowrap;
}

.invalid-contact {
  color: #d93025;
  font-weight: 600;
}

.contact-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 14px;
}

.overview-alert {
  margin-bottom: 16px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.summary-card {
  padding: 14px 16px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  background: #fafafa;
}

.summary-label {
  color: #909399;
  font-size: 13px;
}

.summary-value {
  margin-top: 8px;
  color: #303133;
  font-size: 20px;
  font-weight: 600;
}

.drawer-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}

@media (max-width: 900px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
