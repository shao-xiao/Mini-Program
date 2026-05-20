<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>车位管理</span>
          <el-button type="primary" @click="openDialog()">新增车位</el-button>
        </div>
      </template>

      <el-form :inline="true" :model="query" class="toolbar">
        <el-form-item label="关键词">
          <el-input v-model.trim="query.keyword" clearable placeholder="车位编号/使用方/车牌" @keyup.enter="load" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 140px">
            <el-option label="空闲" value="AVAILABLE" />
            <el-option label="占用" value="OCCUPIED" />
            <el-option label="维护中" value="MAINTENANCE" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="区域">
          <el-select v-model="query.area" clearable placeholder="全部" style="width: 120px">
            <el-option v-for="area in areaOptions" :key="area" :label="area" :value="area" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="list" border>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="spaceNo" label="车位编号" min-width="120" />
        <el-table-column prop="area" label="区域" width="90" />
        <el-table-column prop="floor" label="楼层/位置" min-width="110" />
        <el-table-column label="类型" width="150">
          <template #default="{ row }">{{ row.typeText || spaceTypeText(row.type || row.spaceType) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)">{{ row.statusText || statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="使用方" min-width="170">
          <template #default="{ row }">
            {{ row.partyNameSnapshot || '-' }}
            <span v-if="row.partyTypeText" class="muted">（{{ row.partyTypeText }}）</span>
          </template>
        </el-table-column>
        <el-table-column prop="plateNo" label="车牌" width="130" />
        <el-table-column label="月费" width="110">
          <template #default="{ row }">{{ row.monthlyFee ? `¥ ${Number(row.monthlyFee).toFixed(2)}` : '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="320" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'AVAILABLE'" size="small" @click="openBind(row)">绑定</el-button>
            <el-button v-if="row.status === 'OCCUPIED'" size="small" type="warning" @click="release(row)">释放</el-button>
            <el-button v-if="row.status === 'OCCUPIED'" size="small" @click="viewBills(row)">查看账单</el-button>
            <el-button size="small" type="primary" @click="openDialog(row)">编辑</el-button>
            <el-button v-if="row.status === 'MAINTENANCE' || row.status === 'DISABLED'" size="small" @click="enable(row)">启用</el-button>
            <el-button v-if="row.status === 'AVAILABLE'" size="small" type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="spaceVisible" :title="spaceForm.id ? '编辑车位' : '新增车位'" width="520px" destroy-on-close>
      <el-form ref="spaceFormRef" :model="spaceForm" :rules="spaceRules" label-width="100px">
        <el-form-item label="车位编号" prop="spaceNo">
          <el-input v-model.trim="spaceForm.spaceNo" placeholder="如 B1-004" />
        </el-form-item>
        <el-form-item label="区域" prop="area">
          <el-select v-model="spaceForm.area" placeholder="请选择区域" style="width: 100%">
            <el-option v-for="area in areaOptions" :key="area" :label="area" :value="area" />
          </el-select>
        </el-form-item>
        <el-form-item label="楼层/位置">
          <el-input v-model.trim="spaceForm.floor" placeholder="如 B1、地面停车区" />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-select v-model="spaceForm.type" style="width: 100%">
            <el-option v-for="option in spaceTypeOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="spaceForm.sortOrder" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="spaceForm.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="spaceVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveSpace">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="bindVisible" title="绑定车位" width="560px" destroy-on-close>
      <el-form ref="bindFormRef" :model="bindForm" :rules="bindRules" label-width="110px">
        <el-form-item label="车位编号">
          <el-input :model-value="currentSpace?.spaceNo || ''" disabled />
        </el-form-item>
        <el-form-item label="使用方类型" prop="partyType">
          <el-select v-model="bindForm.partyType" style="width: 100%" @change="onPartyTypeChange">
            <el-option label="租户" value="tenant" />
            <el-option label="VIP" value="vip" />
            <el-option label="外部客户" value="external" />
            <el-option label="内部员工" value="internal" />
            <el-option label="其他" value="other" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="bindForm.partyType === 'tenant'" label="租户" prop="partyId">
          <el-select v-model="bindForm.partyId" filterable placeholder="请选择租户" style="width: 100%">
            <el-option v-for="tenant in tenants" :key="tenant.id" :label="tenantLabel(tenant)" :value="tenant.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-else label="使用方名称" prop="partyName">
          <el-input v-model.trim="bindForm.partyName" placeholder="请输入使用方名称" />
        </el-form-item>
        <el-form-item label="车牌号" prop="plateNo">
          <el-input v-model.trim="bindForm.plateNo" placeholder="请输入车牌号" />
        </el-form-item>
        <el-form-item label="开始日期" prop="startDate">
          <el-date-picker v-model="bindForm.startDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="收费类型" prop="billingType">
          <el-select v-model="bindForm.billingType" style="width: 100%">
            <el-option label="月租" value="monthly" />
            <el-option label="免费" value="free" />
            <el-option label="临停" value="temporary" />
          </el-select>
        </el-form-item>
        <el-form-item label="月租金额" prop="monthlyFee">
          <el-input-number v-model="bindForm.monthlyFee" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="bindForm.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="bindVisible = false">取消</el-button>
        <el-button type="primary" :loading="binding" @click="submitBind">确定绑定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  bindParkingSpace,
  createParkingSpace,
  deleteParkingSpace,
  listParkingSpaces,
  releaseParkingSpace,
  updateParkingSpace,
  updateParkingSpaceStatus
} from '../../api/parking'
import request from '../../utils/request'

