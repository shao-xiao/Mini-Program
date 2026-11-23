<template>
  <section class="area-browser" aria-label="停车区域目录">
    <header class="directory-heading">
      <div class="location">
        <button v-if="area" class="back" @click="emit('navigate', '')">停车区域</button>
        <span v-if="area" aria-hidden="true">›</span>
        <h2>{{ area ? areaLabel(area) : '停车区域' }}</h2>
      </div>
      <span v-if="!loading && !error" class="total">
        <span v-if="!area" class="totals-by-status">空余 {{ totals.free }} · 已出租 {{ totals.rented }} · </span>
        {{ area ? '本区' : '当前视图' }} <b>{{ area ? active?.total || 0 : rows.length }}</b> 个车位
      </span>
    </header>
    <div v-if="error" role="alert" class="state">{{ error }} <el-button @click="emit('retry')">重新加载</el-button></div>
    <p v-else-if="loading" role="status" class="state">正在读取车位…</p>
    <template v-else>
      <div v-if="!area" class="zone-grid">
        <button v-for="zone in zones" :key="zone.key" class="zone" @click="emit('navigate', zone.key)">
          <span class="folder" aria-hidden="true"><b>{{ zone.key.length === 1 ? zone.key : 'P' }}</b></span>
          <strong>{{ zone.label }}</strong>
          <span class="zone-total">{{ zone.total }} 个车位</span>
          <span class="zone-counts">空余 {{ zone.groups.free.length }} · 已出租 {{ zone.groups.rented.length }}</span>
        </button>
      </div>
      <template v-else-if="active">
        <div class="filters" aria-label="按车位状态查看">
          <button :aria-pressed="filter === ''" @click="filter = ''">全部 {{ active.total }}</button>
          <button v-for="group in filters" :key="group.key" :aria-pressed="filter === group.key" @click="filter = group.key">
            {{ group.label }} {{ active.groups[group.key].length }}
          </button>
        </div>
        <section v-for="group in sections" :key="group.key" class="space-section">
          <header><h3>{{ group.label }}</h3><span class="rule"></span><span>{{ active.groups[group.key].length }} 个车位</span></header>
          <div class="space-grid">
            <button v-for="space in active.groups[group.key]" :key="space.id" class="space" :class="group.key"
              aria-haspopup="dialog" :aria-label="space.spaceNo + ' 号车位，' + group.label + '，查看详情'" @click="open(space)">
              <svg viewBox="0 0 40 34" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round">
                <path d="M4 5v25h32V5" opacity=".6" />
                <g v-if="group.key === 'rented'">
                  <rect x="12" y="4" width="16" height="22" rx="5" fill="currentColor" fill-opacity=".12"/>
                  <path d="m15 12 1-4h8l1 4zm1 8h8m-12-5h-2m18 0h2"/>
                </g>
                <path v-else-if="group.key === 'free'" d="M17 24V10h5a4 4 0 0 1 0 8h-5" stroke-width="2.8"/>
                <path v-else d="m15 11 10 11m0-11L15 22"/>
              </svg>
              <b>{{ space.spaceNo || space.code || space.id }}</b>
            </button>
          </div>
        </section>
        <p v-if="!sections.length" class="state">当前条件下没有车位</p>
      </template>
      <p v-else class="state">当前视图中没有此区域的车位</p>
    </template>
    <el-drawer v-model="drawer" title="车位详情" direction="rtl" size="min(420px, 96vw)">
      <template v-if="selected">
        <h3 class="detail-title">{{ selected.spaceNo || selected.code }} 号车位</h3>
        <dl class="details">
          <div v-for="[label, value] in fields" :key="label"><dt>{{ label }}</dt><dd>{{ value ?? '—' }}</dd></div>
        </dl>
      </template>
    </el-drawer>
  </section>
</template>
<script setup>
import { computed, ref, watch } from 'vue'
import { areaLabel, buildParkingDirectory, parkingArea, parkingGroup, parkingGroups } from './parkingDirectory.js'
import { parkingSpaceTypeOptions, workspaceOptionLabel } from '../../workspaceConfigs/parkingMeetingWorkspaceConfig.js'

const props = defineProps({
  rows: { type: Array, required: true }, area: { type: String, default: '' },
  loading: Boolean, error: String, customSort: Boolean
})
const emit = defineEmits(['navigate', 'retry'])
const zones = computed(() => buildParkingDirectory(props.rows, props.customSort))
const totals = computed(() => ({
  free: zones.value.reduce((sum, zone) => sum + zone.groups.free.length, 0),
  rented: zones.value.reduce((sum, zone) => sum + zone.groups.rented.length, 0)
}))
const active = computed(() => zones.value.find(zone => zone.key === props.area))
const filter = ref('')
const filters = computed(() => parkingGroups.filter(group => ['free', 'rented'].includes(group.key) || active.value?.groups[group.key].length))
const sections = computed(() => parkingGroups.filter(group =>
  active.value?.groups[group.key].length && (!filter.value || group.key === filter.value)))
