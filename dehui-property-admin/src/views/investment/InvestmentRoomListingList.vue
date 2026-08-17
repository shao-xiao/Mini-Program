<template>
  <main class="page-container" :class="{ 'page-container--embedded': props.embedded }">
    <DataWorkspace
      :workspace-config="investmentListingWorkspaceConfig"
      :views="workspaceViews"
      :active-view-id="activeViewId"
      :view-dirty="isViewDirty"
      :filter-logic="tablePrefs.filterLogic"
      :filter-logic-visible="tablePrefs.filters.length > 1"
      :filter-items="activeFilterItems"
      :query-notices="queryNotices"
      :metric-data="summary"
      @change-view="switchListingView"
      @create-view="createTableView"
      @duplicate-view="duplicateTableView"
      @rename-view="renameTableView"
      @delete-view="deleteTableView"
      @save-view="saveListingView"
      @remove-filter="removeViewFilter"
    >
      <template #header-actions>
        <el-button
          v-if="hasPermission('investment-listing:create')"
          type="primary"
          :icon="Plus"
          @click="openCreate"
        >新增招商房源</el-button>
      </template>

      <template #toolbar>
        <DhicDataToolbar
          :workspace-config="investmentListingWorkspaceConfig"
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
          @save-field-config="saveListingView"
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
          :data-mode="investmentListingWorkspaceConfig.query.dataMode"
          :column-width="columnWidth"
          :column-fixed="columnFixed"
          :is-column-sortable="isColumnSortable"
          :show-overflow-tooltip="column => ['title', 'buildingName', 'floorName'].includes(column.key)"
          :operation-column="{ width: 278, fixed: 'right', align: 'left' }"
          @column-resize="handleHeaderDragEnd"
          @sort-change="handleTableSortChange"
          @retry="loadWorkspaceData"
          aria-label="招商房源数据表格"
        >
          <template #cell-coverImageUrl="{ row }">
            <div class="cover-box">
              <img v-if="row.coverImageUrl" :src="row.coverImageUrl" alt="房源封面" />
              <span v-else>未上传</span>
            </div>
          </template>
          <template #cell-title="{ row }">
            <span class="listing-title">{{ row.title || fallbackTitle(row) }}</span>
          </template>
          <template #cell-recommended="{ row }">
            <el-tag v-if="row.recommended" size="small" type="success">推荐</el-tag>
            <span v-else>-</span>
          </template>
          <template #operations="{ row }">
            <div class="listing-actions">
              <el-button v-if="hasPermission('investment-listing:update')" link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button v-if="hasPermission('investment-listing:image')" link @click="openImages(row)">图片</el-button>
              <el-button link @click="openPreview(row)">预览</el-button>
              <el-button
                v-if="hasPermission('investment-listing:publish') && row.publishStatus !== 'PUBLISHED'"
                link
                type="success"
                @click="publish(row)"
              >上架</el-button>
              <el-button
                v-else-if="hasPermission('investment-listing:publish')"
                link
                type="warning"
                @click="offline(row)"
              >下架</el-button>
              <el-button v-if="hasPermission('investment-listing:delete')" link type="danger" @click="remove(row)">删除</el-button>
            </div>
          </template>
        </WorkspaceDataGrid>
      </template>

      <template #footer>
        <el-pagination
          v-model:current-page="pagination.currentPage"
          v-model:page-size="pagination.pageSize"
          class="pagination"
          layout="total, sizes, prev, pager, next, jumper"
          :total="pagination.total"
          :page-sizes="[10, 20, 50, 100]"
          @size-change="handleSizeChange"
          @current-change="loadListings"
        />
      </template>
    </DataWorkspace>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑招商房源' : '新增招商房源'" width="760px">
      <el-form label-width="120px" :model="form">
        <el-form-item label="关联房间" required>
          <div class="room-picker">
            <el-input :model-value="selectedRoomText" disabled placeholder="请选择房间" />
            <el-button @click="roomDialogVisible = true">选择房间</el-button>
          </div>
        </el-form-item>
        <el-form-item label="展示标题">
          <el-input v-model="form.title" placeholder="如 802 精装办公空间" />
        </el-form-item>
        <el-form-item label="副标题">
          <el-input v-model="form.subtitle" placeholder="适合研发办公、成长型团队" />
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="form.tags" placeholder="采光好、可注册、近电梯" />
        </el-form-item>
        <el-form-item label="亮点">
          <el-input v-model="form.highlights" type="textarea" :rows="3" placeholder="每行一个亮点，或用逗号分隔" />
        </el-form-item>
        <el-form-item label="房源介绍">
          <el-input v-model="form.description" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item label="租金说明">
          <el-input v-model="form.rentDesc" />
        </el-form-item>
        <el-form-item label="物业费说明">
          <el-input v-model="form.propertyFeeDesc" />
        </el-form-item>
        <el-form-item label="发布状态">
          <el-select v-model="form.publishStatus">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已发布（请使用列表上架操作）" value="PUBLISHED" disabled />
            <el-option label="已下架" value="OFFLINE" />
          </el-select>
        </el-form-item>
        <el-form-item label="推荐/排序">
          <el-switch v-model="form.recommended" active-text="推荐" />
          <el-switch v-model="form.reservable" active-text="允许预约" />
          <el-input-number v-model="form.sortOrder" :min="0" class="sort-input" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="生效时间">
              <el-date-picker v-model="form.effectiveAt" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="失效时间">
              <el-date-picker v-model="form.expiresAt" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="房源图片">
          <div class="pending-upload">
            <input
              ref="pendingImageInputRef"
              class="hidden-file-input"
              type="file"
              accept="image/jpeg,image/png,image/webp"
              multiple
              @change="selectPendingImages"
            />
            <div class="pending-actions">
              <el-button v-if="hasPermission('investment-listing:image')" @click="pendingImageInputRef?.click()">上传封面/展示图</el-button>
              <span class="sub-text">支持 JPG、PNG、WebP，单张不超过20MB；第一张自动设为封面，其余作为展示图。</span>
            </div>
            <div v-if="pendingImages.length" class="pending-image-grid">
              <div v-for="(image, index) in pendingImages" :key="image.key" class="pending-image-card">
                <img :src="image.url" :alt="image.name" @click="previewImage(image.url)" />
                <div class="pending-image-meta">
                  <el-tag v-if="index === 0" size="small" type="success">封面</el-tag>
                  <el-tag v-else size="small" type="info">展示图</el-tag>
                  <el-button v-if="hasPermission('investment-listing:image')" size="small" text type="danger" @click="removePendingImage(index)">移除</el-button>
                </div>
              </div>
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button
          v-if="form.id ? hasPermission('investment-listing:update') : hasPermission('investment-listing:create')"
          type="primary"
          @click="save"
        >保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="roomDialogVisible" title="选择房间" width="900px">
      <el-form :inline="true" class="query-form">
        <el-form-item label="关键词">
          <el-input v-model="roomQuery.keyword" clearable placeholder="房号/楼宇/楼层" @keyup.enter="loadRooms" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="roomQuery.status" clearable placeholder="全部">
            <el-option label="空置" value="VACANT" />
            <el-option label="可租" value="AVAILABLE" />
            <el-option label="已出租" value="RENTED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadRooms">查询</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="rooms" border height="420" @row-dblclick="selectRoom">
        <el-table-column prop="buildingName" label="楼宇" min-width="140" />
        <el-table-column prop="floorName" label="楼层" width="110" />
        <el-table-column prop="roomNumber" label="房号" width="120" />
        <el-table-column prop="area" label="面积" width="100" />
        <el-table-column label="类型" width="110">
          <template #default="{ row }">{{ roomTypeText(row.roomType) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">{{ roomStatusText(row.status) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="selectRoom(row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="imageDialogVisible" title="图片管理" width="760px">
      <div v-if="currentListing" class="image-header">
        <strong>{{ currentListing.title || fallbackTitle(currentListing) }}</strong>
        <el-upload
          v-if="hasPermission('investment-listing:image')"
          accept="image/jpeg,image/png,image/webp"
          :show-file-list="false"
          :before-upload="validateListingImage"
          :http-request="uploadImage"
        >
          <el-button v-if="hasPermission('investment-listing:image')" type="primary">上传图片</el-button>
        </el-upload>
      </div>
      <div class="image-grid">
        <div v-for="image in images" :key="image.id" class="image-card">
          <img :src="image.imageUrl" alt="listing" @click="previewImage(image.imageUrl)" />
          <div class="image-actions">
            <el-tag v-if="image.imageType === 'COVER'" size="small" type="success">封面</el-tag>
            <el-button v-if="hasPermission('investment-listing:image') && image.imageType !== 'COVER'" size="small" @click="setCover(image)">设为封面</el-button>
            <el-button v-if="hasPermission('investment-listing:image')" size="small" type="danger" @click="deleteImage(image)">删除</el-button>
          </div>
        </div>
      </div>
      <el-empty v-if="!images.length" description="暂无图片" />
    </el-dialog>

    <el-dialog v-model="previewDialogVisible" title="招商房源预览" width="760px">
      <div v-if="previewListing" class="listing-preview">
        <div class="preview-cover">
          <img v-if="previewListing.coverImageUrl" :src="previewListing.coverImageUrl" alt="cover" @click="previewImage(previewListing.coverImageUrl)" />
          <span v-else>德汇创新中心</span>
        </div>
        <div class="preview-title">{{ previewListing.title || fallbackTitle(previewListing) }}</div>
        <div class="preview-subtitle">{{ previewListing.subtitle || '暂无副标题' }}</div>
        <div class="preview-meta">
          <el-tag>{{ previewListing.buildingName || '-' }}</el-tag>
          <el-tag>{{ previewListing.floorName || '-' }}</el-tag>
          <el-tag>{{ previewListing.roomNumber || '-' }}</el-tag>
          <el-tag>{{ previewListing.area || '-' }}㎡</el-tag>
          <el-tag>{{ roomTypeText(previewListing.roomType) }}</el-tag>
        </div>
        <p class="preview-description">{{ previewListing.description || '暂无房源介绍' }}</p>
        <div v-if="previewListing.images?.length" class="preview-images">
          <img v-for="image in previewListing.images" :key="image.id" :src="image.imageUrl" alt="preview" @click="previewImage(image.imageUrl)" />
        </div>
      </div>
    </el-dialog>

    <el-dialog v-model="imagePreviewVisible" title="图片预览" width="820px">
      <img v-if="previewImageUrl" class="image-preview-large" :src="previewImageUrl" alt="preview" />
    </el-dialog>
  </main>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { Plus, Refresh } from '@element-plus/icons-vue'
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
  investmentListingStatusOptions,
  investmentListingWorkspaceConfig,
  investmentRoomStatusOptions,
  investmentRoomTypeOptions
} from '../../workspaceConfigs/investmentWorkspaceConfig'
import { workspaceTableOptions } from '../../workspaceConfigs/workspaceConfig'

