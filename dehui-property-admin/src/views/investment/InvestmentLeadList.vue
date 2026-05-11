<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>招商线索</span>
          <el-button type="primary" @click="load">刷新</el-button>
        </div>
      </template>

      <el-table :data="leads" border>
        <el-table-column prop="id" label="线索ID" width="90" />
        <el-table-column label="联系人" min-width="150">
          <template #default="{ row }">
            <div>{{ row.name }}</div>
            <div class="sub-text">{{ row.phone }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="companyName" label="公司名称" min-width="150" />
        <el-table-column label="意向房源" min-width="120">
          <template #default="{ row }">
            {{ row.roomNumber || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="面积/用途" min-width="150">
          <template #default="{ row }">
            <div>{{ row.desiredArea ? `${row.desiredArea}㎡` : '-' }}</div>
            <div class="sub-text" v-if="row.intendedUse">{{ row.intendedUse }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="preferredVisitTime" label="期望看房时间" min-width="150" />
        <el-table-column prop="remark" label="备注" min-width="180" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">
              {{ statusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="提交时间" min-width="150">
          <template #default="{ row }">
            {{ formatDateTime(row.createdTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="190">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'NEW'"
              size="small"
              type="primary"
              @click="updateStatus(row, 'CONTACTED')"
            >
              标记已联系
            </el-button>
            <el-button
              v-if="row.status !== 'CLOSED'"
              size="small"
              @click="updateStatus(row, 'CLOSED')"
            >
              关闭
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../../utils/request'

const leads = ref([])

const statusText = (status) => {
  if (status === 'NEW') return '新线索'
  if (status === 'CONTACTED') return '已联系'
  if (status === 'CLOSED') return '已关闭'
  return status || '-'
}

const statusTagType = (status) => {
  if (status === 'NEW') return 'danger'
  if (status === 'CONTACTED') return 'warning'
  if (status === 'CLOSED') return 'info'
  return ''
}

const formatDateTime = (value) => {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

const load = async () => {
  const data = await request.get('/investment/leads')
  leads.value = data || []
}

const updateStatus = async (row, status) => {
  await request.patch(`/investment/leads/${row.id}/status`, { status })
  ElMessage.success('状态已更新')
  await load()
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

.sub-text {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
}
</style>
