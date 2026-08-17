<template>
  <span v-if="entries.length" class="import-export-manager">
    <template v-for="entry in entries" :key="entry.key">
      <el-dropdown
        v-if="entry.type === 'group'"
        trigger="click"
        @command="activateActionById"
      >
        <el-button :icon="Download">
          {{ entry.label }}
          <el-icon class="el-icon--right"><ArrowDown /></el-icon>
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item
              v-for="action in entry.actions"
              :key="action.id"
              :command="action.id"
              :disabled="isDisabled(action.id)"
            >
              {{ action.label }}
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>

      <el-button
        v-else
        :type="entry.action.tone"
        :icon="actionIcon(entry.action)"
        :loading="isLoading(entry.action.id)"
        :disabled="isDisabled(entry.action.id)"
        @click="activateAction(entry.action)"
      >
        {{ entry.action.label }}
      </el-button>
    </template>

    <input
      v-for="action in importActions"
      :key="`input-${action.id}`"
      :ref="element => setFileInput(action.id, element)"
      type="file"
      :accept="action.accept || ''"
      class="manager-file-input"
      @change="event => handleFileChange(action, event)"
    />
  </span>
</template>

<script setup>
import { computed } from 'vue'
import {
  ArrowDown,
  Document,
  Download,
  Operation,
  Upload
} from '@element-plus/icons-vue'
import { hasPermission } from '../utils/permission'
import { permittedWorkspaceActions } from '../workspaceConfigs/workspaceConfig'

defineOptions({ name: 'ImportExportManager' })

const props = defineProps({
  workspaceConfig: { type: Object, default: null },
  actions: { type: Array, default: () => [] },
  loadingActionIds: { type: Array, default: () => [] },
  disabledActionIds: { type: Array, default: () => [] }
})

const emit = defineEmits(['action', 'file'])
const fileInputs = new Map()

const sourceActions = computed(() => (
  props.actions.length ? props.actions : (props.workspaceConfig?.importExport || [])
))
const visibleActions = computed(() => permittedWorkspaceActions(
  props.workspaceConfig,
  sourceActions.value,
  hasPermission
))
const importActions = computed(() => visibleActions.value.filter(action => action.kind === 'import'))

const entries = computed(() => {
  const result = []
  const groups = new Map()
  visibleActions.value.forEach(action => {
    if (!action.group) {
      result.push({ key: action.id, type: 'action', action })
      return
    }
    let entry = groups.get(action.group)
    if (!entry) {
      entry = {
        key: `group-${action.group}`,
        type: 'group',
        label: action.groupLabel || action.label,
        actions: []
      }
      groups.set(action.group, entry)
      result.push(entry)
    }
    entry.actions.push(action)
  })
  return result
})

function actionIcon(action) {
  if (action.icon === 'upload' || action.kind === 'import') return Upload
  if (action.icon === 'template' || action.kind === 'template') return Document
  if (action.icon === 'batch' || action.kind === 'batch') return Operation
  return Download
}

function setFileInput(actionId, element) {
  if (element) fileInputs.set(actionId, element)
  else fileInputs.delete(actionId)
}

function isLoading(actionId) {
  return props.loadingActionIds.includes(actionId)
}

function isDisabled(actionId) {
  return props.disabledActionIds.includes(actionId)
}

function activateActionById(actionId) {
  const action = visibleActions.value.find(item => item.id === actionId)
  if (action) activateAction(action)
}

function activateAction(action) {
  if (action.kind === 'import') {
    fileInputs.get(action.id)?.click()
    return
  }
  emit('action', action.id)
}

function handleFileChange(action, event) {
  const file = event.target.files?.[0]
  if (file) emit('file', { actionId: action.id, file })
  event.target.value = ''
}
</script>

<style scoped>
.import-export-manager {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.manager-file-input {
  display: none;
}
</style>
