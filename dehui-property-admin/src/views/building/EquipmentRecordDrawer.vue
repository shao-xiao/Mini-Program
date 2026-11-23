<template>
  <el-drawer v-model="visible" title="设备档案" size="min(440px, 96vw)" @closed="returnToList">
    <template v-if="record">
      <div class="record-heading" aria-live="polite" aria-atomic="true">
        <h3>{{ record.equipmentName || record.equipmentCode || '未命名设备' }}</h3>
        <StatusDisplay :value="record.status" :label="status?.label" :tone="status?.tone" />
      </div>
      <dl class="record-fields">
        <div v-for="[label, value] in fields" :key="label">
          <dt>{{ label }}</dt>
          <dd>{{ value }}</dd>
        </div>
      </dl>
    </template>
    <template #footer>
      <nav v-if="record" class="record-navigation" aria-label="浏览本页设备">
        <span>本页 {{ index + 1 }} / {{ rows.length }}</span>
        <div>
          <el-button :icon="ArrowLeft" :disabled="index === 0" @click="recordId = rows[index - 1].id">上一台</el-button>
          <el-button :disabled="index === rows.length - 1" @click="recordId = rows[index + 1].id">
            下一台<el-icon class="el-icon--right"><ArrowRight /></el-icon>
          </el-button>
        </div>
      </nav>
    </template>
  </el-drawer>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import StatusDisplay from '../../components/StatusDisplay.vue'
import { equipmentStatusOptions, equipmentTypeOptions } from '../../workspaceConfigs/equipment'
import { getEquipmentCategory } from '../../utils/equipmentFolders'

const props = defineProps({ rows: { type: Array, required: true } })
const recordId = ref(null)
const visible = ref(false)
let opener

const index = computed(() => props.rows.findIndex(row => row.id === recordId.value))
const record = computed(() => props.rows[index.value])
const status = computed(() => equipmentStatusOptions.find(option => option.value === record.value?.status))
const fields = computed(() => {
  const row = record.value
  if (!row) return []
  const type = equipmentTypeOptions.find(option => option.value === row.equipmentType)
  const originalType = type?.label || row.equipmentType || '未分类'
  const category = getEquipmentCategory(row)
  return [
    ['设备编号', row.equipmentCode || '—'],
    ['设备类型', originalType],
    ...(category.label === originalType ? [] : [['归档分类', category.label], ['归类依据', category.source]]),
    ['所属空间', [row.buildingName, row.floorName, row.roomCode].filter(Boolean).join(' / ')],
    ['位置', row.location],
    ['责任部门', row.responsibleDepartment],
    ['负责人', row.responsibleUserName],
    ['厂家', row.manufacturer],
    ['型号', row.model],
    ['序列号', row.serialNo],
    ['安装日期', row.installDate],
    ['使用年限', row.serviceLifeYears != null ? `${row.serviceLifeYears} 年` : null],
    ['备注', row.remark]
  ].filter(([, value]) => value != null && value !== '')
})

function open(id, trigger) {
  opener = trigger
  recordId.value = id
  visible.value = index.value >= 0
}

function returnToList() {
  recordId.value = null
  if (opener?.isConnected) opener.focus({ preventScroll: true })
  opener = null
}

watch(record, current => { if (!current) visible.value = false })
defineExpose({ open })
</script>

<style scoped>
.record-heading, .record-navigation {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.record-heading { align-items: baseline; padding-bottom: 16px; }
.record-heading h3 { margin: 0; color: var(--dhic-text-primary); font-size: 1.15rem; overflow-wrap: anywhere; }
.record-fields { margin: 0; }
.record-fields > div {
  display: grid;
  grid-template-columns: 6em minmax(0, 1fr);
  gap: 12px;
  padding: 10px 0;
  border-top: 1px solid var(--dhic-border-color-light);
}
.record-fields dt, .record-navigation > span { color: var(--dhic-text-secondary); }
.record-fields dd { margin: 0; white-space: pre-line; overflow-wrap: anywhere; }
.record-navigation { flex-wrap: wrap; }
.record-navigation > span { font-size: .8125rem; font-variant-numeric: tabular-nums; }
@media (pointer: coarse) {
  .record-navigation .el-button { min-height: 44px; }
}
</style>
