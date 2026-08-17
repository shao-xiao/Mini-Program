<template>
  <div class="tenant-workspace-page">
    <header class="page-heading">
      <div>
        <h1>租户档案</h1>
        <p>统一维护租户、合同、租赁空间、履约状态与关联账单</p>
      </div>
      <div class="page-heading-actions">
        <el-button
          :icon="tenantPaneCollapsed ? Expand : Fold"
          :aria-label="tenantPaneCollapsed ? '展开租户栏' : '收起租户栏'"
          @click="tenantPaneCollapsed = !tenantPaneCollapsed"
        >{{ tenantPaneCollapsed ? '展开租户' : '收起租户' }}</el-button>
        <el-button :icon="Refresh" :loading="directoryLoading" @click="reloadCurrent">刷新</el-button>
      </div>
    </header>

    <main :class="['tenant-workspace-shell', { 'is-tenant-collapsed': tenantPaneCollapsed }]">
      <button
        v-if="tenantPaneCollapsed"
        type="button"
        class="collapsed-tenant-rail"
        aria-label="展开租户公司栏"
        @click="tenantPaneCollapsed = false"
      >租户公司</button>

      <TenantDirectoryPane
        v-else
        :tenants="tenants"
        :selected-id="selectedTenantId"
        :search="tenantSearch"
        :status="tenantStatus"
        :page="directoryPage"
        :page-size="directoryPageSize"
        :total="directoryTotal"
        :loading="directoryLoading"
        :error-text="directoryError"
        :stats="tenantStats"
        :stats-available="true"
        :can-create="canCreateTenant"
        :can-update="canUpdateTenant"
        :can-delete="canDeleteTenant"
        @update:search="tenantSearch = $event"
        @update:status="tenantStatus = $event"
        @update:page="changeDirectoryPage"
        @select="selectTenant"
        @view="selectTenant"
        @create="openTenantEditor()"
        @edit="openTenantEditor"
        @contacts="openTenantContacts"
        @delete="removeTenant"
        @retry="reloadCurrent"
      />

      <TenantWorkspaceDetails
        :active-tab="activeTab"
        :tenant="selectedTenant"
        :contract="selectedContract"
        :contacts="contacts"
        :tenant-contracts="tenantContracts"
        :room-rows="detailRoomRows"
        :parking-spaces="parkingSpaces"
        :overview="tenantOverview"
        :bills="bills"
        :history="history"
        :actionable-lease="currentActionableLease"
        :custom-fields="currentCustomFields"
        :loading="detailLoading"
        :error-text="detailError"
        :can-update-tenant="canUpdateTenant"
        :can-create-contract="canCreateContract"
        :can-update-contract="canUpdateContract"
        :can-approve-contract="canApproveContract"
        :can-activate-contract="canActivateContract"
        :can-terminate-contract="canTerminateContract"
        :can-cancel-contract="canCancelContract"
        :can-check-in="canCheckIn"
        :can-check-out="canCheckOut"
        :can-view-bills="canViewBills"
        :can-view-rooms="canViewRooms"
        :can-manage-fields="currentFieldFacade.canManage.value"
        :can-edit-custom-values="currentValueFacade.canEditValues.value"
        @update:active-tab="changeActiveTab"
        @retry="reloadSelectedContext"
        @edit-tenant="openTenantEditor"
        @create-contract="openContractEditor()"
        @edit-contract="openContractEditor"
        @upload-contract="openContractUpload"
        @preview-contract="previewElectronicContract"
        @submit-contract="confirmContractTransition($event, 'submit')"
        @approve-contract="confirmContractTransition($event, 'approve')"
        @activate-contract="confirmContractTransition($event, 'activate')"
        @terminate-contract="confirmContractTransition($event, 'terminate')"
        @cancel-contract="confirmContractTransition($event, 'cancel')"
        @checkin="openOccupancy('checkin')"
        @checkout="openOccupancy('checkout')"
        @edit-custom-values="openCustomValueEditor"
        @manage-custom-fields="openCustomFieldManager"
        @create-contact="openContactEditor()"
        @edit-contact="openContactEditor"
        @deactivate-contact="deactivateTenantContact"
        @delete-contact="removeTenantContact"
        @promote-responsible="promoteResponsibleContact"
        @set-responsible="setResponsibleContact"
        @set-emergency="setEmergencyContact"
        @open-spaces="openAssetRoom"
        @select-contract="selectContract"
        @back-to-contracts="returnToTenantContracts"
        @open-bill="openBill"
        @open-bills="openBills"
        @upload-supplementary="openSupplementaryUpload"
        @preview-attachment="previewContractAttachment"
      />
    </main>

    <input ref="contractFileInput" type="file" accept="application/pdf" class="hidden-file-input" @change="uploadElectronicContract" />

    <TenantEditorDialog
      v-model="tenantEditorVisible"
      :tenant="tenantEditorRecord"
      :service-managers="serviceManagers"
      :saving="saving"
      :submit-handler="saveTenant"
    />
    <ContactEditorDialog
      v-model="contactEditorVisible"
      :contact="contactEditorRecord"
      :saving="saving"
      :submit-handler="saveContact"
    />
    <ContractEditorDialog
      v-model="contractEditorVisible"
      :contract="contractEditorRecord"
      :tenant="selectedTenant"
      :rooms="rooms"
      :saving="saving"
      :submit-handler="saveContract"
    />
    <ContractOccupancyDialog
      v-model="occupancyVisible"
      :mode="occupancyMode"
      :contract="selectedContract"
      :lease="currentActionableLease"
      :saving="saving"
      :submit-handler="saveOccupancy"
    />

    <CustomFieldValueDrawer
      v-model="customValueVisible"
      :title="`${customTarget?.contractNo || customTarget?.tenantName || '当前记录'} · 自定义字段`"
      :row="customTarget"
      :fields="currentCustomFields"
      :editable="currentValueFacade.canEditValues.value"
      :saving="customValueSaving"
      @save="saveCustomValues"
    />

    <el-drawer v-model="customFieldManagerVisible" :title="`${currentWorkspaceLabel}共享字段`" size="min(580px, 92vw)" destroy-on-close>
      <CustomFieldForm
        :definitions="currentFieldFacade.definitions.value"
        :can-manage="currentFieldFacade.canManage.value"
        :loading="currentFieldFacade.loading.value"
        :saving="currentFieldFacade.saving.value"
        :error="currentFieldFacade.error.value"
        :actions="currentFieldActions"
      />
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Expand, Fold, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import CustomFieldForm from '../../components/workspace/CustomFieldForm.vue'
import CustomFieldValueDrawer from '../../components/workspace/CustomFieldValueDrawer.vue'
import { downloadBlob } from '../../utils/downloadBlob.js'
import { hasPermission } from '../../utils/permission.js'
import request from '../../utils/request.js'
import ContactEditorDialog from './workspace/ContactEditorDialog.vue'
import ContractEditorDialog from './workspace/ContractEditorDialog.vue'
import ContractOccupancyDialog from './workspace/ContractOccupancyDialog.vue'
import TenantDirectoryPane from './workspace/TenantDirectoryPane.vue'
import TenantEditorDialog from './workspace/TenantEditorDialog.vue'
import TenantWorkspaceDetails from './workspace/TenantWorkspaceDetails.vue'
import {
  actionableLeaseForContract,
  contractBelongsToTenant,
  contractsForTenant,
  mergeWorkspaceRouteQuery,
  roomsForContracts
} from './workspace/tenantWorkspace.js'
import { useTenantWorkspace } from './workspace/useTenantWorkspace.js'

