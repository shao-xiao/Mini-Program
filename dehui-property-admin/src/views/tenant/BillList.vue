<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="page-header">
          <div>
            <div class="page-title">账单管理</div>
            <div class="page-subtitle">后台创建或自动生成的账单需审核发布后，租户小程序才可见。</div>
          </div>
          <div class="header-actions">
            <el-button :loading="exporting" @click="exportBills">导出 Excel</el-button>
            <el-button v-if="hasPermission('bill:add')" type="primary" @click="openCreateDialog">
              新增账单
            </el-button>
          </div>
        </div>
      </template>

      <el-empty v-if="!hasPermission('bill:view')" description="当前角色无权查看账单" />

      <template v-else>
        <el-form :inline="true" :model="queryForm" class="query-form">
          <el-form-item label="关键词">
            <el-input
              v-model="queryForm.keyword"
              clearable
              placeholder="账单编号/租户/合同/类型"
              style="width: 220px"
              @keyup.enter="searchBills"
            />
          </el-form-item>

          <el-form-item label="租户">
            <el-select v-model="queryForm.tenantId" clearable filterable placeholder="全部租户" style="width: 180px">
              <el-option
                v-for="tenant in tenants"
                :key="tenant.id"
                :label="tenantLabel(tenant)"
                :value="tenant.id"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="账单类型">
            <el-select v-model="queryForm.billType" clearable placeholder="全部类型" style="width: 150px">
              <el-option
                v-for="type in billTypeOptions"
                :key="type.value"
                :label="type.label"
                :value="type.value"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="审核状态">
            <el-select v-model="queryForm.auditStatus" clearable placeholder="全部" style="width: 130px">
              <el-option label="待审核" value="PENDING" />
              <el-option label="已发布" value="APPROVED" />
              <el-option label="已驳回" value="REJECTED" />
            </el-select>
          </el-form-item>

          <el-form-item label="收款状态">
            <el-select v-model="queryForm.status" clearable placeholder="全部" style="width: 130px">
              <el-option label="待缴" value="UNPAID" />
              <el-option label="已缴" value="PAID" />
              <el-option label="已逾期" value="OVERDUE" />
              <el-option label="已取消" value="CANCELLED" />
            </el-select>
          </el-form-item>

          <el-form-item>
            <el-button type="primary" @click="searchBills">查询</el-button>
            <el-button @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>

        <el-table v-loading="loading" :data="bills" border stripe style="width: 100%">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column label="账单" min-width="230">
            <template #default="{ row }">
              <div class="strong-text">{{ row.title || billTypeText(row.billType) }}</div>
              <div class="sub-text">{{ row.billNumber }}</div>
            </template>
          </el-table-column>
          <el-table-column label="租户" min-width="150">
            <template #default="{ row }">{{ row.tenantName || tenantName(row.tenantId) }}</template>
          </el-table-column>
          <el-table-column label="合同" min-width="150">
            <template #default="{ row }">{{ row.contractNumber || contractLabelById(row.contractId) }}</template>
          </el-table-column>
          <el-table-column label="类型" width="120">
            <template #default="{ row }">{{ row.billTypeText || billTypeText(row.billType) }}</template>
          </el-table-column>
          <el-table-column label="来源" width="130">
            <template #default="{ row }">{{ row.sourceTypeText || sourceTypeText(row.sourceType) }}</template>
          </el-table-column>
          <el-table-column label="账期" min-width="190">
            <template #default="{ row }">{{ row.periodStart || '-' }} 至 {{ row.periodEnd || '-' }}</template>
          </el-table-column>
          <el-table-column label="金额" width="130" align="right">
            <template #default="{ row }">
              <div>应收 {{ money(row.amount) }}</div>
              <div class="sub-text">已收 {{ money(row.paidAmount) }}</div>
            </template>
          </el-table-column>
          <el-table-column prop="dueDate" label="到期日" width="120" />
          <el-table-column label="逾期天数" width="100" align="center">
            <template #default="{ row }">{{ row.overdueDays ?? 0 }}</template>
          </el-table-column>
          <el-table-column label="滞纳金" width="120" align="right">
            <template #default="{ row }">{{ money(row.lateFee) }}</template>
          </el-table-column>
          <el-table-column label="是否已开票" width="120">
            <template #default="{ row }">
              <el-tag :type="isInvoiced(row) ? 'success' : 'info'">
                {{ row.invoiceStatusText || invoiceStatusText(row.invoiceStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="审核" width="110">
            <template #default="{ row }">
              <el-tag :type="auditTagType(row.auditStatus)">
                {{ row.auditStatusText || auditStatusText(row.auditStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row)">
                {{ row.overdue && row.status !== 'PAID' ? '已逾期' : (row.statusText || statusText(row.status)) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="审核信息" min-width="160">
            <template #default="{ row }">
              <div>{{ row.approvedBy || '-' }}</div>
              <div class="sub-text">{{ formatTime(row.approvedTime) }}</div>
            </template>
          </el-table-column>
          <el-table-column label="备注" min-width="180">
            <template #default="{ row }">{{ row.remark || row.auditRemark || '-' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="390" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="hasPermission('bill:audit') && row.auditStatus !== 'APPROVED' && row.status !== 'CANCELLED'"
                size="small"
                type="primary"
                @click="approveBill(row)"
              >
                审核通过
              </el-button>
              <el-button
                v-if="hasPermission('bill:audit') && row.auditStatus !== 'REJECTED' && row.status !== 'PAID'"
                size="small"
                @click="rejectBill(row)"
              >
                驳回
              </el-button>
              <el-button
                v-if="row.auditStatus === 'APPROVED' && row.status !== 'PAID' && row.status !== 'CANCELLED' && hasPermission('bill:pay')"
                size="small"
                type="success"
                @click="payBill(row)"
              >
                确认收款
              </el-button>
              <el-button
                v-if="hasPermission('bill:pay')"
                size="small"
                @click="selectInvoiceFile(row)"
              >
                {{ isInvoiced(row) ? '替换发票' : '上传发票' }}
              </el-button>
              <el-button
                v-if="isInvoiced(row)"
                size="small"
                @click="downloadInvoice(row)"
              >
                下载发票
              </el-button>
              <el-button
                v-if="hasPermission('bill:pay') && isInvoiced(row)"
                size="small"
                type="danger"
                plain
                @click="deleteInvoice(row)"
              >
                删除发票
              </el-button>
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
            @current-change="loadBills"
            @size-change="handleSizeChange"
          />
        </div>
      </template>
    </el-card>

    <input
      ref="invoiceInputRef"
      type="file"
      accept="application/pdf"
      class="hidden-file-input"
      @change="handleInvoiceFileChange"
    />

    <el-dialog v-model="dialogVisible" title="新增账单" width="620px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="110px">
        <el-form-item label="账单编号">
          <el-input v-model="form.billNumber" placeholder="留空则系统自动生成" />
        </el-form-item>

        <el-form-item label="租户" prop="tenantId">
          <el-select
            v-model="form.tenantId"
            filterable
            placeholder="请选择租户"
            style="width: 100%"
            @change="handleTenantChange"
          >
            <el-option
              v-for="tenant in activeTenants"
              :key="tenant.id"
              :label="tenantLabel(tenant)"
              :value="tenant.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="合同">
          <el-select
            v-model="form.contractId"
            clearable
            filterable
            placeholder="可不选，适用于手工费用"
            style="width: 100%"
            @change="handleContractChange"
          >
            <el-option
              v-for="contract in selectableContracts"
              :key="contract.id"
              :label="contractOptionLabel(contract)"
              :value="contract.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="账单类型" prop="billType">
          <el-select v-model="form.billType" style="width: 100%" @change="handleBillTypeChange">
            <el-option
              v-for="type in billTypeOptions"
              :key="type.value"
              :label="type.label"
              :value="type.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="标题">
          <el-input v-model="form.title" placeholder="留空则按账期和类型生成" />
        </el-form-item>

        <el-form-item label="账期开始" prop="periodStart">
          <el-date-picker v-model="form.periodStart" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>

        <el-form-item label="账期结束" prop="periodEnd">
          <el-date-picker v-model="form.periodEnd" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>

        <el-form-item label="金额" prop="amount">
          <el-input-number v-model="form.amount" :min="0.01" :precision="2" style="width: 100%" />
        </el-form-item>

        <el-form-item label="到期日" prop="dueDate">
          <el-date-picker v-model="form.dueDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>

        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="费用说明、线下缴费说明等" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="createBill">保存为待审核</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../utils/request'
import { hasPermission } from '../../utils/permission'
import { createPagination, pageParams, readPage, resetToFirstPage } from '../../utils/pagination'

const bills = ref([])
const tenants = ref([])
const contracts = ref([])
const loading = ref(false)
const saving = ref(false)
const exporting = ref(false)
const dialogVisible = ref(false)
const formRef = ref(null)
const invoiceInputRef = ref(null)
const currentInvoiceBill = ref(null)
const pagination = reactive(createPagination(20))

const queryForm = reactive({
  keyword: '',
  tenantId: null,
  billType: '',
  auditStatus: '',
  status: ''
})

const defaultForm = () => ({
  billNumber: '',
  tenantId: null,
  contractId: null,
  billType: 'PROPERTY',
  title: '',
  periodStart: '',
  periodEnd: '',
  amount: null,
  dueDate: '',
  remark: ''
})

const form = reactive(defaultForm())

const billTypeOptions = [
  { label: '租金', value: 'RENT' },
  { label: '物业费', value: 'PROPERTY' },
  { label: '水费', value: 'WATER' },
  { label: '电费', value: 'ELECTRICITY' },
  { label: '燃气费', value: 'GAS' },
  { label: '停车费', value: 'PARKING' },
  { label: '会议室', value: 'MEETING_ROOM' },
  { label: '维修/工单服务费', value: 'WORK_ORDER' },
  { label: '保洁费', value: 'CLEANING' },
  { label: '押金', value: 'DEPOSIT' },
  { label: '滞纳金', value: 'LATE_FEE' },
  { label: '调账补差', value: 'ADJUSTMENT' },
  { label: '其他', value: 'OTHER' }
]

const formRules = {
  tenantId: [{ required: true, message: '请选择租户', trigger: 'change' }],
  billType: [{ required: true, message: '请选择账单类型', trigger: 'change' }],
  periodStart: [{ required: true, message: '请选择账期开始日期', trigger: 'change' }],
  periodEnd: [{ required: true, message: '请选择账期结束日期', trigger: 'change' }],
  amount: [
    { required: true, message: '请输入金额', trigger: 'change' },
    {
      validator: (_rule, value, callback) => {
        if (!value || value <= 0) {
          callback(new Error('金额必须大于0'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ],
  dueDate: [{ required: true, message: '请选择到期日', trigger: 'change' }]
}

const activeTenants = computed(() => tenants.value.filter(item => item.status !== 'INACTIVE'))

const selectableContracts = computed(() => {
  return contracts.value.filter(item => item.status === 'ACTIVE' && (!form.tenantId || item.tenantId === form.tenantId))
})

function searchBills() {
  resetToFirstPage(pagination)
  loadBills()
}

async function loadBills() {
  if (!hasPermission('bill:view')) return
  loading.value = true
  try {
    const params = { ...buildQueryParams(), ...pageParams(pagination) }
    const data = await request.get('/bills', { params })
    const page = readPage(data)
    bills.value = page.records
    pagination.total = page.total
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || '账单加载失败')
  } finally {
    loading.value = false
  }
}

async function loadTenants() {
  const data = await request.get('/tenant/list')
  tenants.value = Array.isArray(data) ? data : []
}

async function loadContracts() {
  const data = await request.get('/contracts')
  contracts.value = Array.isArray(data) ? data : []
}

function resetQuery() {
  resetToFirstPage(pagination)
  queryForm.keyword = ''
  queryForm.tenantId = null
  queryForm.billType = ''
  queryForm.auditStatus = ''
  queryForm.status = ''
  loadBills()
}

function buildQueryParams() {
  return Object.fromEntries(
    Object.entries(queryForm).filter(([, value]) => value !== null && value !== '')
  )
}

function handleSizeChange() {
  resetToFirstPage(pagination)
  loadBills()
}

function resetForm() {
  Object.assign(form, defaultForm())
  formRef.value?.clearValidate()
}

function openCreateDialog() {
  if (!hasPermission('bill:add')) {
    ElMessage.warning('无新增账单权限')
    return
  }
  resetForm()
  dialogVisible.value = true
}

async function createBill() {
  if (!hasPermission('bill:add')) {
    ElMessage.warning('无新增账单权限')
    return
  }
  await formRef.value.validate()
  saving.value = true
  try {
    await request.post('/bills', {
      billNumber: form.billNumber?.trim() || null,
      tenantId: Number(form.tenantId),
      contractId: form.contractId ? Number(form.contractId) : null,
      billType: form.billType,
      title: form.title?.trim() || null,
      periodStart: form.periodStart,
      periodEnd: form.periodEnd,
      amount: form.amount,
      dueDate: form.dueDate,
      remark: form.remark?.trim() || null
    })
    ElMessage.success('账单已保存为待审核')
    dialogVisible.value = false
    resetForm()
    await loadBills()
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || '账单创建失败')
  } finally {
    saving.value = false
  }
}

async function approveBill(row) {
  try {
    await ElMessageBox.confirm(
      `确定发布账单「${row.billNumber}」给租户吗？发布后，该租户已绑定联系人可在小程序账单中心查看。`,
      '审核通过确认',
      { type: 'warning' }
    )
    await request.post(`/bills/${row.id}/approve`, { auditRemark: '后台审核通过' })
    ElMessage.success('账单已发布，租户小程序可见')
    await loadBills()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error?.response?.data?.message || error?.message || '审核失败')
    }
  }
}

async function rejectBill(row) {
  try {
    const result = await ElMessageBox.prompt('请输入驳回原因', `驳回账单 ${row.billNumber}`, {
      confirmButtonText: '驳回',
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputPlaceholder: '例如：金额或账期需调整'
    })
    await request.post(`/bills/${row.id}/reject`, { auditRemark: result.value || '后台驳回' })
    ElMessage.success('账单已驳回')
    await loadBills()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error?.response?.data?.message || error?.message || '驳回失败')
    }
  }
}

async function payBill(row) {
  try {
    await ElMessageBox.confirm(`确定已线下收取账单「${row.billNumber}」的款项 ${money(row.amount)} 元吗？`, '确认收款', {
      type: 'warning'
    })
    await request.post(`/bills/${row.id}/pay`)
    ElMessage.success('收款确认成功')
    await loadBills()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error?.response?.data?.message || error?.message || '收款失败')
    }
  }
}

async function exportBills() {
  if (!hasPermission('bill:view')) {
    ElMessage.warning('无账单查看权限')
    return
  }
  exporting.value = true
  try {
    const blob = await request.get('/bills/export', {
      params: buildQueryParams(),
      responseType: 'blob'
    })
    downloadBlob(blob, `账单导出-${new Date().toISOString().slice(0, 10)}.xlsx`)
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || '导出失败')
  } finally {
    exporting.value = false
  }
}

function selectInvoiceFile(row) {
  if (!hasPermission('bill:pay')) {
    ElMessage.warning('无发票管理权限')
    return
  }
  currentInvoiceBill.value = row
  if (invoiceInputRef.value) {
    invoiceInputRef.value.value = ''
    invoiceInputRef.value.click()
  }
}

async function handleInvoiceFileChange(event) {
  const file = event.target.files?.[0]
  const row = currentInvoiceBill.value
  if (!file || !row) return

  if (file.type !== 'application/pdf' && !file.name.toLowerCase().endsWith('.pdf')) {
    ElMessage.error('只能上传 PDF 发票')
    return
  }

  const formData = new FormData()
  formData.append('file', file)
  try {
    await request.post(`/bills/${row.id}/invoice`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    ElMessage.success('发票已上传')
    await loadBills()
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || '发票上传失败')
  } finally {
    currentInvoiceBill.value = null
    if (invoiceInputRef.value) invoiceInputRef.value.value = ''
  }
}

async function downloadInvoice(row) {
  try {
    const blob = await request.get(`/bills/${row.id}/invoice/download`, {
      responseType: 'blob'
    })
    downloadBlob(blob, row.invoiceFileName || `${row.billNumber}.pdf`)
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || '发票下载失败')
  }
}

async function deleteInvoice(row) {
  try {
    await ElMessageBox.confirm(`确定删除账单「${row.billNumber}」的发票吗？`, '删除发票确认', {
      type: 'warning'
    })
    await request.delete(`/bills/${row.id}/invoice`)
    ElMessage.success('发票已删除')
    await loadBills()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error?.response?.data?.message || error?.message || '发票删除失败')
    }
  }
}

