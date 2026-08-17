<template>
  <main class="page-container">
    <DataWorkspace
      :workspace-config="meetingRoomWorkspaceConfig"
      :views="workspaceViews"
      :active-view-id="activeViewId"
      :view-dirty="isViewDirty"
      :filter-logic="tablePrefs.filterLogic"
      :filter-logic-visible="tablePrefs.filters.length > 1"
      :filter-items="activeFilterItems"
      :query-notices="queryNotices"
      :metric-data="meetingRoomMetricData"
      @change-view="switchMeetingRoomView"
      @create-view="createTableView"
      @duplicate-view="duplicateTableView"
      @rename-view="renameTableView"
      @delete-view="deleteTableView"
      @save-view="saveMeetingRoomView"
      @remove-filter="removeViewFilter"
    >
      <template #header-actions>
        <el-button v-if="hasPermission('meeting-room:create')" type="primary" :icon="Plus" @click="openDialog()">
          {{ meetingRoomWorkspaceConfig.primaryAction.label }}
        </el-button>
      </template>

      <template #toolbar>
        <DhicDataToolbar
          :workspace-config="meetingRoomWorkspaceConfig"
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
          @save-field-config="saveMeetingRoomView"
        >
          <template #right>
            <el-button :icon="Refresh" @click="load">刷新</el-button>
            <span class="view-hint">已加载 {{ resultScope.loadedRecords }} 间，当前视图 {{ resultScope.visibleRecords }} 间</span>
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
          :data-mode="meetingRoomWorkspaceConfig.query.dataMode"
          :column-width="columnWidth"
          :column-fixed="columnFixed"
          :is-column-sortable="isColumnSortable"
          :show-overflow-tooltip="column => ['location', 'remark'].includes(column.key)"
          :row-class-name="roomRowClassName"
          :operation-column="{ width: 175, fixed: 'right', align: 'left' }"
          @column-resize="handleHeaderDragEnd"
          @sort-change="handleTableSortChange"
          @retry="load"
          aria-label="会议室管理数据表格"
        >
          <template #operations="{ row }">
            <div class="action-cell">
              <el-button link type="primary" @click="viewBookings(row)">查看预约</el-button>
              <el-dropdown trigger="click" @command="command => handleRoomCommand(command, row)">
                <el-button link type="primary">更多</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item v-if="hasPermission('meeting-room:update')" command="edit">编辑</el-dropdown-item>
                    <el-dropdown-item v-if="hasPermission('meeting-room:update')" command="toggle">{{ row.status === 'AVAILABLE' ? '停用' : '启用' }}</el-dropdown-item>
                    <el-dropdown-item v-if="hasPermission('meeting-room:delete')" command="delete">删除</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </WorkspaceDataGrid>
      </template>
    </DataWorkspace>

    <el-dialog v-model="visible" :title="form.id ? '编辑会议室' : '新增会议室'" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="130px">
        <el-form-item label="会议室名称" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="位置" prop="location">
          <el-input v-model="form.location" />
        </el-form-item>
        <el-form-item label="容纳人数" prop="capacity">
          <el-input-number v-model="form.capacity" :min="1" style="width:100%" />
        </el-form-item>
        <el-form-item label="设备">
          <el-input v-model="form.equipment" placeholder="投影、白板、视频会议等" />
        </el-form-item>
        <el-form-item label="工作时间费率" prop="workdayHourlyRate">
          <el-input-number v-model="form.workdayHourlyRate" :min="0" :precision="2" style="width:100%" />
        </el-form-item>
        <el-form-item label="非工作时间费率" prop="offHourHourlyRate">
          <el-input-number v-model="form.offHourHourlyRate" :min="0" :precision="2" style="width:100%" />
        </el-form-item>
        <el-form-item label="节假日费率" prop="holidayHourlyRate">
          <el-input-number v-model="form.holidayHourlyRate" :min="0" :precision="2" style="width:100%" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="form.status" style="width:100%">
            <el-option label="可预约" value="AVAILABLE" />
            <el-option label="维护中" value="MAINTENANCE" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button
          v-if="form.id ? hasPermission('meeting-room:update') : hasPermission('meeting-room:create')"
          type="primary"
          @click="save"
        >保存</el-button>
      </template>
    </el-dialog>
  </main>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
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
  buildMeetingRoomMetricSnapshot,
  meetingRoomStatusOptions,
  meetingRoomWorkspaceConfig,
  workspaceOptionLabel
} from '../../workspaceConfigs/parkingMeetingWorkspaceConfig'
import { workspaceTableOptions } from '../../workspaceConfigs/workspaceConfig'

