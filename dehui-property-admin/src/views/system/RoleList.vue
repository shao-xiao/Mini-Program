<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="page-header">
          <div>
            <div class="page-title">角色管理</div>
            <div class="page-subtitle">维护角色、菜单范围和操作权限</div>
          </div>
          <el-button v-if="hasPermission('system:role:create')" type="primary" @click="openCreate">新增角色</el-button>
        </div>
      </template>

      <el-form :inline="true" :model="query" class="filter-form">
        <el-form-item label="角色编码"><el-input v-model="query.roleCode" clearable /></el-form-item>
        <el-form-item label="角色名称"><el-input v-model="query.roleName" clearable /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable style="width: 120px">
            <el-option label="启用" value="ENABLED" />
            <el-option label="禁用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadRoles">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="roles" border stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column prop="roleCode" label="角色编码" width="180" />
        <el-table-column prop="roleName" label="角色名称" width="180" />
        <el-table-column prop="description" label="角色说明" min-width="260" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }"><el-tag :type="row.status === 'DISABLED' ? 'info' : 'success'">{{ row.statusText }}</el-tag></template>
        </el-table-column>
        <el-table-column label="创建时间" width="150"><template #default="{ row }">{{ fmt(row.createdTime) }}</template></el-table-column>
        <el-table-column label="操作" width="360" fixed="right" align="center">
          <template #default="{ row }">
            <el-button v-if="hasPermission('system:role:update')" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="hasPermission('system:role:assign-permission')" size="small" type="primary" @click="openMenuDialog(row)">菜单</el-button>
            <el-button v-if="hasPermission('system:role:assign-permission')" size="small" type="primary" @click="openPermissionDialog(row)">权限</el-button>
            <el-button v-if="hasPermission('system:role:disable') && row.roleCode !== 'SUPER_ADMIN'" size="small" @click="changeStatus(row)">{{ row.status === 'DISABLED' ? '启用' : '禁用' }}</el-button>
            <el-button v-if="hasPermission('system:role:delete') && row.roleCode !== 'SUPER_ADMIN'" size="small" type="danger" @click="deleteRole(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑角色' : '新增角色'" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="角色编码" prop="roleCode"><el-input v-model="form.roleCode" :disabled="!!editingId" /></el-form-item>
        <el-form-item label="角色名称" prop="roleName"><el-input v-model="form.roleName" /></el-form-item>
        <el-form-item label="角色说明"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="form.status" style="width: 100%"><el-option label="启用" value="ENABLED" /><el-option label="禁用" value="DISABLED" /></el-select></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="menuDialog" title="分配菜单" width="520px" destroy-on-close>
      <el-tree
        ref="menuTreeRef"
        :data="menus"
        node-key="id"
        show-checkbox
        default-expand-all
        :props="{ label: 'menuName', children: 'children' }"
      />
      <template #footer>
        <el-button @click="menuDialog = false">取消</el-button>
        <el-button type="primary" @click="saveMenus">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="permissionDialog" title="分配权限" width="760px" destroy-on-close>
      <div v-for="group in permissionGroups" :key="group.module" class="perm-group">
        <div class="perm-title">{{ formatModuleName(group.module) }}</div>
        <el-checkbox-group v-model="selectedPermissionIds">
          <el-checkbox v-for="item in group.items" :key="item.id" :label="item.id">
            {{ formatPermissionName(item) }}
            <span class="perm-code">{{ item.permissionCode }}</span>
          </el-checkbox>
        </el-checkbox-group>
      </div>
      <template #footer>
        <el-button @click="permissionDialog = false">取消</el-button>
        <el-button type="primary" @click="savePermissions">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../utils/request'
import { hasPermission } from '../../utils/permission'

const loading = ref(false)
const saving = ref(false)
const roles = ref([])
const menus = ref([])
const permissions = ref([])
const dialogVisible = ref(false)
const menuDialog = ref(false)
const permissionDialog = ref(false)
const editingId = ref(null)
const currentRoleId = ref(null)
const formRef = ref()
const menuTreeRef = ref()
const selectedPermissionIds = ref([])

