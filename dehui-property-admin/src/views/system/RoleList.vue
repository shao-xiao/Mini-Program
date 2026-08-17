<template>
  <div class="page-container">
    <DataWorkspace
      :workspace-config="systemRoleWorkspaceConfig"
      :views="workspaceViews"
      :active-view-id="activeViewId"
      :view-dirty="isViewDirty"
      :filter-logic="tablePrefs.filterLogic"
      :filter-logic-visible="tablePrefs.filters.length > 1"
      :filter-items="activeFilterItems"
      :query-notices="queryNotices"
      :metric-data="roleMetricData"
      @change-view="switchRoleView"
      @create-view="createTableView"
      @duplicate-view="duplicateTableView"
      @rename-view="renameTableView"
      @delete-view="deleteTableView"
      @save-view="saveRoleView"
      @remove-filter="removeViewFilter"
    >
      <template #header-actions>
        <el-button v-if="hasPermission('system:role:create')" type="primary" :icon="Plus" @click="openCreate">新增角色</el-button>
      </template>

      <template #toolbar>
        <DhicDataToolbar
          :workspace-config="systemRoleWorkspaceConfig"
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
          @save-field-config="saveRoleView"
        >
          <template #right>
            <el-button :icon="Refresh" @click="loadRoles">刷新</el-button>
            <span class="view-hint">全部已加载 {{ resultScope.loadedRecords }} 个角色，当前视图 {{ resultScope.visibleRecords }} 个</span>
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
          :data-mode="systemRoleWorkspaceConfig.query.dataMode"
          :column-width="columnWidth"
          :column-fixed="columnFixed"
          :is-column-sortable="isColumnSortable"
          :show-overflow-tooltip="column => column.key === 'description'"
          :operation-column="{ width: 250, fixed: 'right', align: 'left' }"
          @column-resize="handleHeaderDragEnd"
          @sort-change="handleTableSortChange"
          @retry="loadRoles"
          aria-label="角色管理数据表格"
        >
          <template #operations="{ row }">
            <el-button v-if="hasPermission('system:role:assign-permission')" link type="primary" @click="openPermissionDialog(row)">权限配置</el-button>
            <el-button v-if="hasPermission('system:role:update')" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-dropdown v-if="hasSecondaryRoleAction(row)" trigger="click" @command="command => handleRoleCommand(command, row)">
              <el-button link>更多</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-if="hasPermission('system:role:disable') && !isProtectedSystemRole(row)" command="status">
                    {{ row.status === 'DISABLED' ? '启用' : '禁用' }}
                  </el-dropdown-item>
                  <el-dropdown-item v-if="hasPermission('system:role:delete') && !isProtectedSystemRole(row)" command="delete" divided>删除</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </WorkspaceDataGrid>
      </template>
    </DataWorkspace>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑角色' : '新增角色'" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="角色编码" prop="roleCode"><el-input v-model="form.roleCode" :disabled="!!editingId" /></el-form-item>
        <el-form-item label="角色名称" prop="roleName"><el-input v-model="form.roleName" /></el-form-item>
        <el-form-item label="角色说明"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
        <el-form-item v-if="!isProtectedSystemRole(form)" label="状态"><el-select v-model="form.status" style="width: 100%"><el-option label="启用" value="ENABLED" /><el-option label="禁用" value="DISABLED" /></el-select></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button
          v-if="editingId ? hasPermission('system:role:update') : hasPermission('system:role:create')"
          type="primary"
          :loading="saving"
          @click="submit"
        >保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="permissionDialog" title="权限配置" width="860px" destroy-on-close class="permission-dialog" @closed="resetPermissionDialogState">
      <div class="permission-toolbar">
        <el-input
          v-model="permissionKeyword"
          class="permission-search"
          clearable
          placeholder="搜索业务模块"
        />
        <el-checkbox v-model="showSelectedOnly" class="permission-selected-only">
          仅看已选（{{ configuredModuleCount }}）
        </el-checkbox>
        <div class="permission-toolbar-actions">
          <el-button size="small" @click="expandAllModuleSections">展开全部</el-button>
          <el-button size="small" @click="collapseAllModuleSections">收起全部</el-button>
          <el-button size="small" type="primary" :disabled="!canApplyCurrentTemplate" @click="applyDefaultTemplate">应用默认模板</el-button>
        </div>
      </div>

      <div class="permission-role-hint">
        <span>当前角色：{{ currentRole?.roleName || currentRole?.name || '-' }}</span>
        <span class="permission-persisted-count">
          {{ permissionAccessLoading ? '正在读取已保存权限' : `已配置 ${configuredModuleCount} 个模块` }}
        </span>
        <span v-if="canApplyCurrentTemplate">可应用模板：{{ currentRoleTemplateName }}</span>
        <span v-else>该角色未纳入默认模板，可手动配置。</span>
      </div>

      <el-alert
        v-if="permissionLoadError"
        :title="permissionLoadError"
        type="error"
        :closable="false"
        show-icon
      />
      <el-alert
        v-if="missingPermissionMessage"
        :title="missingPermissionMessage"
        type="error"
        :closable="false"
        show-icon
      />

      <div v-loading="permissionAccessLoading" class="module-permission-body">
        <el-empty v-if="filteredModuleSections.length === 0" description="没有匹配的业务模块" />
        <section
          v-for="section in filteredModuleSections"
          :key="section.key"
          class="module-permission-section"
          :class="{ 'is-mobile-workbench': section.accessModule }"
        >
          <header class="module-section-header">
            <button
              class="module-section-toggle"
              type="button"
              :aria-label="`${isModuleSectionExpanded(section.key) ? '收起' : '展开'}${section.title}`"
              @click="toggleModuleSection(section.key)"
            >
              <span class="permission-expand-icon" aria-hidden="true">
                {{ isModuleSectionExpanded(section.key) ? '▾' : '▸' }}
              </span>
              <span class="module-section-heading">
                <span class="module-section-title-line">
                  <strong>{{ section.title }}</strong>
                  <el-tag
                    v-if="section.accessModule"
                    size="small"
                    type="danger"
                    effect="plain"
                  >移动端入口</el-tag>
                </span>
                <span class="module-section-summary">{{ moduleSectionSummary(section) }}</span>
              </span>
            </button>
            <div class="module-section-control-bar">
              <el-checkbox
                v-if="section.accessModule"
                class="mobile-workbench-access"
                :model-value="modulePermissionState(section.accessModule).read"
                :indeterminate="modulePermissionState(section.accessModule).readPartial"
                :aria-label="section.accessModule.title"
                @change="checked => toggleWorkbenchAccess(section, checked)"
              >启用物业端</el-checkbox>
              <div v-if="section.bulkAccess" class="module-section-actions">
                <el-checkbox
                  :model-value="moduleSectionChecked(section, 'read')"
                  :indeterminate="moduleSectionIndeterminate(section, 'read')"
                  :disabled="section.accessModule && !modulePermissionState(section.accessModule).read"
                  :aria-label="`${section.title}全部读取`"
                  @change="checked => toggleModuleSectionAccess(section, 'read', checked)"
                >全读</el-checkbox>
                <el-checkbox
                  v-if="moduleSectionHasWrite(section)"
                  :model-value="moduleSectionChecked(section, 'write')"
                  :indeterminate="moduleSectionIndeterminate(section, 'write')"
                  :disabled="section.accessModule && !modulePermissionState(section.accessModule).read"
                  :aria-label="`${section.title}全部写入`"
                  @change="checked => toggleModuleSectionAccess(section, 'write', checked)"
                >全写</el-checkbox>
              </div>
            </div>
          </header>

          <div v-show="isModuleSectionExpanded(section.key)" class="module-permission-list">
            <div v-if="section.accessModule" class="mobile-identity-note">
              <span class="mobile-identity-note__title">身份规则</span>
              <span>
                勾选仅开启物业员工入口；租户身份来自小程序用户绑定的租户联系人。若同一微信手机号同时匹配物业员工和租户联系人，登录时分别选择物业或租户身份，一次会话只启用一个身份，权限和数据范围不会合并。
              </span>
            </div>
            <div v-for="module in section.modules" :key="module.key" class="module-permission-row">
              <div class="module-permission-name">
                <span>{{ module.title }}</span>
                <span v-if="module.mobileModule" class="mobile-module-hint">移动端：{{ module.mobileModule }}</span>
                <el-tag
                  v-if="moduleHasPartialAccess(module)"
                  size="small"
                  type="warning"
                  effect="plain"
                >历史部分权限</el-tag>
              </div>
              <div class="module-permission-controls">
                <el-checkbox
                  :model-value="modulePermissionState(module).read"
                  :indeterminate="modulePermissionState(module).readPartial"
                  :disabled="section.accessModule && !modulePermissionState(section.accessModule).read"
                  :aria-label="`${module.title}读取权限`"
                  @change="checked => toggleModuleAccess(module, 'read', checked)"
                >{{ module.readLabel || '读' }}</el-checkbox>
                <el-checkbox
                  v-if="module.writeCodes.length"
                  :model-value="modulePermissionState(module).write"
                  :indeterminate="modulePermissionState(module).writePartial"
                  :disabled="section.accessModule && !modulePermissionState(section.accessModule).read"
                  :aria-label="`${module.title}写入权限`"
                  @change="checked => toggleModuleAccess(module, 'write', checked)"
                >{{ module.writeLabel || '写' }}</el-checkbox>
                <span v-else class="read-only-label">只读模块</span>
              </div>
            </div>
          </div>
        </section>
      </div>
      <div class="permission-dialog-note">
        读权限控制菜单、路由和查询；写权限包含该模块现有新增、编辑、删除及业务操作。未触碰的历史部分权限不会自动扩张。
      </div>
      <template #footer>
        <el-button @click="permissionDialog = false">取消</el-button>
        <el-button
          v-if="hasPermission('system:role:assign-permission')"
          type="primary"
          :loading="permissionSaving"
          :disabled="permissionAccessLoading || loadedPermissionRoleId !== currentRoleId || Boolean(missingPermissionMessage)"
          @click="savePermissions"
        >保存</el-button>
      </template>
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
import {
  getModulePermissionState,
  modulePermissionBundles,
  modulePermissionSections
} from '../../config/modulePermissionBundles'
import {
  buildRoleMenuSelection,
  findMissingModulePermissionCodes,
  menuCodesFromRoleAccess,
  nextModuleAccessChange,
  permissionCodesFromRoleAccess,
  permissionIdsForCodes,
  resolveModulePermissionCodes
} from '../../utils/modulePermissionResolver'
import {
  buildRoleTemplateChanges,
  isMatrixRoleCode,
  roleTemplateLabel
} from '../../utils/roleAccessTemplates'
import { isProtectedSystemRole } from '../../utils/systemRoleProtection'
import { createSystemRoleWorkspaceConfig, systemStatusOptions } from '../../workspaceConfigs/systemWorkspaceConfig'
import { workspaceTableOptions } from '../../workspaceConfigs/workspaceConfig'

