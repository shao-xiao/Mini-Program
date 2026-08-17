<template>
  <section class="directory-pane tenant-directory" aria-label="租户公司">
    <header class="pane-header">
      <div>
        <strong>租户公司</strong>
        <span>共 {{ total }} 家</span>
      </div>
      <el-button
        v-if="canCreate"
        type="primary"
        size="small"
        :icon="Plus"
        aria-label="新增租户"
        @click="$emit('create')"
      >新建</el-button>
    </header>

    <div class="pane-search">
      <el-input
        :model-value="search"
        clearable
        :prefix-icon="Search"
        placeholder="名称 / 编号 / 手机"
        aria-label="搜索租户"
        @update:model-value="$emit('update:search', $event)"
      />
      <el-select
        :model-value="status"
        clearable
        placeholder="全部状态"
        aria-label="筛选租户状态"
        @update:model-value="$emit('update:status', $event || '')"
      >
        <el-option label="正常" value="ACTIVE" />
        <el-option label="停用" value="INACTIVE" />
      </el-select>
    </div>

    <div class="pane-source" role="note">
      <el-icon><InfoFilled /></el-icon>
      <span>数据口径：当前有效合同及关联空间</span>
    </div>

    <div v-if="errorText" class="pane-error" role="alert">
      <span>{{ errorText }}</span>
      <el-button link type="primary" @click="$emit('retry')">重试</el-button>
    </div>

    <div v-loading="loading" class="directory-list">
      <div v-if="!tenants.length && !loading" class="pane-empty">暂无符合条件的租户</div>
      <article
        v-for="tenant in tenants"
        :key="tenant.id"
        :class="['directory-item', { 'is-selected': Number(tenant.id) === Number(selectedId) }]"
      >
        <button class="directory-main" type="button" @click="$emit('select', tenant)">
          <span class="item-title">{{ tenant.tenantName || '未命名租户' }}</span>
          <span class="item-code-row">
            <span class="item-code">{{ tenant.tenantCode || `ID ${tenant.id}` }}</span>
            <el-tag size="small" effect="plain" :type="tenantStatusType(tenant.status)">
              {{ tenantStatusLabel(tenant.status) }}
            </el-tag>
          </span>
          <span class="item-contact">
            <span class="item-contact-label">负责人</span>
            <span class="item-contact-name">{{ tenant.contactName || '未配置' }}</span>
            <span v-if="tenant.phone" class="item-phone">{{ tenant.phone }}</span>
          </span>
          <span
            v-if="statsAvailable"
            class="item-stats"
            title="来源：当前有效合同及其关联房间"
          >
            <span><strong>{{ tenant.activeContractCount ?? stat(tenant).activeContracts }}</strong> 当前合同</span>
            <span><strong>{{ tenant.roomCount ?? stat(tenant).roomCount }}</strong> 租赁空间</span>
          </span>
          <span v-else class="item-stats is-unavailable">合同与空间统计未授权</span>
        </button>
        <el-dropdown v-if="hasActions" trigger="click" @command="command => $emit(command, tenant)">
          <el-button text :icon="MoreFilled" :aria-label="`${tenant.tenantName} 更多操作`" />
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="view">查看详情</el-dropdown-item>
              <el-dropdown-item v-if="canUpdate" command="edit">编辑</el-dropdown-item>
              <el-dropdown-item v-if="canUpdate" command="contacts">联系人</el-dropdown-item>
              <el-dropdown-item v-if="canDelete" command="delete" divided>删除</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </article>
    </div>
    <el-pagination
      v-if="total > pageSize"
      class="pane-pagination"
      small
      background
      layout="prev, pager, next"
      :current-page="page"
      :page-size="pageSize"
      :total="total"
      @current-change="$emit('update:page', $event)"
    />
  </section>
</template>

<script setup>
import { computed } from 'vue'
import { InfoFilled, MoreFilled, Plus, Search } from '@element-plus/icons-vue'

defineOptions({ name: 'TenantDirectoryPane' })

const props = defineProps({
  tenants: { type: Array, default: () => [] },
  selectedId: { type: [String, Number], default: null },
  search: { type: String, default: '' },
  status: { type: String, default: '' },
  page: { type: Number, default: 1 },
  pageSize: { type: Number, default: 30 },
  total: { type: Number, default: 0 },
  loading: { type: Boolean, default: false },
  errorText: { type: String, default: '' },
  stats: { type: Map, default: () => new Map() },
  statsAvailable: { type: Boolean, default: true },
  canCreate: { type: Boolean, default: false },
  canUpdate: { type: Boolean, default: false },
  canDelete: { type: Boolean, default: false }
})

defineEmits(['update:search', 'update:status', 'update:page', 'select', 'create', 'view', 'edit', 'contacts', 'delete', 'retry'])

const hasActions = computed(() => props.canUpdate || props.canDelete)

function stat(tenant) {
  return props.stats.get(Number(tenant.id)) || { activeContracts: 0, roomCount: 0 }
}

function tenantStatusLabel(status) {
  if (['ACTIVE', 'ENABLED'].includes(status)) return '正常'
  if (['INACTIVE', 'DISABLED'].includes(status)) return '停用'
  return status || '未知'
}

function tenantStatusType(status) {
  if (['ACTIVE', 'ENABLED'].includes(status)) return 'success'
  if (['INACTIVE', 'DISABLED'].includes(status)) return 'info'
  return 'warning'
}
</script>

<style scoped>
.directory-pane {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  background: #fafbfc;
}

.pane-header,
.pane-header > div,
.directory-item,
.item-code-row,
.item-meta,
.item-contact,
.item-stats,
.pane-source,
.pane-error {
  display: flex;
  align-items: center;
}

