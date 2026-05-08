<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>会议预约</span>
          <el-button type="primary" @click="openDialog">新增预约</el-button>
        </div>
      </template>

      <el-table :data="bookings" border>
        <el-table-column prop="bookingNumber" label="预约单号" width="160" />
        <el-table-column prop="meetingRoomName" label="会议室" min-width="120" />
        <el-table-column label="申请人" min-width="120">
          <template #default="{ row }">
            {{ applicantText(row) }}
          </template>
        </el-table-column>
        <el-table-column prop="departmentName" label="部门" width="120" />
        <el-table-column label="时间" min-width="240">
          <template #default="{ row }">
            {{ formatTime(row.startTime) }} - {{ formatTime(row.endTime) }}
          </template>
        </el-table-column>
        <el-table-column label="计费" width="120">
          <template #default="{ row }">{{ billingModeText(row.billingMode) }}</template>
        </el-table-column>
        <el-table-column prop="calculatedAmount" label="金额" width="100" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="billId" label="账单ID" width="90" />
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" :disabled="row.status === 'CONFIRMED'" @click="confirm(row)">
              确认
            </el-button>
            <el-button size="small" type="danger" :disabled="row.status === 'CANCELLED'" @click="cancel(row)">
              取消
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="visible" title="新增会议预约" width="620px">
      <el-form :model="form" label-width="110px">
        <el-form-item label="申请人类型">
          <el-radio-group v-model="form.applicantType" @change="onApplicantTypeChange">
            <el-radio-button label="INTERNAL">内部人员</el-radio-button>
            <el-radio-button label="TENANT">租户客户</el-radio-button>
            <el-radio-button label="EXTERNAL">外部客户</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <template v-if="form.applicantType === 'INTERNAL'">
          <el-form-item label="预约人">
            <el-select v-model="form.internalUserId" filterable style="width:100%">
              <el-option
                v-for="user in internalUsers"
                :key="user.id"
                :label="internalUserLabel(user)"
                :value="user.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="部门">
            <el-select
              v-model="form.departmentName"
              filterable
              allow-create
              default-first-option
              style="width:100%"
              placeholder="请选择或输入部门"
            >
              <el-option
                v-for="department in departmentOptions"
                :key="department"
                :label="department"
                :value="department"
              />
            </el-select>
          </el-form-item>
        </template>

        <el-form-item v-if="form.applicantType === 'TENANT'" label="租户">
          <el-select v-model="form.tenantId" filterable style="width:100%">
            <el-option
              v-for="tenant in tenants"
              :key="tenant.id"
              :label="tenant.tenantName"
              :value="tenant.id"
            />
          </el-select>
        </el-form-item>

        <template v-if="form.applicantType !== 'INTERNAL'">
          <el-form-item label="申请人">
            <el-input v-model="form.applicantName" />
          </el-form-item>
          <el-form-item label="联系电话">
            <el-input v-model="form.contactPhone" />
          </el-form-item>
        </template>

        <el-form-item label="开始日期">
          <el-date-picker
            v-model="form.startDate"
            type="date"
            value-format="YYYY-MM-DD"
            format="YYYY年MM月DD日"
            placeholder="请选择开始日期"
            style="width:100%"
            @change="onStartTimeChange"
          />
        </el-form-item>
        <el-form-item label="开始时间">
          <el-time-select
            v-model="form.startClock"
            start="08:00"
            step="00:15"
            end="22:00"
            placeholder="请选择开始时间"
            style="width:100%"
            @change="onStartTimeChange"
          />
        </el-form-item>
        <el-form-item label="结束日期">
          <el-date-picker
            v-model="form.endDate"
            type="date"
            value-format="YYYY-MM-DD"
            format="YYYY年MM月DD日"
            placeholder="请选择结束日期"
            :disabled-date="disableEndDate"
            style="width:100%"
            @change="onEndTimeChange"
          />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-time-select
            v-model="form.endClock"
            start="08:00"
            step="00:15"
            end="22:00"
            :min-time="endMinTime"
            placeholder="请选择结束时间"
            style="width:100%"
            @change="onEndTimeChange"
          />
        </el-form-item>

        <el-form-item label="会议室">
          <el-select
            v-model="form.meetingRoomId"
            style="width:100%"
            :disabled="!hasValidTime"
            placeholder="请先选择预约时间"
          >
            <el-option
              v-for="room in rooms"
              :key="room.id"
              :label="roomOptionLabel(room)"
              :value="room.id"
              :disabled="!canBookRoom(room)"
              :class="{ 'disabled-room-option': !canBookRoom(room) }"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="用途">
          <el-input v-model="form.purpose" type="textarea" :rows="2" placeholder="会议、培训、路演、客户接待等" />
        </el-form-item>

        <el-form-item label="计费方式">
          <el-select v-model="form.billingMode" :disabled="form.applicantType === 'INTERNAL'" style="width:100%">
            <el-option label="内部免费" value="FREE" />
            <el-option label="优惠赠送" value="GIFTED" />
            <el-option label="按小时收费" value="HOURLY" />
          </el-select>
        </el-form-item>

        <el-form-item v-if="form.billingMode === 'HOURLY'" label="折扣">
          <el-input-number v-model="form.discountRate" :min="0" :max="1" :step="0.1" :precision="2" style="width:100%" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="save">保存预约</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../../utils/request'