const systemRoleWorkspaceConfig = createSystemRoleWorkspaceConfig()
const saving = ref(false)
const roles = ref([])
const menus = ref([])
const permissions = ref([])
const dialogVisible = ref(false)
const permissionDialog = ref(false)
const editingId = ref(null)
const currentRoleId = ref(null)
const currentRole = ref(null)
const formRef = ref()
const originalPermissionCodes = ref([])
const originalMenuCodes = ref([])
const moduleAccessChanges = ref({})
const loadedPermissionRoleId = ref(null)
const permissionAccessLoading = ref(false)
const permissionLoadError = ref('')
const permissionSaving = ref(false)
let permissionAccessRequestId = 0
const permissionKeyword = ref('')
const showSelectedOnly = ref(false)
const expandedModuleSectionKeys = ref(new Set())
const currentRoleCode = computed(() => currentRole.value?.roleCode || currentRole.value?.code || '')
const currentRoleTemplateName = computed(() => roleTemplateLabel(currentRoleCode.value))
const canApplyCurrentTemplate = computed(() => isMatrixRoleCode(currentRoleCode.value))

function permissionCatalogCode(item = {}) {
  return String(item.permissionCode || item.code || '').trim()
}

function permissionCatalogItemEnabled(item = {}) {
  return String(item.status || 'ENABLED').toUpperCase() !== 'DISABLED'
}