const props = defineProps({
  embedded: { type: Boolean, default: false }
})

const loading = ref(false)
const loadError = ref('')
const listings = ref([])
const rooms = ref([])
const images = ref([])
const dialogVisible = ref(false)
const roomDialogVisible = ref(false)
const imageDialogVisible = ref(false)
const previewDialogVisible = ref(false)
const imagePreviewVisible = ref(false)
const pendingImageInputRef = ref(null)
const currentListing = ref(null)
const previewListing = ref(null)
const previewImageUrl = ref('')
const pendingImages = ref([])
const MAX_LISTING_IMAGE_SIZE = 20 * 1024 * 1024
const LISTING_IMAGE_TYPES = /^image\/(jpeg|png|webp)$/i

const roomQuery = reactive({ keyword: '', status: 'VACANT' })
const pagination = reactive(createPagination(20))
const summary = reactive(emptySummary())
const form = reactive(initialForm())
const listingPermissions = investmentListingWorkspaceConfig.permissions
const summaryRequestGuard = createLatestRequestGuard()
const listingOptionLabels = new Map([
  ...investmentListingStatusOptions,
  ...investmentRoomStatusOptions,
  ...investmentRoomTypeOptions
].map(option => [option.value, option.label]))

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
  debouncedQueryTask: debouncedListingQuery,
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
} = useDhicTableView(workspaceTableOptions(investmentListingWorkspaceConfig, {
  records: () => listings.value,
  serverTotal: () => pagination.total,
  queryLifecycle: {
    task: () => {
      resetToFirstPage(pagination)
      loadWorkspaceData()
    }
  },
  groupLabel: value => listingOptionLabels.get(value) || value || '未填写'
}))

