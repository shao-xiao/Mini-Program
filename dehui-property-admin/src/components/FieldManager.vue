<template>
  <el-popover placement="bottom-start" width="min(460px, calc(100vw - 48px))" trigger="click" popper-class="dhic-view-popper">
    <template #reference>
      <el-button class="field-manager-button" :icon="Setting">
        字段
        <span class="field-count">{{ visibleCount }}/{{ configurableColumns.length }}</span>
      </el-button>
    </template>

    <div class="field-manager">
      <div class="field-manager-header">
        <div>
          <strong>字段管理</strong>
          <span>显示、排序并固定当前视图字段</span>
        </div>
        <el-tag v-if="resolvedDirty" size="small" type="warning" effect="plain">视图未保存</el-tag>
      </div>

      <el-tabs v-if="resolvedAllowColumnLayout" v-model="activeSection" class="field-manager-tabs">
        <el-tab-pane label="显示字段" name="visibility">
          <FieldVisibilityList
            :columns="configurableColumns"
            :visible-columns="resolvedVisibleColumns"
            @update:visible-columns="updateVisibleColumns"
          />
        </el-tab-pane>
        <el-tab-pane label="排序与固定" name="layout">
          <FieldOrderList
            :columns="configurableColumns"
            :column-order="resolvedColumnOrder"
            :fixed-columns="resolvedFixedColumns"
            @update:column-order="updateColumnOrder"
            @update:fixed-columns="updateFixedColumns"
          />
        </el-tab-pane>
      </el-tabs>
      <FieldVisibilityList
        v-else
        :columns="configurableColumns"
        :visible-columns="resolvedVisibleColumns"
        @update:visible-columns="updateVisibleColumns"
      />

      <CustomFieldForm
        v-if="resolvedAllowCustomFields"
        :definitions="resolvedCustomFieldDefinitions"
        :can-manage="resolvedCanManageCustomFields"
        :loading="resolvedCustomFieldLoading"
        :saving="resolvedCustomFieldSaving"
        :error="resolvedCustomFieldError"
        :actions="customFieldActions"
      />

      <div v-if="resolvedSaveable" class="field-manager-footer">
        <span>{{ resolvedDirty ? '当前视图有未保存调整' : '字段配置已保存' }}</span>
        <el-button type="primary" size="small" :disabled="!resolvedDirty" @click="save">保存到当前视图</el-button>
      </div>
    </div>
  </el-popover>
</template>

<script setup>
import { computed, getCurrentInstance, ref } from 'vue'
import { Setting } from '@element-plus/icons-vue'
import { configurableWorkspaceFields } from '../utils/workspaceFieldManager.js'
import CustomFieldForm from './workspace/CustomFieldForm.vue'
import FieldOrderList from './workspace/FieldOrderList.vue'
import FieldVisibilityList from './workspace/FieldVisibilityList.vue'
import { useWorkspaceToolbar } from './workspace/workspaceToolbarContext.js'

defineOptions({ name: 'FieldManager' })