const rooms = ref([])
const tenants = ref([])
const internalUsers = ref([])
const bookings = ref([])
const visible = ref(false)

const departmentOptions = [
  '总经办',
  '招商运营部',
  '物业服务部',
  '工程部',
  '安保部',
  '财务部',
  '行政人事部'
]

const form = reactive({
  meetingRoomId: null,
  applicantType: 'INTERNAL',
  tenantId: null,
  internalUserId: Number(localStorage.getItem('userId')) || null,
  applicantName: '',
  departmentName: '',
  contactPhone: '',
  startDate: '',
  startClock: '',
  endDate: '',
  endClock: '',
  purpose: '',
  billingMode: 'FREE',
  discountRate: 1
})

const hasValidTime = computed(() => {
  const start = getStartTime()
  const end = getEndTime()
  if (!start || !end) return false
  return new Date(end).getTime() > new Date(start).getTime()
})

const endMinTime = computed(() => {
  if (!form.startDate || !form.endDate || form.startDate !== form.endDate) {
    return '08:00'
  }
  return form.startClock || '08:00'
})

const load = async () => {
  const [roomData, tenantData, internalUserData, bookingData] = await Promise.all([
    request.get('/meetings/rooms'),
    request.get('/tenant/list'),
    request.get('/meetings/bookings/internal-applicants'),
    request.get('/meetings/bookings')
  ])
  rooms.value = roomData || []
  tenants.value = tenantData?.content || tenantData || []
  internalUsers.value = internalUserData || []
  bookings.value = bookingData || []
}

const openDialog = () => {
  Object.assign(form, {
    meetingRoomId: null,
    applicantType: 'INTERNAL',
    tenantId: null,
    internalUserId: Number(localStorage.getItem('userId')) || internalUsers.value[0]?.id || null,
    applicantName: '',
    departmentName: '',
    contactPhone: '',
    startDate: '',
    startClock: '',
    endDate: '',
    endClock: '',
    purpose: '',
    billingMode: 'FREE',
    discountRate: 1
  })
  visible.value = true
}

const getStartTime = () => {
  if (!form.startDate || !form.startClock) return ''
  return `${form.startDate}T${form.startClock}:00`
}

const getEndTime = () => {
  if (!form.endDate || !form.endClock) return ''
  return `${form.endDate}T${form.endClock}:00`
}

const addMinutes = (dateTimeText, minutes) => {
  const date = new Date(dateTimeText)
  date.setMinutes(date.getMinutes() + minutes)
  const pad = (value) => String(value).padStart(2, '0')
  return {
    date: `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`,
    clock: `${pad(date.getHours())}:${pad(date.getMinutes())}`
  }
}

const syncDefaultEndTime = () => {
  if (!form.startDate) return

  if (!form.endDate || form.endDate < form.startDate) {
    form.endDate = form.startDate
  }

  if (!form.startClock) return

  if (!form.endClock || !hasValidTime.value) {
    const next = addMinutes(getStartTime(), 60)
    form.endDate = next.date
    form.endClock = next.clock
  }
}

