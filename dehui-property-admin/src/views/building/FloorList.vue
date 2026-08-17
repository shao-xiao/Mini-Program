<template>
  <div class="page-container">
    <DataWorkspace
      :workspace-config="floorWorkspaceConfig"
      :views="workspaceViews"
      :active-view-id="activeViewId"
      :view-dirty="isViewDirty"
      :filter-logic="tablePrefs.filterLogic"
      :filter-logic-visible="tablePrefs.filters.length > 1"
      :filter-items="activeFilterItems"
      :query-notices="queryNotices"
      @change-view="switchFloorView"
      @create-view="createTableView"
      @duplicate-view="duplicateTableView"
      @rename-view="renameTableView"
      @delete-view="deleteTableView"
      @save-view="saveFloorView"
      @remove-filter="removeViewFilter"
    >
      <template #header-actions>
        <el-button
          v-if="hasPermission(floorPermissions.create)"
          type="primary"
          :icon="Plus"
          :disabled="!currentBuildingId"
          @click="openCreateDialog"
        >
          {{ floorWorkspaceConfig.primaryAction.label }}
        </el-button>
      </template>

      <template #toolbar>
        <DhicDataToolbar
          :workspace-config="floorWorkspaceConfig"
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
          @save-field-config="saveFloorView"
        >
          <template #data-actions>
            <el-button
              v-if="hasPermission(floorPermissions.create)"
              :loading="generating"
              :disabled="!currentBuildingId"
              @click="batchGenerateFloors"
            >
              {{ floorBatchAction.label }}
            </el-button>
          </template>
          <template #right>
            <span class="selector-label">楼宇</span>
            <el-select
              v-model="currentBuildingId"
              class="building-selector"
              filterable
              :loading="buildingLoading"
              placeholder="请选择楼宇"
              aria-label="选择楼宇"
              @change="selectBuilding"
            >
              <el-option
                v-for="item in buildings"
                :key="item.id"
                :label="buildingOptionLabel(item)"
                :value="item.id"
              />
            </el-select>
            <span class="view-hint">{{ currentBuildingLabel }} · 当前视图 {{ resultScope.visibleRecords }} 条</span>
          </template>
        </DhicDataToolbar>
      </template>

      <template #grid>
        <WorkspaceDataGrid
          :loading="floorLoading"
          :error-text="floorErrorText"
          retryable
          :rows="tableRows"
          :columns="visibleOrderedColumns"
          row-key="id"
          :index-width="72"
          :density="tablePrefs.rowDensity"
          :empty-text="currentBuildingId ? tableEmptyText : '请先选择楼宇'"
          :sort-state="activeTableSort"
          :data-mode="floorWorkspaceConfig.query.dataMode"
          :column-width="columnWidth"
          :column-fixed="columnFixed"
          :is-column-sortable="isColumnSortable"
          :show-overflow-tooltip="column => column.key === 'description'"
          :operation-column="{ width: 156, fixed: 'right', align: 'left' }"
          @column-resize="handleHeaderDragEnd"
          @sort-change="handleTableSortChange"
          @retry="loadFloors(currentBuildingId)"
          aria-label="楼层管理数据表格"
        >
          <template #operations="{ row }">
            <div class="row-actions">
              <el-button
                v-if="hasPermission(floorPermissions.update)"
                size="small"
                type="primary"
                @click="openEditDialog(row)"
              >
                编辑
              </el-button>
              <el-button
                v-if="hasPermission(floorPermissions.delete)"
                size="small"
                type="danger"
                @click="deleteFloor(row)"
              >
                删除
              </el-button>
            </div>
          </template>
        </WorkspaceDataGrid>
      </template>
    </DataWorkspace>

    <el-dialog v-model="visible" :title="isEdit ? '编辑楼层' : '新增楼层'" width="min(520px, calc(100vw - 32px))">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="楼层号" prop="floorNumber">
          <el-input-number v-model="form.floorNumber" style="width: 100%" />
        </el-form-item>

        <el-form-item label="楼层名称">
          <el-input v-model="form.floorName" />
        </el-form-item>

        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" style="width: 100%" />
        </el-form-item>

        <el-form-item label="总面积">
          <el-input-number v-model="form.totalArea" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button
          v-if="isEdit ? hasPermission(floorPermissions.update) : hasPermission(floorPermissions.create)"
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
import { floorStatusOptions, floorWorkspaceConfig } from '../../workspaceConfigs/buildingWorkspaceConfig'
import { workspaceAction, workspaceTableOptions } from '../../workspaceConfigs/workspaceConfig'

const buildings = ref([])
const floors = ref([])
const currentBuildingId = ref(null)
const buildingLoading = ref(false)
const saving = ref(false)
const generating = ref(false)
const visible = ref(false)
const isEdit = ref(false)
const editingFloorId = ref(null)
const formRef = ref(null)
const floorPermissions = floorWorkspaceConfig.permissions
const floorBatchAction = workspaceAction(floorWorkspaceConfig, 'batch-generate')
const currentBuilding = computed(() => (
  buildings.value.find(item => Number(item.id) === Number(currentBuildingId.value)) || null
))
const currentBuildingLabel = computed(() => {
  if (!currentBuilding.value) return '未选择楼宇'
  return buildingOptionLabel(currentBuilding.value)
})
const floorListState = useWorkspaceListRequestState({
  hasResults: () => floors.value.length > 0,
  clearResults: () => {
    floors.value = []
  },
  staleMessage: '楼层列表刷新失败，当前显示上次成功数据'
})
const floorLoading = computed(() => floorListState.status.value === 'loading')
const floorErrorText = floorListState.errorText

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
  queryRequestGuard: floorRequestGuard,
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
} = useDhicTableView(workspaceTableOptions(floorWorkspaceConfig, {
  records: () => floors.value,
  serverTotal: () => floors.value.length,
  groupLabel: value => floorStatusOptions.find(option => option.value === value)?.label || value || '未填写'
}))

