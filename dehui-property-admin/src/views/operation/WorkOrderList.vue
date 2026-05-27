<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>工单管理</span>
          <el-button type="primary" @click="openDialog">新增工单</el-button>
        </div>
      </template>

      <el-table :data="list" border :row-class-name="workOrderRowClassName">
        <el-table-column prop="orderNumber" label="工单号" width="150"/>
        <el-table-column prop="title" label="标题" min-width="150"/>
        <el-table-column prop="location" label="位置" min-width="120"/>
        <el-table-column label="类型" width="100">
          <template #default="{row}">
            {{ orderTypeText(row.orderType) }}
          </template>
        </el-table-column>
        <el-table-column label="问题类别" width="110">
          <template #default="{row}">
            {{ categoryText(row.category) }}
          </template>
        </el-table-column>
        <el-table-column label="优先级" width="90">
          <template #default="{row}">
            <div>{{ priorityText(row.priority) }}</div>
            <el-tag v-if="row.slaOverdue" class="sla-tag" type="danger" size="small">超时</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="90">
          <template #default="{row}">
            <el-tag v-if="row.status==='CREATED'">待派单</el-tag>
            <el-tag v-else-if="row.status==='ASSIGNED'" type="warning">已派单</el-tag>
            <el-tag v-else-if="row.status==='PROCESSING'" type="primary">处理中</el-tag>
            <el-tag v-else-if="row.status==='COMPLETED'" type="success">已完成</el-tag>
            <el-tag v-else-if="row.status==='CLOSED'" type="info">已关闭</el-tag>
            <el-tag v-else-if="row.status==='CANCELLED'" type="info">已撤回</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="报修人" min-width="130">
          <template #default="{row}">
            <div>{{ reporterText(row) }}</div>
            <div class="sub-text" v-if="row.reporterPhone">{{ row.reporterPhone }}</div>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="100">
          <template #default="{row}">
            <el-tag v-if="row.mobileUserId" type="success">小程序提交</el-tag>
            <el-tag v-else type="info">后台</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="租户" min-width="140">
          <template #default="{row}">
            {{ tenantText(row.tenantId) }}
          </template>
        </el-table-column>
        <el-table-column label="处理人" min-width="120">
          <template #default="{row}">
            {{ handlerText(row.handlerId) }}
          </template>
        </el-table-column>
        <el-table-column label="提交时间" min-width="150">
          <template #default="{row}">
            {{ formatDateTime(row.submittedTime || row.createdTime) }}
          </template>
        </el-table-column>
        <el-table-column label="阶段时间" min-width="150">
          <template #default="{row}">
            <div>{{ stageTime(row) }}</div>
            <div v-if="row.slaLabel" :class="row.slaOverdue ? 'sla-overdue-text' : 'sub-text'">
              {{ row.slaLabel }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="现场图片" min-width="120">
          <template #default="{row}">
            <el-image
              v-if="row.imageUrls && row.imageUrls.length"
              class="workorder-thumb"
              :src="imageSrc(row.imageUrls[0])"
              :preview-src-list="row.imageUrls.map(imageSrc)"
              preview-teleported
              fit="cover"
            />
            <div v-if="row.imageUrls && row.imageUrls.length" class="sub-text">
              共 {{ row.imageUrls.length }} 张
            </div>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="收费" min-width="130">
          <template #default="{row}">
            <div v-if="row.billable">
              <el-tag :type="row.billId ? 'success' : 'warning'">
                {{ row.billId ? '已生成账单' : '待生成账单' }}
              </el-tag>
              <div class="sub-text">¥ {{ row.chargeAmount || 0 }}</div>
            </div>
            <el-tag v-else type="info">不收费</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="处理结果" min-width="160">
          <template #default="{row}">
            <span>{{ row.handlingResult || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="用户评价" min-width="160">
          <template #default="{row}">
            <div v-if="row.rating">
              <el-rate :model-value="row.rating" disabled size="small"/>
              <div class="sub-text" v-if="row.evaluationContent">{{ row.evaluationContent }}</div>
            </div>
            <span v-else>-</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="340">
          <template #default="{row}">
            <el-button size="small" v-if="row.status==='CREATED'" @click="assign(row)">派单</el-button>
            <el-button size="small" type="primary" v-if="row.status==='ASSIGNED'" @click="start(row)">开始</el-button>
            <el-button size="small" type="success" v-if="row.status==='PROCESSING'" @click="complete(row)">完成</el-button>
            <el-button
              size="small"
              type="warning"
              v-if="row.status==='COMPLETED' && row.billable && !row.billId"
              @click="generateBill(row)"
            >
              生成账单
            </el-button>
            <el-button size="small" type="info" v-if="row.status==='COMPLETED'" @click="close(row)">关闭</el-button>
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

    <el-dialog v-model="visible" title="新增工单" width="560px">
      <el-form :model="form" label-width="92px">
        <el-form-item label="报修人">
          <el-input :model-value="currentUsername" disabled/>
        </el-form-item>

        <el-form-item label="标题">
          <el-input v-model="form.title" placeholder="例如：三楼空调不制冷"/>
        </el-form-item>

        <el-form-item label="描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            placeholder="请描述现象、影响范围、出现时间等"
          />
        </el-form-item>

        <el-form-item label="位置">
          <el-input v-model="form.location" placeholder="例如：三楼 308 / B1 车库入口"/>
        </el-form-item>

        <el-form-item label="关联租户">
          <el-select
            v-model="form.tenantId"
            class="form-select"
            clearable
            filterable
            placeholder="可选，涉及收费时建议选择"
          >
            <el-option
              v-for="tenant in tenants"
              :key="tenant.id"
              :label="tenantLabel(tenant)"
              :value="tenant.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="工单类型">
          <el-select v-model="form.orderType" class="form-select" @change="resetCategory">
            <el-option
              v-for="item in orderTypeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="问题类别">
          <el-select v-model="form.category" class="form-select" placeholder="请选择问题类别">
            <el-option
              v-for="item in currentCategoryOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="优先级">
          <el-select v-model="form.priority" class="form-select">
            <el-option
              v-for="item in priorityOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="visible=false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="assignVisible" title="派单" width="460px">
      <el-form :model="assignForm" label-width="92px">
        <el-form-item label="工单">
          <el-input :model-value="assignForm.orderTitle" disabled/>
        </el-form-item>
        <el-form-item label="维修人员">
          <el-select
            v-model="assignForm.handlerId"
            class="form-select"
            filterable
            placeholder="请选择维修人员"
          >
            <el-option
              v-for="user in activeUsers"
              :key="user.id"
              :label="userLabel(user)"
              :value="user.id"
            />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="assignVisible=false">取消</el-button>
        <el-button type="primary" @click="confirmAssign">确认派单</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="completeVisible" title="完成工单" width="520px">
      <el-form :model="completeForm" label-width="92px">
        <el-form-item label="工单">
          <el-input :model-value="completeForm.orderTitle" disabled/>
        </el-form-item>
        <el-form-item label="处理结果">
          <el-input
            v-model="completeForm.handlingResult"
            type="textarea"
            :rows="4"
            placeholder="请填写维修处理情况、处理结果或后续建议"
          />
        </el-form-item>
        <el-form-item label="是否收费">
          <el-switch
            v-model="completeForm.billable"
            active-text="向租户收费"
            inactive-text="不收费"
          />
        </el-form-item>
        <template v-if="completeForm.billable">
          <el-form-item label="关联租户">
            <el-input :model-value="tenantText(completeForm.tenantId)" disabled/>
          </el-form-item>
          <el-form-item label="收费金额">
            <el-input-number
              v-model="completeForm.chargeAmount"
              :min="0"
              :precision="2"
              :step="50"
              class="form-select"
            />
          </el-form-item>
          <el-form-item label="收费说明">
            <el-input
              v-model="completeForm.chargeRemark"
              type="textarea"
              :rows="2"
              placeholder="例如：装修垃圾清运费 / 人为损坏维修材料费"
            />
          </el-form-item>
        </template>
      </el-form>

      <template #footer>
        <el-button @click="completeVisible=false">取消</el-button>
        <el-button type="primary" @click="confirmComplete">确认完成</el-button>
      </template>
    </el-dialog>

  </div>
</template>

<script setup>
import {ref, reactive, onMounted, computed} from 'vue'
import {ElMessage} from 'element-plus'
import request from '../../utils/request'
import { createPagination, pageParams, readPage, resetToFirstPage } from '../../utils/pagination'

const list = ref([])
const users = ref([])
const tenants = ref([])
const visible = ref(false)
const assignVisible = ref(false)
const completeVisible = ref(false)
const currentUsername = localStorage.getItem('username') || '当前用户'
const currentUserId = Number(localStorage.getItem('userId'))
const pagination = reactive(createPagination(20))

const orderTypeOptions = [
  { label: '维修报修', value: 'REPAIR' },
  { label: '巡检任务', value: 'PATROL' },
  { label: '保洁服务', value: 'CLEAN' },
  { label: '安保事件', value: 'SECURITY' }
]

const categoryOptions = {
  REPAIR: [
    { label: '水电维修', value: 'WATER_ELECTRIC' },
    { label: '空调暖通', value: 'HVAC' },
    { label: '电梯故障', value: 'ELEVATOR' },
    { label: '门禁网络', value: 'ACCESS_NETWORK' },
    { label: '土建装饰', value: 'CIVIL_DECORATION' },
    { label: '其他维修', value: 'OTHER_REPAIR' }
  ],
  PATROL: [
    { label: '设备巡检', value: 'EQUIPMENT_PATROL' },
    { label: '消防巡检', value: 'FIRE_PATROL' },
    { label: '公共区域巡查', value: 'PUBLIC_AREA_PATROL' },
    { label: '安全隐患', value: 'SAFETY_RISK' }
  ],
  CLEAN: [
    { label: '公共区域保洁', value: 'PUBLIC_CLEAN' },
    { label: '卫生间保洁', value: 'RESTROOM_CLEAN' },
    { label: '垃圾清运', value: 'WASTE_REMOVAL' },
    { label: '专项清洁', value: 'SPECIAL_CLEAN' }
  ],
  SECURITY: [
    { label: '门岗秩序', value: 'GATE_ORDER' },
    { label: '车辆秩序', value: 'VEHICLE_ORDER' },
    { label: '访客协助', value: 'VISITOR_SUPPORT' },
    { label: '突发事件', value: 'EMERGENCY' }
  ]
}

const priorityOptions = [
  { label: '低', value: 'LOW' },
  { label: '中', value: 'MEDIUM' },
  { label: '普通', value: 'NORMAL' },
  { label: '高', value: 'HIGH' },
  { label: '紧急', value: 'URGENT' }
]

const form = reactive({
  title:'',
  description:'',
  location:'',
  tenantId:null,
  orderType:'REPAIR',
  category:'WATER_ELECTRIC',
  priority:'MEDIUM'
})

const assignForm = reactive({
  orderId: null,
  orderTitle: '',
  handlerId: null
})

const completeForm = reactive({
  orderId: null,
  orderTitle: '',
  tenantId: null,
  handlingResult: '',
  billable: false,
  chargeAmount: 0,
  chargeRemark: ''
})

const currentCategoryOptions = computed(() => categoryOptions[form.orderType] || [])
const activeUsers = computed(() => users.value)

const optionText = (options, value) => options.find(item => item.value === value)?.label || value || '-'
const orderTypeText = (value) => optionText(orderTypeOptions, value)
const categoryText = (value) => {
  const mobileCategoryOptions = [
    { label: '水路', value: 'WATER' },
    { label: '电路', value: 'ELECTRIC' },
    { label: '空调', value: 'AIR_CONDITIONER' },
    { label: '门窗', value: 'DOOR_WINDOW' },
    { label: '网络', value: 'NETWORK' },
    { label: '保洁', value: 'CLEANING' },
    { label: '其他', value: 'OTHER' }
  ]
  const options = Object.values(categoryOptions).flat().concat(mobileCategoryOptions)
  return optionText(options, value)
}
const priorityText = (value) => optionText(priorityOptions, value)

const formatDateTime = (value)=>{
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

const reporterText = (row)=>{
  if (row.reporterName) return row.reporterName
  if (row.reporterId === currentUserId) return currentUsername
  return row.reporterId ? `用户ID ${row.reporterId}` : '-'
}

const stageTime = (row)=>{
  const timeMap = {
    CREATED: row.submittedTime || row.createdTime,
    ASSIGNED: row.assignedTime,
    PROCESSING: row.processingTime,
    COMPLETED: row.completedTime,
    CLOSED: row.closedTime,
    CANCELLED: row.cancelledTime
  }
  return formatDateTime(timeMap[row.status] || row.updatedTime)
}

const userLabel = (user)=>{
  const name = user.realName || user.username || `用户${user.id}`
  return user.phone ? `${name}（${user.phone}）` : name
}

const tenantLabel = (tenant)=>{
  const name = tenant.tenantName || tenant.tenantCode || `租户${tenant.id}`
  return tenant.contactPerson ? `${name}（${tenant.contactPerson}）` : name
}

const tenantText = (tenantId)=>{
  if (!tenantId) return '-'
  const tenant = tenants.value.find(item => item.id === tenantId)
  return tenant ? tenantLabel(tenant) : `租户ID ${tenantId}`
}

const handlerText = (handlerId)=>{
  if (!handlerId) return '-'
  const user = users.value.find(item => item.id === handlerId)
  return user ? userLabel(user) : `用户ID ${handlerId}`
}

const imageSrc = (url)=>{
  if (!url) return ''
  if (url.startsWith('http')) return url
  return `/api${url}`
}

const workOrderRowClassName = ({row}) => row.slaOverdue ? 'sla-overdue-row' : ''

const load = async ()=>{
  const data = await request.get('/workorders', { params: pageParams(pagination) })
  const page = readPage(data)
  list.value = page.records
  pagination.total = page.total
}

const handleSizeChange = () => {
  resetToFirstPage(pagination)
  load()
}

const loadUsers = async ()=>{
  const data = await request.get('/workorders/assignable-users')
  users.value = data || []
}

const loadTenants = async ()=>{
  const data = await request.get('/tenant/list')
  tenants.value = data || []
}

const resetCategory = ()=>{
  form.category = currentCategoryOptions.value[0]?.value || ''
}

const openDialog = ()=>{
  form.title=''
  form.description=''
  form.location=''
  form.tenantId=null
  form.orderType='REPAIR'
  form.category='WATER_ELECTRIC'
  form.priority='MEDIUM'
  visible.value = true
}

const save = async ()=>{
  if (!form.title) {
    ElMessage.warning('请输入工单标题')
    return
  }

  if (!form.category) {
    ElMessage.warning('请选择问题类别')
    return
  }

  await request.post('/workorders', {
    title: form.title,
    description: form.description,
    location: form.location,
    tenantId: form.tenantId ? Number(form.tenantId) : null,
    orderType: form.orderType,
    category: form.category,
    priority: form.priority
  })

  ElMessage.success('创建成功')
  visible.value = false
  load()
}

const assign = (row)=>{
  assignForm.orderId = row.id
  assignForm.orderTitle = `${row.orderNumber} ${row.title}`
  assignForm.handlerId = row.handlerId || null
  assignVisible.value = true
}

const confirmAssign = async()=>{
  if (!assignForm.handlerId) {
    ElMessage.warning('请选择维修人员')
    return
  }

  await request.patch(`/workorders/${assignForm.orderId}/assign`, {handlerId:Number(assignForm.handlerId)})
  ElMessage.success('已派单')
  assignVisible.value = false
  load()
}

const start = async(row)=>{
  await request.patch(`/workorders/${row.id}/start`)
  ElMessage.success('已开始')
  load()
}

const complete = (row)=>{
  completeForm.orderId = row.id
  completeForm.orderTitle = `${row.orderNumber} ${row.title}`
  completeForm.tenantId = row.tenantId || null
  completeForm.handlingResult = row.handlingResult || ''
  completeForm.billable = Boolean(row.billable)
  completeForm.chargeAmount = Number(row.chargeAmount || 0)
  completeForm.chargeRemark = row.chargeRemark || ''
  completeVisible.value = true
}

const confirmComplete = async()=>{
  if (!completeForm.handlingResult.trim()) {
    ElMessage.warning('请填写处理结果')
    return
  }
  if (completeForm.billable && !completeForm.tenantId) {
    ElMessage.warning('向租户收费的工单需先关联租户')
    return
  }
  if (completeForm.billable && Number(completeForm.chargeAmount) <= 0) {
    ElMessage.warning('请输入大于0的收费金额')
    return
  }
  await request.patch(`/workorders/${completeForm.orderId}/complete`, {
    handlingResult: completeForm.handlingResult,
    billable: completeForm.billable,
    chargeAmount: completeForm.billable ? Number(completeForm.chargeAmount) : null,
    chargeRemark: completeForm.billable ? completeForm.chargeRemark : null
  })
  ElMessage.success('已完成')
  completeVisible.value = false
  load()
}

const close = async(row)=>{
  await request.patch(`/workorders/${row.id}/close`)
  ElMessage.success('已关闭')
  load()
}

const generateBill = async(row)=>{
  await request.post(`/workorders/${row.id}/generate-bill`)
  ElMessage.success('账单已生成')
  load()
}

onMounted(()=>{
  load()
  loadUsers()
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

.form-select{
  width:100%;
}

.sub-text{
  margin-top:4px;
  color:#909399;
  font-size:12px;
}

.sla-tag{
  margin-top:4px;
}

.sla-overdue-text{
  margin-top:4px;
  color:#d93025;
  font-size:12px;
  font-weight:600;
}

:deep(.sla-overdue-row){
  background:#fff1f0;
}

:deep(.sla-overdue-row:hover > td.el-table__cell){
  background:#ffe7e6 !important;
}

.workorder-thumb{
  width:48px;
  height:48px;
  border-radius:6px;
}
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
