<template>
  <main class="page-container">
    <DataWorkspace
      :workspace-config="investmentLeadWorkspaceConfig"
      :views="workspaceViews"
      :active-view-id="activeViewId"
      :view-dirty="isViewDirty"
      :filter-logic="tablePrefs.filterLogic"
      :filter-logic-visible="tablePrefs.filters.length > 1"
      :filter-items="activeFilterItems"
      :query-notices="queryNotices"
      :metric-data="summary"
      @change-view="switchLeadView"
      @create-view="createTableView"
      @duplicate-view="duplicateTableView"
      @rename-view="renameTableView"
      @delete-view="deleteTableView"
      @save-view="saveLeadView"
      @remove-filter="removeViewFilter"
    >
      <template #toolbar>
        <DhicDataToolbar
          :workspace-config="investmentLeadWorkspaceConfig"
          allow-column-layout
          field-config-saveable
          :view-name="activeViewName"
          :field-config-dirty="isViewDirty"
          :columns="orderedColumns"
          :visible-columns="tablePrefs.visibleColumns"
          :column-order="tablePrefs.columnOrder"
          :fixed-columns="tablePrefs.fixedColumns"
          :filter-options="filterOptions"
          :filters="tablePrefs.filters"
          :filter-logic="tablePrefs.filterLogic"
          :filter-logics="filterLogicOptions"
          :sort-options="sortOptions"
          :sorts="tablePrefs.sorts"
          :sort-key="tablePrefs.sortKey"
          :sort-order="tablePrefs.sortOrder"
          :sort-count="sortCount"
          :group-options="groupOptions"
          :groups="tablePrefs.groups"
          :group-key="tablePrefs.groupKey"
          :group-count="groupCount"
          :row-density="tablePrefs.rowDensity"
          :active-filter-count="activeFilterCount"
          :filter-items="activeFilterItems"
          @update:visible-columns="updateVisibleColumns"
          @update:column-order="updateColumnOrder"
          @update:fixed-columns="updateFixedColumns"
          @update:filters="updateFilters"
          @update:filter-logic="updateFilterLogic"
          @update:sort-key="updateSortKey"
          @update:sort-order="updateSortOrder"
          @update:sorts="updateSorts"
          @update:group-key="updateGroupKey"
          @update:groups="updateGroups"
          @update:row-density="updateRowDensity"
          @reset-view="resetTableView"
          @save-field-config="saveLeadView"
        >
          <template #right>
            <el-button :icon="Refresh" @click="loadWorkspaceData">刷新</el-button>
            <span class="view-hint">当前页 {{ resultScope.loadedRecords }} 条 / 共 {{ resultScope.serverTotal }} 条</span>
          </template>
        </DhicDataToolbar>
      </template>

      <template #grid>
        <WorkspaceDataGrid
          :loading="loading"
          :error-text="loadError"
          retryable
          :rows="tableRows"
          :columns="visibleOrderedColumns"
          row-key="id"
          :current-page="pagination.currentPage"
          :page-size="pagination.pageSize"
          :density="tablePrefs.rowDensity"
          :empty-text="tableEmptyText"
          :sort-state="activeTableSort"
          :data-mode="investmentLeadWorkspaceConfig.query.dataMode"
          :column-width="columnWidth"
          :column-fixed="columnFixed"
          :is-column-sortable="isColumnSortable"
          :show-overflow-tooltip="column => ['remark', 'intendedRoom', 'intendedUse'].includes(column.key)"
          :operation-column="{ width: 270, fixed: 'right', align: 'left' }"
          @column-resize="handleHeaderDragEnd"
          @sort-change="handleTableSortChange"
          @retry="loadWorkspaceData"
          aria-label="招商线索数据表格"
        >
          <template #cell-phone="{ row }">
            <span :class="{ 'invalid-lead-field': row.phone && !isValidLeadPhone(row.phone) }">
              {{ leadFieldText(row.phone, 'phone') }}
            </span>
          </template>
          <template #cell-companyName="{ row }">
            <span :class="{ 'invalid-lead-field': row.companyName && !isValidLeadCompany(row.companyName) }">
              {{ leadFieldText(row.companyName, 'company') }}
            </span>
          </template>
          <template #cell-source="{ row }">
            <el-tag v-if="row.source === 'MINIPROGRAM'" type="success" size="small">小程序</el-tag>
            <el-tag v-else type="info" size="small">{{ row.source || '后台' }}</el-tag>
          </template>
          <template #operations="{ row }">
            <div class="lead-actions">
              <el-button link @click="openDetail(row)">查看</el-button>
              <el-button
                v-if="hasPermission('investment-lead:follow') && row.status === 'NEW'"
                link
                type="primary"
                @click="updateStatus(row, 'FOLLOWING')"
              >跟进</el-button>
              <el-button
                v-if="hasPermission('investment-lead:follow') && ['NEW', 'FOLLOWING'].includes(row.status)"
                link
                @click="updateStatus(row, 'VIEWING_SCHEDULED')"
              >已约看房</el-button>
              <el-button
                v-if="hasPermission('investment-lead:follow') && !['CONVERTED', 'INVALID'].includes(row.status)"
                link
                @click="updateStatus(row, 'VIEWED')"
              >已看房</el-button>
              <el-button
                v-if="hasPermission('investment-lead:follow') && !['CONVERTED', 'INVALID'].includes(row.status)"
                link
                type="warning"
                @click="updateStatus(row, 'INVALID')"
              >无效</el-button>
              <el-button
                v-if="hasPermission('investment-lead:convert') && ['FOLLOWING', 'VIEWED'].includes(row.status)"
                link
                type="success"
                @click="updateStatus(row, 'CONVERTED')"
              >已转化</el-button>
              <el-button
                v-if="hasPermission('investment-lead:delete') && row.status === 'INVALID'"
                link
                type="danger"
                @click="deleteLead(row)"
              >删除</el-button>
            </div>
          </template>
        </WorkspaceDataGrid>
      </template>

      <template #footer>
        <div class="pagination-wrapper">
          <el-pagination
            v-model:current-page="pagination.currentPage"
            v-model:page-size="pagination.pageSize"
            :total="pagination.total"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            @current-change="loadLeads"
            @size-change="handleSizeChange"
          />
        </div>
      </template>
    </DataWorkspace>

    <el-drawer v-model="detailVisible" title="招商线索详情" size="min(520px, 92vw)">
      <el-descriptions v-if="detailLead" :column="1" border>
        <el-descriptions-item label="联系人">{{ detailLead.name || '-' }}</el-descriptions-item>
        <el-descriptions-item label="联系电话">{{ detailLead.phone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="公司名称">{{ detailLead.companyName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="来源">{{ detailLead.source === 'MINIPROGRAM' ? '小程序' : (detailLead.source || '后台') }}</el-descriptions-item>
        <el-descriptions-item label="意向房源">{{ detailLead.intendedRoom || '-' }}</el-descriptions-item>
        <el-descriptions-item label="预约快照">
          {{ [detailLead.buildingNameSnapshot, detailLead.floorSnapshot, detailLead.roomCodeSnapshot].filter(Boolean).join(' / ') || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="意向面积">{{ detailLead.desiredArea ? `${detailLead.desiredArea} ㎡` : '-' }}</el-descriptions-item>
        <el-descriptions-item label="使用目的">{{ detailLead.intendedUse || '-' }}</el-descriptions-item>
        <el-descriptions-item label="期望看房时间">{{ detailLead.preferredVisitTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="当前状态">{{ leadStatusText(detailLead.status) }}</el-descriptions-item>
        <el-descriptions-item label="提交时间">{{ detailLead.createdTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ detailLead.remark || '-' }}</el-descriptions-item>
        <el-descriptions-item label="提交用户">
          {{ detailLead.mobilePhoneMasked || '-' }} · {{ detailLead.mobileUserType || '-' }} · {{ detailLead.mobileUserStatus || '-' }}
        </el-descriptions-item>
      </el-descriptions>
      <section v-if="detailLead" class="follow-section">
        <h3>跟进记录</h3>
        <el-empty v-if="!detailLead.followRecords?.length" description="暂无跟进记录" :image-size="64" />
        <el-timeline v-else>
          <el-timeline-item
            v-for="item in detailLead.followRecords"
            :key="item.id"
            :timestamp="item.createdAt"
          >
            {{ item.content }}
            <div class="follow-meta">{{ item.followType }}<span v-if="item.nextFollowAt"> · 下次 {{ item.nextFollowAt }}</span></div>
          </el-timeline-item>
        </el-timeline>
        <el-form v-if="hasPermission('investment-lead:follow')" label-position="top">
          <el-form-item label="新增跟进">
            <el-input v-model="followForm.content" type="textarea" :rows="3" maxlength="1000" show-word-limit />
          </el-form-item>
          <div class="follow-form-row">
            <el-select v-model="followForm.followType" style="width: 150px">
              <el-option label="电话" value="PHONE" />
              <el-option label="微信" value="WECHAT" />
              <el-option label="线下" value="OFFLINE" />
              <el-option label="看房" value="VISIT" />
              <el-option label="其他" value="OTHER" />
            </el-select>
            <el-date-picker
              v-model="followForm.nextFollowAt"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
              placeholder="下次跟进时间"
            />
            <el-button type="primary" :loading="followSaving" @click="saveFollow">保存跟进</el-button>
          </div>
        </el-form>
      </section>
    </el-drawer>
  </main>
</template>

<script setup>
import { onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import DataWorkspace from '../../components/DataWorkspace.vue'
import DhicDataToolbar from '../../components/DhicDataToolbar.vue'
import WorkspaceDataGrid from '../../components/workspace/WorkspaceDataGrid.vue'
import { useDhicTableView } from '../../composables/useDhicTableView'
import request from '../../utils/request'
import { createPagination, pageParams, readPage, resetToFirstPage } from '../../utils/pagination'
import { hasPermission } from '../../utils/permission'
import { createLatestRequestGuard } from '../../utils/requestLifecycle'
import {
  investmentLeadStatusOptions,
  investmentLeadWorkspaceConfig
} from '../../workspaceConfigs/investmentWorkspaceConfig'
import { workspaceTableOptions } from '../../workspaceConfigs/workspaceConfig'

const leads = ref([])
const loading = ref(false)
const loadError = ref('')
const detailVisible = ref(false)
const detailLead = ref(null)
const followSaving = ref(false)
const followForm = reactive({ followType: 'PHONE', content: '', nextFollowAt: '' })
const pagination = reactive(createPagination(20))
const summary = reactive(emptySummary())
const summaryRequestGuard = createLatestRequestGuard()
const leadPermissions = investmentLeadWorkspaceConfig.permissions

const {
  workspaceViews,
  activeViewId,
  activeViewName,
  isViewDirty,
  tablePrefs,
  orderedColumns,
  visibleOrderedColumns,
  tableRows,
  tableEmptyText,
  sortOptions,
  groupOptions,
  filterOptions,
  filterLogicOptions,
  serverQueryParams,
  queryNotices,
  queryRequestGuard: listRequestGuard,
  debouncedQueryTask: debouncedLeadQuery,
  disposeQueryLifecycle,
  resultScope,
  activeFilterItems,
  activeFilterCount,
  groupCount,
  sortCount,
  activeTableSort,
  updateVisibleColumns,
  updateColumnOrder,
  updateFixedColumns,
  columnWidth,
  columnFixed,
  handleHeaderDragEnd,
  handleTableSortChange,
  isColumnSortable,
  updateFilterLogic,
  updateFilters,
  updateSortKey,
  updateSortOrder,
  updateSorts,
  updateGroupKey,
  updateGroups,
  updateRowDensity,
  resetTableView,
  switchTableView,
  createTableView,
  renameTableView,
  duplicateTableView,
  deleteTableView,
  saveCurrentView,
  discardCurrentViewChanges,
  removeViewFilter
} = useDhicTableView(workspaceTableOptions(investmentLeadWorkspaceConfig, {
  records: () => leads.value,
  serverTotal: () => pagination.total,
  queryLifecycle: {
    task: () => {
      resetToFirstPage(pagination)
      loadWorkspaceData()
    }
  },
  groupLabel: value => investmentLeadStatusOptions.find(option => option.value === value)?.label || value || '未填写'
}))

watch(() => JSON.stringify(serverQueryParams.value), () => debouncedLeadQuery.schedule())

function emptySummary() {
  return { totalCount: 0, newCount: 0, followingCount: 0, convertedCount: 0 }
}

function summaryFilterParams() {
  const params = serverQueryParams.value
  return { keyword: params.keyword, status: params.status, source: params.source }
}

function requestErrorMessage(error, fallback) {
  return error?.response?.data?.message || error?.message || error?.msg || fallback
}

async function loadLeads() {
  if (!hasPermission(leadPermissions.view)) {
    leads.value = []
    pagination.total = 0
    return
  }
  const requestId = listRequestGuard.start()
  loading.value = true
  loadError.value = ''
  try {
    const data = await request.get('/investment/leads', {
      params: { ...serverQueryParams.value, ...pageParams(pagination) },
      silent: true
    })
    if (!listRequestGuard.isCurrent(requestId)) return
    const page = readPage(data)
    leads.value = page.records
    pagination.total = page.total
  } catch (error) {
    if (!listRequestGuard.isCurrent(requestId)) return
    leads.value = []
    pagination.total = 0
    loadError.value = requestErrorMessage(error, '招商线索加载失败')
  } finally {
    if (listRequestGuard.isCurrent(requestId)) loading.value = false
  }
}

async function loadSummary() {
  if (!hasPermission(leadPermissions.view)) {
    Object.assign(summary, emptySummary())
    return
  }
  const requestId = summaryRequestGuard.start()
  try {
    const data = await request.get('/investment/leads/summary', {
      params: summaryFilterParams(),
      silent: true
    })
    if (summaryRequestGuard.isCurrent(requestId)) Object.assign(summary, emptySummary(), data || {})
  } catch {
    if (summaryRequestGuard.isCurrent(requestId)) Object.assign(summary, emptySummary())
  }
}

async function loadWorkspaceData() {
  await Promise.all([loadLeads(), loadSummary()])
}

async function handleSizeChange() {
  resetToFirstPage(pagination)
  await loadLeads()
}

async function switchLeadView(viewId) {
  if (isViewDirty.value) {
    try {
      await ElMessageBox.confirm('当前视图有未保存的调整，是否保存后切换？', '切换视图', {
        distinguishCancelAndClose: true,
        confirmButtonText: '保存并切换',
        cancelButtonText: '放弃调整'
      })
      saveCurrentView()
    } catch (action) {
      if (action === 'cancel') discardCurrentViewChanges()
      else return
    }
  }
  switchTableView(viewId)
}

function saveLeadView() {
  saveCurrentView()
  ElMessage.success(`视图“${activeViewName.value}”已保存`)
}

const leadPhonePattern = /^(1[3-9]\d{9}|0\d{2,3}-?\d{7,8})$/
const leadCompanyPattern = /^(?!\d+$)[\u4e00-\u9fa5a-zA-Z0-9（）()\-—\s]{2,50}$/
const unsafeTextPattern = /(<[^>]*>|<\/?script|javascript:|onerror\s*=|onclick\s*=|--|\/\*|\*\/|;)/i
const hasUnsafeText = value => unsafeTextPattern.test(String(value || ''))
const isValidLeadPhone = value => !String(value || '').trim() || leadPhonePattern.test(String(value).trim())
const isValidLeadCompany = value => {
  const text = String(value || '').trim()
  return !text || (leadCompanyPattern.test(text) && !hasUnsafeText(text))
}
const leadFieldText = (value, type) => {
  if (!value) return '-'
  const valid = type === 'phone' ? isValidLeadPhone(value) : isValidLeadCompany(value)
  return valid ? value : `${value}（格式异常）`
}

async function updateStatus(row, status) {
  await request.patch(`/investment/leads/${row.id}/status`, { status })
  ElMessage.success('状态已更新')
  await loadWorkspaceData()
}

async function openDetail(row) {
  detailLead.value = { ...row, followRecords: [] }
  detailVisible.value = true
  try {
    detailLead.value = await request.get(`/investment/leads/${row.id}`, { silent: true })
  } catch (error) {
    ElMessage.error(requestErrorMessage(error, '招商线索详情加载失败'))
  }
}

async function saveFollow() {
  if (!detailLead.value?.id || !followForm.content.trim()) {
    ElMessage.warning('请输入跟进内容')
    return
  }
  followSaving.value = true
  try {
    await request.post(`/investment/leads/${detailLead.value.id}/follow-records`, {
      followType: followForm.followType,
      content: followForm.content.trim(),
      nextFollowAt: followForm.nextFollowAt || null
    })
    Object.assign(followForm, { followType: 'PHONE', content: '', nextFollowAt: '' })
    await openDetail(detailLead.value)
    await loadWorkspaceData()
    ElMessage.success('跟进记录已保存')
  } finally {
    followSaving.value = false
  }
}

function leadStatusText(status) {
  return investmentLeadStatusOptions.find(option => option.value === status)?.label || status || '-'
}

async function deleteLead(row) {
  await ElMessageBox.confirm(`确认删除线索 ${row.name || row.id}？`, '提示', { type: 'warning' })
  await request.delete(`/investment/leads/${row.id}`)
  ElMessage.success('线索已删除')
  await loadWorkspaceData()
}

onMounted(loadWorkspaceData)
onBeforeUnmount(() => {
  disposeQueryLifecycle()
  summaryRequestGuard.dispose()
})
</script>

<style scoped>
.page-container { padding: 20px; }
.view-hint { color: var(--el-text-color-secondary); font-size: 12px; white-space: nowrap; }
.lead-actions { display: flex; align-items: center; gap: 4px; white-space: nowrap; }
.invalid-lead-field { color: var(--el-color-danger); }
.pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 16px; overflow-x: auto; }
.follow-section { margin-top: 20px; }
.follow-section h3 { margin: 0 0 14px; font-size: 15px; }
.follow-form-row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.follow-meta { margin-top: 4px; color: var(--el-text-color-secondary); font-size: 12px; }
</style>