defineOptions({ name: 'TenantWorkspaceView' })

const route = useRoute()
const router = useRouter()
const workspace = useTenantWorkspace()
const {
  tenants,
  contracts,
  rooms,
  archiveRooms,
  parkingSpaces,
  contacts,
  leases,
  bills,
  history,
  tenantOverview,
  serviceManagers,
  directoryPage,
  directoryPageSize,
  directoryTotal,
  directoryLoading,
  detailLoading,
  saving,
  directoryError,
  detailError,
  tenantFields,
  contractFields,
  tenantValues,
  contractValues
} = workspace

const selectedTenantId = ref(null)
const selectedContractId = ref(null)
const tenantSearch = ref('')
const tenantStatus = ref('')
const activeTab = ref('overview')
const tenantPaneCollapsed = ref(false)
const routeReady = ref(false)
const tenantEditorVisible = ref(false)
const tenantEditorRecord = ref(null)
const contactEditorVisible = ref(false)
const contactEditorRecord = ref(null)
const contractEditorVisible = ref(false)
const contractEditorRecord = ref(null)
const occupancyVisible = ref(false)
const occupancyMode = ref('checkin')
const contractFileInput = ref(null)
const uploadTarget = ref(null)
const customValueVisible = ref(false)
const customFieldManagerVisible = ref(false)
const customTarget = ref(null)
const customScope = ref('tenant')
const customValueSaving = ref(false)
let routeMutation = false
let directorySearchTimer = null

