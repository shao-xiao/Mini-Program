<template>
  <el-drawer
    :model-value="modelValue"
    title="账单详情"
    size="min(620px, 92vw)"
    destroy-on-close
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <div v-if="loading" class="detail-state" role="status" aria-busy="true">
      <p>正在加载账单详情</p>
      <el-skeleton :rows="4" />
    </div>
    <div v-else-if="errorText" class="detail-state detail-state--error" role="alert">
      <p>{{ errorText }}</p>
      <el-button plain @click="$emit('retry')">重新加载详情</el-button>
    </div>
    <div v-else-if="bill" class="bill-detail">
      <dl class="amount-strip" aria-label="账单收款金额">
        <div><dt>最终金额</dt><dd>{{ money(bill.finalAmount ?? bill.amount) }}</dd></div>
        <div><dt>已收金额</dt><dd>{{ money(bill.paidAmount) }}</dd></div>
        <div><dt>未收金额</dt><dd>{{ money(unpaidAmount) }}</dd></div>
      </dl>
      <section>
        <h3>基本信息</h3>
        <dl class="detail-grid">
          <div><dt>账单编号</dt><dd>{{ text(bill.billNumber || bill.code) }}</dd></div>
          <div><dt>账单标题</dt><dd>{{ text(bill.title) }}</dd></div>
          <div><dt>租户</dt><dd>{{ text(bill.tenantName) }}</dd></div>
          <div><dt>合同编号</dt><dd>{{ text(bill.contractNumber) }}</dd></div>
          <div><dt>账单类型</dt><dd>{{ formatBillType(bill.billType) }}</dd></div>
          <div><dt>来源</dt><dd>{{ optionLabel(billSourceTypeOptions, bill.sourceType) }}</dd></div>
          <div><dt>账期</dt><dd>{{ periodText }}</dd></div>
          <div><dt>到期日</dt><dd>{{ text(bill.dueDate) }}</dd></div>
        </dl>
      </section>

      <section>
        <h3>计费与状态</h3>
        <dl class="detail-grid">
          <div><dt>计算金额</dt><dd>{{ money(bill.calculatedTotal) }}</dd></div>
          <div><dt>调整金额</dt><dd>{{ signedMoney(bill.adjustmentTotal) }}</dd></div>
          <div v-for="state in statuses" :key="state.name">
            <dt>{{ state.name }}</dt>
            <dd>
              <StatusDisplay v-if="state.value" :value="state.value" :label="state.option?.label" :tone="state.option?.tone" />
              <span v-else>—</span>
            </dd>
          </div>
        </dl>
      </section>

      <section>
        <h3>费用明细与来源快照</h3>
        <el-table v-if="bill.lineItems?.length" :data="bill.lineItems" size="small" border>
          <el-table-column prop="description" label="费用说明" min-width="150" />
          <el-table-column prop="chargeItemType" label="费用类型" width="115">
            <template #default="{ row }">{{ formatBillType(row.chargeItemType) }}</template>
          </el-table-column>
          <el-table-column prop="calculatedAmount" label="计算金额" width="105" align="right" :formatter="formatAmountCell" />
          <el-table-column prop="adjustmentAmount" label="调整金额" width="105" align="right" :formatter="formatAmountCell" />
          <el-table-column prop="finalAmount" label="最终金额" width="105" align="right" :formatter="formatAmountCell" />
          <el-table-column prop="adjustmentReason" label="调整原因" min-width="140" />
        </el-table>
        <el-empty v-else description="暂无费用明细" :image-size="32" />
        <p class="remark">备注：{{ text(bill.remark) }}</p>
      </section>

      <section>
        <h3>发票版本</h3>
        <el-table v-if="bill.invoices?.length" :data="bill.invoices" size="small" border>
          <el-table-column prop="versionNo" label="版本" width="70" />
          <el-table-column prop="invoiceNumber" label="发票号码" min-width="150" />
          <el-table-column prop="amount" label="金额" width="100" align="right" :formatter="formatAmountCell" />
          <el-table-column prop="invoiceStatus" label="状态" width="110">
            <template #default="{ row }">{{ optionLabel(billInvoiceStatusOptions, row.invoiceStatus) }}</template>
          </el-table-column>
          <el-table-column prop="invoiceFileName" label="附件" min-width="150" />
        </el-table>
        <el-empty v-else description="暂无发票" :image-size="32" />
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
        <el-empty v-else description="暂无审计记录" :image-size="32" />
      </section>
    </div>
  </el-drawer>
