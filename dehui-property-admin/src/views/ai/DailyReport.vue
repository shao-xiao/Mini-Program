<template>
  <div class="daily-report-page">
    <div class="page-header">
      <div>
        <h2>AI运营日报</h2>
        <p>{{ report.reportDate || '今日' }} · {{ generatedTime }}</p>
      </div>
      <el-button type="primary" :loading="loading" @click="loadReport">
        刷新日报
      </el-button>
    </div>

    <el-card class="summary-card">
      <template #header>
        <span>运营摘要</span>
      </template>
      <p>{{ report.summary || '暂无日报数据' }}</p>
    </el-card>

    <el-row :gutter="16" class="cards">
      <el-col :span="6">
        <el-card>
          <div class="card-title">房间总数</div>
          <div class="card-value">{{ valueOf('roomCount') }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card>
          <div class="card-title">已出租房间</div>
          <div class="card-value success">{{ valueOf('rentedRoomCount') }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card>
          <div class="card-title">可租房间</div>
          <div class="card-value">{{ valueOf('availableRoomCount') }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card>
          <div class="card-title">活跃合同</div>
          <div class="card-value">{{ valueOf('activeContractCount') }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="cards">
      <el-col :span="8">
        <el-card>
          <template #header>
            <span>财务收缴</span>
          </template>
          <div class="metric-row">
            <span>已支付账单</span>
            <strong>{{ valueOf('paidBillCount') }}</strong>
          </div>
          <div class="metric-row">
            <span>未支付账单</span>
            <strong class="danger">{{ valueOf('unpaidBillCount') }}</strong>
          </div>
          <div class="metric-row">
            <span>今日收款</span>
            <strong>¥ {{ formatMoney(report.todayPaidAmount) }}</strong>
          </div>
        </el-card>
      </el-col>

      <el-col :span="8">
        <el-card>
          <template #header>
            <span>工单运营</span>
          </template>
          <div class="metric-row">
            <span>今日新增</span>
            <strong>{{ valueOf('todayNewWorkOrderCount') }}</strong>
          </div>
          <div class="metric-row">
            <span>处理中</span>
            <strong class="warning">{{ valueOf('processingWorkOrderCount') }}</strong>
          </div>
          <div class="metric-row">
            <span>高优先级</span>
            <strong class="danger">{{ valueOf('highPriorityWorkOrderCount') }}</strong>
          </div>
        </el-card>
      </el-col>

      <el-col :span="8">
        <el-card>
          <template #header>
            <span>设备巡检</span>
          </template>
          <div class="metric-row">
            <span>设备总数</span>
            <strong>{{ valueOf('equipmentTotalCount') }}</strong>
          </div>
          <div class="metric-row">
            <span>故障设备</span>
            <strong class="danger">{{ valueOf('faultEquipmentCount') }}</strong>
          </div>
          <div class="metric-row">
            <span>异常巡检</span>
            <strong class="warning">{{ valueOf('abnormalInspectionCount') }}</strong>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import request from '../../utils/request'

const loading = ref(false)
const report = ref({})

const generatedTime = computed(() => {
  if (!report.value.generatedTime) {
    return '尚未生成'
  }

  return String(report.value.generatedTime).replace('T', ' ').slice(0, 19)
})

function valueOf(key) {
  return report.value[key] ?? '-'
}

function formatMoney(value) {
  const n = Number(value || 0)
  return Number.isFinite(n)
    ? n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
    : '0.00'
}

async function loadReport() {
  loading.value = true

  try {
    report.value = await request.get('/ai/daily-report')
  } finally {
    loading.value = false
  }
}

onMounted(loadReport)
</script>

<style scoped>
.daily-report-page {
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

.page-header p {
  margin: 6px 0 0;
  color: #777;
}

.summary-card,
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

.metric-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid #f0f0f0;
}

.metric-row:last-child {
  border-bottom: none;
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
</style>
