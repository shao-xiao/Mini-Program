<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>停车账单</span>
          <div class="header-actions">
            <el-button @click="syncToBills">同步到账单中心</el-button>
            <el-button type="primary" @click="openCreateDialog">新增停车账单</el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="bills" border style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="billNumber" label="账单编号" min-width="160" />
        <el-table-column label="车位" min-width="130">
          <template #default="{ row }">
            {{ spaceText(row.parkingSpaceId) }}
          </template>
        </el-table-column>
        <el-table-column label="使用方" min-width="160">
          <template #default="{ row }">
            {{ ownerText(row) }}
          </template>
        </el-table-column>
        <el-table-column prop="plateNumber" label="车牌号" width="120" />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            {{ formatBillType(row.billType) }}
          </template>
        </el-table-column>
        <el-table-column prop="periodStart" label="账期开始" width="120" />
        <el-table-column prop="periodEnd" label="账期结束" width="120" />
        <el-table-column prop="amount" label="金额" width="110" />
        <el-table-column prop="dueDate" label="到期日" width="120" />
        <el-table-column prop="paidDate" label="支付日期" width="120" />
        <el-table-column label="账单中心" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.billId" type="success">已同步</el-tag>
            <el-tag v-else type="warning">未同步</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'PAID'" type="success">已支付</el-tag>
            <el-tag v-else-if="row.status === 'UNPAID'" type="danger">未支付</el-tag>
            <el-tag v-else-if="row.status === 'CANCELLED'" type="info">已取消</el-tag>
            <el-tag v-else type="warning">{{ row.status }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="备注" prop="remark" min-width="140" />

        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status !== 'PAID' && row.status !== 'CANCELLED'"
              size="small"
              type="success"
              @click="payBill(row)"
            >
              收款
            </el-button>
            <el-button v-else size="small" disabled>
              已处理
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" title="新增停车账单" width="620px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="120px">
        <el-form-item label="账单编号" prop="billNumber">
          <el-input v-model="form.billNumber" placeholder="如 PARK-202605-001" />
        </el-form-item>

        <el-form-item label="车位" prop="parkingSpaceId">
          <el-select
            v-model="form.parkingSpaceId"
            filterable
            placeholder="请选择车位"
            style="width: 100%"
            @change="handleSpaceChange"
          >
            <el-option
              v-for="space in spaces"
              :key="space.id"
              :label="spaceLabel(space)"
              :value="space.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="使用方" prop="ownerKey">
          <el-select v-model="form.ownerKey" filterable placeholder="请选择租户或VIP" style="width: 100%">
            <el-option label="VIP" value="VIP" />
            <el-option
              v-for="tenant in tenants"
              :key="tenant.id"
              :label="tenantLabel(tenant)"
              :value="tenantOwnerKey(tenant.id)"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="车牌号" prop="plateNumber">
          <el-input v-model="form.plateNumber" placeholder="请输入车牌号" />
        </el-form-item>

        <el-form-item label="账单类型" prop="billType">
          <el-select v-model="form.billType" style="width: 100%">
            <el-option label="月租 MONTHLY" value="MONTHLY" />
            <el-option label="临停 TEMP" value="TEMP" />
          </el-select>
        </el-form-item>

        <el-form-item label="账期开始" prop="periodStart">
          <el-date-picker
            v-model="form.periodStart"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="账期结束" prop="periodEnd">
          <el-date-picker
            v-model="form.periodEnd"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="金额" prop="amount">
          <el-input-number v-model="form.amount" :min="0.01" :precision="2" style="width: 100%" />
        </el-form-item>

        <el-form-item label="到期日" prop="dueDate">
          <el-date-picker
            v-model="form.dueDate"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="请输入备注" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="createBill">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../utils/request'

const bills = ref([])
const spaces = ref([])
const tenants = ref([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const formRef = ref(null)

const defaultForm = () => ({
  billNumber: '',
  parkingSpaceId: null,
  ownerKey: '',
  plateNumber: '',
  billType: 'MONTHLY',
  periodStart: '',
  periodEnd: '',
  amount: null,
  dueDate: '',
  remark: ''
})

const form = reactive(defaultForm())

const formRules = {
  billNumber: [{ required: true, message: '请输入账单编号', trigger: 'blur' }],
  parkingSpaceId: [{ required: true, message: '请选择车位', trigger: 'change' }],
  ownerKey: [{ required: true, message: '请选择租户或VIP', trigger: 'change' }],
  plateNumber: [{ required: true, message: '请输入车牌号', trigger: 'blur' }],
  billType: [{ required: true, message: '请选择账单类型', trigger: 'change' }],
  periodStart: [{ required: true, message: '请选择账期开始日期', trigger: 'change' }],
  periodEnd: [{ required: true, message: '请选择账期结束日期', trigger: 'change' }],
  amount: [
    { required: true, message: '请输入金额', trigger: 'change' },
    {
      validator: (_rule, value, callback) => {
        if (!value || value <= 0) callback(new Error('金额必须大于0'))
        else callback()
      },
      trigger: 'change'
    }
  ],
  dueDate: [{ required: true, message: '请选择到期日', trigger: 'change' }]
}

function resetForm() {
  Object.assign(form, defaultForm())
  formRef.value?.clearValidate()
}

async function loadBills() {
  loading.value = true
  try {
    const data = await request.get('/parking/bills')
    bills.value = Array.isArray(data) ? data : []
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '停车账单加载失败')
  } finally {
    loading.value = false
  }
}

async function loadSpaces() {
  const data = await request.get('/parking/spaces')
  spaces.value = Array.isArray(data) ? data : []
}

async function loadTenants() {
  const data = await request.get('/tenant/list')
  tenants.value = Array.isArray(data) ? data : []
}

function openCreateDialog() {
  resetForm()
  dialogVisible.value = true
}

async function createBill() {
  await formRef.value.validate()
  const owner = parseOwnerKey(form.ownerKey)

  saving.value = true
  try {
    await request.post('/parking/bills', {
      billNumber: form.billNumber,
      parkingSpaceId: Number(form.parkingSpaceId),
      tenantId: owner.vip ? null : owner.tenantId,
      vip: owner.vip,
      plateNumber: form.plateNumber,
      billType: form.billType,
      periodStart: form.periodStart,
      periodEnd: form.periodEnd,
      amount: form.amount,
      dueDate: form.dueDate,
      remark: form.remark
    })

    ElMessage.success('停车账单创建成功')
    dialogVisible.value = false
    resetForm()
    await loadBills()
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '停车账单创建失败')
  } finally {
    saving.value = false
  }
}

async function payBill(row) {
  try {
    await ElMessageBox.confirm(
      `确定收取停车账单「${row.billNumber}」的款项 ${row.amount} 元吗？`,
      '确认收款',
      { type: 'warning' }
    )

    await request.post(`/parking/bills/${row.id}/pay`)
    ElMessage.success('收款成功')
    await loadBills()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error?.response?.data?.message || '收款失败')
    }
  }
}

