<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>公告管理</span>
          <el-button type="primary" @click="openDialog">新增公告</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="list" border style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="title" label="标题" min-width="180" />
        <el-table-column prop="type" label="类型" width="130" />

        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'DRAFT'" type="info">草稿</el-tag>
            <el-tag v-else-if="row.status === 'PUBLISHED'" type="success">已发布</el-tag>
            <el-tag v-else-if="row.status === 'ARCHIVED'" type="warning">已归档</el-tag>
            <el-tag v-else>{{ row.status }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="发布时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.publishTime) }}
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.createdTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="content" label="内容" min-width="260" show-overflow-tooltip />

        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <div class="announcement-actions">
              <el-button size="small" type="primary" plain @click="openDialog(row)">
                编辑
              </el-button>
              <el-button
                v-if="row.status === 'DRAFT'"
                size="small"
                type="success"
                @click="publish(row)"
              >
                发布
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="visible" :title="form.id ? '编辑公告' : '新增公告'" width="680px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" />
        </el-form-item>

        <el-form-item label="类型" prop="type">
          <el-select v-model="form.type" style="width: 100%">
            <el-option label="通知 NOTICE" value="NOTICE" />
            <el-option label="维修维护 MAINTENANCE" value="MAINTENANCE" />
            <el-option label="缴费 PAYMENT" value="PAYMENT" />
            <el-option label="活动 EVENT" value="EVENT" />
          </el-select>
        </el-form-item>

        <el-form-item label="内容" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="6" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">
          {{ form.id ? '保存修改' : '保存为草稿' }}
        </el-button>
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
  id: null,
  title: '',
  type: 'NOTICE',
  content: '',
  status: 'DRAFT'
})

const form = reactive(defaultForm())

const rules = {
  title: [{ required: true, message: '请输入公告标题', trigger: 'blur' }],
  type: [{ required: true, message: '请选择公告类型', trigger: 'change' }],
  content: [{ required: true, message: '请输入公告内容', trigger: 'blur' }]
}

function resetForm() {
  Object.assign(form, defaultForm())
  formRef.value?.clearValidate()
}

async function load() {
  loading.value = true
  try {
    const data = await request.get('/announcements')
    list.value = Array.isArray(data) ? data : []
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '公告加载失败')
  } finally {
    loading.value = false
  }
}

function openDialog(row) {
  resetForm()
  if (row?.id) {
    Object.assign(form, {
      id: row.id,
      title: row.title || '',
      type: row.type || 'NOTICE',
      content: row.content || '',
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
      type: form.type,
      content: form.content,
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
    await load()
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '公告保存失败')
  } finally {
    saving.value = false
  }
}

function formatDateTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

async function publish(row) {
  try {
    await ElMessageBox.confirm(`确定发布公告「${row.title}」吗？`, '发布确认', {
      type: 'warning'
    })

    await request.post(`/announcements/${row.id}/publish`)
    ElMessage.success('公告发布成功')
    await load()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error?.response?.data?.message || '公告发布失败')
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

.announcement-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.announcement-actions :deep(.el-button) {
  margin-left: 0;
}
</style>