const onStartTimeChange = async () => {
  syncDefaultEndTime()
  await loadRoomAvailability()
}

const onEndTimeChange = async () => {
  if (form.startDate && form.endDate && form.endDate < form.startDate) {
    form.endDate = form.startDate
  }

  if (getStartTime() && getEndTime() && !hasValidTime.value) {
    const next = addMinutes(getStartTime(), 60)
    form.endDate = next.date
    form.endClock = next.clock
  }

  await loadRoomAvailability()
}

const disableEndDate = (date) => {
  if (!form.startDate) return false
  const start = new Date(`${form.startDate}T00:00:00`)
  return date.getTime() < start.getTime()
}

const loadRoomAvailability = async () => {
  form.meetingRoomId = null

  if (!hasValidTime.value) {
    rooms.value = await request.get('/meetings/rooms')
    return
  }

  rooms.value = await request.get('/meetings/rooms/availability', {
    params: {
      startTime: getStartTime(),
      endTime: getEndTime()
    }
  })

  const firstBookableRoom = rooms.value.find(canBookRoom)
  form.meetingRoomId = firstBookableRoom?.id || null
}

const canBookRoom = (room) => room.available !== false && room.status === 'ACTIVE'

const roomOptionLabel = (room) => {
  const status = canBookRoom(room) ? '可预约' : (room.unavailableReason || '不可预约')
  const location = room.location || '未填写位置'
  return `${room.roomName}（${location}，${status}）`
}

const onApplicantTypeChange = () => {
  form.billingMode = form.applicantType === 'INTERNAL' ? 'FREE' : 'HOURLY'
}

const save = async () => {
  if (!form.meetingRoomId || !hasValidTime.value) {
    ElMessage.warning('请选择会议室和预约时间')
    return
  }

  if (form.applicantType === 'TENANT' && !form.tenantId) {
    ElMessage.warning('请选择租户')
    return
  }

  if (form.applicantType === 'INTERNAL' && (!form.internalUserId || !form.departmentName)) {
    ElMessage.warning('请选择预约人并填写部门')
    return
  }

  if (form.applicantType === 'EXTERNAL' && !form.applicantName) {
    ElMessage.warning('请输入外部客户名称')
    return
  }

  await request.post('/meetings/bookings', {
    ...form,
    startTime: getStartTime(),
    endTime: getEndTime()
  })
  ElMessage.success('预约已创建')
  visible.value = false
  load()
}

const confirm = async (row) => {
  await request.patch(`/meetings/bookings/${row.id}/confirm`)
  ElMessage.success(row.calculatedAmount > 0 ? '已确认并生成账单' : '已确认')
  load()
}

const cancel = async (row) => {
  await request.patch(`/meetings/bookings/${row.id}/cancel`)
  ElMessage.success('已取消')
  load()
}

const applicantText = (row) => {
  if (row.applicantType === 'INTERNAL') return `内部：${row.applicantName || row.internalUserId}`
  if (row.applicantType === 'TENANT') return `租户：${row.applicantName || row.tenantId}`
  return `外部：${row.applicantName || '-'}`
}

const internalUserLabel = (user) => {
  const name = user.realName || user.username
  return user.phone ? `${name}（${user.phone}）` : name
}

const billingModeText = (value) => ({
  FREE: '内部免费',
  GIFTED: '优惠赠送',
  HOURLY: '按小时收费'
}[value] || value)

const statusText = (value) => ({
  BOOKED: '已预约',
  CONFIRMED: '已确认',
  CANCELLED: '已取消'
}[value] || value)

const statusType = (value) => ({
  BOOKED: 'warning',
  CONFIRMED: 'success',
  CANCELLED: 'info'
}[value] || '')

const formatTime = (value) => value ? value.replace('T', ' ').slice(0, 16) : '-'

onMounted(load)
</script>

<style scoped>
.page-container {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

:deep(.disabled-room-option) {
  color: #a8abb2;
}
</style>
