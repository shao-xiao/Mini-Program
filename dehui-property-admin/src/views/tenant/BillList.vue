<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>账单管理</span>

          <el-button
            v-if="hasPermission('bill:add')"
            type="primary"
            @click="openCreateDialog"
          >
            新增账单
          </el-button>
        </div>
      </template>

      <el-empty
        v-if="!hasPermission('bill:view')"
        description="当前角色无权查看账单"
      />

      <el-table
        v-else
        v-loading="loading"
        :data="bills"
        border
        style="width: 100%"
      >
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="billNumber" label="账单编号" min-width="170" />
        <el-table-column label="租户" min-width="150">
          <template #default="{ row }">
            {{ row.tenantName || tenantName(row.tenantId) }}
          </template>
        </el-table-column>
        <el-table-column label="合同" min-width="180">
          <template #default="{ row }">
            {{ row.contractNumber || contractLabelById(row.contractId) }}
          </template>
        </el-table-column>
        <el-table-column prop="billType" label="类型" width="110" />
        <el-table-column prop="periodStart" label="账期开始" width="120" />
        <el-table-column prop="periodEnd" label="账期结束" width="120" />
        <el-table-column prop="amount" label="应收金额" width="110" />
        <el-table-column prop="paidAmount" label="已收金额" width="110" />
        <el-table-column prop="dueDate" label="到期日" width="120" />

        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'PAID'" type="success">
              已支付
            </el-tag>

            <el-tag
              v-else-if="isOverdue(row)"
              type="danger"
            >
              已逾期
            </el-tag>

            <el-tag
              v-else-if="row.status === 'UNPAID'"
              type="warning"
            >
              未支付
            </el-tag>

            <el-tag v-else type="info">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="
                row.status !== 'PAID' &&
                hasPermission('bill:pay')
              "
              size="small"
              type="success"
              @click="payBill(row)"
            >
              收款
            </el-button>

            <el-button
              v-else
              size="small"
              disabled
            >
              已结清
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      title="新增账单"
      width="560px"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="formRules"
        label-width="110px"
      >
        <el-form-item label="账单编号" prop="billNumber">
          <el-input
            v-model="form.billNumber"
            placeholder="请输入账单编号"
          />
        </el-form-item>

        <el-form-item label="租户" prop="tenantId">
          <el-select
            v-model="form.tenantId"
            filterable
            placeholder="请选择租户"
            style="width: 100%"
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

        <el-form-item label="合同" prop="contractId">
          <el-select
            v-model="form.contractId"
            filterable
            placeholder="请选择已生效合同"
            style="width: 100%"
            @change="handleContractChange"
          >
            <el-option
              v-for="contract in selectableContracts"
              :key="contract.id"
              :label="contractOptionLabel(contract)"
              :value="contract.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="账单类型" prop="billType">
          <el-select
            v-model="form.billType"
            style="width: 100%"
            @change="handleBillTypeChange"
          >
            <el-option label="租金 RENT" value="RENT" />
            <el-option label="物业费 PROPERTY" value="PROPERTY" />
            <el-option label="水费 WATER" value="WATER" />
            <el-option label="电费 ELECTRICITY" value="ELECTRICITY" />
            <el-option label="其他 OTHER" value="OTHER" />
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
          <el-input-number
            v-model="form.amount"
            :min="0.01"
            :precision="2"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="到期日" prop="dueDate">
          <el-date-picker
            v-model="form.dueDate"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">
          取消
        </el-button>

        <el-button
          type="primary"
          :loading="saving"
          @click="createBill"
        >
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../utils/request'
import { hasPermission } from '../../utils/permission'

