<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>租约管理</span>
          <el-button type="primary" @click="openCreateDialog">办理入住</el-button>
        </div>
      </template>

      <el-form inline>
        <el-form-item label="租户">
          <el-select
            v-model="query.tenantId"
            filterable
            placeholder="请选择租户"
            style="width: 240px"
          >
            <el-option
              v-for="tenant in tenants"
              :key="tenant.id"
              :label="tenantLabel(tenant)"
              :value="tenant.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadTenantLeases">查询租约</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="leases" border style="width: 100%">
        <el-table-column prop="id" label="租约ID" width="90" />
        <el-table-column label="租户">
          <template #default="{ row }">{{ tenantName(row.tenantId) }}</template>
        </el-table-column>
        <el-table-column label="房间">
          <template #default="{ row }">{{ roomName(row.roomId) }}</template>
        </el-table-column>
        <el-table-column prop="startDate" label="开始日期" />
        <el-table-column prop="endDate" label="结束日期" />
        <el-table-column prop="status" label="状态">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">
              {{ row.status === 'ACTIVE' ? '在租' : '已退租' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button size="small" type="danger" :disabled="row.status !== 'ACTIVE'" @click="checkout(row)">退租</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" title="办理入住" width="520px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="合同">
          <el-select
            v-model="form.contractId"
            filterable
            placeholder="请选择已生效且未入驻合同"
            style="width: 100%"
            @change="handleContractChange"
          >
            <el-option
              v-for="contract in pendingContracts"
              :key="contract.id"
              :label="contractLabel(contract)"
              :value="contract.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="租户">
          <el-input :model-value="tenantName(form.tenantId)" disabled />
        </el-form-item>

        <el-form-item label="房间">
          <el-input :model-value="roomName(form.roomId)" disabled />
        </el-form-item>

        <el-form-item label="开始日期">
          <el-date-picker
            v-model="form.startDate"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="结束日期">
          <el-date-picker
            v-model="form.endDate"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="备注">
          <el-input v-model="form.remark" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="checkin">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../utils/request'

const leases = ref([])
const dialogVisible = ref(false)
const tenants = ref([])
const rooms = ref([])
const pendingContracts = ref([])
const BUILDING_ID = 1

const query = reactive({
  tenantId: null
})

const form = reactive({
  contractId: null,
  roomId: null,
  tenantId: null,
  startDate: '',
  endDate: '',
  remark: ''
})

const resetForm = () => {
  form.contractId = null
  form.roomId = null
  form.tenantId = null
  form.startDate = ''
  form.endDate = ''
  form.remark = ''
}

const openCreateDialog = () => {
  resetForm()
  loadPendingContracts()
  dialogVisible.value = true
}

function tenantLabel(tenant) {
  return `${tenant.tenantName}（ID:${tenant.id}）`
}

function tenantName(tenantId) {
  if (!tenantId) return ''
  const tenant = tenants.value.find(item => item.id === tenantId)
  return tenant ? `${tenant.tenantName}（ID:${tenant.id}）` : `租户ID:${tenantId}`
}

function roomLabel(room) {
  return `${room.roomNumber || room.roomName || '未命名房间'}（ID:${room.id}）`
}

function roomName(roomId) {
  if (!roomId) return ''
  const room = rooms.value.find(item => item.id === roomId)
  return room ? roomLabel(room) : `房间ID:${roomId}`
}

function contractLabel(contract) {
  return `${contract.contractNumber || '合同'}｜${tenantName(contract.tenantId)}｜${roomName(contract.roomId)}`
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

async function loadPendingContracts() {
  const data = await request.get('/contracts/pending-checkin')
  pendingContracts.value = data || []
}

function handleContractChange(contractId) {
  const contract = pendingContracts.value.find(item => item.id === contractId)
  if (!contract) return

  form.tenantId = contract.tenantId
  form.roomId = contract.roomId
  form.startDate = contract.startDate || ''
  form.endDate = contract.endDate || ''
  form.remark = contract.contractName || ''
}

const loadTenantLeases = async () => {
  if (!query.tenantId) {
    ElMessage.warning('请输入租户ID')
    return
  }

  const data = await request.get(`/rooms/tenants/${query.tenantId}/rooms`)
  leases.value = data || []
}

const checkin = async () => {
  if (!form.contractId || !form.roomId || !form.tenantId || !form.startDate) {
    ElMessage.warning('请选择合同')
    return
  }

  await request.post(`/rooms/${form.roomId}/lease`, {
    contractId: Number(form.contractId),
    tenantId: Number(form.tenantId),
    startDate: form.startDate,
    endDate: form.endDate || null,
    remark: form.remark
  })

  ElMessage.success('入住办理成功')
  dialogVisible.value = false

  query.tenantId = form.tenantId
  await Promise.all([loadTenantLeases(), loadRooms(), loadPendingContracts()])
}

const checkout = async (row) => {
  await ElMessageBox.confirm(`确认将房间 ${row.roomId} 办理退租？`, '提示', {
    type: 'warning'
  })

  await request.post(`/rooms/${row.roomId}/checkout`)
  ElMessage.success('退租成功')
  await Promise.all([loadTenantLeases(), loadRooms()])
}

onMounted(async () => {
  await Promise.all([loadTenants(), loadRooms(), loadPendingContracts()])
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