const availablePermissionCodes = computed(() => permissions.value
  .filter(permissionCatalogItemEnabled)
  .map(permissionCatalogCode)
  .filter(Boolean))
const resolvedPermissionCodes = computed(() => resolveModulePermissionCodes({
  originalCodes: originalPermissionCodes.value,
  changes: moduleAccessChanges.value,
  availableCodes: availablePermissionCodes.value
}))
const configuredModuleCount = computed(() => modulePermissionBundles
  .filter(module => {
    const state = getModulePermissionState(module, resolvedPermissionCodes.value)
    return state.read || state.write || state.readPartial || state.writePartial
  }).length)
const missingModulePermissionCodes = computed(() => findMissingModulePermissionCodes(permissions.value))
const missingPermissionMessage = computed(() => {
  const missing = missingModulePermissionCodes.value.flatMap(item => item.missingCodes)
  if (!missing.length) return ''
  return `权限目录缺少 ${missing.length} 项当前模块能力，暂不能保存。请先完成权限目录升级。`
})
const permissionDialogSections = [
  ...modulePermissionSections.filter(section => section.accessModule),
  ...modulePermissionSections.filter(section => !section.accessModule)
]
const filteredModuleSections = computed(() => {
  const keyword = permissionKeyword.value.trim().toLowerCase()
  return permissionDialogSections.flatMap(section => {
    const sectionMatched = !keyword || section.title.toLowerCase().includes(keyword)
    const accessState = section.accessModule ? modulePermissionState(section.accessModule) : null
    const accessSelected = Boolean(accessState && (
      accessState.read || accessState.write || accessState.readPartial || accessState.writePartial
    ))
    const modules = section.modules.filter(module => {
      const state = modulePermissionState(module)
      const keywordMatched = sectionMatched || module.title.toLowerCase().includes(keyword)
      const selectedMatched = !showSelectedOnly.value || state.read || state.write || state.readPartial || state.writePartial
      return keywordMatched && selectedMatched
    })
    const accessMatched = section.accessModule
      && (!keyword || sectionMatched || section.accessModule.title.toLowerCase().includes(keyword))
      && (!showSelectedOnly.value || accessSelected)
    return modules.length || accessMatched ? [{ ...section, modules }] : []
  })
})

