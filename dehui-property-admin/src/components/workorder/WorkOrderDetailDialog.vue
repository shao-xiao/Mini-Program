<template>
  <el-dialog
    :model-value="modelValue"
    aria-label="工单详情"
    class="workorder-detail-dialog"
    width="min(920px, calc(100vw - 32px))"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <template #header>
      <div class="dialog-heading">
        <div>
          <h2>工单详情</h2>
          <p>{{ values.code || values.orderNumber || '-' }} · {{ values.title || '未命名工单' }}</p>
        </div>
        <el-tag :type="statusTone" effect="light">{{ statusLabel }}</el-tag>
      </div>
    </template>

    <div class="detail-sections">
      <section class="detail-section detail-section--overview" aria-labelledby="workorder-overview-title">
        <header class="section-heading">
          <div>
            <h3 id="workorder-overview-title">工单概况</h3>
            <p>系统编号、类型和当前流转状态</p>
          </div>
          <span class="source-badge source-badge--system">系统信息</span>
        </header>
        <ModuleSchemaDetail :fields="overviewFields" :values="displayValues" />
      </section>

      <section class="detail-section detail-section--tenant" aria-labelledby="workorder-tenant-title">
        <header class="section-heading">
          <div>
            <h3 id="workorder-tenant-title">租户报修信息</h3>
            <p>由报修人提交，保留原始问题、联系方式和报修附件</p>
          </div>
          <span class="source-badge source-badge--tenant">租户提交</span>
        </header>
        <ModuleSchemaDetail :fields="tenantFields" :values="displayValues" />
      </section>

      <section class="detail-section detail-section--property" aria-labelledby="workorder-property-title">
        <header class="section-heading">
          <div>
            <h3 id="workorder-property-title">物业处理信息</h3>
            <p>由物业员工在派单、维修和完工过程中追加，和租户原始报修信息分开保留</p>
          </div>
          <span class="source-badge source-badge--property">物业提交</span>
        </header>

        <div class="property-summary">
          <div class="summary-item">
            <span>当前负责人</span>
            <strong>{{ values.handlerName || '尚未分配' }}</strong>
          </div>
          <div class="summary-item">
            <span>处理状态</span>
            <strong>{{ statusLabel }}</strong>
          </div>
          <div class="summary-item">
            <span>最后更新</span>
            <strong>{{ formatDateTime(values.updatedTime) }}</strong>
          </div>
        </div>

        <div v-if="propertyCustomFields.length" class="property-custom-fields">
          <div class="subsection-title">
            <strong>物业补充字段</strong>
            <span>以下字段的最后保存来源与物业处理记录一致</span>
          </div>
          <ModuleSchemaDetail :fields="propertyCustomFields" :values="displayValues" />
        </div>

        <div v-if="propertyRecords.length" class="handling-records" aria-label="物业处理记录">
          <article v-for="record in propertyRecords" :key="record.id" class="handling-record">
            <div class="record-rail" aria-hidden="true">
              <span class="record-dot"></span>
            </div>
            <div class="record-content">
              <div class="record-heading">
                <div>
                  <strong>{{ actionLabel(record.action) }}</strong>
                  <span>{{ record.operatorName || '系统' }}</span>
                </div>
                <time>{{ formatDateTime(record.createdTime) }}</time>
              </div>
              <p v-if="record.content" class="record-result">{{ record.content }}</p>
              <div v-if="record.attachments?.length" class="record-files">
                <span class="record-files__label">维修现场照片</span>
                <CustomFieldValueDisplay
                  :field="recordImageField"
                  :value="{ version: 1, files: record.attachments }"
                  mode="detail"
                  image-alt="物业维修现场照片"
                />
              </div>
            </div>
          </article>
        </div>

        <el-empty
          v-else-if="!propertyCustomFields.length"
          :image-size="56"
          description="物业尚未提交处理记录"
        />
      </section>
    </div>
  </el-dialog>