const canCreateTenant = hasPermission('tenant:create')
const canUpdateTenant = hasPermission('tenant:update')
const canDeleteTenant = hasPermission('tenant:delete')
const canViewContracts = hasPermission('contract:view')
const canCreateContract = canViewContracts && hasPermission('contract:create')
const canUpdateContract = canViewContracts && hasPermission('contract:update')
const canApproveContract = canViewContracts && hasPermission('contract:approve')
const canActivateContract = canViewContracts && hasPermission('contract:activate')
const canTerminateContract = canViewContracts && hasPermission('contract:terminate')
const canCancelContract = canViewContracts && hasPermission('contract:cancel')
const canCheckIn = canViewContracts && hasPermission('lease:checkin')
const canCheckOut = canViewContracts && hasPermission('lease:checkout')
const canViewBills = hasPermission('bill:view')
const canViewRooms = hasPermission('room:view')

const selectedTenant = computed(() => tenants.value.find(item => Number(item.id) === Number(selectedTenantId.value)) || null)
const selectedContract = computed(() => contracts.value.find(item => Number(item.id) === Number(selectedContractId.value)) || null)
const tenantContracts = computed(() => contractsForTenant(contracts.value, selectedTenantId.value, 'ALL'))
const tenantStats = computed(() => new Map())
const currentActionableLease = computed(() => actionableLeaseForContract(leases.value, selectedContractId.value))
const detailRoomRows = computed(() => selectedContract.value
  ? roomsForContracts([selectedContract.value], rooms.value, leases.value)
  : archiveRooms.value)
const currentFieldFacade = computed(() => customScope.value === 'contract' ? contractFields : tenantFields)
const currentValueFacade = computed(() => customScope.value === 'contract' ? contractValues : tenantValues)
const currentCustomFields = computed(() => selectedContract.value
  ? contractFields.activeDefinitions.value
  : tenantFields.activeDefinitions.value)
const currentWorkspaceLabel = computed(() => customScope.value === 'contract' ? '合同' : '租户')
const currentFieldActions = computed(() => {
  const facade = currentFieldFacade.value
  return {
    retry: () => facade.refresh({ includeDisabled: facade.canManage.value }),
    create: facade.create,
    update: facade.update,
    changeStatus: facade.changeStatus,
    remove: facade.remove,
    reorder: facade.reorder
  }
})

watch(
  () => [route.query.tenantId, route.query.contractId, route.query.tab, route.query.panel].join('|'),
  () => {
    if (routeReady.value && !routeMutation) void applyRouteSelection()
  }
)

watch([tenantSearch, tenantStatus], () => {
  if (!routeReady.value) return
  window.clearTimeout(directorySearchTimer)
  directorySearchTimer = window.setTimeout(() => {
    void reloadDirectory({ page: 1, ensureSelection: true })
  }, 280)
})

