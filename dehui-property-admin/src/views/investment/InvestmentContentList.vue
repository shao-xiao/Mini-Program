<template>
  <div class="page-container" :class="{ 'page-container--embedded': props.embedded }">
    <div class="content-layout">
      <DataWorkspace
        class="content-workspace"
        :workspace-config="investmentContentWorkspaceConfig"
        :views="workspaceViews"
        :active-view-id="activeViewId"
        :view-dirty="isViewDirty"
        :filter-logic="tablePrefs.filterLogic"
        :filter-logic-visible="tablePrefs.filters.length > 1"
        :filter-items="activeFilterItems"
        :query-notices="queryNotices"
        @change-view="switchContentView"
        @create-view="createTableView"
        @duplicate-view="duplicateTableView"
        @rename-view="renameTableView"
        @delete-view="deleteTableView"
        @save-view="saveContentView"
        @remove-filter="removeViewFilter"
      >
        <template #header-actions>
          <el-button
            v-if="hasPermission('investment-content:create')"
            type="primary"
            :icon="Plus"
            @click="openDialog"
          >新增内容</el-button>
        </template>

        <template #toolbar>
          <DhicDataToolbar
            :workspace-config="investmentContentWorkspaceConfig"
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
            @save-field-config="saveContentView"
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
            :data-mode="investmentContentWorkspaceConfig.query.dataMode"
            :column-width="columnWidth"
            :column-fixed="columnFixed"
            :is-column-sortable="isColumnSortable"
            :show-overflow-tooltip="column => ['title', 'subtitle'].includes(column.key)"
            :operation-column="{ width: 286, fixed: 'right', align: 'left' }"
            @column-resize="handleHeaderDragEnd"
            @sort-change="handleTableSortChange"
            @retry="loadWorkspaceData"
            aria-label="招商内容数据表格"
          >
            <template #operations="{ row }">
              <div class="content-actions">
                <el-button v-if="hasPermission('investment-content:update')" link type="primary" @click="edit(row)">编辑</el-button>
                <el-button link @click="preview(row)">预览</el-button>
                <el-button
                  v-if="hasPermission('investment-content:publish') && (row.publishStatus || row.status) !== 'PUBLISHED'"
                  link
                  type="success"
                  @click="publish(row)"
                >发布</el-button>
                <el-button
                  v-else-if="hasPermission('investment-content:publish')"
                  link
                  type="warning"
                  @click="disable(row)"
                >停用</el-button>
                <el-button v-if="hasPermission('investment-content:delete')" link type="danger" @click="remove(row)">删除</el-button>
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
              @current-change="loadContent"
              @size-change="handleSizeChange"
            />
          </div>
        </template>
      </DataWorkspace>

      <el-card class="preview-card" shadow="never">
        <template #header>
          <div class="preview-header">
            <div>
              <div class="page-title">小程序预览</div>
              <div class="page-subtitle">草稿可在后台预览，正式小程序只展示已发布内容</div>
            </div>
            <el-button size="small" @click="loadPreview">刷新</el-button>
          </div>
        </template>

        <div class="phone-preview">
          <div class="phone-top">招商中心</div>
          <div class="phone-body">
            <section class="mini-hero">
              <template v-if="firstItem('hero')">
                <div class="mini-hero-title">{{ firstItem('hero').title }}</div>
                <div v-if="firstItem('hero').subtitle" class="mini-hero-subtitle">{{ firstItem('hero').subtitle }}</div>
                <div v-if="firstItem('hero').content" class="mini-hero-content">{{ firstItem('hero').content }}</div>
              </template>
              <template v-else>
                <div class="mini-hero-title">招商中心</div>
                <div class="mini-hero-subtitle">暂无顶部主视觉内容</div>
              </template>
            </section>

            <preview-section title="园区亮点" :items="previewGroups.highlight" />
            <preview-section title="招商政策" :items="previewGroups.policy" />
            <preview-section title="园区介绍" :items="previewGroups.introduction" single />
            <preview-section title="园区地址" :items="previewGroups.location" single />
            <preview-section title="联系方式" :items="previewGroups.contact" single />
          </div>
        </div>
      </el-card>
    </div>

    <el-dialog v-model="visible" :title="form.id ? '编辑招商内容' : '新增招商内容'" width="720px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="展示区块" prop="sectionKey">
          <el-select v-model="form.sectionKey" style="width: 100%">
            <el-option v-for="item in sectionOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>

        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" />
        </el-form-item>

        <el-form-item label="副标题">
          <el-input v-model="form.subtitle" />
        </el-form-item>

        <el-form-item label="正文内容" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="5" />
        </el-form-item>

        <el-form-item label="展示图片">
          <div class="image-field">
            <el-input v-model="form.imageUrl" placeholder="上传图片后自动回填，也可粘贴已有图片地址" />
            <el-upload
              v-if="hasPermission('investment-content:update') || hasPermission('investment-content:create')"
              :show-file-list="false"
              accept="image/jpeg,image/png,image/gif,image/webp,image/bmp"
              :http-request="uploadShowcaseImage"
            >
              <el-button>上传图片</el-button>
            </el-upload>
            <img v-if="form.imageUrl" class="form-image-preview" :src="form.imageUrl" alt="招商展示图片预览" />
          </div>
        </el-form-item>

        <div class="form-grid">
          <el-form-item label="排序">
            <el-input-number v-model="form.sortOrder" :min="1" :max="999" style="width: 100%" />
          </el-form-item>
          <el-form-item label="状态" prop="status">
            <el-select v-model="form.status" style="width: 100%">
              <el-option label="草稿" value="DRAFT" />
              <el-option label="已发布" value="PUBLISHED" />
              <el-option label="停用" value="DISABLED" />
            </el-select>
          </el-form-item>
        </div>

        <div class="form-grid">
          <el-form-item label="生效时间">
            <el-date-picker
              v-model="form.effectiveAt"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="失效时间">
            <el-date-picker
              v-model="form.expiresAt"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
              style="width: 100%"
            />
          </el-form-item>
        </div>

        <el-form-item label="公开展示">
          <el-switch v-model="form.publicVisible" />
        </el-form-item>

        <el-form-item label="后台备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button
          v-if="form.id ? hasPermission('investment-content:update') : hasPermission('investment-content:create')"
          type="primary"
          :loading="saving"
          @click="save"
        >保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="previewVisible" title="内容预览" width="420px">
      <div v-if="previewRow" class="single-preview">
        <div class="single-section">{{ sectionName(previewRow.sectionKey) }}</div>
        <div class="single-title">{{ previewRow.title }}</div>
        <div v-if="previewRow.subtitle" class="single-subtitle">{{ previewRow.subtitle }}</div>
        <div class="single-content">{{ previewRow.content }}</div>
        <img v-if="previewRow.imageUrl" class="single-image" :src="previewRow.imageUrl" alt="" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, defineComponent, h, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { uploadCustomFieldAttachment } from '../../api/customFields'
