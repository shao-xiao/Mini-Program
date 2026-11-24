<template>
  <section class="directory" aria-label="楼宇空间目录">
    <header class="directory-head">
      <nav aria-label="当前位置">
        <button v-if="building" @click="emit('building', null)">楼宇目录</button><h2 v-else>楼宇目录</h2>
        <template v-if="building">
          <span aria-hidden="true">›</span>
          <button v-if="inRooms" @click="backToFloors">{{ building.buildingName }}</button>
          <h2 v-else>{{ building.buildingName }}</h2>
        </template>
        <template v-if="inRooms"><span aria-hidden="true">›</span><h2>{{ floor ? floorName(floor) : '全部房间' }}</h2></template>
      </nav>
      <span v-if="!loading && !error" class="summary">共 <b>{{ scope.length }}</b> 间 · 未入住 {{ counts.VACANT || 0 }} · 已入住 {{ counts.RENTED || 0 }}</span>
      <slot name="actions" />
    </header>
    <div v-if="loading" class="state" role="status">正在读取空间目录…</div>
    <div v-else-if="error" class="state" role="alert">{{ error }} <el-button @click="emit('retry')">重新加载</el-button></div>
    <template v-else-if="!inRooms">
      <div v-if="building" class="floor-tools"><h3>楼层目录</h3><button @click="allRooms = true">查看全部房间 ›</button></div>
      <div v-for="section in folderSections" :key="section.label" class="section">
        <header class="rule"><h3>{{ section.label }}</h3><i></i><span>{{ section.items.length }} {{ building ? '层' : '栋' }}</span></header>
        <div class="folders">
          <button v-for="item in section.items" :key="item.id" class="tile" @click="building ? emit('floor', item.id) : emit('building', item.id)">
            <SpaceDirectoryIcon :kind="item.iconKind" />
            <strong>{{ building ? floorName(item) : item.buildingName }}</strong>
            <small>{{ item.rooms.length }} 间 · 已入住 {{ item.occupied }}</small>
          </button>
        </div>
      </div>
      <p v-if="!folderSections.length" class="state">{{ building ? '暂无登记楼层' : '暂无楼宇' }}</p>
    </template>
    <template v-else>
      <div :class="{ 'floor-workspace': hasB2Drawing }">
        <BasementDrawing v-if="hasB2Drawing" :key="floor.id" :floor-id="Number(floor.id)" :ledger-area="floor.totalArea" />
        <component :is="hasB2Drawing ? 'details' : 'section'" class="room-list" aria-label="房间台账">
          <summary v-if="hasB2Drawing">房间台账 <small>{{ scope.length }} 间</small></summary>
          <div class="filters" aria-label="筛选房间状态">
            <button :aria-pressed="!filter" @click="filter = ''">全部 {{ scope.length }}</button>
            <button v-for="g in options" :key="g.value" :aria-pressed="filter === g.value" @click="filter = g.value">{{ g.label }} {{ counts[g.value] || 0 }}</button>
          </div>
          <section v-for="g in shown" :key="g.value" class="section">
            <header class="rule"><h3>{{ g.label }}</h3><i></i><span>{{ g.rooms.length }} 间房间</span></header>
            <div class="rooms">
              <button v-for="room in g.rooms" :key="room.id" class="tile room" aria-haspopup="dialog"
                :aria-label="`查看房间 ${room.roomNumber || room.roomCode}，${g.label}`" @click="show(room)">
                <svg viewBox="0 0 46 40" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round" aria-hidden="true" :class="g.value.toLowerCase()">
                  <path d="M7 35h32M11 35V6h25v29" />
                  <path d="M15 34V10l15-2v27z" fill="currentColor" fill-opacity=".11"/><path d="M25 22h2"/>
                  <path v-if="g.value === 'RENTED'" d="M17 17h6v4h-6z" fill="currentColor"/>
                </svg>
                <strong>{{ room.roomNumber || room.roomCode }}</strong><small>{{ areaText(room.area) }}</small>
              </button>
            </div>
          </section>
          <p v-if="!shown.length" class="state">{{ hasB2Drawing && !scope.length ? 'B2 尚无登记房间' : '当前范围内没有符合条件的房间' }}</p>
        </component>
      </div>
    </template>
    <el-drawer v-model="drawer" title="房间详情" direction="rtl" size="min(420px, 96vw)">
      <template v-if="selected">
        <h2>{{ selected.roomNumber || selected.roomCode }}</h2>
        <dl><div v-for="[label, value] in fields" :key="label"><dt>{{ label }}</dt><dd>{{ value ?? '—' }}</dd></div></dl>
      </template>
    </el-drawer>
  </section>
