<template>
  <main class="asset-page parking-asset-page">
    <header class="asset-page__header">
      <div>
        <h1>停车位管理</h1>
        <p>统一维护车位台账、租户车辆绑定、有效期和停车月费。</p>
      </div>
      <el-button v-if="hasPermission('bill:view') || hasPermission('bill:*')" @click="goToParkingBills">
        查看财务账单
      </el-button>
    </header>

    <el-tabs v-model="activeTab" class="asset-tabs" @tab-change="changeTab">
      <el-tab-pane label="车位台账" name="spaces" />
      <el-tab-pane label="绑定记录" name="assignments" />
    </el-tabs>

    <ParkingSpaceList v-if="activeTab === 'spaces'" embedded />

    <DataWorkspace
      v-else
      :workspace-config="parkingAssignmentWorkspaceConfig"
      :views="workspaceViews"
      :active-view-id="activeViewId"
      :view-dirty="isViewDirty"
      :filter-logic="tablePrefs.filterLogic"
      :filter-logic-visible="tablePrefs.filters.length > 1"
      :filter-items="activeFilterItems"
      :query-notices="queryNotices"
      @change-view="switchTableView"
      @create-view="createTableView"
      @duplicate-view="duplicateTableView"
      @rename-view="renameTableView"
      @delete-view="deleteTableView"
      @save-view="saveAssignmentView"
      @remove-filter="removeViewFilter"
    >
      <template #toolbar>
        <DhicDataToolbar
          :workspace-config="parkingAssignmentWorkspaceConfig"
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
          @save-field-config="saveAssignmentView"
        >
          <template #right>
            <el-button :icon="Refresh" :loading="loadingAssignments" @click="loadAssignments">刷新</el-button>
            <span class="view-hint">已加载 {{ resultScope.loadedRecords }} 条，当前视图 {{ resultScope.visibleRecords }} 条</span>
          </template>
        </DhicDataToolbar>
      </template>

      <template #grid>
        <WorkspaceDataGrid
          :loading="loadingAssignments"
          :error-text="assignmentError"
          retryable
          :rows="tableRows"
          :columns="visibleOrderedColumns"
          row-key="id"
          :density="tablePrefs.rowDensity"
          :empty-text="tableEmptyText"
          :sort-state="activeTableSort"
          data-mode="client"
          :column-width="columnWidth"
          :column-fixed="columnFixed"
          :is-column-sortable="isColumnSortable"
          :show-overflow-tooltip="column => ['tenantName', 'remark'].includes(column.key)"
          @column-resize="handleHeaderDragEnd"
          @sort-change="handleTableSortChange"
          @retry="loadAssignments"
          aria-label="停车绑定记录数据表格"
        />
      </template>
    </DataWorkspace>
  </main>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import ParkingSpaceList from '../parking/ParkingSpaceList.vue'
import DataWorkspace from '../../components/DataWorkspace.vue'
import DhicDataToolbar from '../../components/DhicDataToolbar.vue'
import WorkspaceDataGrid from '../../components/workspace/WorkspaceDataGrid.vue'
import { useDhicTableView } from '../../composables/useDhicTableView'
import { listParkingAssignments } from '../../api/parking'
import { hasPermission } from '../../utils/permission'
import { readPage } from '../../utils/pagination'
import { createLatestRequestGuard } from '../../utils/requestLifecycle'
import { parkingAssignmentWorkspaceConfig } from '../../workspaceConfigs/parkingMeetingWorkspaceConfig'
import { workspaceTableOptions } from '../../workspaceConfigs/workspaceConfig'

const route = useRoute()
const router = useRouter()
const activeTab = ref(route.query.tab === 'assignments' ? 'assignments' : 'spaces')
const assignments = ref([])
const loadingAssignments = ref(false)
const assignmentError = ref('')
const assignmentRequest = createLatestRequestGuard()
let pageActive = true

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
  removeViewFilter,
  disposeQueryLifecycle
} = useDhicTableView(workspaceTableOptions(parkingAssignmentWorkspaceConfig, {
  records: () => assignments.value,
  serverTotal: () => assignments.value.length
}))

function saveAssignmentView() {
  saveCurrentView()
  ElMessage.success(`视图“${activeViewName.value}”已保存`)
}

async function changeTab(tab) {
  activeTab.value = tab === 'assignments' ? 'assignments' : 'spaces'
  await router.replace({ path: '/assets/parking', query: { ...route.query, tab: activeTab.value } })
  if (activeTab.value === 'assignments' && !assignments.value.length) await loadAssignments()
}

function goToParkingBills() {
  router.push({ path: '/bills', query: { billType: 'PARKING' } })
}

function normalizeAssignment(row) {
  const spaceId = Number(row.parking_space_id ?? row.parkingSpaceId ?? row.spaceId)
  const tenantId = Number(row.tenant_id ?? row.tenantId)
  return {
    id: Number(row.id),
    assignmentCode: row.code || row.assignmentCode || `#${row.id}`,
    spaceNo: row.spaceNo || `#${spaceId}`,
    tenantName: row.tenantName || `#${tenantId}`,
    plateNo: row.car_plate_no || row.carPlateNo || row.plateNo || '-',
    contactName: row.contactName || '-',
    vehicleType: row.vehicleType || '-',
    startDate: row.start_date || row.startDate || null,
    endDate: row.end_date || row.endDate || null,
    unitPriceSnapshot: row.unit_price_snapshot ?? row.unitPriceSnapshot ?? row.monthly_fee ?? row.monthlyFee ?? null,
    status: row.status || 'ACTIVE',
    remark: row.remark || ''
  }
}

async function loadAssignments() {
  const requestId = assignmentRequest.start()
  loadingAssignments.value = true
  assignmentError.value = ''
  try {
    const assignmentData = await listParkingAssignments()
    if (!pageActive || !assignmentRequest.isCurrent(requestId)) return
    assignments.value = readPage(assignmentData).records.map(normalizeAssignment)
  } catch (error) {
    if (!pageActive || !assignmentRequest.isCurrent(requestId)) return
    assignments.value = []
    assignmentError.value = error?.response?.data?.message || '停车绑定记录加载失败'
  } finally {
    if (pageActive && assignmentRequest.isCurrent(requestId)) loadingAssignments.value = false
  }
}

onMounted(async () => {
  await router.replace({ path: '/assets/parking', query: { ...route.query, tab: activeTab.value } })
  if (activeTab.value === 'assignments') await loadAssignments()
})

onBeforeUnmount(() => {
  pageActive = false
  assignmentRequest.dispose()
  disposeQueryLifecycle()
})
</script>

<style scoped>
.asset-page {
  min-width: 0;
  padding: 16px;
}

.asset-page__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.asset-page__header h1 {
  margin: 0;
  font-size: 20px;
  line-height: 28px;
  letter-spacing: 0;
}

.asset-page__header p {
  margin: 3px 0 0;
  color: var(--dhic-text-muted, #909399);
  font-size: 12px;
}

.asset-tabs {
  margin-top: 8px;
}

.asset-tabs :deep(.el-tabs__header) {
  margin-bottom: 10px;
}

.asset-tabs :deep(.el-tabs__content) {
  display: none;
}

.view-hint {
  color: #7b8494;
  font-size: 12px;
  white-space: nowrap;
}

@media (max-width: 1024px) {
  .asset-page {
    padding: 12px;
  }

  .asset-page__header {
    flex-wrap: wrap;
  }
}
</style>
