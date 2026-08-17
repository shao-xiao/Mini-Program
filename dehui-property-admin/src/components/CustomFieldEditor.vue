<template>
  <CustomFieldFormSection
    :fields="columns"
    :model-value="modelValue"
    :editable="editable"
    :saving="saving"
    @update:model-value="$emit('update:modelValue', $event)"
  />
</template>

<script setup>
import CustomFieldFormSection from './workspace/CustomFieldFormSection.vue'

defineOptions({ name: 'CustomFieldEditor' })

defineProps({
  columns: { type: Array, default: () => [] },
  modelValue: { type: Object, default: () => ({}) },
  editable: { type: Boolean, default: true },
  saving: { type: Boolean, default: false }
})

defineEmits(['update:modelValue'])
</script>
