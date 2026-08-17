<template>
  <main class="page-container">
    <DataWorkspace
      :workspace-config="announcementWorkspaceConfig"
      :views="workspaceViews"
      :active-view-id="activeViewId"
      :view-dirty="isViewDirty"
      :filter-logic="tablePrefs.filterLogic"
      :filter-logic-visible="tablePrefs.filters.length > 1"
      :filter-items="activeFilterItems"
      :query-notices="queryNotices"
      :metric-data="announcementMetricData"
      @change-view="switchAnnouncementView"
      @create-view="createTableView"
      @duplicate-view="duplicateTableView"
      @rename-view="renameTableView"
      @delete-view="deleteTableView"
      @save-view="saveAnnouncementView"
      @remove-filter="removeViewFilter"
    >
      <template #header-actions>
        <el-button v-if="hasPermission('announcement:create')" type="primary" :icon="Plus" @click="openDialog">
          {{ announcementWorkspaceConfig.primaryAction.label }}
        </el-button>
      </template>

      <template #toolbar>
        <DhicDataToolbar
          :workspace-config="announcementWorkspaceConfig"
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
          @save-field-config="saveAnnouncementView"
        >
          <template #right>
            <el-button :icon="Refresh" @click="loadAnnouncements">刷新</el-button>
            <span class="view-hint">已加载 {{ resultScope.loadedRecords }} 条，当前视图 {{ resultScope.visibleRecords }} 条</span>
          </template>
        </DhicDataToolbar>
      </template>

      <template #grid>
        <WorkspaceDataGrid
          :loading="loading"
          :error-text="listErrorText"
          retryable
          :rows="tableRows"
          :columns="visibleOrderedColumns"
          row-key="id"
          :density="tablePrefs.rowDensity"
          :empty-text="tableEmptyText"
          :sort-state="activeTableSort"
          :data-mode="announcementWorkspaceConfig.query.dataMode"
          :column-width="columnWidth"
          :column-fixed="columnFixed"
          :is-column-sortable="isColumnSortable"
          :show-overflow-tooltip="column => column.key === 'content'"
          :operation-column="{ width: 236, fixed: 'right', align: 'left' }"
          @column-resize="handleHeaderDragEnd"
          @sort-change="handleTableSortChange"
          @retry="loadAnnouncements"
          aria-label="公告管理数据表格"
        >
          <template #cell-showOnHome="{ row }">
            <el-tag :type="isHomeCarousel(row.showOnHome) ? 'success' : 'info'" size="small" aria-label="首页轮播">
              {{ isHomeCarousel(row.showOnHome) ? '是' : '否' }}
            </el-tag>
          </template>
          <template #operations="{ row }">
            <div class="row-actions">
              <el-button link @click="openPreview(row)">预览</el-button>
              <el-button v-if="hasPermission('announcement:update')" link type="primary" @click="openDialog(row)">编辑</el-button>
              <el-button
                v-if="hasPermission('announcement:update') && row.status === 'DRAFT'"
                link
                type="success"
                @click="publish(row)"
              >发布</el-button>
              <el-button
                v-if="hasPermission('announcement:update') && row.status === 'PUBLISHED'"
                link
                type="warning"
                @click="disable(row)"
              >停用</el-button>
              <el-button v-if="hasPermission('announcement:delete')" link type="danger" @click="deleteAnnouncement(row)">删除</el-button>
            </div>
          </template>
        </WorkspaceDataGrid>
      </template>
    </DataWorkspace>

    <el-dialog v-model="visible" :title="form.id ? '编辑公告' : '新增公告'" width="680px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" />
        </el-form-item>

        <el-form-item label="摘要">
          <el-input v-model="form.summary" maxlength="500" show-word-limit />
        </el-form-item>

        <el-form-item label="类型" prop="type">
          <el-select v-model="form.type" style="width: 100%">
            <el-option v-for="item in announcementTypeOptions" :key="item.value" :label="`${item.label} ${item.value}`" :value="item.value" />
          </el-select>
        </el-form-item>

        <el-form-item label="内容" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="6" />
        </el-form-item>

        <el-form-item label="封面图">
          <div class="image-field">
            <el-input v-model="form.coverImageUrl" placeholder="上传图片后自动回填，也可粘贴已有图片地址" />
            <el-upload
              v-if="hasPermission('announcement:update') || hasPermission('announcement:create')"
              :show-file-list="false"
              accept="image/jpeg,image/png,image/gif,image/webp,image/bmp"
              :http-request="uploadAnnouncementImage"
            >
              <el-button>上传图片</el-button>
            </el-upload>
            <img v-if="form.coverImageUrl" class="form-image-preview" :src="form.coverImageUrl" alt="公告封面预览" />
          </div>
        </el-form-item>

        <el-form-item label="首页轮播">
          <el-switch
            v-model="form.showOnHome"
            active-text="进入首页轮播"
            inactive-text="仅公告通知"
          />
          <el-input-number v-if="form.showOnHome" v-model="form.carouselSort" :min="0" :max="999" class="carousel-sort" />
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
      </el-form>

      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button v-if="form.id ? hasPermission('announcement:update') : hasPermission('announcement:create')" type="primary" :loading="saving" @click="save">
          {{ form.id ? '保存修改' : '保存为草稿' }}
        </el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="previewVisible" title="公告预览" size="min(520px, 92vw)">
      <article v-if="previewRow" class="announcement-preview">
        <img v-if="previewRow.coverImageUrl" class="preview-cover" :src="previewRow.coverImageUrl" alt="公告封面" />
        <div class="preview-meta">
          <el-tag size="small">{{ announcementTypeText(previewRow.type) }}</el-tag>
          <el-tag :type="previewRow.status === 'PUBLISHED' ? 'success' : 'info'" size="small">
            {{ announcementStatusText(previewRow.status) }}
          </el-tag>
          <span>{{ previewRow.publishTime || previewRow.createdTime || '' }}</span>
        </div>
        <h2>{{ previewRow.title }}</h2>
        <p v-if="previewRow.summary" class="preview-summary">{{ previewRow.summary }}</p>
        <p>{{ previewRow.content || '暂无正文' }}</p>
      </article>
    </el-drawer>
  </main>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { uploadCustomFieldAttachment } from '../../api/customFields'
