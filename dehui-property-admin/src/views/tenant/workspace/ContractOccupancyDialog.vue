<template>
  <el-dialog
    :model-value="modelValue"
    :title="mode === 'checkin' ? '办理入住' : '办理退租'"
    width="min(560px, calc(100vw - 48px))"
    destroy-on-close
    @update:model-value="$emit('update:modelValue', $event)"
    @open="reset"
  >
    <el-alert
      :title="mode === 'checkin' ? '本次将为合同全部房间办理入住' : '本次将为合同全部房间办理退租'"
      :type="mode === 'checkin' ? 'info' : 'warning'"
      show-icon
      :closable="false"
    />

    <dl class="contract-summary">
      <div><dt>合同</dt><dd>{{ contract?.contractNo || '-' }}</dd></div>
      <div><dt>租户</dt><dd>{{ contract?.tenantName || '-' }}</dd></div>
      <div><dt>房间</dt><dd>{{ contract?.roomSummary || contract?.roomNames?.join('、') || '未关联房间' }}</dd></div>
    </dl>

    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <el-form-item label="发生时间" prop="occurredAt">
        <el-date-picker
          v-model="form.occurredAt"
          type="datetime"
          value-format="YYYY-MM-DDTHH:mm:ss"
          style="width: 100%"
          placeholder="不填则以后端接收时间为准"
        />
      </el-form-item>
      <el-form-item :label="mode === 'checkin' ? '入住说明' : '退租原因'" prop="reason">
        <el-input v-model="form.reason" type="textarea" :rows="3" maxlength="500" show-word-limit />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button :type="mode === 'checkin' ? 'primary' : 'danger'" :loading="saving" @click="submit">
        {{ mode === 'checkin' ? '确认入住' : '确认退租' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { nextTick, reactive, ref } from 'vue'

defineOptions({ name: 'ContractOccupancyDialog' })

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  mode: { type: String, default: 'checkin' },
  contract: { type: Object, default: null },
  lease: { type: Object, default: null },
  saving: { type: Boolean, default: false },
  submitHandler: { type: Function, required: true }
})

const emit = defineEmits(['update:modelValue', 'saved'])
const formRef = ref(null)
const form = reactive({ occurredAt: '', reason: '' })
const rules = {
  reason: [{ validator: validateReason, trigger: 'blur' }]
}

async function reset() {
  form.occurredAt = ''
  form.reason = ''
  await nextTick()
  formRef.value?.clearValidate()
}

async function submit() {
  await formRef.value?.validate()
  const result = await props.submitHandler({
    occurredAt: form.occurredAt || undefined,
    reason: form.reason.trim() || undefined,
    version: props.mode === 'checkout' ? props.lease?.version : undefined
  })
  if (result?.success === false) return
  emit('saved', result)
  emit('update:modelValue', false)
}

function validateReason(_rule, value, callback) {
  if (props.mode === 'checkout' && !String(value || '').trim()) callback(new Error('请填写退租原因'))
  else callback()
}
</script>

<style scoped>
.contract-summary {
  display: grid;
  gap: 8px;
  margin: 16px 0;
  padding: 12px;
  background: var(--dhic-page-bg);
}

.contract-summary div {
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr);
  gap: 10px;
}

.contract-summary dt {
  color: var(--dhic-text-muted);
}

.contract-summary dd {
  min-width: 0;
  margin: 0;
  overflow-wrap: anywhere;
}
</style>
