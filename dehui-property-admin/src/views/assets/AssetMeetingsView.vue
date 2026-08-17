<template>
  <main class="asset-page meeting-asset-page">
    <PageHeader title="会议管理" description="统一维护会议室、收费规则与预约记录">
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
        <el-button v-if="hasPermission('meeting-room:create')" type="primary" :icon="Plus" @click="openRoomDialog()">新增会议室</el-button>
      </template>
    </PageHeader>

    <section v-if="canViewMeetingRooms" class="room-panel" aria-labelledby="meeting-room-heading">
      <div class="room-panel__heading">
        <div>
          <h2 id="meeting-room-heading">会议室</h2>
          <span>选择后筛选下方预约记录</span>
        </div>
        <el-input
          v-model.trim="roomKeyword"
          :prefix-icon="Search"
          clearable
          class="room-search"
          placeholder="搜索名称或位置"
          aria-label="搜索会议室"
        />
      </div>

      <div v-loading="loading" class="room-grid">
        <article
          class="room-card room-card--all"
          :class="{ 'is-active': !selectedRoomId }"
        >
          <button
            type="button"
            class="room-card__all-select"
            :aria-pressed="!selectedRoomId"
            @click="selectRoom(null)"
          >
            <span class="room-card__icon"><el-icon><Grid /></el-icon></span>
            <span class="room-card__content">
              <strong>全部会议室</strong>
              <small>查看全部预约</small>
              <span>今日预约 {{ todayBookings.length }} 场</span>
            </span>
            <span class="room-card__all-stats">
              <strong>{{ rooms.length }}</strong>
              <small>会议室</small>
              <span>{{ availableRoomCount }} 间可预约</span>
            </span>
          </button>
        </article>

        <article
          v-for="room in filteredRooms"
          :key="room.id"
          class="room-card"
          :class="{ 'is-active': Number(room.id) === Number(selectedRoomId) }"
        >
          <button
            type="button"
            class="room-card__select"
            :aria-label="`查看${room.name || '会议室'}的预约记录`"
            :aria-pressed="Number(room.id) === Number(selectedRoomId)"
            @click="selectRoom(room.id)"
          >
            <span class="room-card__title-row">
              <strong>{{ room.name || '未命名会议室' }}</strong>
              <span class="room-card__status" :class="statusClass(room.status)">{{ statusText(room.status) }}</span>
            </span>
            <small>{{ room.location || '未配置位置' }} · {{ Number(room.capacity || 0) }} 人</small>
            <span class="room-card__detail-line" :title="meetingRoomFacilitiesText(room)">
              <span>设施</span>
              <span>{{ meetingRoomFacilitiesText(room) }}</span>
            </span>
            <span class="room-card__detail-line">
              <span>开放</span>
              <span>{{ meetingRoomOpenHours(room) }}</span>
            </span>
            <span class="room-card__summary">
              <span>{{ meetingRoomFeeText(room) }}</span>
              <span>今日预约 {{ todayBookingCount(room.id) }} 场</span>
            </span>
          </button>
          <div
            class="room-card__media"
            role="button"
            tabindex="0"
            :aria-label="`选择${room.name || '会议室'}并查看预约记录`"
            @click="selectRoom(room.id)"
            @keydown.enter.prevent="selectRoom(room.id)"
            @keydown.space.prevent="selectRoom(room.id)"
          >
            <CustomFieldValueDisplay
              v-if="roomHasDisplayableCover(room)"
              class="room-card__image"
              :field="ROOM_IMAGE_FIELD"
              :value="[roomCoverImage(room)]"
              :image-alt="`${room.name || '会议室'}图片`"
              mode="detail"
              @image-state="state => handleRoomImageState(room.id, state)"
            />
            <span v-else class="room-card__fallback">
              <strong>会议室详情</strong>
              <span :title="meetingRoomFacilitiesText(room)">{{ meetingRoomFacilitiesText(room) }}</span>
              <span :title="meetingRoomDescriptionText(room)">{{ meetingRoomDescriptionText(room) }}</span>
              <small>{{ meetingRoomOpenHours(room) }}</small>
            </span>
          </div>
          <el-dropdown
            v-if="hasPermission('meeting-room:update') || hasPermission('meeting-room:delete')"
            class="room-card__menu"
            trigger="click"
            @command="command => handleRoomCommand(command, room)"
            @click.stop
          >
            <el-button
              class="room-card__more"
              text
              circle
              :icon="MoreFilled"
              :aria-label="`管理${room.name || '会议室'}`"
            />
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item v-if="hasPermission('meeting-room:update')" command="edit">编辑</el-dropdown-item>
                <el-dropdown-item v-if="hasPermission('meeting-room:update')" command="rate">收费规则</el-dropdown-item>
                <el-dropdown-item v-if="hasPermission('meeting-room:update')" command="toggle">{{ isMeetingRoomAvailable(room) ? '停用' : '启用' }}</el-dropdown-item>
                <el-dropdown-item v-if="hasPermission('meeting-room:delete')" command="delete" divided>删除</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </article>
      </div>
    </section>

    <el-alert v-if="loadError" class="asset-page__error" type="error" :closable="false" show-icon :title="loadError" />

    <section v-if="canViewBookings" class="booking-panel">
      <div class="booking-panel__context">
        <strong>{{ selectedRoom?.name || '全部会议室' }}</strong>
        <span>{{ selectedRoom ? `${selectedRoom.location || '未配置位置'}的预约记录` : '全部会议室预约记录' }}</span>
      </div>
      <MeetingBookingList embedded :room-id="selectedRoomId" />
    </section>

    <el-empty
      v-if="!canViewMeetingRooms && !canViewBookings"
      description="当前账号没有会议室或会议预约查看权限"
      :image-size="84"
    />

    <el-dialog v-model="roomDialogVisible" :title="roomForm.id ? '编辑会议室' : '新增会议室'" width="min(600px, calc(100vw - 32px))">
      <el-form ref="roomFormRef" :model="roomForm" :rules="roomRules" label-width="130px">
        <el-form-item label="会议室名称" prop="name"><el-input v-model.trim="roomForm.name" /></el-form-item>
        <el-form-item label="位置" prop="location"><el-input v-model.trim="roomForm.location" /></el-form-item>
        <el-form-item label="容纳人数" prop="capacity"><el-input-number v-model="roomForm.capacity" :min="1" style="width:100%" /></el-form-item>
        <el-form-item label="设备设施"><el-input v-model.trim="roomForm.facilities" type="textarea" :rows="2" placeholder="投影、白板、视频会议等" /></el-form-item>
        <el-form-item label="房间描述"><el-input v-model.trim="roomForm.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="开放时间">
          <div class="time-range">
            <el-time-picker v-model="roomForm.openTime" value-format="HH:mm:ss" placeholder="开始时间" />
            <span>至</span>
            <el-time-picker v-model="roomForm.closeTime" value-format="HH:mm:ss" placeholder="结束时间" />
          </div>
        </el-form-item>
        <el-form-item label="会议室图片">
          <CustomFieldValueInput
            v-model="roomForm.imageFileIds"
            :field="{ type: 'image', workspaceKey: 'meeting-rooms', maxFiles: 9 }"
          />
        </el-form-item>
        <el-form-item label="初始小时价" prop="hourlyRate">
          <el-input-number v-model="roomForm.hourlyRate" :min="0" :precision="2" style="width:100%" />
          <div class="form-tip">仅用于兼容旧数据；后续价格变化请使用带生效日期的收费规则。</div>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model.trim="roomForm.remark" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="roomForm.status" style="width:100%">
            <el-option label="可预约" value="AVAILABLE" />
            <el-option label="维护中" value="MAINTENANCE" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roomDialogVisible = false">取消</el-button>
        <el-button
          v-if="roomForm.id ? hasPermission('meeting-room:update') : hasPermission('meeting-room:create')"
          type="primary"
          :loading="savingRoom"
          @click="saveRoom"
        >保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="rateDrawerVisible" title="会议室收费规则" size="min(720px, 92vw)">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="预约时保存规则、单价、计费时长和金额快照；替换规则不会改写历史预约。"
      />
      <el-table :data="rateRules" v-loading="rateLoading" class="rate-table">
        <el-table-column prop="chargeType" label="类型" width="100">
          <template #default="{ row }">{{ row.chargeType === 'FREE' ? '免费' : '按小时' }}</template>
        </el-table-column>
        <el-table-column prop="hourlyPrice" label="小时价" width="110" />
        <el-table-column prop="minimumMinutes" label="最低时长(分)" width="125" />
        <el-table-column prop="effectiveFrom" label="生效日期" width="120" />
        <el-table-column prop="effectiveTo" label="失效日期" width="120" />
        <el-table-column prop="status" label="状态" width="90" />
      </el-table>
      <el-divider content-position="left">新收费规则</el-divider>
      <el-form :model="rateForm" label-width="120px">
        <el-form-item label="收费类型">
          <el-radio-group v-model="rateForm.chargeType">
            <el-radio-button value="FREE">免费</el-radio-button>
            <el-radio-button value="HOURLY">按小时</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="rateForm.chargeType === 'HOURLY'" label="小时价格">
          <el-input-number v-model="rateForm.hourlyPrice" :min="0" :precision="2" style="width:100%" />
        </el-form-item>
        <el-form-item label="最低预约时长">
          <el-input-number v-model="rateForm.minimumMinutes" :min="1" :step="30" style="width:100%" />
        </el-form-item>
        <el-form-item label="生效日期">
          <el-date-picker v-model="rateForm.effectiveFrom" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="失效日期">
          <el-date-picker v-model="rateForm.effectiveTo" type="date" value-format="YYYY-MM-DD" clearable style="width:100%" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model.trim="rateForm.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rateDrawerVisible = false">关闭</el-button>
        <el-button type="primary" :loading="savingRate" @click="saveRateRule">保存并替换当前规则</el-button>
      </template>
    </el-drawer>
  </main>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Grid, MoreFilled, Plus, Refresh, Search } from '@element-plus/icons-vue'
