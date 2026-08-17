<template>
  <el-dropdown trigger="click" @command="selectSide">
    <el-button
      text
      circle
      :title="tooltip"
      :class="{ active: modelValue === 'left' || modelValue === 'right' }"
      :icon="modelValue ? Lock : Unlock"
      :aria-label="`${columnLabel}${tooltip}`"
    />
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item command="left" :disabled="modelValue === 'left'">固定到左侧</el-dropdown-item>
        <el-dropdown-item command="right" :disabled="modelValue === 'right'">固定到右侧</el-dropdown-item>
        <el-dropdown-item command="" :disabled="!modelValue" divided>取消固定</el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<script setup>
import { computed } from 'vue'
import { Lock, Unlock } from '@element-plus/icons-vue'

defineOptions({ name: 'FixedColumnControls' })

const props = defineProps({
  columnKey: { type: String, required: true },
  columnLabel: { type: String, required: true },
  modelValue: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue'])
const tooltip = computed(() => {
  if (props.modelValue === 'left') return '已固定在左侧'
  if (props.modelValue === 'right') return '已固定在右侧'
  return '设置固定列'
})

function selectSide(side) {
  emit('update:modelValue', side)
}
</script>

<style scoped>
.el-button.active {
  color: var(--dhic-primary);
  background: var(--dhic-primary-soft);
}
</style>
