<template>
  <el-dialog
    :model-value="modelValue"
    aria-label="巡检详情"
    class="inspection-detail-dialog"
    width="min(920px, calc(100vw - 32px))"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <template #header>
      <div class="dialog-heading">
        <div>
          <h2>巡检详情</h2>
          <p>{{ values.code || `巡检记录 ${values.id || '-'}` }} · {{ typeLabel }} · {{ values.target || '-' }}</p>
        </div>
        <div class="dialog-status">
          <el-tag :type="resultTone" effect="light">{{ resultLabel }}</el-tag>
          <el-tag :type="statusTone" effect="light">{{ statusLabel }}</el-tag>
        </div>
      </div>
    </template>

    <div class="detail-sections">
      <section class="detail-section detail-section--mobile" aria-labelledby="inspection-mobile-title">
        <header class="section-heading">
          <div>
            <h3 id="inspection-mobile-title">小程序巡检信息</h3>
            <p>由物业员工在移动端登记，原始巡检内容与现场照片完整保留</p>
          </div>
          <span class="source-badge source-badge--mobile">移动端提交</span>
        </header>

        <ModuleSchemaDetail :fields="registrationFields" :values="displayValues" />

        <div class="photo-section">
          <div class="subsection-title">
            <strong>现场照片</strong>
            <span>{{ originalAttachments.length }} 张</span>
          </div>
          <CustomFieldValueDisplay
            v-if="originalAttachments.length"
            :field="originalImageField"
            :value="{ version: 1, files: originalAttachments }"
            mode="detail"
            image-alt="小程序巡检现场照片"
          />
          <div v-else class="empty-inline">本次巡检未上传现场照片</div>
        </div>
      </section>

      <section class="detail-section detail-section--property" aria-labelledby="inspection-property-title">
        <header class="section-heading">
          <div>
            <h3 id="inspection-property-title">物业处理信息</h3>
            <p>反馈、复查与关闭记录按时间追加，和原始巡检信息分开归档</p>
          </div>
          <span class="source-badge source-badge--property">物业处理</span>
        </header>

        <div class="property-summary">
          <div class="summary-item">
            <span>当前状态</span>
            <strong>{{ statusLabel }}</strong>
          </div>
          <div class="summary-item">
            <span>最后处理人</span>
            <strong>{{ latestHistory?.operatorName || '暂无处理人' }}</strong>
          </div>
          <div class="summary-item">
            <span>最后处理时间</span>
            <strong>{{ formatDateTime(latestHistory?.operatedAt || values.updatedAt) }}</strong>
          </div>
        </div>

        <div v-if="propertyHistory.length" class="handling-records" aria-label="物业处理记录">
          <article v-for="record in propertyHistory" :key="record.id" class="handling-record">
            <div class="record-rail" aria-hidden="true">
              <span class="record-dot"></span>
            </div>
            <div class="record-content">
              <div class="record-heading">
                <div>
                  <strong>{{ actionLabel(record.action) }}</strong>
                  <span>{{ record.operatorName || '系统' }}</span>
                </div>
                <time>{{ formatDateTime(record.operatedAt) }}</time>
              </div>
              <p v-if="record.content" class="record-result">{{ record.content }}</p>
              <div v-if="record.resolutionImages?.length" class="record-files">
                <div class="record-files__heading">
                  <span>处理后照片</span>
                  <small>{{ record.resolutionImages.length }} 张</small>
                </div>
                <CustomFieldValueDisplay
                  :field="resolutionImageField"
                  :value="{ version: 1, files: record.resolutionImages }"
                  mode="detail"
                  image-alt="物业巡检处理后照片"
                />
              </div>
            </div>
          </article>
        </div>

        <div v-else class="empty-property">
          <strong>暂无物业处理记录</strong>
          <span>后续反馈、复查和关闭信息将在这里按时间展示</span>
        </div>
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

const PHOTO_KEYS = new Set(['images', 'attachments'])
const RESULT_LABELS = { NORMAL: '正常', ABNORMAL: '异常', PENDING: '待处理' }
const RESULT_TONES = { NORMAL: 'success', ABNORMAL: 'danger', PENDING: 'warning' }
const STATUS_LABELS = { OPEN: '待处理', CLOSED: '已关闭' }
const STATUS_TONES = { OPEN: 'warning', CLOSED: 'success' }
const ACTION_LABELS = {
  UPDATE: '补充处理反馈',
  CLOSE: '关闭巡检',
  CLOSED: '关闭巡检',
  REVIEW: '复查巡检'
}

const originalImageField = {
  fieldCode: 'inspectionImages',
  fieldType: 'IMAGE',
  label: '现场照片'
}
const resolutionImageField = {
  fieldCode: 'resolutionImages',
  fieldType: 'IMAGE',
  label: '处理后照片'
}

const registrationFields = computed(() => props.fields.filter(field => !PHOTO_KEYS.has(field.fieldKey)))
const originalAttachments = computed(() => props.values.attachments || props.values.images || [])
const propertyHistory = computed(() => (props.values.history || []).filter(record => (
  String(record.action || '').toUpperCase() !== 'CREATE'
)))
const latestHistory = computed(() => propertyHistory.value.at(-1) || null)
const displayValues = computed(() => ({
  ...props.values,
  createdAt: formatDateTime(props.values.createdAt),
  updatedAt: formatDateTime(props.values.updatedAt)
}))
const resultLabel = computed(() => RESULT_LABELS[props.values.result] || props.values.result || '-')
const resultTone = computed(() => RESULT_TONES[props.values.result] || 'info')
const statusLabel = computed(() => STATUS_LABELS[props.values.status] || props.values.status || '-')
const statusTone = computed(() => STATUS_TONES[props.values.status] || 'info')
const typeLabel = computed(() => {
  const field = props.fields.find(item => item.fieldKey === 'inspectionType')
  const option = field?.options?.find(item => String(item.value) === String(props.values.inspectionType))
  return option?.label || props.values.inspectionType || '未设置类型'
})

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

.dialog-status {
  display: flex;
  flex: 0 0 auto;
  gap: 6px;
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
}

.section-heading h3 {
  font-size: 15px;
  line-height: 1.35;
}

.detail-section--mobile .section-heading {
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

.source-badge--mobile {
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

.photo-section {
  display: grid;
  gap: 10px;
  padding: 12px 14px 14px;
  border-top: 1px solid #e8ecf2;
}

.subsection-title,
.record-files__heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  color: #344054;
  font-size: 13px;
}

.subsection-title span,
.record-files__heading small {
  color: #8490a2;
  font-size: 12px;
  font-weight: 400;
}

.empty-inline {
  padding: 13px;
  border: 1px dashed #dce3ec;
  border-radius: 6px;
  color: #8490a2;
  font-size: 12px;
  text-align: center;
  background: #fafbfc;
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

.empty-property {
  display: grid;
  gap: 4px;
  padding: 18px 14px;
  color: #344054;
  text-align: center;
}

.empty-property span {
  color: #8490a2;
  font-size: 12px;
}

@media (max-width: 720px) {
  .dialog-heading,
  .section-heading,
  .record-heading {
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
    flex-direction: column;
    gap: 3px;
  }
}
</style>
