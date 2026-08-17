<template>
  <section class="workspace-details" aria-label="租户与合同详情">
    <div v-if="!tenant" class="detail-empty">
      <el-empty description="请选择租户查看档案" />
    </div>

    <template v-else>
      <header class="detail-header">
        <div class="detail-identity">
          <div class="detail-heading">
            <span class="detail-eyebrow">{{ contract ? '合同档案' : '租户档案' }}</span>
            <h2>{{ contract ? contract.contractNo : tenant.tenantName }}</h2>
            <span>{{ contract ? contract.roomSummary : tenant.tenantCode || '未配置租户编号' }}</span>
          </div>
        </div>

        <div class="detail-actions">
          <template v-if="contract">
            <el-button :icon="Back" @click="$emit('back-to-contracts')">返回合同列表</el-button>
            <el-button v-if="canUpdateContract && contract.status === 'DRAFT'" @click="$emit('edit-contract', contract)">编辑</el-button>
            <el-button v-if="canUpdateContract" @click="$emit('upload-contract', contract)">上传合同</el-button>
            <el-button :disabled="!hasContractFile" @click="$emit('preview-contract', contract)">查看附件</el-button>
            <el-button
              v-if="canUpdateContract && contract.status === 'DRAFT'"
              type="primary"
              @click="$emit('submit-contract', contract)"
            >提交审批</el-button>
            <el-button
              v-if="canApproveContract && pendingContract"
              type="primary"
              @click="$emit('approve-contract', contract)"
            >审批通过</el-button>
            <el-dropdown v-if="hasSecondaryContractActions" trigger="click" @command="handleContractCommand">
              <el-button>更多操作<el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-if="canActivateContract && contract.status === 'SUSPENDED'" command="activate">恢复生效</el-dropdown-item>
                  <el-dropdown-item v-if="canCheckIn && contract.status === 'ACTIVE' && !actionableLease" command="checkin">办理入住</el-dropdown-item>
                  <el-dropdown-item v-if="canCheckOut && actionableLease" command="checkout">办理退租</el-dropdown-item>
                  <el-dropdown-item v-if="canTerminateContract && !actionableLease && ['ACTIVE', 'SUSPENDED'].includes(contract.status)" divided command="terminate">终止合同</el-dropdown-item>
                  <el-dropdown-item v-if="canCancelContract && ['DRAFT', 'PENDING', 'PENDING_APPROVAL'].includes(contract.status)" command="cancel">作废合同</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
          <template v-else>
            <el-button v-if="canCreateContract" type="primary" @click="$emit('create-contract')">新增合同</el-button>
            <el-button v-if="canUpdateTenant" @click="$emit('edit-tenant', tenant)">编辑租户</el-button>
          </template>
          <el-dropdown v-if="canManageFields || canEditCustomValues" trigger="click" @command="handleCustomCommand">
            <el-button :icon="Setting" aria-label="自定义字段操作" />
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item v-if="canEditCustomValues" command="values">编辑字段值</el-dropdown-item>
                <el-dropdown-item v-if="canManageFields" command="definitions">管理字段定义</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <el-alert v-if="errorText" :title="errorText" type="error" show-icon :closable="false" class="detail-error">
        <template #default><el-button link type="primary" @click="$emit('retry')">重试</el-button></template>
      </el-alert>

      <el-tabs :model-value="activeTab" class="detail-tabs" @update:model-value="$emit('update:activeTab', $event)">
        <template v-if="contract">
          <el-tab-pane label="合同概览" name="overview">
            <ContractOverview :contract="contract" :custom-fields="customFields" />
          </el-tab-pane>
          <el-tab-pane label="租赁房间" name="rooms">
            <GridPanel title="合同关联的全部房间" :count="roomRows.length">
              <template #actions>
                <el-button v-if="canViewRooms" link type="primary" @click="$emit('open-spaces', roomRows[0])">前往资产管理</el-button>
              </template>
              <WorkspaceDataGrid
                :rows="roomRows"
                :columns="roomColumns"
                :loading="loading"
                :show-index="false"
                :operation-column="{ width: 96, fixed: 'right' }"
                empty-text="当前合同未关联房间"
                aria-label="合同租赁房间"
              >
                <template #operations="{ row }">
                  <el-button v-if="canViewRooms" link type="primary" @click="$emit('open-spaces', row)">查看房间</el-button>
                </template>
              </WorkspaceDataGrid>
            </GridPanel>
          </el-tab-pane>
          <el-tab-pane label="电子合同" name="file">
            <div class="attachment-actions">
              <span>仅支持 PDF；预览和下载均通过受保护接口鉴权。</span>
              <div>
                <el-button v-if="canUpdateContract" type="primary" @click="$emit('upload-contract', contract)">上传/替换主合同</el-button>
                <el-button v-if="canUpdateContract" @click="$emit('upload-supplementary', contract)">上传补充协议</el-button>
              </div>
            </div>
            <div v-if="contract.attachments?.length" class="attachment-list">
              <div v-for="attachment in contract.attachments" :key="attachment.attachmentId" class="file-panel">
                <el-icon><Document /></el-icon>
                <div>
                  <strong>{{ attachment.fileName }}</strong>
                  <span>{{ attachmentTypeLabel(attachment.attachmentType) }} · {{ fileSize(attachment.fileSize) }} · {{ formatDateTime(attachment.uploadedAt) }}</span>
                </div>
                <el-button type="primary" link @click="$emit('preview-attachment', { contract, attachment })">预览/下载</el-button>
              </div>
            </div>
            <div v-else class="custom-empty">尚未上传合同附件</div>
          </el-tab-pane>
          <el-tab-pane label="履约记录" name="history">
            <GridPanel title="真实状态变更记录" :count="history.length">
              <WorkspaceDataGrid
                :rows="history"
                :columns="historyColumns"
                :loading="loading"
                :show-index="false"
                row-key="rowKey"
                empty-text="暂无可用的履约记录"
                aria-label="合同履约记录"
              />
            </GridPanel>
          </el-tab-pane>
          <el-tab-pane v-if="canViewBills" label="关联账单" name="bills">
            <BillPanel :rows="bills" :loading="loading" @open="$emit('open-bill', $event)" @open-all="$emit('open-bills')" />
          </el-tab-pane>
        </template>

        <template v-else>
          <el-tab-pane label="租户概览" name="overview">
            <TenantOverview
              :tenant="tenant"
              :overview="overview"
              :contracts="tenantContracts"
              :custom-fields="customFields"
              @select-contract="$emit('select-contract', $event)"
            />
          </el-tab-pane>
          <el-tab-pane label="联系人" name="contacts">
            <div class="summary-strip">
              <span>租户联系人 <strong>{{ contactSummary.total }}</strong> 人</span>
              <span>负责人 <strong>{{ contactSummary.responsible }}</strong> 人</span>
              <span>租户员工 <strong>{{ contactSummary.employees }}</strong> 人</span>
              <span>已绑定小程序 <strong>{{ contactSummary.bound }}</strong> 人</span>
            </div>
            <GridPanel title="租户联系人" :count="contacts.length">
              <template #actions>
                <el-button v-if="canUpdateTenant" type="primary" @click="$emit('create-contact')">新增联系人</el-button>
              </template>
              <WorkspaceDataGrid
                :rows="contacts"
                :columns="contactColumns"
                :loading="loading"
                :show-index="false"
                :operation-column="{ width: 330, fixed: 'right', align: 'left' }"
                empty-text="当前租户暂无联系人"
                aria-label="租户联系人"
              >
                <template #cell-role="{ row }">{{ contactRoleLabel(row) }}</template>
                <template #cell-isPrimary="{ row }"><el-tag v-if="row.isPrimary" size="small" type="success">主负责人</el-tag><span v-else>-</span></template>
                <template #cell-isEmergency="{ row }"><el-tag v-if="row.isEmergency" size="small" type="warning">紧急</el-tag><span v-else>-</span></template>
                <template #cell-bindingStatus="{ row }"><el-tag size="small" :type="row.boundMobileUserCount ? 'success' : 'info'">{{ row.boundMobileUserCount ? '已绑定' : '未绑定' }}</el-tag></template>
                <template #operations="{ row }">
                  <el-button v-if="canUpdateTenant" link type="primary" @click="$emit('edit-contact', row)">编辑</el-button>
                  <el-button v-if="canUpdateTenant && !isResponsible(row) && row.status === 'ACTIVE'" link @click="$emit('promote-responsible', row)">设为负责人</el-button>
                  <el-button v-if="canUpdateTenant && isResponsible(row) && !row.isPrimary && row.status === 'ACTIVE'" link @click="$emit('set-responsible', row)">设为主负责人</el-button>
                  <el-button v-if="canUpdateTenant && !row.isEmergency && row.status === 'ACTIVE'" link @click="$emit('set-emergency', row)">设为紧急</el-button>
                  <el-button v-if="canUpdateTenant && row.status !== 'INACTIVE'" link @click="$emit('deactivate-contact', row)">停用</el-button>
                  <el-button v-if="canUpdateTenant" link type="danger" @click="$emit('delete-contact', row)">删除</el-button>
                </template>
              </WorkspaceDataGrid>
            </GridPanel>
          </el-tab-pane>
          <el-tab-pane label="租赁空间" name="spaces">
            <GridPanel title="房间（来自合同与资产关系）" :count="visibleRoomRows.length">
              <template #actions>
                <el-select v-model="spaceContractFilter" clearable placeholder="全部合同" size="small" style="width: 150px">
                  <el-option v-for="item in tenantContracts" :key="item.id" :label="item.contractNo" :value="item.id" />
                </el-select>
                <el-segmented v-model="roomSpaceFilter" :options="spaceFilterOptions" size="small" />
              </template>
              <WorkspaceDataGrid
                :rows="visibleRoomRows"
                :columns="roomColumns"
                :loading="loading"
                :show-index="false"
                :operation-column="{ width: 96, fixed: 'right' }"
                empty-text="当前租户暂无租赁空间"
                aria-label="租户租赁空间"
              >
                <template #operations="{ row }">
                  <el-button v-if="canViewRooms" link type="primary" @click="$emit('open-spaces', row)">查看房间</el-button>
                </template>
              </WorkspaceDataGrid>
            </GridPanel>
            <GridPanel title="停车位（来自停车位绑定）" :count="visibleParkingSpaces.length">
              <template #actions>
                <el-segmented v-model="parkingSpaceFilter" :options="spaceFilterOptions" size="small" />
              </template>
              <WorkspaceDataGrid
                :rows="visibleParkingSpaces"
                :columns="parkingColumns"
                :loading="loading"
                :show-index="false"
                row-key="rowKey"
                empty-text="当前租户暂无停车位绑定"
                aria-label="租户停车位"
              />
            </GridPanel>
          </el-tab-pane>
          <el-tab-pane label="全部合同" name="contracts">
            <GridPanel title="租户全部合同" :count="visibleTenantContracts.length">
              <template #actions>
                <el-segmented v-model="tenantContractFilter" :options="tenantContractFilterOptions" size="small" />
              </template>
              <WorkspaceDataGrid
                :rows="visibleTenantContracts"
                :columns="contractColumns"
                :loading="loading"
                :show-index="false"
                :operation-column="{ width: 96, fixed: 'right' }"
                empty-text="当前租户暂无合同"
                aria-label="租户全部合同"
              >
                <template #cell-attachments="{ row }">{{ row.attachments?.length || 0 }} 个</template>
                <template #operations="{ row }"><el-button link type="primary" @click="$emit('select-contract', row)">查看</el-button></template>
              </WorkspaceDataGrid>
            </GridPanel>
          </el-tab-pane>
          <el-tab-pane v-if="canViewBills" label="关联账单" name="bills">
            <BillPanel :rows="bills" :loading="loading" @open="$emit('open-bill', $event)" @open-all="$emit('open-bills')" />
          </el-tab-pane>
        </template>
      </el-tabs>
    </template>
  </section>