import DataWorkspace from '../../components/DataWorkspace.vue'
import DhicDataToolbar from '../../components/DhicDataToolbar.vue'
import WorkspaceDataGrid from '../../components/workspace/WorkspaceDataGrid.vue'
import { useDhicTableView } from '../../composables/useDhicTableView'
import { useWorkspaceListRequestState } from '../../composables/workspace/useWorkspaceRequestState'
import request from '../../utils/request'
import { readPage } from '../../utils/pagination'
import { hasPermission } from '../../utils/permission'
import {
  announcementStatusOptions,
  announcementTypeOptions,
  announcementWorkspaceConfig,
  buildAnnouncementMetricSnapshot,
  normalizeAnnouncementWorkspaceRecord
} from '../../workspaceConfigs/operationWorkspaceConfig'
import { workspaceTableOptions } from '../../workspaceConfigs/workspaceConfig'

const list = ref([])
const saving = ref(false)
const visible = ref(false)
const previewVisible = ref(false)
const previewRow = ref(null)
const formRef = ref(null)
const listState = useWorkspaceListRequestState({
  hasResults: () => list.value.length > 0,
  clearResults: () => {
    list.value = []
  },
  staleMessage: '公告列表刷新失败，当前显示上次成功数据'
})
const loading = computed(() => listState.status.value === 'loading')
const listErrorText = listState.errorText

const {
  workspaceViews,
  activeViewId,
  activeViewName,
  isViewDirty,
  tablePrefs,
  orderedColumns,
  visibleOrderedColumns,
  visibleRecords,
  tableRows,
  tableEmptyText,
  sortOptions,
  groupOptions,
  filterOptions,
  filterLogicOptions,
  queryNotices,
  queryRequestGuard: listRequestGuard,
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
} = useDhicTableView(workspaceTableOptions(announcementWorkspaceConfig, {
  records: () => list.value,
  serverTotal: () => list.value.length,
  groupLabel: value => announcementStatusOptions.find(option => option.value === value)?.label
    || announcementTypeOptions.find(option => option.value === value)?.label
    || (typeof value === 'boolean' ? (value ? '是' : '否') : value)
    || '未填写'
}))

const announcementMetricData = computed(() => buildAnnouncementMetricSnapshot(list.value, visibleRecords.value))

const defaultForm = () => ({
  id: null,
  title: '',
  summary: '',
  type: 'NOTICE',
  content: '',
  coverImageUrl: '',
  showOnHome: false,
  carouselSort: 0,
  effectiveAt: '',
  expiresAt: '',
  status: 'DRAFT'
})

const form = reactive(defaultForm())

const rules = {
  title: [{ required: true, message: '请输入公告标题', trigger: 'blur' }],
  type: [{ required: true, message: '请选择公告类型', trigger: 'change' }],
  content: [{ required: true, message: '请输入公告内容', trigger: 'blur' }]
}

async function uploadAnnouncementImage(options) {
  try {
    const result = await uploadCustomFieldAttachment(options.file, {
      fieldType: 'IMAGE',
      workspaceKey: 'announcements'
    })
    form.coverImageUrl = result?.url || ''
    options.onSuccess?.(result)
    ElMessage.success('公告封面上传成功')
  } catch (error) {
    options.onError?.(error)
    ElMessage.error(error?.response?.data?.message || '公告封面上传失败')
  }
}

function resetForm() {
  Object.assign(form, defaultForm())
  formRef.value?.clearValidate()
}

