<template>
  <main class="asset-page energy-asset-page">
    <header class="asset-page__header">
      <div>
        <h1>能耗管理</h1>
        <p>按物业公共区域或租户主体查看表具、抄表记录和用量统计。</p>
      </div>
      <div class="asset-page__header-actions">
        <el-button :icon="Refresh" :loading="loadingSubjects" @click="loadSubjectData">刷新主体</el-button>
        <el-button v-if="hasPermission('bill:view') || hasPermission('bill:*')" @click="goToBills">前往财务账单</el-button>
      </div>
    </header>

    <section class="subject-panel" aria-labelledby="energy-subject-heading">
      <div class="subject-panel__toolbar">
        <div>
          <h2 id="energy-subject-heading">计量主体</h2>
          <span>先选择主体，再查看下方业务数据</span>
        </div>
        <el-input
          v-model.trim="subjectKeyword"
          :prefix-icon="Search"
          clearable
          class="subject-search"
          placeholder="搜索租户公司"
          aria-label="搜索能耗主体"
        />
      </div>

      <div v-loading="loadingSubjects" class="subject-grid">
        <button
          v-for="subject in filteredSubjects"
          :key="subject.key"
          type="button"
          class="subject-card"
          :class="{ 'is-active': subject.key === selectedSubjectKey }"
          @click="selectSubject(subject.key)"
        >
          <span class="subject-card__icon" aria-hidden="true">
            <el-icon><OfficeBuilding v-if="subject.scope === 'property'" /><User v-else /></el-icon>
          </span>
          <span class="subject-card__body">
            <strong>{{ subject.name }}</strong>
            <small>{{ subject.scope === 'property' ? '物业公共区域' : '租户公司' }}</small>
            <span class="subject-card__stats">
              <span>房间 {{ metricText(subject.roomCount, subject.meterDataReady) }}</span>
              <span>表具 {{ metricText(subject.meterCount, subject.meterDataReady) }}</span>
              <span>水 {{ metricText(subject.waterCount, subject.meterDataReady) }}</span>
              <span>电 {{ metricText(subject.electricCount, subject.meterDataReady) }}</span>
              <span>燃气 {{ metricText(subject.gasCount, subject.meterDataReady) }}</span>
              <span :class="{ 'has-risk': subject.abnormalCount > 0 }">异常 {{ metricText(subject.abnormalCount, subject.readingDataReady) }}</span>
            </span>
          </span>
          <el-button
            text
            circle
            :icon="InfoFilled"
            :aria-label="`查看${subject.name}详情`"
            @click.stop="openSubjectDetail(subject)"
          />
        </button>
      </div>

      <el-alert
        v-if="subjectError"
        type="warning"
        :closable="false"
        show-icon
        :title="subjectError"
      />
    </section>

    <section class="energy-detail-panel">
      <div class="energy-detail-panel__heading">
        <div>
          <h2>{{ selectedSubject?.name || '能耗详情' }}</h2>
          <span>{{ selectedSubject?.scope === 'property' ? '公共区域计量数据' : '租户关联计量数据' }}</span>
        </div>
        <el-tabs v-model="activeTab" class="energy-tabs" @tab-change="changeTab">
          <el-tab-pane v-if="canViewMeters" label="表具" name="meters" />
          <el-tab-pane v-if="canViewReadings" label="抄表记录" name="records" />
          <el-tab-pane v-if="canViewReadings" label="用量统计" name="statistics" />
        </el-tabs>
      </div>

      <EnergyMeterList
        v-if="activeTab === 'meters' && canViewMeters"
        embedded
        :subject-scope="selectedSubject?.scope"
        :subject-tenant-id="selectedSubject?.tenantId"
      />
      <EnergyRecordList
        v-else-if="activeTab === 'records' && canViewReadings"
        embedded
        :allow-billing-actions="false"
        :subject-scope="selectedSubject?.scope"
        :subject-tenant-id="selectedSubject?.tenantId"
      />
      <EnergyStats
        v-else-if="activeTab === 'statistics' && canViewReadings"
        embedded
        :subject-scope="selectedSubject?.scope"
        :subject-tenant-id="selectedSubject?.tenantId"
      />
      <el-empty v-else description="当前账号没有能耗数据查看权限" :image-size="88" />
    </section>

    <el-drawer v-model="detailVisible" title="计量主体详情" size="min(480px, 92vw)">
      <el-descriptions v-if="detailSubject" :column="1" border>
        <el-descriptions-item label="主体名称">{{ detailSubject.name }}</el-descriptions-item>
        <el-descriptions-item label="主体类型">{{ detailSubject.scope === 'property' ? '物业公共区域' : '租户公司' }}</el-descriptions-item>
        <el-descriptions-item label="联系人">{{ detailSubject.contactName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="联系电话">{{ detailSubject.phone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="关联合同">{{ detailSubject.contractSummary || '未配置' }}</el-descriptions-item>
        <el-descriptions-item label="关联房间">{{ metricText(detailSubject.roomCount, detailSubject.meterDataReady) }}</el-descriptions-item>
        <el-descriptions-item label="关联表具">{{ metricText(detailSubject.meterCount, detailSubject.meterDataReady) }}</el-descriptions-item>
        <el-descriptions-item label="当前状态">{{ detailSubject.status || '未配置' }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </main>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { InfoFilled, OfficeBuilding, Refresh, Search, User } from '@element-plus/icons-vue'
import EnergyMeterList from '../energy/EnergyMeterList.vue'
import EnergyRecordList from '../energy/EnergyRecordList.vue'
import EnergyStats from '../energy/EnergyStats.vue'
import request from '../../utils/request'
import { hasPermission } from '../../utils/permission'
import { readPage } from '../../utils/pagination'
import { createLatestRequestGuard } from '../../utils/requestLifecycle'
import { loadAllWorkspaceRecords } from '../../utils/workspaceExport'

const route = useRoute()
const router = useRouter()
const tenants = ref([])
const meters = ref([])
const readings = ref([])
const subjectKeyword = ref('')
const selectedSubjectKey = ref('property')
const activeTab = ref('meters')
const loadingSubjects = ref(false)
const meterDataReady = ref(false)
const readingDataReady = ref(false)
const subjectError = ref('')
const detailVisible = ref(false)
const detailSubject = ref(null)
const subjectRequest = createLatestRequestGuard()
let pageActive = true

const canViewMeters = computed(() => hasPermission('energy:meter:view'))
const canViewReadings = computed(() => hasPermission('energy:view'))
const canViewTenants = computed(() => hasPermission('tenant:view') || hasPermission('tenant:*'))

const subjects = computed(() => {
  const rows = [createSubject({ key: 'property', name: '物业公共区域', scope: 'property', tenantId: null })]
  tenants.value.forEach(tenant => rows.push(createSubject({
    key: `tenant-${tenant.id}`,
    name: tenant.tenantName || tenant.name || `租户${tenant.id}`,
    scope: 'tenant',
    tenantId: Number(tenant.id),
    contactName: tenant.contactName || tenant.contactPerson || '',
    phone: tenant.phone || tenant.contactPhone || '',
    contractSummary: tenant.contractNo || tenant.contractName || '',
    status: tenant.status || ''
  })))
  return rows
})

const filteredSubjects = computed(() => {
  const keyword = subjectKeyword.value.toLowerCase()
  if (!keyword) return subjects.value
  return subjects.value.filter(subject => subject.scope === 'property' || subject.name.toLowerCase().includes(keyword))
})

const selectedSubject = computed(() => subjects.value.find(subject => subject.key === selectedSubjectKey.value) || subjects.value[0])

function createSubject(base) {
  const subjectMeters = meters.value.filter(meter => base.scope === 'property'
    ? !meter.tenantId
    : Number(meter.tenantId) === Number(base.tenantId))
  const subjectReadings = readings.value.filter(reading => base.scope === 'property'
    ? !reading.tenantId
    : Number(reading.tenantId) === Number(base.tenantId))
  return {
    ...base,
    meterDataReady: meterDataReady.value,
    readingDataReady: readingDataReady.value,
    roomCount: new Set(subjectMeters.map(meter => Number(meter.roomId)).filter(Boolean)).size,
    meterCount: subjectMeters.length,
    waterCount: subjectMeters.filter(meter => meter.meterType === 'WATER').length,
    electricCount: subjectMeters.filter(meter => ['ELECTRIC', 'ELECTRICITY'].includes(meter.meterType)).length,
    gasCount: subjectMeters.filter(meter => meter.meterType === 'GAS').length,
    abnormalCount: subjectReadings.filter(reading => Boolean(reading.abnormalFlag)).length
  }
}

function metricText(value, ready) {
  return ready ? String(Number(value || 0)) : '未配置'
}

function validTab(value) {
  const allowed = []
  if (canViewMeters.value) allowed.push('meters')
  if (canViewReadings.value) allowed.push('records', 'statistics')
  return allowed.includes(value) ? value : (allowed[0] || '')
}

async function syncUrl() {
  await router.replace({
    path: '/assets/energy',
    query: {
      ...route.query,
      tab: activeTab.value,
      subject: selectedSubjectKey.value
    }
  })
}

async function selectSubject(key) {
  selectedSubjectKey.value = subjects.value.some(subject => subject.key === key) ? key : 'property'
  await syncUrl()
}

async function changeTab(tab) {
  activeTab.value = validTab(tab)
  await syncUrl()
}

function openSubjectDetail(subject) {
  detailSubject.value = subject
  detailVisible.value = true
}

function goToBills() {
  router.push({ path: '/bills', query: { sourceType: 'ENERGY' } })
}

async function fetchAll(url) {
  return loadAllWorkspaceRecords(({ page, pageSize }) => request.get(url, {
    params: { page, pageSize },
    silent: true
  }), { pageSize: 100, maxRecords: 10000 })
}

async function loadSubjectData() {
  const requestId = subjectRequest.start()
  loadingSubjects.value = true
  subjectError.value = ''
  const tasks = await Promise.allSettled([
    canViewTenants.value ? request.get('/tenant/list', { silent: true }) : Promise.resolve([]),
    canViewMeters.value ? fetchAll('/energy/meters') : Promise.resolve([]),
    canViewReadings.value ? fetchAll('/energy/readings') : Promise.resolve([])
  ])
  if (!pageActive || !subjectRequest.isCurrent(requestId)) return

  if (tasks[0].status === 'fulfilled') tenants.value = readPage(tasks[0].value).records
  else {
    tenants.value = []
    subjectError.value = '租户主体未加载；当前仅展示物业公共区域。'
  }
  if (tasks[1].status === 'fulfilled') {
    meters.value = tasks[1].value
    meterDataReady.value = canViewMeters.value
  } else {
    meters.value = []
    meterDataReady.value = false
    subjectError.value = '表具汇总未加载，下方表具页可单独重试。'
  }
  if (tasks[2].status === 'fulfilled') {
    readings.value = tasks[2].value
    readingDataReady.value = canViewReadings.value
  } else {
    readings.value = []
    readingDataReady.value = false
    subjectError.value = '抄表汇总未加载，下方抄表页可单独重试。'
  }

  const requestedSubject = String(route.query.subject || 'property')
  selectedSubjectKey.value = subjects.value.some(subject => subject.key === requestedSubject) ? requestedSubject : 'property'
  loadingSubjects.value = false
}

onMounted(async () => {
  activeTab.value = validTab(String(route.query.tab || 'meters'))
  await loadSubjectData()
  await syncUrl()
})

onBeforeUnmount(() => {
  pageActive = false
  subjectRequest.dispose()
})
</script>

<style scoped>
.asset-page {
  min-width: 0;
  color: var(--dhic-text-primary);
}

.asset-page__header,
.asset-page__header-actions,
.subject-panel__toolbar,
.energy-detail-panel__heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.asset-page__header {
  align-items: flex-start;
  margin-bottom: 12px;
}

.asset-page h1,
.asset-page h2 {
  margin: 0;
  letter-spacing: 0;
}

.asset-page h1 {
  font-size: 20px;
  font-weight: 600;
  line-height: 28px;
}

.asset-page h2 {
  font-size: 14px;
}

.asset-page__header p,
.subject-panel__toolbar span,
.energy-detail-panel__heading span {
  margin: 3px 0 0;
  color: var(--dhic-text-secondary);
  font-size: 13px;
}

.subject-panel,
.energy-detail-panel {
  border: 1px solid var(--dhic-border-color);
  border-radius: var(--dhic-radius-large);
  background: #fff;
  box-shadow: var(--dhic-shadow-card);
}

.subject-panel {
  margin-bottom: 12px;
  padding: 12px;
}

.subject-search {
  width: min(280px, 36vw);
}

.subject-grid {
  display: grid;
  min-height: 112px;
  grid-auto-columns: minmax(260px, 1fr);
  grid-auto-flow: column;
  gap: 8px;
  margin-top: 10px;
  overflow-x: auto;
  padding-bottom: 4px;
}

.subject-card {
  display: grid;
  min-width: 260px;
  grid-template-columns: 36px minmax(0, 1fr) 30px;
  align-items: start;
  gap: 8px;
  padding: 10px;
  border: 1px solid var(--dhic-border-color);
  border-radius: var(--dhic-radius-base);
  background: #fff;
  color: inherit;
  cursor: pointer;
  text-align: left;
}

.subject-card:hover {
  border-color: #c9cdd4;
}

.subject-card:focus-visible {
  outline: 2px solid var(--el-color-primary-light-3);
  outline-offset: 1px;
}

.subject-card.is-active {
  border-color: var(--el-color-primary);
  background: var(--dhic-brand-red-light);
  box-shadow: inset 3px 0 0 var(--el-color-primary);
}

.subject-card__icon {
  display: grid;
  width: 34px;
  height: 34px;
  border-radius: 5px;
  background: #f2f3f5;
  color: #606266;
  place-items: center;
}

.subject-card__body,
.subject-card__body strong,
.subject-card__body small {
  display: block;
  min-width: 0;
}

.subject-card__body strong {
  overflow: hidden;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.subject-card__body small {
  margin-top: 2px;
  color: #909399;
  font-size: 11px;
}

.subject-card__stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 3px 8px;
  margin-top: 8px;
  color: #606266;
  font-size: 11px;
}

.subject-card__stats .has-risk {
  color: #d93025;
  font-weight: 600;
}

.energy-detail-panel {
  min-width: 0;
  overflow: hidden;
}

.energy-detail-panel__heading {
  min-height: 52px;
  padding: 0 16px;
  border-bottom: 1px solid var(--dhic-border-color-light);
}

.energy-tabs {
  width: auto;
  min-width: 310px;
}

.energy-tabs :deep(.el-tabs__header) {
  margin: 0;
}

.energy-tabs :deep(.el-tabs__content) {
  display: none;
}

@media (max-width: 1024px) {
  .asset-page__header,
  .subject-panel__toolbar,
  .energy-detail-panel__heading {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .subject-search,
  .energy-tabs {
    width: 100%;
  }
}
</style>