const listState = useWorkspaceListRequestState({
  hasResults: () => roles.value.length > 0,
  clearResults: () => { roles.value = [] },
  staleMessage: '角色列表刷新失败，当前显示上次成功数据'
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
} = useDhicTableView(workspaceTableOptions(systemRoleWorkspaceConfig, {
  records: () => roles.value,
  serverTotal: () => roles.value.length,
  groupLabel: value => systemStatusOptions.find(option => option.value === value)?.label || value || '未填写'
}))

const roleMetricData = computed(() => ({
  totalCount: roles.value.length,
  visibleCount: visibleRecords.value.length,
  enabledCount: roles.value.filter(item => ['ACTIVE', 'ENABLED'].includes(item.status)).length,
  disabledCount: roles.value.filter(item => ['DISABLED', 'INACTIVE'].includes(item.status)).length,
  permissionCount: permissions.value.length
}))

const form = reactive({ roleCode: '', roleName: '', roleType: 'BUSINESS', description: '', status: 'ENABLED' })
const rules = {
  roleCode: [{ required: true, message: '请输入角色编码', trigger: 'blur' }],
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }]
}

function modulePermissionState(module) {
  return getModulePermissionState(module, resolvedPermissionCodes.value)
}

function moduleHasPartialAccess(module) {
  const state = modulePermissionState(module)
  return state.readPartial || state.writePartial
}

