<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>车位管理</span>
          <el-button type="primary" @click="openDialog">新增车位</el-button>
        </div>
      </template>

      <el-table :data="list" border>
        <el-table-column prop="id" label="ID" width="70"/>
        <el-table-column prop="spaceCode" label="车位编号"/>
        <el-table-column prop="area" label="区域"/>
        <el-table-column label="类型">
          <template #default="{row}">
            {{ spaceTypeText(row.spaceType) }}
          </template>
        </el-table-column>
        
        <el-table-column label="状态">
          <template #default="{row}">
            <el-tag v-if="row.status==='AVAILABLE'" type="success">空闲</el-tag>
            <el-tag v-else-if="row.status==='OCCUPIED'" type="danger">占用</el-tag>
            <el-tag v-else type="info">停用</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="使用方" min-width="150">
          <template #default="{row}">
            {{ ownerText(row) }}
          </template>
        </el-table-column>
        <el-table-column prop="plateNumber" label="车牌"/>

        <el-table-column label="操作" width="280">
          <template #default="{row}">
            <el-button size="small" @click="bind(row)" v-if="row.status==='AVAILABLE'">绑定</el-button>
            <el-button size="small" type="warning" @click="release(row)" v-if="row.status==='OCCUPIED'">释放</el-button>
            <el-button size="small" type="primary" @click="edit(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="visible" :title="form.id ? '编辑车位' : '新增车位'" width="520px">
      <el-form :model="form">
        <el-form-item label="车位编号">
          <el-input v-model="form.spaceCode"/>
        </el-form-item>

        <el-form-item label="区域">
          <el-select v-model="form.area" placeholder="请选择区域" style="width: 100%">
            <el-option v-for="area in areaOptions" :key="area" :label="area" :value="area"/>
          </el-select>
        </el-form-item>

        <el-form-item label="类型">
          <el-select v-model="form.spaceType" style="width: 100%">
            <el-option
              v-for="option in spaceTypeOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="空闲" value="AVAILABLE"/>
            <el-option label="占用" value="OCCUPIED"/>
            <el-option label="停用" value="DISABLED"/>
          </el-select>
        </el-form-item>

        <template v-if="form.status === 'OCCUPIED'">
          <el-form-item label="使用方">
            <el-select v-model="form.ownerKey" filterable placeholder="请选择租户或VIP" style="width: 100%">
              <el-option label="VIP" value="VIP"/>
              <el-option
                v-for="tenant in tenants"
                :key="tenant.id"
                :label="tenantLabel(tenant)"
                :value="tenantOwnerKey(tenant.id)"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="车牌号">
            <el-input v-model.trim="form.plateNumber" placeholder="请输入车牌号"/>
          </el-form-item>
        </template>
      </el-form>

      <template #footer>
        <el-button @click="visible=false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="bindVisible" title="绑定车牌" width="460px" destroy-on-close>
      <el-form ref="bindFormRef" :model="bindForm" :rules="bindRules" label-width="90px">
        <el-form-item label="车位编号">
          <el-input :model-value="currentSpace?.spaceCode || ''" disabled />
        </el-form-item>

        <el-form-item label="使用方" prop="ownerKey">
          <el-select v-model="bindForm.ownerKey" filterable placeholder="请选择租户或VIP" style="width: 100%">
            <el-option label="VIP" value="VIP"/>
            <el-option
              v-for="tenant in tenants"
              :key="tenant.id"
              :label="tenantLabel(tenant)"
              :value="tenantOwnerKey(tenant.id)"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="车牌号" prop="plateNumber">
          <el-input v-model.trim="bindForm.plateNumber" placeholder="请输入车牌号" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="bindVisible=false">取消</el-button>
        <el-button type="primary" :loading="binding" @click="submitBind">确定</el-button>
      </template>
    </el-dialog>

  </div>
</template>

<script setup>
import {ref, reactive, onMounted} from 'vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import request from '../../utils/request'

const list = ref([])
const tenants = ref([])
const visible = ref(false)
const bindVisible = ref(false)
const binding = ref(false)
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

const form = reactive({
  id:null,
  spaceCode:'',
  area:'A',
  spaceType:'NORMAL',
  status:'AVAILABLE',
  ownerKey:'',
  plateNumber:'',
  remark:''
})

const bindForm = reactive({
  ownerKey: '',
  plateNumber: ''
})

const bindRules = {
  ownerKey: [{ required: true, message: '请选择租户或VIP', trigger: 'change' }],
  plateNumber: [{ required: true, message: '请输入车牌号', trigger: 'blur' }]
}

const load = async ()=>{
  const data = await request.get('/parking/spaces')
  list.value = data || []
}

const loadTenants = async ()=>{
  const data = await request.get('/tenant/list')
  tenants.value = data || []
}

const tenantOwnerKey = (tenantId)=>`TENANT:${tenantId}`

const parseOwnerKey = (ownerKey)=>{
  if (ownerKey === 'VIP') return { vip: true, tenantId: null }
  if (String(ownerKey || '').startsWith('TENANT:')) {
    return { vip: false, tenantId: Number(String(ownerKey).replace('TENANT:', '')) }
  }
  return { vip: false, tenantId: null }
}

