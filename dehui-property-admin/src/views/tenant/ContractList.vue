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

      <el-table :data="contracts" border stripe class="contract-table" row-key="id" style="width: 100%">
        <el-table-column label="合同信息" min-width="185">
          <template #default="{ row }">
            <div class="primary-text">{{ row.contractNumber || row.code || '-' }}</div>
            <div class="secondary-text">{{ row.contractName || '未填写合同名称' }}</div>
          </template>
        </el-table-column>

        <el-table-column label="租户 / 房间" min-width="185">
          <template #default="{ row }">
            <div class="primary-text">{{ row.tenantName || tenantDisplayName(row.tenantId) }}</div>
            <div class="secondary-text">{{ row.roomName || roomDisplayName(row.roomId) }}</div>
          </template>
        </el-table-column>

        <el-table-column label="联系方式" min-width="190">
          <template #default="{ row }">
            <div class="primary-text">{{ row.contactPerson || '-' }}</div>
            <div class="secondary-text" :class="{ 'invalid-contact': row.contactPhone && !isValidContactPhone(row.contactPhone) }">
              {{ contactText(row.contactPhone, 'phone') }}
            </div>
            <div class="secondary-text" :class="{ 'invalid-contact': row.contactEmail && !isValidEmail(row.contactEmail) }">
              {{ contactText(row.contactEmail, 'email') }}
            </div>
          </template>
        </el-table-column>

        <el-table-column label="合同周期" width="145">
          <template #default="{ row }">
            <div class="primary-text">{{ row.startDate || '-' }}</div>
            <div class="secondary-text">至 {{ row.endDate || '长期' }}</div>
          </template>
        </el-table-column>

        <el-table-column label="费用" min-width="150">
          <template #default="{ row }">
            <div class="money-line">
              <span>月租</span>
              <strong>{{ formatMoney(row.rentAmount) }}</strong>
            </div>
            <div class="money-line muted-line">
              <span>押金</span>
              <strong>{{ formatMoney(row.depositAmount) }}</strong>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="105" align="center">
          <template #default="{ row }">
            <el-tag :type="contractStatusTag(row.status)" effect="plain">{{ contractStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="290" align="center" fixed="right">
          <template #default="{ row }">
            <div class="action-buttons">
              <el-button size="small" @click="openEditDialog(row)">编辑</el-button>
              <el-button size="small" :disabled="!['DRAFT', 'PENDING'].includes(row.status)" @click="activate(row)">生效</el-button>
              <el-button size="small" :disabled="row.status !== 'ACTIVE'" @click="checkIn(row)">入驻</el-button>
              <el-button size="small" :disabled="!['TERMINATED', 'CANCELLED'].includes(row.status)" @click="reactivate(row)">恢复</el-button>
              <el-button size="small" type="danger" :disabled="row.status !== 'ACTIVE'" @click="terminate(row)">终止</el-button>
              <el-button size="small" :disabled="!['DRAFT', 'PENDING'].includes(row.status)" @click="cancelContract(row)">作废</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑合同' : '新增合同'" width="760px" class="contract-dialog">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <div class="form-section-title">合同信息</div>
        <div class="form-grid">
          <el-form-item label="合同编号" prop="contractNumber">
            <el-input v-model="form.contractNumber" placeholder="不填则由后端自动生成" />
          </el-form-item>

          <el-form-item label="合同名称" prop="contractName">
            <el-input v-model="form.contractName" />
          </el-form-item>
        </div>

        <div class="form-section-title">租户信息</div>
        <div class="form-grid">
          <el-form-item label="租户名称" prop="tenantName">
            <el-input v-model="form.tenantName" placeholder="可直接录入新租户名称" />
          </el-form-item>

          <el-form-item label="联系人" prop="contactPerson">
            <el-input v-model="form.contactPerson" />
          </el-form-item>

          <el-form-item label="联系电话" prop="contactPhone">
            <el-input v-model="form.contactPhone" placeholder="例如 13800000000 或 021-88888888" />
          </el-form-item>

          <el-form-item label="邮箱" prop="contactEmail">
            <el-input v-model="form.contactEmail" placeholder="例如 name@example.com" />
          </el-form-item>
        </div>

        <div class="form-section-title">房间与周期</div>
        <div class="form-grid">
          <el-form-item label="楼层" prop="floorId">
            <el-select v-model="form.floorId" filterable placeholder="请选择楼层" style="width:100%" @change="handleFloorChange">
              <el-option v-for="floor in floors" :key="floor.id" :label="floorLabel(floor)" :value="floor.id" />
            </el-select>
          </el-form-item>

          <el-form-item label="房间" prop="roomId">
            <el-select v-model="form.roomId" filterable placeholder="请选择房间" style="width:100%">
              <el-option v-for="room in availableRooms" :key="room.id" :label="roomLabel(room)" :value="room.id" />
            </el-select>
          </el-form-item>

          <el-form-item label="开始日期" prop="startDate">
            <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
          </el-form-item>

          <el-form-item label="结束日期" prop="endDate">
            <el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
          </el-form-item>
        </div>

        <div class="form-section-title">费用与收款</div>
        <div class="form-grid">
          <el-form-item label="月租金额" prop="rentAmount">
            <el-input-number v-model="form.rentAmount" :min="0" :precision="2" style="width:100%" />
          </el-form-item>

          <el-form-item label="月物业费" prop="propertyFeeAmount">
            <el-input-number v-model="form.propertyFeeAmount" :min="0" :precision="2" style="width:100%" />
          </el-form-item>

          <el-form-item label="押金" prop="depositAmount">
            <el-input-number v-model="form.depositAmount" :min="0" :precision="2" style="width:100%" />
          </el-form-item>

          <el-form-item label="付款方式" prop="paymentCycle">
            <el-select v-model="form.paymentCycle" style="width:100%">
              <el-option label="全款" value="FULL" />
              <el-option label="月付" value="MONTHLY" />
              <el-option label="季付" value="QUARTERLY" />
              <el-option label="半年付" value="SEMI_ANNUAL" />
              <el-option label="年付" value="YEARLY" />
            </el-select>
          </el-form-item>

          <el-form-item label="提前出账天数" prop="billingLeadDays">
            <el-input-number v-model="form.billingLeadDays" :min="0" :max="60" style="width:100%" />
            <div class="form-tip">例如填 7，表示每个账期开始前 7 天自动生成下一期账单。</div>
          </el-form-item>

          <el-form-item label="合同状态" prop="status">
            <el-select v-model="form.status" style="width:100%">
              <el-option label="草稿" value="DRAFT" />
              <el-option label="待生效" value="PENDING" />
              <el-option label="履约中" value="ACTIVE" />
              <el-option label="已终止" value="TERMINATED" />
              <el-option label="已作废" value="CANCELLED" />
            </el-select>
          </el-form-item>
        </div>

        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveContract">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../../utils/request'
import { readPage } from '../../utils/pagination'
import {
  isValidContactPhone,
  isValidEmail,
  optionalContactPhoneRule,
  optionalEmailRule
} from '../../utils/contactValidation'

const BUILDING_ID = 1

const contracts = ref([])
const dialogVisible = ref(false)
const formRef = ref()
const tenants = ref([])
const rooms = ref([])
const floors = ref([])

const form = reactive({
  id: null,
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
  remark: '',
  status: 'DRAFT'
})

const rules = {
  tenantName: [{ required: true, message: '请输入租户名称', trigger: 'blur' }],
  roomId: [{ required: true, message: '请选择房间', trigger: 'change' }],
  contactPhone: [optionalContactPhoneRule],
  contactEmail: [optionalEmailRule]
}

const availableRooms = computed(() => {
  return rooms.value.filter(item => {
    const sameFloor = form.floorId ? Number(item.floorId) === Number(form.floorId) : true
    const selectableStatus = ['AVAILABLE', 'VACANT'].includes(item.status) || Number(item.id) === Number(form.roomId)
    return sameFloor && selectableStatus
  })
})

function resetForm() {
  Object.assign(form, {
    id: null,
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
    remark: '',
    status: 'DRAFT'
  })
  formRef.value?.clearValidate()
}

async function loadContracts() {
  const data = await request.get('/contracts')
  contracts.value = readPage(data).records
}

async function loadTenants() {
  const data = await request.get('/tenant/list')
  tenants.value = readPage(data).records
}

async function loadRooms() {
  const floorsData = await request.get(`/buildings/${BUILDING_ID}/floors`)
  floors.value = readPage(floorsData).records

  const result = []
  for (const floor of floors.value) {
    const data = await request.get(`/buildings/${BUILDING_ID}/floors/${floor.id}/rooms`)
    result.push(...readPage(data).records)
  }
  rooms.value = result
}

function openCreateDialog() {
  resetForm()
  dialogVisible.value = true
}

function openEditDialog(row) {
  resetForm()
  const room = rooms.value.find(item => Number(item.id) === Number(row.roomId))
  Object.assign(form, {
    id: row.id,
    contractNumber: row.contractNumber || row.code || '',
    contractName: row.contractName || '',
    tenantId: row.tenantId || '',
    tenantName: row.tenantName || tenantDisplayName(row.tenantId),
    contactPerson: row.contactPerson || '',
    contactPhone: row.contactPhone || '',
    contactEmail: row.contactEmail || '',
    floorId: room?.floorId || '',
    roomId: row.roomId || '',
    startDate: row.startDate || '',
    endDate: row.endDate || '',
    rentAmount: Number(row.rentAmount || 0),
    propertyFeeAmount: Number(row.propertyFeeAmount || 0),
    depositAmount: Number(row.depositAmount || 0),
    paymentCycle: row.paymentCycle || 'MONTHLY',
    billingLeadDays: Number(row.billingLeadDays ?? 7),
    remark: row.remark || '',
    status: row.status || 'DRAFT'
  })
  dialogVisible.value = true
}

function handleFloorChange() {
  form.roomId = ''
}

function tenantDisplayName(tenantId) {
  const tenant = tenants.value.find(item => Number(item.id) === Number(tenantId))
  return tenant ? tenant.tenantName : `租户ID:${tenantId || '-'}`
}

function floorLabel(floor) {
  return floor.floorName || floor.floorNumber || `${floor.floorNo || floor.id}层`
}

function roomLabel(room) {
  const areaText = room.area ? `｜${room.area}㎡` : ''
  return `${room.roomNumber || room.roomName || '未命名房间'}${areaText}`
}

function roomDisplayName(roomId) {
  if (!roomId) return '-'
  const room = rooms.value.find(item => Number(item.id) === Number(roomId))
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

function contactText(value, type) {
  if (!value) return '-'
  const valid = type === 'phone' ? isValidContactPhone(value) : isValidEmail(value)
  return valid ? value : `${value}（格式异常）`
}

function payload() {
  return {
    contractNumber: form.contractNumber,
    contractName: form.contractName,
    tenantId: form.tenantId || undefined,
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
    remark: form.remark || form.contractName,
    status: form.status
  }
}

async function saveContract() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  if (!isValidContactPhone(form.contactPhone)) {
    ElMessage.warning('请输入正确的联系电话，例如 13800000000 或 021-88888888')
    return
  }
  if (!isValidEmail(form.contactEmail)) {
    ElMessage.warning('请输入正确的邮箱地址，例如 name@example.com')
    return
  }

  if (form.id) {
    await request.put(`/contracts/${form.id}`, payload())
    ElMessage.success('保存成功')
  } else {
    await request.post('/contracts', payload())
    ElMessage.success('创建成功')
  }

  dialogVisible.value = false
  resetForm()
  await Promise.all([loadContracts(), loadTenants(), loadRooms()])
}

async function activate(row) {
  await request.post(`/contracts/${row.id}/activate`)
  ElMessage.success('已生效')
  await loadContracts()
}

async function checkIn(row) {
  await request.post(`/contracts/${row.id}/check-in`, { remark: '后台合同列表办理入驻' })
  ElMessage.success('入驻办理成功')
  await Promise.all([loadContracts(), loadRooms()])
}

async function terminate(row) {
  await request.post(`/contracts/${row.id}/terminate`, { reason: '后台终止合同' })
  ElMessage.success('已终止')
  await Promise.all([loadContracts(), loadRooms()])
}

async function reactivate(row) {
  await request.post(`/contracts/${row.id}/reactivate`, { reason: '后台恢复履约' })
  ElMessage.success('已恢复履约')
  await Promise.all([loadContracts(), loadRooms()])
}

async function cancelContract(row) {
  await request.post(`/contracts/${row.id}/cancel`, { reason: '后台作废合同' })
  ElMessage.success('已作废')
  await Promise.all([loadContracts(), loadRooms()])
}

async function generateBills() {
  const result = await request.post('/contracts/generate-bills')
  ElMessage.success(`已生成 ${result?.generatedCount || result?.generated || 0} 张，跳过 ${result?.skipped || 0} 张`)
}

onMounted(async () => {
  await Promise.all([loadContracts(), loadTenants(), loadRooms()])
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

.invalid-contact {
  color: #d93025;
  font-weight: 600;
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
  flex-wrap: wrap;
  justify-content: center;
  gap: 6px;
}

.action-buttons :deep(.el-button + .el-button) {
  margin-left: 0;
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