async function syncToBills() {
  try {
    const count = await request.post('/parking/bills/sync-to-bills')
    ElMessage.success(`已同步 ${count || 0} 条停车账单`)
    await loadBills()
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '同步失败')
  }
}

function formatBillType(type) {
  return {
    MONTHLY: '月租',
    TEMP: '临停'
  }[type] || type || '-'
}

function tenantOwnerKey(tenantId) {
  return `TENANT:${tenantId}`
}

function parseOwnerKey(ownerKey) {
  if (ownerKey === 'VIP') return { vip: true, tenantId: null }
  if (String(ownerKey || '').startsWith('TENANT:')) {
    return { vip: false, tenantId: Number(String(ownerKey).replace('TENANT:', '')) }
  }
  return { vip: false, tenantId: null }
}

function tenantLabel(tenant) {
  const name = tenant.tenantName || tenant.tenantCode || `租户${tenant.id}`
  return tenant.contactPerson ? `${name}（${tenant.contactPerson}）` : name
}

function tenantText(tenantId) {
  if (!tenantId) return ''
  const tenant = tenants.value.find(item => item.id === tenantId)
  return tenant ? tenantLabel(tenant) : `租户 ${tenantId}`
}

function ownerText(row) {
  if (row.vip) return 'VIP'
  return tenantText(row.tenantId) || '-'
}

function spaceLabel(space) {
  const status = space.status === 'AVAILABLE' ? '空闲' : space.status === 'OCCUPIED' ? '占用' : '停用'
  const plate = space.plateNumber ? ` | ${space.plateNumber}` : ''
  return `${space.spaceCode} | ${space.area || '-'}区 | ${status}${plate}`
}

function spaceText(spaceId) {
  if (!spaceId) return '-'
  const space = spaces.value.find(item => item.id === spaceId)
  return space ? `${space.spaceCode}（${space.area || '-'}区）` : `车位 ${spaceId}`
}

function handleSpaceChange(spaceId) {
  const space = spaces.value.find(item => item.id === spaceId)
  if (!space) return

  if (!form.plateNumber && space.plateNumber) {
    form.plateNumber = space.plateNumber
  }
  if (!form.ownerKey) {
    if (space.spaceType === 'VIP' && !space.tenantId) {
      form.ownerKey = 'VIP'
    } else if (space.tenantId) {
      form.ownerKey = tenantOwnerKey(space.tenantId)
    }
  }
}

onMounted(()=>{
  loadBills()
  loadSpaces()
  loadTenants()
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

.header-actions {
  display: flex;
  gap: 10px;
}
</style>