function moduleSectionHasWrite(section) {
  return section.modules.some(module => module.writeCodes.length > 0)
}

function moduleSectionAccessStates(section, accessType) {
  return section.modules
    .filter(module => accessType === 'read' || module.writeCodes.length > 0)
    .map(module => {
      const state = modulePermissionState(module)
      return {
        checked: Boolean(state[accessType]),
        partial: Boolean(state[`${accessType}Partial`])
      }
    })
}

function moduleSectionChecked(section, accessType) {
  const states = moduleSectionAccessStates(section, accessType)
  return states.length > 0 && states.every(state => state.checked)
}

function moduleSectionIndeterminate(section, accessType) {
  const states = moduleSectionAccessStates(section, accessType)
  const selected = states.filter(state => state.checked || state.partial).length
  return selected > 0 && !states.every(state => state.checked)
}

function moduleSectionSummary(section) {
  if (!section.bulkAccess) {
    return section.modules.map(module => {
      const state = modulePermissionState(module)
      const readLabel = module.readLabel || '基础授权'
      const writeLabel = module.writeLabel || '高级授权'
      return `${readLabel} ${state.read ? '已授权' : '未授权'} · ${writeLabel} ${state.write ? '已授权' : '未授权'}`
    }).join('；')
  }
  const readCount = section.modules.filter(module => modulePermissionState(module).read).length
  const writable = section.modules.filter(module => module.writeCodes.length > 0)
  const writeCount = writable.filter(module => modulePermissionState(module).write).length
  const moduleSummary = writable.length
    ? `读 ${readCount}/${section.modules.length} · 写 ${writeCount}/${writable.length}`
    : `读 ${readCount}/${section.modules.length}`
  if (!section.accessModule) return moduleSummary
  return `${modulePermissionState(section.accessModule).read ? '已启用' : '未启用'} · ${moduleSummary}`
}

function setModuleAccess(module, accessType, checked) {
  const current = moduleAccessChanges.value[module.key] || {}
  moduleAccessChanges.value = {
    ...moduleAccessChanges.value,
    [module.key]: nextModuleAccessChange(current, accessType, checked)
  }
}

function toggleModuleAccess(module, accessType, checked) {
  const section = modulePermissionSections.find(item => item.key === module.sectionKey)
  if (checked && section?.accessModule) {
    setModuleAccess(section.accessModule, 'read', true)
  }
  setModuleAccess(module, accessType, checked)
}

function toggleWorkbenchAccess(section, checked) {
  setModuleAccess(section.accessModule, 'read', checked)
  if (!checked) {
    section.modules.forEach(module => {
      setModuleAccess(module, 'read', false)
      if (module.writeCodes.length) setModuleAccess(module, 'write', false)
    })
  }
}

function toggleModuleSectionAccess(section, accessType, checked) {
  if (checked && section.accessModule) {
    setModuleAccess(section.accessModule, 'read', true)
  }
  section.modules
    .filter(module => accessType === 'read' || module.writeCodes.length > 0)
    .forEach(module => setModuleAccess(module, accessType, checked))
}

function expandAllModuleSections() {
  expandedModuleSectionKeys.value = new Set(modulePermissionSections.map(section => section.key))
}

function collapseAllModuleSections() {
  expandedModuleSectionKeys.value = new Set()
}

function isModuleSectionExpanded(key) {
  return Boolean(permissionKeyword.value.trim()) || expandedModuleSectionKeys.value.has(key)
}

