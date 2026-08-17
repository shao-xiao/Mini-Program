<template>
  <main class="page-container" :class="{ 'page-container--embedded': props.embedded }">
    <DataWorkspace
      :workspace-config="meetingBookingWorkspaceConfig"
      :views="workspaceViews"
      :active-view-id="activeViewId"
      :view-dirty="isViewDirty"
      :filter-logic="tablePrefs.filterLogic"
      :filter-logic-visible="tablePrefs.filters.length > 1"
      :filter-items="activeFilterItems"
      :query-notices="queryNotices"
      :metric-data="meetingBookingMetricData"
      @change-view="switchMeetingBookingView"
      @create-view="createTableView"
      @duplicate-view="duplicateTableView"
      @rename-view="renameTableView"
      @delete-view="deleteTableView"
      @save-view="saveMeetingBookingView"
      @remove-filter="removeViewFilter"
    >
      <template #header-actions>
        <el-button v-if="hasPermission('meeting-booking:create')" type="primary" :icon="Plus" @click="openDialog">
          {{ meetingBookingWorkspaceConfig.primaryAction.label }}
        </el-button>
      </template>

      <template #toolbar>
        <DhicDataToolbar
          :workspace-config="meetingBookingWorkspaceConfig"
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
          @save-field-config="saveMeetingBookingView"
        >
          <template #right>
            <el-button :icon="Refresh" @click="loadBookings">刷新</el-button>
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
          :data-mode="meetingBookingWorkspaceConfig.query.dataMode"
          :column-width="columnWidth"
          :column-fixed="columnFixed"
          :is-column-sortable="isColumnSortable"
          :show-overflow-tooltip="column => ['tenantName', 'applicantName', 'reviewReply', 'remark'].includes(column.key)"
          :row-class-name="bookingRowClassName"
          :operation-column="{ width: 175, fixed: 'right', align: 'left' }"
          @column-resize="handleHeaderDragEnd"
          @sort-change="handleTableSortChange"
          @retry="loadBookings"
          aria-label="会议预约数据表格"
        >
          <template #operations="{ row }">
            <div class="action-cell">
              <el-button
                v-if="hasPermission('meeting-booking:confirm') && (row.status === 'PENDING' || row.status === 'BOOKED')"
                link
                type="primary"
                @click="openReview(row)"
              >审核</el-button>
              <el-button v-else-if="hasPermission('meeting-booking:complete') && row.status === 'CONFIRMED'" link type="success" @click="complete(row)">完成</el-button>
              <el-button v-else link type="primary" @click="view(row)">查看</el-button>
              <el-dropdown trigger="click" @command="command => handleBookingCommand(command, row)">
                <el-button link type="primary">更多</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="view">查看详情</el-dropdown-item>
                    <el-dropdown-item v-if="canViewBills" command="bill" :disabled="!row.billingId">查看账单</el-dropdown-item>
                    <el-dropdown-item v-if="hasPermission('meeting-booking:cancel')" command="cancel" :disabled="!(['PENDING', 'BOOKED'].includes(row.status) || (row.status === 'CONFIRMED' && !row.billPaid))">取消</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </WorkspaceDataGrid>
      </template>
    </DataWorkspace>

    <el-dialog v-model="visible" title="新增会议预约" width="680px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="来源类型" prop="sourceType">
          <el-radio-group v-model="form.sourceType" @change="onSourceChange">
            <el-radio-button value="INTERNAL">内部员工</el-radio-button>
            <el-radio-button value="TENANT">租户</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="申请人" prop="applicantName">
          <el-input v-model="form.applicantName" />
        </el-form-item>
        <el-form-item label="部门">
          <el-input v-model.trim="form.departmentName" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item v-if="form.sourceType === 'TENANT'" label="租户" prop="tenantId">
          <el-select v-model="form.tenantId" filterable style="width:100%" @change="onTenantChange">
            <el-option v-for="tenant in tenants" :key="tenant.id" :label="tenant.tenantName" :value="tenant.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="form.applicantPhone" />
        </el-form-item>
        <el-form-item label="会议用途" prop="purpose">
          <el-input v-model.trim="form.purpose" />
        </el-form-item>
        <el-form-item label="参会人数">
          <el-input-number v-model="form.participantCount" :min="1" style="width:100%" />
        </el-form-item>
        <el-form-item label="会议室" prop="roomId">
          <el-select v-model="form.roomId" style="width:100%" @change="calculate">
            <el-option v-for="room in availableRooms" :key="room.id" :label="room.name" :value="room.id" :disabled="room.status !== 'AVAILABLE'" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始时间" prop="startTime">
          <el-date-picker v-model="form.startTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" format="YYYY-MM-DD HH:mm" style="width:100%" @change="onTimeChange" />
        </el-form-item>
        <el-form-item label="结束时间" prop="endTime">
          <el-date-picker v-model="form.endTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" format="YYYY-MM-DD HH:mm" style="width:100%" @change="onTimeChange" />
        </el-form-item>
        <el-form-item label="收费规则快照">
          <el-input :model-value="feeTypeText(form.feeType)" readonly />
        </el-form-item>
        <el-form-item label="预约费用依据">
          <el-input :model-value="`¥ ${money(form.amount)}`" readonly />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button v-if="hasPermission('meeting-booking:create')" type="primary" @click="save">保存预约</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailVisible" title="预约详情" width="560px">
      <el-descriptions v-if="current" :column="1" border>
        <el-descriptions-item label="预约单号">{{ current.bookingNo }}</el-descriptions-item>
        <el-descriptions-item label="会议室">{{ current.roomName }}</el-descriptions-item>
        <el-descriptions-item label="申请人">{{ current.applicantName }}</el-descriptions-item>
        <el-descriptions-item label="部门">{{ current.departmentName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="提交时间">{{ formatTime(current.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="时间">{{ formatTime(current.startTime) }} - {{ formatTime(current.endTime) }}</el-descriptions-item>
        <el-descriptions-item label="金额">¥ {{ money(current.amount) }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ statusText(current.status) }}</el-descriptions-item>
        <el-descriptions-item label="物业回复">{{ current.reviewReply || '-' }}</el-descriptions-item>
        <el-descriptions-item label="审核人">{{ current.reviewerName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="审核时间">{{ formatTime(current.reviewedAt) }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ current.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <el-dialog v-model="reviewVisible" title="审核预约" width="600px" destroy-on-close>
      <div v-if="reviewBooking" class="review-summary">
        <div>
          <span>预约编号</span>
          <strong>{{ reviewBooking.bookingNo }}</strong>
        </div>
        <div>
          <span>会议室</span>
          <strong>{{ reviewBooking.roomName }}</strong>
        </div>
        <div>
          <span>申请主体</span>
          <strong>{{ reviewBooking.applicantName || reviewBooking.tenantName || '-' }}</strong>
        </div>
        <div>
          <span>预约时间</span>
          <strong>{{ formatTime(reviewBooking.startTime) }} - {{ formatTime(reviewBooking.endTime) }}</strong>
        </div>
      </div>
      <el-form label-position="top" class="review-form">
        <el-form-item label="审核结果">
          <el-radio-group v-model="reviewForm.decision" @change="onReviewDecisionChange">
            <el-radio-button value="APPROVE">审核通过</el-radio-button>
            <el-radio-button value="REJECT">驳回预约</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="reviewForm.decision === 'REJECT' ? '驳回原因' : '预约回复'" :required="reviewForm.decision === 'REJECT'">
          <el-input
            v-model.trim="reviewForm.reviewReply"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            :placeholder="reviewForm.decision === 'REJECT' ? '例如：该时间段正在使用，请选择其他时间' : '向预约人说明审核结果或使用提醒'"
          />
        </el-form-item>
        <div class="review-hint">审核结果和回复会同步显示在预约租户的小程序中。</div>
      </el-form>
      <template #footer>
        <el-button @click="reviewVisible = false">取消</el-button>
        <el-button
          :type="reviewForm.decision === 'REJECT' ? 'danger' : 'primary'"
          :loading="reviewSubmitting"
          @click="submitReview"
        >提交审核</el-button>
      </template>
    </el-dialog>
  </main>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
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
  buildMeetingBookingMetricSnapshot,
  meetingBookingSourceOptions,
  meetingBookingStatusOptions,
  meetingBookingWorkspaceConfig,
  workspaceOptionLabel
} from '../../workspaceConfigs/parkingMeetingWorkspaceConfig'
import { workspaceTableOptions } from '../../workspaceConfigs/workspaceConfig'

const props = defineProps({
  embedded: { type: Boolean, default: false },
  roomId: { type: [Number, String], default: null }
})
const canViewBills = computed(() => hasPermission('bill:view') || hasPermission('bill:*'))

const route = useRoute()
const router = useRouter()
const rooms = ref([])
const availableRooms = ref([])
const tenants = ref([])
const bookings = ref([])
const visible = ref(false)
const detailVisible = ref(false)
const reviewVisible = ref(false)
const reviewSubmitting = ref(false)
const reviewBooking = ref(null)
const current = ref(null)
const formRef = ref()
const listState = useWorkspaceListRequestState({
  hasResults: () => bookings.value.length > 0,
  clearResults: () => {
    bookings.value = []
  },
  staleMessage: '会议预约刷新失败，当前显示上次成功数据'
})
const loading = computed(() => listState.status.value === 'loading')
const listErrorText = listState.errorText
const routeRoomId = computed(() => {
  const roomId = Number(props.embedded ? props.roomId : route.query.roomId)
  return Number.isFinite(roomId) && roomId > 0 ? roomId : null
})
const bookingRows = computed(() => routeRoomId.value
  ? bookings.value.filter(item => Number(item.roomId) === routeRoomId.value)
  : bookings.value)

const form = reactive({
  roomId: null,
  sourceType: 'INTERNAL',
  tenantId: null,
  tenantName: '',
  applicantName: '',
  departmentName: '',
  applicantPhone: '',
  purpose: '',
  participantCount: 1,
  startTime: '',
  endTime: '',
  feeType: 'INTERNAL_FREE',
  amount: 0,
  remark: ''
})

const reviewForm = reactive({
  decision: 'APPROVE',
  reviewReply: '预约已审核通过'
})

const rules = computed(() => ({
  sourceType: [{ required: true, message: '请选择来源类型', trigger: 'change' }],
  applicantName: [{ required: true, message: '请输入申请人', trigger: 'blur' }],
  purpose: [{ required: true, message: '请输入会议用途', trigger: 'blur' }],
  tenantId: form.sourceType === 'TENANT' ? [{ required: true, message: '请选择租户', trigger: 'change' }] : [],
  roomId: [{ required: true, message: '请选择会议室', trigger: 'change' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }]
}))

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
} = useDhicTableView(workspaceTableOptions(meetingBookingWorkspaceConfig, {
  records: () => bookingRows.value,
  serverTotal: () => bookingRows.value.length,
  filters: () => routeRoomId.value ? [{
    label: '会议室',
    value: rooms.value.find(room => Number(room.id) === routeRoomId.value)?.name || `#${routeRoomId.value}`
  }] : [],
  groupLabel: value => workspaceOptionLabel(
    [...meetingBookingStatusOptions, ...meetingBookingSourceOptions],
    value
  )
}))

const meetingBookingMetricData = computed(() => buildMeetingBookingMetricSnapshot(bookingRows.value, visibleRecords.value))

const switchMeetingBookingView = async (viewId) => {
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

const saveMeetingBookingView = () => {
  saveCurrentView()
  ElMessage.success(`视图“${activeViewName.value}”已保存`)
}

const loadRooms = async () => {
  const data = await request.get('/meeting-rooms', { silent: true })
  rooms.value = readPage(data).records
  availableRooms.value = rooms.value
}

const loadTenants = async () => {
  const data = await request.get('/tenant/list', { silent: true })
  tenants.value = readPage(data).records
}

const loadBookings = async () => {
  const requestId = listRequestGuard.start()
  const requestContext = listState.begin({ resource: '/meeting-bookings' })
  try {
    const data = await request.get('/meeting-bookings', { silent: true })
    if (!listRequestGuard.isCurrent(requestId) || !listState.isCurrent(requestContext)) return
    bookings.value = readPage(data).records
    listState.succeed(requestContext)
  } catch (error) {
    if (!listRequestGuard.isCurrent(requestId) || !listState.isCurrent(requestContext)) return
    listState.fail(requestContext, error?.response?.data?.message || '会议预约加载失败，请稍后重试')
  }
}

const load = async () => {
  await Promise.allSettled([loadRooms(), loadTenants(), loadBookings()])
}

const openDialog = () => {
  Object.assign(form, {
    roomId: routeRoomId.value,
    sourceType: 'INTERNAL',
    tenantId: null,
    tenantName: '',
    applicantName: '',
    departmentName: '',
    applicantPhone: '',
    purpose: '',
    participantCount: 1,
    startTime: '',
    endTime: '',
    feeType: 'INTERNAL_FREE',
    amount: 0,
    remark: ''
  })
  visible.value = true
}

const applyInternalFree = () => {
  form.feeType = 'INTERNAL_FREE'
  form.amount = 0
}

const onSourceChange = () => {
  if (form.sourceType === 'INTERNAL') {
    form.tenantId = null
    form.tenantName = ''
    applyInternalFree()
    return
  }
  form.feeType = 'HOURLY'
  calculate()
}

const onTenantChange = () => {
  const tenant = tenants.value.find(item => item.id === form.tenantId)
  form.tenantName = tenant?.tenantName || ''
  if (!form.applicantName) form.applicantName = tenant?.contactPerson || tenant?.tenantName || ''
  if (!form.applicantPhone) form.applicantPhone = tenant?.contactPhone || ''
}

const onTimeChange = async () => {
  await loadAvailability()
  await calculate()
}

const loadAvailability = async () => {
  if (!form.startTime || !form.endTime) {
    availableRooms.value = rooms.value
    return
  }
  availableRooms.value = await request.get('/meeting-rooms/available', {
    params: { startTime: form.startTime, endTime: form.endTime }
  })
}

const calculate = async () => {
  if (form.sourceType === 'INTERNAL') {
    applyInternalFree()
    return
  }
  if (!form.roomId || !form.startTime || !form.endTime || !form.sourceType) return
  try {
    const data = await request.get('/meeting-bookings/calculate', {
      params: {
        roomId: form.roomId,
        sourceType: form.sourceType,
        startTime: form.startTime,
        endTime: form.endTime
      },
      silent: true
    })
    form.feeType = data.feeType
    form.amount = data.amount
  } catch (error) {
    ElMessage.error(error.message || '金额计算失败')
  }
}

const save = async () => {
  await formRef.value?.validate()
  await request.post('/meeting-bookings', { ...form })
  ElMessage.success('预约已创建')
  visible.value = false
  await loadBookings()
}

const openReview = (row) => {
  reviewBooking.value = row
  reviewForm.decision = 'APPROVE'
  reviewForm.reviewReply = '预约已审核通过'
  reviewVisible.value = true
}

const onReviewDecisionChange = (decision) => {
  reviewForm.reviewReply = decision === 'APPROVE' ? '预约已审核通过' : ''
}

const submitReview = async () => {
  if (!reviewBooking.value || reviewSubmitting.value) return
  if (reviewForm.decision === 'REJECT' && !reviewForm.reviewReply) {
    ElMessage.warning('驳回预约时请填写回复')
    return
  }
  reviewSubmitting.value = true
  try {
    const payload = { reviewReply: reviewForm.reviewReply }
    if (reviewForm.decision === 'REJECT') {
      await request.post(`/meeting-bookings/${reviewBooking.value.id}/reject`, payload)
    } else {
      await request.post(`/meeting-bookings/${reviewBooking.value.id}/confirm`, payload)
    }
    ElMessage.success(reviewForm.decision === 'REJECT' ? '预约已驳回并回复' : '预约审核已通过')
    reviewVisible.value = false
    await loadBookings()
  } finally {
    reviewSubmitting.value = false
  }
}

const cancel = async (row) => {
  const reason = await ElMessageBox.prompt('请输入取消原因', '取消预约', {
    confirmButtonText: '确认取消',
    cancelButtonText: '返回',
    inputPlaceholder: '可选'
  }).catch(() => null)
  if (!reason) return
  await request.post(`/meeting-bookings/${row.id}/cancel`, { cancelReason: reason.value || '' })
  ElMessage.success('已取消')
  await loadBookings()
}

const complete = async (row) => {
  await request.post(`/meeting-bookings/${row.id}/complete`)
  ElMessage.success('已完成')
  await loadBookings()
}

const viewBill = (row) => {
  if (!row.billingId || !row.billNumber) {
    ElMessage.warning('未找到关联财务账单')
    return
  }
  router.push({ path: '/bills', query: { keyword: row.billNumber } })
}

const view = (row) => {
  current.value = row
  detailVisible.value = true
}

const handleBookingCommand = (command, row) => {
  if (command === 'view') {
    view(row)
  } else if (command === 'bill') {
    viewBill(row)
  } else if (command === 'cancel') {
    cancel(row)
  }
}

const bookingRowClassName = ({ row }) => {
  if (row.__group) return 'view-group-row'
  if (['CANCELLED', 'REJECTED'].includes(row.status)) return 'row-muted'
  if (['PENDING', 'BOOKED'].includes(row.status)) return 'row-warning'
  if (row.status === 'COMPLETED') return 'row-done'
  return ''
}

const feeTypeText = (value) => ({ INTERNAL_FREE: '内部免费', HOURLY: '按小时收费' }[value] || value)
const statusText = (value) => ({
  PENDING: '待审核',
  BOOKED: '待审核',
  CONFIRMED: '审核通过',
  REJECTED: '已驳回',
  CANCELLED: '已取消',
  COMPLETED: '已完成'
}[value] || value)
const money = (value) => Number(value || 0).toFixed(2)
const formatTime = (value) => value ? String(value).replace('T', ' ').slice(0, 16) : '-'

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

.page-container--embedded {
  padding: 0;
}

.action-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}

.action-cell :deep(.el-button) {
  margin-left: 0;
}

.review-summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 20px;
  padding: 14px 16px;
  margin-bottom: 18px;
  background: var(--dhic-bg-soft, #f5f7fa);
  border-radius: 10px;
}

.review-summary div {
  min-width: 0;
}

.review-summary span,
.review-summary strong {
  display: block;
}

.review-summary span {
  margin-bottom: 4px;
  color: var(--dhic-text-secondary, #64748b);
  font-size: 12px;
}

.review-summary strong {
  color: var(--dhic-text-primary, #1f2937);
  font-size: 14px;
  line-height: 1.45;
}

.review-hint {
  margin-top: -6px;
  color: var(--dhic-text-secondary, #64748b);
  font-size: 12px;
}

:deep(.row-warning) {
  --el-table-tr-bg-color: #fffaf0;
}

:deep(.row-muted) {
  --el-table-tr-bg-color: #f7f8fa;
  color: #606266;
}

:deep(.row-done) {
  --el-table-tr-bg-color: #f6fcf8;
}
</style>
