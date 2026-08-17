<template>
  <div
    v-if="selectedCount > 0"
    class="workspace-batch-toolbar"
    :aria-label="`已选择 ${selectedCount} 项，可执行批量操作`"
  >
    <span class="batch-live-region" role="status" aria-live="polite" aria-atomic="true">
      已选择 {{ selectedCount }} 项，可执行批量操作
    </span>
    <div class="batch-selection">
      <span class="selection-count">{{ selectedCount }}</span>
      <span>项已选择</span>
    </div>
    <div class="batch-actions">
      <el-button
        v-for="action in visibleActions"
        :key="action.id"
        :type="action.tone"
        :icon="actionIcon(action)"
        :loading="loadingActionIds.includes(action.id)"
        :disabled="disabledActionIds.includes(action.id)"
        @click="$emit('action', action.id)"
      >
        {{ action.label }}
      </el-button>
      <slot name="extra" />
      <el-button text :icon="Close" @click="$emit('clear')">取消选择</el-button>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import {
  CircleClose,
  Close,
  Delete,
  Download,
  Edit,
  Operation,
  UserFilled
} from '@element-plus/icons-vue'
import { hasPermission } from '../utils/permission'
import { permittedWorkspaceActions } from '../workspaceConfigs/workspaceConfig'

defineOptions({ name: 'BatchToolbar' })

const props = defineProps({
  workspaceConfig: { type: Object, default: null },
  actions: { type: Array, default: () => [] },
  selectedCount: { type: Number, default: 0 },
  loadingActionIds: { type: Array, default: () => [] },
  disabledActionIds: { type: Array, default: () => [] }
})

defineEmits(['action', 'clear'])

const sourceActions = computed(() => (
  props.actions.length ? props.actions : (props.workspaceConfig?.batchActions || [])
))
const visibleActions = computed(() => permittedWorkspaceActions(
  props.workspaceConfig,
  sourceActions.value,
  hasPermission
))

function actionIcon(action) {
  return {
    assign: UserFilled,
    close: CircleClose,
    delete: Delete,
    download: Download,
    edit: Edit,
    batch: Operation
  }[action.icon] || Operation
}
</script>

<style scoped>
.workspace-batch-toolbar {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 46px;
  padding: 7px 12px;
  border-top: 1px solid #f1d4d1;
  border-bottom: 1px solid #f1d4d1;
  background: #fff7f6;
}

.batch-selection,
.batch-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.batch-selection {
  color: #4b5563;
  font-size: 13px;
  font-weight: 600;
}

.selection-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 24px;
  height: 24px;
  padding: 0 7px;
  border-radius: 12px;
  background: #d93025;
  color: #fff;
  font-variant-numeric: tabular-nums;
}

.batch-live-region {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

@media (max-width: 720px) {
  .workspace-batch-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .batch-actions {
    flex-wrap: wrap;
    width: 100%;
  }
}
</style>