function expandPriorityModuleSection() {
  const prioritySection = permissionDialogSections.find(section => section.accessModule)
    || permissionDialogSections[0]
  expandedModuleSectionKeys.value = new Set(prioritySection ? [prioritySection.key] : [])
}

function toggleModuleSection(key) {
  const next = new Set(expandedModuleSectionKeys.value)
  if (next.has(key)) next.delete(key)
  else next.add(key)
  expandedModuleSectionKeys.value = next
}

async function switchRoleView(viewId) {
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

function saveRoleView() {
  saveCurrentView()
  ElMessage.success(`视图“${activeViewName.value}”已保存`)
}

async function loadRoles() {
  const requestId = listRequestGuard.start()
  const requestContext = listState.begin({ resource: '/system/roles' })
  try {
    const data = await request.get('/system/roles', { silent: true })
    if (!listRequestGuard.isCurrent(requestId) || !listState.isCurrent(requestContext)) return
    roles.value = readPage(data).records
    listState.succeed(requestContext)
  } catch (error) {
    if (!listRequestGuard.isCurrent(requestId) || !listState.isCurrent(requestContext)) return
    listState.fail(requestContext, error?.response?.data?.message || '角色列表加载失败，请稍后重试')
  }
}

async function loadMeta() {
  const [menuData, permissionData] = await Promise.all([
    request.get('/system/menus'),
    request.get('/system/permissions')
  ])
  menus.value = menuData || []
  permissions.value = permissionData || []
}

function resetForm() {
  Object.assign(form, { roleCode: '', roleName: '', roleType: 'BUSINESS', description: '', status: 'ENABLED' })
}

function openCreate() {
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  Object.assign(form, row)
  dialogVisible.value = true
}

async function submit() {
  await formRef.value?.validate()
  saving.value = true
  const payload = {
    roleCode: form.roleCode,
    roleName: form.roleName,
    description: form.description,
    status: form.status,
    roleType: form.roleType || 'BUSINESS'
  }
  try {
    if (editingId.value) {
      await request.put(`/system/roles/${editingId.value}`, payload)
    } else {
      await request.post('/system/roles', payload)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await loadRoles()
  } finally {
    saving.value = false
  }
}

async function openPermissionDialog(row) {
  const requestId = ++permissionAccessRequestId
  currentRoleId.value = row.id
  currentRole.value = row
  originalPermissionCodes.value = []
  originalMenuCodes.value = []
  moduleAccessChanges.value = {}
  loadedPermissionRoleId.value = null
  permissionLoadError.value = ''
  permissionKeyword.value = ''
  showSelectedOnly.value = false
  permissionDialog.value = true
  permissionAccessLoading.value = true
  try {
    const access = await request.get(`/system/roles/${row.id}/access`, { silent: true })
    if (requestId !== permissionAccessRequestId || currentRoleId.value !== row.id) return
    originalPermissionCodes.value = permissionCodesFromRoleAccess(permissions.value, access)
    originalMenuCodes.value = menuCodesFromRoleAccess(menus.value, access)
    loadedPermissionRoleId.value = row.id
    expandPriorityModuleSection()
  } catch (error) {
    if (requestId !== permissionAccessRequestId || currentRoleId.value !== row.id) return
    permissionLoadError.value = error?.response?.data?.message || '已保存权限读取失败，请刷新后重试'
  } finally {
    if (requestId === permissionAccessRequestId) permissionAccessLoading.value = false
  }
}

async function savePermissions() {
  if (loadedPermissionRoleId.value !== currentRoleId.value || permissionAccessLoading.value) {
    ElMessage.error('权限尚未成功读取，不能覆盖数据库配置')
    return
  }
  if (missingPermissionMessage.value) {
    ElMessage.error(missingPermissionMessage.value)
    return
  }
  const permissionCodes = resolvedPermissionCodes.value
  const permissionIds = permissionIdsForCodes(permissions.value, permissionCodes)
  const { menuIds, menuCodes } = buildRoleMenuSelection({
    menus: menus.value,
    permissionCodes,
    originalMenuCodes: originalMenuCodes.value
  })
  permissionSaving.value = true
  try {
    await request.post(`/system/roles/${currentRoleId.value}/access`, {
      menuIds,
      menuCodes,
      permissionIds,
      permissionCodes
    })
    const persistedAccess = await request.get(`/system/roles/${currentRoleId.value}/access`, { silent: true })
    const persistedPermissionCodes = permissionCodesFromRoleAccess(permissions.value, persistedAccess)
    const persisted = new Set(persistedPermissionCodes)
    if (permissionCodes.some(code => !persisted.has(code))) {
      throw new Error('权限保存后校验失败')
    }
    originalPermissionCodes.value = persistedPermissionCodes
    originalMenuCodes.value = menuCodesFromRoleAccess(menus.value, persistedAccess)
    moduleAccessChanges.value = {}
    ElMessage.success(`模块权限已保存，共配置 ${configuredModuleCount.value} 个模块`)
    permissionDialog.value = false
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || '权限保存失败，请重试')
  } finally {
    permissionSaving.value = false
  }
}

function resetPermissionDialogState() {
  permissionAccessRequestId += 1
  currentRoleId.value = null
  currentRole.value = null
  loadedPermissionRoleId.value = null
  originalPermissionCodes.value = []
  originalMenuCodes.value = []
  moduleAccessChanges.value = {}
  permissionLoadError.value = ''
  permissionAccessLoading.value = false
  permissionKeyword.value = ''
  showSelectedOnly.value = false
  expandedModuleSectionKeys.value = new Set()
}

async function applyDefaultTemplate() {
  if (!canApplyCurrentTemplate.value) return
  const changes = buildRoleTemplateChanges(currentRoleCode.value)
  const selections = Object.values(changes)
  const writeCount = selections.filter(item => item.write).length
  const readCount = selections.filter(item => item.read && !item.write).length
  await ElMessageBox.confirm(
    `确认应用“${currentRoleTemplateName.value}”默认权限模板？将配置 ${writeCount} 个可写模块和 ${readCount} 个只读模块，保存后才会写入数据库。`,
    '应用默认模板',
    { type: 'warning' }
  )
  moduleAccessChanges.value = changes
  expandAllModuleSections()
  ElMessage.success('默认模板已应用，请确认后保存')
}

async function changeStatus(row) {
  const next = row.status === 'DISABLED' ? 'ENABLED' : 'DISABLED'
  await request.patch(`/system/roles/${row.id}/status`, null, { params: { status: next } })
  ElMessage.success('状态已更新')
  await loadRoles()
}

async function deleteRole(row) {
  await ElMessageBox.confirm(`确定删除角色「${row.roleName}」吗？`, '删除确认', { type: 'warning' })
  await request.delete(`/system/roles/${row.id}`)
  ElMessage.success('删除成功')
  await loadRoles()
}

function hasSecondaryRoleAction(row) {
  return !isProtectedSystemRole(row)
    && (hasPermission('system:role:disable') || hasPermission('system:role:delete'))
}

function handleRoleCommand(command, row) {
  if (command === 'status') return changeStatus(row)
  if (command === 'delete') return deleteRole(row)
}

onMounted(async () => {
  await loadMeta()
  await loadRoles()
})
onBeforeUnmount(disposeQueryLifecycle)
</script>

<style scoped>
.page-container { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; }
.page-title { font-size: 18px; font-weight: 600; }
.page-subtitle { margin-top: 6px; color: #909399; font-size: 13px; }
.filter-form { margin-bottom: 14px; }
.view-hint { color: var(--dhic-text-muted); font-size: 12px; white-space: nowrap; }
.permission-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid #ebeef5;
}
.permission-search { max-width: 420px; }
.permission-toolbar-actions { display: flex; gap: 8px; flex-shrink: 0; }
.permission-role-hint {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 10px 0;
  color: #606266;
  font-size: 13px;
  border-bottom: 1px solid #ebeef5;
}
.permission-persisted-count {
  color: var(--dhic-primary, #d92d20);
  font-weight: 600;
}
.module-permission-body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 8px;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 8px 6px 0 0;
}
.module-permission-section {
  width: 100%;
  flex: 0 0 auto;
  padding: 0;
}
.module-permission-section.is-mobile-workbench {
  border-color: rgba(217, 45, 32, 0.35);
  box-shadow: 0 0 0 1px rgba(217, 45, 32, 0.04);
}
.module-permission-section.is-mobile-workbench .module-section-header {
  background: #fff7f6;
}
.module-section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 52px;
  color: var(--dhic-text-primary, #303133);
}
.module-section-toggle {
  display: flex;
  flex: 1;
  align-items: center;
  min-width: 0;
  padding: 0;
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
  text-align: left;
}
.permission-expand-icon {
  width: 22px;
  flex: 0 0 22px;
  color: var(--dhic-text-secondary, #606266);
  font-size: 13px;
  text-align: center;
}
.module-section-heading {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}
.module-section-title-line {
  display: flex;
  align-items: center;
  gap: 8px;
}
.module-section-summary {
  color: var(--dhic-text-muted, #909399);
  font-size: 12px;
}
.module-section-control-bar {
  display: flex;
  align-items: center;
  gap: 18px;
  flex: 0 0 auto;
}
.module-section-actions {
  display: grid;
  grid-template-columns: 54px 54px;
  gap: 12px;
  flex: 0 0 120px;
}
.mobile-workbench-access {
  font-weight: 600;
}
.module-permission-list {
  margin-left: 0;
  border-top: 1px solid var(--dhic-border-light, #ebeef5);
  border-left: 0;
}
.mobile-identity-note {
  display: flex;
  gap: 10px;
  padding: 10px 14px;
  border-bottom: 1px solid #f5d9d6;
  background: #fffafa;
  color: var(--dhic-text-secondary, #606266);
  font-size: 12px;
  line-height: 1.6;
}
.mobile-identity-note__title {
  flex: 0 0 auto;
  color: var(--dhic-primary, #d92d20);
  font-weight: 600;
}
.module-permission-row {
  display: flex;
  align-items: center;
  min-height: 42px;
  padding: 6px 8px 6px 14px;
  border-top: 1px solid var(--dhic-border-light, #f5f7fa);
}
.module-permission-row:first-child { border-top: 0; }
.module-permission-name {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 8px;
  color: var(--dhic-text-primary, #303133);
  font-size: 13px;
}
.mobile-module-hint {
  color: var(--dhic-text-muted, #909399);
  font-size: 12px;
}
.module-permission-controls {
  display: grid;
  grid-template-columns: 58px 66px;
  gap: 8px;
  margin-left: auto;
  flex: 0 0 132px;
}
.read-only-label {
  color: var(--dhic-text-muted, #909399);
  font-size: 12px;
  line-height: 32px;
}
.permission-dialog-note {
  margin-top: 10px;
  color: var(--dhic-text-muted, #909399);
  font-size: 12px;
  line-height: 1.5;
}
:deep(.permission-dialog) {
  display: flex;
  flex-direction: column;
  height: min(760px, calc(100vh - 32px));
  max-width: calc(100vw - 32px);
  margin-top: 16px;
  margin-bottom: 16px;
}
:deep(.permission-dialog .el-dialog__body) {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}
@media (max-width: 1024px) {
  .permission-toolbar,
  .permission-role-hint { flex-wrap: wrap; }
  .permission-search { max-width: none; flex: 1 1 260px; }
  .module-section-header {
    align-items: flex-start;
    flex-direction: column;
    gap: 8px;
  }
  .module-section-toggle { width: 100%; }
  .module-section-control-bar {
    width: 100%;
    justify-content: flex-end;
    padding-left: 22px;
  }
}
</style>
