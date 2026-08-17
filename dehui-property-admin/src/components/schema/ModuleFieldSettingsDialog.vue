<template>
  <el-dialog v-model="visible" title="模块字段配置" width="920px">
    <el-alert title="这里配置共享字段结构；个人表格视图仅控制列宽、顺序和显隐。" type="info" :closable="false" />
    <el-table :data="fields" row-key="fieldKey" max-height="560">
      <el-table-column prop="label" label="字段" min-width="150" />
      <el-table-column prop="fieldKey" label="fieldKey" min-width="160" />
      <el-table-column prop="dataType" label="类型" width="120" />
      <el-table-column prop="source" label="来源" width="100" />
      <el-table-column label="Surface" min-width="300">
        <template #default="{ row }">
          <el-tag v-for="surface in enabledSurfaces(row)" :key="surface" size="small" class="surface-tag">
            {{ surface }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="90">
        <template #default="{ row }">
          <el-button v-if="row.source === 'SYSTEM'" link type="primary" @click="edit(row)">配置</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-drawer v-model="editing" title="系统字段 Surface" size="520px" append-to-body>
      <el-form v-if="draft" label-width="120px">
        <el-form-item label="fieldKey"><el-input :model-value="draft.fieldKey" disabled /></el-form-item>
        <el-form-item label="字段名称"><el-input v-model="draft.label" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="draft.description" type="textarea" /></el-form-item>
        <el-form-item v-for="surface in surfaces" :key="surface.key" :label="surface.label">
          <el-switch v-model="draft.surfaces[surface.key].visible" />
          <el-checkbox v-if="surface.form" v-model="draft.surfaces[surface.key].editable">可编辑</el-checkbox>
          <el-input-number v-model="draft.surfaces[surface.key].order" :min="0" />
        </el-form-item>
        <el-form-item v-if="['IMAGE', 'ATTACHMENT'].includes(draft.dataType)" label="最大文件数">
          <el-input-number v-model="draft.validation.maxFiles" :min="1" :max="30" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editing = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-drawer>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getModuleFieldSchema, updateModuleSystemField } from '../../api/customFields.js'
import { normalizeModuleSchema } from '../../utils/moduleFieldSchema.js'

const props = defineProps({ moduleKey: { type: String, required: true } })
const visible = defineModel({ type: Boolean, default: false })
const fields = ref([])
const editing = ref(false)
const saving = ref(false)
const draft = ref(null)
const surfaces = [
  { key: 'adminList', label: '后台列表' },
  { key: 'adminForm', label: '后台表单', form: true },
  { key: 'adminDetail', label: '后台详情' },
  { key: 'miniProgramForm', label: '小程序表单', form: true },
  { key: 'miniProgramDetail', label: '小程序详情' }
]

async function load() {
  const schema = await getModuleFieldSchema(props.moduleKey, null)
  fields.value = normalizeModuleSchema(schema)
}
watch(visible, open => {
  if (open) load()
})
function enabledSurfaces(field) {
  return surfaces.filter(item => field.surfaces?.[item.key]?.visible !== false).map(item => item.label)
}
function edit(field) {
  draft.value = structuredClone(field)
  draft.value.surfaces ||= {}
  for (const surface of surfaces) draft.value.surfaces[surface.key] ||= { visible: true, editable: false, order: 0 }
  draft.value.validation ||= {}
  editing.value = true
}
async function save() {
  saving.value = true
  try {
    await updateModuleSystemField(props.moduleKey, draft.value.fieldKey, {
      label: draft.value.label,
      description: draft.value.description,
      required: draft.value.required,
      surfaces: draft.value.surfaces,
      validation: draft.value.validation
    })
    await load()
    editing.value = false
    ElMessage.success('模块字段配置已保存')
  } finally {
    saving.value = false
  }
}
defineExpose({ load })
</script>

<style scoped>
.surface-tag { margin: 2px 4px 2px 0; }
</style>