onMounted(async () => {
  tenantPaneCollapsed.value = window.innerWidth <= 1180
  window.addEventListener('resize', handleViewportResize)
  await workspace.loadDirectory({ keyword: tenantSearch.value, status: tenantStatus.value })
  await workspace.loadServiceManagers()
  routeReady.value = true
  await applyRouteSelection({ normalize: true })
})

onBeforeUnmount(() => {
  window.clearTimeout(directorySearchTimer)
  window.removeEventListener('resize', handleViewportResize)
})

function handleViewportResize() {
  if (window.innerWidth <= 1024) tenantPaneCollapsed.value = true
}

async function applyRouteSelection(options = {}) {
  const requestedTenantId = numericQuery(route.query.tenantId)
  const nextTenant = tenants.value.find(item => Number(item.id) === requestedTenantId) || tenants.value[0] || null
  const requestedContractId = numericQuery(route.query.contractId)
  selectedTenantId.value = nextTenant?.id ?? null
  selectedContractId.value = null
  closeTransientPanels()

  if (!selectedTenantId.value) {
    workspace.clearDetail()
  } else {
    await workspace.loadTenantContext(selectedTenantId.value)
    const nextContract = contracts.value.find(item => Number(item.id) === requestedContractId) || null
    selectedContractId.value = contractBelongsToTenant(nextContract, selectedTenantId.value) ? nextContract.id : null
    customScope.value = selectedContractId.value ? 'contract' : 'tenant'
    activeTab.value = validTab(route.query.tab, Boolean(selectedContractId.value), route.query.panel)
    if (selectedContractId.value) await workspace.loadContractContext(selectedTenantId.value, selectedContractId.value)
  }

  if (options.normalize) {
    await replaceWorkspaceQuery({
      tenantId: selectedTenantId.value,
      contractId: selectedContractId.value,
      tab: activeTab.value,
      panel: undefined
    })
  }
}

async function selectTenant(tenant) {
  selectedTenantId.value = tenant.id
  selectedContractId.value = null
  customScope.value = 'tenant'
  activeTab.value = 'overview'
  closeTransientPanels()
  await replaceWorkspaceQuery({ tenantId: tenant.id, contractId: undefined, tab: 'overview', panel: undefined })
  await workspace.loadTenantContext(tenant.id)
}

async function selectContract(contract) {
  if (!contractBelongsToTenant(contract, selectedTenantId.value)) {
    ElMessage.error('该合同不属于当前租户')
    return
  }
  selectedContractId.value = contract.id
  customScope.value = 'contract'
  activeTab.value = 'overview'
  closeTransientPanels()
  await replaceWorkspaceQuery({ contractId: contract.id, tab: 'overview', panel: undefined })
  await workspace.loadContractContext(selectedTenantId.value, contract.id)
}

async function returnToTenantContracts() {
  selectedContractId.value = null
  customScope.value = 'tenant'
  activeTab.value = 'contracts'
  closeTransientPanels()
  await replaceWorkspaceQuery({ contractId: undefined, tab: 'contracts', panel: undefined })
  await workspace.loadTenantContext(selectedTenantId.value)
}

async function changeActiveTab(tab) {
  activeTab.value = validTab(tab, Boolean(selectedContractId.value))
  await replaceWorkspaceQuery({ tab: activeTab.value })
}

async function replaceWorkspaceQuery(patch) {
  routeMutation = true
  try {
    await router.replace({ path: '/tenant/workspace', query: mergeWorkspaceRouteQuery(route.query, patch) })
  } finally {
    routeMutation = false
  }
}

