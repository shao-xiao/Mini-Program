<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>会议室管理</span>
          <el-button type="primary" @click="openDialog()">新增会议室</el-button>
        </div>
      </template>

      <el-form :inline="true" :model="query" class="filter-bar">
        <el-form-item label="会议室">
          <el-input v-model="query.name" clearable placeholder="会议室名称" style="width:180px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部状态" style="width:150px">
            <el-option label="可预约" value="AVAILABLE" />
            <el-option label="维护中" value="MAINTENANCE" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="容纳人数">
          <el-input-number v-model="query.minCapacity" :min="1" controls-position="right" placeholder="不少于" style="width:150px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="rooms" border>
        <el-table-column prop="name" label="会议室" min-width="140" />
        <el-table-column prop="location" label="位置" min-width="140" />
        <el-table-column prop="capacity" label="容纳人数" width="100" />
        <el-table-column prop="equipment" label="设备" min-width="180" />
        <el-table-column prop="workdayHourlyRate" label="工作时段/小时" width="130" />
        <el-table-column prop="offHourHourlyRate" label="非工作时段/小时" width="140" />
        <el-table-column prop="holidayHourlyRate" label="节假日/小时" width="120" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="roomStatusType(row.status)">{{ roomStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <div class="action-buttons">
              <el-button size="small" @click="openDialog(row)">编辑</el-button>
              <el-button size="small" @click="viewBookings(row)">查看预约</el-button>
              <el-button size="small" :type="row.status === 'AVAILABLE' ? 'warning' : 'success'" @click="toggleStatus(row)">
                {{ row.status === 'AVAILABLE' ? '停用' : '启用' }}
              </el-button>
              <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="visible" :title="form.id ? '编辑会议室' : '新增会议室'" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="130px">
        <el-form-item label="会议室名称" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="位置" prop="location">
          <el-input v-model="form.location" />
        </el-form-item>
        <el-form-item label="容纳人数" prop="capacity">
          <el-input-number v-model="form.capacity" :min="1" style="width:100%" />
        </el-form-item>
        <el-form-item label="设备">
          <el-input v-model="form.equipment" placeholder="投影、白板、视频会议等" />
        </el-form-item>
        <el-form-item label="工作时间费率" prop="workdayHourlyRate">
          <el-input-number v-model="form.workdayHourlyRate" :min="0" :precision="2" style="width:100%" />
        </el-form-item>
        <el-form-item label="非工作时间费率" prop="offHourHourlyRate">
          <el-input-number v-model="form.offHourHourlyRate" :min="0" :precision="2" style="width:100%" />
        </el-form-item>
        <el-form-item label="节假日费率" prop="holidayHourlyRate">
          <el-input-number v-model="form.holidayHourlyRate" :min="0" :precision="2" style="width:100%" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="form.status" style="width:100%">
            <el-option label="可预约" value="AVAILABLE" />
            <el-option label="维护中" value="MAINTENANCE" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../utils/request'

const router = useRouter()
const rooms = ref([])
const visible = ref(false)
const formRef = ref()

const query = reactive({
  name: '',
  status: '',
  minCapacity: null
})

const form = reactive({
  id: null,
  name: '',
  location: '',
  capacity: 12,
  equipment: '',
  workdayHourlyRate: 100,
  offHourHourlyRate: 150,
  holidayHourlyRate: 200,
  status: 'AVAILABLE',
  remark: ''
})

const rules = {
  name: [{ required: true, message: '请输入会议室名称', trigger: 'blur' }],
  location: [{ required: true, message: '请输入位置', trigger: 'blur' }],
  capacity: [{ required: true, type: 'number', min: 1, message: '容纳人数必须大于0', trigger: 'change' }],
  workdayHourlyRate: [{ required: true, type: 'number', min: 0, message: '费率不能为负数', trigger: 'change' }],
  offHourHourlyRate: [{ required: true, type: 'number', min: 0, message: '费率不能为负数', trigger: 'change' }],
  holidayHourlyRate: [{ required: true, type: 'number', min: 0, message: '费率不能为负数', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

const reset = () => {
  Object.assign(form, {
    id: null,
    name: '',
    location: '',
    capacity: 12,
    equipment: '',
    workdayHourlyRate: 100,
    offHourHourlyRate: 150,
    holidayHourlyRate: 200,
    status: 'AVAILABLE',
    remark: ''
  })
}

const load = async () => {
  rooms.value = await request.get('/meeting-rooms', {
    params: {
      name: query.name || undefined,
      status: query.status || undefined,
      minCapacity: query.minCapacity || undefined
    }
  })
}

const resetQuery = () => {
  Object.assign(query, { name: '', status: '', minCapacity: null })
  load()
}

const openDialog = (row) => {
  reset()
  if (row) {
    Object.assign(form, row)
  }
  visible.value = true
}

const save = async () => {
  await formRef.value?.validate()
  const payload = { ...form }
  if (form.id) {
    await request.put(`/meeting-rooms/${form.id}`, payload)
    ElMessage.success('更新成功')
  } else {
    await request.post('/meeting-rooms', payload)
    ElMessage.success('新增成功')
  }
  visible.value = false
  load()
}

const toggleStatus = async (row) => {
  const nextStatus = row.status === 'AVAILABLE' ? 'DISABLED' : 'AVAILABLE'
  await request.put(`/meeting-rooms/${row.id}`, { ...row, status: nextStatus })
  ElMessage.success(nextStatus === 'AVAILABLE' ? '已启用' : '已停用')
  load()
}

const viewBookings = (row) => {
  router.push({ path: '/meetings/bookings', query: { roomId: row.id } })
}

const remove = async (row) => {
  await ElMessageBox.confirm(`确认删除会议室 ${row.name}？`, '提示', { type: 'warning' })
  await request.delete(`/meeting-rooms/${row.id}`)
  ElMessage.success('删除成功')
  load()
}

const roomStatusText = (status) => ({
  AVAILABLE: '可预约',
  MAINTENANCE: '维护中',
  DISABLED: '停用'
}[status] || status)

const roomStatusType = (status) => ({
  AVAILABLE: 'success',
  MAINTENANCE: 'warning',
  DISABLED: 'info'
}[status] || '')

onMounted(load)
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

.filter-bar {
  margin-bottom: 14px;
}

.action-buttons {
  display: grid;
  grid-template-columns: repeat(2, 72px);
  gap: 8px;
  align-items: center;
}

.action-buttons :deep(.el-button) {
  width: 72px;
  margin-left: 0;
  padding-left: 0;
  padding-right: 0;
}
</style>