const bills = ref([])
const tenants = ref([])
const contracts = ref([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const formRef = ref(null)

const defaultForm = () => ({
  billNumber: '',
  tenantId: null,
  contractId: null,
  billType: 'RENT',
  periodStart: '',
  periodEnd: '',
  amount: null,
  dueDate: ''
})

const form = reactive(defaultForm())

const formRules = {
  billNumber: [
    {
      required: true,
      message: '请输入账单编号',
      trigger: 'blur'
    }
  ],

  tenantId: [
    {
      required: true,
      message: '请选择租户',
      trigger: 'change'
    }
  ],

  contractId: [
    {
      required: true,
      message: '请选择合同',
      trigger: 'change'
    }
  ],

  billType: [
    {
      required: true,
      message: '请选择账单类型',
      trigger: 'change'
    }
  ],

  periodStart: [
    {
      required: true,
      message: '请选择账期开始日期',
      trigger: 'change'
    }
  ],

  periodEnd: [
    {
      required: true,
      message: '请选择账期结束日期',
      trigger: 'change'
    }
  ],

  amount: [
    {
      required: true,
      message: '请输入金额',
      trigger: 'change'
    },
    {
      validator: (_rule, value, callback) => {
        if (!value || value <= 0) {
          callback(new Error('金额必须大于0'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ],

  dueDate: [
    {
      required: true,
      message: '请选择到期日',
      trigger: 'change'
    }
  ]
}

const activeTenants = computed(() => tenants.value.filter(item => item.status !== 'INACTIVE'))

const selectableContracts = computed(() => {
  return contracts.value.filter(item => {
    return item.status === 'ACTIVE' && (!form.tenantId || item.tenantId === form.tenantId)
  })
})

function resetForm() {
  Object.assign(form, defaultForm())
  formRef.value?.clearValidate()
}

async function loadBills() {
  if (!hasPermission('bill:view')) {
    return
  }

  loading.value = true

  try {
    const data = await request.get('/bills')
    bills.value = Array.isArray(data) ? data : []
  } catch (error) {
    ElMessage.error(
      error?.response?.data?.message || '账单加载失败'
    )
  } finally {
    loading.value = false
  }
}

async function loadTenants() {
  const data = await request.get('/tenant/list')
  tenants.value = Array.isArray(data) ? data : []
}

async function loadContracts() {
  const data = await request.get('/contracts')
  contracts.value = Array.isArray(data) ? data : []
}

function tenantLabel(tenant) {
  return `${tenant.tenantName}（ID:${tenant.id}）`
}

function tenantName(tenantId) {
  const tenant = tenants.value.find(item => item.id === tenantId)
  return tenant ? tenant.tenantName : `租户ID:${tenantId || '-'}`
}

function contractOptionLabel(contract) {
  const name = contract.contractName ? `｜${contract.contractName}` : ''
  const rent = contract.rentAmount ? `｜租金${contract.rentAmount}` : ''
  const propertyFee = contract.propertyFeeAmount ? `｜物业费${contract.propertyFeeAmount}` : ''
  return `${contract.contractNumber}${name}${rent}${propertyFee}`
}

function contractLabelById(contractId) {
  if (!contractId) return '无合同关联'
  const contract = contracts.value.find(item => item.id === contractId)
  return contract ? contract.contractNumber : `合同ID:${contractId}`
}

function handleTenantChange() {
  form.contractId = null
}

function handleContractChange(contractId) {
  const contract = contracts.value.find(item => item.id === contractId)
  if (!contract) return

  form.tenantId = contract.tenantId

  if (form.billType === 'RENT' && contract.rentAmount) {
    form.amount = Number(contract.rentAmount)
  } else if (form.billType === 'PROPERTY' && contract.propertyFeeAmount) {
    form.amount = Number(contract.propertyFeeAmount)
  }
}

function handleBillTypeChange() {
  handleContractChange(form.contractId)
}

function openCreateDialog() {
  if (!hasPermission('bill:add')) {
    ElMessage.warning('无新增账单权限')
    return
  }

  resetForm()
  dialogVisible.value = true
}

async function createBill() {
  if (!hasPermission('bill:add')) {
    ElMessage.warning('无新增账单权限')
    return
  }

  await formRef.value.validate()

  saving.value = true

  try {
    await request.post('/bills', {
      billNumber: form.billNumber,
      tenantId: Number(form.tenantId),
      contractId: Number(form.contractId),
      billType: form.billType,
      periodStart: form.periodStart,
      periodEnd: form.periodEnd,
      amount: form.amount,
      dueDate: form.dueDate
    })

    ElMessage.success('账单创建成功')

    dialogVisible.value = false

    resetForm()

    await loadBills()
  } catch (error) {
    ElMessage.error(
      error?.response?.data?.message || '账单创建失败'
    )
  } finally {
    saving.value = false
  }
}

function isOverdue(row) {
  if (!row || row.status === 'PAID' || !row.dueDate) {
    return false
  }

  const today = new Date().toISOString().slice(0, 10)

  return String(row.dueDate) < today
}

async function payBill(row) {
  if (!hasPermission('bill:pay')) {
    ElMessage.warning('无账单收款权限')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确定收取账单「${row.billNumber}」的全额款项 ${row.amount} 元吗？`,
      '确认收款',
      { type: 'warning' }
    )

    await request.post(`/bills/${row.id}/pay`)

    ElMessage.success('收款成功')

    await loadBills()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(
        error?.response?.data?.message || '收款失败'
      )
    }
  }
}

onMounted(async () => {
  await Promise.all([
    loadBills(),
    loadTenants(),
    loadContracts()
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
