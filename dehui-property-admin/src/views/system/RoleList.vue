<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="page-header">
          <div>
            <div class="page-title">角色管理</div>
            <div class="page-subtitle">维护系统角色，用于用户权限分配</div>
          </div>
          <el-button type="primary" @click="openCreateDialog">新增角色</el-button>
        </div>
      </template>

      <el-table :data="roles" border stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" align="center" />
        <el-table-column prop="roleCode" label="角色编码" width="160" />
        <el-table-column label="角色名称" width="180">
          <template #default="{ row }">
            <div>
              <div>{{ formatRoleName(row.roleCode) }}</div>
              <div class="role-code">{{ row.roleCode }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="角色说明" min-width="260">
          <template #default="{ row }">
            {{ row.description || defaultRoleDesc(row.roleCode) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">
              {{ row.status === 'ACTIVE' ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">
            {{ formatTime(row.createdTime) }}
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" title="新增角色" width="520px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="角色编码" prop="roleCode">
          <el-select v-model="form.roleCode" placeholder="请选择角色编码" style="width: 100%">
            <el-option label="ADMIN - 系统管理员" value="ADMIN" />
            <el-option label="MANAGER - 运营经理" value="MANAGER" />
            <el-option label="STAFF - 普通员工" value="STAFF" />
            <el-option label="SECURITY - 安保人员" value="SECURITY" />
            <el-option label="CLEANER - 保洁人员" value="CLEANER" />
            <el-option label="FINANCE - 财务人员" value="FINANCE" />
          </el-select>
        </el-form-item>

        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="form.roleName" placeholder="请输入角色名称" />
        </el-form-item>

        <el-form-item label="角色说明">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            placeholder="请输入角色说明"
          />
        </el-form-item>

        <el-form-item label="状态" prop="status">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="禁用" value="DISABLED" />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitRole">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../../utils/request'

const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const formRef = ref(null)
const roles = ref([])

const form = reactive({
  roleCode: '',
  roleName: '',
  description: '',
  status: 'ACTIVE'
})

const rules = {
  roleCode: [{ required: true, message: '请选择角色编码', trigger: 'change' }],
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

function unwrap(res) {
  if (res?.code === 200) return res.data
  if (res?.data?.code === 200) return res.data.data
  if (Array.isArray(res)) return res
  if (Array.isArray(res?.data)) return res.data
  return res?.data ?? res
}

async function loadRoles() {
  loading.value = true
  try {
    const res = await request.get('/system/roles')
    roles.value = unwrap(res) || []
  } catch (e) {
    ElMessage.error(e.message || '加载角色列表失败')
  } finally {
    loading.value = false
  }
}

function resetForm() {
  form.roleCode = ''
  form.roleName = ''
  form.description = ''
  form.status = 'ACTIVE'
}

function openCreateDialog() {
  resetForm()
  dialogVisible.value = true
}

async function submitRole() {
  if (!formRef.value) return

  await formRef.value.validate(async valid => {
    if (!valid) return

    saving.value = true
    try {
      await request.post('/system/roles', {
        roleCode: form.roleCode,
        roleName: form.roleName,
        description: form.description,
        status: form.status
      })

      ElMessage.success('创建成功')
      dialogVisible.value = false
      await loadRoles()
    } catch (e) {
      ElMessage.error(e.message || '创建角色失败')
    } finally {
      saving.value = false
    }
  })
}


function formatRoleName(code) {
  return {
    ADMIN: '系统管理员',
    MANAGER: '运营经理',
    STAFF: '普通员工',
    SECURITY: '安保人员',
    CLEANER: '保洁人员',
    FINANCE: '财务人员'
  }[code] || code || '-'
}

function defaultRoleDesc(code) {
  return {
    ADMIN: '系统最高权限，拥有所有模块访问能力',
    MANAGER: '负责运营管理、租户及资产管理',
    STAFF: '日常操作人员',
    SECURITY: '负责巡检与安保相关工作',
    CLEANER: '负责保洁与基础服务',
    FINANCE: '负责账单、收费、财务统计'
  }[code] || '-'
}

function formatTime(time) {
  if (!time) return '-'
  return String(time).replace('T', ' ').slice(0, 16)
}

onMounted(loadRoles)
</script>

<style scoped>
.page-container {
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
}

.page-subtitle {
  margin-top: 6px;
  font-size: 13px;
  color: #909399;
}
.role-code {
  margin-top: 2px;
  font-size: 12px;
  color: #909399;
}

</style>