const router = useRouter()
const rooms = ref([])
const visible = ref(false)
const formRef = ref()
const listState = useWorkspaceListRequestState({
  hasResults: () => rooms.value.length > 0,
  clearResults: () => {
    rooms.value = []
  },
  staleMessage: '会议室刷新失败，当前显示上次成功数据'
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
} = useDhicTableView(workspaceTableOptions(meetingRoomWorkspaceConfig, {
  records: () => rooms.value,
  serverTotal: () => rooms.value.length,
  groupLabel: value => workspaceOptionLabel(meetingRoomStatusOptions, value)
}))

const meetingRoomMetricData = computed(() => buildMeetingRoomMetricSnapshot(rooms.value, visibleRecords.value))

const form = reactive({
  id: null,
  name: '',
  location: '',
  capacity: 12,
  equipment: '',
  workdayHourlyRate: 100,
  offHourHourlyRate: 150,
  holidayHourlyRate: 200,
  status: 'AVAILABLE',
  remark: ''
})

const rules = {
  name: [{ required: true, message: '请输入会议室名称', trigger: 'blur' }],
  location: [{ required: true, message: '请输入位置', trigger: 'blur' }],
  capacity: [{ required: true, type: 'number', min: 1, message: '容纳人数必须大于0', trigger: 'change' }],
  workdayHourlyRate: [{ required: true, type: 'number', min: 0, message: '费率不能为负数', trigger: 'change' }],
  offHourHourlyRate: [{ required: true, type: 'number', min: 0, message: '费率不能为负数', trigger: 'change' }],
  holidayHourlyRate: [{ required: true, type: 'number', min: 0, message: '费率不能为负数', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

const reset = () => {
  Object.assign(form, {
    id: null,
    name: '',
    location: '',
    capacity: 12,
    equipment: '',
    workdayHourlyRate: 100,
    offHourHourlyRate: 150,
    holidayHourlyRate: 200,
    status: 'AVAILABLE',
    remark: ''
  })
}

const switchMeetingRoomView = async (viewId) => {
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

const saveMeetingRoomView = () => {
  saveCurrentView()
  ElMessage.success(`视图“${activeViewName.value}”已保存`)
}

const load = async () => {
  const requestId = listRequestGuard.start()
  const requestContext = listState.begin({ resource: '/meeting-rooms' })
  try {
    const data = await request.get('/meeting-rooms', { silent: true })
    if (!listRequestGuard.isCurrent(requestId) || !listState.isCurrent(requestContext)) return
    rooms.value = readPage(data).records
    listState.succeed(requestContext)
  } catch (error) {
    if (!listRequestGuard.isCurrent(requestId) || !listState.isCurrent(requestContext)) return
    listState.fail(requestContext, error?.response?.data?.message || '会议室加载失败，请稍后重试')
  }
}

const openDialog = (row) => {
  reset()
  if (row) {
    Object.assign(form, row)
  }
  visible.value = true
}

const save = async () => {
  await formRef.value?.validate()
  const payload = { ...form }
  if (form.id) {
    await request.put(`/meeting-rooms/${form.id}`, payload)
    ElMessage.success('更新成功')
  } else {
    await request.post('/meeting-rooms', payload)
    ElMessage.success('新增成功')
  }
  visible.value = false
  await load()
}

const toggleStatus = async (row) => {
  const nextStatus = row.status === 'AVAILABLE' ? 'DISABLED' : 'AVAILABLE'
  await request.put(`/meeting-rooms/${row.id}`, { ...row, status: nextStatus })
  ElMessage.success(nextStatus === 'AVAILABLE' ? '已启用' : '已停用')
  await load()
}

const viewBookings = (row) => {
  router.push({ path: '/meetings/bookings', query: { roomId: row.id } })
}

const remove = async (row) => {
  await ElMessageBox.confirm(`确认删除会议室 ${row.name}？`, '提示', { type: 'warning' })
  await request.delete(`/meeting-rooms/${row.id}`)
  ElMessage.success('删除成功')
  await load()
}

const handleRoomCommand = (command, row) => {
  if (command === 'edit') {
    openDialog(row)
  } else if (command === 'toggle') {
    toggleStatus(row)
  } else if (command === 'delete') {
    remove(row)
  }
}

const roomRowClassName = ({ row }) => {
  if (row.__group) return 'view-group-row'
  if (row.status === 'MAINTENANCE') return 'row-warning'
  if (row.status === 'DISABLED') return 'row-muted'
  return ''
}

onMounted(load)

onBeforeUnmount(() => {
  listRequestGuard.dispose()
  disposeQueryLifecycle()
})
</script>

<style scoped>
.page-container {
  min-width: 0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.filter-bar {
  margin-bottom: 14px;
}

.primary-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
  line-height: 1.35;
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

.metric-value {
  color: #303133;
  font-size: 18px;
  font-weight: 700;
}

.stack-cell,
.action-cell {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.action-cell {
  align-items: center;
}

:deep(.row-warning) {
  --el-table-tr-bg-color: #fffaf0;
}

:deep(.row-muted) {
  --el-table-tr-bg-color: #f7f8fa;
  color: #606266;
}
</style>
