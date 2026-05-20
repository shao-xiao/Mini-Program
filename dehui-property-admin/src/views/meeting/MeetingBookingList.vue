<template>
  <div class="page-container">
    <el-row :gutter="16" class="stats-row">
      <el-col :span="6">
        <el-card><div class="stat-label">今日预约</div><div class="stat-value">{{ stats.todayBookingCount || 0 }}</div></el-card>
      </el-col>
      <el-col :span="6">
        <el-card><div class="stat-label">本月预约</div><div class="stat-value">{{ stats.monthBookingCount || 0 }}</div></el-card>
      </el-col>
      <el-col :span="6">
        <el-card><div class="stat-label">本月收入</div><div class="stat-value">¥ {{ money(stats.monthRevenue) }}</div></el-card>
      </el-col>
      <el-col :span="6">
        <el-card><div class="stat-label">取消率</div><div class="stat-value">{{ stats.cancelRate || 0 }}%</div></el-card>
      </el-col>
    </el-row>

    <el-card>
      <template #header>
        <div class="card-header">
          <span>会议预约</span>
          <el-button type="primary" @click="openDialog">新增预约</el-button>
        </div>
      </template>

      <el-form :inline="true" :model="query" class="filter-bar">
        <el-form-item label="会议室">
          <el-select v-model="query.roomId" clearable placeholder="全部会议室" style="width:180px">
            <el-option v-for="room in rooms" :key="room.id" :label="room.name" :value="room.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源">
          <el-select v-model="query.sourceType" clearable placeholder="全部来源" style="width:140px">
            <el-option label="内部员工" value="INTERNAL" />
            <el-option label="租户" value="TENANT" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部状态" style="width:140px">
            <el-option label="已预约" value="PENDING" />
            <el-option label="已确认" value="CONFIRMED" />
            <el-option label="已取消" value="CANCELLED" />
            <el-option label="已完成" value="COMPLETED" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期">
          <el-date-picker v-model="query.dateRange" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" />
        </el-form-item>
        <el-form-item label="申请人">
          <el-input v-model="query.applicantName" clearable placeholder="申请人" style="width:150px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadBookings">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="bookings" border>
        <el-table-column prop="bookingNo" label="预约单号" width="170" />
        <el-table-column prop="roomName" label="会议室" min-width="120" />
        <el-table-column prop="applicantName" label="申请人" min-width="120" />
        <el-table-column label="来源类型" width="110">
          <template #default="{ row }">
            <el-tag :type="row.sourceType === 'INTERNAL' ? 'primary' : 'success'">{{ sourceTypeText(row.sourceType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="department" label="部门" width="120" />
        <el-table-column label="时间" min-width="240">
          <template #default="{ row }">{{ formatTime(row.startTime) }} - {{ formatTime(row.endTime) }}</template>
        </el-table-column>
        <el-table-column label="计费" width="120">
          <template #default="{ row }">{{ feeTypeText(row.feeType) }}</template>
        </el-table-column>
        <el-table-column label="金额" width="110">
          <template #default="{ row }">¥ {{ money(row.amount) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="billingId" label="账单ID" width="90" />
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'PENDING'" size="small" type="primary" @click="confirm(row)">确认</el-button>
            <el-button v-if="row.status === 'PENDING' || (row.status === 'CONFIRMED' && !row.billPaid)" size="small" type="danger" @click="cancel(row)">取消</el-button>
            <el-button v-if="row.status === 'CONFIRMED'" size="small" type="success" @click="complete(row)">完成</el-button>
            <el-button v-if="row.billingId" size="small" @click="viewBill(row)">查看账单</el-button>
            <el-button size="small" @click="view(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="visible" title="新增会议预约" width="680px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="来源类型" prop="sourceType">
          <el-radio-group v-model="form.sourceType" @change="onSourceChange">
            <el-radio-button label="INTERNAL">内部员工</el-radio-button>
            <el-radio-button label="TENANT">租户</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="申请人" prop="applicantName">
          <el-input v-model="form.applicantName" />
        </el-form-item>
        <el-form-item label="部门">
          <el-input v-model="form.department" />
        </el-form-item>
        <el-form-item v-if="form.sourceType === 'TENANT'" label="租户" prop="tenantId">
          <el-select v-model="form.tenantId" filterable style="width:100%" @change="onTenantChange">
            <el-option v-for="tenant in tenants" :key="tenant.id" :label="tenant.tenantName" :value="tenant.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="form.applicantPhone" />
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
        <el-form-item label="计费类型">
          <el-input :model-value="feeTypeText(form.feeType)" readonly />
        </el-form-item>
        <el-form-item label="金额">
          <el-input :model-value="`¥ ${money(form.amount)}`" readonly />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="save">保存预约</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailVisible" title="预约详情" width="560px">
      <el-descriptions v-if="current" :column="1" border>
        <el-descriptions-item label="预约单号">{{ current.bookingNo }}</el-descriptions-item>
        <el-descriptions-item label="会议室">{{ current.roomName }}</el-descriptions-item>
        <el-descriptions-item label="申请人">{{ current.applicantName }}</el-descriptions-item>
        <el-descriptions-item label="时间">{{ formatTime(current.startTime) }} - {{ formatTime(current.endTime) }}</el-descriptions-item>
        <el-descriptions-item label="金额">¥ {{ money(current.amount) }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ statusText(current.status) }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ current.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../utils/request'

const route = useRoute()
const router = useRouter()
const rooms = ref([])
const availableRooms = ref([])
const tenants = ref([])
const bookings = ref([])
const stats = ref({})
const visible = ref(false)
const detailVisible = ref(false)
const current = ref(null)
const formRef = ref()

const query = reactive({
  roomId: route.query.roomId ? Number(route.query.roomId) : null,
  sourceType: '',
  status: '',
  dateRange: [],
  applicantName: ''
})

const form = reactive({
  roomId: null,
  sourceType: 'INTERNAL',
  tenantId: null,
  tenantName: '',
  applicantName: '',
  department: '',
  applicantPhone: '',
  startTime: '',
  endTime: '',
  feeType: 'INTERNAL_FREE',
  amount: 0,
  remark: ''
})

const rules = computed(() => ({
  sourceType: [{ required: true, message: '请选择来源类型', trigger: 'change' }],
  applicantName: [{ required: true, message: '请输入申请人', trigger: 'blur' }],
  tenantId: form.sourceType === 'TENANT' ? [{ required: true, message: '请选择租户', trigger: 'change' }] : [],
  roomId: [{ required: true, message: '请选择会议室', trigger: 'change' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }]
}))

const loadRooms = async () => {
  rooms.value = await request.get('/meeting-rooms')
  availableRooms.value = rooms.value
}

const loadTenants = async () => {
  const data = await request.get('/tenant/list')
  tenants.value = data?.content || data || []
}

const loadStats = async () => {
  stats.value = await request.get('/meeting-bookings/stats')
}

const loadBookings = async () => {
  bookings.value = await request.get('/meeting-bookings', {
    params: {
      roomId: query.roomId || undefined,
      sourceType: query.sourceType || undefined,
      status: query.status || undefined,
      applicantName: query.applicantName || undefined,
      startDate: query.dateRange?.[0] || undefined,
      endDate: query.dateRange?.[1] || undefined
    }
  })
}

const load = async () => {
  await Promise.all([loadRooms(), loadTenants(), loadStats(), loadBookings()])
}

const resetQuery = () => {
  Object.assign(query, { roomId: null, sourceType: '', status: '', dateRange: [], applicantName: '' })
  loadBookings()
}

const openDialog = () => {
  Object.assign(form, {
    roomId: null,
    sourceType: 'INTERNAL',
    tenantId: null,
    tenantName: '',
    applicantName: '',
    department: '',
    applicantPhone: '',
    startTime: '',
    endTime: '',
    feeType: 'INTERNAL_FREE',
    amount: 0,
    remark: ''
  })
  visible.value = true
}

const onSourceChange = () => {
  form.feeType = form.sourceType === 'INTERNAL' ? 'INTERNAL_FREE' : 'HOURLY'
  form.amount = form.sourceType === 'INTERNAL' ? 0 : form.amount
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
  await Promise.all([loadBookings(), loadStats()])
}

const confirm = async (row) => {
  await request.post(`/meeting-bookings/${row.id}/confirm`)
  ElMessage.success(row.amount > 0 ? '已确认并生成账单' : '已确认')
  await Promise.all([loadBookings(), loadStats()])
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
  await Promise.all([loadBookings(), loadStats()])
}

const complete = async (row) => {
  await request.post(`/meeting-bookings/${row.id}/complete`)
  ElMessage.success('已完成')
  await Promise.all([loadBookings(), loadStats()])
}

const viewBill = (row) => {
  router.push('/bills')
  ElMessage.info(`关联账单ID：${row.billingId}`)
}

const view = (row) => {
  current.value = row
  detailVisible.value = true
}

const sourceTypeText = (value) => ({ INTERNAL: '内部员工', TENANT: '租户' }[value] || value)
const feeTypeText = (value) => ({ INTERNAL_FREE: '内部免费', HOURLY: '按小时收费' }[value] || value)
const statusText = (value) => ({ PENDING: '已预约', CONFIRMED: '已确认', CANCELLED: '已取消', COMPLETED: '已完成' }[value] || value)
const statusType = (value) => ({ PENDING: 'warning', CONFIRMED: 'success', CANCELLED: 'info', COMPLETED: 'primary' }[value] || '')
const money = (value) => Number(value || 0).toFixed(2)
const formatTime = (value) => value ? String(value).replace('T', ' ').slice(0, 16) : '-'

onMounted(load)
</script>

<style scoped>
.page-container {
  padding: 20px;
}

.stats-row {
  margin-bottom: 16px;
}

.stat-label {
  color: #7b8494;
  font-size: 13px;
}

.stat-value {
  margin-top: 8px;
  font-size: 22px;
  font-weight: 700;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.filter-bar {
  margin-bottom: 14px;
}
</style>
