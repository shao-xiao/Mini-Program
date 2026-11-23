<template>
  <section ref="browser" class="equipment-browser" aria-label="按设备类型浏览">
    <header class="browser-header">
      <nav aria-label="文件夹位置">
        <template v-if="activeFolder">
          <button type="button" class="back-link" @click="goBack">设备类型</button>
          <el-icon aria-hidden="true"><ArrowRight /></el-icon>
        </template>
        <h2 ref="heading" tabindex="-1">{{ activeFolder?.label ?? '设备类型' }}</h2>
      </nav>
      <span v-if="!loading && !errorText" class="count">{{ activeFolder ? `${activeFolder.count} 台设备` : `${folders.length} 个类型` }}</span>
    </header>

    <div v-if="errorText" class="browser-state" role="alert">
      <span>{{ errorText }}</span><el-button @click="emit('retry')">重新加载</el-button>
    </div>
    <p v-else-if="loading" role="status" class="browser-state">正在加载…</p>
    <ul v-else-if="activeFolder ? rows.length : folders.length" class="icon-grid" :aria-label="activeFolder ? '设备' : '设备类型文件夹'">
      <template v-if="!activeFolder">
        <li v-for="folder in folders" :key="folder.key">
          <button type="button" class="tile" :data-folder-key="folder.key" :aria-label="`打开${folder.label}文件夹，${folder.count} 台设备`" @click="enter(folder)">
            <span class="folder-art" aria-hidden="true">
              <span class="folder-sheet"></span>
              <span class="folder-front"><EquipmentTypeIcon :folder="folder" /></span>
            </span>
            <span class="tile-name">{{ folder.label }}</span>
            <span class="tile-meta">{{ folder.count }} 台</span>
          </button>
        </li>
      </template>
      <template v-else>
        <li v-for="row in rows" :key="row.id">
          <button type="button" class="tile" aria-haspopup="dialog" :aria-label="`查看设备：${row.equipmentName || row.equipmentCode || row.id}`"
            @click="emit('open-record', row, $event.currentTarget)">
            <span class="record-art" aria-hidden="true"><EquipmentTypeIcon :folder="activeFolder" /></span>
            <span class="tile-name">{{ row.equipmentName || row.equipmentCode || '未命名设备' }}</span>
            <span class="tile-meta">{{ row.equipmentCode || row.location || `ID ${row.id}` }}</span>
          </button>
        </li>
      </template>
    </ul>
    <p v-else class="browser-state" role="status">{{ activeFolder ? '此类型下没有符合条件的设备' : '没有符合条件的设备' }}</p>
  </section>
</template>

<script setup>
import { nextTick, ref, watch } from 'vue'
import { ArrowRight } from '@element-plus/icons-vue'
import EquipmentTypeIcon from './EquipmentTypeIcon.vue'

const props = defineProps({
  folders: { type: Array, required: true },
  activeFolder: { type: Object, default: null },
  rows: { type: Array, default: () => [] },
  loading: Boolean,
  errorText: { type: String, default: '' }
})
const emit = defineEmits(['open-folder', 'back', 'open-record', 'retry'])
const browser = ref(null)
const heading = ref(null)
let focusAfterNavigation = false

function enter(folder) {
  focusAfterNavigation = true
  emit('open-folder', folder)
}

function goBack() {
  focusAfterNavigation = true
  emit('back')
}

watch(() => props.activeFolder?.key ?? null, async (key, previous) => {
  if (!focusAfterNavigation && !browser.value?.contains(document.activeElement)) return
  focusAfterNavigation = false
  await nextTick()
  const folders = browser.value?.querySelectorAll('[data-folder-key]') ?? []
  const trigger = key === null ? Array.from(folders).find(button => button.dataset.folderKey === String(previous)) : null
  const target = trigger || heading.value
  target?.focus({ preventScroll: true })
})
</script>