import DataWorkspace from '../../components/DataWorkspace.vue'
import DhicDataToolbar from '../../components/DhicDataToolbar.vue'
import WorkspaceDataGrid from '../../components/workspace/WorkspaceDataGrid.vue'
import { useDhicTableView } from '../../composables/useDhicTableView'
import request from '../../utils/request'
import { createPagination, pageParams, readPage, resetToFirstPage } from '../../utils/pagination'
import { hasPermission } from '../../utils/permission'
import { createLatestRequestGuard } from '../../utils/requestLifecycle'
import {
  investmentContentStatusOptions,
  investmentContentWorkspaceConfig,
  investmentSectionOptions as sectionOptions
} from '../../workspaceConfigs/investmentWorkspaceConfig'
import { workspaceTableOptions } from '../../workspaceConfigs/workspaceConfig'

const props = defineProps({
  embedded: { type: Boolean, default: false }
})

const PreviewSection = defineComponent({
  name: 'PreviewSection',
  props: {
    title: { type: String, required: true },
    items: { type: Array, default: () => [] },
    single: { type: Boolean, default: false }
  },
  setup(props) {
    return () => h('section', { class: 'mini-section' }, [
      h('div', { class: 'mini-section-title' }, props.title),
      props.items.length
        ? h('div', { class: props.single ? 'mini-single-list' : 'mini-card-list' }, props.items.map(item =>
          h('div', { class: props.single ? 'mini-text-block' : 'mini-card-item', key: item.id || `${item.title}-${item.sortOrder}` }, [
            h('div', { class: 'mini-item-title' }, item.title || '-'),
            item.subtitle ? h('div', { class: 'mini-item-subtitle' }, item.subtitle) : null,
            item.content ? h('div', { class: 'mini-item-content' }, item.content) : null
          ])
        ))
        : h('div', { class: 'mini-empty' }, '暂无内容')
    ])
  }
})

