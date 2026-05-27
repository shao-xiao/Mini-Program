<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <div>
            <span>租户管理</span>
            <span class="header-tip">维护租户主档、联系人账号和小程序绑定入口</span>
          </div>
          <el-button type="primary" @click="openCreateDialog">新增租户</el-button>
        </div>
      </template>

      <el-table :data="tenants" border style="width: 100%">
        <el-table-column prop="tenantCode" label="租户编号" width="140" />
        <el-table-column prop="tenantName" label="租户名称" min-width="180" />
        <el-table-column prop="contactPerson" label="联系人" min-width="120" />
        <el-table-column label="联系电话" min-width="155">
          <template #default="{ row }">
            <span :class="{ 'invalid-contact': row.contactPhone && !isValidContactPhone(row.contactPhone) }">
              {{ contactText(row.contactPhone, 'phone') }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="邮箱" min-width="190">
          <template #default="{ row }">
            <span :class="{ 'invalid-contact': row.contactEmail && !isValidEmail(row.contactEmail) }">
              {{ contactText(row.contactEmail, 'email') }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="businessLicense" label="营业执照 / 统一信用代码" min-width="190" />
        <el-table-column label="状态" width="105" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" effect="plain">
              {{ row.status === 'ACTIVE' ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openEditDialog(row)">编辑</el-button>
            <el-button size="small" type="primary" @click="openContactDialog(row)">联系人</el-button>
            <el-button size="small" @click="openOverview(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑租户' : '新增租户'" width="560px">
      <el-form ref="tenantFormRef" :model="form" :rules="tenantRules" label-width="120px">
        <el-form-item label="租户名称" prop="tenantName">
          <el-input v-model="form.tenantName" />
        </el-form-item>

        <el-form-item label="联系人" prop="contactPerson">
          <el-input v-model="form.contactPerson" />
        </el-form-item>

        <el-form-item label="联系电话" prop="contactPhone">
          <el-input v-model="form.contactPhone" placeholder="例如 13800000000 或 021-88888888" />
        </el-form-item>

        <el-form-item label="邮箱" prop="contactEmail">
          <el-input v-model="form.contactEmail" placeholder="例如 name@example.com" />
        </el-form-item>

        <el-form-item label="营业执照" prop="businessLicense">
          <el-input v-model="form.businessLicense" />
        </el-form-item>

        <el-form-item label="状态" prop="status">
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
      width="820px"
    >
      <div class="contact-toolbar">
        <el-button type="primary" @click="openContactForm">新增联系人</el-button>
      </div>

      <el-table :data="contacts" border style="width: 100%">
        <el-table-column prop="name" label="姓名" min-width="120" />
        <el-table-column label="手机号 / 账号" min-width="150">
          <template #default="{ row }">
            <span :class="{ 'invalid-contact': row.phone && !isValidContactPhone(row.phone) }">
              {{ contactText(row.phone, 'phone') }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="邮箱" min-width="180">
          <template #default="{ row }">
            <span :class="{ 'invalid-contact': row.email && !isValidEmail(row.email) }">
              {{ contactText(row.email, 'email') }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="role" label="角色" min-width="120" />
        <el-table-column label="主联系人" width="100" align="center">
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
        <el-table-column label="操作" width="230">
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

    <el-dialog v-model="contactFormVisible" :title="contactForm.id ? '编辑联系人' : '新增联系人'" width="540px">
      <el-form ref="contactFormRef" :model="contactForm" :rules="contactRules" label-width="115px">
        <el-form-item label="姓名" prop="name">
          <el-input v-model="contactForm.name" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="contactForm.phone" placeholder="例如 13800000000" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="contactForm.email" placeholder="例如 name@example.com" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-input v-model="contactForm.role" placeholder="如：财务 / 行政 / 负责人" />
        </el-form-item>
        <el-form-item label="主联系人" prop="isPrimary">
          <el-switch v-model="contactForm.isPrimary" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
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

    <el-drawer
      v-model="overviewVisible"
      :title="overview?.tenant ? `租户详情 - ${overview.tenant.tenantName}` : '租户详情'"
      size="72%"
      destroy-on-close
    >
      <el-skeleton v-if="overviewLoading" :rows="8" animated />

      <template v-else-if="overview">
        <el-alert
          :title="overview.miniProgramVisibility?.message || '已维护且启用的联系人，可用于后续小程序绑定和账单查看。'"
          :type="overview.contacts?.length ? 'success' : 'warning'"
          show-icon
          :closable="false"
          class="overview-alert"
        />

        <el-tabs v-model="overviewTab">
          <el-tab-pane label="总览" name="summary">
            <div class="summary-grid">
              <div class="summary-card">
                <div class="summary-label">联系人数量</div>
                <div class="summary-value">{{ overview.contacts?.length || 0 }}</div>
              </div>
              <div class="summary-card">
                <div class="summary-label">已发布账单</div>
                <div class="summary-value">{{ overview.billSummary?.publishedCount || 0 }}</div>
              </div>
              <div class="summary-card">
                <div class="summary-label">待缴金额</div>
                <div class="summary-value">{{ money(overview.billSummary?.unpaidAmount) }}</div>
              </div>
              <div class="summary-card">
                <div class="summary-label">租户状态</div>
                <div class="summary-value">{{ overview.tenant.status || '-' }}</div>
              </div>
            </div>

            <el-descriptions :column="2" border>
              <el-descriptions-item label="租户名称">{{ overview.tenant.tenantName }}</el-descriptions-item>
              <el-descriptions-item label="状态">{{ overview.tenant.status || '-' }}</el-descriptions-item>
              <el-descriptions-item label="联系人">{{ overview.tenant.contactPerson || '-' }}</el-descriptions-item>
              <el-descriptions-item label="联系电话">
                <span :class="{ 'invalid-contact': overview.tenant.contactPhone && !isValidContactPhone(overview.tenant.contactPhone) }">
                  {{ contactText(overview.tenant.contactPhone, 'phone') }}
                </span>
              </el-descriptions-item>
              <el-descriptions-item label="邮箱">
                <span :class="{ 'invalid-contact': overview.tenant.contactEmail && !isValidEmail(overview.tenant.contactEmail) }">
                  {{ contactText(overview.tenant.contactEmail, 'email') }}
                </span>
              </el-descriptions-item>
              <el-descriptions-item label="营业执照">{{ overview.tenant.businessLicense || '-' }}</el-descriptions-item>
            </el-descriptions>
          </el-tab-pane>

          <el-tab-pane label="联系人账号" name="contacts">
            <div class="drawer-toolbar">
              <el-button type="primary" @click="openContactDialog(overview.tenant)">维护联系人</el-button>
            </div>
            <el-table :data="overview.contacts || []" border>
              <el-table-column prop="name" label="姓名" />
              <el-table-column label="手机号 / 账号">
                <template #default="{ row }">
                  <span :class="{ 'invalid-contact': row.phone && !isValidContactPhone(row.phone) }">
                    {{ contactText(row.phone, 'phone') }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="邮箱">
                <template #default="{ row }">
                  <span :class="{ 'invalid-contact': row.email && !isValidEmail(row.email) }">
                    {{ contactText(row.email, 'email') }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="角色">
                <template #default="{ row }">{{ row.roleText || row.role || '-' }}</template>
              </el-table-column>
              <el-table-column label="主联系人" width="90">
                <template #default="{ row }"><el-tag v-if="row.isPrimary" type="success">是</el-tag><span v-else>-</span></template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="合同与入驻" name="contracts">
            <el-table :data="overview.contracts || []" border>
              <el-table-column prop="contractNumber" label="合同编号" min-width="140" />
              <el-table-column prop="roomName" label="房间" min-width="120" />
              <el-table-column label="周期" min-width="190">
                <template #default="{ row }">{{ row.startDate || '-' }} 至 {{ row.endDate || '-' }}</template>
              </el-table-column>
              <el-table-column label="费用" min-width="160">
                <template #default="{ row }">租金 {{ money(row.rentAmount) }} / 物业 {{ money(row.propertyFeeAmount) }}</template>
              </el-table-column>
              <el-table-column label="状态" width="100">
                <template #default="{ row }">{{ row.statusText || row.status }}</template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="账单" name="bills">
            <el-alert
              title="待审核账单租户小程序不可见；审核发布后，已绑定该租户的联系人可在小程序账单中心查看。"
              type="info"
              show-icon
              :closable="false"
              class="overview-alert"
            />
            <el-table :data="overview.recentBills || []" border>
              <el-table-column prop="billNumber" label="账单编号" min-width="150" />
              <el-table-column label="类型" width="110">
                <template #default="{ row }">{{ row.billTypeText || row.billType }}</template>
              </el-table-column>
              <el-table-column label="账期" min-width="190">
                <template #default="{ row }">{{ row.periodStart || '-' }} 至 {{ row.periodEnd || '-' }}</template>
              </el-table-column>
              <el-table-column label="金额" width="120">
                <template #default="{ row }">{{ money(row.amount) }}</template>
              </el-table-column>
              <el-table-column label="审核" width="100">
                <template #default="{ row }">
                  <el-tag :type="row.auditStatus === 'APPROVED' ? 'success' : 'warning'">{{ row.auditStatusText }}</el-tag>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../utils/request'
import { readPage } from '../../utils/pagination'
import {
  isValidContactPhone,
  isValidEmail,
  optionalContactPhoneRule,
  optionalEmailRule,
  validateContactPhone
} from '../../utils/contactValidation'

const tenants = ref([])
const dialogVisible = ref(false)
const contactDialogVisible = ref(false)
const contactFormVisible = ref(false)
const overviewVisible = ref(false)
const overviewLoading = ref(false)
const overviewTab = ref('summary')
const contacts = ref([])
const currentTenant = ref(null)
const overview = ref(null)
const tenantFormRef = ref()
const contactFormRef = ref()

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
  email: '',
  role: '',
  isPrimary: false,
  status: 'ACTIVE'
})

const requiredContactPhoneRule = {
  validator(rule, value, callback) {
    if (!String(value || '').trim()) {
      callback(new Error('请输入联系电话'))
      return
    }
    validateContactPhone(rule, value, callback)
  },
  trigger: 'blur'
}

const tenantRules = {
  tenantName: [{ required: true, message: '请输入租户名称', trigger: 'blur' }],
  contactPhone: [optionalContactPhoneRule],
  contactEmail: [optionalEmailRule]
}

const contactRules = {
  name: [{ required: true, message: '请输入联系人姓名', trigger: 'blur' }],
  phone: [requiredContactPhoneRule],
  email: [optionalEmailRule]
}

function resetForm() {
  Object.assign(form, {
    id: null,
    tenantName: '',
    contactPerson: '',
    contactPhone: '',
    contactEmail: '',
    businessLicense: '',
    status: 'ACTIVE'
  })
  tenantFormRef.value?.clearValidate()
}

function resetContactForm() {
  Object.assign(contactForm, {
    id: null,
    name: '',
    phone: '',
    email: '',
    role: '',
    isPrimary: false,
    status: 'ACTIVE'
  })
  contactFormRef.value?.clearValidate()
}

async function loadTenants() {
  const data = await request.get('/tenant/list')
  tenants.value = readPage(data).records
}

async function loadContacts() {
  if (!currentTenant.value) return
  const data = await request.get(`/tenant/${currentTenant.value.id}/contacts`)
  contacts.value = readPage(data).records
}

async function loadOverview(tenantId) {
  overviewLoading.value = true
  try {
    overview.value = await request.get(`/tenant/${tenantId}/overview`)
  } finally {
    overviewLoading.value = false
  }
}

function openCreateDialog() {
  resetForm()
  dialogVisible.value = true
}

function openEditDialog(row) {
  resetForm()
  Object.assign(form, row)
  dialogVisible.value = true
}

async function openContactDialog(row) {
  currentTenant.value = row
  contactDialogVisible.value = true
  await loadContacts()
}

async function openOverview(row) {
  overview.value = null
  overviewTab.value = 'summary'
  overviewVisible.value = true
  await loadOverview(row.id)
}

function openContactForm() {
  resetContactForm()
  contactFormVisible.value = true
}

function editContact(row) {
  resetContactForm()
  Object.assign(contactForm, {
    id: row.id,
    name: row.name || '',
    phone: row.phone || '',
    email: row.email || '',
    role: row.role || '',
    isPrimary: Boolean(row.isPrimary),
    status: row.status || 'ACTIVE'
  })
  contactFormVisible.value = true
}

async function saveTenant() {
  const valid = await tenantFormRef.value.validate().catch(() => false)
  if (!valid) return
  if (!isValidContactPhone(form.contactPhone)) {
    ElMessage.warning('请输入正确的联系电话，例如 13800000000 或 021-88888888')
    return
  }
  if (!isValidEmail(form.contactEmail)) {
    ElMessage.warning('请输入正确的邮箱地址，例如 name@example.com')
    return
  }

  await request.post('/tenant/save', { ...form })
  ElMessage.success('保存成功')

  dialogVisible.value = false
  await loadTenants()
  if (overviewVisible.value && form.id) {
    await loadOverview(form.id)
  }
}

async function saveContact() {
  if (!currentTenant.value) return
  const valid = await contactFormRef.value.validate().catch(() => false)
  if (!valid) return
  if (!String(contactForm.phone || '').trim()) {
    ElMessage.warning('请输入联系电话')
    return
  }
  if (!isValidContactPhone(contactForm.phone)) {
    ElMessage.warning('请输入正确的联系电话，例如 13800000000 或 021-88888888')
    return
  }
  if (!isValidEmail(contactForm.email)) {
    ElMessage.warning('请输入正确的邮箱地址，例如 name@example.com')
    return
  }

  const data = await request.post(`/tenant/${currentTenant.value.id}/contacts`, {
    id: contactForm.id,
    name: contactForm.name,
    phone: contactForm.phone,
    email: contactForm.email,
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
  if (overviewVisible.value && currentTenant.value) {
    await loadOverview(currentTenant.value.id)
  }
}

async function deactivateContact(row) {
  await ElMessageBox.confirm(`确定停用联系人「${row.name}」吗？停用后无法绑定小程序。`, '停用确认', { type: 'warning' })
  await request.post(`/tenant/contacts/${row.id}/deactivate`)
  ElMessage.success('联系人已停用')
  await loadContacts()
  if (overviewVisible.value && currentTenant.value) {
    await loadOverview(currentTenant.value.id)
  }
}

async function resetPassword(row) {
  await ElMessageBox.confirm(`确定重置联系人「${row.name}」的初始密码吗？`, '重置确认', { type: 'warning' })
  const data = await request.post(`/tenant/contacts/${row.id}/reset-password`)
  await loadContacts()
  if (data?.initialPassword) {
    showPassword(data.initialPassword)
  }
  if (overviewVisible.value && currentTenant.value) {
    await loadOverview(currentTenant.value.id)
  }
}

function contactText(value, type) {
  if (!value) return '-'
  const valid = type === 'phone' ? isValidContactPhone(value) : isValidEmail(value)
  return valid ? value : `${value}（格式异常）`
}

function money(value) {
  return Number(value || 0).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })
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

.invalid-contact {
  color: #d93025;
  font-weight: 600;
}

.contact-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 14px;
}

.overview-alert {
  margin-bottom: 16px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.summary-card {
  padding: 14px 16px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  background: #fafafa;
}

.summary-label {
  color: #909399;
  font-size: 13px;
}

.summary-value {
  margin-top: 8px;
  color: #303133;
  font-size: 20px;
  font-weight: 600;
}

.drawer-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}

@media (max-width: 900px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
