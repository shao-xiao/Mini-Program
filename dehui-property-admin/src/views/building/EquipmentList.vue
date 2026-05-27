<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>设备台账</span>
          <el-button type="primary" @click="openDialog">新增设备</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="list" border style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="equipmentName" label="设备名称" min-width="150" />
        <el-table-column prop="equipmentCode" label="设备编号" min-width="130" />
        <el-table-column prop="equipmentType" label="设备类型" width="120" />
        <el-table-column prop="location" label="位置" min-width="140" />

        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'NORMAL'" type="success">正常</el-tag>
            <el-tag v-else-if="row.status === 'FAULT'" type="danger">故障</el-tag>
            <el-tag v-else-if="row.status === 'MAINTENANCE'" type="warning">维护中</el-tag>
            <el-tag v-else-if="row.status === 'DISABLED'" type="info">停用</el-tag>
            <el-tag v-else>{{ row.status }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="manufacturer" label="厂家" min-width="120" />
        <el-table-column prop="model" label="型号" min-width="120" />
        <el-table-column prop="installDate" label="安装日期" width="120" />
        <el-table-column prop="remark" label="备注" min-width="160" />

        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="edit(row)">编辑</el-button>
            <el-dropdown @command="status => updateStatus(row, status)">
              <el-button size="small">
                修改状态
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="NORMAL">正常</el-dropdown-item>
                  <el-dropdown-item command="FAULT">故障</el-dropdown-item>
                  <el-dropdown-item command="MAINTENANCE">维护中</el-dropdown-item>
                  <el-dropdown-item command="DISABLED">停用</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pagination.currentPage"
          v-model:page-size="pagination.pageSize"
          :total="pagination.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="load"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>

    <el-dialog v-model="visible" :title="form.id ? '编辑设备' : '新增设备'" width="620px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="设备名称" prop="equipmentName">
          <el-input v-model="form.equipmentName" placeholder="请输入设备名称" />
        </el-form-item>

        <el-form-item label="设备编号">
          <el-input v-model="form.equipmentCode" placeholder="如 EQ-001" />
        </el-form-item>

        <el-form-item label="设备类型">
          <el-select v-model="form.equipmentType" placeholder="请选择设备类型" style="width: 100%">
            <el-option label="电梯 ELEVATOR" value="ELEVATOR" />
            <el-option label="空调 HVAC" value="HVAC" />
            <el-option label="消防 FIRE" value="FIRE" />
            <el-option label="配电 POWER" value="POWER" />
            <el-option label="给排水 WATER" value="WATER" />
            <el-option label="安防 SECURITY" value="SECURITY" />
            <el-option label="其他 OTHER" value="OTHER" />
          </el-select>
        </el-form-item>

        <el-form-item label="位置">
          <el-input v-model="form.location" placeholder="如 1F大厅 / B1设备房" />
        </el-form-item>

        <el-form-item label="厂家">
          <el-input v-model="form.manufacturer" />
        </el-form-item>

        <el-form-item label="型号">
          <el-input v-model="form.model" />
        </el-form-item>

        <el-form-item label="安装日期">
          <el-date-picker
            v-model="form.installDate"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../utils/request'
import { createPagination, pageParams, readPage, resetToFirstPage } from '../../utils/pagination'

const list = ref([])
const loading = ref(false)
const saving = ref(false)
const visible = ref(false)
const pagination = reactive(createPagination(20))
const formRef = ref(null)

const defaultForm = () => ({
  id: null,
  equipmentName: '',
  equipmentCode: '',
  equipmentType: 'ELEVATOR',
  location: '',
  manufacturer: '',
  model: '',
  installDate: '',
  remark: ''
})

const form = reactive(defaultForm())

const rules = {
  equipmentName: [{ required: true, message: '请输入设备名称', trigger: 'blur' }]
}

function resetForm() {
  Object.assign(form, defaultForm())
  formRef.value?.clearValidate()
}

async function load() {
  loading.value = true
  try {
    const data = await request.get('/equipments', { params: pageParams(pagination) })
    const page = readPage(data)
    list.value = page.records
    pagination.total = page.total
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '设备列表加载失败')
  } finally {
    loading.value = false
  }
}

function handleSizeChange() {
  resetToFirstPage(pagination)
  load()
}

function openDialog() {
  resetForm()
  visible.value = true
}

function edit(row) {
  Object.assign(form, {
    id: row.id,
    equipmentName: row.equipmentName || '',
    equipmentCode: row.equipmentCode || '',
    equipmentType: row.equipmentType || 'ELEVATOR',
    location: row.location || '',
    manufacturer: row.manufacturer || '',
    model: row.model || '',
    installDate: row.installDate || '',
    remark: row.remark || ''
  })
  formRef.value?.clearValidate()
  visible.value = true
}

async function save() {
  await formRef.value.validate()

  saving.value = true
  try {
    const payload = { ...form }
    delete payload.id

    if (form.id) {
      await request.put(`/equipments/${form.id}`, payload)
      ElMessage.success('设备保存成功')
    } else {
      await request.post('/equipments', payload)
      ElMessage.success('设备新增成功')
    }

    visible.value = false
    resetForm()
    await load()
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '设备保存失败')
  } finally {
    saving.value = false
  }
}

async function updateStatus(row, status) {
  try {
    await ElMessageBox.confirm(
      `确定将设备「${row.equipmentName}」状态改为 ${status} 吗？`,
      '修改状态确认',
      { type: 'warning' }
    )

    await request.patch(`/equipments/${row.id}/status?status=${status}`)
    ElMessage.success('设备状态已更新')
    await load()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error?.response?.data?.message || '设备状态更新失败')
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
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
