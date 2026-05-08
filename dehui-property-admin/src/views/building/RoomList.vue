<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>房间管理</span>
          <el-button type="primary" @click="openCreateDialog">新增房间</el-button>
        </div>
      </template>

      <el-form inline>
        <el-form-item label="楼层">
          <el-select
            v-model="query.floorId"
            class="floor-select"
            @change="loadRooms"
            placeholder="请选择楼层"
          >
            <el-option
              v-for="f in floors"
              :key="f.id"
              :label="formatFloorLabel(f)"
              :value="f.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="loadRooms">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="rooms" border style="width: 100%">
        <el-table-column prop="id" label="房间ID" width="90" />
        <el-table-column prop="roomNumber" label="房间号" />
        <el-table-column prop="roomName" label="房间名称" />
        <el-table-column prop="area" label="面积㎡" />
        <el-table-column prop="roomType" label="类型" />
        <el-table-column prop="status" label="状态" />
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-button size="small" @click="openEditDialog(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="deleteRoom(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑房间' : '新增房间'" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="房间号">
          <el-input v-model="form.roomNumber" />
        </el-form-item>

        <el-form-item label="房间名称">
          <el-input v-model="form.roomName" />
        </el-form-item>

        <el-form-item label="面积">
          <el-input-number v-model="form.area" :min="0" style="width:100%" />
        </el-form-item>

        <el-form-item label="类型">
          <el-select v-model="form.roomType" style="width:100%">
            <el-option label="办公室" value="OFFICE" />
            <el-option label="商铺" value="SHOP" />
            <el-option label="会议室" value="MEETING_ROOM" />
            <el-option label="设备间" value="EQUIPMENT_ROOM" />
          </el-select>
        </el-form-item>

        <el-form-item label="状态">
          <el-select v-model="form.status" style="width:100%">
            <el-option label="可用" value="AVAILABLE" />
            <el-option label="已租" value="RENTED" />
            <el-option label="维修中" value="MAINTENANCE" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveRoom">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../utils/request'

const BUILDING_ID = 1

const floors = ref([])
const rooms = ref([])
const dialogVisible = ref(false)

const query = reactive({
  floorId: null
})

const form = reactive({
  id: null,
  roomNumber: '',
  roomName: '',
  area: 0,
  roomType: 'OFFICE',
  status: 'AVAILABLE'
})

const resetForm = () => {
  form.id = null
  form.roomNumber = ''
  form.roomName = ''
  form.area = 0
  form.roomType = 'OFFICE'
  form.status = 'AVAILABLE'
}

const formatFloorLabel = (floor) => {
  const name = floor.floorName || `${floor.floorNumber}层`
  const number = floor.floorNumber

  if (number === null || number === undefined) {
    return name
  }

  return `${name}（${number}层）`
}

const loadFloors = async () => {
  const data = await request.get(`/buildings/${BUILDING_ID}/floors`)
  floors.value = data.content || data || []

  if (floors.value.length > 0) {
    query.floorId = floors.value[0].id
  }
}

const loadRooms = async () => {
  if (!query.floorId) return

  const data = await request.get(
    `/buildings/${BUILDING_ID}/floors/${query.floorId}/rooms`
  )

  rooms.value = data.content || data || []
}

const openCreateDialog = () => {
  resetForm()
  dialogVisible.value = true
}

const openEditDialog = (row) => {
  Object.assign(form, row)
  dialogVisible.value = true
}

const saveRoom = async () => {
  if (!query.floorId) {
    ElMessage.warning('请先选择楼层')
    return
  }

  if (!form.roomNumber) {
    ElMessage.warning('请输入房间号')
    return
  }

  if (form.id) {
    await request.put(
      `/buildings/${BUILDING_ID}/floors/${query.floorId}/rooms/${form.id}`,
      form
    )
    ElMessage.success('更新成功')
  } else {
    await request.post(
      `/buildings/${BUILDING_ID}/floors/${query.floorId}/rooms`,
      form
    )
    ElMessage.success('新增成功')
  }

  dialogVisible.value = false
  loadRooms()
}

const deleteRoom = async (row) => {
  await ElMessageBox.confirm(`确认删除房间 ${row.roomNumber}？`, '提示', {
    type: 'warning'
  })

  await request.delete(
    `/buildings/${BUILDING_ID}/floors/${query.floorId}/rooms/${row.id}`
  )

  ElMessage.success('删除成功')
  loadRooms()
}

onMounted(async () => {
  await loadFloors()
  await loadRooms()
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

.floor-select {
  width: 180px;
}
</style>