watch(() => JSON.stringify(serverQueryParams.value), () => debouncedListingQuery.schedule())

const selectedRoomText = computed(() => {
  if (!form.roomId) return ''
  const asset = [form.buildingName, form.floorName, form.roomNumber].filter(Boolean).join(' / ')
  const detail = [form.area ? `${form.area}㎡` : '', roomTypeText(form.roomType), roomStatusText(form.status)]
    .filter(value => value && value !== '-')
    .join(' · ')
  return [asset, detail].filter(Boolean).join(' ｜ ')
})

function initialForm() {
  return {
    id: null,
    roomId: null,
    buildingName: '',
    floorName: '',
    roomNumber: '',
    area: null,
    roomType: '',
    status: '',
    title: '',
    subtitle: '',
    description: '',
    highlights: '',
    tags: '',
    rentDesc: '',
    propertyFeeDesc: '',
    publishStatus: 'DRAFT',
    recommended: false,
    reservable: true,
    effectiveAt: '',
    expiresAt: '',
    sortOrder: 0
  }
}

function resetForm() {
  Object.assign(form, initialForm())
  clearPendingImages()
}

function emptySummary() {
  return { totalCount: 0, publishedCount: 0, officeCount: 0, recommendedCount: 0, draftCount: 0 }
}

function summaryFilterParams() {
  const params = serverQueryParams.value
  return {
    keyword: params.keyword,
    publishStatus: params.publishStatus,
    buildingId: params.buildingId,
    floorId: params.floorId
  }
}

