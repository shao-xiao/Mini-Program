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
        <el-table-column prop="spaceType" label="类型"/>
        
        <el-table-column label="状态">
          <template #default="{row}">
            <el-tag v-if="row.status==='AVAILABLE'" type="success">空闲</el-tag>
            <el-tag v-else-if="row.status==='OCCUPIED'" type="danger">占用</el-tag>
            <el-tag v-else type="info">停用</el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="tenantId" label="租户ID"/>
        <el-table-column prop="plateNumber" label="车牌"/>

        <el-table-column label="操作" width="200">
          <template #default="{row}">
            <el-button size="small" @click="bind(row)" v-if="row.status==='AVAILABLE'">绑定</el-button>
            <el-button size="small" type="warning" @click="release(row)" v-if="row.status==='OCCUPIED'">释放</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="visible" title="新增车位">
      <el-form :model="form">
        <el-form-item label="车位编号">
          <el-input v-model="form.spaceCode"/>
        </el-form-item>

        <el-form-item label="区域">
          <el-input v-model="form.area"/>
        </el-form-item>

        <el-form-item label="类型">
          <el-select v-model="form.spaceType">
            <el-option label="固定" value="FIXED"/>
            <el-option label="临停" value="TEMP"/>
            <el-option label="VIP" value="VIP"/>
          </el-select>
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
import {ElMessage, ElMessageBox} from 'element-plus'
import request from '../../utils/request'

const list = ref([])
const visible = ref(false)

const form = reactive({
  spaceCode:'',
  area:'',
  spaceType:'FIXED'
})

const load = async ()=>{
  const data = await request.get('/parking/spaces')
  list.value = data || []
}

const openDialog = ()=>{
  form.spaceCode=''
  form.area=''
  form.spaceType='FIXED'
  visible.value = true
}

const save = async ()=>{
  await request.post('/parking/spaces', form)
  ElMessage.success('新增成功')
  visible.value = false
  load()
}

const bind = async(row)=>{
  const tenantId = prompt('输入租户ID')
  const plate = prompt('输入车牌')

  await request.patch(`/parking/spaces/${row.id}/bind?tenantId=${tenantId}&plateNumber=${plate}`)
  ElMessage.success('绑定成功')
  load()
}

const release = async(row)=>{
  await request.patch(`/parking/spaces/${row.id}/release`)
  ElMessage.success('已释放')
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