function downloadBlob(blob, filename) {
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)
}

function handleTenantChange() {
  form.contractId = null
}

function handleContractChange(contractId) {
  const contract = contracts.value.find(item => item.id === contractId)
  if (!contract) return
  form.tenantId = contract.tenantId
  if (form.billType === 'RENT' && contract.rentAmount) {
    form.amount = Number(contract.rentAmount)
  } else if (form.billType === 'PROPERTY' && contract.propertyFeeAmount) {
    form.amount = Number(contract.propertyFeeAmount)
  }
}

function handleBillTypeChange() {
  handleContractChange(form.contractId)
}

function tenantLabel(tenant) {
  return `${tenant.tenantName}（ID:${tenant.id}）`
}

function tenantName(tenantId) {
  const tenant = tenants.value.find(item => item.id === tenantId)
  return tenant ? tenant.tenantName : `租户ID:${tenantId || '-'}`
}

function contractOptionLabel(contract) {
  const name = contract.contractName ? ` | ${contract.contractName}` : ''
  const rent = contract.rentAmount ? ` | 租金${contract.rentAmount}` : ''
  const propertyFee = contract.propertyFeeAmount ? ` | 物业费${contract.propertyFeeAmount}` : ''
  return `${contract.contractNumber}${name}${rent}${propertyFee}`
}

