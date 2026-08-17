<template>
  <el-descriptions :column="2" border>
    <el-descriptions-item v-for="field in fields" :key="field.fieldKey" :label="field.label">
      <span v-if="field.dataType !== 'IMAGE' && field.dataType !== 'ATTACHMENT'">{{ display(field) }}</span>
      <CustomFieldValueDisplay
        v-else
        :field="{ ...field, fieldCode: field.fieldKey, fieldType: field.dataType }"
        :value="value(field)"
        mode="detail"
      />
    </el-descriptions-item>
  </el-descriptions>
</template>

<script setup>
import CustomFieldValueDisplay from '../workspace/CustomFieldValueDisplay.vue'
const props = defineProps({ fields: { type: Array, default: () => [] }, values: { type: Object, default: () => ({}) } })
const value = field => props.values[field.apiField] ?? props.values[field.fieldKey] ?? props.values.customFields?.[field.fieldKey]
const display = field => {
  const raw = value(field)
  if (raw == null || raw === '') return '-'
  const option = field.options?.find(item => String(item.value) === String(raw))
  return option?.label ?? (Array.isArray(raw) ? raw.join('、') : String(raw))
}
</script>