function requestErrorMessage(error, fallback) {
  return error?.response?.data?.message || error?.message || error?.msg || fallback
}

function fallbackTitle(row) {
  return [row.floorName, row.roomNumber].filter(Boolean).join(' ') || '未命名房源'
}

function roomTypeText(value) {
  const map = { OFFICE: '办公', WAREHOUSE: '仓储', PUBLIC_AREA: '公共区域', SHOP: '商业', OTHER: '其他' }
  return map[value] || value || '-'
}

function roomStatusText(value) {
  const map = { VACANT: '空置', AVAILABLE: '可租', RENTED: '已出租', RESERVED: '预留', DISABLED: '停用' }
  return map[value] || value || '-'
}

async function loadListings() {
  if (!hasPermission(listingPermissions.view)) {
    listings.value = []
    pagination.total = 0
    return
  }
  const requestId = listRequestGuard.start()
  loading.value = true
  loadError.value = ''
  try {
    const data = await request.get('/investment/listings', {
      params: { ...serverQueryParams.value, ...pageParams(pagination) },
      silent: true
    })
    if (!listRequestGuard.isCurrent(requestId)) return
    const page = readPage(data)
    listings.value = page.records
    pagination.total = page.total
  } catch (error) {
    if (!listRequestGuard.isCurrent(requestId)) return
    listings.value = []
    pagination.total = 0
    loadError.value = requestErrorMessage(error, '招商房源加载失败')
  } finally {
    if (listRequestGuard.isCurrent(requestId)) loading.value = false
  }
}

async function loadSummary() {
  if (!hasPermission(listingPermissions.view)) {
    Object.assign(summary, emptySummary())
    return
  }
  const requestId = summaryRequestGuard.start()
  try {
    const data = await request.get('/investment/listings/summary', {
      params: summaryFilterParams(),
      silent: true
    })
    if (summaryRequestGuard.isCurrent(requestId)) Object.assign(summary, emptySummary(), data || {})
  } catch {
    if (summaryRequestGuard.isCurrent(requestId)) Object.assign(summary, emptySummary())
  }
}

async function loadWorkspaceData() {
  await Promise.all([loadListings(), loadSummary()])
}

async function handleSizeChange() {
  resetToFirstPage(pagination)
  await loadListings()
}

