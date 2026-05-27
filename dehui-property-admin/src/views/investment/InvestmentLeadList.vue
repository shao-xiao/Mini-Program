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
        <el-table-column label="来源" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.source === 'MINIPROGRAM'" type="success">小程序</el-tag>
            <el-tag v-else type="info">{{ row.source || '后台' }}</el-tag>
          </template>
        </el-table-column>
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
        <el-table-column prop="preferredVisitTime" label="期望看房时间" min-width="150" show-overflow-tooltip />
        <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
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
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <div class="lead-actions">
              <el-button v-if="row.status === 'NEW'" size="small" type="primary" @click="updateStatus(row, 'FOLLOWING')">跟进</el-button>
              <el-button v-if="row.status !== 'CONVERTED'" size="small" @click="updateStatus(row, 'VIEWED')">已看房</el-button>
              <el-button v-if="row.status !== 'CONVERTED'" size="small" @click="convertTenant(row)">转租户</el-button>
              <el-button v-if="row.status !== 'CONVERTED'" size="small" type="success" @click="convertContract(row)">转合同</el-button>
              <el-button v-if="!['CONVERTED', 'INVALID'].includes(row.status)" size="small" @click="updateStatus(row, 'INVALID')">无效</el-button>
            </div>
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
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../../utils/request'
import { createPagination, pageParams, readPage, resetToFirstPage } from '../../utils/pagination'

const leads = ref([])
const pagination = reactive(createPagination(20))

const statusText = (status) => {
  if (status === 'NEW') return '新线索'
  if (status === 'FOLLOWING') return '跟进中'
  if (status === 'VIEWED') return '已看房'
  if (status === 'NEGOTIATING') return '洽谈中'
  if (status === 'CONVERTED') return '已转化'
  if (status === 'INVALID') return '无效'
  if (status === 'CONTACTED') return '跟进中'
  if (status === 'CLOSED') return '无效'
  return status || '-'
}

const statusTagType = (status) => {
  if (status === 'NEW') return 'danger'
  if (['FOLLOWING', 'VIEWED', 'NEGOTIATING', 'CONTACTED'].includes(status)) return 'warning'
  if (status === 'CONVERTED') return 'success'
  if (['INVALID', 'CLOSED'].includes(status)) return 'info'
  return ''
}

const formatDateTime = (value) => {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

const load = async () => {
  const data = await request.get('/investment/leads', { params: pageParams(pagination) })
  const page = readPage(data)
  leads.value = page.records
  pagination.total = page.total
}

const handleSizeChange = () => {
  resetToFirstPage(pagination)
  load()
}

const updateStatus = async (row, status) => {
  await request.patch(`/investment/leads/${row.id}/status`, { status })
  ElMessage.success('状态已更新')
  await load()
}

const convertTenant = async (row) => {
  await request.post(`/investment/leads/${row.id}/convert-tenant`)
  ElMessage.success('已转为租户')
  await load()
}

const convertContract = async (row) => {
  await request.post(`/investment/leads/${row.id}/convert-contract`)
  ElMessage.success('已生成合同草稿')
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

.lead-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.lead-actions :deep(.el-button) {
  min-width: 56px;
  margin-left: 0;
  padding-left: 10px;
  padding-right: 10px;
}
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