function openTenantEditor(tenant = null) {
  tenantEditorRecord.value = tenant
    ? {
        ...tenant,
        logoFileId: Object.prototype.hasOwnProperty.call(tenantOverview.value || {}, 'logoFileId')
          ? tenantOverview.value.logoFileId
          : tenant.logoFileId ?? null,
        logoUrl: Object.prototype.hasOwnProperty.call(tenantOverview.value || {}, 'logoUrl')
          ? tenantOverview.value.logoUrl || ''
          : tenant.logoUrl || '',
        logoFileName: Object.prototype.hasOwnProperty.call(tenantOverview.value || {}, 'logoFileName')
          ? tenantOverview.value.logoFileName || ''
          : tenant.logoFileName || '',
        companyShortName: tenantOverview.value?.companyShortName ?? tenant.companyShortName,
        invoiceTitle: tenantOverview.value?.invoiceTitle ?? tenant.invoiceTitle,
        taxNumber: tenantOverview.value?.taxNumber ?? tenant.taxNumber,
        serviceManagerUserId: tenantOverview.value?.serviceManager?.id ?? tenant.serviceManagerUserId
      }
    : null
  tenantEditorVisible.value = true
}

async function openTenantContacts(tenant) {
  await selectTenant(tenant)
  await changeActiveTab('contacts')
}

async function saveTenant(payload, logoChange = null) {
  try {
    const result = await workspace.saveTenant(payload)
    if (payload.id && logoChange) {
      await workspace.updateTenantLogo(payload.id, logoChange.logoFileId)
    }
    ElMessage.success(payload.id ? '租户已更新' : '租户已创建')
    await reloadDirectory()
    const nextId = result?.id || payload.id || selectedTenantId.value
    const next = tenants.value.find(item => Number(item.id) === Number(nextId)) || tenants.value[0]
    if (next) await selectTenant(next)
    return result
  } catch (error) {
    ElMessage.error(errorMessage(error, '租户保存失败'))
    return { success: false }
  }
}

async function removeTenant(tenant) {
  try {
    await ElMessageBox.confirm(`确定删除租户“${tenant.tenantName}”吗？有关联业务时后端将拒绝删除。`, '删除租户', { type: 'warning' })
    await workspace.deleteTenant(tenant.id)
    ElMessage.success('租户已删除')
    await reloadDirectory()
    await applyRouteSelection({ normalize: true })
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(errorMessage(error, '租户删除失败'))
  }
}

function openContactEditor(contact = null) {
  contactEditorRecord.value = contact
  contactEditorVisible.value = true
}

async function saveContact(payload) {
  try {
    const result = await workspace.saveContact(selectedTenantId.value, payload)
    const messages = [
      result?.loginHint || (payload.id ? '联系人已更新' : '联系人已创建')
    ]
    if (Number(result?.boundMobileUserCount || 0) > 0) messages.push('已绑定微信用户')
    if (result?.multipleIdentities) messages.push('该手机号存在多个业务身份，用户登录时可选择身份。')
    ElMessage.success(messages.join('；'))
    await workspace.loadTenantContext(selectedTenantId.value)
    return result
  } catch (error) {
    ElMessage.error(errorMessage(error, '联系人保存失败'))
    return { success: false }
  }
}

async function deactivateTenantContact(contact) {
  try {
    await ElMessageBox.confirm(`停用联系人“${contact.name}”后，其小程序绑定将不可继续使用。`, '停用联系人', { type: 'warning' })
    await workspace.deactivateContact(contact.id)
    ElMessage.success('联系人已停用')
    await workspace.loadTenantContext(selectedTenantId.value)
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(errorMessage(error, '联系人停用失败'))
  }
}

async function removeTenantContact(contact) {
  try {
    await ElMessageBox.confirm(`确定删除联系人“${contact.name}”吗？`, '删除联系人', { type: 'warning' })
    await workspace.deleteContact(selectedTenantId.value, contact.id)
    ElMessage.success('联系人已删除')
    await workspace.loadTenantContext(selectedTenantId.value)
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(errorMessage(error, '联系人删除失败'))
  }
}

async function setResponsibleContact(contact) {
  try {
    await ElMessageBox.confirm(`将“${contact.name}”设为主负责人？当前其他负责人保持不变。`, '设置主负责人', { type: 'warning' })
    await workspace.setResponsibleContact(contact.id)
    ElMessage.success('主负责人已切换')
    await workspace.loadTenantContext(selectedTenantId.value)
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(errorMessage(error, '设置主负责人失败'))
  }
}

