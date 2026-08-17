<template>
  <el-dialog
    :model-value="modelValue"
    :title="contact?.id ? '编辑联系人' : '新增联系人'"
    width="min(540px, calc(100vw - 48px))"
    destroy-on-close
    @update:model-value="$emit('update:modelValue', $event)"
    @open="reset"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
      <el-form-item label="姓名" prop="name">
        <el-input v-model.trim="form.name" maxlength="64" />
      </el-form-item>
      <el-form-item label="手机号" prop="phone">
        <el-input v-model.trim="form.phone" maxlength="32" />
      </el-form-item>
      <el-form-item label="邮箱" prop="email">
        <el-input v-model.trim="form.email" maxlength="120" />
      </el-form-item>
      <el-form-item label="职位">
        <el-input v-model.trim="form.position" maxlength="64" />
      </el-form-item>
      <el-form-item label="联系人类型" prop="role">
        <el-select v-model="form.role" style="width: 100%">
          <el-option label="租户负责人" value="RESPONSIBLE" />
          <el-option label="租户员工" value="EMPLOYEE" />
        </el-select>
      </el-form-item>
      <el-form-item label="主负责人">
        <div class="primary-responsible-field">
          <el-switch v-model="form.isPrimary" :disabled="form.role !== 'RESPONSIBLE'" />
          <span class="field-help">可设置多位负责人，但仅能指定一位主负责人。</span>
        </div>
      </el-form-item>
      <el-form-item label="紧急联系人">
        <el-switch v-model="form.isEmergency" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="form.status" style="width: 100%">
          <el-option label="启用" value="ACTIVE" />
          <el-option label="停用" value="INACTIVE" />
        </el-select>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { nextTick, reactive, ref, watch } from 'vue'

defineOptions({ name: 'ContactEditorDialog' })

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  contact: { type: Object, default: null },
  saving: { type: Boolean, default: false },
  submitHandler: { type: Function, required: true }
})

const emit = defineEmits(['update:modelValue', 'saved'])
const formRef = ref(null)
const form = reactive(defaultForm())

watch(() => form.role, (role) => {
  if (role !== 'RESPONSIBLE') form.isPrimary = false
})

const rules = {
  name: [{ required: true, message: '请输入联系人姓名', trigger: 'blur' }],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^(?:1\d{10}|0\d{2,3}-?\d{7,8})$/, message: '联系电话格式不正确', trigger: 'blur' }
  ],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }]
}

function defaultForm() {
  return {
    id: null,
    name: '',
    phone: '',
    email: '',
    position: '',
    role: 'EMPLOYEE',
    isPrimary: false,
    isEmergency: false,
    status: 'ACTIVE'
  }
}

async function reset() {
  Object.assign(form, defaultForm(), props.contact || {})
  form.role = props.contact?.role === 'RESPONSIBLE' || props.contact?.isPrimary ? 'RESPONSIBLE' : 'EMPLOYEE'
  form.isPrimary = Boolean(props.contact?.isPrimary)
  form.isEmergency = Boolean(props.contact?.isEmergency)
  await nextTick()
  formRef.value?.clearValidate()
}

async function submit() {
  await formRef.value?.validate()
  if (form.role !== 'RESPONSIBLE') form.isPrimary = false
  const result = await props.submitHandler({ ...form })
  if (result?.success === false) return
  emit('saved', result)
  emit('update:modelValue', false)
}
</script>

<style scoped>
.primary-responsible-field {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 32px;
}

.field-help {
  color: var(--dhic-text-secondary);
  font-size: 12px;
  line-height: 1.5;
}
</style>
