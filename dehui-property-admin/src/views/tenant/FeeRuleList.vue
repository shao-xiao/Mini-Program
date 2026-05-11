<template>
  <div class="fee-rule-page">
    <el-card>
      <template #header>
        <div class="page-header">
          <div>
            <div class="page-title">周期附加收费</div>
            <div class="page-subtitle">用于公摊水电、煤气、会议、其它周期性附加费用；租金和物业费由合同台账自动出账</div>
          </div>

          <el-button
            v-if="hasPermission('feerule:add')"
            type="primary"
            @click="openCreateDialog"
          >
            新增附加收费
          </el-button>
        </div>
      </template>

      <el-empty
        v-if="!hasPermission('feerule:view')"
        description="当前角色无权查看周期附加收费"
      />

      <el-table
        v-else
        v-loading="loading"
        :data="rules"
        border
        style="width: 100%"
      >
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="ruleName" label="规则名称" min-width="160" />
        <el-table-column label="租户" min-width="140">
          <template #default="{ row }">{{ tenantName(row.tenantId) }}</template>
        </el-table-column>
        <el-table-column label="合同" min-width="150">
          <template #default="{ row }">{{ contractName(row.contractId) }}</template>
        </el-table-column>
        <el-table-column label="费用类型" width="110">
          <template #default="{ row }">{{ feeTypeText(row.feeType) }}</template>
        </el-table-column>
        <el-table-column label="金额" width="120" align="right">
          <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
        </el-table-column>
        <el-table-column label="周期" width="100">
          <template #default="{ row }">{{ cycleText(row.cycle) }}</template>
        </el-table-column>
        <el-table-column prop="startDate" label="开始日期" width="120" />
        <el-table-column prop="endDate" label="结束日期" width="120" />
        <el-table-column label="出账日" width="100">
          <template #default="{ row }">每期 {{ row.generateDay || 1 }} 日</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'ACTIVE'" type="success">启用</el-tag>
            <el-tag v-else type="info">{{ row.status || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="160" />

        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="hasPermission('feerule:generate')"
              type="success"
              size="small"
              @click="generateBill(row)"
            >
              生成
            </el-button>

            <el-button
              v-else
              size="small"
              disabled
            >
              无权限
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      title="新增周期附加收费"
      width="620px"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rulesForm"
        label-width="110px"
      >
        <el-form-item label="规则名称" prop="ruleName">
          <el-input
            v-model="form.ruleName"
            placeholder="请输入规则名称"
          />
        </el-form-item>

        <el-alert
          title="租金、物业费请在合同台账维护；此处只用于合同外的周期性附加费用。"
          type="info"
          show-icon
          :closable="false"
          class="form-alert"
        />

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
          >
            <el-option
              v-for="contract in selectableContracts"
              :key="contract.id"
              :label="contractOptionLabel(contract)"
              :value="contract.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="费用类型" prop="feeType">
          <el-select
            v-model="form.feeType"
            placeholder="请选择费用类型"
            style="width: 100%"
          >
            <el-option
              v-for="type in feeTypeOptions"
              :key="type.value"
              :label="type.label"
              :value="type.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="金额" prop="amount">
          <el-input-number
            v-model="form.amount"
            :min="0.01"
            :precision="2"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="周期" prop="cycle">
          <el-select
            v-model="form.cycle"
            placeholder="请选择周期"
            style="width: 100%"
          >
            <el-option label="每月" value="MONTHLY" />
            <el-option label="每季度" value="QUARTERLY" />
            <el-option label="每年" value="YEARLY" />
          </el-select>
        </el-form-item>

        <el-form-item label="开始日期" prop="startDate">
          <el-date-picker
            v-model="form.startDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="请选择开始日期"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="结束日期" prop="endDate">
          <el-date-picker
            v-model="form.endDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="请选择结束日期"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="出账日" prop="generateDay">
          <el-input-number
            v-model="form.generateDay"
            :min="1"
            :max="31"
            style="width: 100%"
          />
          <div class="form-tip">每个周期第几日生成账单。</div>
        </el-form-item>

        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="3"
            placeholder="请输入备注"
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
          @click="submitForm"
        >
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../utils/request'
import { hasPermission } from '../../utils/permission'

const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const formRef = ref(null)
const rules = ref([])
const tenants = ref([])
const contracts = ref([])

const feeTypeOptions = [
  { label: '水', value: 'WATER' },
  { label: '电', value: 'ELECTRICITY' },
  { label: '煤', value: 'GAS' },
  { label: '会议', value: 'MEETING_ROOM' },
  { label: '其它', value: 'OTHER' }
]

const defaultForm = () => ({
  ruleName: '',
  tenantId: null,
  contractId: null,
  feeType: 'WATER',
  amount: null,
  cycle: 'MONTHLY',
  startDate: '',
  endDate: '',
  generateDay: 1,
  remark: ''
})

const form = reactive(defaultForm())

const activeTenants = computed(() => tenants.value.filter(item => item.status !== 'INACTIVE'))

const selectableContracts = computed(() => {
  return contracts.value.filter(item => item.status === 'ACTIVE' && (!form.tenantId || item.tenantId === form.tenantId))
})

const rulesForm = {
  ruleName: [
    {
      required: true,
      message: '请输入规则名称',
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

  feeType: [
    {
      required: true,
      message: '请选择费用类型',
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

  cycle: [
    {
      required: true,
      message: '请选择周期',
      trigger: 'change'
    }
  ],

  startDate: [
    {
      required: true,
      message: '请选择开始日期',
      trigger: 'change'
    }
  ],

  generateDay: [
    {
      required: true,
      message: '请输入生成日',
      trigger: 'change'
    }
  ]
}

function resetForm() {
  Object.assign(form, defaultForm())
  formRef.value?.clearValidate()
}

function feeTypeText(value) {
  return feeTypeOptions.find(item => item.value === value)?.label || value || '-'
}

function cycleText(value) {
  return {
    MONTHLY: '每月',
    QUARTERLY: '每季度',
    YEARLY: '每年'
  }[value] || value || '-'
}

function formatMoney(value) {
  return Number(value || 0).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })
}

function tenantLabel(tenant) {
  return `${tenant.tenantName}（ID:${tenant.id}）`
}

function tenantName(tenantId) {
  const tenant = tenants.value.find(item => item.id === tenantId)
  return tenant ? tenant.tenantName : `租户ID:${tenantId || '-'}`
}

function contractName(contractId) {
  const contract = contracts.value.find(item => item.id === contractId)
  return contract ? contract.contractNumber : `合同ID:${contractId || '-'}`
}

function contractOptionLabel(contract) {
  const name = contract.contractName ? `｜${contract.contractName}` : ''
  return `${contract.contractNumber}${name}`
}

function handleTenantChange() {
  form.contractId = null
}

function openCreateDialog() {
  if (!hasPermission('feerule:add')) {
    ElMessage.warning('无新增收费规则权限')
    return
  }

  resetForm()
  dialogVisible.value = true
}

async function loadRules() {
  if (!hasPermission('feerule:view')) {
    return
  }

  loading.value = true

  try {
    const res = await request.get('/feerules')
    rules.value = Array.isArray(res) ? res : []
  } catch (error) {
    ElMessage.error(
      error?.response?.data?.message || '收费规则加载失败'
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

async function submitForm() {
  if (!hasPermission('feerule:add')) {
    ElMessage.warning('无新增收费规则权限')
    return
  }

  await formRef.value.validate()

  saving.value = true

  try {
    await request.post('/feerules', { ...form })

    ElMessage.success('附加收费规则创建成功')

    dialogVisible.value = false

    resetForm()

    await loadRules()
  } catch (error) {
    ElMessage.error(
      error?.response?.data?.message || '收费规则创建失败'
    )
  } finally {
    saving.value = false
  }
}

async function generateBill(row) {
  if (!hasPermission('feerule:generate')) {
    ElMessage.warning('无生成账单权限')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确定根据收费规则「${row.ruleName}」生成账单吗？`,
      '生成账单确认',
      { type: 'warning' }
    )

    await request.post(`/feerules/${row.id}/generate-bill`)

    ElMessage.success('账单生成成功')

    await loadRules()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(
        error?.response?.data?.message || '账单生成失败'
      )
    }
  }
}

onMounted(async () => {
  await Promise.all([loadRules(), loadTenants(), loadContracts()])
})
</script>

<style scoped>
.fee-rule-page {
  padding: 16px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.page-title {
  font-weight: 600;
  color: #303133;
}

.page-subtitle {
  margin-top: 6px;
  color: #909399;
  font-size: 13px;
}

.form-alert {
  margin-bottom: 18px;
}

.form-tip {
  width: 100%;
  margin-top: 6px;
  color: #909399;
  font-size: 12px;
}
</style>
