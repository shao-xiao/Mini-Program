<template>
  <div class="page-container">
    <DataWorkspace
      :workspace-config="buildingWorkspaceConfig"
      :views="workspaceViews"
      :active-view-id="activeViewId"
      :view-dirty="isViewDirty"
      :filter-logic="tablePrefs.filterLogic"
      :filter-logic-visible="tablePrefs.filters.length > 1"
      :filter-items="activeFilterItems"
      :query-notices="queryNotices"
      @change-view="switchBuildingView"
      @create-view="createTableView"
      @duplicate-view="duplicateTableView"
      @rename-view="renameTableView"
      @delete-view="deleteTableView"
      @save-view="saveBuildingView"
      @remove-filter="removeViewFilter"
    >
      <template #header-actions>
        <el-button
          v-if="hasPermission(buildingPermissions.create)"
          type="primary"
          :icon="Plus"
          @click="openCreateDialog"
        >
          {{ buildingWorkspaceConfig.primaryAction.label }}
        </el-button>
      </template>

      <template #toolbar>
        <DhicDataToolbar
          :workspace-config="buildingWorkspaceConfig"
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
          @save-field-config="saveBuildingView"
        >
          <template #right>
            <span class="view-hint">
              全部已加载 {{ resultScope.loadedRecords }} 条，当前视图 {{ resultScope.visibleRecords }} 条
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
          :data-mode="buildingWorkspaceConfig.query.dataMode"
          :column-width="columnWidth"
          :column-fixed="columnFixed"
          :is-column-sortable="isColumnSortable"
          :show-overflow-tooltip="column => ['address', 'description'].includes(column.key)"
          :operation-column="{ width: 156, fixed: 'right', align: 'left' }"
          @column-resize="handleHeaderDragEnd"
          @sort-change="handleTableSortChange"
          @retry="load"
          aria-label="楼宇管理数据表格"
        >
          <template #operations="{ row }">
            <div class="row-actions">
              <el-button
                v-if="hasPermission(buildingPermissions.update)"
                size="small"
                type="primary"
                @click="openEditDialog(row)"
              >
                编辑
              </el-button>
              <el-button
                v-if="hasPermission(buildingPermissions.delete)"
                size="small"
                type="danger"
                @click="deleteBuilding(row)"
              >
                删除
              </el-button>
            </div>
          </template>
        </WorkspaceDataGrid>
      </template>
    </DataWorkspace>

    <el-dialog v-model="visible" :title="isEdit ? '编辑楼宇' : '新增楼宇'" width="min(620px, calc(100vw - 32px))">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="名称" prop="buildingName">
          <el-input v-model="form.buildingName" />
        </el-form-item>

        <el-form-item label="编码">
          <el-input v-model="form.buildingCode" />
        </el-form-item>

        <el-form-item label="地址">
          <el-input v-model="form.address" />
        </el-form-item>

        <el-form-item label="楼层数">
          <el-input-number v-model="form.totalFloors" :min="0" style="width: 100%" />
        </el-form-item>

        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>

        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button
          v-if="isEdit ? hasPermission(buildingPermissions.update) : hasPermission(buildingPermissions.create)"
          type="primary"
          :loading="saving"
          @click="save"
        >
          保存
        </el-button>
      </template>
    </el-dialog>
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
import { hasPermission } from '../../utils/permission'
import { buildingStatusOptions, buildingWorkspaceConfig } from '../../workspaceConfigs/buildingWorkspaceConfig'
import { workspaceTableOptions } from '../../workspaceConfigs/workspaceConfig'

const list = ref([])
const saving = ref(false)
const visible = ref(false)
const isEdit = ref(false)
const editingId = ref(null)
const formRef = ref(null)
const buildingPermissions = buildingWorkspaceConfig.permissions
const listState = useWorkspaceListRequestState({
  hasResults: () => list.value.length > 0,
  clearResults: () => {
    list.value = []
  },
  staleMessage: '楼宇列表刷新失败，当前显示上次成功数据'
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
} = useDhicTableView(workspaceTableOptions(buildingWorkspaceConfig, {
  records: () => list.value,
  serverTotal: () => list.value.length,
  groupLabel: value => buildingStatusOptions.find(option => option.value === value)?.label || value || '未填写'
}))

const defaultForm = () => ({
  buildingName: '',
  buildingCode: '',
  address: '',
  totalFloors: 1,
  description: '',
  status: 'ACTIVE'
})

const form = reactive(defaultForm())
const rules = {
  buildingName: [{ required: true, message: '请输入楼宇名称', trigger: 'blur' }]
}

function resetForm() {
  Object.assign(form, defaultForm())
  editingId.value = null
  isEdit.value = false
  formRef.value?.clearValidate()
}

async function switchBuildingView(viewId) {
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

function saveBuildingView() {
  saveCurrentView()
  ElMessage.success(`视图“${activeViewName.value}”已保存`)
}

async function load() {
  const requestId = listRequestGuard.start()
  const requestContext = listState.begin({ resource: '/buildings' })
  try {
    const data = await request.get('/buildings', { silent: true })
    if (!listRequestGuard.isCurrent(requestId) || !listState.isCurrent(requestContext)) return
    list.value = Array.isArray(data) ? data : (data?.content || [])
    listState.succeed(requestContext)
  } catch (error) {
    if (!listRequestGuard.isCurrent(requestId) || !listState.isCurrent(requestContext)) return
    listState.fail(requestContext, error?.response?.data?.message || '楼宇列表加载失败，请稍后重试')
  }
}

function openCreateDialog() {
  resetForm()
  visible.value = true
}

function openEditDialog(row) {
  resetForm()
  isEdit.value = true
  editingId.value = row.id
  Object.assign(form, {
    buildingName: row.buildingName || '',
    buildingCode: row.buildingCode || '',
    address: row.address || '',
    totalFloors: row.totalFloors ?? 1,
    description: row.description || '',
    status: row.status || 'ACTIVE'
  })
  visible.value = true
}

async function save() {
  await formRef.value.validate()
  saving.value = true
  try {
    const payload = { ...form }
    if (isEdit.value && editingId.value) {
      await request.put(`/buildings/${editingId.value}`, payload)
      ElMessage.success('楼宇更新成功')
    } else {
      await request.post('/buildings', payload)
      ElMessage.success('楼宇新增成功')
    }
    visible.value = false
    resetForm()
    await load()
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function deleteBuilding(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除楼宇「${row.buildingName}」吗？如果该楼宇下已有楼层或房间，后端可能会拒绝删除。`,
      '删除确认',
      { type: 'warning' }
    )
    await request.delete(`/buildings/${row.id}`)
    ElMessage.success('楼宇删除成功')
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error?.response?.data?.message || '删除失败')
    }
  }
}

onMounted(load)
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

.row-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  min-height: 28px;
}
</style>
