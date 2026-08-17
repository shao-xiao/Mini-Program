<template>
  <aside class="finance-tenant-pane" aria-label="租户公司">
    <header class="tenant-pane-header">
      <div>
        <strong>租户公司</strong>
        <span>{{ tenants.length }} 家</span>
      </div>
    </header>

    <div class="tenant-search">
      <el-input
        :model-value="search"
        clearable
        :prefix-icon="Search"
        placeholder="搜索名称、编号、联系人或电话"
        aria-label="搜索租户公司"
        @update:model-value="$emit('update:search', $event)"
      />
    </div>

    <div v-if="errorText" class="tenant-error" role="alert">
      <span>{{ errorText }}</span>
      <el-button link type="primary" @click="$emit('retry')">重试</el-button>
    </div>

    <div v-loading="loading" class="tenant-list">
      <button
        type="button"
        :class="['tenant-item', 'all-tenants', { 'is-selected': selectedId === null }]"
        :aria-pressed="selectedId === null"
        @click="$emit('select', null)"
      >
        <span class="tenant-primary-line">
          <span class="tenant-name">全部租户</span>
          <span class="tenant-scope-label">全局</span>
        </span>
        <span class="tenant-code">查看全局账单和汇总</span>
      </button>

      <div v-if="!tenants.length && !loading" class="tenant-empty">暂无符合条件的租户</div>
      <button
        v-for="tenant in tenants"
        :key="tenant.id"
        type="button"
        :class="['tenant-item', { 'is-selected': Number(tenant.id) === Number(selectedId) }]"
        :aria-pressed="Number(tenant.id) === Number(selectedId)"
        @click="$emit('select', tenant)"
      >
        <span class="tenant-primary-line">
          <span class="tenant-name">{{ tenant.tenantName || '未命名租户' }}</span>
          <span v-if="numberValue(tenant.overdueBillCount) > 0" class="tenant-risk">
            逾期 {{ numberValue(tenant.overdueBillCount) }}
          </span>
        </span>
        <span class="tenant-meta-line">
          <span class="tenant-code">{{ tenant.tenantCode || `ID ${tenant.id}` }}</span>
          <span v-if="tenant.contactPerson || tenant.contactPhone" class="tenant-contact">
            {{ [tenant.contactPerson, tenant.contactPhone].filter(Boolean).join(' · ') }}
          </span>
        </span>
        <span class="tenant-summary-line">
          <span class="tenant-stats">
            <span>合同 {{ numberValue(tenant.activeContractCount) }}</span>
            <span>待缴 {{ numberValue(tenant.unpaidBillCount) }}</span>
          </span>
          <span class="tenant-unpaid">未收 {{ money(tenant.unpaidAmount) }}</span>
        </span>
      </button>
    </div>
  </aside>
</template>

<script setup>
import { Search } from '@element-plus/icons-vue'

defineOptions({ name: 'BillTenantDirectoryPane' })

defineProps({
  tenants: { type: Array, default: () => [] },
  selectedId: { type: [String, Number], default: null },
  search: { type: String, default: '' },
  loading: { type: Boolean, default: false },
  errorText: { type: String, default: '' }
})

defineEmits(['update:search', 'select', 'retry'])

function numberValue(value) {
  return Number(value || 0)
}

function money(value) {
  return `¥${Number(value || 0).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })}`
}
</script>

<style scoped>
.finance-tenant-pane {
  display: grid;
  grid-template-rows: auto auto auto minmax(0, 1fr);
  min-width: 0;
  min-height: 0;
  background: var(--dhic-surface, #fff);
}

.tenant-pane-header,
.tenant-pane-header > div,
.tenant-primary-line,
.tenant-meta-line,
.tenant-summary-line,
.tenant-stats,
.tenant-error {
  display: flex;
  align-items: center;
}

.tenant-pane-header {
  min-height: 48px;
  padding: 0 14px;
  border-bottom: 1px solid var(--dhic-border-soft);
}

.tenant-pane-header > div {
  gap: 8px;
}

.tenant-pane-header strong {
  color: var(--dhic-text);
  font-size: 14px;
}

.tenant-pane-header span,
.tenant-code,
.tenant-contact,
.tenant-stats,
.tenant-unpaid {
  color: var(--dhic-text-muted);
  font-size: 12px;
}

.tenant-search {
  padding: 8px 12px;
  border-bottom: 1px solid var(--dhic-border-soft);
}

.tenant-list {
  min-height: 0;
  overflow-y: auto;
  scrollbar-gutter: stable;
}

.tenant-item {
  position: relative;
  display: grid;
  width: 100%;
  gap: 6px;
  border: 0;
  border-bottom: 1px solid var(--dhic-border-soft);
  padding: 10px 12px 10px 14px;
  background: var(--dhic-card-bg, #fff);
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition: background-color 0.16s ease;
}

.tenant-item::before {
  position: absolute;
  inset: 0 auto 0 0;
  width: 3px;
  background: transparent;
  content: '';
}

.tenant-item:hover {
  background: var(--dhic-page-bg);
}

.tenant-item.is-selected {
  background: color-mix(in srgb, var(--dhic-primary-soft) 62%, var(--dhic-card-bg, #fff));
}

.tenant-item.is-selected::before {
  background: var(--dhic-primary);
}

.tenant-item:focus-visible {
  outline: 2px solid var(--dhic-primary);
  outline-offset: -2px;
}

.tenant-name,
.tenant-code,
.tenant-contact {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tenant-name {
  color: var(--dhic-text);
  font-weight: 600;
}

.tenant-primary-line,
.tenant-summary-line {
  justify-content: space-between;
  min-width: 0;
  gap: 8px;
}

.tenant-meta-line {
  min-width: 0;
  gap: 8px;
}

.tenant-meta-line .tenant-code {
  flex: none;
  max-width: 52%;
}

.tenant-contact {
  min-width: 0;
  padding-left: 8px;
  overflow: hidden;
  border-left: 1px solid var(--dhic-border-soft);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tenant-stats {
  gap: 8px;
}

.tenant-risk {
  flex: none;
  color: var(--el-color-danger, var(--dhic-primary));
  font-size: 11px;
  font-weight: 600;
}

.tenant-unpaid {
  color: var(--dhic-text-regular);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.all-tenants {
  padding-top: 11px;
  padding-bottom: 11px;
}

.tenant-scope-label {
  flex: none;
  color: var(--dhic-text-muted);
  font-size: 11px;
  font-weight: 600;
}

.tenant-error {
  justify-content: space-between;
  gap: 8px;
  padding: 10px 12px;
  background: var(--dhic-danger-soft);
  color: var(--dhic-danger);
  font-size: 12px;
}

.tenant-empty {
  padding: 20px 12px;
  color: var(--dhic-text-muted);
  font-size: 13px;
  text-align: center;
}

@media (prefers-reduced-motion: reduce) {
  .tenant-item {
    transition: none;
  }
}
</style>
