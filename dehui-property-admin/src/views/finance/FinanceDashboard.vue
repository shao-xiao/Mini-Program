<template>
  <div ref="pageRef" class="finance-dashboard">
    <header class="page-header">
      <div><h2>财务看板</h2><p>收缴进度、逾期余额与租户欠款一览</p></div>
      <div class="page-actions"><span class="updated-at">{{ loading ? '正在更新…' : updatedAt ? `更新于 ${updatedAt}` : '' }}</span><el-date-picker v-model="billingPeriod" type="month" value-format="YYYY-MM" clearable placeholder="全部账期" aria-label="财务统计账期" @change="loadDashboard" /><el-button @click="selectThisMonth">本月</el-button><el-button type="primary" :loading="loading" @click="loadDashboard">刷新</el-button></div>
    </header>
    <el-alert v-if="errorMessage" class="error-alert" type="error" :title="errorMessage" :closable="false" show-icon><el-button link type="primary" @click="loadDashboard">重新加载</el-button></el-alert>
    <div class="scope-line"><strong>{{ billingPeriod ? `${billingPeriod} 账期` : '全部账期' }}</strong><span>审核通过账单口径</span><span v-if="ready">{{ unpaidTenants.length }} 家租户存在未收</span><el-button v-if="canViewBills" link @click="openBills()">财务账单 →</el-button></div>
    <div class="summary-grid" :aria-busy="loading">
      <div v-for="item in summaryCards" :key="item.label" class="summary-card"><div class="summary-label">{{ item.label }}<small>{{ item.scope }}</small></div><strong class="summary-value" :class="item.tone">{{ item.value }}</strong><p>{{ item.desc }}</p></div>
    </div>
    <div class="status-grid">
      <section class="panel collection-panel"><header><div><h3>收缴进度</h3><p>已确认收款金额 / 审核通过的应收金额</p></div><strong class="rate-value">{{ percent(collectionRate) }}</strong></header><div class="collection-body"><progress :value="Math.min(100, Math.max(0, collectionRate || 0))" max="100" aria-label="账期收缴进度"></progress><div class="collection-legend"><span>已收 <b>{{ amount(summary.receivedAmount) }}</b></span><span>未收 <b>{{ amount(summary.unpaidAmount) }}</b></span></div><div class="arrears-strip"><span>逾期占未收余额 <b class="danger">{{ percent(overdueShare) }}</b></span><span>{{ ready ? overdueShare == null ? '当前无未收余额' : '优先跟进已过到期日的未结清账单' : '等待统计数据' }}</span></div></div></section>
      <section class="panel workflow-panel"><header><div><h3>审核与开票</h3><p>待办数量与开票金额单独列示</p></div><el-button v-if="canViewBills" link type="primary" @click="openBills()">查看账单 →</el-button></header><div class="workflow-grid"><div><span>待审核账单</span><strong>{{ count(summary.pendingReviewCount) }}<small v-if="ready"> 笔</small></strong><p>待审核金额未计入应收</p></div><div><span>待开票账单</span><strong>{{ count(summary.pendingInvoiceCount) }}<small v-if="ready"> 笔</small></strong><p>审核通过且尚未完成开票</p></div><div><span>已开票金额</span><strong>{{ amount(summary.invoicedAmount, true) }}</strong><p>当前范围内有效发票金额</p></div></div></section>
    </div>
    <div class="chart-grid">
      <section class="panel trend-panel"><header><div><h3>账期应收与已收</h3><p>已收款归属对应账期，缺少记录的月份留空</p></div><span class="panel-meta">{{ trendRows.length ? `${trendRows[0].dimension} — ${trendRows.at(-1).dimension}` : '暂无账期' }}</span></header><el-empty v-if="!ready || !hasTrend" :description="loading ? '正在汇总账期数据' : errorMessage ? '统计数据暂不可用' : '当前账期暂无应收与收款记录'" :image-size="56" /><div v-else ref="trendRef" class="chart" role="img" aria-label="各账期应收金额与已收金额趋势"></div></section>
      <section class="panel type-panel"><header><div><h3>费用构成</h3><p>按审核通过账单的费用项净额统计</p></div><span class="panel-meta">{{ chargeRows.length }} 类费用</span></header><div v-if="ready && chargeRows.length" class="charge-list"><div v-for="item in chargeRows" :key="item.chargeItemType" class="charge-row"><div><span>{{ chargeTypeText(item.chargeItemType) }}</span><strong>¥ {{ formatMoney(item.amount) }}<small>{{ item.share == null ? '冲减' : `${item.share.toFixed(1)}%` }}</small></strong></div><progress :value="Math.max(0, item.share || 0)" max="100" :aria-label="`${chargeTypeText(item.chargeItemType)}金额占比`"></progress></div><p v-if="chargeRows.some(item => item.amount < 0)" class="charge-note">占比按正向费用计算，冲减金额单列。</p></div><el-empty v-else :description="loading ? '正在汇总费用数据' : errorMessage ? '统计数据暂不可用' : '当前范围暂无费用项记录'" :image-size="56" /></section>
    </div>
    <section class="panel tenant-panel"><header><div><h3>租户收缴与跟进</h3><p>按未收金额从高到低排列；欠款占比以当前筛选范围未收总额为基数</p></div><span class="panel-meta">{{ ready ? tenantRows.length : '—' }} 家租户</span></header><div class="tenant-table"><WorkspaceDataGrid :rows="ready ? tenantRows : []" :columns="tenantColumns" row-key="rowId" :show-index="false" density="compact" data-mode="client" :loading="loading" :empty-text="errorMessage ? '统计数据暂不可用，请重新加载' : '当前范围暂无租户账单'" :operation-column="{width:110,align:'left'}" aria-label="财务看板租户收缴明细"><template v-if="canViewBills" #operations="{row}"><el-button v-if="row.tenantId" link type="primary" @click="openBills(row.tenantId)">查看账单 →</el-button></template></WorkspaceDataGrid></div></section>
    <footer class="data-note">统计范围：{{ billingPeriod || '全部账期' }}。应收、已收、未收和逾期金额采用审核通过账单口径；“已收”按账单归属账期统计，不代表当月现金流。租户未收金额不等同于逾期金额。</footer>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import request from '../../utils/request'
