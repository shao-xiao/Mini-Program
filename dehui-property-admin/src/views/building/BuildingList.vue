<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>楼宇管理</span>
          <el-button type="primary" @click="openCreateDialog">新增楼宇</el-button>
        </div>
      </template>

      <el-table :data="list" v-loading="loading" border style="width: 100%">
        <el-table-column type="index" label="序号" width="70" />
        <el-table-column prop="buildingName" label="楼宇名称" min-width="140" />
        <el-table-column prop="buildingCode" label="楼宇编号" width="120" />
        <el-table-column prop="address" label="地址" min-width="180" />
        <el-table-column prop="totalFloors" label="楼层数" width="90" />
        <el-table-column prop="description" label="说明" min-width="160" />

        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'ACTIVE'" type="success">启用</el-tag>
            <el-tag v-else-if="row.status === 'DISABLED'" type="info">停用</el-tag>
            <el-tag v-else type="warning">{{ row.status || '未设置' }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="openEditDialog(row)">
              编辑
            </el-button>
            <el-button size="small" type="danger" @click="deleteBuilding(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="visible" :title="isEdit ? '编辑楼宇' : '新增楼宇'" width="620px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="名称" prop="buildingName">
          <el-input v-model="form.buildingName" />
        </el-form-item>

        <el-form-item label="编码">
          <el-input v-model="form.buildingCode" />
        </el-form-item>

        <el-form-item label="地址">
          <el-input v-model="form.address" />
        </el-form-item>

        <el-form-item label="楼层数">
          <el-input-number v-model="form.totalFloors" :min="0" style="width: 100%" />
        </el-form-item>

        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="启用 ACTIVE" value="ACTIVE" />
            <el-option label="停用 DISABLED" value="DISABLED" />
          </el-select>
        </el-form-item>

        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="3" />
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

const list = ref([])
const loading = ref(false)
const saving = ref(false)
const visible = ref(false)
const isEdit = ref(false)
const editingId = ref(null)
const formRef = ref(null)

const defaultForm = () => ({
  buildingName: '',
  buildingCode: '',
  address: '',
  totalFloors: 1,
  description: '',
  status: 'ACTIVE'
})

const form = reactive(defaultForm())

const rules = {
  buildingName: [{ required: true, message: '请输入楼宇名称', trigger: 'blur' }]
}

function resetForm() {
  Object.assign(form, defaultForm())
  editingId.value = null
  isEdit.value = false
  formRef.value?.clearValidate()
}

async function load() {
  loading.value = true
  try {
    const data = await request.get('/buildings')
    list.value = Array.isArray(data) ? data : (data?.content || [])
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '楼宇列表加载失败')
  } finally {
    loading.value = false
  }
}

function openCreateDialog() {
  resetForm()
  visible.value = true
}

function openEditDialog(row) {
  resetForm()
  isEdit.value = true
  editingId.value = row.id

  Object.assign(form, {
    buildingName: row.buildingName || '',
    buildingCode: row.buildingCode || '',
    address: row.address || '',
    totalFloors: row.totalFloors ?? 1,
    description: row.description || '',
    status: row.status || 'ACTIVE'
  })

  visible.value = true
}

async function save() {
  await formRef.value.validate()

  saving.value = true
  try {
    const payload = { ...form }

    if (isEdit.value && editingId.value) {
      await request.put(`/buildings/${editingId.value}`, payload)
      ElMessage.success('楼宇更新成功')
    } else {
      await request.post('/buildings', payload)
      ElMessage.success('楼宇新增成功')
    }

    visible.value = false
    resetForm()
    await load()
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function deleteBuilding(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除楼宇「${row.buildingName}」吗？如果该楼宇下已有楼层或房间，后端可能会拒绝删除。`,
      '删除确认',
      { type: 'warning' }
    )

    await request.delete(`/buildings/${row.id}`)
    ElMessage.success('楼宇删除成功')
    await load()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error?.response?.data?.message || '删除失败')
    }
  }
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
