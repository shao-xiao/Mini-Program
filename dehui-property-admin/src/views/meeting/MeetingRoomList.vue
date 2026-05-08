<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>会议室管理</span>
          <el-button type="primary" @click="openDialog()">新增会议室</el-button>
        </div>
      </template>

      <el-table :data="rooms" border>
        <el-table-column prop="roomName" label="会议室" min-width="140" />
        <el-table-column prop="location" label="位置" min-width="140" />
        <el-table-column prop="capacity" label="容纳人数" width="100" />
        <el-table-column prop="facilities" label="设施" min-width="180" />
        <el-table-column prop="workdayWorkHourRate" label="工作时段/小时" width="130" />
        <el-table-column prop="workdayOffHourRate" label="非工作时段/小时" width="140" />
        <el-table-column prop="holidayRate" label="节假日/小时" width="120" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">
              {{ row.status === 'ACTIVE' ? '可预约' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openDialog(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="visible" :title="form.id ? '编辑会议室' : '新增会议室'" width="560px">
      <el-form :model="form" label-width="130px">
        <el-form-item label="会议室名称">
          <el-input v-model="form.roomName" />
        </el-form-item>
        <el-form-item label="位置">
          <el-input v-model="form.location" />
        </el-form-item>
        <el-form-item label="容纳人数">
          <el-input-number v-model="form.capacity" :min="1" style="width:100%" />
        </el-form-item>
        <el-form-item label="设施">
          <el-input v-model="form.facilities" placeholder="投影、白板、视频会议等" />
        </el-form-item>
        <el-form-item label="工作时间费率">
          <el-input-number v-model="form.workdayWorkHourRate" :min="0" :precision="2" style="width:100%" />
        </el-form-item>
        <el-form-item label="非工作时间费率">
          <el-input-number v-model="form.workdayOffHourRate" :min="0" :precision="2" style="width:100%" />
        </el-form-item>
        <el-form-item label="节假日费率">
          <el-input-number v-model="form.holidayRate" :min="0" :precision="2" style="width:100%" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width:100%">
            <el-option label="可预约" value="ACTIVE" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
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
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../utils/request'

const rooms = ref([])
const visible = ref(false)

const form = reactive({
  id: null,
  roomName: '',
  location: '',
  capacity: 12,
  facilities: '',
  workdayWorkHourRate: 100,
  workdayOffHourRate: 150,
  holidayRate: 200,
  status: 'ACTIVE'
})

const reset = () => {
  Object.assign(form, {
    id: null,
    roomName: '',
    location: '',
    capacity: 12,
    facilities: '',
    workdayWorkHourRate: 100,
    workdayOffHourRate: 150,
    holidayRate: 200,
    status: 'ACTIVE'
  })
}

const load = async () => {
  rooms.value = await request.get('/meetings/rooms')
}

const openDialog = (row) => {
  reset()
  if (row) {
    Object.assign(form, row)
  }
  visible.value = true
}

const save = async () => {
  if (!form.roomName) {
    ElMessage.warning('请输入会议室名称')
    return
  }

  const payload = { ...form }
  if (form.id) {
    await request.put(`/meetings/rooms/${form.id}`, payload)
    ElMessage.success('更新成功')
  } else {
    await request.post('/meetings/rooms', payload)
    ElMessage.success('新增成功')
  }
  visible.value = false
  load()
}

const remove = async (row) => {
  await ElMessageBox.confirm(`确认删除会议室 ${row.roomName}？`, '提示', { type: 'warning' })
  await request.delete(`/meetings/rooms/${row.id}`)
  ElMessage.success('删除成功')
  load()
}

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
</style>