const query = reactive({ roleCode: '', roleName: '', status: '', page: 0, size: 100 })
const form = reactive({ roleCode: '', roleName: '', description: '', status: 'ENABLED' })
const rules = {
  roleCode: [{ required: true, message: '请输入角色编码', trigger: 'blur' }],
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }]
}

const moduleNameMap = {
  ai: 'AI 分析',
  announcement: '公告管理',
  asset: '资产管理',
  bill: '账单管理',
  contract: '合同管理',
  energy: '能耗管理',
  feerule: '收费规则',
  finance: '财务管理',
  inspection: '巡检管理',
  lease: '租赁管理',
  meeting: '会议管理',
  parking: '停车管理',
  'parking-bill': '停车账单',
  system: '系统管理',
  tenant: '租户管理',
  visitor: '访客管理',
  workorder: '工单管理'
}

const actionNameMap = {
  add: '新增',
  assign: '分配',
  'assign-permission': '分配权限',
  'assign-role': '分配角色',
  audit: '审核',
  close: '关闭',
  complete: '完成',
  create: '新增',
  delete: '删除',
  disable: '禁用',
  export: '导出',
  generate: '生成账单',
  pay: '收款',
  'reset-password': '重置密码',
  update: '编辑',
  upload: '上传',
  view: '查看'
}

const permissionNameMap = {
  'ai:view': '查看 AI 分析',
  'announcement:view': '查看公告',
  'asset:create': '新增资产',
  'asset:delete': '删除资产',
  'asset:update': '编辑资产',
  'asset:view': '查看资产',
  'bill:add': '新增账单',
  'bill:audit': '审核账单',
  'bill:pay': '确认收款',
  'bill:view': '查看账单',
  'contract:create': '新增合同',
  'contract:view': '查看合同',
  'energy:create': '新增能耗记录',
  'energy:view': '查看能耗',
  'feerule:add': '新增收费规则',
  'feerule:generate': '生成收费账单',
  'feerule:view': '查看收费规则',
  'finance:bill:audit': '财务审核账单',
  'finance:bill:create': '财务新增账单',
  'finance:bill:export': '导出财务账单',
  'finance:bill:pay': '财务确认收款',
  'finance:bill:view': '查看财务账单',
  'finance:invoice:upload': '上传发票',
  'inspection:view': '查看巡检',
  'lease:create': '新增租赁',
  'lease:delete': '删除租赁',
  'lease:update': '编辑租赁',
  'lease:view': '查看租赁',
  'meeting:create': '新增会议预约',
  'meeting:view': '查看会议',
  'parking:create': '新增车位',
  'parking:update': '编辑车位',
  'parking:view': '查看车位',
  'parking-bill:add': '新增停车账单',
  'parking-bill:pay': '停车账单收款',
  'parking-bill:view': '查看停车账单',
  'system:menu:view': '查看菜单',
  'system:permission:view': '查看权限',
  'system:role:assign-permission': '分配角色权限',
  'system:role:create': '新增角色',
  'system:role:delete': '删除角色',
  'system:role:disable': '禁用角色',
  'system:role:update': '编辑角色',
  'system:role:view': '查看角色',
  'system:user:assign-role': '分配用户角色',
  'system:user:create': '新增用户',
  'system:user:delete': '删除用户',
  'system:user:disable': '禁用用户',
  'system:user:reset-password': '重置用户密码',
  'system:user:update': '编辑用户',
  'system:user:view': '查看用户',
  'tenant:create': '新增租户',
  'tenant:portal:view': '查看租户门户',
  'tenant:view': '查看租户',
  'visitor:view': '查看访客',
  'workorder:assign': '分配工单',
  'workorder:close': '关闭工单',
  'workorder:complete': '完成工单',
  'workorder:create': '新增工单',
  'workorder:view': '查看工单'
}

