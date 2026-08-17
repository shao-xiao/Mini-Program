<template>
  <main class="investment-manage-page">
    <header class="page-heading">
      <div>
        <h1>招商管理</h1>
        <p>园区招商展示内容与招商房源统一维护</p>
      </div>
    </header>

    <el-tabs v-model="activeTab" class="manage-tabs" @tab-change="handleTabChange">
      <el-tab-pane v-if="canViewContent" label="招商展示" name="content">
        <InvestmentContentList embedded />
      </el-tab-pane>
      <el-tab-pane v-if="canViewListings" label="招商房源" name="listings">
        <InvestmentRoomListingList embedded />
      </el-tab-pane>
    </el-tabs>

    <el-empty v-if="availableTabs.length === 0" description="暂无招商管理查看权限" />
  </main>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { hasAnyPermission } from '../../utils/permission'
import InvestmentContentList from './InvestmentContentList.vue'
import InvestmentRoomListingList from './InvestmentRoomListingList.vue'

const route = useRoute()
const router = useRouter()

const canViewContent = computed(() => hasAnyPermission(['investment-content:*', 'investment-content:view']))
const canViewListings = computed(() => hasAnyPermission(['investment-listing:*', 'investment-listing:view']))
const availableTabs = computed(() => [
  ...(canViewContent.value ? ['content'] : []),
  ...(canViewListings.value ? ['listings'] : [])
])
const activeTab = ref('content')

function requestedTab() {
  const tab = String(route.query.tab || '')
  return availableTabs.value.includes(tab) ? tab : availableTabs.value[0] || ''
}

function syncTabFromRoute() {
  const nextTab = requestedTab()
  activeTab.value = nextTab
  if (nextTab && route.query.tab !== nextTab) {
    router.replace({ query: { ...route.query, tab: nextTab } })
  }
}

function handleTabChange(tab) {
  if (!availableTabs.value.includes(tab)) return
  router.replace({ query: { ...route.query, tab } })
}

watch(
  () => [route.query.tab, ...availableTabs.value],
  syncTabFromRoute,
  { immediate: true }
)
</script>

<style scoped>
.investment-manage-page {
  min-width: 0;
  padding: 18px 20px 24px;
}

.page-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.page-heading h1 {
  margin: 0;
  color: var(--dhic-text-primary);
  font-size: 20px;
  line-height: 30px;
  letter-spacing: 0;
}

.page-heading p {
  margin: 2px 0 0;
  color: var(--dhic-text-secondary);
  font-size: 13px;
}

.manage-tabs :deep(.el-tabs__header) {
  margin: 0;
}

.manage-tabs :deep(.el-tabs__content) {
  overflow: visible;
}

@media (max-width: 1180px) {
  .investment-manage-page {
    padding-inline: 14px;
  }
}
</style>