import { hasPermission } from '../../utils/permission'
import { financeNumber, financeRatio, financeMonthlyWindow, financeTenantRows, financeChargeRows } from '../../utils/financeDashboard'
import WorkspaceDataGrid from '../../components/workspace/WorkspaceDataGrid.vue'

const router = useRouter()
const canViewBills = computed(() => hasPermission('bill:view'))
const loading = ref(false)
const ready = ref(false)
const errorMessage = ref('')
const updatedAt = ref('')
const billingPeriod = ref('')
const summary = ref({})
const monthly = ref([])
const tenants = ref([])
const chargeTypes = ref([])
const pageRef = ref(null)
const trendRef = ref(null)
const charts = []
let requestVersion = 0
let observer
const collectionRate = computed(() => ready.value ? financeRatio(summary.value.receivedAmount, summary.value.receivableAmount) : null)
const overdueShare = computed(() => ready.value ? financeRatio(summary.value.overdueAmount, summary.value.unpaidAmount) : null)
const tenantRows = computed(() => financeTenantRows(tenants.value, summary.value.unpaidAmount))
const unpaidTenants = computed(() => tenantRows.value.filter(row => financeNumber(row.unpaidAmount) > 0))
const chargeRows = computed(() => financeChargeRows(chargeTypes.value))
const trendRows = computed(() => financeMonthlyWindow(monthly.value))
const hasTrend = computed(() => trendRows.value.some(row => row.record && (financeNumber(row.record.receivableAmount) !== 0 || financeNumber(row.record.receivedAmount) !== 0)))
function formatMoney(value) { return financeNumber(value).toLocaleString('zh-CN', { minimumFractionDigits:2, maximumFractionDigits:2 }) }
function shortMoney(value) { const n=financeNumber(value); return Math.abs(n)>=10000 ? `${(n/10000).toFixed(1)}万` : formatMoney(n) }
function amount(value, short = false) { return ready.value ? `¥ ${short ? shortMoney(value) : formatMoney(value)}` : '—' }
function count(value) { return ready.value ? financeNumber(value).toLocaleString('zh-CN') : '—' }
function percent(value) { return value == null ? '—' : `${value.toFixed(1)}%` }
const summaryCards = computed(() => [
  {label:'应收金额',scope:'审核通过',value:amount(summary.value.receivableAmount,true),desc:'当前账期范围内应收总额'},
  {label:'已收金额',scope:'账期归属',value:amount(summary.value.receivedAmount,true),desc:'当前范围已确认收款',tone:'success'},
  {label:'未收金额',scope:'待跟进',value:amount(summary.value.unpaidAmount,true),desc:ready.value ? `${unpaidTenants.value.length} 家租户存在未收` : '等待统计数据',tone:'warning'},
  {label:'逾期余额',scope:'截至今日',value:amount(summary.value.overdueAmount,true),desc:'已过到期日且尚未结清',tone:'danger'}
])
const tenantColumns = [
  {key:'tenantName',label:'租户',minWidth:220,fixed:'left'},
  {key:'receivableAmount',label:'应收金额（元）',type:'money',minWidth:150,align:'right'},
  {key:'receivedAmount',label:'已收金额（元）',type:'money',minWidth:150,align:'right'},
  {key:'unpaidAmount',label:'未收金额（元）',type:'money',minWidth:150,align:'right'},
  {key:'collectionRate',label:'收缴率',minWidth:100,align:'right',valueGetter:row=>percent(row.collectionRate)},
  {key:'unpaidShare',label:'欠款占比',minWidth:100,align:'right',valueGetter:row=>percent(row.unpaidShare)},
  {key:'followUp',label:'跟进状态',minWidth:105,valueGetter:row=>financeNumber(row.unpaidAmount)>0?'待跟进':financeNumber(row.receivableAmount)>0?'已收齐':'暂无应收'}
]
function chargeTypeText(type) { return {RENT:'租金',PROPERTY_FEE:'物业费',PROPERTY:'物业费',WATER:'水费',ELECTRICITY:'电费',GAS:'燃气费',PARKING:'停车费',MEETING_ROOM:'会议室',WORK_ORDER:'维修/工单服务费',CLEANING:'保洁费',DEPOSIT:'押金',LATE_FEE:'滞纳金',ADJUSTMENT:'调账补差',OTHER:'其他'}[type] || type || '未分类' }
function renderCharts() {
  charts.splice(0).forEach(chart => chart.dispose())
  if (!trendRef.value?.clientWidth || !ready.value || !hasTrend.value) return
  const chart=echarts.init(trendRef.value)
  charts.push(chart)
  chart.setOption({
    animation:false,color:['#d93025','#475467'],
    tooltip:{trigger:'axis',valueFormatter:value=>value==null?'无账期记录':`¥ ${formatMoney(value)}`},
    legend:{top:8,right:16,itemWidth:12,itemHeight:8,textStyle:{fontSize:11,color:'#667085'},data:['应收金额','已收金额']},
    grid:{left:65,right:25,top:48,bottom:32},
    xAxis:{type:'category',data:trendRows.value.map(row=>row.dimension),axisTick:{show:false},axisLine:{lineStyle:{color:'#e4e7ec'}},axisLabel:{fontSize:11,color:'#667085'}},
    yAxis:{type:'value',splitNumber:4,axisLabel:{formatter:shortMoney,fontSize:11,color:'#98a2b3'},splitLine:{lineStyle:{color:'#eef0f3',type:'dashed'}}},
    series:[{name:'应收金额',type:'bar',barMaxWidth:28,itemStyle:{borderRadius:[3,3,0,0]},label:{show:true,position:'top',fontSize:11,color:'#667085',formatter:p=>p.value?shortMoney(p.value):''},data:trendRows.value.map(row=>row.record?financeNumber(row.record.receivableAmount):null)},
      {name:'已收金额',type:'line',connectNulls:false,symbolSize:6,data:trendRows.value.map(row=>row.record?financeNumber(row.record.receivedAmount):null)}]
  })
}
async function loadDashboard() {
  const version=++requestVersion
  const period=billingPeriod.value
  loading.value=true; ready.value=false; errorMessage.value=''; updatedAt.value=''
  summary.value={}; monthly.value=[]; tenants.value=[]; chargeTypes.value=[]
  charts.splice(0).forEach(chart=>chart.dispose())
  try {
    const data=await request.get('/finance/dashboard',{params:{billingPeriod:period||undefined},silent:true})
    if(version!==requestVersion)return
    if(!data?.summary)throw new Error('财务统计返回不完整，请重试')
    summary.value=data.summary
    monthly.value=Array.isArray(data.monthly)?data.monthly:[]
    tenants.value=Array.isArray(data.byTenant)?data.byTenant:[]
    chargeTypes.value=Array.isArray(data.byChargeType)?data.byChargeType:[]
    ready.value=true
    updatedAt.value=new Date().toLocaleTimeString('zh-CN',{hour12:false})
  } catch(error) {
    if(version===requestVersion)errorMessage.value=error?.response?.data?.message || error?.message || '财务看板加载失败，请重试'
  } finally {
    if(version===requestVersion){loading.value=false;await nextTick();renderCharts()}
  }
}
function selectThisMonth() { const date=new Date();billingPeriod.value=`${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}`;loadDashboard() }
function openBills(tenantId) { if(canViewBills.value)router.push({path:'/bills',query:{tenantId:tenantId||'all',tab:'bills'}}) }
function resizeCharts() { charts.forEach(chart=>chart.resize()) }
onMounted(()=>{loadDashboard();window.addEventListener('resize',resizeCharts);observer=new ResizeObserver(resizeCharts);if(pageRef.value)observer.observe(pageRef.value)})
onBeforeUnmount(()=>{requestVersion+=1;window.removeEventListener('resize',resizeCharts);observer?.disconnect();charts.splice(0).forEach(chart=>chart.dispose())})
</script>

