<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="page-header">
          <div>
            <div class="page-title">用户管理</div>
            <div class="page-subtitle">统一维护后台账号、工程师账号、财务账号和租户账号</div>
          </div>
          <el-button v-if="hasPermission('system:user:create')" type="primary" @click="openCreate">新增用户</el-button>
        </div>
      </template>

      <el-form :inline="true" :model="query" class="filter-form">
        <el-form-item label="用户名"><el-input v-model="query.username" clearable /></el-form-item>
        <el-form-item label="姓名"><el-input v-model="query.realName" clearable /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="query.phone" clearable /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="query.userType" clearable style="width: 150px">
            <el-option v-for="item in userTypes" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable style="width: 120px">
            <el-option label="启用" value="ENABLED" />
            <el-option label="禁用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadUsers">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="users" border stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column prop="username" label="用户名" width="130" />
        <el-table-column prop="realName" label="姓名" width="120" />
        <el-table-column prop="phone" label="手机号" width="140" />
        <el-table-column prop="userTypeText" label="用户类型" width="140" />
        <el-table-column prop="tenantName" label="所属租户" min-width="150" show-overflow-tooltip />
        <el-table-column label="角色" min-width="220">
          <template #default="{ row }">
            <el-tag v-for="role in row.roleNames" :key="role" type="success" class="role-tag">{{ role }}</el-tag>
            <span v-if="!row.roleNames || row.roleNames.length === 0" class="muted">未分配</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'DISABLED' ? 'info' : 'success'">{{ row.statusText }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最近登录" width="150"><template #default="{ row }">{{ fmt(row.lastLoginAt) }}</template></el-table-column>
        <el-table-column label="创建时间" width="150"><template #default="{ row }">{{ fmt(row.createdTime) }}</template></el-table-column>
        <el-table-column label="操作" width="360" fixed="right" align="center">
          <template #default="{ row }">
            <el-button v-if="hasPermission('system:user:update')" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="hasPermission('system:user:assign-role')" size="small" type="primary" @click="openRoleDialog(row)">角色</el-button>
            <el-button v-if="hasPermission('system:user:reset-password')" size="small" @click="resetPassword(row)">重置密码</el-button>
            <el-button
              v-if="hasPermission('system:user:disable') && row.username !== 'admin'"
              size="small"
              :type="row.status === 'DISABLED' ? 'success' : 'warning'"
              @click="changeStatus(row)"
            >
              {{ row.status === 'DISABLED' ? '启用' : '禁用' }}
            </el-button>
            <el-button v-if="hasPermission('system:user:delete') && row.username !== 'admin'" size="small" type="danger" @click="deleteUser(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          layout="total, sizes, prev, pager, next"
          :total="total"
          v-model:current-page="currentPage"
          v-model:page-size="query.size"
          @current-change="loadUsers"
          @size-change="loadUsers"
        />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑用户' : '新增用户'" width="620px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="用户名" prop="username"><el-input v-model="form.username" /></el-form-item>
        <el-form-item v-if="!editingId" label="初始密码" prop="password"><el-input v-model="form.password" type="password" show-password /></el-form-item>
        <el-form-item label="姓名"><el-input v-model="form.realName" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="form.email" /></el-form-item>
        <el-form-item label="用户类型" prop="userType">
          <el-select v-model="form.userType" style="width: 100%">
            <el-option v-for="item in userTypes" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.userType === 'TENANT'" label="关联租户" prop="tenantId">
          <el-select v-model="form.tenantId" filterable clearable style="width: 100%">
            <el-option v-for="tenant in tenants" :key="tenant.id" :label="tenant.tenantName" :value="tenant.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="部门"><el-input v-model="form.department" /></el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.roleIds" multiple style="width: 100%">
            <el-option v-for="role in roles" :key="role.id" :label="`${role.roleName} (${role.roleCode})`" :value="role.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态"><el-select v-model="form.status" style="width: 100%"><el-option label="启用" value="ENABLED" /><el-option label="禁用" value="DISABLED" /></el-select></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="roleDialog" title="分配角色" width="520px" destroy-on-close>
      <el-select v-model="roleForm.roleIds" multiple style="width: 100%">
        <el-option v-for="role in roles" :key="role.id" :label="`${role.roleName} (${role.roleCode})`" :value="role.id" />
      </el-select>
      <template #footer>
        <el-button @click="roleDialog = false">取消</el-button>
        <el-button type="primary" @click="saveRoles">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../utils/request'