</template>

<script setup>
import { computed } from 'vue'
import ModuleSchemaDetail from '../schema/ModuleSchemaDetail.vue'
import CustomFieldValueDisplay from '../workspace/CustomFieldValueDisplay.vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  fields: { type: Array, default: () => [] },
  values: { type: Object, default: () => ({}) }
})
const emit = defineEmits(['update:modelValue'])

const OVERVIEW_KEYS = new Set(['code', 'title', 'orderType', 'problemCategory', 'status', 'priority'])
const TENANT_KEYS = new Set([
  'contactName',
  'contactPhone',
  'companyName',
  'reportedAt',
  'location',
  'description',
  'images',
  'attachments',
  'remark',
  'createdTime'
])
const PROPERTY_ACTIONS = new Set([
  'ASSIGNED',
  'PROCESSING',
  'PROGRESS',
  'COMPLETED',
  'COMPLETE',
  'CLOSED',
  'CLOSE',
  'STATUS_CHANGE'
])
const STATUS_LABELS = {
  CREATED: '待处理',
  SUBMITTED: '待处理',
  ASSIGNED: '已派单',
  PROCESSING: '处理中',
  COMPLETED: '已完成',
  CLOSED: '已关闭',
  CANCELLED: '已撤回'
}
const STATUS_TONES = {
  CREATED: 'warning',
  SUBMITTED: 'warning',
  ASSIGNED: 'info',
  PROCESSING: 'primary',
  COMPLETED: 'success',
  CLOSED: 'info',
  CANCELLED: 'danger'
}
const ACTION_LABELS = {
  ASSIGNED: '工单派单',
  PROCESSING: '开始处理',
  PROGRESS: '维修处理记录',
  COMPLETED: '完成工单',
  COMPLETE: '完成工单',
  CLOSED: '关闭工单',
  CLOSE: '关闭工单',
  STATUS_CHANGE: '状态调整'
}
const recordImageField = {
  fieldCode: 'propertyRecordImages',
  fieldType: 'IMAGE',
  label: '维修现场照片'
}

const propertyCustomKeySet = computed(() => new Set(props.values.propertyCustomFieldKeys || []))
const overviewFields = computed(() => props.fields.filter(field => OVERVIEW_KEYS.has(field.fieldKey)))
const propertyCustomFields = computed(() => props.fields.filter(field => (
  field.source === 'CUSTOM' && propertyCustomKeySet.value.has(field.fieldKey)
)))
const tenantFields = computed(() => props.fields.filter(field => (
  TENANT_KEYS.has(field.fieldKey)
  || (field.source === 'CUSTOM' && !propertyCustomKeySet.value.has(field.fieldKey))
)))
const propertyRecords = computed(() => (props.values.records || []).filter(record => (
  PROPERTY_ACTIONS.has(String(record.action || '').toUpperCase())
)))
const displayValues = computed(() => ({
  ...props.values,
  reportedAt: formatDateTime(props.values.reportedAt),
  createdTime: formatDateTime(props.values.createdTime),
  updatedTime: formatDateTime(props.values.updatedTime)
}))
const statusLabel = computed(() => STATUS_LABELS[props.values.status] || props.values.status || '-')
const statusTone = computed(() => STATUS_TONES[props.values.status] || 'info')

function actionLabel(action) {
  const value = String(action || '').toUpperCase()
  return ACTION_LABELS[value] || value || '处理记录'
}

function formatDateTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}
</script>

<style scoped>
.dialog-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding-right: 28px;
}

.dialog-heading h2,
.section-heading h3 {
  margin: 0;
  color: #1f2937;
}

.dialog-heading h2 {
  font-size: 18px;
  line-height: 1.35;
}

.dialog-heading p,
.section-heading p {
  margin: 4px 0 0;
  color: #7a8699;
  font-size: 12px;
  line-height: 1.5;
}

.detail-sections {
  display: grid;
  gap: 12px;
}

