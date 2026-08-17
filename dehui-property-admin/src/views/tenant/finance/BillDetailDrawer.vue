<template>
  <el-drawer
    :model-value="modelValue"
    title="账单详情"
    size="min(620px, 92vw)"
    destroy-on-close
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <div v-if="bill" class="bill-detail">
      <section>
        <h3>基本信息</h3>
        <dl class="detail-grid">
          <div><dt>账单编号</dt><dd>{{ text(bill.billNumber || bill.code) }}</dd></div>
          <div><dt>账单标题</dt><dd>{{ text(bill.title) }}</dd></div>
          <div><dt>租户</dt><dd>{{ text(bill.tenantName) }}</dd></div>
          <div><dt>合同编号</dt><dd>{{ text(bill.contractNumber) }}</dd></div>
          <div><dt>账单类型</dt><dd>{{ text(optionLabel(bill.billType)) }}</dd></div>
          <div><dt>来源</dt><dd>{{ text(optionLabel(bill.sourceType)) }}</dd></div>
          <div><dt>账期</dt><dd>{{ periodText }}</dd></div>
          <div><dt>到期日</dt><dd>{{ text(bill.dueDate) }}</dd></div>
        </dl>
      </section>

      <section>
        <h3>金额与状态</h3>
        <dl class="detail-grid">
          <div><dt>计算金额</dt><dd>{{ money(bill.calculatedTotal) }}</dd></div>
          <div><dt>调整金额</dt><dd>{{ signedMoney(bill.adjustmentTotal) }}</dd></div>
          <div><dt>最终金额</dt><dd>{{ money(bill.finalAmount ?? bill.amount) }}</dd></div>
          <div><dt>已收金额</dt><dd>{{ money(bill.paidAmount) }}</dd></div>
          <div><dt>未收金额</dt><dd>{{ money(Number(bill.finalAmount ?? bill.amount ?? 0) - Number(bill.paidAmount || 0)) }}</dd></div>
          <div><dt>账单状态</dt><dd>{{ text(workflowLabel(bill.workflowStatus)) }}</dd></div>
          <div><dt>审核状态</dt><dd>{{ text(optionLabel(bill.auditStatus)) }}</dd></div>
          <div><dt>支付状态</dt><dd>{{ text(optionLabel(bill.status || bill.payStatus)) }}</dd></div>
          <div><dt>发票状态</dt><dd>{{ text(optionLabel(bill.invoiceStatus)) }}</dd></div>
        </dl>
      </section>

      <section>
        <h3>费用明细与来源快照</h3>
        <el-table v-if="bill.lineItems?.length" :data="bill.lineItems" size="small" border>
          <el-table-column prop="description" label="费用说明" min-width="150" />
          <el-table-column prop="chargeItemType" label="费用类型" width="115" />
          <el-table-column prop="calculatedAmount" label="计算金额" width="105" align="right" />
          <el-table-column prop="adjustmentAmount" label="调整金额" width="105" align="right" />
          <el-table-column prop="finalAmount" label="最终金额" width="105" align="right" />
          <el-table-column prop="adjustmentReason" label="调整原因" min-width="140" />
        </el-table>
        <el-empty v-else description="暂无费用明细" :image-size="54" />
        <p class="remark">备注：{{ text(bill.remark) }}</p>
      </section>

      <section>
        <h3>发票版本</h3>
        <el-table v-if="bill.invoices?.length" :data="bill.invoices" size="small" border>
          <el-table-column prop="versionNo" label="版本" width="70" />
          <el-table-column prop="invoiceNumber" label="发票号码" min-width="150" />
          <el-table-column prop="amount" label="金额" width="100" align="right" />
          <el-table-column prop="invoiceStatus" label="状态" width="110" />
          <el-table-column prop="invoiceFileName" label="附件" min-width="150" />
        </el-table>
        <el-empty v-else description="暂无发票" :image-size="54" />
      </section>

      <section>
        <h3>审计轨迹</h3>
        <el-timeline v-if="bill.auditLogs?.length">
          <el-timeline-item
            v-for="log in bill.auditLogs"
            :key="log.id"
            :timestamp="String(log.occurredAt || '')"
          >
            {{ text(log.action) }} · {{ text(log.operatorName) }}
            <div v-if="log.reason" class="audit-reason">{{ log.reason }}</div>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="暂无审计记录" :image-size="54" />
      </section>
    </div>
  </el-drawer>
</template>

<script setup>
import { computed } from 'vue'
import {
  billAuditStatusOptions,
  billInvoiceStatusOptions,
  billPayStatusOptions,
  billSourceTypeOptions,
  billTypeOptions,
  billWorkflowStatusOptions
} from '../../../workspaceConfigs/financeWorkspaceConfig.js'

defineOptions({ name: 'BillDetailDrawer' })

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  bill: { type: Object, default: null }
})

defineEmits(['update:modelValue'])

const options = [
  ...billTypeOptions,
  ...billSourceTypeOptions,
  ...billAuditStatusOptions,
  ...billPayStatusOptions,
  ...billInvoiceStatusOptions,
  ...billWorkflowStatusOptions
]

const periodText = computed(() => {
  if (!props.bill) return '-'
  return [props.bill.periodStart, props.bill.periodEnd].filter(Boolean).join(' 至 ') || '-'
})

function optionLabel(value) {
  return options.find(item => String(item.value) === String(value))?.label || value
}

function workflowLabel(value) {
  return billWorkflowStatusOptions.find(item => String(item.value) === String(value))?.label || value
}

function text(value) {
  return value === undefined || value === null || value === '' ? '-' : value
}

function money(value) {
  return `¥${Number(value || 0).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })}`
}

function signedMoney(value) {
  const number = Number(value || 0)
  return `${number > 0 ? '+' : ''}${money(number)}`
}
</script>

<style scoped>
.bill-detail {
  display: grid;
  gap: 20px;
}

.bill-detail section {
  padding-bottom: 18px;
  border-bottom: 1px solid var(--dhic-border-soft);
}

.bill-detail h3 {
  margin: 0 0 12px;
  color: var(--dhic-text);
  font-size: 14px;
  letter-spacing: 0;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 18px;
  margin: 0;
}

.detail-grid div {
  min-width: 0;
}

.detail-grid dt {
  margin-bottom: 3px;
  color: var(--dhic-text-muted);
  font-size: 12px;
}

.detail-grid dd {
  margin: 0;
  overflow-wrap: anywhere;
  color: var(--dhic-text);
  font-size: 13px;
}

.source-note,
.remark {
  margin: 0;
  color: var(--dhic-text-secondary);
  font-size: 13px;
  line-height: 1.7;
}

.remark {
  margin-top: 10px;
}

.audit-reason {
  margin-top: 3px;
  color: var(--dhic-text-secondary);
  font-size: 12px;
}

@media (max-width: 620px) {
  .detail-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
