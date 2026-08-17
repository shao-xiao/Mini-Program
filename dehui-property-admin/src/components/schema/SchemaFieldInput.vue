<template>
  <el-input
    v-if="field.dataType === 'TEXT' || field.dataType === 'EMAIL' || field.dataType === 'URL'"
    :model-value="modelValue"
    :disabled="disabled"
    :maxlength="field.validation?.maxLength"
    @update:model-value="emitValue"
  />
  <el-input
    v-else-if="field.dataType === 'TEXTAREA'"
    type="textarea"
    :rows="4"
    :model-value="modelValue"
    :disabled="disabled"
    :maxlength="field.validation?.maxLength"
    show-word-limit
    @update:model-value="emitValue"
  />
  <el-input-number
    v-else-if="field.dataType === 'NUMBER'"
    :model-value="modelValue"
    :disabled="disabled"
    controls-position="right"
    @update:model-value="emitValue"
  />
  <el-date-picker
    v-else-if="field.dataType === 'DATE' || field.dataType === 'DATETIME'"
    :model-value="modelValue"
    :type="field.dataType === 'DATE' ? 'date' : 'datetime'"
    :value-format="field.dataType === 'DATE' ? 'YYYY-MM-DD' : 'YYYY-MM-DD HH:mm:ss'"
    :disabled="disabled"
    @update:model-value="emitValue"
  />
  <el-time-picker
    v-else-if="field.dataType === 'TIME'"
    :model-value="modelValue"
    value-format="HH:mm"
    format="HH:mm"
    :disabled="disabled"
    @update:model-value="emitValue"
  />
  <el-select
    v-else-if="field.dataType === 'SELECT' || field.dataType === 'MULTISELECT'"
    :model-value="modelValue"
    :multiple="field.dataType === 'MULTISELECT'"
    :disabled="disabled"
    clearable
    @update:model-value="emitValue"
  >
    <el-option v-for="option in field.options" :key="option.value" :label="option.label" :value="option.value" />
  </el-select>
  <el-switch
    v-else-if="field.dataType === 'BOOLEAN'"
    :model-value="Boolean(modelValue)"
    :disabled="disabled"
    @update:model-value="emitValue"
  />
  <CustomFieldValueInput
    v-else-if="field.dataType === 'IMAGE' || field.dataType === 'ATTACHMENT'"
    :model-value="modelValue"
    :field="fileDefinition"
    :disabled="disabled"
    @update:model-value="emitValue"
  />
  <el-input v-else :model-value="modelValue" :disabled="disabled" @update:model-value="emitValue" />
</template>

<script setup>
import { computed } from 'vue'
import CustomFieldValueInput from '../workspace/CustomFieldValueInput.vue'

const props = defineProps({
  field: { type: Object, required: true },
  modelValue: { default: null },
  disabled: Boolean,
  moduleKey: { type: String, required: true }
})
const emit = defineEmits(['update:modelValue'])
const emitValue = value => emit('update:modelValue', value)
const fileDefinition = computed(() => ({
  ...props.field,
  fieldCode: props.field.fieldKey,
  fieldType: props.field.dataType,
  workspaceKey: props.moduleKey,
  config: props.field.validation || {}
}))
</script>