async function promoteResponsibleContact(contact) {
  try {
    await ElMessageBox.confirm(`将“${contact.name}”设为租户负责人？当前其他负责人保持不变。`, '设置负责人', { type: 'warning' })
    const result = await workspace.saveContact(selectedTenantId.value, {
      id: contact.id,
      name: contact.name,
      phone: contact.phone,
      email: contact.email || '',
      position: contact.position || '',
      role: 'RESPONSIBLE',
      isPrimary: false,
      isEmergency: Boolean(contact.isEmergency),
      status: contact.status || 'ACTIVE'
    })
    if (result?.success === false) return
    ElMessage.success('负责人已设置')
    await workspace.loadTenantContext(selectedTenantId.value)
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(errorMessage(error, '设置负责人失败'))
  }
}

async function setEmergencyContact(contact) {
  try {
    await workspace.setEmergencyContact(contact.id)
    ElMessage.success('紧急联系人已切换')
    await workspace.loadTenantContext(selectedTenantId.value)
  } catch (error) {
    ElMessage.error(errorMessage(error, '设置紧急联系人失败'))
  }
}

function openContractEditor(contract = null) {
  if (!selectedTenant.value) return
  contractEditorRecord.value = contract
  contractEditorVisible.value = true
}

async function saveContract(payload, source) {
  try {
    const result = await workspace.saveContract(payload, source)
    ElMessage.success(source?.id ? '合同已更新' : '合同已创建')
    await reloadDirectory()
    const nextId = result?.id || source?.id
    const next = contracts.value.find(item => Number(item.id) === Number(nextId))
    if (next) await selectContract(next)
    else await workspace.loadTenantContext(selectedTenantId.value)
    return result
  } catch (error) {
    ElMessage.error(errorMessage(error, '合同保存失败'))
    return { success: false }
  }
}

async function confirmContractTransition(contract, action) {
  const labels = {
    submit: '提交审批',
    approve: '审批通过',
    activate: '恢复生效',
    terminate: '终止合同',
    cancel: '作废合同'
  }
  try {
    const reasonRequired = ['terminate', 'cancel'].includes(action)
    let reason = `租户工作台${labels[action]}`
    if (reasonRequired) {
      const response = await ElMessageBox.prompt(`请填写${labels[action]}原因`, labels[action], {
        inputType: 'textarea',
        inputValidator: value => Boolean(String(value || '').trim()) || '原因不能为空'
      })
      reason = response.value.trim()
    } else {
      await ElMessageBox.confirm(`确定对合同“${contract.contractNo}”执行${labels[action]}吗？`, labels[action], { type: 'warning' })
    }
    await workspace.transitionContract(contract, action, reason)
    ElMessage.success(`${labels[action]}成功`)
    await reloadCurrent()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(errorMessage(error, `${labels[action]}失败`))
  }
}

function openOccupancy(mode) {
  if (!selectedContract.value) return
  if (mode === 'checkout' && !currentActionableLease.value) {
    ElMessage.warning('当前合同没有唯一可办理退租的租约，请先核验履约状态')
    return
  }
  occupancyMode.value = mode
  occupancyVisible.value = true
}

async function saveOccupancy(payload) {
  try {
    const result = occupancyMode.value === 'checkin'
      ? await workspace.checkInContract(selectedContract.value, payload)
      : await workspace.checkOutLease(currentActionableLease.value, payload)
    ElMessage.success(occupancyMode.value === 'checkin' ? '入住办理成功' : '退租办理成功')
    await reloadCurrent()
    return result
  } catch (error) {
    ElMessage.error(errorMessage(error, occupancyMode.value === 'checkin' ? '入住办理失败' : '退租办理失败'))
    return { success: false }
  }
}

function openContractUpload(contract) {
  uploadTarget.value = { contract, attachmentType: 'MAIN_CONTRACT' }
  contractFileInput.value.value = ''
  contractFileInput.value.click()
}

