<template>
  <section class="attention" aria-labelledby="dashboard-attention-heading">
    <h2 id="dashboard-attention-heading">待处理</h2>
    <div v-for="group in groups" :key="group.key" class="attention-group">
      <button
        v-if="group.records.length && !group.hint"
        type="button"
        class="attention-row"
        :aria-expanded="expanded === group.key"
        :aria-controls="`attention-${group.key}`"
        @click="expanded = expanded === group.key ? null : group.key"
      >
        <span>{{ group.title }}</span>
        <span class="attention-count">{{ group.records.length }} {{ group.unit }}</span>
        <ArrowRight class="attention-arrow" aria-hidden="true" />
      </button>
      <div v-else class="attention-row">
        <span>{{ group.title }}</span>
        <span class="attention-hint">{{ group.hint || `0 ${group.unit}` }}</span>
      </div>
      <div
        v-if="group.records.length && !group.hint"
        v-show="expanded === group.key"
        :id="`attention-${group.key}`"
        class="attention-records"
        role="region"
        :aria-label="`${group.title}明细`"
        tabindex="0"
      >
        <table v-if="expanded === group.key">
          <thead><tr><th scope="col">{{ group.numberLabel }}</th><th scope="col">租户</th><th scope="col">到期日</th></tr></thead>
          <tbody>
            <tr v-for="record in group.records" :key="record.id ?? record[group.numberKey]">
              <td>{{ record[group.numberKey] || '—' }}</td>
              <td>{{ record.tenantName?.trim() || (record.tenantId != null ? `ID ${record.tenantId}` : '—') }}</td>
              <td>{{ record[group.dateKey] || '—' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ArrowRight } from '@element-plus/icons-vue'

const props = defineProps({
  overdueBills: { type: Array, required: true },
  expiringContracts: { type: Array, required: true },
  billHint: { type: String, default: '' },
  contractHint: { type: String, default: '' }
})

const expanded = ref(null)
const groups = computed(() => [
  {
    key: 'bills', title: '逾期账单', unit: '笔', numberLabel: '账单编号',
    numberKey: 'billNumber', dateKey: 'dueDate', records: props.overdueBills, hint: props.billHint
  },
  {
    key: 'contracts', title: '30 天内到期合同', unit: '份', numberLabel: '合同编号',
    numberKey: 'contractNumber', dateKey: 'endDate', records: props.expiringContracts, hint: props.contractHint
  }
])
</script>

<style scoped>
.attention {
  min-width: 0;
}
.attention h2 {
  margin: 0 0 10px;
  font-size: 20px;
  line-height: 1.35;
  font-weight: 650;
  color: var(--dhic-text-primary);
}
.attention-group {
  border-bottom: 1px solid var(--dhic-border-color-light);
}
.attention-row {
  box-sizing: border-box;
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  min-height: 48px;
  padding: 8px 8px 8px 0;
  border: 0;
  border-radius: 3px;
  background: transparent;
  color: var(--dhic-text-primary);
  text-align: start;
  font: inherit;
  font-size: 15px;
}
button.attention-row {
  cursor: pointer;
}
button.attention-row:hover, button.attention-row:active {
  background: var(--el-fill-color-light);
}
button.attention-row:focus-visible, .attention-records:focus-visible {
  outline: 2px solid var(--dhic-focus-ring);
  outline-offset: -2px;
}
.attention-count, .attention-hint {
  margin-inline-start: auto;
  text-align: end;
  font-variant-numeric: tabular-nums;
}
.attention-count { font-weight: 600; }
.attention-hint { color: var(--dhic-text-secondary); font-size: 14px; }
.attention-arrow {
  flex: 0 0 12px;
  width: 12px;
  height: 12px;
  color: var(--dhic-text-secondary);
  transition: transform var(--dhic-motion-feedback) var(--dhic-ease);
}
[aria-expanded="true"] .attention-arrow { transform: rotate(90deg); }
.attention-records {
  max-height: 230px;
  overflow: auto;
  margin-bottom: 8px;
}
table {
  width: 100%;
  border-spacing: 0;
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}
th, td {
  padding: 8px;
  text-align: start;
  border-bottom: 1px solid var(--dhic-border-color-light);
}
th {
  position: sticky;
  top: 0;
  color: var(--dhic-text-regular);
  background: var(--el-fill-color-light);
  font-weight: 500;
}
td:first-child { overflow-wrap: anywhere; }
td:last-child, th { white-space: nowrap; }
@media (prefers-reduced-motion: reduce) {
  .attention-arrow { transition: none; }
}
</style>