<style scoped>
.finance-dashboard { padding: 8px 4px; min-width: 0; color:#202124; }.page-header { display:flex; justify-content:space-between; align-items:center; gap:16px; border-left:3px solid #d93025; padding-left:15px; margin-bottom:16px; }.page-header h2 { margin:0; font-size:20px; }.page-header p { margin:6px 0 0; font-size:12px; color:#8a94a3; }.page-actions { display:flex; align-items:center; gap:10px; flex-wrap:wrap; }.page-actions :deep(.el-date-editor) { width:150px; }.updated-at { font-size:11px; color:#98a2b3; white-space:nowrap; }.error-alert { margin-bottom:14px; }.scope-line { display:flex; align-items:center; flex-wrap:wrap; gap:12px; font-size:11px; color:#8a94a3; margin:0 0 12px; }.scope-line strong { color:#475467;font-weight:500; }.scope-line :deep(.el-button) { margin-left:auto; font-size:12px; color:#667085; }
.summary-grid { display:grid;grid-template-columns:repeat(4,minmax(0,1fr));border:1px solid #e4e7ec;border-radius:7px;background:white;overflow:hidden;margin-bottom:14px; }.summary-card { padding:18px;min-width:0;border-right:1px solid #eef0f3; }.summary-card:last-child { border:0; }.summary-label { display:flex;justify-content:space-between;gap:10px;color:#667085;font-size:12px; }.summary-label small { font-size:10px;background:#f6f7f9;padding:2px 4px;color:#98a2b3;border-radius:3px; }.summary-value { display:block;margin-top:12px;font-size:clamp(21px,1.8vw,28px);font-weight:600;font-variant-numeric:tabular-nums;overflow-wrap:anywhere; }.summary-card p { margin:9px 0 0;font-size:11px;color:#98a2b3;line-height:1.5; }
.status-grid { display:grid;grid-template-columns:1fr 1fr;gap:14px;margin-bottom:14px; }.panel { min-width:0;background:white;border:1px solid #e4e7ec;border-radius:7px;overflow:hidden; }.panel > header { display:flex;align-items:center;justify-content:space-between;gap:12px;padding:13px 16px;border-bottom:1px solid #eef0f3; }.panel h3 { margin:0;font-size:13px;font-weight:600; }.panel header p { margin:5px 0 0;font-size:11px;color:#98a2b3;line-height:1.5; }.panel-meta { font-size:11px;color:#667085;padding:4px 7px;border-radius:4px;background:#f6f7f9;white-space:nowrap; }.rate-value { font-size:24px;color:#4f6b58;font-weight:600; }.collection-body { padding:18px 16px 12px; }progress { appearance:none;display:block;width:100%;border:0;border-radius:5px;overflow:hidden;height:8px;background:#eef0f3; }progress::-webkit-progress-bar { background:#eef0f3; }progress::-webkit-progress-value { background:#4f6b58;border-radius:5px; }progress::-moz-progress-bar { background:#4f6b58;border-radius:5px; }.collection-legend { display:flex;justify-content:space-between;flex-wrap:wrap;gap:8px;color:#8a94a3;font-size:11px;margin:12px 0 16px; }.collection-legend b { color:#475467;font-weight:500;font-variant-numeric:tabular-nums; }.arrears-strip { display:flex;justify-content:space-between;flex-wrap:wrap;gap:8px;padding-top:12px;border-top:1px solid #eef0f3;font-size:11px;color:#8a94a3; }.arrears-strip b { margin-left:7px;font-weight:600; }
.workflow-grid { display:grid;grid-template-columns:repeat(3,minmax(0,1fr));padding:18px 0; }.workflow-grid > div { padding:0 16px;min-width:0;border-right:1px solid #eef0f3; }.workflow-grid > div:last-child { border:0; }.workflow-grid span { display:block;color:#667085;font-size:11px; }.workflow-grid strong { display:block;margin-top:12px;font-size:20px;font-weight:600;font-variant-numeric:tabular-nums;overflow-wrap:anywhere; }.workflow-grid strong small { font-size:11px;color:#98a2b3;font-weight:400; }.workflow-grid p { font-size:10px;color:#98a2b3;line-height:1.6;margin:9px 0 0; }
.chart-grid { display:grid;grid-template-columns:minmax(0,1.65fr) minmax(0,1fr);gap:14px; }.chart { width:100%;height:280px; }.trend-panel :deep(.el-empty),.type-panel :deep(.el-empty) { min-height:280px;padding:20px; }.charge-list { padding:4px 16px 14px;max-height:280px;overflow-y:auto; }.charge-row { padding-top:12px; }.charge-row > div { display:flex;justify-content:space-between;align-items:center;gap:12px;font-size:12px;margin-bottom:7px; }.charge-row span { color:#667085; }.charge-row strong { font-size:12px;color:#475467;font-weight:500;font-variant-numeric:tabular-nums; }.charge-row small { display:inline-block;min-width:44px;text-align:right;color:#98a2b3;font-size:10px;margin-left:10px; }.charge-note { font-size:10px;color:#98a2b3;line-height:1.5;margin:12px 0 0; }.charge-row progress { height:4px; }.charge-row progress::-webkit-progress-value { background:#d93025; }.charge-row progress::-moz-progress-bar { background:#d93025; }.tenant-panel { margin-top:14px; }.tenant-table { padding:12px 14px; }.tenant-table :deep(.el-table) { font-size:12px;font-variant-numeric:tabular-nums; }.data-note { margin-top:12px;color:#98a2b3;font-size:11px;line-height:1.8;padding:0 3px; }.success { color:#4f6b58; }.warning { color:#a66b19; }.danger { color:#d93025; }
@media(max-width:1200px) { .page-header { align-items:flex-start;flex-direction:column; }.page-actions { width:100%; }.status-grid { grid-template-columns:minmax(0,1fr); }.chart-grid { grid-template-columns:minmax(0,1fr); }.charge-list { max-height:none; }.summary-card { padding:15px 12px; } }
@media(max-width:640px) { .summary-grid { grid-template-columns:repeat(2,minmax(0,1fr)); }.summary-card:nth-child(-n+2) { border-bottom:1px solid #eef0f3; }.summary-card:nth-child(2) { border-right:0; }.workflow-grid { grid-template-columns:repeat(2,minmax(0,1fr));gap:18px 0; }.workflow-grid > div:last-child { grid-column:1/-1;border-top:1px solid #eef0f3;padding-top:14px; }.panel > header { flex-wrap:wrap; }.updated-at { width:100%; }.summary-value { font-size:22px; } }
</style>
