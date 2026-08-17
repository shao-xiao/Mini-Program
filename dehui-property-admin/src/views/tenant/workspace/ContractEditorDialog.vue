<template>
  <el-dialog
    :model-value="modelValue"
    :title="contract?.id ? '编辑合同' : '新增合同'"
    width="min(760px, calc(100vw - 48px))"
    class="contract-editor-dialog"
    destroy-on-close
    @update:model-value="$emit('update:modelValue', $event)"
    @open="reset"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <div class="form-section-title">合同信息</div>
      <div class="form-grid">
        <el-form-item label="合同编号" prop="contractNo">
          <el-input v-model.trim="form.contractNo" maxlength="64" placeholder="新增时可留空，由后端生成" />
        </el-form-item>
        <el-form-item label="租户">
          <el-input :model-value="tenant?.tenantName || tenant?.name || '-'" disabled />
        </el-form-item>
        <el-form-item label="签署时间" prop="signedAt">
          <el-date-picker
            v-model="form.signedAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
            placeholder="可选"
          />
        </el-form-item>
        <el-form-item label="生效日期" prop="effectiveDate">
          <el-date-picker v-model="form.effectiveDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
      </div>

      <div class="form-section-title">租赁空间</div>
      <div class="form-grid room-filter-grid">
        <el-form-item label="楼宇筛选">
          <el-select v-model="form.buildingIds" multiple filterable collapse-tags style="width: 100%" @change="reconcileLocation">
            <el-option v-for="item in buildingOptions" :key="item.id" :label="item.label" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="楼层筛选">
          <el-select v-model="form.floorIds" multiple filterable collapse-tags style="width: 100%" @change="reconcileRooms">
            <el-option v-for="item in floorOptions" :key="item.id" :label="item.label" :value="item.id" />
          </el-select>
        </el-form-item>
      </div>
      <el-form-item label="合同房间" prop="roomIds">
        <el-select
          v-model="form.roomIds"
          multiple
          filterable
          collapse-tags
          collapse-tags-tooltip
          style="width: 100%"
          placeholder="请选择合同关联的全部房间"
        >
          <el-option v-for="room in availableRooms" :key="room.id" :label="roomLabel(room)" :value="Number(room.id)" />
        </el-select>
        <p class="form-help">合同房间以当前所选的完整集合保存，入住和退租按该集合整体办理。</p>
      </el-form-item>

      <div class="form-section-title">周期与金额</div>
      <div class="form-grid">
        <el-form-item label="开始日期" prop="startDate">
          <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束日期" prop="endDate">
          <el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="月租金额" prop="rentAmount">
          <el-input-number v-model="form.rentAmount" :min="0" :precision="2" :step="100" style="width: 100%" />
        </el-form-item>
        <el-form-item label="押金" prop="depositAmount">
          <el-input-number v-model="form.depositAmount" :min="0" :precision="2" :step="100" style="width: 100%" />
        </el-form-item>
      </div>

      <el-form-item label="备注" prop="remark">
        <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="500" show-word-limit />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, nextTick, reactive, ref } from 'vue'

defineOptions({ name: 'ContractEditorDialog' })

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  contract: { type: Object, default: null },
  tenant: { type: Object, default: null },
  rooms: { type: Array, default: () => [] },
  saving: { type: Boolean, default: false },
  submitHandler: { type: Function, required: true }
})

const emit = defineEmits(['update:modelValue', 'saved'])
const formRef = ref(null)
const form = reactive(defaultForm())

const rules = {
  roomIds: [{ type: 'array', required: true, min: 1, message: '请至少选择一个房间', trigger: 'change' }],
  startDate: [{ required: true, message: '请选择开始日期', trigger: 'change' }],
  endDate: [
    { required: true, message: '请选择结束日期', trigger: 'change' },
    { validator: validateEndDate, trigger: 'change' }
  ]
}

const selectedRoomIdSet = computed(() => new Set(form.roomIds.map(Number)))
const buildingOptions = computed(() => uniqueOptions(props.rooms, 'buildingId', room => (
  room.buildingName || room.buildingCode || `楼宇 ${room.buildingId}`
)))
const floorOptions = computed(() => uniqueOptions(
  props.rooms.filter(room => !form.buildingIds.length || form.buildingIds.map(Number).includes(Number(room.buildingId))),
  'floorId',
  room => `${room.buildingName ? `${room.buildingName} / ` : ''}${room.floorName || `${room.floorNumber || ''}层` || `楼层 ${room.floorId}`}`
))
const availableRooms = computed(() => props.rooms.filter(room => {
  const selected = selectedRoomIdSet.value.has(Number(room.id))
  const buildingMatched = !form.buildingIds.length || form.buildingIds.map(Number).includes(Number(room.buildingId))
  const floorMatched = !form.floorIds.length || form.floorIds.map(Number).includes(Number(room.floorId))
  const stateAvailable = ['AVAILABLE', 'VACANT'].includes(room.status) || selected
  return buildingMatched && floorMatched && stateAvailable
}))

