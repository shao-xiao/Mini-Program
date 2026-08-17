<template>
  <section
    v-if="summary.total"
    class="contract-lifecycle-notice"
    aria-label="待生效合同提示"
  >
    <div class="notice-copy">
      <strong>{{ summary.total }} 份合同尚未生效</strong>
      <span>租赁摘要仅统计已审批生效且在当前租期内的合同。</span>
    </div>

    <div class="notice-statuses" aria-label="待处理合同状态">
      <span v-if="summary.draft">草稿 {{ summary.draft }}</span>
      <span v-if="summary.pending">待审批 {{ summary.pending }}</span>
      <span v-if="summary.suspended">已暂停 {{ summary.suspended }}</span>
    </div>

    <button type="button" class="notice-action" @click="$emit('select-contract', summary.first)">
      处理合同
    </button>
  </section>
</template>

<script setup>
import { computed } from 'vue'

defineOptions({ name: 'TenantContractLifecycleNotice' })

const props = defineProps({
  contracts: { type: Array, default: () => [] }
})

defineEmits(['select-contract'])

const summary = computed(() => {
  const actionable = props.contracts.filter(contract =>
    ['DRAFT', 'PENDING', 'PENDING_APPROVAL', 'SUSPENDED'].includes(contract?.status)
  )

  return {
    total: actionable.length,
    draft: actionable.filter(contract => contract.status === 'DRAFT').length,
    pending: actionable.filter(contract => ['PENDING', 'PENDING_APPROVAL'].includes(contract.status)).length,
    suspended: actionable.filter(contract => contract.status === 'SUSPENDED').length,
    first: actionable.find(contract => contract.status === 'DRAFT')
      || actionable.find(contract => ['PENDING', 'PENDING_APPROVAL'].includes(contract.status))
      || actionable[0]
  }
})
</script>

<style scoped>
.contract-lifecycle-notice {
  display: flex;
  align-items: center;
  gap: 14px;
  min-height: 46px;
  border-top: 1px solid #e5e8ed;
  padding: 7px 12px;
  background: #fafbfc;
  color: #35383d;
}

.notice-copy {
  display: grid;
  flex: 1 1 auto;
  min-width: 220px;
  gap: 2px;
}

.notice-copy strong {
  font-size: 13px;
  font-weight: 650;
}

.notice-copy span {
  color: #6b7280;
  font-size: 12px;
  line-height: 1.5;
}

.notice-statuses {
  display: flex;
  flex: 0 0 auto;
  flex-wrap: wrap;
  gap: 6px;
}

.notice-statuses span {
  border: 1px solid #d9dde3;
  border-radius: 4px;
  padding: 2px 7px;
  background: #fff;
  color: #50545a;
  font-size: 11px;
  font-weight: 600;
}

.notice-action {
  flex: 0 0 auto;
  border: 0;
  padding: 5px 4px;
  background: transparent;
  color: #35383d;
  font: inherit;
  font-size: 12px;
  font-weight: 650;
  cursor: pointer;
}

.notice-action::after {
  margin-left: 3px;
  content: '›';
}

.notice-action:hover {
  color: var(--dhic-primary);
}

.notice-action:focus-visible {
  border-radius: 3px;
  outline: 2px solid var(--dhic-primary);
  outline-offset: 2px;
}

@media (max-width: 760px) {
  .contract-lifecycle-notice {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .notice-copy {
    flex-basis: 100%;
  }

  .notice-action {
    margin-left: auto;
  }
}
</style>