import { hasPermission } from '../../utils/permission'

const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const roleDialog = ref(false)
const formRef = ref()
const users = ref([])
const roles = ref([])
const tenants = ref([])
const total = ref(0)
const editingId = ref(null)
const currentPage = computed({
  get: () => query.page + 1,
  set: value => { query.page = value - 1 }
})

const userTypes = [
  { label: '系统管理员', value: 'ADMIN' },
  { label: '运营/管理人员', value: 'MANAGER' },
  { label: '财务人员', value: 'FINANCE' },
  { label: '工程维修人员', value: 'ENGINEER' },
  { label: '安保人员', value: 'SECURITY' },
  { label: '保洁人员', value: 'CLEANER' },
  { label: '租户用户', value: 'TENANT' },
  { label: '普通员工', value: 'STAFF' }
]

const query = reactive({ username: '', realName: '', phone: '', userType: '', status: '', page: 0, size: 20 })
const form = reactive({ username: '', password: '123456', realName: '', phone: '', email: '', userType: 'STAFF', tenantId: null, department: '', status: 'ENABLED', roleIds: [] })
const roleForm = reactive({ userId: null, roleIds: [] })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入初始密码', trigger: 'blur' }],
  userType: [{ required: true, message: '请选择用户类型', trigger: 'change' }]
}

function records(data) {
  if (Array.isArray(data)) return { records: data, total: data.length }
  return { records: data?.records || [], total: data?.total || 0 }
}

async function loadUsers() {
  loading.value = true
  try {
    const data = await request.get('/system/users', { params: { ...query } })
    const page = records(data)
    users.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}

async function loadRoles() {
  const data = await request.get('/system/roles', { params: { size: 500 } })
  roles.value = records(data).records
}

async function loadTenants() {
  tenants.value = await request.get('/tenant/list')
}

function resetQuery() {
  Object.assign(query, { username: '', realName: '', phone: '', userType: '', status: '', page: 0 })
  loadUsers()
}

function resetForm() {
  Object.assign(form, { username: '', password: '123456', realName: '', phone: '', email: '', userType: 'STAFF', tenantId: null, department: '', status: 'ENABLED', roleIds: [] })
}

function openCreate() {
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  Object.assign(form, { ...row, password: '', roleIds: row.roleIds || [] })
  dialogVisible.value = true
}

async function submit() {
  await formRef.value?.validate()
  saving.value = true
  try {
    const payload = { ...form }
    if (editingId.value) {
      delete payload.password
      await request.put(`/system/users/${editingId.value}`, payload)
    } else {
      await request.post('/system/users', payload)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await loadUsers()
  } finally {
    saving.value = false
  }
}

function openRoleDialog(row) {
  roleForm.userId = row.id
  roleForm.roleIds = [...(row.roleIds || [])]
  roleDialog.value = true
}

async function saveRoles() {
  await request.post(`/system/users/${roleForm.userId}/roles`, { ids: roleForm.roleIds })
  ElMessage.success('角色已更新')
  roleDialog.value = false
  await loadUsers()
}

async function changeStatus(row) {
  const next = row.status === 'DISABLED' ? 'ENABLED' : 'DISABLED'
  await request.patch(`/system/users/${row.id}/status`, null, { params: { status: next } })
  ElMessage.success('状态已更新')
  await loadUsers()
}

async function resetPassword(row) {
  const data = await request.patch(`/system/users/${row.id}/reset-password`)
  await ElMessageBox.alert(`新密码：${data.password}`, '密码已重置', { confirmButtonText: '知道了' })
}

async function deleteUser(row) {
  await ElMessageBox.confirm(`确定删除用户「${row.realName || row.username}」吗？`, '删除确认', { type: 'warning' })
  await request.delete(`/system/users/${row.id}`)
  ElMessage.success('删除成功')
  await loadUsers()
}

function fmt(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : '-'
}

onMounted(async () => {
  await Promise.all([loadRoles(), loadTenants()])
  await loadUsers()
})
</script>

<style scoped>
.page-container { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; }
.page-title { font-size: 18px; font-weight: 600; }
.page-subtitle { margin-top: 6px; color: #909399; font-size: 13px; }
.filter-form { margin-bottom: 14px; }
.role-tag { margin-right: 6px; margin-bottom: 4px; }
.muted { color: #909399; }
.pager { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