<style scoped>
.equipment-browser {
  min-width: 0; padding: 0 16px 12px; color: var(--dhic-text-primary);
  background: var(--dhic-card-bg);
}
.browser-header, .browser-header nav { display: flex; align-items: center; gap: 8px; }
.browser-header { min-height: 48px; justify-content: space-between; border-bottom: 1px solid var(--dhic-border-color-light); }
.browser-header nav { min-width: 0; }
.browser-header nav > .el-icon { color: var(--dhic-text-secondary); font-size: 12px; }
.browser-header h2 { margin: 0; font-size: 15px; line-height: 1.4; font-weight: 650; overflow-wrap: anywhere; }
.browser-header h2:focus { outline: none; }
.back-link, .tile { border: none; background: transparent; color: inherit; font: inherit; cursor: pointer; -webkit-tap-highlight-color: transparent; }
.back-link { min-height: 44px; padding: 0 2px; color: var(--dhic-text-regular); font-size: 14px; white-space: nowrap; }
.count, .tile-meta { color: var(--dhic-text-secondary); font-size: 12px; line-height: 1.4; font-variant-numeric: tabular-nums; }
.count { white-space: nowrap; }
.icon-grid {
  display: grid; grid-template-columns: repeat(auto-fill, minmax(min(116px, 100%), 1fr));
  align-items: start; gap: 10px 8px; margin: 0; padding: 14px 0 4px; list-style: none;
}
.icon-grid li { min-width: 0; }
.tile {
  width: 100%; min-height: 116px; padding: 9px 6px; border-radius: 8px;
  display: flex; flex-direction: column; align-items: center; justify-content: flex-start;
  text-align: center; touch-action: manipulation;
}
.tile-name, .tile-meta { max-width: 100%; overflow-wrap: anywhere; }
.tile-name { margin-top: 8px; font-size: 14px; line-height: 1.4; font-weight: 550; }
.tile-meta { margin-top: 1px; }
.folder-art, .record-art { position: relative; height: 54px; width: 70px; flex: none; transition: transform 140ms ease-out; }
.folder-art::before {
  content: ''; position: absolute; inset: 3px 1px 2px;
  background: linear-gradient(#ffda71, #e6ae37);
  clip-path: polygon(0 0, 34% 0, 41% 9%, 96% 9%, 100% 14%, 100% 100%, 0 100%);
  border-radius: 5px;
}
.folder-sheet { position: absolute; inset: 9px 3px 6px; border-radius: 4px; background: #fff3bd; }
.folder-front {
  position: absolute; inset: 12px 0 2px; display: grid; place-items: center;
  border-radius: 4px 4px 6px 6px; border: 1px solid #d3a13d;
  background: linear-gradient(#ffe499, #ffca55);
  color: #755015;
  box-shadow: inset 0 1px 0 #fff1b3, var(--dhic-shadow-subtle);
}
.folder-front svg { width: 25px; height: 25px; }
.record-art {
  height: 49px; width: 45px; margin: 3px 0 2px; display: grid; place-items: center;
  color: var(--dhic-sidebar-bg-secondary); border: 1px solid var(--dhic-table-heading-rule); border-radius: 4px 9px 4px 4px;
  background: linear-gradient(150deg, var(--dhic-surface) 40%, var(--dhic-table-heading-bg)); box-shadow: var(--dhic-shadow-subtle);
}
.record-art::after {
  content: ''; position: absolute; right: -1px; top: -1px; width: 10px; height: 10px;
  background: linear-gradient(45deg, var(--dhic-border-color) 47%, var(--dhic-table-heading-rule) 49%, var(--dhic-card-bg) 54%); border-radius: 0 0 0 3px;
}
.record-art svg { width: 26px; height: 26px; }
@media (hover: hover) {
  .tile:hover { background: var(--dhic-row-hover); }
  .tile:hover .folder-art, .tile:hover .record-art { transform: translateY(-2px); }
  .back-link:hover { text-decoration: underline; }
}
.tile:active { background: var(--dhic-row-selected); }
.tile:active .folder-art, .tile:active .record-art { transform: scale(.96); }
.tile:focus-visible, .back-link:focus-visible { outline: 2px solid var(--dhic-focus-ring); outline-offset: -2px; }
.browser-state { min-height: 120px; display: flex; align-items: center; justify-content: center; gap: 10px; margin: 0; color: var(--dhic-text-secondary); }
@media (max-width: 600px) {
  .equipment-browser { padding-inline: 10px; }
  .icon-grid { grid-template-columns: repeat(auto-fill, minmax(min(92px, 100%), 1fr)); gap: 4px; }
}
@media (prefers-reduced-motion: reduce) {
  .folder-art, .record-art { transition: none; }
  .tile:is(:hover, :active) .folder-art, .tile:is(:hover, :active) .record-art { transform: none; }
}
</style>