import PageHeader from '../../components/layout/PageHeader.vue'
import MeetingBookingList from '../meeting/MeetingBookingList.vue'
import CustomFieldValueDisplay from '../../components/workspace/CustomFieldValueDisplay.vue'
import CustomFieldValueInput from '../../components/workspace/CustomFieldValueInput.vue'
import request from '../../utils/request'
import { hasPermission } from '../../utils/permission'
import { readPage } from '../../utils/pagination'
import { createLatestRequestGuard } from '../../utils/requestLifecycle'
import {
  firstMeetingRoomImage,
  isMeetingRoomAvailable,
  localDateKey,
  meetingRoomDescriptionText,
  meetingRoomFacilitiesText,
  meetingRoomFeeText,
  meetingRoomOpenHours,
  normalizeMeetingRoomImages
} from '../../utils/meetingRoomPresentation'

const route = useRoute()
const router = useRouter()
const rooms = ref([])
const bookings = ref([])
const roomKeyword = ref('')
const selectedRoomId = ref(null)
const loading = ref(false)
const loadError = ref('')
const roomDialogVisible = ref(false)
const savingRoom = ref(false)
const roomFormRef = ref(null)
const rateDrawerVisible = ref(false)
const rateLoading = ref(false)
const savingRate = ref(false)
const rateRoomId = ref(null)
const rateRules = ref([])
const rateForm = reactive(defaultRateForm())
const requestGuard = createLatestRequestGuard()
const ROOM_IMAGE_FIELD = Object.freeze({ type: 'image' })
const failedRoomCoverIds = ref(new Set())
let pageActive = true

