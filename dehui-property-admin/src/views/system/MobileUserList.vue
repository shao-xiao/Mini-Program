<template>
  <div class="page-container">
    <el-alert
      class="mobile-user-notice"
      title="小程序用户由微信手机号授权自动生成并匹配；租户身份来自租户联系人，员工身份来自后台用户与角色权限。"
      type="info"
      show-icon
      :closable="false"
    />
    <DataWorkspace
      :workspace-config="mobileUserWorkspaceConfig"
      :views="workspaceViews"
      :active-view-id="activeViewId"
      :view-dirty="isViewDirty"
      :filter-logic="tablePrefs.filterLogic"
      :filter-logic-visible="tablePrefs.filters.length > 1"
      :filter-items="activeFilterItems"
      :query-notices="queryNotices"
      :metric-data="mobileUserMetricData"
      @change-view="switchMobileUserView"
      @create-view="createTableView"
      @duplicate-view="duplicateTableView"
      @rename-view="renameTableView"
      @delete-view="deleteTableView"
      @save-view="saveMobileUserView"
      @remove-filter="removeViewFilter"
    >
      <template #toolbar>
        <DhicDataToolbar
          :workspace-config="mobileUserWorkspaceConfig"
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
          @save-field-config="saveMobileUserView"
        >
          <template #right>
            <el-button :icon="Refresh" @click="loadUsers">刷新</el-button>
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
          :data-mode="mobileUserWorkspaceConfig.query.dataMode"
          :column-width="columnWidth"
          :column-fixed="columnFixed"
          :is-column-sortable="isColumnSortable"
          :show-overflow-tooltip="column => ['tenantName', 'tenantContactName', 'boundSysRealName'].includes(column.key)"
          :operation-column="{ width: 220, fixed: 'right', align: 'left' }"
          @column-resize="handleHeaderDragEnd"
          @sort-change="handleTableSortChange"
          @retry="loadUsers"
          aria-label="小程序用户管理数据表格"
        >
          <template #operations="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button v-if="hasPermission('mobile:update')" link type="primary" @click="toggleStatus(row)">
              {{ row.status === 'DISABLED' ? '启用' : '停用' }}
            </el-button>
            <el-button
              v-if="hasPermission('mobile:update') && row.bindingState === 'BOUND'"
              link
              type="danger"
              @click="unbind(row)"
            >解除绑定</el-button>
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
            @current-change="loadUsers"
            @size-change="handleSizeChange"
          />
        </div>
      </template>
    </DataWorkspace>

    <el-dialog v-model="detailDialogVisible" title="小程序用户详情" width="760px" destroy-on-close>
      <div v-loading="detailLoading" class="mobile-user-detail">
        <el-descriptions v-if="detail.id" :column="2" border size="small">
          <el-descriptions-item label="用户编号">{{ detail.code || '-' }}</el-descriptions-item>
          <el-descriptions-item label="微信昵称">{{ detail.nickname || '-' }}</el-descriptions-item>
          <el-descriptions-item label="姓名">{{ detail.realName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="验证手机号">{{ detail.verifiedPhone || '-' }}</el-descriptions-item>
          <el-descriptions-item label="当前身份">{{ detail.userType || 'PUBLIC' }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ detail.status || '-' }}</el-descriptions-item>
        </el-descriptions>

        <h4>验证手机号可用身份</h4>
        <div v-for="identity in detail.availableIdentities || []" :key="identity.bindId" class="identity-row">
          <div>
            <strong>{{ identity.userType === 'STAFF' ? identity.staffRealName : identity.tenantName }}</strong>
            <span class="view-hint">
              {{ identity.userType === 'STAFF' ? '物业员工' : (identity.contactRole === 'RESPONSIBLE' ? '租户负责人' : '租户员工') }}
            </span>
          </div>
          <el-tag v-if="identity.current" type="success">当前身份</el-tag>
          <el-button
            v-else-if="hasPermission('mobile:update')"
            link
            type="primary"
            @click="setDefaultIdentity(identity)"
          >设为默认</el-button>
        </div>
        <el-empty v-if="detail.id && !(detail.availableIdentities || []).length" description="当前验证手机号没有合法候选身份" />

        <h4>绑定审计</h4>
        <div v-for="audit in detail.auditRecords || []" :key="audit.id" class="audit-row">
          <span>{{ audit.createdAt || '-' }}</span>
          <span>{{ audit.action }}</span>
          <span>{{ audit.detail || '-' }}</span>
        </div>
        <el-empty v-if="detail.id && !(detail.auditRecords || []).length" description="暂无绑定审计记录" />
      </div>
      <template #footer><el-button @click="detailDialogVisible = false">关闭</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import DataWorkspace from '../../components/DataWorkspace.vue'
import DhicDataToolbar from '../../components/DhicDataToolbar.vue'
import WorkspaceDataGrid from '../../components/workspace/WorkspaceDataGrid.vue'
import { useDhicTableView } from '../../composables/useDhicTableView'
import request from '../../utils/request'
import { createPagination, readPage, resetToFirstPage } from '../../utils/pagination'
import { hasPermission } from '../../utils/permission'
import {
  createMobileUserWorkspaceConfig,
  mobileUserTypeOptions,
  systemStatusOptions
} from '../../workspaceConfigs/systemWorkspaceConfig'
import { workspaceTableOptions } from '../../workspaceConfigs/workspaceConfig'

const mobileUserWorkspaceConfig = createMobileUserWorkspaceConfig()
const loading = ref(false)
const loadError = ref('')
const detailDialogVisible = ref(false)
const detailLoading = ref(false)
const detail = reactive({})
const users = ref([])
const pagination = reactive(createPagination(20))

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
  debouncedQueryTask: debouncedMobileUserQuery,
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
} = useDhicTableView(workspaceTableOptions(mobileUserWorkspaceConfig, {
  records: () => users.value,
  serverTotal: () => pagination.total,
  queryLifecycle: {
    task: () => {
      resetToFirstPage(pagination)
      loadUsers()
    }
  },
  groupLabel: value => (
    mobileUserTypeOptions.find(option => option.value === value)?.label
    || systemStatusOptions.find(option => option.value === value)?.label
    || value
    || '未填写'
  )
}))