</template>

<script setup>
import { computed, defineComponent, h, ref, watch } from 'vue'
import { ArrowDown, Back, Document, Setting } from '@element-plus/icons-vue'
import CustomFieldCellRenderer from '../../../components/workspace/CustomFieldCellRenderer.vue'
import WorkspaceDataGrid from '../../../components/workspace/WorkspaceDataGrid.vue'
import TenantContractLifecycleNotice from './TenantContractLifecycleNotice.vue'

defineOptions({ name: 'TenantWorkspaceDetails' })

const props = defineProps({
  tenant: { type: Object, default: null },
  contract: { type: Object, default: null },
  activeTab: { type: String, default: 'overview' },
  contacts: { type: Array, default: () => [] },
  tenantContracts: { type: Array, default: () => [] },
  roomRows: { type: Array, default: () => [] },
  parkingSpaces: { type: Array, default: () => [] },
  overview: { type: Object, default: null },
  bills: { type: Array, default: () => [] },
  history: { type: Array, default: () => [] },
  actionableLease: { type: Object, default: null },
  customFields: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  errorText: { type: String, default: '' },
  canUpdateTenant: { type: Boolean, default: false },
  canCreateContract: { type: Boolean, default: false },
  canUpdateContract: { type: Boolean, default: false },
  canApproveContract: { type: Boolean, default: false },
  canActivateContract: { type: Boolean, default: false },
  canTerminateContract: { type: Boolean, default: false },
  canCancelContract: { type: Boolean, default: false },
  canCheckIn: { type: Boolean, default: false },
  canCheckOut: { type: Boolean, default: false },
  canViewBills: { type: Boolean, default: false },
  canViewRooms: { type: Boolean, default: false },
  canManageFields: { type: Boolean, default: false },
  canEditCustomValues: { type: Boolean, default: false }
})

