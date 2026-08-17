<template>
  <section class="finance-record-panel" :aria-label="title">
    <header class="record-panel-header">
      <div>
        <h3>{{ title }}</h3>
        <p>{{ subtitle }}</p>
      </div>
      <span class="record-count">共 {{ total }} 条</span>
    </header>

    <WorkspaceDataGrid
      :loading="loading"
      :error-text="errorText"
      retryable
      :rows="rows"
      :columns="columns"
      row-key="id"
      :current-page="page"
      :page-size="pageSize"
      density="compact"
      :empty-text="emptyText"
      :operation-column="hasOperations ? { width: operationWidth, fixed: 'right', align: 'left' } : null"
      :aria-label="`${title}数据表格`"
      @retry="$emit('retry')"
      @row-click="row => $emit('row-click', row)"
    >
      <template v-if="hasOperations" #operations="scope">
        <slot name="operations" v-bind="scope" />
      </template>
    </WorkspaceDataGrid>

    <footer class="record-pagination">
      <el-pagination
        :current-page="page"
        :page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="$emit('update:page', $event)"
        @size-change="$emit('update:page-size', $event)"
      />
    </footer>
  </section>
</template>

<script setup>
import { computed, useSlots } from 'vue'
import WorkspaceDataGrid from '../../../components/workspace/WorkspaceDataGrid.vue'

defineOptions({ name: 'FinanceRecordPanel' })

defineProps({
  title: { type: String, required: true },
  subtitle: { type: String, default: '' },
  rows: { type: Array, default: () => [] },
  columns: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  errorText: { type: String, default: '' },
  emptyText: { type: String, default: '暂无记录' },
  total: { type: Number, default: 0 },
  page: { type: Number, default: 1 },
  pageSize: { type: Number, default: 20 },
  operationWidth: { type: Number, default: 120 }
})

defineEmits(['retry', 'row-click', 'update:page', 'update:page-size'])

const slots = useSlots()
const hasOperations = computed(() => Boolean(slots.operations))
</script>

<style scoped>
.finance-record-panel {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  min-width: 0;
  min-height: 0;
  height: 100%;
}

.record-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 14px;
  border-bottom: 1px solid var(--dhic-border-soft);
}

.record-panel-header h3 {
  margin: 0;
  color: var(--dhic-text);
  font-size: 15px;
  letter-spacing: 0;
}

.record-panel-header p {
  margin: 3px 0 0;
  color: var(--dhic-text-muted);
  font-size: 12px;
}

.record-count {
  color: var(--dhic-text-muted);
  font-size: 12px;
  white-space: nowrap;
}

.record-pagination {
  display: flex;
  justify-content: flex-end;
  padding: 10px 12px;
  border-top: 1px solid var(--dhic-border-soft);
}
</style>
