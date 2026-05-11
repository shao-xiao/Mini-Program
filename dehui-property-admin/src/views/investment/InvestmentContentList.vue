<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>招商内容</span>
          <el-button type="primary" @click="openDialog">新增内容</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="list" border>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="title" label="标题" min-width="160" />
        <el-table-column label="类型" width="130">
          <template #default="{ row }">
            {{ typeText(row.contentType) }}
          </template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="80" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'DRAFT'" type="info">草稿</el-tag>
            <el-tag v-else-if="row.status === 'PUBLISHED'" type="success">已发布</el-tag>
            <el-tag v-else>{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="内容" min-width="260" show-overflow-tooltip />
        <el-table-column label="发布时间" min-width="150">
          <template #default="{ row }">
            {{ formatDateTime(row.publishTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'DRAFT'"
              size="small"
              type="success"
              @click="publish(row)"
            >
              发布
            </el-button>
            <el-button v-else size="small" disabled>已发布</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="visible" title="新增招商内容" width="680px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="类型" prop="contentType">
          <el-select v-model="form.contentType" style="width: 100%">
            <el-option label="园区名称/主标题" value="OVERVIEW" />
            <el-option label="园区介绍" value="INTRO" />
            <el-option label="园区地址" value="ADDRESS" />
            <el-option label="联系电话" value="CONTACT" />
            <el-option label="园区亮点" value="HIGHLIGHT" />
            <el-option label="招商政策" value="POLICY" />
          </el-select>
        </el-form-item>

        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" />
        </el-form-item>

        <el-form-item label="内容" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="5" />
        </el-form-item>

        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="1" :max="999" style="width: 100%" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存为草稿</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../utils/request'

const list = ref([])
const loading = ref(false)
const saving = ref(false)
const visible = ref(false)
const formRef = ref(null)

const defaultForm = () => ({
  title: '',
  content: '',
  contentType: 'HIGHLIGHT',
  status: 'DRAFT',
  sortOrder: 100
})

const form = reactive(defaultForm())

const rules = {
  contentType: [{ required: true, message: '请选择内容类型', trigger: 'change' }],
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入内容', trigger: 'blur' }]
}

const typeText = (type) => {
  const map = {
    OVERVIEW: '主标题',
    INTRO: '园区介绍',
    ADDRESS: '园区地址',
    CONTACT: '联系电话',
    HIGHLIGHT: '园区亮点',
    POLICY: '招商政策'
  }
  return map[type] || type || '-'
}

const formatDateTime = (value) => {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

const resetForm = () => {
  Object.assign(form, defaultForm())
  formRef.value?.clearValidate()
}

const load = async () => {
  loading.value = true
  try {
    const data = await request.get('/investment/contents')
    list.value = Array.isArray(data) ? data : []
  } finally {
    loading.value = false
  }
}

const openDialog = () => {
  resetForm()
  visible.value = true
}

const save = async () => {
  await formRef.value.validate()
  saving.value = true
  try {
    await request.post('/investment/contents', { ...form })
    ElMessage.success('招商内容草稿创建成功')
    visible.value = false
    resetForm()
    await load()
  } finally {
    saving.value = false
  }
}

const publish = async (row) => {
  try {
    await ElMessageBox.confirm(`确定发布「${row.title}」吗？`, '发布确认', { type: 'warning' })
    await request.post(`/investment/contents/${row.id}/publish`)
    ElMessage.success('发布成功')
    await load()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error?.message || '发布失败')
    }
  }
}

onMounted(load)
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
</style>
