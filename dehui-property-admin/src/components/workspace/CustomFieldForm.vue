<template>
  <section class="custom-field-manager" aria-label="共享自定义字段">
    <div class="custom-field-heading">
      <div>
        <strong>共享自定义字段</strong>
        <span>{{ definitions.length }} / {{ maximumFields }}</span>
      </div>
      <el-button
        v-if="canManage"
        link
        type="primary"
        :icon="Plus"
        :disabled="definitions.length >= maximumFields"
        @click="openCreate"
      >新增字段</el-button>
    </div>

    <div v-if="error" class="custom-field-error" role="alert">
      <span>{{ error }}</span>
      <el-button link type="primary" @click="actions.retry?.()">重试</el-button>
    </div>

    <div v-loading="loading" class="custom-field-list">
      <div v-if="!definitions.length && !loading" class="custom-field-empty">
        {{ canManage ? '当前工作区还没有自定义字段' : '当前工作区暂无共享自定义字段' }}
      </div>
      <div v-for="(field, index) in definitions" :key="field.id || field.fieldCode" class="custom-field-row">
        <span class="field-type-icon" aria-hidden="true">{{ workspaceFieldTypeIcon(field.type) }}</span>
        <div class="field-summary">
          <span class="field-name">{{ field.label }}</span>
          <span class="field-meta">{{ typeLabel(field.fieldType) }} · {{ field.fieldCode }}</span>
        </div>
        <el-tag v-if="field.status === 'DISABLED'" size="small" type="info" effect="plain">已停用</el-tag>
        <div v-if="canManage" class="field-actions">
          <el-button text :icon="Top" :disabled="index === 0" :aria-label="`上移字段 ${field.label}`" @click="move(field, -1)" />
          <el-button text :icon="Bottom" :disabled="index === definitions.length - 1" :aria-label="`下移字段 ${field.label}`" @click="move(field, 1)" />
          <el-button text :icon="EditPen" :aria-label="`编辑字段 ${field.label}`" @click="openEdit(field)" />
          <el-button text :icon="CopyDocument" :aria-label="`复制字段 ${field.label}`" @click="openDuplicate(field)" />
          <el-button
            text
            :type="field.status === 'DISABLED' ? 'success' : 'warning'"
            :icon="field.status === 'DISABLED' ? CircleCheck : CircleClose"
            :aria-label="`${field.status === 'DISABLED' ? '启用' : '停用'}字段 ${field.label}`"
            @click="toggleStatus(field)"
          />
          <el-button text type="danger" :icon="Delete" :aria-label="`删除字段 ${field.label}`" @click="remove(field)" />
        </div>
      </div>
    </div>

    <p v-if="!canManage" class="permission-note">字段定义由具备字段管理和当前模块维护权限的人员统一管理。</p>

    <CustomFieldDefinitionDialog
      v-model="dialogVisible"
      :definition="editingDefinition"
      :submitting="saving"
      :submit-handler="submitDefinition"
      @saved="$emit('saved', $event)"
    />
  </section>
</template>

<script setup>
import { ref } from 'vue'
import {
  Bottom,
  CircleCheck,
  CircleClose,
  CopyDocument,
  Delete,
  EditPen,
  Plus,
  Top
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  customFieldDefinitionTypeLabel,
} from '../../utils/customFieldDefinitionForm.js'
import { workspaceFieldTypeIcon } from '../../utils/workspaceFieldManager.js'
import CustomFieldDefinitionDialog from './CustomFieldDefinitionDialog.vue'

defineOptions({ name: 'CustomFieldForm' })

const props = defineProps({
  definitions: { type: Array, default: () => [] },
  canManage: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
  saving: { type: Boolean, default: false },
  error: { type: String, default: '' },
  maximumFields: { type: Number, default: 50 },
  actions: { type: Object, required: true }
})
defineEmits(['saved'])
const dialogVisible = ref(false)
const editingDefinition = ref(null)

function typeLabel(type) {
  return customFieldDefinitionTypeLabel(type)
}

function openCreate() {
  editingDefinition.value = null
  dialogVisible.value = true
}

function openEdit(field) {
  editingDefinition.value = field
  dialogVisible.value = true
}

function openDuplicate(field) {
  editingDefinition.value = {
    ...field,
    id: null,
    fieldCode: '',
    key: '',
    fieldName: `${field.fieldName || field.label} 副本`,
    label: `${field.fieldName || field.label} 副本`,
    version: 0
  }
  dialogVisible.value = true
}

async function submitDefinition(payload, source) {
  return source?.id
    ? props.actions.update(source.id, payload)
    : props.actions.create(payload)
}

async function toggleStatus(field) {
  const status = field.status === 'DISABLED' ? 'ENABLED' : 'DISABLED'
  await props.actions.changeStatus(field.id, status)
  ElMessage.success(status === 'ENABLED' ? '字段已启用' : '字段已停用')
}

async function remove(field) {
  try {
    await ElMessageBox.confirm(
      `字段“${field.label}”将被软删除，历史值和审计记录仍会保留。`,
      '删除自定义字段',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  await props.actions.remove(field.id)
  ElMessage.success('字段已删除')
}

async function move(field, direction) {
  const order = props.definitions.map(item => item.id)
  const index = order.indexOf(field.id)
  const target = index + direction
  if (index < 0 || target < 0 || target >= order.length) return
  ;[order[index], order[target]] = [order[target], order[index]]
  await props.actions.reorder(order.map((fieldId, displayOrder) => ({ fieldId, displayOrder })))
}
</script>

<style scoped>
.custom-field-manager {
  display: grid;
  gap: 8px;
  border-top: 1px solid var(--dhic-border-soft);
  padding-top: 10px;
}

.custom-field-heading,
.custom-field-heading > div,
.custom-field-row,
.field-actions {
  display: flex;
  align-items: center;
}

.custom-field-heading {
  justify-content: space-between;
}

.custom-field-heading > div {
  gap: 8px;
}

.custom-field-heading span,
.field-meta,
.permission-note {
  color: var(--dhic-text-muted);
  font-size: 12px;
}

.custom-field-list {
  display: grid;
  max-height: 280px;
  overflow-y: auto;
}

.custom-field-row {
  gap: 8px;
  min-height: 48px;
  border-bottom: 1px solid var(--dhic-border-soft);
}

.field-type-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border: 1px solid var(--dhic-border);
  border-radius: 4px;
  color: var(--dhic-text-secondary);
  font-size: 11px;
}

.field-summary {
  display: grid;
  flex: 1;
  min-width: 0;
}

.field-name,
.field-meta {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.field-actions {
  gap: 1px;
}

.field-actions :deep(.el-button) {
  width: 28px;
  height: 28px;
  margin: 0;
}

.custom-field-empty,
.custom-field-error,
.permission-note {
  padding: 10px;
  text-align: center;
}

.custom-field-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--dhic-danger-soft);
  color: var(--dhic-danger);
  font-size: 12px;
  text-align: left;
}

.permission-note {
  margin: 0;
}
</style>