function openSupplementaryUpload(contract) {
  uploadTarget.value = { contract, attachmentType: 'SUPPLEMENTARY_AGREEMENT' }
  contractFileInput.value.value = ''
  contractFileInput.value.click()
}

async function uploadElectronicContract(event) {
  const file = event.target.files?.[0]
  const { contract, attachmentType } = uploadTarget.value || {}
  if (!file || !contract) return
  if (file.type !== 'application/pdf' && !file.name.toLowerCase().endsWith('.pdf')) {
    ElMessage.error('只能上传 PDF 电子合同')
    return
  }
  const body = new FormData()
  body.append('file', file)
  try {
    saving.value = true
    await request.post(`/contracts/${contract.id}/attachments`, body, {
      params: { attachmentType },
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    const successMessage = attachmentType === 'SUPPLEMENTARY_AGREEMENT'
      ? '补充协议已上传'
      : mainContractUploadMessage(contract)
    ElMessage.success(successMessage)
    await workspace.loadTenantContext(selectedTenantId.value)
  } catch (error) {
    ElMessage.error(errorMessage(error, '电子合同上传失败'))
  } finally {
    saving.value = false
    uploadTarget.value = null
    event.target.value = ''
  }
}

function mainContractUploadMessage(contract) {
  if (contract?.status === 'DRAFT') {
    return '主合同已上传，当前仍为草稿；提交审批并审批通过后才会计入租赁摘要'
  }
  if (['PENDING', 'PENDING_APPROVAL'].includes(contract?.status)) {
    return '主合同已上传，当前仍待审批；审批通过且进入当前租期后才会计入租赁摘要'
  }
  return '主合同已上传'
}

async function previewContractAttachment({ contract, attachment }) {
  const previewWindow = window.open('', '_blank')
  try {
    const blob = await request.get(
      `/contracts/${contract.id}/attachments/${attachment.attachmentId}/download`,
      { responseType: 'blob' }
    )
    downloadBlob(blob, attachment.fileName || '合同附件.pdf', { previewWindow, download: false })
  } catch (error) {
    previewWindow?.close()
    ElMessage.error(errorMessage(error, '合同附件打开失败'))
  }
}

async function previewElectronicContract(contract) {
  const previewWindow = window.open('', '_blank')
  try {
    const blob = await request.get(`/contracts/${contract.id}/file/download`, { responseType: 'blob' })
    downloadBlob(blob, contract.contractFileName || `${contract.contractNo || '电子合同'}.pdf`, { previewWindow, download: false })
  } catch (error) {
    previewWindow?.close()
    ElMessage.error(errorMessage(error, '电子合同打开失败'))
  }
}

function openCustomValueEditor(record) {
  customScope.value = selectedContract.value ? 'contract' : 'tenant'
  customTarget.value = record
  customValueVisible.value = true
}

function openCustomFieldManager() {
  customScope.value = selectedContract.value ? 'contract' : 'tenant'
  customFieldManagerVisible.value = true
}

async function saveCustomValues({ row, values, complete }) {
  customValueSaving.value = true
  try {
    const result = await currentValueFacade.value.saveRecord(row, values)
    complete(result)
    if (result.success) ElMessage.success('自定义字段已保存')
  } finally {
    customValueSaving.value = false
  }
}

function openAssetRoom(room) {
  if (!room) return
  router.push({
    path: '/assets/spaces',
    query: mergeWorkspaceRouteQuery({}, {
      buildingId: room.buildingId,
      floorId: room.floorId,
      roomId: room.roomId || room.id
    })
  })
}

function openBills() {
  router.push({
    path: '/bills',
    query: mergeWorkspaceRouteQuery({}, {
      tenantId: selectedTenantId.value,
      contractId: selectedContractId.value
    })
  })
}

function openBill(bill) {
  router.push({
    path: '/bills',
    query: mergeWorkspaceRouteQuery({}, {
      tenantId: selectedTenantId.value,
      contractId: selectedContractId.value,
      keyword: bill.billNumber || bill.code
    })
  })
}

async function reloadCurrent() {
  await reloadDirectory()
  await reloadSelectedContext()
}

async function reloadDirectory(options = {}) {
  await workspace.loadDirectory({
    keyword: tenantSearch.value,
    status: tenantStatus.value,
    page: options.page || directoryPage.value
  })
  if (options.ensureSelection && !selectedTenant.value) {
    const next = tenants.value[0]
    if (next) await selectTenant(next)
    else {
      selectedTenantId.value = null
      selectedContractId.value = null
      workspace.clearDetail()
    }
  }
}

async function changeDirectoryPage(page) {
  await reloadDirectory({ page, ensureSelection: true })
}

async function reloadSelectedContext() {
  if (!selectedTenantId.value) return
  await workspace.loadTenantContext(selectedTenantId.value)
  if (selectedContractId.value) await workspace.loadContractContext(selectedTenantId.value, selectedContractId.value)
}

function closeTransientPanels() {
  customValueVisible.value = false
  customFieldManagerVisible.value = false
}

function validTab(value, contractMode, panel) {
  const contractTabs = new Set(['overview', 'rooms', 'file', 'history', 'bills'])
  const tenantTabs = new Set(['overview', 'contacts', 'spaces', 'contracts', 'bills'])
  const requested = String(value || (panel === 'contracts' ? 'contracts' : panel === 'occupancy' ? 'contracts' : 'overview'))
  return (contractMode ? contractTabs : tenantTabs).has(requested) ? requested : 'overview'
}

function numericQuery(value) {
  const result = Number(value)
  return Number.isFinite(result) && result > 0 ? result : null
}

function errorMessage(error, fallback) {
  return error?.response?.data?.message || error?.message || fallback
}

</script>

<style scoped>
.tenant-workspace-page {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  gap: 12px;
  height: calc(100vh - var(--dhic-header-height) - (var(--dhic-page-padding) * 2));
  min-height: 620px;
  padding: 0;
  overflow: hidden;
  background: var(--dhic-page-bg);
}

.page-heading,
.page-heading-actions {
  display: flex;
  align-items: center;
}

.page-heading {
  justify-content: space-between;
  gap: 16px;
  min-height: 56px;
  padding: 0;
}

.page-heading h1 {
  margin: 0;
  color: var(--dhic-text);
  font-size: 20px;
  font-weight: 600;
  letter-spacing: -0.2px;
}

.page-heading p {
  margin: 4px 0 0;
  color: var(--dhic-text-muted);
  font-size: 13px;
}

.page-heading-actions {
  gap: 8px;
}

.page-heading-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.page-heading-actions :deep(.el-button) {
  border-color: var(--dhic-border-color);
  border-radius: 6px;
  background: #fff;
}

.tenant-workspace-shell {
  display: grid;
  grid-template-columns: 296px minmax(0, 1fr);
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  border: 1px solid var(--dhic-border-soft);
  border-radius: var(--dhic-radius-large);
  background: #fff;
  box-shadow: var(--dhic-shadow-card);
}

.tenant-workspace-shell > * + * {
  border-left: 1px solid var(--dhic-border-soft);
}

.tenant-workspace-shell.is-tenant-collapsed {
  grid-template-columns: 44px minmax(0, 1fr);
}

.collapsed-tenant-rail {
  border: 0;
  padding: 12px 0;
  background: var(--dhic-page-bg);
  color: var(--dhic-text-secondary);
  font-size: 12px;
  writing-mode: vertical-rl;
  cursor: pointer;
}

.collapsed-tenant-rail:focus-visible {
  outline: 2px solid var(--dhic-primary);
  outline-offset: -2px;
}

.hidden-file-input {
  display: none;
}

@media (max-width: 1180px) {
  .tenant-workspace-shell:not(.is-tenant-collapsed) {
    grid-template-columns: 264px minmax(0, 1fr);
  }

  .tenant-workspace-shell.is-tenant-collapsed {
    grid-template-columns: 44px minmax(0, 1fr);
  }
}
</style>
