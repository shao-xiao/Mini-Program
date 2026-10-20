<template>
  <div class="daily-report-page">
    <header class="page-header">
      <div><h2>运营日报</h2><p>{{ today }} · 园区运营实时总览<span v-if="updatedAt"> · 更新于 {{ updatedAt }}</span></p></div>
      <div class="header-actions"><el-tag :type="loading ? 'info' : risks.length ? 'warning' : incomplete ? 'info' : 'success'">{{ loading ? '汇总中' : risks.length ? `${risks.length} 类待关注` : incomplete ? '数据待补全' : '暂无待关注事项' }}</el-tag><el-button :disabled="loading || !updatedAt || !readyCount" @click="exportReport">下载日报</el-button><el-button type="primary" :loading="loading" @click="loadReport">刷新日报</el-button></div>
    </header>
    <el-alert v-if="incomplete && !loading" class="source-alert" :title="`已汇总 ${readyCount} / ${sourceList.length} 项数据源；未授权或加载失败的指标显示为 —，不计作零。`" type="info" :closable="false" show-icon />
    <section class="overview" :aria-busy="loading">
      <div class="overview-heading"><span class="eyebrow">今日运营摘要</span><span class="scope-badge">当前账号可见范围</span></div>
      <p>{{ summary }}</p>
      <div class="overview-links"><el-button v-for="item in shortcuts" :key="item.path" link @click="go(item.path)">{{ item.label }} →</el-button></div>
    </section>
    <section class="kpi-grid">
      <div v-for="item in kpis" :key="item.label" class="kpi-card"><div class="card-label">{{ item.label }}<small>{{ item.scope }}</small></div><strong :class="item.tone">{{ item.value }}</strong><p>{{ item.note }}</p></div>
    </section>
    <div class="report-grid">
      <section class="panel risk-panel"><header><div><h3>优先处理</h3><p>根据实际待办生成，点击进入对应业务</p></div><span class="scope-badge">{{ risks.length }} 类事项</span></header>
        <div v-if="risks.length" class="risk-list"><div v-for="(item, index) in risks" :key="item.title" class="risk-item"><span class="risk-number">{{ index + 1 }}</span><div><h4>{{ item.title }}<el-tag size="small" :type="item.tone">{{ item.level }}</el-tag></h4><p>{{ item.text }}</p></div><el-button link type="primary" @click="go(item.path)">去处理 →</el-button></div></div>
        <el-empty v-else :description="loading ? '正在汇总业务数据' : incomplete ? '已加载范围内暂无待关注事项；部分数据尚未获取' : '当前可见范围暂无待关注事项'" :image-size="56" />
      </section>
      <section class="panel"><header><div><h3>收缴与租赁</h3><p>累计账单余额 · 按流水收款时间统计</p></div></header><div class="metric-list"><div v-for="item in financeRows" :key="item.label"><span>{{ item.label }}</span><strong :class="item.tone">{{ item.value }}</strong></div></div></section>
      <section class="panel"><header><div><h3>工单与巡检</h3><p>今日发生与累计待办分别列示</p></div></header><div class="metric-list"><div v-for="item in operationRows" :key="item.label"><span>{{ item.label }}</span><strong>{{ item.value }}</strong></div></div></section>
      <section class="panel"><header><div><h3>设备与能耗</h3><p>设备实时状态 · 最近有记录的能耗账期</p></div></header><div class="metric-list"><div v-for="item in assetRows" :key="item.label"><span>{{ item.label }}</span><strong :class="item.tone">{{ item.value }}</strong></div></div></section>
      <section class="panel trend-panel"><header><div><h3>近 7 天新增工单</h3><p>按创建日期统计，不包含未来日期</p></div><span class="scope-badge">{{ ready('workorders') ? '单位：单' : hint('workorders') }}</span></header><div v-if="ready('workorders')" class="trend-bars" role="img" :aria-label="work.trend.map(row => `${row.day}新增${row.count}单`).join('；')"><div v-for="row in work.trend" :key="row.day" class="trend-column"><strong>{{ row.count }}</strong><div class="bar-track"><progress :value="row.count" :max="trendMax" :aria-label="`${row.day}新增${row.count}单`"></progress></div><span>{{ row.day.slice(5) }}</span></div></div><el-empty v-else :description="hint('workorders')" :image-size="48" /></section>
    </div>
    <div class="detail-grid">
      <section class="panel"><header><div><h3>逾期账单</h3><p>到期最早的 5 笔未结清账单</p></div><el-button v-if="ready('finance')" link type="primary" @click="go('/bills')">全部账单 →</el-button></header><WorkspaceDataGrid :rows="sourceData('overdue', [])" :columns="billColumns" row-key="id" :show-index="false" density="compact" data-mode="client" :empty-text="ready('overdue') ? '暂无逾期账单' : hint('overdue')" aria-label="日报逾期账单" /></section>
      <section class="panel"><header><div><h3>待跟进工单</h3><p>优先关注紧急工单，再按创建时间排序</p></div><el-button v-if="ready('workorders')" link type="primary" @click="go('/workorders')">全部工单 →</el-button></header><WorkspaceDataGrid :rows="work.rows" :columns="workColumns" row-key="id" :show-index="false" density="compact" data-mode="client" :empty-text="ready('workorders') ? '暂无待跟进工单' : hint('workorders')" aria-label="日报工单待办" /></section>
      <section class="panel contract-panel"><header><div><h3>未来 30 天合同到期</h3><p>仅展示生效合同，提前安排续租与交接</p></div><el-button v-if="ready('contracts')" link type="primary" @click="go('/contracts')">合同管理 →</el-button></header><WorkspaceDataGrid :rows="contracts.expiring.slice(0, 6)" :columns="contractColumns" row-key="id" :show-index="false" density="compact" data-mode="client" :empty-text="ready('contracts') ? '未来 30 天暂无生效合同到期' : hint('contracts')" aria-label="日报合同到期提醒" /></section>
    </div>
    <footer class="data-sources"><strong>数据覆盖</strong><span v-for="source in sourceList" :key="source.label" :class="source.status">{{ source.label }} · {{ source.status === 'ready' ? '已更新' : source.status === 'denied' ? '未授权' : source.status === 'error' ? '加载失败' : '待加载' }}</span><p>日报为当前业务数据的实时汇总；行动提醒由待办状态和到期日期生成。无历史快照时不展示历史指标。</p></footer>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import request from '../../utils/request'
