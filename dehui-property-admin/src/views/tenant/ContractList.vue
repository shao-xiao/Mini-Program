<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>合同管理</span>
          <div class="header-actions">
            <el-button @click="generateBills">生成到期账单</el-button>
            <el-button type="primary" @click="openCreateDialog">新增合同</el-button>
          </div>
        </div>
      </template>

      <el-table
        :data="contracts"
        border
        stripe
        class="contract-table"
        row-key="id"
        style="width: 100%"
      >
        <el-table-column label="合同信息" min-width="185">
          <template #default="{ row }">
            <div class="primary-text">{{ row.contractNumber || '-' }}</div>
            <div class="secondary-text">{{ row.contractName || '未填写合同名称' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="租户 / 房间" min-width="175">
          <template #default="{ row }">
            <div class="primary-text">{{ tenantDisplayName(row.tenantId) }}</div>
            <div class="secondary-text">{{ roomDisplayName(row.roomId) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="合同周期" width="145">
          <template #default="{ row }">
            <div class="primary-text">{{ row.startDate || '-' }}</div>
            <div class="secondary-text">至 {{ row.endDate || '长期' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="费用" min-width="155">
          <template #default="{ row }">
            <div class="money-line">
              <span>月租</span>
              <strong>{{ formatMoney(row.rentAmount) }}</strong>
            </div>
            <div class="money-line">
              <span>物业</span>
              <strong>{{ formatMoney(row.propertyFeeAmount) }}</strong>
            </div>
            <div class="money-line muted-line">
              <span>押金</span>
              <strong>{{ formatMoney(row.depositAmount) }}</strong>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="规则 / 状态" width="118" align="center">
          <template #default="{ row }">
            <div class="primary-text">{{ paymentMethodText(row.paymentCycle) }}</div>
            <div class="secondary-text">{{ billingRuleText(row) }}</div>
            <div class="status-tag">
              <el-tag :type="contractStatusTag(row.status)" effect="plain">{{ contractStatusText(row.status) }}</el-tag>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="210" align="center">
          <template #default="{ row }">
            <div class="action-buttons">
              <el-button size="small" :disabled="!['DRAFT', 'PENDING'].includes(row.status)" @click="activate(row)">生效</el-button>
              <el-button size="small" :disabled="row.status !== 'ACTIVE'" @click="checkIn(row)">入驻</el-button>
              <el-button size="small" type="danger" :disabled="row.status !== 'ACTIVE'" @click="terminate(row)">终止</el-button>
              <el-button size="small" :disabled="['ACTIVE', 'TERMINATED', 'CANCELLED'].includes(row.status)" @click="cancelContract(row)">作废</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" title="新增合同" width="760px" class="contract-dialog">
      <el-form :model="form" label-position="top">
        <div class="form-section-title">合同信息</div>
        <div class="form-grid">
          <el-form-item label="合同编号">
            <el-input v-model="form.contractNumber" />
          </el-form-item>

          <el-form-item label="合同名称">
            <el-input v-model="form.contractName" />
          </el-form-item>
        </div>

        <div class="form-section-title">租户信息</div>
        <div class="form-grid">
          <el-form-item label="租户名称">
            <el-input v-model="form.tenantName" placeholder="可直接录入新租户名称" />
          </el-form-item>

          <el-form-item label="联系人">
            <el-input v-model="form.contactPerson" />
          </el-form-item>

          <el-form-item label="联系电话">
            <el-input v-model="form.contactPhone" />
          </el-form-item>

          <el-form-item label="邮箱">
            <el-input v-model="form.contactEmail" />
          </el-form-item>
        </div>

        <div class="form-section-title">房间与周期</div>
        <div class="form-grid">
          <el-form-item label="楼层">
            <el-select v-model="form.floorId" filterable placeholder="请选择楼层" style="width:100%" @change="handleFloorChange">
              <el-option
                v-for="floor in floors"
                :key="floor.id"
                :label="floorLabel(floor)"
                :value="floor.id"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="房间">
            <el-select v-model="form.roomId" filterable placeholder="请选择可租房间" style="width:100%">
              <el-option
                v-for="room in availableRooms"
                :key="room.id"
                :label="roomLabel(room)"
                :value="room.id"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="开始日期">
            <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
          </el-form-item>

          <el-form-item label="结束日期">
            <el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
          </el-form-item>
        </div>

        <div class="form-section-title">费用与收款</div>
        <div class="form-grid">
          <el-form-item label="月租金额">
            <el-input-number v-model="form.rentAmount" :min="0" :precision="2" style="width:100%" />
          </el-form-item>

          <el-form-item label="月物业费">
            <el-input-number v-model="form.propertyFeeAmount" :min="0" :precision="2" style="width:100%" />
          </el-form-item>

          <el-form-item label="押金">
            <el-input-number v-model="form.depositAmount" :min="0" :precision="2" style="width:100%" />
          </el-form-item>

          <el-form-item label="付款方式">
            <el-select v-model="form.paymentCycle" style="width:100%">
              <el-option label="全款" value="FULL" />
              <el-option label="月付" value="MONTHLY" />
              <el-option label="季付" value="QUARTERLY" />
              <el-option label="半年付" value="SEMI_ANNUAL" />
              <el-option label="年付" value="YEARLY" />
            </el-select>
          </el-form-item>

          <el-form-item label="提前出账天数">
            <el-input-number
              v-model="form.billingLeadDays"
              :min="0"
              :max="60"
              style="width:100%"
            />
            <div class="form-tip">例如填 7，表示每个账期开始前 7 天自动生成下一期账单。</div>
          </el-form-item>

          <el-form-item label="备注">
            <el-input v-model="form.remark" />
          </el-form-item>
        </div>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="createContract">保存</el-button>
      </template>
    </el-dialog>

  </div>
</template>

<script setup>
import { computed, ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../../utils/request'

const contracts = ref([])
const dialogVisible = ref(false)
const tenants = ref([])
const rooms = ref([])
const floors = ref([])
const BUILDING_ID = 1

const form = reactive({
  contractNumber: '',
  contractName: '',
  tenantId: '',
  tenantName: '',
  contactPerson: '',
  contactPhone: '',
  contactEmail: '',
  floorId: '',
  roomId: '',
  leaseId: '',
  startDate: '',
  endDate: '',
  rentAmount: 0,
  propertyFeeAmount: 0,
  depositAmount: 0,
  paymentCycle: 'MONTHLY',
  billingLeadDays: 7,
  billingRule: '',
  remark: ''
})

const resetForm = () => {
  form.contractNumber = ''
  form.contractName = ''
  form.tenantId = ''
  form.tenantName = ''
  form.contactPerson = ''
  form.contactPhone = ''
  form.contactEmail = ''
  form.floorId = ''
  form.roomId = ''
  form.leaseId = ''
  form.startDate = ''
  form.endDate = ''
  form.rentAmount = 0
  form.propertyFeeAmount = 0
  form.depositAmount = 0
  form.paymentCycle = 'MONTHLY'
  form.billingLeadDays = 7
  form.billingRule = ''
  form.remark = ''
}

const loadContracts = async () => {
  const data = await request.get('/contracts')
  contracts.value = data || []
}

const availableRooms = computed(() => {
  return rooms.value.filter(item => {
    const sameFloor = form.floorId ? item.floorId === form.floorId : true
    return sameFloor && ['AVAILABLE', 'VACANT'].includes(item.status)
  })
})

function tenantLabel(tenant) {
  return `${tenant.tenantName}（ID:${tenant.id}）`
}

function tenantName(tenantId) {
  const tenant = tenants.value.find(item => item.id === tenantId)
  return tenant ? `${tenant.tenantName}（ID:${tenant.id}）` : `租户ID:${tenantId || '-'}`
}

function tenantDisplayName(tenantId) {
  const tenant = tenants.value.find(item => item.id === tenantId)
  return tenant ? tenant.tenantName : `租户ID:${tenantId || '-'}`
}

function roomLabel(room) {
  const areaText = room.area ? `｜${room.area}㎡` : ''
  return `${room.roomNumber || room.roomName || '未命名房间'}${areaText}（ID:${room.id}）`
}

function roomName(roomId) {
  if (!roomId) return ''
  const room = rooms.value.find(item => item.id === roomId)
  return room ? roomLabel(room) : `房间ID:${roomId}`
}

function roomDisplayName(roomId) {
  if (!roomId) return '-'
  const room = rooms.value.find(item => item.id === roomId)
  if (!room) return `房间ID:${roomId}`

  const areaText = room.area ? ` / ${room.area}㎡` : ''
  return `${room.roomNumber || room.roomName || '未命名房间'}${areaText}`
}

function formatMoney(value) {
  const number = Number(value || 0)
  if (!number) return '-'
  return number.toLocaleString('zh-CN', {
    minimumFractionDigits: 0,
    maximumFractionDigits: 2
  })
}

function paymentMethodText(value) {
  const cycleMap = {
    FULL: '全款',
    MONTHLY: '月付',
    QUARTERLY: '季付',
    SEMI_ANNUAL: '半年付',
    YEARLY: '年付'
  }
  return cycleMap[value] || value || '-'
}

function contractStatusText(status) {
  const map = {
    DRAFT: '草稿',
    PENDING: '待生效',
    ACTIVE: '履约中',
    EXPIRED: '已到期',
    TERMINATED: '已终止',
    CANCELLED: '已作废'
  }
  return map[status] || status || '-'
}

function contractStatusTag(status) {
  const map = {
    DRAFT: 'info',
    PENDING: 'warning',
    ACTIVE: 'success',
    EXPIRED: 'info',
    TERMINATED: 'danger',
    CANCELLED: 'info'
  }
  return map[status] || ''
}

function billingRuleText(row) {
  if (row.billingLeadDays !== null && row.billingLeadDays !== undefined) {
    return `${row.billingLeadDays} 天`
  }

  return '7 天'
}

async function loadTenants() {
  const data = await request.get('/tenant/list')
  tenants.value = data || []
}

async function loadRooms() {
  const floorsData = await request.get(`/buildings/${BUILDING_ID}/floors`)
  floors.value = floorsData.content || floorsData || []
  const result = []

  for (const floor of floors.value) {
    const data = await request.get(`/buildings/${BUILDING_ID}/floors/${floor.id}/rooms`)
    result.push(...(data.content || data || []))
  }

  rooms.value = result
}

const openCreateDialog = () => {
  resetForm()
  dialogVisible.value = true
}

function handleFloorChange() {
  form.roomId = ''
}

function floorLabel(floor) {
  return floor.floorName || floor.floorNumber || `${floor.floorNo || floor.id}层`
}

const createContract = async () => {
  if (!form.tenantName || !form.roomId) {
    ElMessage.warning('请输入租户名称并选择房间')
    return
  }

  const payload = {
    contractNumber: form.contractNumber,
    contractName: form.contractName,
    tenantName: form.tenantName,
    contactPerson: form.contactPerson,
    contactPhone: form.contactPhone,
    contactEmail: form.contactEmail,
    roomId: Number(form.roomId),
    startDate: form.startDate,
    endDate: form.endDate,
    rentAmount: form.rentAmount,
    propertyFeeAmount: form.propertyFeeAmount,
    depositAmount: form.depositAmount,
    paymentCycle: form.paymentCycle,
    billingLeadDays: form.billingLeadDays,
    remark: form.remark
  }

  await request.post('/contracts', payload)
  ElMessage.success('创建成功')
  dialogVisible.value = false
  resetForm()
  await Promise.all([loadContracts(), loadTenants()])
}

const activate = async (row) => {
  await request.post(`/contracts/${row.id}/activate`)
  ElMessage.success('已生效')
  loadContracts()
}

const checkIn = async (row) => {
  await request.post(`/contracts/${row.id}/check-in`, { remark: '后台合同列表办理入驻' })
  ElMessage.success('入住办理成功')
  await Promise.all([loadContracts(), loadRooms()])
}

const terminate = async (row) => {
  await request.post(`/contracts/${row.id}/terminate`, { reason: '后台终止合同' })
  ElMessage.success('已终止')
  await Promise.all([loadContracts(), loadRooms()])
}

const cancelContract = async (row) => {
  await request.post(`/contracts/${row.id}/cancel`, { reason: '后台作废合同' })
  ElMessage.success('已作废')
  await Promise.all([loadContracts(), loadRooms()])
}

const generateBills = async () => {
  const result = await request.post('/contracts/generate-bills')
  ElMessage.success(`已生成 ${result?.generated || 0} 张，跳过 ${result?.skipped || 0} 张`)
}

onMounted(async () => {
  await Promise.all([
    loadContracts(),
    loadTenants(),
    loadRooms()
  ])
})
</script>

<style scoped>
.page-container {
  padding: 20px;
}

.page-container :deep(.el-card__body) {
  padding: 22px 24px 28px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  min-height: 36px;
  font-weight: 600;
}

.header-actions {
  display: flex;
  gap: 10px;
}

.contract-table {
  font-size: 14px;
}

.contract-table :deep(.el-table__header th) {
  height: 52px;
  background: #f7f8fa;
  color: #303133;
  font-weight: 600;
}

.contract-table :deep(.el-table__row td) {
  padding: 14px 0;
  vertical-align: middle;
}

.primary-text {
  color: #1f2937;
  font-weight: 600;
  line-height: 22px;
  word-break: break-word;
}

.secondary-text {
  margin-top: 4px;
  color: #6b7280;
  font-size: 13px;
  line-height: 20px;
  word-break: break-word;
}

.money-line {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  line-height: 22px;
  white-space: nowrap;
}

.money-line span {
  color: #909399;
}

.money-line strong {
  min-width: 66px;
  color: #1f2937;
  font-weight: 600;
  text-align: right;
}

.muted-line strong {
  color: #4b5563;
}

.action-buttons {
  display: flex;
  justify-content: center;
  gap: 6px;
}

.action-buttons :deep(.el-button + .el-button) {
  margin-left: 0;
}

.status-tag {
  margin-top: 8px;
}

.contract-dialog :deep(.el-dialog__body) {
  padding: 8px 28px 4px;
  max-height: 68vh;
  overflow-y: auto;
}

.contract-dialog :deep(.el-dialog__footer) {
  padding: 14px 28px 22px;
}

.form-section-title {
  margin: 18px 0 12px;
  padding-left: 10px;
  border-left: 3px solid #d93025;
  color: #303133;
  font-size: 15px;
  font-weight: 600;
  line-height: 18px;
}

.form-section-title:first-child {
  margin-top: 4px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 18px;
  row-gap: 2px;
}

.form-grid :deep(.el-form-item) {
  margin-bottom: 16px;
}

.form-grid :deep(.el-form-item__label) {
  padding-bottom: 6px;
  color: #606266;
  font-weight: 500;
}

.form-tip {
  width: 100%;
  margin-top: 6px;
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
}

@media (max-width: 900px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
