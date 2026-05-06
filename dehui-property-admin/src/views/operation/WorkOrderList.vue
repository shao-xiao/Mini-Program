<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>工单管理</span>
          <el-button type="primary" @click="openDialog">新增工单</el-button>
        </div>
      </template>

      <el-table :data="list" border>
        <el-table-column prop="orderNumber" label="工单号" width="150"/>
        <el-table-column prop="title" label="标题"/>
        <el-table-column prop="orderType" label="类型"/>
        <el-table-column prop="category" label="类别"/>
        <el-table-column prop="priority" label="优先级"/>

        <el-table-column label="状态">
          <template #default="{row}">
            <el-tag v-if="row.status==='CREATED'">待派单</el-tag>
            <el-tag v-else-if="row.status==='ASSIGNED'" type="warning">已派单</el-tag>
            <el-tag v-else-if="row.status==='PROCESSING'" type="primary">处理中</el-tag>
            <el-tag v-else-if="row.status==='COMPLETED'" type="success">已完成</el-tag>
            <el-tag v-else-if="row.status==='CLOSED'" type="info">已关闭</el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="reporterId" label="报修人"/>
        <el-table-column prop="handlerId" label="处理人"/>

        <el-table-column label="操作" width="260">
          <template #default="{row}">
            <el-button size="small" v-if="row.status==='CREATED'" @click="assign(row)">派单</el-button>
            <el-button size="small" type="primary" v-if="row.status==='ASSIGNED'" @click="start(row)">开始</el-button>
            <el-button size="small" type="success" v-if="row.status==='PROCESSING'" @click="complete(row)">完成</el-button>
            <el-button size="small" type="info" v-if="row.status==='COMPLETED'" @click="close(row)">关闭</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="visible" title="新增工单">
      <el-form :model="form">
        <el-form-item label="标题">
          <el-input v-model="form.title"/>
        </el-form-item>

        <el-form-item label="描述">
          <el-input v-model="form.description"/>
        </el-form-item>

        <el-form-item label="位置">
          <el-input v-model="form.location"/>
        </el-form-item>

        <el-form-item label="类型">
          <el-select v-model="form.orderType">
            <el-option label="维修" value="REPAIR"/>
            <el-option label="巡检" value="PATROL"/>
            <el-option label="保洁" value="CLEAN"/>
            <el-option label="安保" value="SECURITY"/>
          </el-select>
        </el-form-item>

        <el-form-item label="类别">
          <el-input v-model="form.category"/>
        </el-form-item>

        <el-form-item label="优先级">
          <el-select v-model="form.priority">
            <el-option label="低" value="LOW"/>
            <el-option label="中" value="MEDIUM"/>
            <el-option label="高" value="HIGH"/>
          </el-select>
        </el-form-item>

        <el-form-item label="报修人ID">
          <el-input v-model="form.reporterId"/>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="visible=false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>

  </div>
</template>

<script setup>
import {ref, reactive, onMounted} from 'vue'
import {ElMessage} from 'element-plus'
import request from '../../utils/request'

const list = ref([])
const visible = ref(false)

const form = reactive({
  title:'',
  description:'',
  location:'',
  orderType:'REPAIR',
  category:'',
  priority:'MEDIUM',
  reporterId:''
})

const load = async ()=>{
  const data = await request.get('/workorders')
  list.value = data || []
}

const openDialog = ()=>{
  form.title=''
  form.description=''
  form.location=''
  form.category=''
  form.priority='MEDIUM'
  form.reporterId=''
  visible.value = true
}

const save = async ()=>{
  await request.post('/workorders', {
    title: form.title,
    description: form.description,
    location: form.location,
    orderType: form.orderType,
    category: form.category,
    priority: form.priority,
    reporterId: Number(form.reporterId)
  })

  ElMessage.success('创建成功')
  visible.value = false
  load()
}

const assign = async(row)=>{
  const handlerId = prompt('输入处理人ID')
  await request.patch(`/workorders/${row.id}/assign`, {handlerId:Number(handlerId)})
  ElMessage.success('已派单')
  load()
}

const start = async(row)=>{
  await request.patch(`/workorders/${row.id}/start`)
  ElMessage.success('已开始')
  load()
}

const complete = async(row)=>{
  await request.patch(`/workorders/${row.id}/complete`)
  ElMessage.success('已完成')
  load()
}

const close = async(row)=>{
  await request.patch(`/workorders/${row.id}/close`)
  ElMessage.success('已关闭')
  load()
}

onMounted(load)
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