import { hasPermission } from '../../utils/permission'
import { loadOperationsSources, reportDate, summarizeWorkOrders, summarizeContracts, summarizePayments } from '../../utils/operationsReport'
import { downloadBlob } from '../../utils/downloadBlob'
import WorkspaceDataGrid from '../../components/workspace/WorkspaceDataGrid.vue'

const router = useRouter()
const loading = ref(true)
const updatedAt = ref('')
const today = ref(reportDate())
const sources = ref({})
let requestVersion = 0
const sourceList = computed(() => Object.values(sources.value))
const readyCount = computed(() => sourceList.value.filter(item => item.status === 'ready').length)
const incomplete = computed(() => readyCount.value < sourceList.value.length)
const ready = key => sources.value[key]?.status === 'ready'
const hint = key => sources.value[key]?.status === 'denied' ? '未授权查看' : sources.value[key]?.status === 'error' ? '加载失败，请刷新重试' : '正在加载'
const sourceData = (key, fallback = {}) => ready(key) ? sources.value[key].data : fallback
const count = (key, value) => ready(key) ? Number(value || 0).toLocaleString('zh-CN') : '—'
const money = value => Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const amount = (key, value) => ready(key) ? `¥ ${money(value)}` : '—'
const finance = computed(() => sourceData('finance'))
const rooms = computed(() => sourceData('rooms'))
const work = computed(() => summarizeWorkOrders(sourceData('workorders', []), today.value))
const contracts = computed(() => summarizeContracts(sourceData('contracts', []), today.value))
const payments = computed(() => summarizePayments(sourceData('payments', []), today.value))
const inspections = computed(() => sourceData('inspections'))
const equipment = computed(() => sourceData('equipment'))
const energy = computed(() => sourceData('energy'))
const latestEnergy = computed(() => [...(energy.value.trend || [])].sort((a, b) => String(b.periodMonth).localeCompare(String(a.periodMonth)))[0])
const trendMax = computed(() => Math.max(1, ...work.value.trend.map(row => row.count)))
const shortcuts = computed(() => [
  ['bill:view', '财务账单', '/bills'], ['workorder:view', '工单管理', '/workorders'], ['inspection:view', '巡检管理', '/inspections'], ['contract:view', '合同管理', '/contracts'], ['energy:view', '能耗统计', '/assets/energy?tab=statistics']
].filter(([permission]) => hasPermission(permission)).map(([, label, path]) => ({ label, path })))
const kpis = computed(() => [
  { label: '房间出租率', scope: '实时', value: ready('rooms') ? `${rooms.value.rentalRate}%` : '—', note: ready('rooms') ? `已租 ${rooms.value.rentedRoomCount} / 共 ${rooms.value.roomCount} 间` : hint('rooms') },
  { label: '累计待收金额', scope: '累计', value: amount('finance', finance.value.unpaidAmount), note: ready('finance') ? `未结清 ${finance.value.unpaidCount} 笔` : hint('finance'), tone: 'danger' },
  { label: '逾期账单', scope: '截至今日', value: count('finance', finance.value.overdueCount), note: '已过到期日且未结清', tone: 'danger' },
  { label: '待跟进工单', scope: '累计', value: count('workorders', work.value.pending), note: ready('workorders') ? `今日新增 ${work.value.today} 单` : hint('workorders') },
  { label: '异常巡检待处理', scope: '累计', value: count('inspections', inspections.value.abnormal?.totalCount), note: '异常且尚未关闭', tone: 'warning' },
  { label: '即将到期合同', scope: '未来30天', value: count('contracts', contracts.value.expiring.length), note: ready('contracts') ? `生效合同 ${contracts.value.active} 份` : hint('contracts') }
])
const financeRows = computed(() => [
  { label: '今日收款', value: amount('payments', payments.value.today) },
  { label: '本月收款', value: amount('payments', payments.value.month) },
  { label: '累计逾期余额', value: amount('finance', finance.value.overdueAmount), tone: 'danger' },
  { label: '已结清账单', value: count('finance', finance.value.paidCount) },
  { label: '未出租房间', value: count('rooms', rooms.value.availableRoomCount) },
  { label: '生效合同', value: count('contracts', contracts.value.active) }
])
const operationRows = computed(() => [
  { label: '今日新增工单', value: count('workorders', work.value.today) },
  { label: '待派单', value: count('workorders', work.value.unassigned) },
  { label: '已派单 / 处理中', value: count('workorders', work.value.processing) },
  { label: '已完成待关闭', value: count('workorders', work.value.awaitingClose) },
  { label: '今日巡检记录', value: count('inspections', inspections.value.daily?.totalCount) },
  { label: '巡检待处理（含正常记录）', value: count('inspections', inspections.value.total?.openCount) }
])
const assetRows = computed(() => [
  { label: '设备总数 / 故障设备', value: ready('equipment') ? `${equipment.value.total} / ${equipment.value.fault}` : '—' },
  { label: '最近能耗账期', value: ready('energy') ? latestEnergy.value?.periodMonth || '暂无抄表记录' : '—' },
  ...[['electricUsage', '电用量', 'kWh'], ['waterUsage', '水用量', 'm³'], ['gasUsage', '燃气用量', 'm³']].map(([key, label, unit]) => ({ label, value: ready('energy') && latestEnergy.value ? `${money(latestEnergy.value[key])} ${unit}` : '—' })),
  { label: '当期能耗结算费用', value: latestEnergy.value ? amount('energy', latestEnergy.value.settlementAmount) : '—' }
])
const risks = computed(() => {
  const result = []
  const add = (key, value, title, text, path, tone = 'warning') => { if (ready(key) && Number(value) > 0) result.push({ title, text, path, tone, level: tone === 'danger' ? '优先跟进' : '待关注' }) }
  add('finance', finance.value.overdueCount, `${finance.value.overdueCount} 笔账单已逾期`, `逾期余额 ¥ ${money(finance.value.overdueAmount)}，核对收款记录并跟进催缴。`, '/bills', 'danger')
  add('workorders', work.value.high, `${work.value.high} 单高优先级工单未闭环`, '优先核查派单、处理进展与验收情况。', '/workorders', 'danger')
  add('equipment', equipment.value.fault, `${equipment.value.fault} 台设备处于故障状态`, '安排检查维修，复核设备恢复情况。', '/assets/equipment', 'danger')
  add('inspections', inspections.value.abnormal?.totalCount, `${inspections.value.abnormal?.totalCount} 条异常巡检未关闭`, '查看现场照片、处理反馈并跟进整改。', '/inspections')
  add('workorders', work.value.unassigned, `${work.value.unassigned} 单工单待派发`, '明确责任人与处理安排，减少待办积压。', '/workorders')
  add('contracts', contracts.value.expiring.length, `${contracts.value.expiring.length} 份合同将在 30 天内到期`, '联系租户确认续租意向，安排合同及空间交接。', '/contracts')
  add('energy', energy.value.abnormalCount, `${energy.value.abnormalCount} 条能耗异常待关注`, '复核抄表读数与异常处理状态。', '/assets/energy?tab=statistics')
  return result
})
const summary = computed(() => {
  if (loading.value) return '正在汇总当前账号可见的业务数据…'
  if (!readyCount.value) return '暂未获取可用业务数据，请检查下方数据覆盖状态后刷新。'
  const parts = []
  if (ready('rooms')) parts.push(`房源 ${rooms.value.roomCount} 间，已租 ${rooms.value.rentedRoomCount} 间，出租率 ${rooms.value.rentalRate}%`)
  if (ready('workorders')) parts.push(`今日新增工单 ${work.value.today} 单，累计待跟进 ${work.value.pending} 单`)
  if (ready('inspections')) parts.push(`今日巡检 ${inspections.value.daily.totalCount} 条`)
  parts.push(risks.value.length ? `当前有 ${risks.value.length} 类事项需要跟进，请优先处理下列待办` : '已加载数据范围内暂无待关注事项')
  return `${parts.join('；')}。`
})
const billColumns = [
  { key: 'billNumber', label: '账单编号', minWidth: 150 }, { key: 'tenantName', label: '租户', minWidth: 150 }, { key: 'dueDate', label: '到期日', minWidth: 110 },
  { key: 'balance', label: '未收金额（元）', minWidth: 130, type: 'money', valueGetter: row => Math.max(0, Number(row.amount || 0) - Number(row.paidAmount || 0)) }
]
const statusText = { CREATED: '待提交', SUBMITTED: '待派单', ASSIGNED: '已派单', PROCESSING: '处理中', COMPLETED: '待关闭' }
const workColumns = [{ key: 'title', label: '工单内容', minWidth: 150 }, { key: 'tenantName', label: '租户', minWidth: 120 }, { key: 'status', label: '状态', minWidth: 90, valueGetter: row => statusText[row.status] || row.status }, { key: 'handlerName', label: '处理人', minWidth: 90 }]
const contractColumns = [{ key: 'contractNo', label: '合同编号', minWidth: 160 }, { key: 'tenantName', label: '租户', minWidth: 190 }, { key: 'endDate', label: '到期日', minWidth: 120 }]
async function loadReport() {
  const id = ++requestVersion
  loading.value = true
  today.value = reportDate()
  try {
    const result = await loadOperationsSources((...args) => request.get(...args), hasPermission, today.value)
    if (id !== requestVersion) return
    sources.value = result
    updatedAt.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
  } finally { if (id === requestVersion) loading.value = false }
}
function go(path) { router.push(path) }
function exportReport() {
  const lines = [`运营日报 · ${today.value}`, `更新于 ${updatedAt.value} · 当前账号可见范围`, '', summary.value, '', ...kpis.value.map(item => `${item.label}：${item.value}（${item.scope}）`), '', '优先处理', ...risks.value.map(item => `${item.title}：${item.text}`), '', ...[['收缴与租赁', financeRows.value], ['工单与巡检', operationRows.value], ['设备与能耗', assetRows.value]].flatMap(([title, rows]) => [title, ...rows.map(row => `${row.label}：${row.value}`), '']), '数据覆盖', ...sourceList.value.map(item => `${item.label}：${item.status === 'ready' ? '已更新' : item.status === 'denied' ? '未授权' : '加载失败'}`), '以上为实时汇总，未获取的数据不计作零。']
  downloadBlob(new Blob(['\uFEFF', lines.join('\n')], { type: 'text/plain;charset=utf-8' }), `运营日报-${today.value}.txt`)
}
onMounted(loadReport)
onBeforeUnmount(() => { requestVersion += 1 })
</script>

