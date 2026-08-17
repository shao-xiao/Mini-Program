<template>
  <section class="data-workspace">
    <header class="workspace-header">
      <div class="workspace-title-block">
        <h2>{{ resolvedTitle }}</h2>
        <p v-if="resolvedSubtitle">{{ resolvedSubtitle }}</p>
      </div>

      <WorkspaceViewManager
        class="workspace-view-entry"
        :views="resolvedViews"
        :active-view-id="activeViewId"
        :view-dirty="viewDirty"
        @change="$emit('change-view', $event)"
        @create="$emit('create-view', $event)"
        @duplicate="(viewId, name) => $emit('duplicate-view', viewId, name)"
        @rename="(viewId, name) => $emit('rename-view', viewId, name)"
        @delete="$emit('delete-view', $event)"
        @save="$emit('save-view')"
      />

      <div v-if="$slots['header-actions']" class="workspace-header-actions">
        <slot name="header-actions" />
      </div>
    </header>

    <div v-if="filterItems.length" class="workspace-filter-strip" aria-label="当前筛选条件">
      <span class="filter-strip-label">筛选条件</span>
      <span v-if="filterLogicVisible" class="workspace-filter-logic">
        {{ filterLogic === 'or' ? '满足任一' : '满足全部' }}
      </span>
      <el-tag
        v-for="item in filterItems"
        :key="item.key || `${item.label}-${item.value}`"
        :closable="item.removable"
        effect="plain"
        @close="$emit('remove-filter', item.index)"
      >
        <span class="filter-label">{{ item.label }}</span>
        <span class="filter-value">{{ item.value }}</span>
        <span v-if="item.scopeLabel" class="filter-scope" :class="`scope-${item.scope}`">{{ item.scopeLabel }}</span>
      </el-tag>
    </div>

    <div v-if="queryNotices.length" class="workspace-query-notices" role="status" aria-live="polite">
      <span v-for="notice in queryNotices" :key="notice">{{ notice }}</span>
    </div>

    <div v-if="$slots.metrics || resolvedMetrics.length" class="workspace-metrics">
      <slot name="metrics" :definitions="resolvedMetrics" :data="metricData">
        <MetricPanel :definitions="resolvedMetrics" :data="metricData" :states="metricStates" />
      </slot>
    </div>

    <div class="workspace-toolbar">
      <slot name="toolbar" />
    </div>

    <BatchToolbar
      :workspace-config="workspaceConfig"
      :selected-count="selectedCount"
      :loading-action-ids="batchLoadingActionIds"
      :disabled-action-ids="batchDisabledActionIds"
      @action="$emit('batch-action', $event)"
      @clear="$emit('clear-selection')"
    >
      <template v-if="$slots['batch-actions']" #extra>
        <slot name="batch-actions" />
      </template>
    </BatchToolbar>

    <div class="workspace-grid">
      <slot name="grid" />
    </div>

    <div v-if="$slots.footer" class="workspace-footer">
      <slot name="footer" />
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import BatchToolbar from './BatchToolbar.vue'
import MetricPanel from './MetricPanel.vue'
import WorkspaceViewManager from './WorkspaceViewManager.vue'

defineOptions({ name: 'DataWorkspace' })

const props = defineProps({
  workspaceConfig: { type: Object, default: null },
  title: { type: String, default: '' },
  subtitle: { type: String, default: '' },
  views: { type: Array, default: () => [] },
  activeViewId: { type: String, default: 'default' },
  viewDirty: { type: Boolean, default: false },
  filterLogic: { type: String, default: 'and' },
  filterLogicVisible: { type: Boolean, default: false },
  filterItems: { type: Array, default: () => [] },
  queryNotices: { type: Array, default: () => [] },
  selectedCount: { type: Number, default: 0 },
  metricData: { type: Object, default: () => ({}) },
  metricStates: { type: Object, default: () => ({}) },
  batchLoadingActionIds: { type: Array, default: () => [] },
  batchDisabledActionIds: { type: Array, default: () => [] }
})

