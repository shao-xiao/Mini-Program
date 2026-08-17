<template>
  <div class="page-container">
    <DataWorkspace
      :workspace-config="systemUserWorkspaceConfig"
      :views="workspaceViews"
      :active-view-id="activeViewId"
      :view-dirty="isViewDirty"
      :filter-logic="tablePrefs.filterLogic"
      :filter-logic-visible="tablePrefs.filters.length > 1"
      :filter-items="activeFilterItems"
      :query-notices="queryNotices"
      :metric-data="userMetricData"
      @change-view="switchUserView"
      @create-view="createTableView"
      @duplicate-view="duplicateTableView"
      @rename-view="renameTableView"
      @delete-view="deleteTableView"
      @save-view="saveUserView"
      @remove-filter="removeViewFilter"
    >
      <template #header-actions>
        <el-button
          v-if="hasPermission('system:user:create')"
          type="primary"
          :icon="Plus"
          @click="openCreate"
        >新增用户</el-button>
      </template>

      <template #toolbar>
        <DhicDataToolbar
          :workspace-config="systemUserWorkspaceConfig"
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
          @save-field-config="saveUserView"
        >
          <template #right>
            <el-button :icon="Refresh" @click="loadUsers">刷新</el-button>
            <span class="view-hint">全部已加载 {{ resultScope.loadedRecords }} 人，当前视图 {{ resultScope.visibleRecords }} 人</span>
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
          :data-mode="systemUserWorkspaceConfig.query.dataMode"
          :column-width="columnWidth"
          :column-fixed="columnFixed"
          :is-column-sortable="isColumnSortable"
          :show-overflow-tooltip="column => ['email', 'remark'].includes(column.key)"
          :operation-column="{ width: 250, fixed: 'right', align: 'left' }"
          @column-resize="handleHeaderDragEnd"
          @sort-change="handleTableSortChange"
          @retry="loadUsers"
          aria-label="后台用户管理数据表格"
        >
          <template #operations="{ row }">
            <el-button v-if="hasPermission('system:user:update')" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="hasPermission('system:user:assign-role')" link type="primary" @click="openRoleDialog(row)">角色</el-button>
            <el-button v-if="hasPermission('system:user:disable')" link type="primary" @click="changeStatus(row)">
              {{ row.status === 'DISABLED' ? '启用' : '停用' }}
            </el-button>
            <el-dropdown v-if="hasSecondaryUserAction(row)" trigger="click" @command="command => handleUserCommand(command, row)">
              <el-button link>更多</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-if="hasPermission('system:user:reset-password') && row.webLoginEnabled" command="reset-password">重置后台密码</el-dropdown-item>
                  <el-dropdown-item v-if="hasPermission('system:user:update') && row.mobileBound" command="unbind-mobile">解除移动绑定</el-dropdown-item>
                  <el-dropdown-item v-if="hasPermission('system:user:view')" command="login-records">查看登录记录</el-dropdown-item>
                  <el-dropdown-item v-if="hasPermission('system:user:delete')" command="delete" divided>删除</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </WorkspaceDataGrid>
      </template>
    </DataWorkspace>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑用户' : '新增用户'" width="620px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="姓名" prop="realName"><el-input v-model.trim="form.realName" maxlength="64" /></el-form-item>
        <el-form-item label="手机号" prop="phone"><el-input v-model.trim="form.phone" maxlength="32" /></el-form-item>
        <el-form-item label="主要角色" prop="primaryRoleId">
          <el-select v-model="form.primaryRoleId" filterable style="width: 100%">
            <el-option v-for="role in roles" :key="role.id" :label="`${role.roleName} (${role.code || role.roleCode})`" :value="role.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="后台登录">
          <el-switch v-model="form.webLoginEnabled" />
        </el-form-item>
        <el-form-item label="移动工作台">
          <el-switch v-model="form.mobileEnabled" />
          <span class="form-tip">通过微信验证手机号进入，无需后台账号密码</span>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <template v-if="form.webLoginEnabled">
          <el-form-item label="用户名" prop="username"><el-input v-model.trim="form.username" maxlength="64" /></el-form-item>
          <el-form-item v-if="!editingId" label="初始密码" prop="password"><el-input v-model="form.password" type="password" show-password /></el-form-item>
        </template>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button
          v-if="editingId ? hasPermission('system:user:update') : hasPermission('system:user:create')"
          type="primary"
          :loading="saving"
          @click="submit"
        >保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="roleDialog" title="分配角色" width="520px" destroy-on-close>
      <el-select v-model="roleForm.primaryRoleId" filterable style="width: 100%">
        <el-option v-for="role in roles" :key="role.id" :label="`${role.roleName} (${role.code || role.roleCode})`" :value="role.id" />
      </el-select>
      <template #footer>
        <el-button @click="roleDialog = false">取消</el-button>
        <el-button v-if="hasPermission('system:user:assign-role')" type="primary" @click="saveRoles">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="loginLogDialog" title="后台登录记录" width="720px" destroy-on-close>
      <div v-loading="loginLogsLoading" class="login-log-list">
        <div v-for="record in loginLogs" :key="record.id" class="login-log-row">
          <span>{{ record.loginAt || '-' }}</span>
          <span>{{ record.username || '-' }}</span>
          <span>{{ record.loginResult || '-' }}</span>
          <span>{{ record.clientIp || '-' }}</span>
        </div>
        <el-empty v-if="!loginLogsLoading && loginLogs.length === 0" description="暂无后台登录记录" />
      </div>
      <template #footer><el-button @click="loginLogDialog = false">关闭</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import DataWorkspace from '../../components/DataWorkspace.vue'
