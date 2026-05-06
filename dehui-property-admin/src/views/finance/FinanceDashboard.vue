<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="page-header">
          <div>
            <div class="page-title">财务看板</div>
            <div class="page-subtitle">基于账单数据统计应收、实收、未收与逾期情况</div>
          </div>
          <el-button type="primary" @click="loadBills">刷新</el-button>
        </div>
      </template>

      <el-row :gutter="16" class="summary-row">
        <el-col :span="6">
          <el-card class="summary-card" shadow="never">
            <div class="summary-label">应收金额</div>
            <div class="summary-value">¥ {{ totalAmount }}</div>
          </el-card>
        </el-col>

        <el-col :span="6">
          <el-card class="summary-card" shadow="never">
            <div class="summary-label">实收金额</div>
            <div class="summary-value">¥ {{ paidAmount }}</div>
          </el-card>
        </el-col>

        <el-col :span="6">
          <el-card class="summary-card" shadow="never">
            <div class="summary-label">未收金额</div>
            <div class="summary-value">¥ {{ unpaidAmount }}</div>
          </el-card>
        </el-col>

        <el-col :span="6">
          <el-card class="summary-card" shadow="never">
            <div class="summary-label">逾期金额</div>
            <div class="summary-value danger">¥ {{ overdueAmount }}</div>
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-card shadow="never">
            <template #header>账单状态统计</template>
            <el-table :data="statusStats" border stripe v-loading="loading">
              <el-table-column prop="statusName" label="状态" />
              <el-table-column prop="count" label="数量" width="100" align="center" />
              <el-table-column prop="amount" label="金额" width="160" align="right" />
            </el-table>
          </el-card>
        </el-col>

        <el-col :span="12">
          <el-card shadow="never">
            <template #header>费用类型统计</template>
            <el-table :data="typeStats" border stripe v-loading="loading">
              <el-table-column prop="billTypeName" label="费用类型" />
              <el-table-column prop="count" label="数量" width="100" align="center" />
              <el-table-column prop="amount" label="金额" width="160" align="right" />
            </el-table>
          </el-card>
        </el-col>
      </el-row>

      <el-card shadow="never" class="table-card">
        <template #header>租户财务汇总</template>
        <el-table :data="tenantStats" border stripe v-loading="loading">
          <el-table-column prop="tenantId" label="租户ID" width="100" align="center" />
          <el-table-column prop="billCount" label="账单数" width="100" align="center" />
          <el-table-column prop="totalAmount" label="应收金额" width="160" align="right" />
          <el-table-column prop="paidAmount" label="实收金额" width="160" align="right" />
          <el-table-column prop="unpaidAmount" label="未收金额" width="160" align="right" />
          <el-table-column prop="overdueAmount" label="逾期金额" width="160" align="right" />
        </el-table>
      </el-card>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../../utils/request'

const loading = ref(false)
const bills = ref([])

function unwrap(res) {
  if (res?.code === 200) return res.data
  if (res?.data?.code === 200) return res.data.data
  if (Array.isArray(res)) return res
  if (Array.isArray(res?.data)) return res.data
  return res?.data ?? res
}

async function loadBills() {
  loading.value = true
  try {
    const res = await request.get('/bills')
    bills.value = unwrap(res) || []
  } catch (e) {
    ElMessage.error(e.message || '加载财务看板数据失败')
  } finally {
    loading.value = false
  }
}

const totalAmount = computed(() => money(sum(bills.value, 'amount')))

const paidAmount = computed(() => {
  return money(bills.value.reduce((total, item) => {
    if (item.status === 'PAID') return total + Number(item.amount || 0)
    return total + Number(item.paidAmount || 0)
  }, 0))
})

const unpaidAmount = computed(() => {
  return money(bills.value.reduce((total, item) => {
    if (item.status !== 'PAID') {
      return total + Number(item.amount || 0) - Number(item.paidAmount || 0)
    }
    return total
  }, 0))
})

const overdueAmount = computed(() => {
  return money(bills.value.reduce((total, item) => {
    if (isOverdue(item)) {
      return total + Number(item.amount || 0) - Number(item.paidAmount || 0)
    }
    return total
  }, 0))
})

const statusStats = computed(() => {
  const map = new Map()
  bills.value.forEach(item => {
    const key = item.status || 'UNKNOWN'
    if (!map.has(key)) {
      map.set(key, { statusName: formatStatus(key), count: 0, amount: 0 })
    }
    const row = map.get(key)
    row.count += 1
    row.amount += Number(item.amount || 0)
  })
  return Array.from(map.values()).map(item => ({ ...item, amount: money(item.amount) }))
})

const typeStats = computed(() => {
  const map = new Map()
  bills.value.forEach(item => {
    const key = item.billType || 'OTHER'
    if (!map.has(key)) {
      map.set(key, { billTypeName: formatBillType(key), count: 0, amount: 0 })
    }
    const row = map.get(key)
    row.count += 1
    row.amount += Number(item.amount || 0)
  })
  return Array.from(map.values()).map(item => ({ ...item, amount: money(item.amount) }))
})

const tenantStats = computed(() => {
  const map = new Map()
  bills.value.forEach(item => {
    const key = item.tenantId || '-'
    if (!map.has(key)) {
      map.set(key, {
        tenantId: key,
        billCount: 0,
        totalAmount: 0,
        paidAmount: 0,
        unpaidAmount: 0,
        overdueAmount: 0
      })
    }

    const row = map.get(key)
    const amount = Number(item.amount || 0)
    const paid = item.status === 'PAID' ? amount : Number(item.paidAmount || 0)
    const unpaid = item.status !== 'PAID' ? amount - paid : 0

    row.billCount += 1
    row.totalAmount += amount
    row.paidAmount += paid
    row.unpaidAmount += unpaid
    if (isOverdue(item)) row.overdueAmount += unpaid
  })

  return Array.from(map.values()).map(item => ({
    ...item,
    totalAmount: money(item.totalAmount),
    paidAmount: money(item.paidAmount),
    unpaidAmount: money(item.unpaidAmount),
    overdueAmount: money(item.overdueAmount)
  }))
})

function sum(list, field) {
  return list.reduce((total, item) => total + Number(item[field] || 0), 0)
}

function money(value) {
  return Number(value || 0).toFixed(2)
}

function isOverdue(item) {
  if (item.status === 'PAID') return false
  if (!item.dueDate) return false
  return String(item.dueDate) < today()
}

function today() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function formatStatus(status) {
  return {
    UNPAID: '未支付',
    PAID: '已支付',
    OVERDUE: '逾期',
    CANCELLED: '已取消'
  }[status] || status || '-'
}

function formatBillType(type) {
  return {
    RENT: '租金',
    PROPERTY: '物业费',
    PARKING: '停车费',
    ENERGY: '能耗费',
    OTHER: '其他'
  }[type] || type || '-'
}

onMounted(loadBills)
</script>

<style scoped>
.page-container {
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
}

.page-subtitle {
  margin-top: 6px;
  font-size: 13px;
  color: #909399;
}

.summary-row {
  margin-bottom: 16px;
}

.summary-card {
  height: 100px;
}

.summary-label {
  color: #909399;
  font-size: 13px;
}

.summary-value {
  margin-top: 14px;
  font-size: 26px;
  font-weight: 700;
  color: #1f1b1b;
}

.summary-value.danger {
  color: #d93025;
}

.table-card {
  margin-top: 16px;
}
</style>