const resolvedTitle = computed(() => props.title || props.workspaceConfig?.title || '数据工作台')
const resolvedSubtitle = computed(() => props.subtitle || props.workspaceConfig?.subtitle || '')
const resolvedViews = computed(() => props.views.length ? props.views : (props.workspaceConfig?.views || []))
const resolvedMetrics = computed(() => props.workspaceConfig?.metrics || [])

defineEmits([
  'change-view',
  'create-view',
  'duplicate-view',
  'rename-view',
  'delete-view',
  'save-view',
  'remove-filter',
  'batch-action',
  'clear-selection'
])
</script>

<style scoped>
.data-workspace {
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--dhic-border-color);
  border-radius: var(--dhic-radius-large);
  background: var(--dhic-card-bg);
  box-shadow: var(--dhic-shadow-card);
}

.workspace-header {
  display: grid;
  grid-template-columns: minmax(220px, auto) minmax(260px, 1fr) auto;
  align-items: center;
  gap: 12px;
  min-height: 56px;
  padding: 8px 14px;
  border-bottom: 1px solid var(--dhic-border-color-light);
  background: var(--dhic-card-bg);
}

.workspace-title-block {
  min-width: 0;
}

.workspace-title-block h2 {
  margin: 0;
  color: var(--dhic-text-primary);
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 0;
  line-height: 1.35;
}

.workspace-title-block p {
  margin: 2px 0 0;
  color: var(--dhic-text-secondary);
  font-size: 12px;
  line-height: 1.4;
}

.workspace-view-entry {
  min-width: 0;
}

.workspace-header-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.workspace-filter-strip {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 7px;
  min-height: 42px;
  padding: 7px 12px;
  border-bottom: 1px solid var(--dhic-border-color-light);
  background: #fafbfc;
}

.filter-strip-label {
  margin-right: 2px;
  color: #697386;
  font-size: 12px;
  font-weight: 600;
}

.workspace-filter-logic {
  color: #667085;
  font-size: 12px;
  font-weight: 600;
}

.filter-label {
  color: #5f6876;
}

.filter-value {
  margin-left: 5px;
  color: #202938;
  font-weight: 600;
}

.filter-scope {
  margin-left: 6px;
  color: #667085;
  font-size: 11px;
  font-weight: 600;
}

.filter-scope.scope-page,
.filter-scope.scope-unsupported {
  color: #b54708;
}

.workspace-query-notices {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  padding: 7px 12px;
  border-bottom: 1px solid #fedf89;
  background: #fffaeb;
  color: #93370d;
  font-size: 12px;
}

.workspace-metrics {
  padding: 10px 14px 0;
}

.workspace-metrics :deep(.list-summary-grid) {
  margin: 0 0 12px;
}

.workspace-toolbar {
  padding: 0 14px;
}

.workspace-toolbar :deep(.dhic-data-toolbar) {
  margin: 0 0 10px;
}

.workspace-grid {
  min-width: 0;
  overflow-x: auto;
  overflow-y: hidden;
  scrollbar-gutter: stable;
}

.workspace-grid :deep(.el-table) {
  border-radius: 0;
}

.workspace-grid :deep(.el-scrollbar__bar.is-horizontal) {
  height: 9px;
}

.workspace-footer {
  padding: 0 14px 14px;
}

@media (max-width: 1180px) {
  .workspace-header {
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 10px;
  }

  .workspace-view-entry {
    grid-column: 1 / -1;
    grid-row: 2;
  }

  .workspace-header-actions {
    grid-column: 2;
    grid-row: 1;
  }
}

@media (max-width: 720px) {
  .workspace-header {
    grid-template-columns: 1fr;
  }

  .workspace-header-actions {
    grid-column: 1;
    grid-row: auto;
    justify-content: flex-start;
  }
}
</style>