</template>

<script setup>
import { computed } from 'vue'
import StatusDisplay from '../../../components/StatusDisplay.vue'
import {
  billAuditStatusOptions,
  billInvoiceStatusOptions,
  billPayStatusOptions,
  billSourceTypeOptions,
  formatBillType,
  billWorkflowStatusOptions
} from '../../../workspaceConfigs/financeWorkspaceConfig.js'

defineOptions({ name: 'BillDetailDrawer' })

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  bill: { type: Object, default: null },
  loading: { type: Boolean, default: false },
  errorText: { type: String, default: '' }
})

defineEmits(['update:modelValue', 'retry'])

const statuses = computed(() => {
  const bill = props.bill || {}
  return [
    ['账单状态', billWorkflowStatusOptions, bill.workflowStatus],
    ['审核状态', billAuditStatusOptions, bill.auditStatus],
    ['支付状态', billPayStatusOptions, bill.payStatus || bill.status],
    ['发票状态', billInvoiceStatusOptions, bill.invoiceStatus]
  ].map(([name, options, value]) => ({ name, value, option: options.find(item => item.value === value) }))
})

const unpaidAmount = computed(() => {
  const bill = props.bill
  if (!bill) return null
  if (bill.unpaidAmount != null) return bill.unpaidAmount
  const amount = bill.finalAmount ?? bill.amount
  if (amount == null || bill.paidAmount == null) return null
  return Math.max(0, Number(amount) - Number(bill.paidAmount))
})

const periodText = computed(() => {
  if (!props.bill) return '-'
  return [props.bill.periodStart, props.bill.periodEnd].filter(Boolean).join(' 至 ') || '-'
})

function optionLabel(options, value) {
  return options.find(item => item.value === value)?.label || text(value)
}

function formatAmountCell(_row, _column, value) {
  return money(value)
}

function text(value) {
  return value === undefined || value === null || value === '' ? '-' : value
}

function money(value) {
  if (value == null || value === '' || !Number.isFinite(Number(value))) return '—'
  return `¥${Number(value).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })}`
}

function signedMoney(value) {
  return `${Number(value) > 0 ? '+' : ''}${money(value)}`
}
</script>

<style scoped>
.bill-detail {
  display: grid;
  gap: 16px;
  font-variant-numeric: tabular-nums;
}

.amount-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin: 0;
  padding: 12px 0;
  border: 1px solid var(--dhic-border);
  border-radius: var(--dhic-radius-base);
  background: var(--dhic-page-bg);
}

.amount-strip > div {
  min-width: 0;
  padding: 0 12px;
}

.amount-strip > div + div {
  border-inline-start: 1px solid var(--dhic-border);
}

.amount-strip dt,
.detail-state {
  color: var(--dhic-text-secondary);
}

.amount-strip dt {
  font-size: 12px;
}

.amount-strip dd {
  margin: 6px 0 0;
  font-size: clamp(0.875rem, 1.35vw, 1.125rem);
  font-weight: 650;
  color: var(--dhic-text);
  overflow-wrap: anywhere;
}

.detail-state--error {
  color: var(--dhic-danger);
}

.bill-detail :deep(.el-empty) {
  padding-block: 12px;
}

.bill-detail :deep(.el-empty__description) {
  margin-top: 6px;
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
  gap: 10px 18px;
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
