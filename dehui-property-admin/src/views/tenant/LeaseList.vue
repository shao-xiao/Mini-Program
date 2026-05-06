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
        <el-form-item label="租户ID">
          <el-input v-model="query.tenantId" placeholder="请输入租户ID" style="width: 180px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadTenantLeases">查询租约</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="leases" border style="width: 100%">
        <el-table-column prop="id" label="租约ID" width="90" />
        <el-table-column prop="tenantId" label="租户ID" />
        <el-table-column prop="roomId" label="房间ID" />
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
        <el-form-item label="房间ID">
          <el-input v-model="form.roomId" />
        </el-form-item>

        <el-form-item label="租户ID">
          <el-input v-model="form.tenantId" />
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
import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../utils/request'

const leases = ref([])
const dialogVisible = ref(false)

const query = reactive({
  tenantId: ''
})

const form = reactive({
  roomId: '',
  tenantId: '',
  startDate: '',
  endDate: '',
  remark: ''
})

const resetForm = () => {
  form.roomId = ''
  form.tenantId = ''
  form.startDate = ''
  form.endDate = ''
  form.remark = ''
}

const openCreateDialog = () => {
  resetForm()
  dialogVisible.value = true
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
  if (!form.roomId || !form.tenantId || !form.startDate) {
    ElMessage.warning('请填写房间ID、租户ID和开始日期')
    return
  }

  await request.post(`/rooms/${form.roomId}/lease`, {
    tenantId: Number(form.tenantId),
    startDate: form.startDate,
    endDate: form.endDate || null,
    remark: form.remark
  })

  ElMessage.success('入住办理成功')
  dialogVisible.value = false

  query.tenantId = form.tenantId
  await loadTenantLeases()
}

const checkout = async (row) => {
  await ElMessageBox.confirm(`确认将房间 ${row.roomId} 办理退租？`, '提示', {
    type: 'warning'
  })

  await request.post(`/rooms/${row.roomId}/checkout`)
  ElMessage.success('退租成功')
  await loadTenantLeases()
}
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