const canViewMeetingRooms = computed(() => hasPermission('meeting-room:view') || hasPermission('meeting-room:*'))
const canViewBookings = computed(() => hasPermission('meeting-booking:view') || hasPermission('meeting-booking:*'))

const roomForm = reactive(defaultRoomForm())
const roomRules = {
  name: [{ required: true, message: '请输入会议室名称', trigger: 'blur' }],
  location: [{ required: true, message: '请输入位置', trigger: 'blur' }],
  capacity: [{ required: true, type: 'number', min: 1, message: '容纳人数必须大于0', trigger: 'change' }],
  hourlyRate: [{ required: true, type: 'number', min: 0, message: '费率不能为负数', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

const filteredRooms = computed(() => {
  const keyword = roomKeyword.value.toLowerCase()
  if (!keyword) return rooms.value
  return rooms.value.filter(room => [room.name, room.location, room.code]
    .some(value => String(value || '').toLowerCase().includes(keyword)))
})
const availableRoomCount = computed(() => rooms.value.filter(isMeetingRoomAvailable).length)
const selectedRoom = computed(() => rooms.value.find(room => Number(room.id) === Number(selectedRoomId.value)) || null)
const today = localDateKey()
const todayBookings = computed(() => bookings.value.filter(booking => String(booking.startTime || '').slice(0, 10) === today && booking.status !== 'CANCELLED'))

function todayBookingCount(roomId) {
  return todayBookings.value.filter(booking => Number(booking.roomId) === Number(roomId)).length
}

function roomCoverImage(room) {
  return firstMeetingRoomImage(room?.imageFileIds)
}

function roomHasDisplayableCover(room) {
  return Boolean(roomCoverImage(room)) && !failedRoomCoverIds.value.has(Number(room.id))
}

function handleRoomImageState(roomId, state = {}) {
  if (state.loading) return
  const id = Number(roomId)
  const next = new Set(failedRoomCoverIds.value)
  if (state.hasImage) next.delete(id)
  else next.add(id)
  failedRoomCoverIds.value = next
}

function defaultRoomForm() {
  return {
    id: null,
    name: '',
    location: '',
    capacity: 12,
    hourlyRate: 100,
    facilities: '',
    description: '',
    openTime: '08:00:00',
    closeTime: '22:00:00',
    imageFileIds: [],
    status: 'AVAILABLE',
    remark: ''
  }
}

function defaultRateForm() {
  return {
    chargeType: 'HOURLY',
    hourlyPrice: 100,
    minimumMinutes: 60,
    effectiveFrom: new Date().toISOString().slice(0, 10),
    effectiveTo: '',
    remark: ''
  }
}

function queryRoomId(value) {
  const roomId = Number(value)
  return Number.isFinite(roomId) && roomId > 0 ? roomId : null
}

async function syncUrl() {
  const tab = ['rooms', 'bookings'].includes(String(route.query.tab || ''))
    ? String(route.query.tab)
    : 'bookings'
  const query = { ...route.query, tab }
  if (selectedRoomId.value) query.roomId = String(selectedRoomId.value)
  else delete query.roomId
  await router.replace({ path: '/assets/meetings', query })
}

async function selectRoom(roomId) {
  selectedRoomId.value = queryRoomId(roomId)
  await syncUrl()
}

async function load() {
  const requestId = requestGuard.start()
  loading.value = true
  loadError.value = ''
  failedRoomCoverIds.value = new Set()
  const [roomResult, bookingResult] = await Promise.allSettled([
    canViewMeetingRooms.value ? request.get('/meeting-rooms', { silent: true }) : Promise.resolve([]),
    canViewBookings.value ? request.get('/meeting-bookings', { silent: true }) : Promise.resolve([])
  ])
  if (!pageActive || !requestGuard.isCurrent(requestId)) return

  const errors = []
  if (roomResult.status === 'fulfilled') rooms.value = readPage(roomResult.value).records
  else {
    rooms.value = []
    errors.push(roomResult.reason?.response?.data?.message || '会议室加载失败')
  }
  if (bookingResult.status === 'fulfilled') bookings.value = readPage(bookingResult.value).records
  else {
    bookings.value = []
    errors.push(bookingResult.reason?.response?.data?.message || '会议预约加载失败')
  }
  loadError.value = errors.join('；')
  if (selectedRoomId.value && !rooms.value.some(room => Number(room.id) === Number(selectedRoomId.value))) {
    selectedRoomId.value = null
    await syncUrl()
  }
  if (pageActive && requestGuard.isCurrent(requestId)) loading.value = false
}

function openRoomDialog(room = null) {
  Object.assign(roomForm, defaultRoomForm(), room ? {
    ...room,
    hourlyRate: Number(room.workdayHourlyRate ?? room.hourlyPrice ?? 0),
    imageFileIds: normalizeMeetingRoomImages(room.imageFileIds)
  } : {})
  roomFormRef.value?.clearValidate()
  roomDialogVisible.value = true
}

async function saveRoom() {
  await roomFormRef.value?.validate()
  savingRoom.value = true
  try {
    const payload = { ...roomForm, workdayHourlyRate: roomForm.hourlyRate }
    delete payload.id
    delete payload.hourlyRate
    if (roomForm.id) await request.put(`/meeting-rooms/${roomForm.id}`, payload)
    else await request.post('/meeting-rooms', payload)
    ElMessage.success(roomForm.id ? '会议室已更新' : '会议室已新增')
    roomDialogVisible.value = false
    await load()
  } finally {
    savingRoom.value = false
  }
}

async function toggleRoom(room) {
  const nextStatus = isMeetingRoomAvailable(room) ? 'DISABLED' : 'AVAILABLE'
  await request.put(`/meeting-rooms/${room.id}`, { ...room, status: nextStatus })
  ElMessage.success(nextStatus === 'AVAILABLE' ? '会议室已启用' : '会议室已停用')
  await load()
}

async function deleteRoom(room) {
  await ElMessageBox.confirm(`确认删除会议室“${room.name}”吗？`, '删除确认', { type: 'warning' })
  await request.delete(`/meeting-rooms/${room.id}`)
  ElMessage.success('会议室已删除')
  await load()
}

function handleRoomCommand(command, room) {
  if (command === 'edit') openRoomDialog(room)
  else if (command === 'rate') openRateRules(room)
  else if (command === 'toggle') toggleRoom(room)
  else if (command === 'delete') deleteRoom(room)
}

async function openRateRules(room) {
  rateRoomId.value = room.id
  rateDrawerVisible.value = true
  rateLoading.value = true
  Object.assign(rateForm, defaultRateForm(), {
    hourlyPrice: Number(room.workdayHourlyRate || 0)
  })
  try {
    rateRules.value = await request.get(`/meeting-rooms/${room.id}/rate-rules`, { silent: true })
  } finally {
    rateLoading.value = false
  }
}

async function saveRateRule() {
  if (!rateRoomId.value || !rateForm.effectiveFrom) {
    ElMessage.warning('请选择生效日期')
    return
  }
  savingRate.value = true
  try {
    const payload = {
      ...rateForm,
      hourlyPrice: rateForm.chargeType === 'FREE' ? 0 : rateForm.hourlyPrice,
      effectiveTo: rateForm.effectiveTo || null
    }
    await request.post(`/meeting-rooms/${rateRoomId.value}/rate-rules/replace`, payload)
    ElMessage.success('收费规则已保存，历史预约快照不受影响')
    rateRules.value = await request.get(`/meeting-rooms/${rateRoomId.value}/rate-rules`, { silent: true })
  } finally {
    savingRate.value = false
  }
}

function statusText(status) {
  return { ACTIVE: '可预约', AVAILABLE: '可预约', ENABLED: '可预约', MAINTENANCE: '维护中', DISABLED: '停用' }[status] || status || '未配置'
}

function statusClass(status) {
  return isMeetingRoomAvailable(status) ? 'is-available' : 'is-unavailable'
}

onMounted(async () => {
  selectedRoomId.value = queryRoomId(route.query.roomId)
  await load()
  await syncUrl()
})

onBeforeUnmount(() => {
  pageActive = false
  requestGuard.dispose()
})
</script>

<style scoped>
.asset-page {
  min-width: 0;
  padding: 16px;
}

.asset-page__header,
.asset-page__header-actions,
.room-panel__heading,
.booking-panel__context {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.asset-page__header {
  align-items: flex-start;
  margin-bottom: 12px;
}

.asset-page h1,
.asset-page h2 {
  margin: 0;
  letter-spacing: 0;
}

.asset-page h1 {
  font-size: 20px;
  line-height: 28px;
}

.asset-page h2 {
  font-size: 14px;
}

.asset-page__header p,
.room-panel__heading span,
.booking-panel__context span {
  margin: 3px 0 0;
  color: #909399;
  font-size: 12px;
}

.room-panel,
.booking-panel {
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  background: #fff;
}

.time-range {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
}

.form-tip {
  margin-top: 6px;
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
}

.rate-table {
  margin-top: 16px;
}

.room-panel {
  margin-bottom: 12px;
  padding: 12px;
}

.asset-page__error {
  margin-bottom: 12px;
}

.room-search {
  width: min(280px, 36vw);
}

.room-grid {
  display: grid;
  min-height: 138px;
  grid-auto-columns: minmax(340px, 1fr);
  grid-auto-flow: column;
  gap: 8px;
  margin-top: 10px;
  overflow-x: auto;
  padding-bottom: 4px;
}

.room-card {
  position: relative;
  display: grid;
  min-width: 340px;
  min-height: 128px;
  grid-template-columns: minmax(0, 1fr) 118px;
  gap: 10px;
  padding: 9px;
  border: 1px solid #e4e7ed;
  border-radius: 5px;
  background: #fff;
  color: inherit;
  transition: border-color 0.18s ease, box-shadow 0.18s ease, background-color 0.18s ease;
}

.room-card:hover {
  border-color: #c9cdd4;
  box-shadow: 0 3px 10px rgb(31 35 41 / 7%);
}

.room-card__select:focus-visible,
.room-card__all-select:focus-visible {
  outline: 2px solid var(--el-color-primary-light-3);
  outline-offset: 2px;
}

.room-card.is-active {
  border-color: var(--el-color-primary);
  background: #fff2f0;
  box-shadow: inset 3px 0 0 var(--el-color-primary);
}

.room-card__select,
.room-card__all-select {
  min-width: 0;
  padding: 0;
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
  font: inherit;
  text-align: left;
}

.room-card__select {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  justify-content: flex-start;
  padding: 2px 0 2px 2px;
}

.room-card__title-row {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 6px;
  padding-right: 2px;
}

.room-card__title-row strong {
  min-width: 0;
  overflow: hidden;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.room-card__select > small {
  display: block;
  margin-top: 4px;
  overflow: hidden;
  color: #909399;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.room-card__detail-line {
  display: grid;
  min-width: 0;
  grid-template-columns: 28px minmax(0, 1fr);
  gap: 5px;
  margin-top: 5px;
  color: #606266;
  font-size: 10px;
  line-height: 1.25;
}

.room-card__detail-line > span:first-child {
  color: #909399;
}

.room-card__detail-line > span:last-child {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.room-card__summary {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 3px 8px;
  margin-top: auto;
  padding-top: 7px;
  color: #606266;
  font-size: 11px;
}

.room-card__media {
  min-width: 0;
  height: 108px;
  overflow: hidden;
  border: 1px solid #ebeef5;
  border-radius: 5px;
  background: #f7f8fa;
  cursor: pointer;
}

.room-card__media:focus-visible {
  outline: 2px solid var(--el-color-primary-light-3);
  outline-offset: 2px;
}

.room-card__image,
.room-card__image :deep(.image-value) {
  width: 100%;
  height: 100%;
}

.room-card__image :deep(.image-value) {
  display: block;
}

.room-card__image :deep(.thumbnail) {
  display: block;
  width: 100%;
  height: 100%;
  border: 0;
  border-radius: 0;
}

.room-card__fallback {
  display: flex;
  box-sizing: border-box;
  height: 100%;
  flex-direction: column;
  gap: 4px;
  padding: 8px;
  color: #606266;
  font-size: 10px;
  line-height: 1.35;
}

.room-card__fallback strong {
  color: #303133;
  font-size: 11px;
}

.room-card__fallback > span {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 1;
}

.room-card__fallback small {
  margin-top: auto;
  color: #909399;
  font-size: 10px;
}

.room-card__menu {
  position: absolute;
  z-index: 2;
  top: 7px;
  right: 7px;
}

.room-card__menu :deep(.room-card__more.el-button) {
  width: 26px;
  height: 26px;
  min-height: 26px;
  padding: 0;
  border: 1px solid rgb(255 255 255 / 36%);
  background: rgb(31 35 41 / 66%);
  box-shadow: 0 1px 4px rgb(31 35 41 / 22%);
  color: #fff;
  backdrop-filter: blur(4px);
}

.room-card__menu :deep(.room-card__more.el-button:hover),
.room-card__menu :deep(.room-card__more.el-button:focus-visible),
.room-card__menu :deep(.room-card__more.el-button[aria-expanded='true']) {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary);
  color: #fff;
}

.room-card--all {
  display: block;
}

.room-card__all-select {
  display: grid;
  width: 100%;
  min-height: 108px;
  grid-template-columns: 36px minmax(0, 1fr) 92px;
  align-items: start;
  gap: 8px;
}

.room-card__icon {
  display: grid;
  width: 34px;
  height: 34px;
  border-radius: 5px;
  background: #f2f3f5;
  color: #606266;
  place-items: center;
}

.room-card__content,
.room-card__content strong,
.room-card__content small {
  display: block;
  min-width: 0;
}

.room-card__content strong,
.room-card__content small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.room-card__content strong {
  font-size: 13px;
}

.room-card__content small {
  margin-top: 2px;
  color: #909399;
  font-size: 11px;
}

.room-card__status {
  display: inline-block;
  flex: none;
  padding: 1px 6px;
  border-radius: 3px;
  font-size: 11px;
}

.room-card__status.is-available {
  background: #f0f9eb;
  color: #529b2e;
}

.room-card__status.is-unavailable {
  background: #fdf6ec;
  color: #b88230;
}

.room-card--all .room-card__content > span {
  display: block;
  margin-top: 10px;
  color: #606266;
  font-size: 12px;
}

.room-card__all-stats {
  display: flex;
  height: 100%;
  flex-direction: column;
  align-items: flex-end;
  justify-content: center;
  padding-right: 4px;
  color: #606266;
}

.room-card__all-stats strong {
  color: #303133;
  font-size: 22px;
  line-height: 1;
}

.room-card__all-stats small,
.room-card__all-stats span {
  margin-top: 4px;
  color: #909399;
  font-size: 10px;
}

.booking-panel {
  overflow: hidden;
}

.booking-panel__context {
  justify-content: flex-start;
  min-height: 46px;
  padding: 0 12px;
  border-bottom: 1px solid #e4e7ed;
}

@media (max-width: 1024px) {
  .asset-page {
    padding: 12px;
  }

  .asset-page__header,
  .room-panel__heading {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .room-search {
    width: 100%;
  }

  .room-grid {
    grid-auto-columns: minmax(320px, 84vw);
  }
}

@media (max-width: 640px) {
  .room-grid {
    grid-auto-columns: minmax(292px, calc(100vw - 56px));
  }

  .room-card {
    min-width: 0;
    grid-template-columns: minmax(0, 1fr) 102px;
  }

  .room-card__all-select {
    grid-template-columns: 34px minmax(0, 1fr) 74px;
  }

  .room-card__media {
    height: 104px;
  }
}

@media (max-width: 420px) {
  .room-card {
    grid-template-columns: minmax(0, 1fr);
  }

  .room-card__media {
    height: 88px;
  }

  .room-card__menu {
    top: auto;
    right: 5px;
    bottom: 94px;
  }
}
</style>
