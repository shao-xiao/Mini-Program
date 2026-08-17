<template>
  <div class="workspace-view-manager">
    <div class="view-manager-label">
      <el-icon><Grid /></el-icon>
      <span>当前视图</span>
    </div>

    <el-select
      class="view-select"
      :model-value="activeViewId"
      aria-label="选择数据视图"
      @change="changeView"
    >
      <el-option
        v-for="view in views"
        :key="view.id"
        :label="view.name"
        :value="view.id"
      >
        <div class="view-option">
          <span>{{ view.name }}</span>
          <el-tag v-if="view.system" size="small" effect="plain">预设</el-tag>
        </div>
      </el-option>
      <template #footer>
        <button type="button" class="create-view-option" @click="createView">
          <el-icon><Plus /></el-icon>
          <span>新建视图</span>
        </button>
      </template>
    </el-select>

    <div class="view-actions">
      <span
        class="view-save-status"
        :class="{ dirty: viewDirty }"
        role="status"
        aria-live="polite"
        aria-atomic="true"
      >
        <el-icon>
          <WarningFilled v-if="viewDirty" />
          <CircleCheckFilled v-else />
        </el-icon>
        {{ viewDirty ? '有未保存修改' : '已保存' }}
      </span>
      <el-tooltip content="复制当前视图" placement="bottom">
        <el-button text circle :icon="CopyDocument" aria-label="复制当前视图" @click="duplicateView" />
      </el-tooltip>
      <el-tooltip :content="viewDirty ? '保存当前视图' : '当前视图已保存'" placement="bottom">
        <span class="view-action-wrap">
          <el-button
            text
            circle
            :icon="Check"
            :disabled="!viewDirty"
            :aria-label="viewDirty ? '保存当前视图，当前有未保存修改' : '当前视图已保存'"
            @click="$emit('save')"
          />
        </span>
      </el-tooltip>
      <el-tooltip v-if="!activeView?.system" content="重命名视图" placement="bottom">
        <el-button text circle :icon="EditPen" aria-label="重命名视图" @click="renameView" />
      </el-tooltip>
      <el-tooltip v-if="!activeView?.system" content="删除视图" placement="bottom">
        <el-button text circle :icon="Delete" aria-label="删除视图" @click="deleteView" />
      </el-tooltip>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import {
  Check,
  CircleCheckFilled,
  CopyDocument,
  Delete,
  EditPen,
  Grid,
  Plus,
  WarningFilled
} from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'

defineOptions({ name: 'WorkspaceViewManager' })

const props = defineProps({
  views: { type: Array, default: () => [] },
  activeViewId: { type: String, default: 'default' },
  viewDirty: { type: Boolean, default: false }
})

const emit = defineEmits(['change', 'create', 'duplicate', 'rename', 'delete', 'save'])

const activeView = computed(() => props.views.find(view => view.id === props.activeViewId))

function changeView(viewId) {
  if (viewId) emit('change', String(viewId))
}

async function createView() {
  const result = await ElMessageBox.prompt('输入新视图名称', '新建视图', {
    confirmButtonText: '创建',
    cancelButtonText: '取消',
    inputPlaceholder: '例如：维修设备',
    inputValidator: value => Boolean(String(value || '').trim()) || '请输入视图名称'
  }).catch(() => null)

  if (result?.value) emit('create', result.value.trim())
}

async function renameView() {
  if (!activeView.value || activeView.value.system) return
  const result = await ElMessageBox.prompt('修改当前视图名称', '重命名视图', {
    confirmButtonText: '保存',
    cancelButtonText: '取消',
    inputValue: activeView.value.name,
    inputValidator: value => Boolean(String(value || '').trim()) || '请输入视图名称'
  }).catch(() => null)

  if (result?.value) emit('rename', activeView.value.id, result.value.trim())
}

async function duplicateView() {
  if (!activeView.value) return
  const result = await ElMessageBox.prompt('输入复制后的视图名称', '复制视图', {
    confirmButtonText: '复制',
    cancelButtonText: '取消',
    inputValue: `${activeView.value.name} 副本`,
    inputValidator: value => Boolean(String(value || '').trim()) || '请输入视图名称'
  }).catch(() => null)

  if (result?.value) emit('duplicate', activeView.value.id, result.value.trim())
}

async function deleteView() {
  if (!activeView.value || activeView.value.system) return
  const confirmed = await ElMessageBox.confirm(
    `确认删除视图“${activeView.value.name}”？`,
    '删除视图',
    { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
  ).then(() => true).catch(() => false)

  if (confirmed) emit('delete', activeView.value.id)
}
</script>

<style scoped>
.workspace-view-manager {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 38px;
  background: #fff;
}

.view-manager-label {
  display: flex;
  align-items: center;
  gap: 7px;
  flex: 0 0 auto;
  color: #4b5563;
  font-size: 13px;
  font-weight: 600;
}

.view-select {
  width: min(320px, 100%);
}

.view-select :deep(.el-select__wrapper) {
  min-height: 32px;
  border-radius: 4px;
  box-shadow: 0 0 0 1px #d9dee7 inset;
}

.view-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  flex: 0 0 auto;
}

.view-action-wrap {
  display: inline-flex;
}

.view-save-status {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 24px;
  padding: 0 8px;
  border: 1px solid #ccebd9;
  border-radius: 12px;
  background: #f2fbf6;
  color: #16834a;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.view-save-status.dirty {
  border-color: #f3d6a2;
  background: #fff8eb;
  color: #a35b00;
}

.view-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.create-view-option {
  display: flex;
  align-items: center;
  gap: 7px;
  width: 100%;
  min-height: 32px;
  padding: 5px 8px;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: #d93025;
  cursor: pointer;
  font: inherit;
  text-align: left;
}

.create-view-option:hover {
  background: #fff7f6;
}

@media (max-width: 720px) {
  .workspace-view-manager {
    flex-wrap: wrap;
  }

  .view-select {
    flex: 1 1 180px;
  }

  .view-actions {
    margin-left: auto;
  }
}
</style>