const props = defineProps({
  columns: { type: Array, default: null },
  visibleColumns: { type: Array, default: null },
  columnOrder: { type: Array, default: null },
  fixedColumns: { type: Object, default: null },
  customColumns: { type: Array, default: null },
  customFieldDefinitions: { type: Array, default: null },
  canManageCustomFields: { type: Boolean, default: undefined },
  customFieldLoading: { type: Boolean, default: undefined },
  customFieldSaving: { type: Boolean, default: undefined },
  customFieldError: { type: String, default: undefined },
  allowCustomFields: { type: Boolean, default: undefined },
  allowColumnLayout: { type: Boolean, default: undefined },
  saveable: { type: Boolean, default: undefined },
  dirty: { type: Boolean, default: undefined }
})
const emit = defineEmits([
  'update:visibleColumns',
  'update:columnOrder',
  'update:fixedColumns',
  'update:customColumns',
  'create:custom-column',
  'create:custom-field',
  'update:custom-field',
  'change-custom-field-status',
  'delete:custom-field',
  'reorder:custom-fields',
  'retry:custom-fields',
  'save'
])
const instance = getCurrentInstance()
const controller = useWorkspaceToolbar('FieldManager')
const activeSection = ref('visibility')
const resolvedColumns = computed(() => resolveProp('columns', controller.fields.columns, []))
const configurableColumns = computed(() => configurableWorkspaceFields(resolvedColumns.value))
const resolvedVisibleColumns = computed(() => resolveProp('visibleColumns', controller.fields.visibleColumns, []))
const resolvedColumnOrder = computed(() => resolveProp('columnOrder', controller.fields.columnOrder, []))
const resolvedFixedColumns = computed(() => resolveProp('fixedColumns', controller.fields.fixedColumns, {}))
const resolvedCustomColumns = computed(() => resolveProp('customColumns', controller.fields.customColumns, []))
const resolvedCustomFieldDefinitions = computed(() => resolveProp(
  'customFieldDefinitions',
  controller.fields.customFieldDefinitions,
  resolvedCustomColumns.value
))
const resolvedCanManageCustomFields = computed(() => resolveProp(
  'canManageCustomFields',
  controller.fields.canManageCustomFields,
  false
))
const resolvedCustomFieldLoading = computed(() => resolveProp('customFieldLoading', controller.fields.customFieldLoading, false))
const resolvedCustomFieldSaving = computed(() => resolveProp('customFieldSaving', controller.fields.customFieldSaving, false))
const resolvedCustomFieldError = computed(() => resolveProp('customFieldError', controller.fields.customFieldError, ''))
const resolvedAllowCustomFields = computed(() => resolveProp('allowCustomFields', controller.fields.allowCustomFields, false))
const resolvedAllowColumnLayout = computed(() => resolveProp('allowColumnLayout', controller.fields.allowColumnLayout, false))
const resolvedSaveable = computed(() => resolveProp('saveable', controller.fields.saveable, false))
const resolvedDirty = computed(() => resolveProp('dirty', controller.fields.dirty, false))
const visibleCount = computed(() => configurableColumns.value.filter(column => (
  column.required || resolvedVisibleColumns.value.includes(column.key)
)).length)
const customFieldActions = {
  create: payload => dispatchResult(controller.fields.createCustomField, 'create:custom-field', payload),
  update: (fieldId, payload) => dispatchResult(controller.fields.updateCustomField, 'update:custom-field', { fieldId, payload }),
  changeStatus: (fieldId, status) => dispatchResult(
    controller.fields.changeCustomFieldStatus,
    'change-custom-field-status',
    { fieldId, status }
  ),
  remove: fieldId => dispatchResult(controller.fields.deleteCustomField, 'delete:custom-field', fieldId),
  reorder: order => dispatchResult(controller.fields.reorderCustomFields, 'reorder:custom-fields', order),
  retry: () => dispatchResult(controller.fields.retryCustomFields, 'retry:custom-fields')
}

function resolveProp(name, contextValue, fallback) {
  if (Object.hasOwn(instance?.vnode?.props || {}, name)) return props[name]
  return contextValue ?? fallback
}

function dispatch(contextAction, eventName, value) {
  if (controller.provided) contextAction(value)
  else emit(eventName, value)
}

function dispatchResult(contextAction, eventName, value) {
  if (controller.provided) return contextAction(value)
  emit(eventName, value)
  return Promise.resolve(value)
}

function updateVisibleColumns(value) {
  dispatch(controller.fields.updateVisibleColumns, 'update:visibleColumns', value)
}

function updateColumnOrder(value) {
  dispatch(controller.fields.updateColumnOrder, 'update:columnOrder', value)
}

function updateFixedColumns(value) {
  dispatch(controller.fields.updateFixedColumns, 'update:fixedColumns', value)
}

function updateCustomColumns(value) {
  dispatch(controller.fields.updateCustomColumns, 'update:customColumns', value)
}

function createCustomColumn(value) {
  dispatch(controller.fields.createCustomColumn, 'create:custom-column', value)
}

function save() {
  if (controller.provided) controller.fields.save()
  else emit('save')
}
</script>

<style scoped>
.field-manager-button {
  border-color: transparent !important;
  background: transparent !important;
  box-shadow: none !important;
}

.field-manager-button:hover {
  color: var(--dhic-primary) !important;
  background: var(--dhic-primary-soft) !important;
}

.field-count {
  margin-left: 4px;
  color: var(--dhic-text-muted);
  font-size: 12px;
}

.field-manager {
  display: grid;
  gap: 10px;
}

.field-manager-header,
.field-manager-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.field-manager-header > div {
  display: grid;
  gap: 2px;
}

.field-manager-header strong {
  color: var(--dhic-text);
}

.field-manager-header span,
.field-manager-footer span {
  color: var(--dhic-text-muted);
  font-size: 12px;
}

.field-manager-tabs :deep(.el-tabs__header) {
  margin-bottom: 10px;
}

.field-manager-footer {
  border-top: 1px solid var(--dhic-border-soft);
  padding-top: 10px;
}
</style>
