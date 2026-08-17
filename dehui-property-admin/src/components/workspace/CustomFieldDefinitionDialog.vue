<template>
  <el-dialog v-model="visible" :title="editing ? '编辑字段定义' : '创建字段定义'" width="640px" destroy-on-close>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
      <el-form-item label="字段名称" prop="fieldName">
        <input v-model="form.fieldName" class="definition-input" aria-label="字段名称" maxlength="50" placeholder="请输入字段名称" />
      </el-form-item>
      <el-form-item label="字段编码">
        <input v-model="form.fieldCode" class="definition-input" aria-label="字段编码" :disabled="editing" placeholder="可留空自动生成，仅支持字母、数字和下划线" />
      </el-form-item>
      <el-form-item label="字段类型" prop="fieldType">
        <el-select v-model="form.fieldType" :disabled="editing" style="width: 100%" @change="handleTypeChange">
          <el-option v-for="option in typeOptions" :key="option.value" :label="option.label" :value="option.value" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="needsOptions" label="选项配置" prop="optionsText">
        <el-input v-model="form.optionsText" type="textarea" :rows="4" placeholder="每行一个选项" />
      </el-form-item>
      <template v-if="isFileType">
        <el-form-item label="文件数量">
          <el-input-number v-model="form.maxFiles" :min="1" :max="30" />
          <span class="form-tip">最多30个</span>
        </el-form-item>
        <el-form-item label="允许多文件">
          <el-switch :model-value="true" disabled />
          <span class="form-tip">文件字段固定支持多选和连续追加</span>
        </el-form-item>
      </template>
      <el-form-item v-if="!isFileType" label="默认值">
        <el-input v-model="form.defaultValue" placeholder="可选" />
      </el-form-item>
      <el-form-item label="必填">
        <el-switch v-model="form.required" />
      </el-form-item>
      <el-divider content-position="left">使用场景</el-divider>
      <el-form-item label="后台列表">
        <el-switch v-model="form.surfaces.adminList.visible" />
      </el-form-item>
      <el-form-item label="后台表单">
        <el-switch v-model="form.surfaces.adminForm.visible" />
        <el-checkbox v-model="form.surfaces.adminForm.editable" :disabled="!form.surfaces.adminForm.visible">允许编辑</el-checkbox>
      </el-form-item>
      <el-form-item label="后台详情">
        <el-switch v-model="form.surfaces.adminDetail.visible" />
      </el-form-item>
      <el-form-item label="小程序表单">
        <el-switch v-model="form.surfaces.miniProgramForm.visible" />
        <el-checkbox v-model="form.surfaces.miniProgramForm.editable" :disabled="!form.surfaces.miniProgramForm.visible">允许编辑</el-checkbox>
      </el-form-item>
      <el-form-item label="小程序详情">
        <el-switch v-model="form.surfaces.miniProgramDetail.visible" />
      </el-form-item>
      <el-form-item v-if="!isFileType" label="唯一值">
        <el-switch v-model="form.uniqueValue" />
      </el-form-item>
      <el-form-item label="说明">
        <el-input v-model="form.description" type="textarea" :rows="2" maxlength="200" show-word-limit />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">{{ editing ? '保存字段' : '创建字段' }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  CUSTOM_FIELD_DEFINITION_TYPES,
  buildCustomFieldDefinitionPayload,
  createCustomFieldDefinitionDraft,
  customFieldTypeIsFile,
  customFieldDefinitionValidation,
  definitionToCustomFieldDraft
} from '../../utils/customFieldDefinitionForm'

const props = defineProps({
  modelValue: Boolean,
  definition: { type: Object, default: null },
  submitting: Boolean,
  submitHandler: { type: Function, default: null }
})
const emit = defineEmits(['update:modelValue', 'saved'])

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})
const editing = computed(() => Boolean(props.definition?.id))
const typeOptions = CUSTOM_FIELD_DEFINITION_TYPES
const formRef = ref()
const form = reactive({ ...createCustomFieldDefinitionDraft(), optionsText: '' })
const isFileType = computed(() => customFieldTypeIsFile(form.fieldType))
const needsOptions = computed(() => ['SINGLE_SELECT', 'MULTI_SELECT'].includes(form.fieldType))

const rules = {
  fieldName: [{ required: true, message: '请输入字段名称', trigger: 'blur' }],
  fieldType: [{ required: true, message: '请选择字段类型', trigger: 'change' }]
}

const reset = () => {
  const draft = props.definition
    ? definitionToCustomFieldDraft(props.definition)
    : createCustomFieldDefinitionDraft()
  Object.assign(form, draft, {
    optionsText: (draft.options || []).map(option => option.label).join('\n')
  })
}

watch(() => props.modelValue, (open) => {
  if (open) reset()
})

const handleTypeChange = () => {
  const next = createCustomFieldDefinitionDraft({ ...form, fieldType: form.fieldType })
  form.maxFiles = next.maxFiles
  if (customFieldTypeIsFile(form.fieldType)) {
    form.defaultValue = ''
    form.uniqueValue = false
  }
}

const submit = async () => {
  if (typeof formRef.value?.validate === 'function') {
    await formRef.value.validate()
  }
  if (needsOptions.value) {
    form.options = String(form.optionsText || '')
      .split(/\r?\n/)
      .map(label => label.trim())
      .filter(Boolean)
      .map(label => ({ label }))
  }
  const validation = customFieldDefinitionValidation(form)
  if (!validation.valid) {
    ElMessage.warning(validation.message)
    return
  }
  const payload = buildCustomFieldDefinitionPayload(form)
  const saved = props.submitHandler ? await props.submitHandler(payload) : payload
  emit('saved', saved)
  visible.value = false
}
</script>

<style scoped>
.form-tip {
  margin-left: 12px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.definition-input {
  box-sizing: border-box;
  width: 100%;
  height: 32px;
  padding: 0 11px;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  font: inherit;
}
</style>