async function switchListingView(viewId) {
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

function saveListingView() {
  saveCurrentView()
  ElMessage.success(`视图“${activeViewName.value}”已保存`)
}

function openCreate() {
  resetForm()
  dialogVisible.value = true
  loadRooms()
}

function openEdit(row) {
  clearPendingImages()
  Object.assign(form, {
    ...initialForm(),
    ...row,
    recommended: Number(row.recommended) === 1 || row.recommended === true,
    reservable: Number(row.reservable) === 1 || row.reservable === true
  })
  dialogVisible.value = true
}

async function save() {
  if (!form.roomId) {
    ElMessage.warning('请选择关联房间')
    return
  }
  const payload = {
    ...form,
    effectiveAt: form.effectiveAt || null,
    expiresAt: form.expiresAt || null
  }
  let savedId = form.id
  if (form.id) {
    await request.put(`/investment/listings/${form.id}`, payload)
  } else {
    const createdId = await request.post('/investment/listings', payload)
    savedId = Number(createdId?.id || createdId)
  }
  await uploadPendingImages(savedId)
  ElMessage.success('已保存')
  dialogVisible.value = false
  await loadWorkspaceData()
}

async function loadRooms() {
  const data = await request.get('/investment/rooms/candidates', {
    params: {
      page: 1,
      pageSize: 50,
      keyword: roomQuery.keyword || undefined,
      status: roomQuery.status || undefined
    }
  })
  rooms.value = readPage(data).records
}

function selectRoom(row) {
  form.roomId = row.id
  form.buildingName = row.buildingName
  form.floorName = row.floorName
  form.roomNumber = row.roomNumber
  form.area = row.area
  form.roomType = row.roomType
  form.status = row.status
  if (!form.title) form.title = `${row.roomNumber || ''} 招商房源`.trim()
  roomDialogVisible.value = false
}

async function publish(row) {
  try {
    await request.post(`/investment/listings/${row.id}/publish`)
    ElMessage.success('已上架')
    await loadWorkspaceData()
  } catch (error) {
    ElMessage.error(requestErrorMessage(error, '房源上架失败'))
  }
}

async function offline(row) {
  await request.post(`/investment/listings/${row.id}/offline`)
  ElMessage.success('已下架')
  await loadWorkspaceData()
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除招商房源配置 ${row.title || fallbackTitle(row)}？房间台账不会被删除。`, '提示', { type: 'warning' })
  await request.delete(`/investment/listings/${row.id}`)
  ElMessage.success('已删除')
  await loadWorkspaceData()
}

async function openImages(row) {
  currentListing.value = row
  imageDialogVisible.value = true
  await loadImages()
}

async function openPreview(row) {
  previewListing.value = await request.get(`/investment/listings/${row.id}`)
  previewDialogVisible.value = true
}

function previewImage(url) {
  previewImageUrl.value = url
  imagePreviewVisible.value = true
}

async function loadImages() {
  if (!currentListing.value) return
  images.value = await request.get(`/investment/listings/${currentListing.value.id}/images`)
}

async function uploadImage(options) {
  const formData = new FormData()
  formData.append('file', options.file)
  formData.append('imageType', images.value.length ? 'GALLERY' : 'COVER')
  await request.post(`/investment/listings/${currentListing.value.id}/images`, formData)
  ElMessage.success('上传成功')
  await loadImages()
  await loadWorkspaceData()
}

function selectPendingImages(event) {
  const files = Array.from(event.target.files || [])
  const accepted = files.filter(file => LISTING_IMAGE_TYPES.test(file.type)
    && file.size <= MAX_LISTING_IMAGE_SIZE)
  if (files.some(file => !LISTING_IMAGE_TYPES.test(file.type))) {
    ElMessage.warning('仅支持 jpg、png、webp 图片')
  }
  if (files.some(file => file.size > MAX_LISTING_IMAGE_SIZE)) {
    ElMessage.warning('单张图片不能超过20MB')
  }
  pendingImages.value = pendingImages.value.concat(accepted.map(file => ({
    key: `${file.name}-${file.size}-${file.lastModified}-${Math.random()}`,
    name: file.name,
    file,
    url: URL.createObjectURL(file)
  })))
  event.target.value = ''
}

function validateListingImage(file) {
  if (!LISTING_IMAGE_TYPES.test(file.type)) {
    ElMessage.warning('仅支持 jpg、png、webp 图片')
    return false
  }
  if (file.size > MAX_LISTING_IMAGE_SIZE) {
    ElMessage.warning('单张图片不能超过20MB')
    return false
  }
  return true
}

function removePendingImage(index) {
  const [removed] = pendingImages.value.splice(index, 1)
  if (removed?.url) URL.revokeObjectURL(removed.url)
}

function clearPendingImages() {
  pendingImages.value.forEach(image => {
    if (image.url) URL.revokeObjectURL(image.url)
  })
  pendingImages.value = []
  if (pendingImageInputRef.value) pendingImageInputRef.value.value = ''
}

async function uploadPendingImages(savedId) {
  if (!savedId || !pendingImages.value.length) return
  for (let index = 0; index < pendingImages.value.length; index += 1) {
    const image = pendingImages.value[index]
    const formData = new FormData()
    formData.append('file', image.file)
    formData.append('imageType', index === 0 ? 'COVER' : 'GALLERY')
    formData.append('sortOrder', index)
    await request.post(`/investment/listings/${savedId}/images`, formData)
  }
  clearPendingImages()
}

async function setCover(image) {
  await request.post(`/investment/listings/images/${image.id}/cover`)
  ElMessage.success('已设置封面')
  await loadImages()
  await loadWorkspaceData()
}

async function deleteImage(image) {
  await ElMessageBox.confirm('确认删除这张图片？', '提示', { type: 'warning' })
  await request.delete(`/investment/listings/images/${image.id}`)
  ElMessage.success('已删除')
  await loadImages()
  await loadWorkspaceData()
}

onMounted(loadWorkspaceData)
onBeforeUnmount(() => {
  disposeQueryLifecycle()
  summaryRequestGuard.dispose()
  clearPendingImages()
})
</script>

<style scoped>
.page-container {
  padding: 24px;
}

.page-container--embedded {
  padding: 14px 0 0;
}

.view-hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  white-space: nowrap;
}

.listing-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.page-header h2 {
  margin: 0;
  font-size: 18px;
}

.page-header p,
.sub-text,
.summary-card small {
  margin: 6px 0 0;
  color: #8a93a3;
  font-size: 12px;
}

.query-form {
  margin-bottom: 16px;
}

.listing-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.summary-card {
  border: 1px solid #e6eaf2;
  border-left: 4px solid #2f6fed;
  border-radius: 6px;
  padding: 14px 16px;
  background: #fff;
}

.summary-card.success {
  border-left-color: #16a66a;
}

.summary-card.warning {
  border-left-color: #d97706;
}

.summary-card span {
  display: block;
  color: #697386;
  font-size: 12px;
}

.summary-card strong {
  display: block;
  margin-top: 4px;
  font-size: 24px;
  color: #1f2937;
}

.cover-box {
  width: 112px;
  aspect-ratio: 16 / 9;
  border-radius: 6px;
  overflow: hidden;
  background: #f3f5f8;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #9aa3b2;
  font-size: 12px;
}

.cover-box img,
.image-card img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.listing-title {
  font-weight: 700;
  color: #1f2937;
}

.tag-line {
  display: flex;
  gap: 6px;
  margin-top: 8px;
}

.pagination {
  justify-content: flex-end;
  margin-top: 16px;
}

.room-picker,
.image-header {
  display: flex;
  gap: 12px;
  align-items: center;
}

.sort-input {
  margin-left: 18px;
}

.pending-upload {
  width: 100%;
}

.hidden-file-input {
  display: none;
}

.pending-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.pending-image-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 12px;
}

.pending-image-card {
  border: 1px solid #e6eaf2;
  border-radius: 6px;
  overflow: hidden;
  background: #fff;
}

.pending-image-card img {
  width: 100%;
  aspect-ratio: 16 / 9;
  display: block;
  object-fit: cover;
  cursor: zoom-in;
}

.pending-image-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px;
}

.image-header {
  justify-content: space-between;
  margin-bottom: 16px;
}

.image-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.image-card {
  border: 1px solid #e6eaf2;
  border-radius: 6px;
  overflow: hidden;
  background: #fff;
}

.image-card img {
  aspect-ratio: 16 / 9;
  display: block;
}

.image-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px;
}

.listing-preview {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.preview-cover {
  aspect-ratio: 16 / 9;
  border-radius: 8px;
  overflow: hidden;
  background: linear-gradient(135deg, #f8d3d0, #f4f7fb);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #b42318;
  font-weight: 700;
}

.preview-cover img,
.preview-images img,
.image-preview-large {
  width: 100%;
  display: block;
  object-fit: cover;
}

.preview-cover img {
  height: 100%;
  cursor: zoom-in;
}

.preview-title {
  font-size: 20px;
  font-weight: 700;
  color: #111827;
}

.preview-subtitle,
.preview-description {
  color: #6b7280;
  line-height: 1.7;
}

.preview-meta,
.preview-images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.preview-images img {
  width: 150px;
  aspect-ratio: 16 / 9;
  border-radius: 6px;
  cursor: zoom-in;
}

.image-preview-large {
  max-height: 70vh;
  border-radius: 8px;
}
</style>
