<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>停车账单</span>
          <el-button type="primary" :loading="syncing" @click="syncBills">
            生成并同步到账单中心
          </el-button>
        </div>
      </template>

      <el-form :inline="true" :model="query" class="toolbar">
        <el-form-item label="账期">
          <el-date-picker
            v-model="query.month"
            type="month"
            value-format="YYYY-MM"
            placeholder="选择月份"
            style="width: 150px"
            @change="load"
          />
        </el-form-item>
        <el-form-item label="关键词">
          <el-input
            v-model.trim="query.keyword"
            clearable
            placeholder="账单/车位/使用方/车牌"
            style="width: 220px"
            @keyup.enter="load"
          />
        </el-form-item>
        <el-form-item label="缴费状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 140px">
            <el-option label="未支付" value="UNPAID" />
            <el-option label="已支付" value="PAID" />
            <el-option label="已作废" value="VOID" />
          </el-select>
        </el-form-item>
        <el-form-item label="同步状态">
          <el-select v-model="query.syncStatus" clearable placeholder="全部" style="width: 140px">
            <el-option label="未同步" value="unsynced" />
            <el-option label="已同步" value="synced" />
            <el-option label="同步失败" value="failed" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="bills" border style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="账单编号" min-width="170">
          <template #default="{ row }">
            {{ row.billNo || row.billNumber || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="spaceNoSnapshot" label="车位" width="120" />
        <el-table-column label="使用方" min-width="180">
          <template #default="{ row }">
            {{ row.partyNameSnapshot || '-' }}
            <span v-if="row.partyTypeText" class="muted">（{{ row.partyTypeText }}）</span>
          </template>
        </el-table-column>
        <el-table-column prop="plateNoSnapshot" label="车牌号" width="130" />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            {{ row.billTypeText || billTypeText(row.billType) }}
          </template>
        </el-table-column>
        <el-table-column label="账期" min-width="180">
          <template #default="{ row }">
            {{ row.periodStart || '-' }} 至 {{ row.periodEnd || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="金额" width="120">
          <template #default="{ row }">
            {{ formatMoney(row.amount) }}
          </template>
        </el-table-column>
        <el-table-column label="缴费状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)">
              {{ row.statusText || statusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="同步状态" width="120">
          <template #default="{ row }">
            <el-tag :type="syncStatusTag(row.syncStatus)">
              {{ row.syncStatusText || syncStatusText(row.syncStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="失败原因" min-width="160">
          <template #default="{ row }">
            <span class="error-text">{{ row.syncError || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button v-if="canPay(row)" size="small" type="success" @click="pay(row)">
              收款
            </el-button>
            <el-button v-else size="small" disabled>
              已处理
            </el-button>
            <el-button v-if="canVoid(row)" size="small" type="danger" plain @click="voidBill(row)">
              作废
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listParkingBills,
  payParkingBill,
  syncParkingBills,
  voidParkingBill
} from '../../api/parking'

const route = useRoute()
const bills = ref([])
const loading = ref(false)
const syncing = ref(false)

const query = reactive({
  month: currentMonth(),
  keyword: routeKeyword(),
  status: '',
  syncStatus: ''
})

async function load() {
  loading.value = true
  try {
    bills.value = await listParkingBills(compactParams({ ...query }))
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  query.month = currentMonth()
  query.keyword = ''
  query.status = ''
  query.syncStatus = ''
  load()
}

async function syncBills() {
  if (!query.month) {
    ElMessage.warning('请先选择账期')
    return
  }

  syncing.value = true
  try {
    const result = await syncParkingBills({ month: query.month })
    const summary = syncSummary(result)
    if (Number(result?.failedCount || 0) > 0) {
      ElMessage.warning(summary)
    } else {
      ElMessage.success(summary)
    }
    await load()
  } finally {
    syncing.value = false
  }
}

async function pay(row) {
  try {
    await ElMessageBox.confirm(
      `确定收取停车账单「${row.billNo || row.billNumber}」的款项 ${formatMoney(row.amount)} 吗？`,
      '确认收款',
      { type: 'warning' }
    )
    await payParkingBill(row.id)
    ElMessage.success('收款成功')
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error?.message || '收款失败')
    }
  }
}

async function voidBill(row) {
  try {
    await ElMessageBox.confirm(
      `确定作废停车账单「${row.billNo || row.billNumber}」吗？`,
      '确认作废',
      { type: 'warning' }
    )
    await voidParkingBill(row.id)
    ElMessage.success('账单已作废')
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error?.message || '作废失败')
    }
  }
}

function canPay(row) {
  return !['PAID', 'VOID', 'CANCELLED'].includes(row.status)
}

function canVoid(row) {
  return row.status === 'UNPAID'
}

function formatMoney(value) {
  const amount = Number(value || 0)
  return `￥${amount.toFixed(2)}`
}

function billTypeText(type) {
  return {
    monthly: '月租',
    MONTHLY: '月租',
    temporary: '临停',
    TEMP: '临停'
  }[type] || type || '-'
}

function statusText(status) {
  return {
    UNPAID: '未支付',
    PAID: '已支付',
    VOID: '已作废',
    CANCELLED: '已取消'
  }[status] || status || '-'
}

function statusTag(status) {
  return {
    UNPAID: 'danger',
    PAID: 'success',
    VOID: 'info',
    CANCELLED: 'info'
  }[status] || 'warning'
}

function syncStatusText(status) {
  return {
    unsynced: '未同步',
    synced: '已同步',
    failed: '同步失败'
  }[status] || status || '-'
}

function syncStatusTag(status) {
  return {
    unsynced: 'warning',
    synced: 'success',
    failed: 'danger'
  }[status] || 'info'
}

function syncSummary(result = {}) {
  const generated = Number(result.generatedCount || 0)
  const synced = Number(result.syncedCount || 0)
  const skipped = Number(result.skippedCount || 0)
  const failed = Number(result.failedCount || 0)
  return `生成 ${generated} 条，同步 ${synced} 条，跳过 ${skipped} 条，失败 ${failed} 条`
}

function currentMonth() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

function routeKeyword() {
  return typeof route.query.keyword === 'string' ? route.query.keyword : ''
}

function compactParams(params) {
  return Object.fromEntries(
    Object.entries(params).filter(([, value]) => value !== '' && value !== null && value !== undefined)
  )
}

watch(
  () => route.query.keyword,
  () => {
    query.keyword = routeKeyword()
    load()
  }
)

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

.toolbar {
  margin-bottom: 12px;
}

.muted {
  color: #909399;
}

.error-text {
  color: #d93026;
}
</style>
