<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>停车账单</span>
          <el-button type="primary" @click="openCreateDialog">新增停车账单</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="bills" border style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="billNumber" label="账单编号" min-width="160" />
        <el-table-column prop="parkingSpaceId" label="车位ID" width="90" />
        <el-table-column prop="tenantId" label="租户ID" width="90" />
        <el-table-column prop="plateNumber" label="车牌号" width="120" />
        <el-table-column prop="billType" label="类型" width="100" />
        <el-table-column prop="periodStart" label="账期开始" width="120" />
        <el-table-column prop="periodEnd" label="账期结束" width="120" />
        <el-table-column prop="amount" label="金额" width="110" />
        <el-table-column prop="dueDate" label="到期日" width="120" />
        <el-table-column prop="paidDate" label="支付日期" width="120" />

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

        <el-form-item label="车位ID" prop="parkingSpaceId">
          <el-input-number v-model="form.parkingSpaceId" :min="1" style="width: 100%" />
        </el-form-item>

        <el-form-item label="租户ID" prop="tenantId">
          <el-input-number v-model="form.tenantId" :min="1" style="width: 100%" />
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
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const formRef = ref(null)

const defaultForm = () => ({
  billNumber: '',
  parkingSpaceId: null,
  tenantId: null,
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
  parkingSpaceId: [{ required: true, message: '请输入车位ID', trigger: 'change' }],
  tenantId: [{ required: true, message: '请输入租户ID', trigger: 'change' }],
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

function openCreateDialog() {
  resetForm()
  dialogVisible.value = true
}

async function createBill() {
  await formRef.value.validate()

  saving.value = true
  try {
    await request.post('/parking/bills', {
      billNumber: form.billNumber,
      parkingSpaceId: Number(form.parkingSpaceId),
      tenantId: Number(form.tenantId),
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

onMounted(loadBills)
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
