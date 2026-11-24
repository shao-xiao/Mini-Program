<template>
  <div class="space-actions">
    <div v-if="canBrowseDirectory" class="layout-switch" aria-label="空间显示方式">
      <button :aria-pressed="directoryMode" @click="emit('layout', true)">空间目录</button>
      <button :aria-pressed="!directoryMode" @click="emit('layout', false)">表格</button>
    </div>
    <slot />
    <el-button :icon="Refresh" :loading="loading" @click="emit('refresh')">
      刷新
    </el-button>
  </div>
</template>

<script setup>
import { Refresh } from '@element-plus/icons-vue'
defineProps({ canBrowseDirectory: Boolean, directoryMode: Boolean, loading: Boolean })
const emit = defineEmits(['layout', 'refresh'])
</script>

<style scoped>
.space-actions {display:flex;align-items:center;gap:8px;flex-wrap:wrap;}
.layout-switch { display:inline-flex; padding:2px; background:var(--dhic-surface); border:1px solid var(--dhic-border-color); border-radius:6px; }
.layout-switch button {padding:5px 10px; background:transparent; border:0; border-radius:4px; color:var(--dhic-text-regular); font:inherit; font-size:13px; cursor:pointer;}
.layout-switch button[aria-pressed=true] { background:var(--dhic-row-selected); color:var(--dhic-text-primary); font-weight:600; }
</style>