</template>
<script setup>
import { computed, ref, watch, defineAsyncComponent } from 'vue'
import SpaceDirectoryIcon from './SpaceDirectoryIcon.vue'
import { roomStatusOptions, roomTypeOptions, roomContractStageOptions } from '../../workspaceConfigs/buildingWorkspaceConfig.js'

const BasementDrawing = defineAsyncComponent(() => import('../../components/spaces/BasementDrawing.vue'))
const props = defineProps({
  buildings: Array, floors: Array, rooms: Array, building: Object, floor: Object, hasB2Drawing: Boolean, loading: Boolean, error: String
})
const emit = defineEmits(['building', 'floor', 'retry'])
const allRooms = ref(false)
const filter = ref('')
const drawer = ref(false)
const selectedId = ref(null)
const floorName = floor => floor.floorName || (floor.floorNumber < 0 ? 'B' + Math.abs(floor.floorNumber) : floor.floorNumber + 'F')
const areaText = value => value == null || value === '' ? '—' : Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 2 }) + ' ㎡'
const status = room => room.status === 'AVAILABLE' ? 'VACANT' : room.status || 'UNKNOWN'
const labelFor = (options, value) => options.find(option => option.value === value)?.label ?? value
const statusName = value => ({ VACANT: '未入住', RENTED: '已入住', UNKNOWN: '待确认' }[value] || labelFor(roomStatusOptions, value))
const inRooms = computed(() => Boolean(props.building && (props.floor || allRooms.value)))
const scope = computed(() => props.rooms.filter(room =>
  (!props.building || Number(room.buildingId) === Number(props.building.id)) &&
  (!props.floor || Number(room.floorId) === Number(props.floor.id))))
const counts = computed(() => {
  const result = {}
  for (const room of scope.value) result[status(room)] = (result[status(room)] || 0) + 1
  return result
})
const options = computed(() => [...new Set([...roomStatusOptions.map(option => option.value), ...Object.keys(counts.value)])]
  .filter(value => ['VACANT','RENTED'].includes(value) || counts.value[value])
  .map(value => ({value, label: statusName(value)})))
const collator = new Intl.Collator('zh-CN', {numeric: true})
const shown = computed(() => options.value.filter(group => counts.value[group.value] && (!filter.value || group.value === filter.value))
  .map(group => ({ ...group, rooms: scope.value.filter(room => status(room) === group.value)
    .sort((a,b) => collator.compare(a.roomNumber || a.roomCode || '', b.roomNumber || b.roomCode || '') || a.id-b.id) })))