.detail-section {
  overflow: hidden;
  border: 1px solid #e2e7ee;
  border-radius: 8px;
  background: #fff;
}

.section-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 14px;
  border-bottom: 1px solid #e8ecf2;
  background: #f8fafc;
}

.section-heading h3 {
  font-size: 15px;
  line-height: 1.35;
}

.detail-section--tenant .section-heading {
  border-left: 3px solid #d99100;
  background: #fffaf0;
}

.detail-section--property .section-heading {
  border-left: 3px solid #2563eb;
  background: #f4f8ff;
}

.source-badge {
  flex: 0 0 auto;
  padding: 3px 8px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.4;
}

.source-badge--system {
  color: #596579;
  background: #edf1f6;
}

.source-badge--tenant {
  color: #8a5b00;
  background: #fff0c2;
}

.source-badge--property {
  color: #1d4ed8;
  background: #dbeafe;
}

.detail-section :deep(.el-descriptions) {
  margin: 0;
}

.detail-section :deep(.el-descriptions__body) {
  background: transparent;
}

.detail-section :deep(.el-descriptions__cell) {
  padding: 10px 12px !important;
}

.detail-section :deep(.el-descriptions__label) {
  width: 112px;
  color: #596579;
  font-weight: 600;
  background: #fafbfc !important;
}

.property-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  border-bottom: 1px solid #e8ecf2;
}

.summary-item {
  display: grid;
  gap: 4px;
  padding: 10px 14px;
  border-right: 1px solid #e8ecf2;
}

.summary-item:last-child {
  border-right: 0;
}

.summary-item span {
  color: #7a8699;
  font-size: 12px;
}

.summary-item strong {
  overflow: hidden;
  color: #293548;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.property-custom-fields {
  border-bottom: 1px solid #e8ecf2;
}

.subsection-title {
  display: flex;
  align-items: baseline;
  gap: 10px;
  padding: 10px 14px;
  color: #344054;
  font-size: 13px;
}

.subsection-title span {
  color: #8490a2;
  font-size: 12px;
}

.handling-records {
  padding: 8px 14px 12px;
}

.handling-record {
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr);
}

.record-rail {
  position: relative;
}

.record-rail::after {
  position: absolute;
  top: 18px;
  bottom: -8px;
  left: 5px;
  width: 1px;
  background: #dce3ec;
  content: '';
}

.handling-record:last-child .record-rail::after {
  display: none;
}

.record-dot {
  position: absolute;
  top: 14px;
  left: 2px;
  width: 7px;
  height: 7px;
  border: 2px solid #fff;
  border-radius: 50%;
  background: #2563eb;
  box-shadow: 0 0 0 1px #8fb6f6;
}

.record-content {
  min-width: 0;
  padding: 9px 0 12px 8px;
}

.record-heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.record-heading div {
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-width: 0;
}

.record-heading strong {
  color: #293548;
  font-size: 13px;
}

.record-heading span,
.record-heading time {
  color: #8490a2;
  font-size: 12px;
}

.record-result {
  margin: 7px 0 0;
  padding: 8px 10px;
  border-radius: 6px;
  color: #344054;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  background: #f7f9fc;
}

.record-files {
  display: grid;
  gap: 7px;
  margin-top: 8px;
}

.record-files__label {
  color: #596579;
  font-size: 12px;
  font-weight: 600;
}

@media (max-width: 720px) {
  .section-heading {
    align-items: flex-start;
  }

  .property-summary {
    grid-template-columns: 1fr;
  }

  .summary-item {
    border-right: 0;
    border-bottom: 1px solid #e8ecf2;
  }

  .summary-item:last-child {
    border-bottom: 0;
  }

  .detail-section :deep(.el-descriptions__label) {
    width: 96px;
  }

  .record-heading {
    align-items: flex-start;
    flex-direction: column;
    gap: 3px;
  }
}
</style>
