<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>租户管理</span>
          <el-button type="primary" @click="openCreateDialog">新增租户</el-button>
        </div>
      </template>

      <el-table :data="tenants" border style="width: 100%">
        <el-table-column prop="id" label="租户ID" width="90" />
        <el-table-column prop="tenantName" label="租户名称" />
        <el-table-column prop="contactPerson" label="联系人" />
        <el-table-column prop="contactPhone" label="联系电话" />
        <el-table-column prop="contactEmail" label="邮箱" />
        <el-table-column prop="businessLicense" label="营业执照" />
        <el-table-column prop="status" label="状态" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button size="small" @click="openEditDialog(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑租户' : '新增租户'" width="520px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="租户名称">
          <el-input v-model="form.tenantName" />
        </el-form-item>

        <el-form-item label="联系人">
          <el-input v-model="form.contactPerson" />
        </el-form-item>

        <el-form-item label="联系电话">
          <el-input v-model="form.contactPhone" />
        </el-form-item>

        <el-form-item label="邮箱">
          <el-input v-model="form.contactEmail" />
        </el-form-item>

        <el-form-item label="营业执照">
          <el-input v-model="form.businessLicense" />
        </el-form-item>

        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="正常" value="ACTIVE" />
            <el-option label="停用" value="INACTIVE" />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveTenant">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../../utils/request'

const tenants = ref([])
const dialogVisible = ref(false)

const form = reactive({
  id: null,
  tenantName: '',
  contactPerson: '',
  contactPhone: '',
  contactEmail: '',
  businessLicense: '',
  status: 'ACTIVE'
})

const resetForm = () => {
  form.id = null
  form.tenantName = ''
  form.contactPerson = ''
  form.contactPhone = ''
  form.contactEmail = ''
  form.businessLicense = ''
  form.status = 'ACTIVE'
}

const loadTenants = async () => {
  const data = await request.get('/tenant/list')
  tenants.value = data || []
}

const openCreateDialog = () => {
  resetForm()
  dialogVisible.value = true
}

const openEditDialog = (row) => {
  Object.assign(form, row)
  dialogVisible.value = true
}

const saveTenant = async () => {
  if (!form.tenantName) {
    ElMessage.warning('请输入租户名称')
    return
  }

  await request.post('/tenant/save', form)
  ElMessage.success('保存成功')

  dialogVisible.value = false
  await loadTenants()
}

onMounted(() => {
  loadTenants()
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