const list = ref([])
const loading = ref(false)
const loadError = ref('')
const saving = ref(false)
const visible = ref(false)
const previewVisible = ref(false)
const previewRow = ref(null)
const pagination = reactive(createPagination(20))
const formRef = ref(null)
const previewGroups = reactive(createEmptyGroups())
const contentPermissions = investmentContentWorkspaceConfig.permissions

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
  debouncedQueryTask: debouncedContentQuery,
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
} = useDhicTableView(workspaceTableOptions(investmentContentWorkspaceConfig, {
  records: () => list.value,
  serverTotal: () => pagination.total,
  queryLifecycle: {
    task: () => {
      resetToFirstPage(pagination)
      loadWorkspaceData()
    }
  },
  groupLabel: value => (
    sectionOptions.find(option => option.value === value)?.label
    || investmentContentStatusOptions.find(option => option.value === value)?.label
    || value
    || '未填写'
  )
}))

watch(() => JSON.stringify(serverQueryParams.value), () => debouncedContentQuery.schedule())

const defaultForm = () => ({
  id: null,
  sectionKey: 'highlight',
  title: '',
  subtitle: '',
  content: '',
  imageUrl: '',
  publicVisible: true,
  effectiveAt: '',
  expiresAt: '',
  status: 'DRAFT',
  sortOrder: 10,
  remark: ''
})

const form = reactive(defaultForm())

