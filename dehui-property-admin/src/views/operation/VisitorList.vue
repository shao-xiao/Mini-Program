<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>访客管理</span>
          <el-button type="primary" @click="openDialog">访客登记</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="list" border style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="visitorName" label="访客姓名" width="120" />
        <el-table-column prop="visitorPhone" label="手机号" width="130" />
        <el-table-column label="租户" width="130">
          <template #default="{ row }">
            {{ tenantName(row.tenantId) }}
          </template>
        </el-table-column>
        <el-table-column label="来源" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.source === 'MINIPROGRAM'" type="success">小程序</el-tag>
            <el-tag v-else type="info">后台</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="visitedPerson" label="被访人" width="120" />
        <el-table-column prop="visitReason" label="来访事由" min-width="150" />
        <el-table-column prop="visitTime" label="来访时间" width="180" />
        <el-table-column prop="leaveTime" label="离开时间" width="180" />

        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'REGISTERED'" type="info">已登记</el-tag>
            <el-tag v-else-if="row.status === 'ENTERED'" type="success">已进入</el-tag>
            <el-tag v-else-if="row.status === 'LEFT'" type="warning">已离开</el-tag>
            <el-tag v-else-if="row.status === 'CANCELLED'" type="danger">已取消</el-tag>
            <el-tag v-else>{{ row.status }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="remark" label="备注" min-width="140" />

        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'REGISTERED'"
              size="small"
              type="success"
              @click="enter(row)"
            >
              入场
            </el-button>

            <el-button
              v-if="row.status === 'ENTERED'"
              size="small"
              type="warning"
              @click="leave(row)"
            >
              离场
            </el-button>

            <el-button
              v-if="row.status === 'REGISTERED'"
              size="small"
              type="danger"
              @click="cancel(row)"
            >
              取消
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="visible" title="访客登记" width="620px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="访客姓名" prop="visitorName">
          <el-input v-model="form.visitorName" />
        </el-form-item>

        <el-form-item label="手机号" prop="visitorPhone">
          <el-input v-model="form.visitorPhone" />
        </el-form-item>

        <el-form-item label="身份证号">
          <el-input v-model="form.idCardNo" />
        </el-form-item>

        <el-form-item label="租户ID">
          <el-input-number v-model="form.tenantId" :min="1" style="width: 100%" />
        </el-form-item>

        <el-form-item label="被访人" prop="visitedPerson">
          <el-input v-model="form.visitedPerson" />
        </el-form-item>

        <el-form-item label="来访事由" prop="visitReason">
          <el-input v-model="form.visitReason" />
        </el-form-item>

        <el-form-item label="来访时间" prop="visitTime">
          <el-date-picker
            v-model="form.visitTime"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
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

const list = ref([])
const tenants = ref([])
const loading = ref(false)
const saving = ref(false)
const visible = ref(false)
const formRef = ref(null)

const defaultForm = () => ({
  visitorName: '',
  visitorPhone: '',
  idCardNo: '',
  tenantId: null,
  visitedPerson: '',
  visitReason: '',
  visitTime: '',
  remark: ''
})

const form = reactive(defaultForm())

const rules = {
  visitorName: [{ required: true, message: '请输入访客姓名', trigger: 'blur' }],
  visitorPhone: [{ required: true, message: '请输入手机号', trigger: 'blur' }],
  visitedPerson: [{ required: true, message: '请输入被访人', trigger: 'blur' }],
  visitReason: [{ required: true, message: '请输入来访事由', trigger: 'blur' }],
  visitTime: [{ required: true, message: '请选择来访时间', trigger: 'change' }]
}

function resetForm() {
  Object.assign(form, defaultForm())
  formRef.value?.clearValidate()
}

async function load() {
  loading.value = true
  try {
    const data = await request.get('/visitors')
    list.value = Array.isArray(data) ? data : []
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '访客记录加载失败')
  } finally {
    loading.value = false
  }
}

async function loadTenants() {
  const data = await request.get('/tenant/list')
  tenants.value = Array.isArray(data) ? data : []
}

function tenantName(tenantId) {
  if (!tenantId) return '-'
  const tenant = tenants.value.find(item => item.id === tenantId)
  return tenant ? tenant.tenantName : `租户ID:${tenantId}`
}

function openDialog() {
  resetForm()
  visible.value = true
}

async function save() {
  await formRef.value.validate()

  saving.value = true
  try {
    await request.post('/visitors', {
      visitorName: form.visitorName,
      visitorPhone: form.visitorPhone,
      idCardNo: form.idCardNo,
      tenantId: form.tenantId,
      visitedPerson: form.visitedPerson,
      visitReason: form.visitReason,
      visitTime: form.visitTime,
      remark: form.remark
    })

    ElMessage.success('访客登记成功')
    visible.value = false
    resetForm()
    await load()
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '访客登记失败')
  } finally {
    saving.value = false
  }
}

async function enter(row) {
  await request.patch(`/visitors/${row.id}/enter`)
  ElMessage.success('访客已入场')
  await load()
}

async function leave(row) {
  await request.patch(`/visitors/${row.id}/leave`)
  ElMessage.success('访客已离场')
  await load()
}

async function cancel(row) {
  try {
    await ElMessageBox.confirm(`确定取消访客「${row.visitorName}」的来访登记吗？`, '取消确认', {
      type: 'warning'
    })
    await request.patch(`/visitors/${row.id}/cancel`)
    ElMessage.success('已取消')
    await load()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error?.response?.data?.message || '取消失败')
    }
  }
}

onMounted(async () => {
  await Promise.all([load(), loadTenants()])
})
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