import DhicDataToolbar from '../../components/DhicDataToolbar.vue'
import WorkspaceDataGrid from '../../components/workspace/WorkspaceDataGrid.vue'
import { useDhicTableView } from '../../composables/useDhicTableView'
import { useWorkspaceListRequestState } from '../../composables/workspace/useWorkspaceRequestState'
import request from '../../utils/request'
import { readPage } from '../../utils/pagination'
import { hasPermission } from '../../utils/permission'
import { createSystemUserWorkspaceConfig, systemStatusOptions } from '../../workspaceConfigs/systemWorkspaceConfig'
import { workspaceTableOptions } from '../../workspaceConfigs/workspaceConfig'

const loadingRows = ref([])
const roleFieldOptions = reactive([])
const systemUserWorkspaceConfig = createSystemUserWorkspaceConfig({ roleOptions: roleFieldOptions })
const saving = ref(false)
const dialogVisible = ref(false)
const roleDialog = ref(false)
const formRef = ref()
const users = loadingRows
const roles = ref([])
const editingId = ref(null)
const loginLogDialog = ref(false)
const loginLogsLoading = ref(false)
const loginLogs = ref([])

const listState = useWorkspaceListRequestState({
  hasResults: () => users.value.length > 0,
  clearResults: () => { users.value = [] },
  staleMessage: '用户列表刷新失败，当前显示上次成功数据'
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
} = useDhicTableView(workspaceTableOptions(systemUserWorkspaceConfig, {
  records: () => users.value,
  serverTotal: () => users.value.length,
  groupLabel: value => systemStatusOptions.find(option => option.value === value)?.label || value || '未填写'
}))

const userMetricData = computed(() => ({
  totalCount: users.value.length,
  visibleCount: visibleRecords.value.length,
  enabledCount: users.value.filter(item => ['ACTIVE', 'ENABLED'].includes(item.status)).length,
  disabledCount: users.value.filter(item => ['DISABLED', 'INACTIVE'].includes(item.status)).length,
  roleCount: roles.value.length
}))

const form = reactive({
  username: '',
  password: '123456',
  realName: '',
  phone: '',
  status: 'ENABLED',
  webLoginEnabled: true,
  mobileEnabled: false,
  primaryRoleId: null,
  remark: ''
})

const roleForm = reactive({ userId: null, primaryRoleId: null })

const rules = {
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  primaryRoleId: [{ required: true, message: '请选择主要角色', trigger: 'change' }],
  username: [{ validator: (_rule, value, callback) => (
    form.webLoginEnabled && !String(value || '').trim() ? callback(new Error('请输入用户名')) : callback()
  ), trigger: 'blur' }],
  password: [{ validator: (_rule, value, callback) => (
    form.webLoginEnabled && !editingId.value && !String(value || '').trim()
      ? callback(new Error('请输入初始密码'))
      : callback()
  ), trigger: 'blur' }],
  phone: [{ validator: (_rule, value, callback) => (
    form.mobileEnabled && !/^1\d{10}$/.test(String(value || '').replace(/\D/g, ''))
      ? callback(new Error('开启移动工作台时必须填写有效手机号'))
      : callback()
  ), trigger: 'blur' }]
}

async function switchUserView(viewId) {
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

function saveUserView() {
  saveCurrentView()
  ElMessage.success(`视图“${activeViewName.value}”已保存`)
}

async function loadUsers() {
  const requestId = listRequestGuard.start()
  const requestContext = listState.begin({ resource: '/system/users' })
  try {
    const data = await request.get('/system/users', { silent: true })
    if (!listRequestGuard.isCurrent(requestId) || !listState.isCurrent(requestContext)) return
    users.value = readPage(data).records
    listState.succeed(requestContext)
  } catch (error) {
    if (!listRequestGuard.isCurrent(requestId) || !listState.isCurrent(requestContext)) return
    listState.fail(requestContext, error?.response?.data?.message || '用户列表加载失败，请稍后重试')
  }
}

async function loadRoles() {
  const data = await request.get('/system/roles', { params: { size: 500 } })
  roles.value = readPage(data).records
  roleFieldOptions.splice(0, roleFieldOptions.length, ...roles.value.map(role => ({
    label: role.roleName || role.name || role.code || role.roleCode,
    value: role.roleName || role.name || role.code || role.roleCode
  })))
}

function resetForm() {
  Object.assign(form, {
    username: '', password: '123456', realName: '', phone: '', status: 'ENABLED',
    webLoginEnabled: true, mobileEnabled: false, primaryRoleId: roles.value[0]?.id || null, remark: ''
  })
}

function openCreate() {
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  Object.assign(form, {
    ...row,
    password: '',
    primaryRoleId: row.primaryRoleId || row.roleIds?.[0] || null,
    webLoginEnabled: row.webLoginEnabled !== false,
    mobileEnabled: Boolean(row.mobileEnabled)
  })
  dialogVisible.value = true
}

async function submit() {
  await formRef.value?.validate()
  saving.value = true
  try {
    const payload = { ...form }
    payload.roleIds = payload.primaryRoleId ? [payload.primaryRoleId] : []
    if (editingId.value) {
      delete payload.password
      await request.put(`/system/users/${editingId.value}`, payload)
    } else {
      await request.post('/system/users', payload)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await loadUsers()
  } finally {
    saving.value = false
  }
}

function openRoleDialog(row) {
  roleForm.userId = row.id
  roleForm.primaryRoleId = row.primaryRoleId || row.roleIds?.[0] || null
  roleDialog.value = true
}

async function saveRoles() {
  if (!roleForm.primaryRoleId) {
    ElMessage.warning('请选择主要角色')
    return
  }
  await request.post(`/system/users/${roleForm.userId}/roles`, {
    primaryRoleId: roleForm.primaryRoleId,
    ids: [roleForm.primaryRoleId]
  })
  ElMessage.success('角色已更新')
  roleDialog.value = false
  await loadUsers()
}

async function changeStatus(row) {
  const next = row.status === 'DISABLED' ? 'ENABLED' : 'DISABLED'
  await request.patch(`/system/users/${row.id}/status`, null, { params: { status: next } })
  ElMessage.success('状态已更新')
  await loadUsers()
}

async function resetPassword(row) {
  const data = await request.patch(`/system/users/${row.id}/reset-password`)
  await ElMessageBox.alert(`新密码：${data.password}`, '密码已重置', { confirmButtonText: '知道了' })
}

async function deleteUser(row) {
  await ElMessageBox.confirm(`确定删除用户「${row.realName || row.username}」吗？`, '删除确认', { type: 'warning' })
  await request.delete(`/system/users/${row.id}`)
  ElMessage.success('删除成功')
  await loadUsers()
}

async function unbindMobile(row) {
  await ElMessageBox.confirm(`确定解除「${row.realName || row.username}」的微信移动绑定吗？解除后需重新验证手机号。`, '解除绑定', { type: 'warning' })
  await request.delete(`/system/users/${row.id}/mobile-binding`)
  ElMessage.success('移动绑定已解除')
  await loadUsers()
}

async function showLoginRecords(row) {
  loginLogDialog.value = true
  loginLogsLoading.value = true
  loginLogs.value = []
  try {
    const data = await request.get('/logs/logins', { params: { userId: row.id, size: 50 }, silent: true })
    loginLogs.value = readPage(data).records
  } finally {
    loginLogsLoading.value = false
  }
}

function hasSecondaryUserAction(row) {
  return (row.webLoginEnabled && hasPermission('system:user:reset-password'))
    || (row.mobileBound && hasPermission('system:user:update'))
    || hasPermission('system:user:view')
    || hasPermission('system:user:delete')
}

function handleUserCommand(command, row) {
  if (command === 'reset-password') return resetPassword(row)
  if (command === 'unbind-mobile') return unbindMobile(row)
  if (command === 'login-records') return showLoginRecords(row)
  if (command === 'delete') return deleteUser(row)
}

onMounted(async () => {
  await loadRoles()
  await loadUsers()
})
onBeforeUnmount(disposeQueryLifecycle)
</script>

<style scoped>
.page-container { min-width: 0; }
.view-hint { color: var(--dhic-text-muted); font-size: 12px; white-space: nowrap; }
.login-log-list { min-height: 160px; overflow-x: auto; }
.login-log-row {
  display: grid;
  grid-template-columns: 180px minmax(160px, 1fr) 150px 140px;
  min-width: 650px;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid var(--dhic-border-light, #f0f2f5);
  font-size: 13px;
}
</style>
