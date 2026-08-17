<template>
  <span class="custom-field-cell-renderer">
    <CellRenderer :field="field" :row="row" :option-map="optionMap" />
  </span>
</template>

<script setup>
import CellRenderer from '../CellRenderer.vue'

defineOptions({ name: 'CustomFieldCellRenderer' })

defineProps({
  field: { type: Object, required: true },
  row: { type: Object, required: true },
  optionMap: { type: Map, default: null }
})
</script>

<style scoped>
.custom-field-cell-renderer {
  min-width: 0;
}
</style>