const drawer = ref(false)
const selectedId = ref(null)
const selected = computed(() => props.rows.find(row => row.id === selectedId.value))
function open(space) {
  selectedId.value = space.id
  drawer.value = true
}
watch(() => props.area, () => { filter.value = ''; drawer.value = false })
watch(selected, row => { if (!row) drawer.value = false })
const yuan = value => value == null ? null : '¥' + Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const fields = computed(() => {
  const row = selected.value
  if (!row) return []
  const group = parkingGroups.find(group => group.key === parkingGroup(row))
  return [
    ['区域', areaLabel(parkingArea(row))], ['楼层 / 位置', row.floor || row.location], ['状态', group.label],
    ['车位类型', row.typeText || workspaceOptionLabel(parkingSpaceTypeOptions, row.type)],
    ['月租标准', yuan(row.monthlyRate)],
    ...(row.assignmentId == null ? [] : [
      ['使用方', row.partyNameSnapshot], ['车牌号', row.plateNo], ['停车月费', yuan(row.monthlyFee)],
      ['绑定开始', row.effectiveFrom], ['绑定结束', row.effectiveTo]
    ]), ['备注', row.remark || '—']
  ]
})
</script>
<style scoped>
.area-browser { padding: 4px 18px 18px; min-width: 0; color: var(--dhic-text-primary); }
.directory-heading,.location,.space-section header,.filters { display:flex; align-items:center; gap:10px; }
.directory-heading { justify-content:space-between; min-height:44px; border-bottom:1px solid var(--dhic-border-color-light); }
h2,h3 { margin:0; font-size:15px; } .location { color:var(--dhic-text-regular); }
button { font:inherit; color:inherit; background:none; border:0; cursor:pointer; }
button:focus-visible { outline:2px solid var(--dhic-focus-ring); outline-offset:-2px; }
.back { padding:8px 0; }.back:hover { text-decoration:underline; }
.total,.zone-total,.zone-counts,.space-section header>span,.filters { font-size:12px; color:var(--dhic-text-secondary); }
.zone-grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(min(160px,100%),1fr)); gap:12px; padding-top:16px; }
.zone,.space { display:flex; align-items:center; flex-direction:column; border-radius:8px; }
.zone { padding:12px 4px; gap:4px; }.zone:hover,.space:hover { background:var(--dhic-row-hover); }
.zone:active,.space:active { background:var(--dhic-row-selected); }
.zone strong {font-size:15px;} .folder { display:grid; place-items:center; width:68px; height:45px; margin:6px 0 5px; position:relative; isolation:isolate; }
.folder::before,.folder::after { content:''; position:absolute; z-index:-1; border-radius:4px; }
.folder::before { inset:-4px 1px 0; background:#e5ad3d; clip-path:polygon(0 0,34% 0,43% 13%,100% 13%,100% 100%,0 100%); }
.folder::after { inset:6px 0 0; background:linear-gradient(#ffe59b,#ffca55); border:1px solid #d3a13d; }
.folder b { font-size:21px; color:#785819; padding-top:7px; }
.filters { flex-wrap:wrap; margin:12px 0; gap:6px; }.filters button { padding:6px 12px; border-radius:5px; }
.filters button[aria-pressed=true] { background:var(--dhic-row-selected); color:var(--dhic-text-primary); font-weight:600; }
.space-section { margin:16px 0 8px; }.space-section h3 { font-size:13px; }.rule {flex:1; border-top:1px solid var(--dhic-border-color-light);}
.space-grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(min(68px,100%),1fr)); gap:6px; margin-top:6px; }
.space { padding:8px 3px; gap:3px; min-height:58px; }.space svg { width:34px; height:29px; color:#75818c; }
.free svg { color:#36765d; }.rented svg { color:#876131; }.space b { font-size:12px; overflow-wrap:anywhere; }
.state { padding:30px 0; text-align:center; color:var(--dhic-text-secondary); }
.detail-title { font-size:20px; }.details>div { display:flex; justify-content:space-between; gap:16px; padding:10px 0; border-bottom:1px solid var(--dhic-border-color-light); }
dt { color:var(--dhic-text-secondary); } dd {margin:0; text-align:right; overflow-wrap:anywhere;}
</style>
