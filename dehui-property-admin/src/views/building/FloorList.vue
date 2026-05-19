<template>
  <div class="page-container">
    <el-row :gutter="20">
      <el-col :span="8">
        <el-card>
          <template #header>楼宇列表</template>
          <el-table v-loading="buildingLoading" :data="buildings" border @row-click="selectBuilding">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="buildingName" label="楼宇名称" />
          </el-table>
        </el-card>
      </el-col>

      <el-col :span="16">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>楼层管理（楼宇ID：{{ currentBuildingId || '-' }}）</span>
              <div class="header-actions">
                <el-button :disabled="!currentBuildingId" @click="batchGenerateFloors">
                  批量生成
                </el-button>
                <el-button type="primary" :disabled="!currentBuildingId" @click="openCreateDialog">
                  新增楼层
                </el-button>
              </div>
            </div>
          </template>

          <el-table v-loading="floorLoading" :data="floors" border style="width: 100%">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="floorNumber" label="楼层号" width="110" />
            <el-table-column prop="floorName" label="楼层名称" min-width="140" />
            <el-table-column prop="totalArea" label="总面积" width="130" />

            <el-table-column label="操作" width="170" fixed="right">
              <template #default="{ row }">
                <el-button size="small" type="primary" @click="openEditDialog(row)">
                  编辑
                </el-button>
                <el-button size="small" type="danger" @click="deleteFloor(row)">
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="visible" :title="isEdit ? '编辑楼层' : '新增楼层'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="楼层号" prop="floorNumber">
          <el-input-number v-model="form.floorNumber" style="width: 100%" />
        </el-form-item>

        <el-form-item label="楼层名称">
          <el-input v-model="form.floorName" />
        </el-form-item>

        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" style="width: 100%" />
        </el-form-item>

        <el-form-item label="总面积">
          <el-input-number v-model="form.totalArea" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">
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

const buildings = ref([])
const floors = ref([])
const currentBuildingId = ref(null)

const buildingLoading = ref(false)
const floorLoading = ref(false)
const saving = ref(false)
const visible = ref(false)
const isEdit = ref(false)
const editingFloorId = ref(null)
const formRef = ref(null)

const defaultForm = () => ({
  floorNumber: 1,
  floorName: '',
  sortOrder: 1,
  totalArea: 0
})

const form = reactive(defaultForm())

const rules = {
  floorNumber: [{ required: true, message: '请输入楼层号', trigger: 'change' }]
}

function resetForm() {
  Object.assign(form, defaultForm())
  isEdit.value = false
  editingFloorId.value = null
  formRef.value?.clearValidate()
}

async function loadBuildings() {
  buildingLoading.value = true
  try {
    const data = await request.get('/buildings')
    buildings.value = Array.isArray(data) ? data : (data?.content || [])
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '楼宇列表加载失败')
  } finally {
    buildingLoading.value = false
  }
}

async function loadFloors(buildingId) {
  if (!buildingId) {
    floors.value = []
    return
  }

  floorLoading.value = true
  try {
    const data = await request.get('/floors', {
      params: { building_id: buildingId }
    })
    floors.value = Array.isArray(data) ? data : []
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '楼层列表加载失败')
  } finally {
    floorLoading.value = false
  }
}

async function selectBuilding(row) {
  currentBuildingId.value = row.id
  await loadFloors(row.id)
}

function openCreateDialog() {
  resetForm()
  visible.value = true
}

function openEditDialog(row) {
  resetForm()
  isEdit.value = true
  editingFloorId.value = row.id

  Object.assign(form, {
    floorNumber: row.floorNumber ?? 1,
    floorName: row.floorName || '',
    sortOrder: row.sortOrder ?? row.floorNumber ?? 1,
    totalArea: row.totalArea ?? 0
  })

  visible.value = true
}

async function save() {
  if (!currentBuildingId.value) {
    ElMessage.warning('请先选择楼宇')
    return
  }

  await formRef.value.validate()

  saving.value = true
  try {
    const payload = {
      ...form,
      buildingId: currentBuildingId.value
    }

    if (isEdit.value && editingFloorId.value) {
      await request.put(`/floors/${editingFloorId.value}`, payload)
      ElMessage.success('楼层更新成功')
    } else {
      await request.post('/floors', payload)
      ElMessage.success('楼层新增成功')
    }

    visible.value = false
    resetForm()
    await loadFloors(currentBuildingId.value)
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function deleteFloor(row) {
  if (!currentBuildingId.value) {
    ElMessage.warning('请先选择楼宇')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确定停用/删除楼层「${row.floorName || row.floorNumber}」吗？系统会使用软删除保留历史数据。`,
      '删除确认',
      { type: 'warning' }
    )

    await request.delete(`/floors/${row.id}`)
    ElMessage.success('楼层删除成功')
    await loadFloors(currentBuildingId.value)
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error?.response?.data?.message || '删除失败')
    }
  }
}

async function batchGenerateFloors() {
  if (!currentBuildingId.value) {
    ElMessage.warning('请先选择楼宇')
    return
  }

  await request.post('/floors/batch-generate', {
    buildingId: currentBuildingId.value,
    basementStart: 2,
    basementEnd: 1,
    aboveStart: 1,
    aboveEnd: 9,
    status: 'ACTIVE'
  })
  ElMessage.success('楼层批量生成完成')
  await loadFloors(currentBuildingId.value)
}

onMounted(async () => {
  await loadBuildings()
  if (buildings.value.length > 0) {
    currentBuildingId.value = buildings.value[0].id
    await loadFloors(currentBuildingId.value)
  }
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

.header-actions {
  display: flex;
  gap: 8px;
}
</style>