const router = useRouter()
const list = ref([])
const tenants = ref([])
const loading = ref(false)
const saving = ref(false)
const binding = ref(false)
const spaceVisible = ref(false)
const bindVisible = ref(false)
const spaceFormRef = ref(null)
const bindFormRef = ref(null)
const currentSpace = ref(null)

const areaOptions = ['A', 'B', 'C', 'D']
const spaceTypeOptions = [
  { label: '普通车位', value: 'NORMAL' },
  { label: '充电车位（快充）', value: 'CHARGING_FAST' },
  { label: '充电车位（慢充）', value: 'CHARGING_SLOW' },
  { label: '机械车位', value: 'MECHANICAL' },
  { label: '临停车位', value: 'TEMPORARY' }
]

const query = reactive({ keyword: '', status: '', area: '' })
const spaceForm = reactive({ id: null, spaceNo: '', area: 'A', floor: '', type: 'NORMAL', sortOrder: 0, remark: '' })
const bindForm = reactive({
  partyType: 'tenant',
  partyId: null,
  partyName: '',
  plateNo: '',
  startDate: new Date().toISOString().slice(0, 10),
  billingType: 'monthly',
  monthlyFee: 300,
  remark: ''
})

const spaceRules = {
  spaceNo: [{ required: true, message: '请输入车位编号', trigger: 'blur' }],
  area: [{ required: true, message: '请选择区域', trigger: 'change' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }]
}

const bindRules = {
  partyType: [{ required: true, message: '请选择使用方类型', trigger: 'change' }],
  partyId: [{ required: true, message: '请选择租户', trigger: 'change' }],
  partyName: [{ required: true, message: '请输入使用方名称', trigger: 'blur' }],
  plateNo: [{ required: true, message: '请输入车牌号', trigger: 'blur' }],
  startDate: [{ required: true, message: '请选择开始日期', trigger: 'change' }],
  billingType: [{ required: true, message: '请选择收费类型', trigger: 'change' }],
  monthlyFee: [{
    validator: (_rule, value, callback) => {
      if (bindForm.billingType === 'monthly' && (!value || value <= 0)) callback(new Error('月租金额必须大于0'))
      else callback()
    },
    trigger: 'change'
  }]
}

