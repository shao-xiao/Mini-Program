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
        <el-table-column prop="tenantId" label="租户ID" />
        <el-table-column prop="roomId" label="房间ID" />
        <el-table-column prop="leaseId" label="租约ID" />
        <el-table-column prop="startDate" label="开始日期" />
        <el-table-column prop="endDate" label="结束日期" />
        <el-table-column prop="rentAmount" label="租金" />
        <el-table-column prop="depositAmount" label="押金" />
        <el-table-column prop="paymentCycle" label="付款周期" />
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

        <el-form-item label="租户ID">
          <el-input v-model="form.tenantId" />
        </el-form-item>

        <el-form-item label="房间ID">
          <el-input v-model="form.roomId" />
        </el-form-item>

        <el-form-item label="租约ID">
          <el-input v-model="form.leaseId" />
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
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../../utils/request'

const contracts = ref([])
const dialogVisible = ref(false)

const form = reactive({
  contractNumber: '',
  contractName: '',
  tenantId: '',
  roomId: '',
  leaseId: '',
  startDate: '',
  endDate: '',
  rentAmount: 0,
  depositAmount: 0,
  paymentCycle: 'MONTHLY',
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
  form.depositAmount = 0
  form.paymentCycle = 'MONTHLY'
  form.remark = ''
}

const loadContracts = async () => {
  const data = await request.get('/contracts')
  contracts.value = data || []
}

const openCreateDialog = () => {
  resetForm()
  dialogVisible.value = true
}

const createContract = async () => {
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

onMounted(() => {
  loadContracts()
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
