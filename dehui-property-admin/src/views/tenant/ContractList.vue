<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>合同管理</span>
          <el-button type="primary" @click="openCreateDialog">新增合同</el-button>
        </div>
      </template>

      <el-table :data="contracts" border style="width: 100%">
        <el-table-column prop="contractNumber" label="合同编号" />
        <el-table-column prop="contractName" label="合同名称" />
        <el-table-column label="租户">
          <template #default="{ row }">{{ tenantName(row.tenantId) }}</template>
        </el-table-column>
        <el-table-column label="房间">
          <template #default="{ row }">{{ roomName(row.roomId) }}</template>
        </el-table-column>
        <el-table-column prop="leaseId" label="租约ID" />
        <el-table-column prop="startDate" label="开始日期" />
        <el-table-column prop="endDate" label="结束日期" />
        <el-table-column prop="rentAmount" label="租金" />
        <el-table-column prop="propertyFeeAmount" label="物业费" />
        <el-table-column prop="depositAmount" label="押金" />
        <el-table-column prop="paymentCycle" label="付款周期" />
        <el-table-column label="收款规则" min-width="160">
          <template #default="{ row }">
            {{ paymentRuleText(row) }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'DRAFT'" type="info">草稿</el-tag>
            <el-tag v-else-if="row.status === 'ACTIVE'" type="success">已生效</el-tag>
            <el-tag v-else-if="row.status === 'TERMINATED'" type="danger">已终止</el-tag>
            <el-tag v-else type="warning">{{ row.status }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button size="small" :disabled="row.status !== 'DRAFT'" @click="activate(row)">生效</el-button>
            <el-button size="small" type="danger" :disabled="row.status !== 'ACTIVE'" @click="terminate(row)">终止</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" title="新增合同" width="520px">
      <el-form :model="form" label-width="110px">

        <el-form-item label="合同编号">
          <el-input v-model="form.contractNumber" />
        </el-form-item>

        <el-form-item label="合同名称">
          <el-input v-model="form.contractName" />
        </el-form-item>

        <el-form-item label="租户">
          <el-select
            v-model="form.tenantId"
            filterable
            placeholder="请选择租户"
            style="width:100%"
            @change="handleTenantChange"
          >
            <el-option
              v-for="tenant in activeTenants"
              :key="tenant.id"
              :label="tenantLabel(tenant)"
              :value="tenant.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="租约">
          <el-select
            v-model="form.leaseId"
            filterable
            placeholder="请选择该租户的在租记录"
            style="width:100%"
            @change="handleLeaseChange"
          >
            <el-option
              v-for="lease in activeLeases"
              :key="lease.id"
              :label="leaseLabel(lease)"
              :value="lease.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="房间">
          <el-input :model-value="roomName(form.roomId)" disabled />
        </el-form-item>

        <el-form-item label="开始日期">
          <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>

        <el-form-item label="结束日期">
          <el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>

        <el-form-item label="月租金额">
          <el-input-number v-model="form.rentAmount" style="width:100%" />
        </el-form-item>

        <el-form-item label="月物业费">
          <el-input-number v-model="form.propertyFeeAmount" style="width:100%" />
        </el-form-item>

        <el-form-item label="押金">
          <el-input-number v-model="form.depositAmount" style="width:100%" />
        </el-form-item>

        <el-form-item label="付款周期">
          <el-select v-model="form.paymentCycle" style="width:100%">
            <el-option label="月付" value="MONTHLY" />
            <el-option label="季付" value="QUARTERLY" />
            <el-option label="年付" value="YEARLY" />
          </el-select>
        </el-form-item>

        <el-form-item label="每月出账日">
          <el-input-number v-model="form.billingDay" :min="1" :max="28" style="width:100%" />
        </el-form-item>

        <el-form-item label="每月到期日">
          <el-input-number v-model="form.dueDay" :min="1" :max="31" style="width:100%" />
        </el-form-item>

        <el-form-item label="收款规则">
          <el-input v-model="form.paymentTerms" type="textarea" :rows="2" placeholder="例如：租金与物业费按月收取，每月1日出账，10日前付款" />
        </el-form-item>

        <el-form-item label="备注">
          <el-input v-model="form.remark" />
        </el-form-item>

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
const tenantLeases = ref([])
const BUILDING_ID = 1

const form = reactive({
  contractNumber: '',
  contractName: '',
  tenantId: '',
  roomId: '',
  leaseId: '',
  startDate: '',
  endDate: '',
  rentAmount: 0,
  propertyFeeAmount: 0,
  depositAmount: 0,
  paymentCycle: 'MONTHLY',
  billingDay: 1,
  dueDay: 10,
  paymentTerms: '',
  remark: ''
})

const resetForm = () => {
  form.contractNumber = ''
  form.contractName = ''
  form.tenantId = ''
  form.roomId = ''
  form.leaseId = ''
  form.startDate = ''
  form.endDate = ''
  form.rentAmount = 0
  form.propertyFeeAmount = 0
  form.depositAmount = 0
  form.paymentCycle = 'MONTHLY'
  form.billingDay = 1
  form.dueDay = 10
  form.paymentTerms = ''
  form.remark = ''
}

const loadContracts = async () => {
  const data = await request.get('/contracts')
  contracts.value = data || []
}

const activeTenants = computed(() => {
  return tenants.value.filter(item => item.status !== 'INACTIVE')
})

const activeLeases = computed(() => {
  return tenantLeases.value.filter(item => item.status === 'ACTIVE')
})

function tenantLabel(tenant) {
  return `${tenant.tenantName}（ID:${tenant.id}）`
}

function tenantName(tenantId) {
  const tenant = tenants.value.find(item => item.id === tenantId)
  return tenant ? `${tenant.tenantName}（ID:${tenant.id}）` : `租户ID:${tenantId || '-'}`
}

function roomLabel(room) {
  return `${room.roomNumber || room.roomName || '未命名房间'}（ID:${room.id}）`
}

function roomName(roomId) {
  if (!roomId) return ''
  const room = rooms.value.find(item => item.id === roomId)
  return room ? roomLabel(room) : `房间ID:${roomId}`
}

function leaseLabel(lease) {
  return `${roomName(lease.roomId)}｜${lease.startDate || '-'} 至 ${lease.endDate || '长期'}`
}

function paymentRuleText(row) {
  const cycleMap = {
    MONTHLY: '月付',
    QUARTERLY: '季付',
    YEARLY: '年付'
  }
  const cycle = cycleMap[row.paymentCycle] || row.paymentCycle || '-'
  const billing = row.billingDay ? `${row.billingDay}日出账` : '未设出账日'
  const due = row.dueDay ? `${row.dueDay}日前付款` : '未设到期日'
  return `${cycle}，${billing}，${due}`
}

async function loadTenants() {
  const data = await request.get('/tenant/list')
  tenants.value = data || []
}

async function loadRooms() {
  const floorsData = await request.get(`/buildings/${BUILDING_ID}/floors`)
  const floors = floorsData.content || floorsData || []
  const result = []

  for (const floor of floors) {
    const data = await request.get(`/buildings/${BUILDING_ID}/floors/${floor.id}/rooms`)
    result.push(...(data.content || data || []))
  }

  rooms.value = result
}

async function loadTenantLeases(tenantId) {
  if (!tenantId) {
    tenantLeases.value = []
    return
  }

  const data = await request.get(`/rooms/tenants/${tenantId}/rooms`)
  tenantLeases.value = data || []
}

const openCreateDialog = () => {
  resetForm()
  tenantLeases.value = []
  dialogVisible.value = true
}

async function handleTenantChange(tenantId) {
  form.leaseId = ''
  form.roomId = ''
  await loadTenantLeases(tenantId)
}

function handleLeaseChange(leaseId) {
  const lease = tenantLeases.value.find(item => item.id === leaseId)
  if (!lease) return

  form.roomId = lease.roomId
  form.startDate = lease.startDate || form.startDate
  form.endDate = lease.endDate || form.endDate
}

const createContract = async () => {
  if (!form.tenantId || !form.roomId || !form.leaseId) {
    ElMessage.warning('请选择租户和租约')
    return
  }

  await request.post('/contracts', form)
  ElMessage.success('创建成功')
  dialogVisible.value = false
  resetForm()
  loadContracts()
}

const activate = async (row) => {
  await request.post(`/contracts/${row.id}/activate`)
  ElMessage.success('已生效')
  loadContracts()
}

const terminate = async (row) => {
  await request.post(`/contracts/${row.id}/terminate`)
  ElMessage.success('已终止')
  loadContracts()
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

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
