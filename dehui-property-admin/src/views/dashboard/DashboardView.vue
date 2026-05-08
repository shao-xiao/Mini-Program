<template>
  <div class="dashboard-page">
    <div class="page-header">
      <h2>首页驾驶舱</h2>
      <el-button type="primary" :loading="loading" @click="loadDashboard">
        刷新数据
      </el-button>
    </div>

    <el-row :gutter="16" class="cards">
      <el-col :span="6">
        <el-card>
          <div class="card-title">本月应收</div>
          <div class="card-value">¥ {{ formatMoney(finance.monthReceivable) }}</div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card>
          <div class="card-title">本月实收</div>
          <div class="card-value success">¥ {{ formatMoney(finance.monthReceived) }}</div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card>
          <div class="card-title">未收款金额</div>
          <div class="card-value danger">¥ {{ formatMoney(finance.unpaidAmount) }}</div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card>
          <div class="card-title">本月收缴率</div>
          <div class="card-value">{{ finance.collectionRate }}%</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="summary-card">
      <template #header>
        <span>财务收缴进度</span>
      </template>
      <el-progress :percentage="finance.collectionRate" :stroke-width="18" />
      <div class="progress-desc">
        本月账单 {{ finance.monthBillCount }} 笔，已支付 {{ finance.paidBillCount }} 笔，未支付 {{ finance.unpaidBillCount }} 笔
      </div>
    </el-card>

    <el-row :gutter="16" class="cards">
      <el-col :span="6">
        <el-card>
          <div class="card-title">房间总数</div>
          <div class="card-value">{{ report.roomCount ?? '-' }}</div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card>
          <div class="card-title">已出租房间</div>
          <div class="card-value">{{ report.rentedRoomCount ?? '-' }}</div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card>
          <div class="card-title">逾期账单数</div>
          <div class="card-value danger">{{ finance.overdueBillCount }}</div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card>
          <div class="card-title">30天内到期合同</div>
          <div class="card-value warning">{{ expiringContracts.length }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="summary-card">
      <template #header>
        <span>合同到期预警</span>
      </template>

      <el-table :data="expiringContracts" border style="width: 100%">
        <el-table-column prop="contractNo" label="合同编号" min-width="150" />
        <el-table-column prop="contractName" label="合同名称" min-width="160" />
        <el-table-column prop="tenantId" label="租户ID" width="90" />
        <el-table-column prop="roomId" label="房间ID" width="90" />
        <el-table-column prop="leaseId" label="租约ID" width="90" />
        <el-table-column prop="startDate" label="开始日期" width="120" />
        <el-table-column prop="endDate" label="结束日期" width="120" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'ACTIVE'" type="success">已生效</el-tag>
            <el-tag v-else type="info">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card class="summary-card">
      <template #header>
        <span>最近账单</span>
      </template>

      <el-table :data="recentBills" border style="width: 100%">
        <el-table-column prop="billNumber" label="账单编号" min-width="170" />
        <el-table-column prop="sourceName" label="来源" width="90" />
        <el-table-column prop="tenantId" label="租户ID" width="90" />
        <el-table-column prop="contractId" label="合同ID" width="90" />
        <el-table-column prop="billType" label="类型" width="110" />
        <el-table-column prop="periodStart" label="账期开始" width="120" />
        <el-table-column prop="amount" label="应收金额" width="110" />
        <el-table-column prop="paidAmount" label="已收金额" width="110" />
        <el-table-column prop="dueDate" label="到期日" width="120" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'PAID'" type="success">已支付</el-tag>
            <el-tag v-else-if="isOverdue(row)" type="danger">已逾期</el-tag>
            <el-tag v-else-if="row.status === 'UNPAID'" type="warning">未支付</el-tag>
            <el-tag v-else type="info">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card class="summary-card">
      <template #header>
        <span>租户财务汇总</span>
      </template>

      <el-table :data="tenantFinanceList" border style="width: 100%">
        <el-table-column prop="tenantId" label="租户ID" width="100" />
        <el-table-column prop="billCount" label="账单数" width="100" />
        <el-table-column label="应收总额" width="140">
          <template #default="{ row }">
            ¥ {{ formatMoney(row.totalAmount) }}
          </template>
        </el-table-column>
        <el-table-column label="已收总额" width="140">
          <template #default="{ row }">
            ¥ {{ formatMoney(row.paidAmount) }}
          </template>
        </el-table-column>
        <el-table-column label="未收金额" width="140">
          <template #default="{ row }">
            <span :class="{ danger: row.unpaidAmount > 0 }">
              ¥ {{ formatMoney(row.unpaidAmount) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="unpaidBillCount" label="未支付账单" width="120" />
        <el-table-column prop="overdueBillCount" label="逾期账单" width="120" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.overdueBillCount > 0" type="danger">有逾期</el-tag>
            <el-tag v-else-if="row.unpaidAmount > 0" type="warning">有欠费</el-tag>
            <el-tag v-else type="success">正常</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card class="summary-card">
      <template #header>
        <span>AI运营摘要</span>
      </template>
      <p>{{ report.summary || '暂无数据' }}</p>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import request from '../../utils/request'

const loading = ref(false)
const report = ref({})
const bills = ref([])
const parkingBills = ref([])
const contracts = ref([])

const currentMonth = new Date().toISOString().slice(0, 7)

function money(value) {
  const n = Number(value || 0)
  return Number.isFinite(n) ? n : 0
}

function formatMoney(value) {
  return money(value).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })
}

const expiringContracts = computed(() => {
  const today = new Date()
  const todayText = today.toISOString().slice(0, 10)

  const limit = new Date(today)
  limit.setDate(limit.getDate() + 30)
  const limitText = limit.toISOString().slice(0, 10)

  return contracts.value
    .filter(item => {
      return item.status === 'ACTIVE'
        && item.endDate
        && String(item.endDate) >= todayText
        && String(item.endDate) <= limitText
    })
    .sort((a, b) => String(a.endDate || '').localeCompare(String(b.endDate || '')))
})

const monthBills = computed(() => {
  return allBills.value.filter(item => {
    return String(item.periodStart || '').startsWith(currentMonth)
  })
})

const allBills = computed(() => {
  return [
    ...bills.value.map(normalizeBill),
    ...parkingBills.value.map(normalizeParkingBill)
  ]
})

function normalizeBill(item) {
  return {
    ...item,
    source: 'RENT',
    sourceName: '租赁',
    paidAmount: money(item.paidAmount)
  }
}

function normalizeParkingBill(item) {
  const amount = money(item.amount)

  return {
    ...item,
    source: 'PARKING',
    sourceName: '停车',
    contractId: '-',
    paidAmount: item.status === 'PAID' ? amount : 0
  }
}

function isOverdue(row) {
  if (!row || row.status === 'PAID' || !row.dueDate) {
    return false
  }

  const today = new Date().toISOString().slice(0, 10)
  return String(row.dueDate) < today
}

const recentBills = computed(() => {
  return [...allBills.value]
    .sort((a, b) => String(b.createdTime || '').localeCompare(String(a.createdTime || '')))
    .slice(0, 8)
})

const tenantFinanceList = computed(() => {
  const map = new Map()

  allBills.value.forEach(item => {
    const tenantId = item.tenantId || '未知'

    if (!map.has(tenantId)) {
      map.set(tenantId, {
        tenantId,
        billCount: 0,
        totalAmount: 0,
        paidAmount: 0,
        unpaidAmount: 0,
        unpaidBillCount: 0,
        overdueBillCount: 0
      })
    }

    const row = map.get(tenantId)
    const amount = money(item.amount)
    const paid = money(item.paidAmount)

    row.billCount += 1
    row.totalAmount += amount
    row.paidAmount += paid
    row.unpaidAmount += Math.max(amount - paid, 0)

    if (item.status !== 'PAID') {
      row.unpaidBillCount += 1
    }

    if (isOverdue(item)) {
      row.overdueBillCount += 1
    }
  })

  return Array.from(map.values()).sort((a, b) => b.unpaidAmount - a.unpaidAmount)
})

const finance = computed(() => {
  const monthReceivable = monthBills.value.reduce((sum, item) => sum + money(item.amount), 0)
  const monthReceived = monthBills.value.reduce((sum, item) => sum + money(item.paidAmount), 0)
  const unpaidAmount = allBills.value.reduce((sum, item) => {
    if (item.status === 'PAID' || item.status === 'CANCELLED') {
      return sum
    }

    return sum + Math.max(money(item.amount) - money(item.paidAmount), 0)
  }, 0)

  const monthBillCount = monthBills.value.length
  const paidBillCount = monthBills.value.filter(item => item.status === 'PAID').length
  const unpaidBillCount = monthBills.value.filter(item => item.status !== 'PAID').length
  const overdueBills = allBills.value.filter(item => isOverdue(item))
  const overdueBillCount = overdueBills.length

  const collectionRate = monthReceivable > 0
    ? Math.round((monthReceived / monthReceivable) * 100)
    : 0

  return {
    monthReceivable,
    monthReceived,
    unpaidAmount,
    collectionRate,
    monthBillCount,
    paidBillCount,
    unpaidBillCount,
    overdueBillCount
  }
})

async function loadReport() {
  try {
    const data = await request.get('/ai/daily-report', { silent: true })
    report.value = data || {}
  } catch {
    report.value = {}
  }
}

async function loadBills() {
  try {
    const data = await request.get('/bills', { silent: true })
    bills.value = Array.isArray(data) ? data : []
  } catch {
    bills.value = []
  }
}

async function loadParkingBills() {
  try {
    const data = await request.get('/parking/bills', { silent: true })
    parkingBills.value = Array.isArray(data) ? data : []
  } catch {
    parkingBills.value = []
  }
}

async function loadContracts() {
  try {
    const data = await request.get('/contracts', { silent: true })
    contracts.value = Array.isArray(data) ? data : []
  } catch {
    contracts.value = []
  }
}

async function loadDashboard() {
  loading.value = true

  try {
    await Promise.allSettled([
      loadReport(),
      loadBills(),
      loadParkingBills(),
      loadContracts()
    ])
  } finally {
    loading.value = false
  }
}

onMounted(loadDashboard)
</script>

<style scoped>
.dashboard-page {
  padding: 4px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.page-header h2 {
  margin: 0;
}

.cards {
  margin-bottom: 20px;
}

.card-title {
  color: #666;
  font-size: 14px;
}

.card-value {
  margin-top: 10px;
  font-size: 26px;
  font-weight: bold;
}

.success {
  color: #67c23a;
}

.danger {
  color: #f56c6c;
}

.warning {
  color: #e6a23c;
}

.summary-card {
  margin-bottom: 20px;
}

.progress-desc {
  margin-top: 12px;
  color: #666;
}
</style>