<style scoped>
.daily-report-page { padding: 4px; min-width: 0; color: #202124; }
.page-header { display: flex; justify-content: space-between; align-items: center; gap: 16px; margin-bottom: 20px; border-left: 3px solid #d93025; padding-left: 15px; }
h2 { margin: 0; font-size: 20px; } .page-header p { margin: 7px 0 0; color: #8a94a3; font-size: 12px; }
.header-actions { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.source-alert { margin-bottom: 14px; } .overview { background: #fff; border: 1px solid #e4e7ec; border-radius: 7px; padding: 18px 20px; margin-bottom: 16px; }
.overview-heading { display: flex; justify-content: space-between; align-items: center; gap: 12px; }.eyebrow { font-size: 13px; font-weight: 600; }.scope-badge { font-size: 11px; color: #667085; background: #f6f7f9; padding: 4px 7px; border-radius: 4px; white-space: nowrap; }
.overview > p { font-size: 14px; line-height: 1.9; margin: 10px 0; color: #475467; }.overview-links { display: flex; flex-wrap: wrap; gap: 16px; }.overview-links :deep(.el-button) { margin: 0; font-size: 12px; color: #667085; }
.kpi-grid { display: grid; grid-template-columns: repeat(6,minmax(0,1fr)); margin-bottom: 16px; background: #fff; border: 1px solid #e4e7ec; border-radius: 7px; overflow: hidden; }.kpi-card { padding: 17px 15px; min-width: 0; border-right: 1px solid #eef0f3; }.kpi-card:last-child { border: 0; }.card-label { display: flex; justify-content: space-between; font-size: 12px; color: #667085; gap: 5px; }.card-label small { font-size: 10px; color: #98a2b3; }.kpi-card > strong { display: block; font-size: clamp(19px,1.5vw,26px); margin-top: 13px; font-variant-numeric: tabular-nums; overflow-wrap: anywhere; }.kpi-card p { color: #98a2b3; font-size: 11px; margin: 9px 0 0; line-height: 1.5; }
.report-grid { display: grid; grid-template-columns: repeat(3,minmax(0,1fr)); gap: 14px; }.panel { background: #fff; border: 1px solid #e4e7ec; border-radius: 7px; min-width: 0; overflow: hidden; }.panel > header { display: flex; justify-content: space-between; align-items: center; gap: 12px; padding: 14px 16px; border-bottom: 1px solid #eef0f3; }.panel h3 { font-size: 13px; margin: 0; }.panel header p { font-size: 11px; color: #98a2b3; margin: 5px 0 0; }.risk-panel { grid-column: span 2; }.risk-list { padding: 0 16px; }.risk-item { display: flex; align-items: center; gap: 12px; padding: 14px 0; border-bottom: 1px solid #f0f1f3; }.risk-item:last-child { border: 0; }.risk-number { flex-shrink: 0; font-size: 12px; background: #fdf0ee; color: #d93025; border-radius: 4px; padding: 5px 8px; }.risk-item > div { flex: 1; min-width: 0; }.risk-item h4 { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; margin: 0; font-size: 13px; font-weight: 500; }.risk-item p { margin: 6px 0 0; font-size: 12px; color: #8a94a3; line-height: 1.6; }.risk-item > :deep(.el-button) { flex-shrink: 0; }
.metric-list { padding: 2px 16px; }.metric-list > div { display: flex; justify-content: space-between; align-items: center; gap: 12px; padding: 12px 0; border-bottom: 1px solid #f0f1f3; font-size: 12px; }.metric-list > div:last-child { border: 0; }.metric-list span { color: #667085; }.metric-list strong { font-weight: 600; font-variant-numeric: tabular-nums; text-align: right; }
.trend-bars { display: flex; justify-content: space-around; gap: 12px; padding: 18px 18px 14px; }.trend-column { flex: 1; min-width: 0; text-align: center; }.trend-column strong { font-size: 12px; font-weight: 500; color: #475467; }.bar-track { height: 150px; margin: 7px 0; display: flex; align-items: flex-end; justify-content: center; border-bottom: 1px solid #e4e7ec; background: linear-gradient(to top, #f7f8fa 1px, transparent 1px); background-size: 100% 50px; }.bar-track progress { appearance: none; writing-mode: vertical-lr; transform: rotate(180deg); display: block; width: 24px; height: 150px; border: 0; background: transparent; }.bar-track progress::-webkit-progress-bar { background: transparent; }.bar-track progress::-webkit-progress-value { background: #d93025; border-radius: 0 0 3px 3px; }.trend-column > span { color: #98a2b3; font-size: 10px; }
.detail-grid { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 14px; margin-top: 14px; }.detail-grid .panel > :deep(.workspace-data-grid) { margin: 10px 14px; }.detail-grid :deep(.el-table) { font-size: 12px; font-variant-numeric: tabular-nums; }.contract-panel { grid-column: 1/-1; }.data-sources { margin-top: 16px; padding: 14px 16px; border: 1px solid #e4e7ec; border-radius: 7px; background: #fafbfc; font-size: 11px; color: #98a2b3; display: flex; gap: 10px 14px; flex-wrap: wrap; }.data-sources strong { color: #667085; font-weight: 500; }.data-sources p { width: 100%; margin: 0; line-height: 1.6; }.data-sources .error { color: #d93025; }.danger { color: #d93025; }.warning { color: #b77925; }
@media(max-width:1250px) { .kpi-grid { grid-template-columns: repeat(3,minmax(0,1fr)); }.kpi-card:nth-child(-n+3) { border-bottom: 1px solid #eef0f3; }.report-grid { grid-template-columns: repeat(2,minmax(0,1fr)); }.risk-panel { grid-column: 1/-1; } }
@media(max-width:760px) { .page-header { align-items: flex-start; flex-direction: column; }.report-grid,.detail-grid { grid-template-columns: minmax(0,1fr); }.kpi-grid { grid-template-columns: repeat(2,minmax(0,1fr)); }.risk-panel { grid-column: auto; }.kpi-card { border-bottom: 1px solid #eef0f3; }.card-label { flex-wrap: wrap; }.kpi-card > strong { font-size: 20px; }.header-actions { gap: 8px; } }
</style>