async function load() {
  loading.value = true
  try {
    list.value = await listParkingSpaces({ ...query })
  } finally {
    loading.value = false
  }
}

async function loadTenants() {
  tenants.value = await request.get('/tenant/list')
}

function resetQuery() {
  query.keyword = ''
  query.status = ''
  query.area = ''
  load()
}

function openDialog(row) {
  Object.assign(spaceForm, {
    id: row?.id || null,
    spaceNo: row?.spaceNo || row?.spaceCode || '',
    area: row?.area || 'A',
    floor: row?.floor || '',
    type: normalizeSpaceType(row?.type || row?.spaceType),
    sortOrder: row?.sortOrder || 0,
    remark: row?.remark || ''
  })
  spaceVisible.value = true
}

async function saveSpace() {
  await spaceFormRef.value.validate()
  saving.value = true
  try {
    const payload = { ...spaceForm }
    if (spaceForm.id) {
      await updateParkingSpace(spaceForm.id, payload)
      ElMessage.success('保存成功')
    } else {
      await createParkingSpace(payload)
      ElMessage.success('新增成功')
    }
    spaceVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

function openBind(row) {
  currentSpace.value = row
  Object.assign(bindForm, {
    partyType: 'tenant',
    partyId: null,
    partyName: '',
    plateNo: '',
    startDate: new Date().toISOString().slice(0, 10),
    billingType: 'monthly',
    monthlyFee: 300,
    remark: ''
  })
  bindVisible.value = true
}

function onPartyTypeChange(type) {
  bindForm.partyId = null
  bindForm.partyName = type === 'vip' ? 'VIP' : ''
}

async function submitBind() {
  await bindFormRef.value.validate()
  binding.value = true
  try {
    await bindParkingSpace(currentSpace.value.id, { ...bindForm })
    ElMessage.success('绑定成功')
    bindVisible.value = false
    await load()
  } finally {
    binding.value = false
  }
}

async function release(row) {
  await ElMessageBox.confirm(`确定释放车位「${row.spaceNo}」吗？`, '释放确认', { type: 'warning' })
  await releaseParkingSpace(row.id, { endDate: new Date().toISOString().slice(0, 10) })
  ElMessage.success('已释放')
  await load()
}

async function enable(row) {
  await updateParkingSpaceStatus(row.id, 'AVAILABLE')
  ElMessage.success('已启用')
  await load()
}

async function remove(row) {
  await ElMessageBox.confirm(`确定删除车位「${row.spaceNo}」吗？`, '删除确认', { type: 'warning' })
  await deleteParkingSpace(row.id)
  ElMessage.success('已删除')
  await load()
}

function viewBills(row) {
  router.push({ path: '/parking/bills', query: { keyword: row.spaceNo } })
}

function tenantLabel(tenant) {
  const name = tenant.tenantName || tenant.tenantCode || `租户${tenant.id}`
  return tenant.contactPerson ? `${name}（${tenant.contactPerson}）` : name
}

function normalizeSpaceType(type) {
  if (type === 'FIXED' || type === 'VIP' || !type) return 'NORMAL'
  if (type === 'TEMP') return 'TEMPORARY'
  return type
}

function statusTag(status) {
  return { AVAILABLE: 'success', OCCUPIED: 'danger', MAINTENANCE: 'warning', DISABLED: 'info' }[status] || 'info'
}

function statusText(status) {
  return { AVAILABLE: '空闲', OCCUPIED: '占用', MAINTENANCE: '维护中', DISABLED: '停用' }[status] || status || '-'
}

function spaceTypeText(type) {
  return {
    NORMAL: '普通车位',
    CHARGING_FAST: '充电车位（快充）',
    CHARGING_SLOW: '充电车位（慢充）',
    MECHANICAL: '机械车位',
    TEMPORARY: '临停车位'
  }[type] || type || '-'
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

.toolbar {
  margin-bottom: 12px;
}

.muted {
  color: #909399;
}
</style>