function floorIconKind(floor) {
  if (/屋面|屋顶/.test(floor.floorName || '')) return 'roof'
  return floor.floorNumber < 0 ? 'basement' : 'floor'
}
const folderSections = computed(() => {
  const byId = new Map()
  for (const room of scope.value) {
    const id = Number(props.building ? room.floorId : room.buildingId)
    if (!byId.has(id)) byId.set(id, [])
    byId.get(id).push(room)
  }
  const items = (props.building ? props.floors : props.buildings).map(item => {
    const rooms = byId.get(Number(item.id)) || []
    const iconKind = props.building ? floorIconKind(item) : 'building'
    return { ...item, iconKind, rooms, occupied: rooms.filter(room => status(room) === 'RENTED').length }
  })
  if (!props.building) return items.length ? [{ label:'在册楼宇', items }] : []
  const upper = [], lower = [], basement = [], roofs = [], other = []
  for (const item of items) {
    const number = Number(item.floorNumber)
    if (item.iconKind === 'roof') roofs.push(item)
    else if (number >= 6) upper.push(item)
    else if (number >= 1) lower.push(item)
    else if (number < 0) basement.push(item)
    else other.push(item)
  }
  const ascending = (a, b) => a.floorNumber - b.floorNumber
  return [
    { label: roofs.length ? '6F 至屋面' : '6F 及以上', items: [...upper.sort(ascending), ...roofs.sort(ascending)] },
    { label: '1F 至 5F', items: lower.sort(ascending) },
    { label: '其他楼层', items: other },
    { label: '地下楼层', items: basement.sort((a, b) => b.floorNumber - a.floorNumber) }
  ].filter(section => section.items.length)
})
function backToFloors() { allRooms.value = false; emit('floor', null) }
function show(room) { selectedId.value = room.id; drawer.value = true }
const selected = computed(() => scope.value.find(room => room.id === selectedId.value))
const fields = computed(() => {
  const r = selected.value
  if (!r) return []
  return [['楼宇',r.buildingName],['楼层',r.floorName],['房间编号',r.roomCode],['房间名称',r.roomName],
    ['入住状态',statusName(status(r))],['房间用途',labelFor(roomTypeOptions,r.roomType)],['房间面积',areaText(r.area)],
    ['物业计费面积',areaText(r.propertyFeeArea)],['合同阶段',labelFor(roomContractStageOptions,r.contractStage)],['备注',r.description || '—']]
})
watch(() => [props.building?.id, props.floor?.id], () => { allRooms.value = false; filter.value = ''; drawer.value = false })
watch(allRooms, () => { filter.value = '' })
watch(selected, room => { if (!room) drawer.value = false })
</script>
<style scoped>
.directory { container-type:inline-size; background:var(--dhic-surface); color:var(--dhic-text-primary); border:1px solid var(--dhic-border-color); border-radius:8px; padding:6px 18px 20px; }
.directory-head,.directory-head nav,.rule,.filters,.floor-tools { display:flex; align-items:center; gap:10px; }
.directory-head .summary {margin-inline-start:auto;}
.directory-head { flex-wrap:wrap; justify-content:space-between; min-height:48px; border-bottom:1px solid var(--dhic-border-color-light); }
h2,h3 { margin:0; font-size:15px; } h3 {font-size:13px;}
button { cursor:pointer; font:inherit; color:inherit; background:none; border:0; }
button:focus-visible {outline:2px solid var(--dhic-focus-ring);outline-offset:-2px;}
.directory-head button,.floor-tools button {min-height:38px; padding:3px; color:var(--dhic-text-regular);}
.directory-head button:hover,.floor-tools button:hover { text-decoration:underline; }
small,.summary,.rule span,.state {font-size:12px;color:var(--dhic-text-secondary);}
.floor-tools {justify-content:space-between; margin-top:6px;}
.rule {margin:12px 0 4px;} .rule i {flex:1; border-top:1px solid var(--dhic-border-color-light);}
.folders,.rooms {display:grid;gap:6px;grid-template-columns:repeat(auto-fill,minmax(min(128px,100%),1fr));}
.rooms {grid-template-columns:repeat(auto-fill,minmax(min(82px,100%),1fr));}
.tile {display:flex; flex-direction:column;align-items:center;gap:4px;border-radius:6px;padding:10px 4px;}
.tile strong {font-size:13px;overflow-wrap:anywhere;} .tile:hover {background:var(--dhic-row-hover);}
.tile:active,.filters button[aria-pressed=true] {background:var(--dhic-row-selected);}
.filters {flex-wrap:wrap;gap:6px;margin-top:12px;}.filters button {border-radius:5px;padding:6px 12px;font-size:13px;}
.room svg {width:38px;height:34px;color:#747c86;} .room .vacant {color:#38775e;} .room .rented {color:#8b6435;}
.state {padding:24px 0;text-align:center;} dl div {display:flex;justify-content:space-between;gap:16px;padding:10px 0;border-bottom:1px solid var(--dhic-border-color-light);}
dt {color:var(--dhic-text-secondary);}dd {margin:0;text-align:right;overflow-wrap:anywhere;}
.floor-workspace {display:grid; grid-template-columns:minmax(0,1fr); gap:16px; padding-top:10px;}
.floor-workspace .room-list {border-top:1px solid var(--dhic-border-color-light);padding-top:8px;}
.room-list summary {cursor:pointer;padding:8px 0;font-size:13px;font-weight:600;}
.room-list summary small {margin-left:6px;font-weight:400;}
</style>
