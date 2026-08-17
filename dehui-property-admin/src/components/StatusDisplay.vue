<template>
  <el-tag size="small" effect="light" :type="resolvedTone" class="dhic-status-display">
    <span v-if="showDot" class="dhic-status-display__dot" aria-hidden="true" />
    {{ resolvedLabel }}
  </el-tag>
</template>
<script setup>
import { computed } from 'vue'
defineOptions({ name: 'DhicStatusDisplay' })
const props = defineProps({ value: { type: [String, Number, Boolean], default: '' }, label: { type: String, default: '' }, tone: { type: String, default: '' }, showDot: { type: Boolean, default: true } })
const labels = Object.freeze({ ACTIVE:'启用',ENABLED:'启用',DISABLED:'停用',INACTIVE:'停用',NORMAL:'正常',ABNORMAL:'异常',PENDING:'待处理',PROCESSING:'处理中',CONFIRMED:'已确认',COMPLETED:'已完成',CANCELLED:'已取消',CLOSED:'已关闭',BOUND:'已绑定',UNBOUND:'未绑定',REVERIFY_REQUIRED:'待重新认证',NOT_APPLICABLE:'不适用',AVAILABLE:'可用',MAINTENANCE:'维护中',PUBLIC:'普通用户',RESPONSIBLE:'负责人',EMPLOYEE:'员工' })
const tones = Object.freeze({ ACTIVE:'success',ENABLED:'success',NORMAL:'success',AVAILABLE:'success',COMPLETED:'success',CONFIRMED:'success',ABNORMAL:'danger',CANCELLED:'info',CLOSED:'info',DISABLED:'info',INACTIVE:'info',PENDING:'warning',PROCESSING:'warning',MAINTENANCE:'warning',REVERIFY_REQUIRED:'warning' })
const key = computed(() => String(props.value ?? '').trim().toUpperCase())
const resolvedLabel = computed(() => props.label && props.label !== key.value ? props.label : labels[key.value] || props.label || String(props.value ?? '-'))
const resolvedTone = computed(() => props.tone || tones[key.value] || 'info')
</script>
<style scoped>
.dhic-status-display { display: inline-flex; align-items: center; gap: 5px; max-width: 100%; }
.dhic-status-display__dot { width: 6px; height: 6px; flex: 0 0 6px; border-radius: 50%; background: currentColor; }
</style>