const emit = defineEmits([
  'update:activeTab', 'retry', 'edit-tenant', 'create-contract', 'edit-contract', 'upload-contract', 'preview-contract',
  'submit-contract', 'approve-contract', 'activate-contract', 'checkin', 'checkout', 'terminate-contract',
  'cancel-contract', 'edit-custom-values', 'manage-custom-fields', 'create-contact', 'edit-contact',
  'deactivate-contact', 'delete-contact', 'promote-responsible', 'set-responsible', 'set-emergency', 'open-spaces', 'select-contract', 'back-to-contracts',
  'open-bill', 'open-bills', 'upload-supplementary', 'preview-attachment'
])

const tenantContractFilter = ref('ALL')
const roomSpaceFilter = ref('ALL')
const parkingSpaceFilter = ref('ALL')
const spaceContractFilter = ref(null)
const spaceFilterOptions = [
  { label: '全部', value: 'ALL' },
  { label: '当前', value: 'CURRENT' },
  { label: '历史', value: 'HISTORY' }
]
const tenantContractFilterOptions = [
  { label: '全部', value: 'ALL' },
  { label: '当前合同', value: 'ACTIVE' },
  { label: '即将到期', value: 'EXPIRING' },
  { label: '已到期', value: 'EXPIRED' },
  { label: '已终止', value: 'TERMINATED' },
  { label: '待处理', value: 'PENDING' }
]
const visibleTenantContracts = computed(() => props.tenantContracts.filter(contract => {
  if (tenantContractFilter.value === 'ALL') return true
  if (tenantContractFilter.value === 'PENDING') {
    return ['DRAFT', 'PENDING', 'PENDING_APPROVAL', 'SUSPENDED'].includes(contract.status)
  }
  if (tenantContractFilter.value === 'CLOSED') {
    return ['TERMINATED', 'CANCELLED', 'EXPIRED'].includes(contract.status)
  }
  if (tenantContractFilter.value === 'EXPIRING') return Boolean(contract.expiringSoon)
  return contract.status === tenantContractFilter.value
}))
const contactSummary = computed(() => ({
  total: props.contacts.length,
  responsible: props.contacts.filter(item => item.isPrimary || item.role === 'RESPONSIBLE').length,
  employees: props.contacts.filter(item => !item.isPrimary && item.role !== 'RESPONSIBLE').length,
  bound: props.contacts.filter(item => Number(item.boundMobileUserCount || 0) > 0).length
}))
const visibleRoomRows = computed(() => props.roomRows.filter(row => {
  if (spaceContractFilter.value && Number(row.contractId) !== Number(spaceContractFilter.value)) return false
  if (roomSpaceFilter.value === 'CURRENT') return Boolean(row.current)
  if (roomSpaceFilter.value === 'HISTORY') return !row.current
  return true
}))
const visibleParkingSpaces = computed(() => props.parkingSpaces.filter(row => {
  if (parkingSpaceFilter.value === 'CURRENT') return Boolean(row.current)
  if (parkingSpaceFilter.value === 'HISTORY') return !row.current
  return true
}))