.pane-header {
  justify-content: space-between;
  min-height: 50px;
  padding: 0 14px;
  border-bottom: 1px solid var(--dhic-border-soft);
  background: #fff;
}

.pane-header > div {
  gap: 8px;
}

.pane-header strong {
  color: var(--dhic-text);
  font-size: 14px;
  font-weight: 650;
}

.pane-header span,
.item-code,
.item-meta,
.item-stats {
  color: var(--dhic-text-muted);
  font-size: 12px;
}

.pane-search {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 104px;
  gap: 8px;
  padding: 10px 12px 11px;
  border-bottom: 1px solid var(--dhic-border-soft);
  background: #fff;
}

.pane-source {
  flex: 0 0 auto;
  gap: 5px;
  min-height: 30px;
  border-bottom: 1px solid var(--dhic-border-soft);
  padding: 5px 12px;
  background: #fafbfc;
  color: var(--dhic-text-muted);
  font-size: 11px;
  line-height: 1.4;
}

.pane-source .el-icon {
  flex: 0 0 auto;
  color: #8f959e;
}

.pane-pagination {
  flex: 0 0 auto;
  justify-content: center;
  border-top: 1px solid var(--dhic-border-soft);
  padding: 8px 4px;
}

.directory-list {
  flex: 1 1 auto;
  min-height: 0;
  padding: 8px;
  overflow-y: auto;
  scrollbar-gutter: stable;
}

.directory-item {
  position: relative;
  margin-bottom: 6px;
  overflow: hidden;
  border: 1px solid transparent;
  border-radius: var(--dhic-radius-base);
  background: #fff;
  box-shadow: 0 1px 2px rgb(31 35 41 / 3%);
  transition: border-color 0.16s ease, background-color 0.16s ease, box-shadow 0.16s ease;
}

.directory-item::before {
  position: absolute;
  inset: 8px auto 8px 0;
  width: 3px;
  border-radius: 0 3px 3px 0;
  background: transparent;
  content: '';
}

.directory-item.is-selected {
  border-color: #efb5b0;
  background: var(--dhic-brand-red-light);
  box-shadow: 0 2px 8px rgba(217, 45, 32, 0.08);
}

.directory-item:not(.is-selected):hover {
  border-color: #e4e7ec;
  background: #fff;
  box-shadow: 0 4px 12px rgb(31 35 41 / 7%);
}

.directory-item.is-selected::before {
  background: var(--dhic-brand-red);
}

.directory-main {
  display: grid;
  flex: 1;
  min-width: 0;
  gap: 4px;
  border: 0;
  padding: 10px 5px 11px 12px;
  background: transparent;
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.directory-main:focus-visible {
  outline: 2px solid var(--dhic-primary);
  outline-offset: -2px;
}

.item-title,
.item-code,
.item-contact-name,
.item-phone {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-code-row {
  justify-content: space-between;
  gap: 8px;
  min-width: 0;
}

.item-code {
  flex: 1 1 auto;
  min-width: 0;
}

.item-title {
  flex: 1 1 auto;
  min-width: 0;
  color: var(--dhic-text);
  font-size: 14px;
  font-weight: 600;
}

.item-code-row :deep(.el-tag) {
  flex: 0 0 auto;
  height: 20px;
  border-radius: 4px;
  padding: 0 5px;
  font-size: 11px;
}

.item-contact,
.item-stats {
  gap: 6px;
  min-width: 0;
}

.item-contact {
  color: var(--dhic-text-secondary);
  font-size: 12px;
}

.item-contact-label {
  flex: 0 0 auto;
  color: var(--dhic-text-muted);
}

.item-contact-name,
.item-phone {
  min-width: 0;
}

.item-contact-name {
  flex: 0 1 auto;
}

.item-phone {
  flex: 1 1 auto;
  color: var(--dhic-text-muted);
}

.item-stats span {
  display: inline-flex;
  align-items: baseline;
  gap: 3px;
  border-radius: 4px;
  padding: 2px 6px;
  background: #f3f5f7;
  color: var(--dhic-text-muted);
  font-size: 11px;
}

.item-stats strong {
  color: var(--dhic-text);
  font-size: 12px;
  font-weight: 650;
}

.directory-item.is-selected .item-title,
.directory-item.is-selected .item-stats strong {
  color: var(--dhic-text-primary);
}

.directory-item.is-selected .item-code,
.directory-item.is-selected .item-contact,
.directory-item.is-selected .item-contact-label,
.directory-item.is-selected .item-phone,
.directory-item.is-selected .item-stats span {
  color: var(--dhic-text-secondary);
}

.directory-item.is-selected .item-code-row :deep(.el-tag) {
  border-color: #efb5b0;
  background: #fff;
  color: var(--dhic-brand-red);
}

.directory-item.is-selected .item-stats span {
  background: rgba(255, 255, 255, 0.72);
}

.directory-item.is-selected :deep(.el-dropdown .el-button) {
  color: var(--dhic-text-regular);
}

.item-stats.is-unavailable {
  color: var(--dhic-text-muted);
  font-size: 11px;
}

.directory-item :deep(.el-dropdown) {
  align-self: flex-start;
  margin: 5px 4px 0 0;
}

.pane-empty,
.pane-error {
  padding: 18px 12px;
  color: var(--dhic-text-muted);
  font-size: 13px;
}

.pane-empty {
  text-align: center;
}

.pane-error {
  justify-content: space-between;
  background: var(--dhic-danger-soft);
  color: var(--dhic-danger);
}
</style>
