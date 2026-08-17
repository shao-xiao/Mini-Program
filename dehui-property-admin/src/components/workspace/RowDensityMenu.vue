<template>
  <el-popover placement="bottom-start" width="180" trigger="click" popper-class="dhic-view-popper">
    <template #reference>
      <el-button class="workspace-toolbar-button" :icon="Rank">行高</el-button>
    </template>
    <div class="density-menu" role="menu" aria-label="表格行高">
      <button
        v-for="option in densityOptions"
        :key="option.value"
        type="button"
        role="menuitemradio"
        :aria-checked="rowDensity === option.value"
        :class="{ active: rowDensity === option.value }"
        @click="controller.view.updateRowDensity(option.value)"
      >
        <el-icon v-if="rowDensity === option.value"><Check /></el-icon>
        <span v-else class="icon-space" />
        {{ option.label }}
      </button>
    </div>
  </el-popover>
</template>

<script setup>
import { computed } from 'vue'
import { Check, Rank } from '@element-plus/icons-vue'
import { useWorkspaceToolbar } from './workspaceToolbarContext.js'

defineOptions({ name: 'RowDensityMenu' })

const controller = useWorkspaceToolbar('RowDensityMenu')
const densityOptions = [
  { label: '紧凑', value: 'compact' },
  { label: '默认', value: 'standard' },
  { label: '宽松', value: 'comfortable' }
]
const rowDensity = computed(() => controller.view.rowDensity || 'standard')
</script>

<style scoped>
.density-menu {
  display: grid;
  gap: 4px;
}

.density-menu button {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  min-height: 36px;
  padding: 0 10px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--dhic-text-regular);
  cursor: pointer;
  font: inherit;
  text-align: left;
}

.density-menu button:hover,
.density-menu button.active {
  color: var(--dhic-primary);
  background: var(--dhic-primary-soft);
}

.icon-space {
  display: inline-block;
  width: 14px;
}
</style>