watch(() => props.tenant?.id, () => {
  tenantContractFilter.value = 'ALL'
  roomSpaceFilter.value = 'ALL'
  parkingSpaceFilter.value = 'ALL'
  spaceContractFilter.value = null
})

const pendingContract = computed(() => ['PENDING', 'PENDING_APPROVAL'].includes(props.contract?.status))
const hasContractFile = computed(() => Boolean(
  props.contract?.hasContractFile === true
  || props.contract?.hasContractFile === 1
  || props.contract?.contractFileName
  || props.contract?.attachments?.some(item => item.attachmentType === 'ELECTRONIC_CONTRACT')
))
const hasSecondaryContractActions = computed(() => Boolean(
  (props.canActivateContract && props.contract?.status === 'SUSPENDED')
  || (props.canCheckIn && props.contract?.status === 'ACTIVE' && !props.actionableLease)
  || (props.canCheckOut && props.actionableLease)
  || (props.canTerminateContract && !props.actionableLease && ['ACTIVE', 'SUSPENDED'].includes(props.contract?.status))
  || (props.canCancelContract && ['DRAFT', 'PENDING', 'PENDING_APPROVAL'].includes(props.contract?.status))
))

const contactColumns = [
  { key: 'name', label: '姓名', minWidth: 120 },
  { key: 'phone', label: '手机号', minWidth: 150 },
  { key: 'email', label: '邮箱', minWidth: 180 },
  { key: 'position', label: '职位', minWidth: 120 },
  { key: 'role', label: '联系人类型', minWidth: 120 },
  { key: 'isPrimary', label: '主负责人', width: 100 },
  { key: 'isEmergency', label: '紧急联系人', width: 110 },
  { key: 'bindingStatus', label: '小程序绑定', width: 110 },
  { key: 'lastLoginAt', label: '最近登录', type: 'date', format: 'datetime', minWidth: 165 },
  { key: 'status', label: '状态', width: 100 }
]

function contactRoleLabel(row) {
  return isResponsible(row) ? '租户负责人' : '租户员工'
}