watch(() => JSON.stringify(serverQueryParams.value), () => debouncedMobileUserQuery.schedule())

const mobileUserMetricData = computed(() => ({
  totalCount: pagination.total,
  currentPageCount: users.value.length
}))

async function switchMobileUserView(viewId) {
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

function saveMobileUserView() {
  saveCurrentView()
  ElMessage.success(`视图“${activeViewName.value}”已保存`)
}

async function loadUsers() {
  const requestId = listRequestGuard.start()
  loading.value = true
  loadError.value = ''
  try {
    const data = await request.get('/mobile-users', {
      params: {
        ...serverQueryParams.value,
        page: pagination.currentPage - 1,
        size: pagination.pageSize
      },
      silent: true
    })
    if (!listRequestGuard.isCurrent(requestId)) return
    const page = readPage(data)
    users.value = page.records
    pagination.total = page.total
  } catch (error) {
    if (!listRequestGuard.isCurrent(requestId)) return
    users.value = []
    pagination.total = 0
    loadError.value = error?.response?.data?.message || '小程序用户加载失败，请稍后重试'
  } finally {
    if (listRequestGuard.isCurrent(requestId)) loading.value = false
  }
}

function handleSizeChange() {
  resetToFirstPage(pagination)
  loadUsers()
}

async function openDetail(row) {
  detailDialogVisible.value = true
  detailLoading.value = true
  Object.keys(detail).forEach(key => delete detail[key])
  try {
    Object.assign(detail, await request.get(`/mobile-users/${row.id}`, { silent: true }))
  } finally {
    detailLoading.value = false
  }
}

async function setDefaultIdentity(identity) {
  await ElMessageBox.confirm('默认身份只能从服务端按验证手机号计算出的合法候选中选择，确认切换？', '设置默认身份')
  await request.post(`/mobile-users/${detail.id}/default-identity`, { bindId: identity.bindId })
  ElMessage.success('默认身份已更新')
  await openDetail({ id: detail.id })
  await loadUsers()
}

async function toggleStatus(row) {
  const next = row.status === 'DISABLED' ? 'ENABLED' : 'DISABLED'
  await ElMessageBox.confirm(
    next === 'DISABLED' ? '停用后现有移动 token 将立即失效，确认继续？' : '启用后仍需用户重新验证手机号，确认继续？',
    next === 'DISABLED' ? '停用小程序用户' : '启用小程序用户'
  )
  await request.patch(`/mobile-users/${row.id}/status`, { status: next })
  ElMessage.success('状态已更新')
  await loadUsers()
}

async function unbind(row) {
  await ElMessageBox.confirm('解除后不删除历史记录，用户需要重新完成微信手机号验证。确认继续？', '解除身份绑定', { type: 'warning' })
  await request.post(`/mobile-users/${row.id}/unbind`)
  ElMessage.success('绑定已解除')
  await loadUsers()
}

onMounted(loadUsers)
onBeforeUnmount(disposeQueryLifecycle)
</script>

<style scoped>
.page-container { min-width: 0; }
.mobile-user-notice { margin-bottom: 12px; }
.view-hint { color: var(--dhic-text-muted); font-size: 12px; white-space: nowrap; }
.pagination-wrapper { display: flex; justify-content: flex-end; }
.mobile-user-detail { min-width: 0; }
.mobile-user-detail h4 { margin: 18px 0 10px; color: var(--dhic-text-primary); font-size: 14px; }
.identity-row,
.audit-row {
  display: grid;
  grid-template-columns: 1fr auto;
  align-items: center;
  gap: 12px;
  min-height: 42px;
  border-bottom: 1px solid var(--dhic-border-light, #f0f2f5);
}
.identity-row > div { display: flex; align-items: center; gap: 10px; }
.audit-row { grid-template-columns: 170px 190px minmax(220px, 1fr); min-width: 620px; font-size: 13px; }
</style>
