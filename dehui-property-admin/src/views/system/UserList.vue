<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="page-header">
          <div>
            <div class="page-title">用户管理</div>
            <div class="page-subtitle">系统用户、状态与角色分配</div>
          </div>
          <el-button type="primary" @click="openCreateDialog">新增用户</el-button>
        </div>
      </template>

      <el-table :data="users" border stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" align="center" />
        <el-table-column prop="username" label="用户名" width="140" />
        <el-table-column prop="realName" label="姓名" width="140" />
        <el-table-column prop="phone" label="手机号" width="150" />

        <el-table-column label="已分配角色" min-width="220">
          <template #default="{ row }">
            <el-tag
              v-for="role in getUserRoleNames(row.id)"
              :key="role"
              type="success"
              style="margin-right: 6px; margin-bottom: 4px"
            >
              {{ role }}
            </el-tag>
            <span v-if="getUserRoleNames(row.id).length === 0" class="empty-text">未分配</span>
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

        <el-table-column label="操作" width="140" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openAssignRole(row)">分配角色</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" title="新增用户" width="520px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" />
        </el-form-item>

        <el-form-item label="姓名">
          <el-input v-model="form.realName" placeholder="请输入姓名" />
        </el-form-item>

        <el-form-item label="电话">
          <el-input v-model="form.phone" placeholder="请输入手机号" />
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
        <el-button type="primary" :loading="saving" @click="submitUser">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="roleDialog" title="分配角色" width="420px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="当前用户">
          <el-input :model-value="currentUserName" disabled />
        </el-form-item>

        <el-form-item label="选择角色">
          <el-select v-model="selectedRoleId" placeholder="请选择角色" style="width: 100%">
            <el-option
              v-for="role in roles"
              :key="role.id"
              :label="`${role.roleName}（${role.roleCode}）`"
              :value="role.id"
            />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="roleDialog = false">取消</el-button>
        <el-button type="primary" :loading="assigning" @click="assignRole">确定</el-button>
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
const assigning = ref(false)

const users = ref([])
const roles = ref([])
const userRolesMap = ref({})

const dialogVisible = ref(false)
const roleDialog = ref(false)
const formRef = ref(null)

const currentUserId = ref(null)
const currentUserName = ref('')
const selectedRoleId = ref(null)

const form = reactive({
  username: '',
  password: '',
  realName: '',
  phone: '',
  status: 'ACTIVE'
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

function unwrap(res) {
  if (res?.code === 200) return res.data
  if (res?.data?.code === 200) return res.data.data
  if (Array.isArray(res)) return res
  if (Array.isArray(res?.data)) return res.data
  return res?.data ?? res
}

async function loadUsers() {
  loading.value = true
  try {
    const res = await request.get('/system/users')
    users.value = unwrap(res) || []
    await loadAllUserRoles()
  } catch (e) {
    ElMessage.error(e.message || '加载用户列表失败')
  } finally {
    loading.value = false
  }
}

async function loadRoles() {
  try {
    const res = await request.get('/system/roles')
    roles.value = unwrap(res) || []
  } catch (e) {
    ElMessage.error(e.message || '加载角色列表失败')
  }
}

async function loadAllUserRoles() {
  const map = {}

  for (const user of users.value) {
    try {
      const res = await request.get(`/system/users/${user.id}/roles`)
      map[user.id] = unwrap(res) || []
    } catch (e) {
      map[user.id] = []
    }
  }

  userRolesMap.value = map
}

function getUserRoleNames(userId) {
  const userRoles = userRolesMap.value[userId] || []
  return userRoles
    .map(userRole => {
      const role = roles.value.find(item => item.id === userRole.roleId)
      return role ? role.roleName : `角色ID:${userRole.roleId}`
    })
    .filter(Boolean)
}

function resetForm() {
  form.username = ''
  form.password = ''
  form.realName = ''
  form.phone = ''
  form.status = 'ACTIVE'
}

function openCreateDialog() {
  resetForm()
  dialogVisible.value = true
}

async function submitUser() {
  if (!formRef.value) return

  await formRef.value.validate(async valid => {
    if (!valid) return

    saving.value = true
    try {
      await request.post('/system/users', {
        username: form.username,
        password: form.password,
        realName: form.realName,
        phone: form.phone,
        status: form.status
      })

      ElMessage.success('创建成功')
      dialogVisible.value = false
      await loadUsers()
    } catch (e) {
      ElMessage.error(e.message || '创建用户失败')
    } finally {
      saving.value = false
    }
  })
}

function openAssignRole(row) {
  currentUserId.value = row.id
  currentUserName.value = row.realName || row.username
  selectedRoleId.value = null
  roleDialog.value = true
}

async function assignRole() {
  if (!currentUserId.value || !selectedRoleId.value) {
    ElMessage.warning('请选择角色')
    return
  }

  assigning.value = true
  try {
    await request.post(`/system/users/${currentUserId.value}/roles/${selectedRoleId.value}`)
    ElMessage.success('分配成功')
    roleDialog.value = false
    await loadUsers()
  } catch (e) {
    ElMessage.error(e.message || '分配角色失败')
  } finally {
    assigning.value = false
  }
}

function formatTime(time) {
  if (!time) return '-'
  return String(time).replace('T', ' ').slice(0, 16)
}

onMounted(async () => {
  await loadRoles()
  await loadUsers()
})
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

.empty-text {
  color: #909399;
  font-size: 13px;
}
</style>