function defaultForm() {
  return {
    id: null,
    version: null,
    contractNo: '',
    buildingIds: [],
    floorIds: [],
    roomIds: [],
    startDate: '',
    endDate: '',
    effectiveDate: '',
    signedAt: '',
    rentAmount: 0,
    depositAmount: 0,
    remark: ''
  }
}

async function reset() {
  const roomIds = (props.contract?.roomIds || (props.contract?.roomId ? [props.contract.roomId] : [])).map(Number)
  const selectedRooms = props.rooms.filter(room => roomIds.includes(Number(room.id)))
  Object.assign(form, defaultForm(), props.contract ? {
    id: props.contract.id,
    version: props.contract.version ?? null,
    contractNo: props.contract.contractNo || props.contract.contractNumber || props.contract.code || '',
    buildingIds: uniqueNumbers(selectedRooms.map(room => room.buildingId)),
    floorIds: uniqueNumbers(selectedRooms.map(room => room.floorId)),
    roomIds,
    startDate: props.contract.startDate || '',
    endDate: props.contract.endDate || '',
    effectiveDate: props.contract.effectiveDate || props.contract.startDate || '',
    signedAt: normalizeDateTime(props.contract.signedAt),
    rentAmount: Number(props.contract.rentAmount || 0),
    depositAmount: Number(props.contract.depositAmount || 0),
    remark: props.contract.remark || ''
  } : {})
  await nextTick()
  formRef.value?.clearValidate()
}

function reconcileLocation() {
  const buildings = new Set(form.buildingIds.map(Number))
  form.floorIds = form.floorIds.filter(floorId => props.rooms.some(room => (
    Number(room.floorId) === Number(floorId) && buildings.has(Number(room.buildingId))
  )))
  reconcileRooms()
}

function reconcileRooms() {
  const buildings = new Set(form.buildingIds.map(Number))
  const floors = new Set(form.floorIds.map(Number))
  form.roomIds = form.roomIds.filter(roomId => props.rooms.some(room => (
    Number(room.id) === Number(roomId)
      && (!buildings.size || buildings.has(Number(room.buildingId)))
      && (!floors.size || floors.has(Number(room.floorId)))
  )))
}

async function submit() {
  await formRef.value?.validate()
  const payload = {
    contractNo: form.contractNo || undefined,
    tenantId: Number(props.tenant?.id),
    roomIds: form.roomIds.map(Number),
    startDate: form.startDate,
    endDate: form.endDate,
    effectiveDate: form.effectiveDate || form.startDate,
    signedAt: form.signedAt || undefined,
    currency: 'CNY',
    rentAmount: Number(form.rentAmount || 0),
    depositAmount: Number(form.depositAmount || 0),
    remark: form.remark || undefined
  }
  if (form.id) {
    payload.version = Number(form.version)
    payload.changeReason = '租户工作台编辑合同'
  }
  const result = await props.submitHandler(payload, props.contract)
  if (result?.success === false) return
  emit('saved', result)
  emit('update:modelValue', false)
}

function validateEndDate(_rule, value, callback) {
  if (value && form.startDate && value < form.startDate) callback(new Error('结束日期不能早于开始日期'))
  else callback()
}

function roomLabel(room) {
  const location = [room.buildingName, room.floorName, room.roomNumber || room.roomName].filter(Boolean).join(' / ')
  const area = room.area ? ` · ${room.area}㎡` : ''
  return `${location || `房间 ${room.id}`}${area}`
}

function uniqueOptions(records, key, label) {
  const seen = new Set()
  return records.flatMap(record => {
    const id = Number(record[key])
    if (!id || seen.has(id)) return []
    seen.add(id)
    return [{ id, label: label(record) }]
  })
}

function uniqueNumbers(values) {
  return [...new Set(values.map(Number).filter(Number.isFinite))]
}

function normalizeDateTime(value) {
  return value ? String(value).slice(0, 19) : ''
}
</script>

<style scoped>
.contract-editor-dialog :deep(.el-dialog__body) {
  max-height: min(72vh, 760px);
  overflow-y: auto;
  padding-top: 8px;
}

.form-section-title {
  margin: 16px 0 10px;
  border-left: 3px solid var(--dhic-primary);
  padding-left: 9px;
  color: var(--dhic-text);
  font-size: 14px;
  font-weight: 600;
}

.form-section-title:first-child {
  margin-top: 0;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.form-help {
  width: 100%;
  margin: 5px 0 0;
  color: var(--dhic-text-muted);
  font-size: 12px;
}

@media (max-width: 760px) {
  .form-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