const tenantLabel = (tenant)=>{
  const name = tenant.tenantName || tenant.tenantCode || `租户${tenant.id}`
  return tenant.contactPerson ? `${name}（${tenant.contactPerson}）` : name
}

const tenantText = (tenantId)=>{
  if (!tenantId) return ''
  const tenant = tenants.value.find(item => item.id === tenantId)
  return tenant ? tenantLabel(tenant) : `租户 ${tenantId}`
}

const ownerKeyOf = (row)=>{
  if (row.spaceType === 'VIP' && !row.tenantId) return 'VIP'
  return row.tenantId ? tenantOwnerKey(row.tenantId) : ''
}

const ownerText = (row)=>{
  if (row.spaceType === 'VIP' && !row.tenantId) return 'VIP'
  return tenantText(row.tenantId) || '-'
}

const spaceTypeText = (type)=>({
  NORMAL: '普通车位',
  CHARGING_FAST: '充电车位（快充）',
  CHARGING_SLOW: '充电车位（慢充）',
  MECHANICAL: '机械车位',
  TEMPORARY: '临停车位',
  FIXED: '固定',
  TEMP: '临停',
  VIP: 'VIP'
}[type] || type || '-')

const openDialog = ()=>{
  form.id=null
  form.spaceCode=''
  form.area='A'
  form.spaceType='NORMAL'
  form.status='AVAILABLE'
  form.ownerKey=''
  form.plateNumber=''
  form.remark=''
  visible.value = true
}

const edit = (row)=>{
  form.id = row.id
  form.spaceCode = row.spaceCode || ''
  form.area = row.area || 'A'
  form.spaceType = normalizeSpaceType(row.spaceType)
  form.status = row.status || 'AVAILABLE'
  form.ownerKey = ownerKeyOf(row)
  form.plateNumber = row.plateNumber || ''
  form.remark = row.remark || ''
  visible.value = true
}

const normalizeSpaceType = (type)=>{
  if (type === 'FIXED') return 'NORMAL'
  if (type === 'TEMP') return 'TEMPORARY'
  if (type === 'VIP') return 'NORMAL'
  return type || 'NORMAL'
}

const buildPayload = ()=>{
  const owner = parseOwnerKey(form.ownerKey)
  const occupied = form.status === 'OCCUPIED'
  return {
    spaceCode: form.spaceCode,
    area: form.area,
    spaceType: occupied && owner.vip ? 'VIP' : form.spaceType,
    status: form.status,
    tenantId: occupied && !owner.vip ? owner.tenantId : null,
    plateNumber: occupied ? form.plateNumber : null,
    remark: form.remark
  }
}

const save = async ()=>{
  if (!form.spaceCode.trim()) {
    ElMessage.warning('请输入车位编号')
    return
  }
  if (!form.area) {
    ElMessage.warning('请选择区域')
    return
  }
  if (form.status === 'OCCUPIED' && !form.ownerKey) {
    ElMessage.warning('请选择使用方')
    return
  }
  if (form.status === 'OCCUPIED' && !form.plateNumber) {
    ElMessage.warning('请输入车牌号')
    return
  }

  if (form.id) {
    await request.put(`/parking/spaces/${form.id}`, buildPayload())
    ElMessage.success('保存成功')
  } else {
    await request.post('/parking/spaces', buildPayload())
    ElMessage.success('新增成功')
  }
  visible.value = false
  load()
}

const bind = (row)=>{
  currentSpace.value = row
  bindForm.ownerKey = ownerKeyOf(row)
  bindForm.plateNumber = row.plateNumber || ''
  bindVisible.value = true
}

const submitBind = async()=>{
  if (!bindFormRef.value || !currentSpace.value) return

  await bindFormRef.value.validate(async valid => {
    if (!valid) return

    binding.value = true

    try {
      await request.patch(`/parking/spaces/${currentSpace.value.id}/bind`, null, {
        params: {
          tenantId: parseOwnerKey(bindForm.ownerKey).tenantId,
          vip: parseOwnerKey(bindForm.ownerKey).vip,
          plateNumber: bindForm.plateNumber
        }
      })
      ElMessage.success('绑定成功')
      bindVisible.value = false
      await load()
    } finally {
      binding.value = false
    }
  })
}

const release = async(row)=>{
  await request.patch(`/parking/spaces/${row.id}/release`)
  ElMessage.success('已释放')
  load()
}

const remove = async(row)=>{
  await ElMessageBox.confirm(`确定删除车位「${row.spaceCode}」吗？`, '删除确认', {type: 'warning'})
  await request.delete(`/parking/spaces/${row.id}`)
  ElMessage.success('已删除')
  load()
}

onMounted(()=>{
  load()
  loadTenants()
})
</script>

<style scoped>
.page-container{
  padding:20px;
}
.card-header{
  display:flex;
  justify-content:space-between;
}
</style>
