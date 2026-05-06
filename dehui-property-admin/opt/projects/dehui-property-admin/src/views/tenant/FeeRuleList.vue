<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>收费规则管理</span>
          <el-button type="primary" @click="openCreateDialog">新增收费规则</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="rules" border style="width: 100%">
        <el-table-column prop="id" label="规则ID" width="80" />
        <el-table-column prop="ruleName" label="规则名称" />
        <el-table-column prop="tenantId" label="租户ID" />
        <el-table-column prop="contractId" label="合同ID" />
        <el-table-column prop="feeType" label="费用类型" />
        <el-table-column prop="amount" label="金额" />
        <el-table-column prop="cycle" label="缴费周期" />
        <el-table-column prop="startDate" label="开始日期" />
        <el-table-column prop="endDate" label="结束日期" />
        <el-table-column prop="generateDay" label="生成日" />
        <el-table-column prop="status" label="状态" />
        <el-table-column prop="remark" label="备注" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="generateBill(row)">生成账单</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" title="新增收费规则" width="520px">
      <el-form :model="form" :rules="rulesForm" ref="formRef" label-width="110px">
        <el-form-item label="规则名称" prop="ruleName">
          <el-input v-model="form.ruleName" placeholder="请输入规则名称" />
        </el-form-item>

        <el-form-item label="租户ID" prop="tenantId">
          <el-input v-model="form.tenantId" placeholder="请输入租户ID" />
        </el-form-item>

        <el-form-item label="合同ID" prop="contractId">
          <el-input v-model="form.contractId" placeholder="请输入合同ID" />
        </el-form-item>

        <el-form-item label="费用类型" prop="feeType">
          <el-select v-model="form.feeType" placeholder="请选择费用类型" style="width: 100%">
            <el-option label="租金" value="RENT" />
            <el-option label="物业费" value="PROPERTY" />
            <el-option label="水费" value="WATER" />
            <el-option label="电费" value="ELECTRICITY" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>

        <el-form-item label="金额" prop="amount">
          <el-input-number v-model="form.amount" :min="0.01" style="width: 100%" />
        </el-form-item>

        <el-form-item label="缴费周期" prop="cycle">
          <el-select v-model="form.cycle" placeholder="请选择缴费周期" style="width: 100%">
            <el-option label="月付" value="MONTHLY" />
            <el-option label="季付" value="QUARTERLY" />
            <el-option label="年付" value="YEARLY" />
          </el-select>
        </el-form-item>

        <el-form-item label="开始日期" prop="startDate">
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

        <el-form-item label="生成日" prop="generateDay">
          <el-input-number v-model="form.generateDay" :min="1" :max="28" style="width: 100%" />
        </el-form-item>

        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入备注" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="createRule">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../utils/request'

const rules = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const formRef = ref(null)

const form = reactive({
  ruleName: '',
  tenantId: '',
  contractId: '',
  feeType: '',
  amount: 0,
  cycle: 'MONTHLY',
  startDate: '',
  endDate: '',
  generateDay: 1,
  remark: ''
})

const rulesForm = reactive({
  ruleName: [{ required: true, message: '请输入规则名称', trigger: 'blur' }],
  tenantId: [{ required: true, message: '请输入租户ID', trigger: 'blur' }],
  contractId: [{ required: true, message: '请输入合同ID', trigger: 'blur' }],
  feeType: [{ required: true, message: '请选择费用类型', trigger: 'change' }],
  amount: [
    { required: true, message: '请输入金额', trigger: 'blur' },
    { type: 'number', min: 0.01, message: '金额必须大于0', trigger: 'blur' }
  ],
  cycle: [{ required: true, message: '请选择缴费周期', trigger: 'change' }],
  startDate: [{ required: true, message: '请选择开始日期', trigger: 'change' }],
  generateDay: [{ required: true, message: '请输入生成日', trigger: 'blur' }]
})

const resetForm = () => {
  form.ruleName = ''
  form.tenantId = ''
  form.contractId = ''
  form.feeType = ''
  form.amount = 0
  form.cycle = 'MONTHLY'
  form.startDate = ''
  form.endDate = ''
  form.generateDay = 1
  form.remark = ''
}

const loadRules = async () => {
  loading.value = true
  try {
    const data = await request.get('/feerules')
    rules.value = data || []
  } catch (error) {
    ElMessage.error('加载收费规则失败')
  } finally {
    loading.value = false
  }
}

const openCreateDialog = () => {
  resetForm()
  dialogVisible.value = true
}

const createRule = async () => {
  if (!formRef.value) return
  
  await formRef.value.validate(async (valid) => {
    if (!valid) return

    try {
      await request.post('/feerules', form)
      ElMessage.success('新增成功')
      dialogVisible.value = false
      resetForm()
      loadRules()
    } catch (error) {
      ElMessage.error('新增失败')
    }
  })
}

const generateBill = async (row) => {
  await ElMessageBox.confirm(`确认要为规则「${row.ruleName}」生成账单？`, '提示', {
    type: 'warning'
  })

  try {
    await request.post(`/feerules/${row.id}/generate-bill`)
    ElMessage.success('账单生成成功')
    loadRules()
  } catch (error) {
    ElMessage.error('账单生成失败')
  }
}

onMounted(() => {
  loadRules()
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