const rules = {
  sectionKey: [{ required: true, message: '请选择展示区块', trigger: 'change' }],
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入正文内容', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

async function uploadShowcaseImage(options) {
  try {
    const result = await uploadCustomFieldAttachment(options.file, {
      fieldType: 'IMAGE',
      workspaceKey: 'investment-contents'
    })
    form.imageUrl = result?.url || ''
    options.onSuccess?.(result)
    ElMessage.success('展示图片上传成功')
  } catch (error) {
    options.onError?.(error)
    ElMessage.error(requestErrorMessage(error, '展示图片上传失败'))
  }
}

const sectionName = (key) => sectionOptions.find(item => item.value === key)?.label || key || '-'

const firstItem = (key) => previewGroups[key]?.[0] || null

function createEmptyGroups() {
  return sectionOptions.reduce((result, item) => {
    result[item.value] = []
    return result
  }, {})
}

function normalizeGroups(data) {
  const groups = createEmptyGroups()
  sectionOptions.forEach(item => {
    const rows = Array.isArray(data?.[item.value]) ? data[item.value] : []
    groups[item.value] = rows
      .map(row => ({
        ...row,
        sectionKey: row.sectionKey || item.value,
        sectionName: row.sectionName || item.label,
        sortOrder: Number(row.sortOrder || 0)
      }))
      .sort((a, b) => Number(a.sortOrder || 0) - Number(b.sortOrder || 0))
  })
  return groups
}

function applyGroups(groups) {
  sectionOptions.forEach(item => {
    previewGroups[item.value] = groups[item.value] || []
  })
}

const previewListGroups = computed(() => normalizeGroups(
  list.value.reduce((result, row) => {
    const key = row.sectionKey || 'highlight'
    result[key] = result[key] || []
    result[key].push(row)
    return result
  }, {})
))

const resetForm = () => {
  Object.assign(form, defaultForm())
  formRef.value?.clearValidate()
}

function requestErrorMessage(error, fallback) {
  return error?.response?.data?.message || error?.message || error?.msg || fallback
}

const loadContent = async () => {
  if (!hasPermission(contentPermissions.view)) {
    list.value = []
    pagination.total = 0
    return
  }
  const requestId = listRequestGuard.start()
  loading.value = true
  loadError.value = ''
  try {
    const data = await request.get('/investment/contents', {
      params: { ...serverQueryParams.value, ...pageParams(pagination) },
      silent: true
    })
    if (!listRequestGuard.isCurrent(requestId)) return
    const page = readPage(data)
    list.value = page.records.map(row => ({
      ...row,
      publishStatus: row.publishStatus || row.status
    }))
    pagination.total = page.total
    await loadPreview()
  } catch (error) {
    if (!listRequestGuard.isCurrent(requestId)) return
    list.value = []
    pagination.total = 0
    applyGroups(createEmptyGroups())
    loadError.value = requestErrorMessage(error, '招商内容加载失败')
  } finally {
    if (listRequestGuard.isCurrent(requestId)) loading.value = false
  }
}

const loadWorkspaceData = () => loadContent()

const loadPreview = async () => {
  try {
    const data = await request.get('/investment/contents/preview', { silent: true })
    applyGroups(normalizeGroups(data))
  } catch (error) {
    applyGroups(previewListGroups.value)
  }
}

const handleSizeChange = () => {
  resetToFirstPage(pagination)
  loadContent()
}

async function switchContentView(viewId) {
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

function saveContentView() {
  saveCurrentView()
  ElMessage.success(`视图“${activeViewName.value}”已保存`)
}

const openDialog = () => {
  resetForm()
  visible.value = true
}

const edit = (row) => {
  Object.assign(form, {
    id: row.id,
    sectionKey: row.sectionKey || 'highlight',
    title: row.title || '',
    subtitle: row.subtitle || '',
    content: row.content || '',
    imageUrl: row.imageUrl || '',
    publicVisible: row.publicVisible !== false && Number(row.publicVisible) !== 0,
    effectiveAt: row.effectiveAt || '',
    expiresAt: row.expiresAt || '',
    status: row.publishStatus || row.status || 'DRAFT',
    sortOrder: Number(row.sortOrder || 10),
    remark: row.remark || ''
  })
  formRef.value?.clearValidate()
  visible.value = true
}

const save = async () => {
  await formRef.value.validate()
  saving.value = true
  try {
    const payload = {
      sectionKey: form.sectionKey,
      title: form.title,
      subtitle: form.subtitle,
      content: form.content,
      imageUrl: form.imageUrl,
      publicVisible: form.publicVisible,
      effectiveAt: form.effectiveAt || null,
      expiresAt: form.expiresAt || null,
      sortOrder: form.sortOrder,
      publishStatus: form.status,
      remark: form.remark
    }

    if (form.id) {
      await request.put(`/investment/contents/${form.id}`, payload)
      ElMessage.success('招商内容已保存')
    } else {
      await request.post('/investment/contents', payload)
      ElMessage.success('招商内容已创建')
    }
    visible.value = false
    resetForm()
    await loadWorkspaceData()
  } finally {
    saving.value = false
  }
}

const publish = async (row) => {
  await ElMessageBox.confirm(`确定发布「${row.title}」吗？`, '发布确认', { type: 'warning' })
  await request.post(`/investment/contents/${row.id}/publish`)
  ElMessage.success('发布成功')
  await loadWorkspaceData()
}

const disable = async (row) => {
  await ElMessageBox.confirm(`确定停用「${row.title}」吗？停用后小程序端不再展示。`, '停用确认', { type: 'warning' })
  await request.post(`/investment/contents/${row.id}/disable`)
  ElMessage.success('已停用')
  await loadWorkspaceData()
}

const remove = async (row) => {
  await ElMessageBox.confirm(`确定删除「${row.title}」吗？`, '删除确认', { type: 'warning' })
  await request.delete(`/investment/contents/${row.id}`)
  ElMessage.success('已删除')
  await loadWorkspaceData()
}

const preview = (row) => {
  previewRow.value = {
    ...row,
    sectionKey: row.sectionKey || 'highlight',
    imageUrl: row.imageUrl || ''
  }
  previewVisible.value = true
}

onMounted(loadWorkspaceData)
onBeforeUnmount(() => disposeQueryLifecycle())
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

.page-container {
  padding: 20px;
}

.page-container--embedded {
  padding: 14px 0 0;
}

.content-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 18px;
  align-items: start;
}

.content-workspace {
  min-width: 0;
}

.view-hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  white-space: nowrap;
}

