<template>
  <div class="fee-rule-page">
    <el-card>
      <template #header>
        <div class="page-header">
          <span>收费规则管理</span>
          <el-button type="primary" @click="openCreateDialog">
            新增收费规则
          </el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="rules" border style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="ruleName" label="规则名称" min-width="160" />
        <el-table-column prop="tenantId" label="租户ID" width="90" />
        <el-table-column prop="contractId" label="合同ID" width="90" />
        <el-table-column prop="feeType" label="费用类型" width="120" />
        <el-table-column prop="amount" label="金额" width="120" />
        <el-table-column prop="cycle" label="周期" width="120" />
        <el-table-column prop="startDate" label="开始日期" width="120" />
        <el-table-column prop="endDate" label="结束日期" width="120" />
        <el-table-column prop="generateDay" label="生成日" width="90" />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column prop="remark" label="备注" min-width="160" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="success" size="small" @click="generateBill(row)">
              生成账单
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" title="新增收费规则" width="620px">
      <el-form ref="formRef" :model="form" :rules="rulesForm" label-width="110px">
        <el-form-item label="规则名称" prop="ruleName">
          <el-input v-model="form.ruleName" placeholder="请输入规则名称" />
        </el-form-item>

        <el-form-item label="租户ID" prop="tenantId">
          <el-input-number v-model="form.tenantId" :min="1" style="width: 100%" />
        </el-form-item>

        <el-form-item label="合同ID" prop="contractId">
          <el-input-number v-model="form.contractId" :min="1" style="width: 100%" />
        </el-form-item>

        <el-form-item label="费用类型" prop="feeType">
          <el-select v-model="form.feeType" placeholder="请选择费用类型" style="width: 100%">
            <el-option label="租金 RENT" value="RENT" />
            <el-option label="物业费 PROPERTY" value="PROPERTY" />
            <el-option label="水费 WATER" value="WATER" />
            <el-option label="电费 ELECTRICITY" value="ELECTRICITY" />
            <el-option label="其他 OTHER" value="OTHER" />
          </el-select>
        </el-form-item>

        <el-form-item label="金额" prop="amount">
          <el-input-number v-model="form.amount" :min="0.01" :precision="2" style="width: 100%" />
        </el-form-item>

        <el-form-item label="周期" prop="cycle">
          <el-select v-model="form.cycle" placeholder="请选择周期" style="width: 100%">
            <el-option label="月付 MONTHLY" value="MONTHLY" />
            <el-option label="季付 QUARTERLY" value="QUARTERLY" />
            <el-option label="年付 YEARLY" value="YEARLY" />
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

        <el-form-item label="生成日" prop="generateDay">
          <el-input-number v-model="form.generateDay" :min="1" :max="31" style="width: 100%" />
        </el-form-item>

        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="请输入备注" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../utils/request'

const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const formRef = ref(null)
const rules = ref([])

const defaultForm = () => ({
  ruleName: '',
  tenantId: null,
  contractId: null,
  feeType: 'RENT',
  amount: null,
  cycle: 'MONTHLY',
  startDate: '',
  endDate: '',
  generateDay: 1,
  remark: ''
})

const form = reactive(defaultForm())

const rulesForm = {
  ruleName: [{ required: true, message: '请输入规则名称', trigger: 'blur' }],
  tenantId: [{ required: true, message: '请输入租户ID', trigger: 'change' }],
  contractId: [{ required: true, message: '请输入合同ID', trigger: 'change' }],
  feeType: [{ required: true, message: '请选择费用类型', trigger: 'change' }],
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
  cycle: [{ required: true, message: '请选择周期', trigger: 'change' }],
  startDate: [{ required: true, message: '请选择开始日期', trigger: 'change' }],
  generateDay: [{ required: true, message: '请输入生成日', trigger: 'change' }]
}

function resetForm() {
  Object.assign(form, defaultForm())
  formRef.value?.clearValidate()
}

function openCreateDialog() {
  resetForm()
  dialogVisible.value = true
}

async function loadRules() {
  loading.value = true
  try {
    const res = await request.get('/feerules')
    rules.value = Array.isArray(res) ? res : []
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '收费规则加载失败')
  } finally {
    loading.value = false
  }
}

async function submitForm() {
  await formRef.value.validate()
  saving.value = true
  try {
    await request.post('/feerules', { ...form })
    ElMessage.success('收费规则创建成功')
    dialogVisible.value = false
    resetForm()
    await loadRules()
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '收费规则创建失败')
  } finally {
    saving.value = false
  }
}

async function generateBill(row) {
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
      ElMessage.error(error?.response?.data?.message || '账单生成失败')
    }
  }
}

onMounted(loadRules)
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
</style>