async function switchAnnouncementView(viewId) {
  if (viewId === activeViewId.value) return
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

function saveAnnouncementView() {
  saveCurrentView()
  ElMessage.success(`视图“${activeViewName.value}”已保存`)
}

async function loadAnnouncements() {
  const requestId = listRequestGuard.start()
  const requestContext = listState.begin({ resource: '/announcements' })
  try {
    const data = await request.get('/announcements', { silent: true })
    if (!listRequestGuard.isCurrent(requestId) || !listState.isCurrent(requestContext)) return
    list.value = readPage(data).records.map(normalizeAnnouncementWorkspaceRecord)
    listState.succeed(requestContext)
  } catch (error) {
    if (!listRequestGuard.isCurrent(requestId) || !listState.isCurrent(requestContext)) return
    listState.fail(requestContext, error?.response?.data?.message || '公告加载失败，请稍后重试')
  }
}

function openDialog(row) {
  resetForm()
  if (row?.id) {
    Object.assign(form, {
      id: row.id,
      title: row.title || '',
      summary: row.summary || '',
      type: row.type || 'NOTICE',
      content: row.content || '',
      coverImageUrl: row.coverImageUrl || '',
      showOnHome: isHomeCarousel(row.showOnHome),
      carouselSort: Number(row.carouselSort || 0),
      effectiveAt: row.effectiveAt || '',
      expiresAt: row.expiresAt || '',
      status: row.status || 'DRAFT'
    })
  }
  visible.value = true
}

async function save() {
  await formRef.value.validate()
  saving.value = true
  try {
    const payload = {
      title: form.title,
      summary: form.summary,
      type: form.type,
      content: form.content,
      coverImageUrl: form.coverImageUrl,
      showOnHome: form.showOnHome,
      carouselSort: form.carouselSort,
      effectiveAt: form.effectiveAt || null,
      expiresAt: form.expiresAt || null,
      status: form.status || 'DRAFT'
    }
    if (form.id) {
      await request.put(`/announcements/${form.id}`, payload)
      ElMessage.success('公告修改成功')
    } else {
      await request.post('/announcements', payload)
      ElMessage.success('公告草稿创建成功')
    }
    visible.value = false
    resetForm()
    await loadAnnouncements()
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '公告保存失败')
  } finally {
    saving.value = false
  }
}

function isHomeCarousel(value) {
  return value === true || value === 1 || value === '1' || value === 'true'
}

async function publish(row) {
  try {
    await ElMessageBox.confirm(`确定发布公告「${row.title}」吗？`, '发布确认', { type: 'warning' })
    await request.post(`/announcements/${row.id}/publish`)
    ElMessage.success('公告发布成功')
    await loadAnnouncements()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error?.response?.data?.message || '公告发布失败')
  }
}

async function disable(row) {
  try {
    await ElMessageBox.confirm(`确定停用公告「${row.title}」吗？停用后小程序将不再展示。`, '停用确认', { type: 'warning' })
    await request.post(`/announcements/${row.id}/disable`)
    ElMessage.success('公告已停用')
    await loadAnnouncements()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error?.response?.data?.message || '公告停用失败')
  }
}

function openPreview(row) {
  previewRow.value = { ...row }
  previewVisible.value = true
}

function announcementTypeText(type) {
  return announcementTypeOptions.find(option => option.value === type)?.label || type || '其他'
}

function announcementStatusText(status) {
  return announcementStatusOptions.find(option => option.value === status)?.label || status || '-'
}

async function deleteAnnouncement(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除公告「${row.title}」吗？删除后小程序端和后台列表都不会再显示。`,
      '删除确认',
      { type: 'warning' }
    )
    await request.delete(`/announcements/${row.id}`)
    ElMessage.success('公告删除成功')
    await loadAnnouncements()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error?.response?.data?.message || '公告删除失败')
  }
}

onMounted(loadAnnouncements)

onBeforeUnmount(() => {
  listRequestGuard.dispose()
  disposeQueryLifecycle()
})
</script>

<style scoped>
.image-field {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  width: 100%;
}

.form-image-preview {
  grid-column: 1 / -1;
  width: 180px;
  height: 108px;
  border-radius: 8px;
  object-fit: cover;
}

.preview-cover {
  width: 100%;
  max-height: 280px;
  margin-bottom: 16px;
  border-radius: 10px;
  object-fit: cover;
}

.page-container {
  min-width: 0;
}

.view-hint {
  color: var(--dhic-text-secondary);
  font-size: 13px;
}

.row-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.row-actions :deep(.el-button) {
  margin-left: 0;
}

.form-help {
  display: block;
  width: 100%;
  margin-top: 4px;
  color: var(--dhic-text-secondary);
  font-size: 12px;
}

.announcement-preview {
  color: var(--dhic-text-primary);
}

.announcement-preview h2 {
  margin: 18px 0 12px;
  font-size: 22px;
  line-height: 1.45;
  letter-spacing: 0;
}

.announcement-preview p {
  margin: 0;
  color: var(--dhic-text-regular);
  line-height: 1.8;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.carousel-sort { margin-left: 14px; width: 140px; }
.preview-summary { color: var(--el-text-color-secondary); font-weight: 600; }

.preview-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  color: var(--dhic-text-secondary);
  font-size: 12px;
}
</style>
