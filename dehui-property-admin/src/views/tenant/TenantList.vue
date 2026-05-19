<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <div>
            <span>租户管理</span>
            <span class="header-tip">租户可由合同台账自动生成，也可在此维护联系人账号</span>
          </div>
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
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-button size="small" @click="openEditDialog(row)">编辑</el-button>
            <el-button size="small" type="primary" @click="openContactDialog(row)">联系人</el-button>
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

    <el-dialog
      v-model="contactDialogVisible"
      :title="currentTenant ? `联系人账号 - ${currentTenant.tenantName}` : '联系人账号'"
      width="760px"
    >
      <div class="contact-toolbar">
        <el-button type="primary" @click="openContactForm">新增联系人</el-button>
      </div>

      <el-table :data="contacts" border style="width: 100%">
        <el-table-column prop="name" label="姓名" min-width="120" />
        <el-table-column prop="phone" label="手机号/账号" min-width="140" />
        <el-table-column prop="role" label="角色" min-width="120" />
        <el-table-column label="主联系人" width="95" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isPrimary" type="success">是</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="95" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">
              {{ row.status === 'ACTIVE' ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastBindTime" label="最近绑定" min-width="170" />
        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <el-button size="small" @click="editContact(row)">编辑</el-button>
            <el-button size="small" @click="resetPassword(row)">重置密码</el-button>
            <el-button size="small" type="danger" :disabled="row.status !== 'ACTIVE'" @click="deactivateContact(row)">
              停用
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="contactFormVisible" :title="contactForm.id ? '编辑联系人' : '新增联系人'" width="520px">
      <el-form :model="contactForm" label-width="110px">
        <el-form-item label="姓名">
          <el-input v-model="contactForm.name" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="contactForm.phone" />
        </el-form-item>
        <el-form-item label="角色">
          <el-input v-model="contactForm.role" placeholder="如 财务/行政/负责人" />
        </el-form-item>
        <el-form-item label="主联系人">
          <el-switch v-model="contactForm.isPrimary" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="contactForm.status" style="width: 100%">
            <el-option label="正常" value="ACTIVE" />
            <el-option label="停用" value="INACTIVE" />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="contactFormVisible = false">取消</el-button>
        <el-button type="primary" @click="saveContact">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../utils/request'

const tenants = ref([])
const dialogVisible = ref(false)
const contactDialogVisible = ref(false)
const contactFormVisible = ref(false)
const contacts = ref([])
const currentTenant = ref(null)

const form = reactive({
  id: null,
  tenantName: '',
  contactPerson: '',
  contactPhone: '',
  contactEmail: '',
  businessLicense: '',
  status: 'ACTIVE'
})

const contactForm = reactive({
  id: null,
  name: '',
  phone: '',
  role: '',
  isPrimary: false,
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

const resetContactForm = () => {
  contactForm.id = null
  contactForm.name = ''
  contactForm.phone = ''
  contactForm.role = ''
  contactForm.isPrimary = false
  contactForm.status = 'ACTIVE'
}

const loadTenants = async () => {
  const data = await request.get('/tenant/list')
  tenants.value = data || []
}

const loadContacts = async () => {
  if (!currentTenant.value) return
  const data = await request.get(`/tenant/${currentTenant.value.id}/contacts`)
  contacts.value = Array.isArray(data) ? data : []
}

const openCreateDialog = () => {
  resetForm()
  dialogVisible.value = true
}

const openEditDialog = (row) => {
  Object.assign(form, row)
  dialogVisible.value = true
}

const openContactDialog = async (row) => {
  currentTenant.value = row
  contactDialogVisible.value = true
  await loadContacts()
}

const openContactForm = () => {
  resetContactForm()
  contactFormVisible.value = true
}

const editContact = (row) => {
  Object.assign(contactForm, row)
  contactFormVisible.value = true
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

const saveContact = async () => {
  if (!currentTenant.value) return
  if (!contactForm.name || !contactForm.phone) {
    ElMessage.warning('请输入联系人姓名和手机号')
    return
  }

  const data = await request.post(`/tenant/${currentTenant.value.id}/contacts`, {
    name: contactForm.name,
    phone: contactForm.phone,
    role: contactForm.role,
    isPrimary: contactForm.isPrimary,
    status: contactForm.status
  })
  contactFormVisible.value = false
  await loadContacts()

  if (data?.initialPassword) {
    showPassword(data.initialPassword)
  } else {
    ElMessage.success('联系人已保存')
  }
}

const deactivateContact = async (row) => {
  await ElMessageBox.confirm(`确定停用联系人「${row.name}」吗？停用后无法绑定小程序。`, '停用确认', { type: 'warning' })
  await request.post(`/tenant/contacts/${row.id}/deactivate`)
  ElMessage.success('联系人已停用')
  await loadContacts()
}

const resetPassword = async (row) => {
  await ElMessageBox.confirm(`确定重置联系人「${row.name}」的初始密码吗？`, '重置确认', { type: 'warning' })
  const data = await request.post(`/tenant/contacts/${row.id}/reset-password`)
  await loadContacts()
  if (data?.initialPassword) {
    showPassword(data.initialPassword)
  }
}

function showPassword(password) {
  ElMessageBox.alert(`初始密码：${password}\n请告知租户联系人，该密码只在本次生成时显示。`, '租户联系人初始密码', {
    confirmButtonText: '我已记录'
  })
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

.header-tip {
  margin-left: 12px;
  color: #909399;
  font-size: 13px;
}

.contact-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 14px;
}
</style>