const defaultForm = () => ({
  floorNumber: 1,
  floorName: '',
  sortOrder: 1,
  totalArea: 0
})
const form = reactive(defaultForm())
const rules = {
  floorNumber: [{ required: true, message: '请输入楼层号', trigger: 'change' }]
}

function buildingOptionLabel(building) {
  const name = building?.buildingName || '未命名楼宇'
  const code = building?.buildingCode || '-'
  return `${name}｜${code}`
}

function resetForm() {
  Object.assign(form, defaultForm())
  isEdit.value = false
  editingFloorId.value = null
  formRef.value?.clearValidate()
}

async function switchFloorView(viewId) {
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

function saveFloorView() {
  saveCurrentView()
  ElMessage.success(`视图“${activeViewName.value}”已保存`)
}

async function loadBuildings() {
  buildingLoading.value = true
  try {
    const data = await request.get('/buildings', { silent: true })
    buildings.value = Array.isArray(data) ? data : (data?.content || [])
  } catch (error) {
    buildings.value = []
    ElMessage.error(error?.response?.data?.message || '楼宇列表加载失败')
  } finally {
    buildingLoading.value = false
  }
}

async function loadFloors(buildingId) {
  if (!buildingId) {
    floors.value = []
    return
  }
  const requestId = floorRequestGuard.start()
  const requestContext = floorListState.begin({ buildingId })
  try {
    const data = await request.get('/floors', {
      params: { building_id: buildingId },
      silent: true
    })
    if (!floorRequestGuard.isCurrent(requestId) || !floorListState.isCurrent(requestContext)) return
    floors.value = Array.isArray(data) ? data : []
    floorListState.succeed(requestContext)
  } catch (error) {
    if (!floorRequestGuard.isCurrent(requestId) || !floorListState.isCurrent(requestContext)) return
    floorListState.fail(requestContext, error?.response?.data?.message || '楼层列表加载失败，请稍后重试')
  }
}

async function selectBuilding(buildingId) {
  currentBuildingId.value = buildingId || null
  await loadFloors(currentBuildingId.value)
}

function openCreateDialog() {
  if (!currentBuildingId.value) {
    ElMessage.warning('请先选择楼宇')
    return
  }
  resetForm()
  visible.value = true
}

function openEditDialog(row) {
  resetForm()
  isEdit.value = true
  editingFloorId.value = row.id
  Object.assign(form, {
    floorNumber: row.floorNumber ?? 1,
    floorName: row.floorName || '',
    sortOrder: row.sortOrder ?? row.floorNumber ?? 1,
    totalArea: row.totalArea ?? 0
  })
  visible.value = true
}

async function save() {
  if (!currentBuildingId.value) {
    ElMessage.warning('请先选择楼宇')
    return
  }
  await formRef.value.validate()
  saving.value = true
  try {
    const payload = { ...form, buildingId: currentBuildingId.value }
    if (isEdit.value && editingFloorId.value) {
      await request.put(`/floors/${editingFloorId.value}`, payload)
      ElMessage.success('楼层更新成功')
    } else {
      await request.post('/floors', payload)
      ElMessage.success('楼层新增成功')
    }
    visible.value = false
    resetForm()
    await loadFloors(currentBuildingId.value)
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function deleteFloor(row) {
  if (!currentBuildingId.value) {
    ElMessage.warning('请先选择楼宇')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定停用/删除楼层「${row.floorName || row.floorNumber}」吗？系统会使用软删除保留历史数据。`,
      '删除确认',
      { type: 'warning' }
    )
    await request.delete(`/floors/${row.id}`)
    ElMessage.success('楼层删除成功')
    await loadFloors(currentBuildingId.value)
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error?.response?.data?.message || '删除失败')
    }
  }
}

async function batchGenerateFloors() {
  if (!currentBuildingId.value) {
    ElMessage.warning('请先选择楼宇')
    return
  }
  generating.value = true
  try {
    await request.post('/floors/batch-generate', {
      buildingId: currentBuildingId.value,
      basementStart: 2,
      basementEnd: 1,
      aboveStart: 1,
      aboveEnd: 9,
      status: 'ACTIVE'
    })
    ElMessage.success('楼层批量生成完成')
    await loadFloors(currentBuildingId.value)
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '楼层批量生成失败')
  } finally {
    generating.value = false
  }
}

onMounted(async () => {
  await loadBuildings()
  if (buildings.value.length > 0) {
    currentBuildingId.value = buildings.value[0].id
    await loadFloors(currentBuildingId.value)
  }
})

onBeforeUnmount(disposeQueryLifecycle)
</script>

<style scoped>
.page-container {
  padding: 20px;
}

.building-selector {
  width: 230px;
}

.selector-label,
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

@media (max-width: 1024px) {
  .building-selector {
    flex: 1 1 220px;
    width: auto;
  }

  .view-hint {
    flex-basis: 100%;
  }
}
</style>