const permissionGroups = computed(() => {
  const map = new Map()
  permissions.value.forEach(item => {
    const key = item.module || '其他'
    if (!map.has(key)) map.set(key, [])
    map.get(key).push(item)
  })
  return Array.from(map.entries()).map(([module, items]) => ({ module, items }))
})

function formatModuleName(module) {
  if (!module) return '其他'
  return moduleNameMap[module] || module
}

function formatPermissionName(item) {
  const code = item.permissionCode || ''
  if (permissionNameMap[code]) {
    return permissionNameMap[code]
  }

  const parts = code.split(':')
  const module = parts[0]
  const action = parts.slice(1).join(':')
  const actionText = actionNameMap[action] || actionNameMap[parts[parts.length - 1]]

  if (moduleNameMap[module] && actionText) {
    return `${actionText}${moduleNameMap[module]}`
  }

  return item.permissionName || code || '未命名权限'
}

function records(data) {
  if (Array.isArray(data)) return data
  return data?.records || []
}

async function loadRoles() {
  loading.value = true
  try {
    roles.value = records(await request.get('/system/roles', { params: { ...query } }))
  } finally {
    loading.value = false
  }
}

async function loadMeta() {
  const [menuData, permissionData] = await Promise.all([
    request.get('/system/menus'),
    request.get('/system/permissions')
  ])
  menus.value = menuData || []
  permissions.value = permissionData || []
}

function resetQuery() {
  Object.assign(query, { roleCode: '', roleName: '', status: '', page: 0 })
  loadRoles()
}

function resetForm() {
  Object.assign(form, { roleCode: '', roleName: '', description: '', status: 'ENABLED' })
}

function openCreate() {
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  Object.assign(form, row)
  dialogVisible.value = true
}

async function submit() {
  await formRef.value?.validate()
  saving.value = true
  try {
    if (editingId.value) {
      await request.put(`/system/roles/${editingId.value}`, { ...form })
    } else {
      await request.post('/system/roles', { ...form })
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await loadRoles()
  } finally {
    saving.value = false
  }
}

async function openMenuDialog(row) {
  currentRoleId.value = row.id
  menuDialog.value = true
  const ids = await request.get(`/system/roles/${row.id}/menus`)
  await nextTick()
  menuTreeRef.value?.setCheckedKeys(ids || [])
}

async function saveMenus() {
  const ids = menuTreeRef.value?.getCheckedKeys(false) || []
  await request.post(`/system/roles/${currentRoleId.value}/menus`, { ids })
  ElMessage.success('菜单权限已保存')
  menuDialog.value = false
}

async function openPermissionDialog(row) {
  currentRoleId.value = row.id
  selectedPermissionIds.value = await request.get(`/system/roles/${row.id}/permissions`) || []
  permissionDialog.value = true
}

async function savePermissions() {
  await request.post(`/system/roles/${currentRoleId.value}/permissions`, { ids: selectedPermissionIds.value })
  ElMessage.success('操作权限已保存')
  permissionDialog.value = false
}

async function changeStatus(row) {
  const next = row.status === 'DISABLED' ? 'ENABLED' : 'DISABLED'
  await request.patch(`/system/roles/${row.id}/status`, null, { params: { status: next } })
  ElMessage.success('状态已更新')
  await loadRoles()
}

async function deleteRole(row) {
  await ElMessageBox.confirm(`确定删除角色「${row.roleName}」吗？`, '删除确认', { type: 'warning' })
  await request.delete(`/system/roles/${row.id}`)
  ElMessage.success('删除成功')
  await loadRoles()
}

function fmt(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : '-'
}

onMounted(async () => {
  await loadMeta()
  await loadRoles()
})
</script>

<style scoped>
.page-container { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; }
.page-title { font-size: 18px; font-weight: 600; }
.page-subtitle { margin-top: 6px; color: #909399; font-size: 13px; }
.filter-form { margin-bottom: 14px; }
.perm-group { padding: 12px 0; border-bottom: 1px solid #ebeef5; }
.perm-title { font-weight: 600; margin-bottom: 10px; }
.perm-code { color: #909399; font-size: 12px; margin-left: 6px; }
</style>