function isResponsible(row) {
  return row?.role === 'RESPONSIBLE' || Boolean(row?.isPrimary)
}
const roomColumns = [
  { key: 'buildingName', label: '楼宇', minWidth: 130 },
  { key: 'floorName', label: '楼层', minWidth: 110 },
  { key: 'roomNumber', label: '房间号', minWidth: 110 },
  { key: 'roomName', label: '房间名称', minWidth: 130 },
  { key: 'area', label: '面积(㎡)', type: 'number', width: 110 },
  { key: 'roomType', label: '类型', width: 110 },
  { key: 'status', label: '房间状态', width: 110 },
  { key: 'contractNo', label: '合同编号', minWidth: 150 },
  { key: 'occupancyStatus', label: '入住状态', width: 120 }
]
const contractColumns = [
  { key: 'contractNo', label: '合同编号', minWidth: 150 },
  { key: 'contractType', label: '类型', width: 100 },
  { key: 'roomSummary', label: '租赁房间', minWidth: 180 },
  { key: 'totalArea', label: '面积(㎡)', type: 'number', width: 110 },
  { key: 'startDate', label: '开始日期', width: 120 },
  { key: 'endDate', label: '结束日期', width: 120 },
  { key: 'rentAmount', label: '月租', type: 'money', width: 120 },
  { key: 'depositAmount', label: '押金', type: 'money', width: 120 },
  { key: 'attachments', label: '附件', width: 90 },
  { key: 'updatedAt', label: '更新时间', type: 'date', format: 'datetime', minWidth: 165 },
  { key: 'status', label: '状态', width: 110 }
]
const parkingColumns = [
  { key: 'spaceNumber', label: '车位编号', minWidth: 120 },
  { key: 'area', label: '区域', minWidth: 120 },
  { key: 'spaceType', label: '车位类型', width: 110 },
  { key: 'startDate', label: '开始日期', type: 'date', width: 120 },
  { key: 'endDate', label: '结束日期', type: 'date', width: 120 },
  { key: 'plateNumber', label: '绑定车辆', minWidth: 120 },
  { key: 'boundContactName', label: '绑定员工', minWidth: 120 },
  { key: 'status', label: '状态', width: 100 }
]
const historyColumns = [
  { key: 'occurredAt', label: '操作时间', type: 'date', format: 'datetime', minWidth: 165 },
  { key: 'sourceType', label: '记录来源', width: 110 },
  { key: 'fromStatus', label: '操作前', width: 120 },
  { key: 'toStatus', label: '操作后', width: 120 },
  { key: 'operatorName', label: '操作人', width: 120 },
  { key: 'reason', label: '原因', minWidth: 180 }
]
const billColumns = [
  { key: 'billNumber', label: '账单编号', minWidth: 150 },
  { key: 'billingPeriod', label: '计费周期', minWidth: 130 },
  { key: 'feeTypeSummary', label: '费用类型', minWidth: 150 },
  { key: 'calculatedAmount', label: '系统计算', type: 'money', width: 120 },
  { key: 'adjustmentAmount', label: '调整金额', type: 'money', width: 120 },
  { key: 'finalAmount', label: '最终金额', type: 'money', width: 120 },
  { key: 'paidAmount', label: '已收金额', type: 'money', width: 125 },
  { key: 'unpaidAmount', label: '未付金额', type: 'money', width: 125 },
  { key: 'workflowStatus', label: '账单状态', width: 115 },
  { key: 'payStatus', label: '支付状态', width: 115 },
  { key: 'dueDate', label: '到期日', type: 'date', width: 120 },
  { key: 'invoiceStatus', label: '发票状态', width: 115 },
  { key: 'contractLinkLabel', label: '关联合同', minWidth: 210 }
]

function handleContractCommand(command) {
  const events = {
    submit: 'submit-contract',
    approve: 'approve-contract',
    activate: 'activate-contract',
    checkin: 'checkin',
    checkout: 'checkout',
    terminate: 'terminate-contract',
    cancel: 'cancel-contract'
  }
  if (events[command]) emit(events[command], props.contract)
}

function handleCustomCommand(command) {
  emit(command === 'values' ? 'edit-custom-values' : 'manage-custom-fields', props.contract || props.tenant)
}

function formatDateTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 19) : '-'
}

const CustomFieldSummary = defineComponent({
  name: 'CustomFieldSummary',
  props: { record: Object, fields: Array },
  setup(summaryProps) {
    return () => h('section', { class: 'custom-summary is-wide' }, [
      h('h3', '自定义字段'),
      summaryProps.fields?.length
        ? h('dl', summaryProps.fields.map(field => h('div', { key: field.key }, [
          h('dt', field.label),
          h('dd', [h(CustomFieldCellRenderer, { field, row: summaryProps.record })])
        ])))
        : h('p', { class: 'custom-empty' }, '当前工作区尚未配置自定义字段')
    ])
  }
})

