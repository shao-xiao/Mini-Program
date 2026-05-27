<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>巡检记录</span>
          <el-button type="primary" @click="openDialog">新增巡检</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="list" border style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="inspectionDate" label="巡检日期" width="120" />
        <el-table-column prop="inspector" label="巡检人" width="110" />
        <el-table-column prop="inspectionType" label="巡检类型" width="130" />
        <el-table-column prop="area" label="区域/地点" width="130" />
        <el-table-column prop="target" label="巡检对象" min-width="160" />

        <el-table-column label="结果" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.result === 'NORMAL'" type="success">正常</el-tag>
            <el-tag v-else-if="row.result === 'ABNORMAL'" type="danger">异常</el-tag>
            <el-tag v-else>{{ row.result }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="problemDescription" label="问题描述" min-width="180" show-overflow-tooltip />
        <el-table-column prop="actionTaken" label="处理措施" min-width="160" show-overflow-tooltip />

        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'OPEN'" type="warning">未关闭</el-tag>
            <el-tag v-else-if="row.status === 'CLOSED'" type="success">已关闭</el-tag>
            <el-tag v-else>{{ row.status }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />

        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'OPEN'"
              size="small"
              type="success"
              @click="closeInspection(row)"
            >
              关闭
            </el-button>
            <el-button v-else size="small" disabled>已关闭</el-button>
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

    <el-dialog v-model="visible" title="新增巡检" width="720px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="巡检日期" prop="inspectionDate">
          <el-date-picker
            v-model="form.inspectionDate"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="巡检人" prop="inspector">
          <el-input v-model="form.inspector" />
        </el-form-item>

        <el-form-item label="巡检类型" prop="inspectionType">
          <el-select v-model="form.inspectionType" style="width: 100%">
            <el-option label="日常巡检 DAILY" value="DAILY" />
            <el-option label="消防巡检 FIRE" value="FIRE" />
            <el-option label="安保巡检 SECURITY" value="SECURITY" />
            <el-option label="保洁巡检 CLEAN" value="CLEAN" />
            <el-option label="设备巡检 EQUIPMENT" value="EQUIPMENT" />
            <el-option label="其他 OTHER" value="OTHER" />
          </el-select>
        </el-form-item>

        <el-form-item label="区域/地点" prop="area">
          <el-input v-model="form.area" placeholder="如 B1停车区 / 1F大厅 / 消防通道" />
        </el-form-item>

        <el-form-item label="巡检对象" prop="target">
          <el-input v-model="form.target" placeholder="如 全部电梯 / 消防栓 / 公共区域 / 重点设施" />
        </el-form-item>

        <el-form-item label="巡检结果" prop="result">
          <el-select v-model="form.result" style="width: 100%">
            <el-option label="正常 NORMAL" value="NORMAL" />
            <el-option label="异常 ABNORMAL" value="ABNORMAL" />
          </el-select>
        </el-form-item>

        <el-form-item label="问题描述">
          <el-input
            v-model="form.problemDescription"
            type="textarea"
            :rows="3"
            placeholder="如发现异常，请填写问题描述"
          />
        </el-form-item>

        <el-form-item label="处理措施">
          <el-input
            v-model="form.actionTaken"
            type="textarea"
            :rows="3"
            placeholder="填写现场处理措施或后续安排"
          />
        </el-form-item>

        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../utils/request'
import { createPagination, pageParams, readPage, resetToFirstPage } from '../../utils/pagination'

const list = ref([])
const loading = ref(false)
const saving = ref(false)
const visible = ref(false)
const pagination = reactive(createPagination(20))
const formRef = ref(null)

const defaultForm = () => ({
  inspectionDate: '',
  inspector: '',
  inspectionType: 'DAILY',
  area: '',
  target: '',
  result: 'NORMAL',
  problemDescription: '',
  actionTaken: '',
  remark: ''
})

const form = reactive(defaultForm())

const rules = {
  inspectionDate: [{ required: true, message: '请选择巡检日期', trigger: 'change' }],
  inspector: [{ required: true, message: '请输入巡检人', trigger: 'blur' }],
  inspectionType: [{ required: true, message: '请选择巡检类型', trigger: 'change' }],
  area: [{ required: true, message: '请输入区域/地点', trigger: 'blur' }],
  target: [{ required: true, message: '请输入巡检对象', trigger: 'blur' }],
  result: [{ required: true, message: '请选择巡检结果', trigger: 'change' }]
}

function resetForm() {
  Object.assign(form, defaultForm())
  formRef.value?.clearValidate()
}

async function load() {
  loading.value = true
  try {
    const data = await request.get('/inspections', { params: pageParams(pagination) })
    const page = readPage(data)
    list.value = page.records
    pagination.total = page.total
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '巡检记录加载失败')
  } finally {
    loading.value = false
  }
}

function handleSizeChange() {
  resetToFirstPage(pagination)
  load()
}

function openDialog() {
  resetForm()
  visible.value = true
}

async function save() {
  await formRef.value.validate()

  saving.value = true
  try {
    await request.post('/inspections', { ...form })
    ElMessage.success('巡检记录创建成功')
    visible.value = false
    resetForm()
    await load()
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '巡检记录创建失败')
  } finally {
    saving.value = false
  }
}

async function closeInspection(row) {
  try {
    await ElMessageBox.confirm(
      `确定关闭巡检记录「${row.area} - ${row.target}」吗？`,
      '关闭确认',
      { type: 'warning' }
    )

    await request.post(`/inspections/${row.id}/close`)
    ElMessage.success('巡检记录已关闭')
    await load()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error?.response?.data?.message || '关闭失败')
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
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