function contractLabelById(contractId) {
  if (!contractId) return '无合同'
  const contract = contracts.value.find(item => item.id === contractId)
  return contract ? contract.contractNumber : `合同ID:${contractId}`
}

function billTypeText(value) {
  return billTypeOptions.find(item => item.value === value)?.label || value || '-'
}

function sourceTypeText(value) {
  return {
    MANUAL: '手工账单',
    CONTRACT: '合同自动出账',
    FEE_RULE: '周期收费',
    ENERGY: '能耗抄表',
    PARKING: '停车账单',
    MEETING_ROOM: '会议室预约',
    WORK_ORDER: '工单服务',
    DEV_FIXTURE: '开发测试'
  }[value] || value || '历史账单'
}

function statusText(value) {
  return {
    UNPAID: '待缴',
    PAID: '已缴',
    OVERDUE: '已逾期',
    CANCELLED: '已取消'
  }[value] || value || '-'
}

function auditStatusText(value) {
  return {
    PENDING: '待审核',
    APPROVED: '已发布',
    REJECTED: '已驳回'
  }[value] || '已发布'
}

function invoiceStatusText(value) {
  return value === 'INVOICED' ? '已开票' : '未开票'
}

function isInvoiced(row) {
  return row.invoiceStatus === 'INVOICED'
}

function auditTagType(value) {
  return {
    PENDING: 'warning',
    APPROVED: 'success',
    REJECTED: 'danger'
  }[value] || 'success'
}

function statusTagType(row) {
  if (row.status === 'PAID') return 'success'
  if (row.status === 'CANCELLED') return 'info'
  if (row.overdue) return 'danger'
  return 'warning'
}

function money(value) {
  return Number(value || 0).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })
}

function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

onMounted(async () => {
  await Promise.all([loadTenants(), loadContracts()])
  await loadBills()
})
</script>

<style scoped>
.page-container {
  padding: 20px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.page-title {
  font-weight: 600;
  color: #303133;
}

.page-subtitle {
  margin-top: 6px;
  color: #909399;
  font-size: 13px;
}

.query-form {
  margin-bottom: 12px;
}

.strong-text {
  font-weight: 600;
  color: #303133;
}

.sub-text {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
}

.hidden-file-input {
  display: none;
}
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