const TenantOverview = defineComponent({
  name: 'TenantOverview',
  props: { tenant: Object, overview: Object, contracts: Array, customFields: Array },
  emits: ['select-contract'],
  setup(overviewProps, { emit: overviewEmit }) {
    const record = () => overviewProps.overview || overviewProps.tenant || {}
    return () => h('div', { class: 'overview-stack' }, [
      overviewGroup('企业信息', [
        detail('企业全称', record().companyName || overviewProps.tenant?.tenantName),
        detail('租户编号', record().tenantCode || overviewProps.tenant?.tenantCode),
        detail('统一社会信用代码', record().unifiedCreditCode || overviewProps.tenant?.creditCode),
        ...(record().companyShortName ? [detail('企业简称', record().companyShortName)] : []),
        detail('租户状态', record().status),
        detail('更新时间', formatDateTime(record().updatedAt))
      ], { wide: true }),
      overviewGroup('租赁摘要', [
        detail('有效合同数', record().activeContractCount || 0),
        detail('租赁房间数', record().activeRoomCount || 0),
        detail('涉及楼宇数', record().activeBuildingCount || 0),
        detail('租赁总面积', `${Number(record().totalArea || 0)} ㎡`),
        detail('楼宇 / 楼层', [record().buildingSummary, record().floorSummary].filter(Boolean).join(' / ')),
        detail('房间', record().roomSummary),
        detail('当前有效租期', activeLeaseLabel(record()))
      ], {
        wide: true,
        footer: h(TenantContractLifecycleNotice, {
          contracts: overviewProps.contracts,
          onSelectContract: contract => overviewEmit('select-contract', contract)
        })
      }),
      overviewGroup('物业服务信息', [
        detail('物业对接人', record().serviceManager?.name),
        detail('物业对接人手机号', record().serviceManager?.phone)
      ]),
      overviewGroup('紧急联系信息', [
        detail('紧急联系人', record().emergencyContact?.name),
        detail('紧急联系人手机号', record().emergencyContact?.phone)
      ]),
      overviewGroup('开票信息', [
        detail('开票抬头', record().invoiceTitle),
        detail('纳税人识别号', record().taxNumber)
      ]),
      h(CustomFieldSummary, { record: overviewProps.tenant, fields: overviewProps.customFields })
    ])
  }
})

const ContractOverview = defineComponent({
  name: 'ContractOverview',
  props: { contract: Object, customFields: Array },
  setup(overviewProps) {
    return () => h('div', { class: 'overview-stack' }, [
      h('dl', { class: 'overview-grid' }, [
        detail('合同编号', overviewProps.contract?.contractNo),
        detail('租户', overviewProps.contract?.tenantName),
        detail('合同状态', overviewProps.contract?.status),
        detail('租赁房间', overviewProps.contract?.roomSummary),
        detail('开始日期', overviewProps.contract?.startDate),
        detail('结束日期', overviewProps.contract?.endDate),
        detail('生效日期', overviewProps.contract?.effectiveDate),
        detail('签署时间', formatDateTime(overviewProps.contract?.signedAt)),
        detail('月租金额', money(overviewProps.contract?.rentAmount)),
        detail('押金', money(overviewProps.contract?.depositAmount)),
        detail('备注', overviewProps.contract?.remark),
        detail('更新时间', formatDateTime(overviewProps.contract?.updatedAt))
      ]),
      h(CustomFieldSummary, { record: overviewProps.contract, fields: overviewProps.customFields })
    ])
  }
})

const GridPanel = defineComponent({
  name: 'GridPanel',
  props: { title: String, count: Number },
  setup(panelProps, { slots }) {
    return () => h('div', { class: 'grid-panel' }, [
      h('div', { class: 'grid-panel-header' }, [
        h('div', [h('strong', panelProps.title), h('span', `${panelProps.count || 0} 条`)]),
        slots.actions?.()
      ]),
      slots.default?.()
    ])
  }
})

