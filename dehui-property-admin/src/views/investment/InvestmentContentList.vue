<template>
  <div class="page-container">
    <div class="content-layout">
      <el-card class="list-card" shadow="never">
        <template #header>
          <div class="page-header">
            <div>
              <div class="page-title">招商内容</div>
              <div class="page-subtitle">按小程序招商中心展示区块维护内容、发布状态和排序</div>
            </div>
            <el-button type="primary" @click="openDialog">新增内容</el-button>
          </div>
        </template>

        <el-table v-loading="loading" :data="list" border stripe class="content-table">
          <el-table-column label="序号" width="80" align="center">
            <template #default="{ $index }">{{ sequence($index) }}</template>
          </el-table-column>
          <el-table-column label="展示区块" width="130">
            <template #default="{ row }">{{ sectionName(row.sectionKey) }}</template>
          </el-table-column>
          <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
          <el-table-column prop="subtitle" label="副标题" min-width="170" show-overflow-tooltip />
          <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="statusTag(row.publishStatus || row.status)">
                {{ statusText(row.publishStatus || row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="更新时间" width="160">
            <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="300" fixed="right">
            <template #default="{ row }">
              <el-button size="small" @click="edit(row)">编辑</el-button>
              <el-button size="small" @click="preview(row)">预览</el-button>
              <el-button
                v-if="(row.publishStatus || row.status) !== 'PUBLISHED'"
                size="small"
                type="success"
                @click="publish(row)"
              >
                发布
              </el-button>
              <el-button v-else size="small" type="warning" @click="disable(row)">停用</el-button>
              <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
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
            @current-change="load"
            @size-change="handleSizeChange"
          />
        </div>
      </el-card>

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
            <preview-section title="招商公告" :items="previewGroups.notice" />
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

        <el-form-item label="图片地址">
          <el-input v-model="form.imageUrl" placeholder="可选，后续可接文件上传" />
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

        <el-form-item label="后台备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
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
import { computed, defineComponent, h, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../utils/request'
import { createPagination, pageParams, readPage } from '../../utils/pagination'

const sectionOptions = [
  { label: '顶部主视觉', value: 'hero' },
  { label: '园区亮点', value: 'highlight' },
  { label: '招商政策', value: 'policy' },
  { label: '园区介绍', value: 'introduction' },
  { label: '园区地址', value: 'location' },
  { label: '联系方式', value: 'contact' },
  { label: '招商公告', value: 'notice' }
]

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
const saving = ref(false)
const visible = ref(false)
const previewVisible = ref(false)
const previewRow = ref(null)
const pagination = reactive(createPagination(20))
const formRef = ref(null)
const previewGroups = reactive(createEmptyGroups())

const defaultForm = () => ({
  id: null,
  sectionKey: 'highlight',
  title: '',
  subtitle: '',
  content: '',
  imageUrl: '',
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

const sectionName = (key) => sectionOptions.find(item => item.value === key)?.label || key || '-'

const statusText = (status) => ({
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  DISABLED: '停用'
}[status] || status || '-')

const statusTag = (status) => ({
  DRAFT: 'info',
  PUBLISHED: 'success',
  DISABLED: 'warning'
}[status] || 'info')

const firstItem = (key) => previewGroups[key]?.[0] || null

const sequence = (index) => (pagination.currentPage - 1) * pagination.pageSize + index + 1

const formatDateTime = (value) => {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

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

const load = async () => {
  loading.value = true
  try {
    const data = await request.get('/investment/contents', { params: pageParams(pagination) })
    const page = readPage(data)
    list.value = page.records
    pagination.total = page.total
    await loadPreview()
  } finally {
    loading.value = false
  }
}

const loadPreview = async () => {
  try {
    const data = await request.get('/investment/contents/preview', { silent: true })
    applyGroups(normalizeGroups(data))
  } catch (error) {
    applyGroups(previewListGroups.value)
  }
}

const handleSizeChange = () => {
  pagination.currentPage = 1
  load()
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
    await load()
  } finally {
    saving.value = false
  }
}

const publish = async (row) => {
  await ElMessageBox.confirm(`确定发布「${row.title}」吗？`, '发布确认', { type: 'warning' })
  await request.post(`/investment/contents/${row.id}/publish`)
  ElMessage.success('发布成功')
  await load()
}

const disable = async (row) => {
  await ElMessageBox.confirm(`确定停用「${row.title}」吗？停用后小程序端不再展示。`, '停用确认', { type: 'warning' })
  await request.post(`/investment/contents/${row.id}/disable`)
  ElMessage.success('已停用')
  await load()
}

const remove = async (row) => {
  await ElMessageBox.confirm(`确定删除「${row.title}」吗？`, '删除确认', { type: 'warning' })
  await request.delete(`/investment/contents/${row.id}`)
  ElMessage.success('已删除')
  await load()
}

const preview = (row) => {
  previewRow.value = {
    ...row,
    sectionKey: row.sectionKey || 'highlight',
    imageUrl: row.imageUrl || ''
  }
  previewVisible.value = true
}

onMounted(load)
</script>

<style scoped>
.page-container {
  padding: 20px;
}

.content-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 18px;
  align-items: start;
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