.content-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
}

.page-header,
.preview-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
}

.page-subtitle {
  margin-top: 6px;
  font-size: 13px;
  color: #909399;
}

.content-table {
  width: 100%;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.preview-card {
  position: sticky;
  top: 20px;
}

.phone-preview {
  width: 300px;
  margin: 0 auto;
  overflow: hidden;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  background: #f5f6f8;
}

.phone-top {
  height: 42px;
  line-height: 42px;
  text-align: center;
  font-weight: 600;
  background: #ffffff;
  border-bottom: 1px solid #ebeef5;
}

.phone-body {
  max-height: 680px;
  padding: 12px;
  overflow-y: auto;
}

.mini-hero {
  padding: 18px 16px;
  border-radius: 8px;
  background: #d93025;
  color: #ffffff;
}

.mini-hero-title {
  font-size: 20px;
  font-weight: 700;
}

.mini-hero-subtitle,
.mini-hero-content {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.5;
  color: rgba(255, 255, 255, 0.86);
}

.mini-section {
  margin-top: 12px;
  padding: 12px;
  border-radius: 8px;
  background: #ffffff;
}

.mini-section-title {
  margin-bottom: 10px;
  font-weight: 700;
  color: #1f1b1b;
}

.mini-card-list,
.mini-single-list {
  display: grid;
  gap: 8px;
}

.mini-card-list {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.mini-card-item,
.mini-text-block {
  padding: 10px;
  border-radius: 6px;
  background: #f5f6f8;
}

.mini-item-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}

.mini-item-subtitle,
.mini-item-content {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.5;
  color: #606266;
}

.mini-empty {
  padding: 10px;
  border-radius: 6px;
  background: #f5f6f8;
  color: #a8abb2;
  font-size: 12px;
}

.single-preview {
  padding: 4px 0;
}

.single-section {
  color: #d93025;
  font-size: 13px;
  font-weight: 600;
}

.single-title {
  margin-top: 12px;
  font-size: 20px;
  font-weight: 700;
  color: #1f1b1b;
}

.single-subtitle {
  margin-top: 8px;
  color: #606266;
}

.single-content {
  margin-top: 12px;
  white-space: pre-wrap;
  line-height: 1.7;
  color: #303133;
}

.single-image {
  width: 100%;
  margin-top: 12px;
  border-radius: 8px;
}

@media (max-width: 1180px) {
  .content-layout {
    grid-template-columns: 1fr;
  }

  .preview-card {
    position: static;
  }
}
</style>