const BillPanel = defineComponent({
  name: 'BillPanel',
  props: { rows: Array, loading: Boolean },
  emits: ['open', 'open-all'],
  setup(panelProps, { emit: panelEmit }) {
    const filter = ref('ALL')
    const filteredRows = computed(() => (panelProps.rows || []).filter(row => {
      if (filter.value === 'ALL') return true
      if (filter.value === 'UNPAID') return row.payStatus !== 'PAID'
      if (filter.value === 'PAID') return row.payStatus === 'PAID'
      if (filter.value === 'OVERDUE') {
        return row.payStatus === 'OVERDUE'
          || (row.payStatus !== 'PAID' && row.dueDate && String(row.dueDate) < new Date().toISOString().slice(0, 10))
      }
      if (filter.value === 'PENDING_INVOICE') return !['ISSUED', 'INVOICED'].includes(row.invoiceStatus)
      if (filter.value === 'ISSUED') return ['ISSUED', 'INVOICED'].includes(row.invoiceStatus)
      return true
    }))
    return () => h(GridPanel, { title: '关联账单（只读）', count: filteredRows.value.length }, {
      actions: () => h('div', { class: 'bill-actions' }, [
        h('select', {
          value: filter.value,
          'aria-label': '筛选关联账单',
          onChange: event => { filter.value = event.target.value }
        }, [
          h('option', { value: 'ALL' }, '全部'),
          h('option', { value: 'UNPAID' }, '待缴'),
          h('option', { value: 'PAID' }, '已缴'),
          h('option', { value: 'OVERDUE' }, '逾期'),
          h('option', { value: 'PENDING_INVOICE' }, '待开票'),
          h('option', { value: 'ISSUED' }, '已开票')
        ]),
        h('button', { class: 'link-button', type: 'button', onClick: () => panelEmit('open-all') }, '前往财务账单')
      ]),
      default: () => h(WorkspaceDataGrid, {
        rows: filteredRows.value,
        columns: billColumns,
        loading: panelProps.loading,
        showIndex: false,
        operationColumn: { width: 96, fixed: 'right' },
        emptyText: '当前范围暂无关联账单',
        ariaLabel: '关联账单'
      }, {
        operations: ({ row }) => h('button', { class: 'link-button', type: 'button', onClick: () => panelEmit('open', row) }, '查看账单')
      })
    })
  }
})

function detail(label, value) {
  return h('div', [h('dt', label), h('dd', value === undefined || value === null || value === '' ? '-' : String(value))])
}

function overviewGroup(title, items, options = {}) {
  return h('section', { class: ['overview-group', { 'is-wide': options.wide }] }, [
    h('h3', title),
    h('dl', { class: 'overview-grid' }, items),
    options.footer
  ])
}

function activeLeaseLabel(record) {
  if (!Number(record?.activeContractCount || 0)) return '暂无有效合同'
  return `${record.activeLeaseStartDate || '-'} 至 ${record.activeLeaseEndDate || '-'}（${record.activeContractCount} 份）`
}

function attachmentTypeLabel(type) {
  return type === 'SUPPLEMENTARY_AGREEMENT' ? '补充协议' : '主合同'
}

