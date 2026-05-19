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
        <el-table-column label="操作" width="250">
          <template #default="{ row }">
            <el-button size="small" @click="openEditDialog(row)">编辑</el-button>
            <el-button size="small" type="primary" @click="openContactDialog(row)">联系人</el-button>
            <el-button size="small" @click="openOverview(row)">详情</el-button>
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

    <el-drawer
      v-model="overviewVisible"
      :title="overview?.tenant ? `租户详情 - ${overview.tenant.tenantName}` : '租户详情'"
      size="72%"
      destroy-on-close
    >
      <el-skeleton v-if="overviewLoading" :rows="8" animated />

      <template v-else-if="overview">
        <el-alert
          :title="overview.miniProgramVisibility?.message || '已发布账单可由已绑定联系人在小程序查看。'"
          :type="overview.miniProgramVisibility?.visibleContactCount > 0 ? 'success' : 'warning'"
          show-icon
          :closable="false"
          class="overview-alert"
        />

        <el-tabs v-model="overviewTab">
          <el-tab-pane label="总览" name="summary">
            <div class="summary-grid">
              <div class="summary-card">
                <div class="summary-label">有效联系人</div>
                <div class="summary-value">{{ overview.miniProgramVisibility?.hasActiveContact ? '有' : '无' }}</div>
              </div>
              <div class="summary-card">
                <div class="summary-label">已绑定小程序联系人</div>
                <div class="summary-value">{{ overview.miniProgramVisibility?.boundContactCount || 0 }}</div>
              </div>
              <div class="summary-card">
                <div class="summary-label">已发布账单</div>
                <div class="summary-value">{{ overview.billSummary?.publishedCount || 0 }}</div>
              </div>
              <div class="summary-card">
                <div class="summary-label">待缴金额</div>
                <div class="summary-value">{{ money(overview.billSummary?.unpaidAmount) }}</div>
              </div>
            </div>

            <el-descriptions :column="2" border>
              <el-descriptions-item label="租户名称">{{ overview.tenant.tenantName }}</el-descriptions-item>
              <el-descriptions-item label="状态">{{ overview.tenant.status || '-' }}</el-descriptions-item>
              <el-descriptions-item label="联系人">{{ overview.tenant.contactPerson || '-' }}</el-descriptions-item>
              <el-descriptions-item label="联系电话">{{ overview.tenant.contactPhone || '-' }}</el-descriptions-item>
              <el-descriptions-item label="邮箱">{{ overview.tenant.contactEmail || '-' }}</el-descriptions-item>
              <el-descriptions-item label="营业执照">{{ overview.tenant.businessLicense || '-' }}</el-descriptions-item>
            </el-descriptions>
          </el-tab-pane>

          <el-tab-pane label="联系人账号" name="contacts">
            <div class="drawer-toolbar">
              <el-button type="primary" @click="openContactDialog(overview.tenant)">维护联系人</el-button>
            </div>
            <el-table :data="overview.contacts || []" border>
              <el-table-column prop="name" label="姓名" />
              <el-table-column prop="phone" label="手机号/账号" />
              <el-table-column label="角色">
                <template #default="{ row }">{{ row.roleText || row.role || '-' }}</template>
              </el-table-column>
              <el-table-column label="主联系人" width="90">
                <template #default="{ row }"><el-tag v-if="row.isPrimary" type="success">是</el-tag><span v-else>-</span></template>
              </el-table-column>
              <el-table-column label="小程序绑定" width="120">
                <template #default="{ row }">
                  <el-tag :type="row.boundMiniProgram ? 'success' : 'info'">{{ row.boundMiniProgram ? '已绑定' : '未绑定' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="lastBoundAt" label="最近绑定" />
              <el-table-column prop="lastLoginAtText" label="最近登录" />
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

            <div class="section-title">入驻记录</div>
            <el-table :data="overview.occupancies || []" border>
              <el-table-column prop="contractNumber" label="合同" />
              <el-table-column prop="roomName" label="房间" />
              <el-table-column prop="checkInDate" label="入驻日期" />
              <el-table-column prop="plannedEndDate" label="计划结束" />
              <el-table-column prop="checkoutDate" label="退租日期" />
              <el-table-column prop="status" label="状态" />
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
              <el-table-column label="小程序可见" width="120">
                <template #default="{ row }">
                  <el-tag :type="row.visibleToTenantMiniProgram ? 'success' : 'info'">{{ row.visibleToTenantMiniProgram ? '可见' : '不可见' }}</el-tag>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="小程序可见性" name="visibility">
            <el-descriptions :column="1" border>
              <el-descriptions-item label="说明">{{ overview.miniProgramVisibility?.message }}</el-descriptions-item>
              <el-descriptions-item label="已绑定联系人">{{ overview.miniProgramVisibility?.boundContactCount || 0 }}</el-descriptions-item>
              <el-descriptions-item label="可看账单联系人">{{ overview.miniProgramVisibility?.visibleContactCount || 0 }}</el-descriptions-item>
            </el-descriptions>
            <div class="section-title">可查看账单的联系人</div>
            <el-table :data="overview.miniProgramVisibility?.visibleContacts || []" border>
              <el-table-column prop="name" label="姓名" />
              <el-table-column prop="phone" label="手机号" />
              <el-table-column label="角色">
                <template #default="{ row }">{{ row.roleText || row.role || '-' }}</template>
              </el-table-column>
              <el-table-column prop="lastBoundAt" label="最近绑定" />
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </template>
    </el-drawer>
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
const overviewVisible = ref(false)
const overviewLoading = ref(false)
const overviewTab = ref('summary')
const contacts = ref([])
const currentTenant = ref(null)
const overview = ref(null)

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

const loadOverview = async (tenantId) => {
  overviewLoading.value = true
  try {
    overview.value = await request.get(`/tenant/${tenantId}/overview`)
  } finally {
    overviewLoading.value = false
  }
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

const openOverview = async (row) => {
  overview.value = null
  overviewTab.value = 'summary'
  overviewVisible.value = true
  await loadOverview(row.id)
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
  if (overviewVisible.value && currentTenant.value) {
    await loadOverview(currentTenant.value.id)
  }
}

const deactivateContact = async (row) => {
  await ElMessageBox.confirm(`确定停用联系人「${row.name}」吗？停用后无法绑定小程序。`, '停用确认', { type: 'warning' })
  await request.post(`/tenant/contacts/${row.id}/deactivate`)
  ElMessage.success('联系人已停用')
  await loadContacts()
  if (overviewVisible.value && currentTenant.value) {
    await loadOverview(currentTenant.value.id)
  }
}

const resetPassword = async (row) => {
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

.section-title {
  margin: 18px 0 10px;
  color: #303133;
  font-weight: 600;
}

@media (max-width: 900px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