function fileSize(value) {
  const bytes = Number(value || 0)
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

function money(value) {
  return `¥${Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}
</script>

<style scoped>
.workspace-details {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  background: var(--dhic-page-bg);
}

.detail-empty {
  display: grid;
  place-items: center;
  height: 100%;
}

.detail-header,
.detail-identity,
.detail-actions,
.grid-panel-header,
.grid-panel-header > div,
.file-panel {
  display: flex;
  align-items: center;
}

.detail-header {
  justify-content: space-between;
  gap: 16px;
  min-height: 72px;
  border-bottom: 1px solid var(--dhic-border-soft);
  padding: 10px 18px;
  background: #fff;
}

.detail-identity {
  min-width: 0;
}

.detail-heading {
  display: grid;
  min-width: 0;
  gap: 2px;
}

.detail-heading h2 {
  margin: 0;
  overflow: hidden;
  color: var(--dhic-text);
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 0;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.detail-heading > span,
.detail-eyebrow {
  color: var(--dhic-text-muted);
  font-size: 11px;
  font-weight: 500;
}

.detail-actions {
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 6px;
}

.detail-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.detail-error {
  margin: 10px 16px 0;
}

.detail-tabs {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-height: 0;
  overflow: hidden;
  padding: 0;
  background: var(--dhic-page-bg);
}

.detail-tabs :deep(.el-tabs__header) {
  flex: 0 0 auto;
  margin: 0;
  padding: 0 18px;
  background: #fff;
}

.detail-tabs :deep(.el-tabs__nav-wrap::after) {
  height: 1px;
  background: var(--dhic-border-soft);
}

.detail-tabs :deep(.el-tabs__item) {
  height: 42px;
  padding: 0 18px;
}

.detail-tabs :deep(.el-tabs__item.is-active) {
  font-weight: 600;
}

.detail-tabs :deep(.el-tabs__content) {
  flex: 1 1 auto;
  min-height: 0;
  overflow: auto;
  padding: 16px;
  scrollbar-gutter: stable;
}

.detail-tabs :deep(.el-tabs__content),
.detail-tabs :deep(.el-tab-pane) {
  min-height: 0;
}

.overview-stack {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
}

.overview-stack > :deep(.overview-group) {
  grid-column: span 2;
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--dhic-border-soft);
  border-color: #e5e8ed;
  border-radius: 8px;
  background: #fff;
  box-shadow: var(--dhic-shadow-card);
}

.overview-stack > :deep(.overview-group.is-wide),
.overview-stack > :deep(.custom-summary),
.overview-stack > :deep(.overview-grid) {
  grid-column: 1 / -1;
}

.overview-stack :deep(.overview-group h3),
.overview-stack :deep(.custom-summary h3) {
  display: flex;
  align-items: center;
  min-height: 38px;
  margin: 0;
  border-bottom: 1px solid var(--dhic-border-soft);
  padding: 0 12px;
  background: #fafbfc;
  color: var(--dhic-text);
  font-size: 14px;
  font-weight: 650;
}

.overview-stack :deep(.overview-group h3::before),
.overview-stack :deep(.custom-summary h3::before) {
  width: 3px;
  height: 14px;
  margin-right: 7px;
  border-radius: 2px;
  background: var(--dhic-primary);
  content: '';
}

.summary-strip,
.attachment-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  border: 1px solid var(--dhic-border-soft);
  padding: 10px 12px;
  color: var(--dhic-text-muted);
  font-size: 13px;
}

.summary-strip {
  justify-content: flex-start;
}

.bill-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.bill-actions select {
  border: 1px solid var(--dhic-border);
  border-radius: 4px;
  padding: 4px 8px;
  background: #fff;
}

.attachment-list {
  display: grid;
  gap: 8px;
}

.overview-stack :deep(.overview-grid),
.overview-stack :deep(.custom-summary dl) {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 0;
  margin: 0;
  background: #fff;
}

.overview-stack :deep(.overview-grid > div),
.overview-stack :deep(.custom-summary dl > div) {
  min-width: 0;
  min-height: 62px;
  border-right: 1px solid var(--dhic-border-soft);
  border-bottom: 1px solid var(--dhic-border-soft);
  padding: 9px 12px;
  background: #fff;
}

.overview-stack :deep(.overview-grid dt),
.overview-stack :deep(.custom-summary dt) {
  margin-bottom: 4px;
  color: var(--dhic-text-muted);
  font-size: 11px;
  line-height: 1.35;
}

.overview-stack :deep(.overview-grid dd),
.overview-stack :deep(.custom-summary dd) {
  margin: 0;
  color: var(--dhic-text);
  font-size: 13px;
  font-weight: 500;
  line-height: 1.45;
  overflow-wrap: anywhere;
}

.overview-stack > :deep(.custom-summary) {
  overflow: hidden;
  border: 1px solid #e5e8ed;
  border-radius: 8px;
  background: #fff;
  box-shadow: var(--dhic-shadow-card);
}

.custom-empty {
  margin: 0;
  padding: 14px;
  background: #fff;
  color: var(--dhic-text-muted);
  text-align: center;
}

.grid-panel {
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--dhic-border-color);
  border-radius: var(--dhic-radius-large);
  background: #fff;
  box-shadow: var(--dhic-shadow-card);
}

.grid-panel-header {
  justify-content: space-between;
  min-height: 44px;
  padding: 0 12px;
  border-bottom: 1px solid var(--dhic-border-color-light);
}

.grid-panel-header > div {
  gap: 8px;
}

.grid-panel-header span {
  color: var(--dhic-text-muted);
  font-size: 12px;
}

.file-panel {
  gap: 14px;
  border: 1px solid var(--dhic-border-soft);
  padding: 18px;
}

.file-panel > .el-icon {
  width: 36px;
  height: 36px;
  color: var(--dhic-primary);
  font-size: 28px;
}

.file-panel > div {
  display: grid;
  flex: 1;
  min-width: 0;
  gap: 4px;
}

.file-panel span {
  color: var(--dhic-text-muted);
  font-size: 12px;
}

.link-button {
  border: 0;
  padding: 4px;
  background: transparent;
  color: var(--dhic-primary);
  cursor: pointer;
}

.link-button:focus-visible {
  outline: 2px solid var(--dhic-primary);
  outline-offset: 2px;
}

@media (max-width: 1200px) {
  .overview-stack > :deep(.overview-group.is-wide),
  .overview-stack > :deep(.custom-summary),
  .overview-stack > :deep(.overview-grid) {
    grid-column: 1 / -1;
  }

  .detail-header {
    align-items: flex-start;
  }
}

@media (max-width: 900px) {
  .overview-stack {
    grid-template-columns: 1fr;
  }

  .overview-stack > :deep(.overview-group),
  .overview-stack > :deep(.overview-group.is-wide),
  .overview-stack > :deep(.custom-summary),
  .overview-stack > :deep(.overview-grid) {
    grid-column: 1;
  }
}
</style>
