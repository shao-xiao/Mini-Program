<template>
  <el-container class="layout-container">
    <el-aside width="240px" class="aside">
      <div class="brand">
        <img src="../assets/dehui-logo.png" class="logo" />
        <div>
          <div class="brand-title">德汇创新中心</div>
          <div class="brand-subtitle">物业管理系统</div>
        </div>
      </div>

      <el-menu
        :default-active="$route.path"
        router
        class="side-menu"
        background-color="#1f1b1b"
        text-color="#e6e6e6"
        active-text-color="#d93025"
      >
        <el-menu-item index="/dashboard">驾驶舱</el-menu-item>

        <el-sub-menu v-if="hasRole(['ADMIN','MANAGER'])" index="asset">
          <template #title>资产管理</template>
          <el-menu-item index="/buildings">楼宇管理</el-menu-item>
          <el-menu-item index="/floors">楼层管理</el-menu-item>
          <el-menu-item index="/rooms">房间管理</el-menu-item>
          <el-menu-item index="/equipment">设备台账</el-menu-item>
        </el-sub-menu>

        <el-sub-menu v-if="hasRole(['ADMIN','MANAGER'])" index="tenant">
          <template #title>租赁管理</template>
          <el-menu-item index="/tenants">租户管理</el-menu-item>
          <el-menu-item index="/leases">租户入驻</el-menu-item>
          <el-menu-item index="/contracts">合同台账</el-menu-item>
        </el-sub-menu>

        <el-sub-menu v-if="hasRole(['ADMIN','MANAGER','STAFF','SECURITY'])" index="ops">
          <template #title>运营管理</template>
          <el-menu-item index="/workorders">工单管理</el-menu-item>
          <el-menu-item index="/inspections">巡检管理</el-menu-item>
          <el-menu-item v-if="hasRole(['ADMIN','MANAGER','SECURITY'])" index="/visitors">访客管理</el-menu-item>
          <el-menu-item v-if="hasRole(['ADMIN','MANAGER'])" index="/announcements">公告管理</el-menu-item>
        </el-sub-menu>

        <el-sub-menu v-if="hasRole(['ADMIN','FINANCE','MANAGER'])" index="finance">
          <template #title>财务管理</template>
          <el-menu-item index="/bills">账单管理</el-menu-item>
          <el-menu-item index="/feerules">收费规则</el-menu-item>
          <el-menu-item index="/finance/dashboard">财务看板</el-menu-item>
        </el-sub-menu>

        <el-sub-menu v-if="hasRole(['ADMIN','MANAGER','FINANCE'])" index="parking">
          <template #title>停车管理</template>
          <el-menu-item index="/parking/spaces">停车管理</el-menu-item>
          <el-menu-item index="/parking/bills">停车账单</el-menu-item>
        </el-sub-menu>

        <el-sub-menu v-if="hasRole(['ADMIN','MANAGER'])" index="energy">
          <template #title>能耗管理</template>
          <el-menu-item index="/energy/records">抄表管理</el-menu-item>
          <el-menu-item index="/energy/stats">能耗统计</el-menu-item>
        </el-sub-menu>

        <el-sub-menu v-if="hasRole(['ADMIN','MANAGER'])" index="ai">
          <template #title>AI分析</template>
          <el-menu-item index="/ai/daily-report">运营日报</el-menu-item>
        </el-sub-menu>

        <el-sub-menu v-if="hasRole(['ADMIN'])" index="system">
          <template #title>系统管理</template>
          <el-menu-item index="/system/users">用户管理</el-menu-item>
          <el-menu-item index="/system/roles">角色管理</el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="system-title">德汇创新中心物业管理平台</div>

        <div class="right">
          <span class="user-role">{{ role }}</span>
          <span class="username">{{ username }}</span>
          <el-button class="logout-btn" size="small" @click="logout">退出</el-button>
        </div>
      </el-header>

      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()

const username = localStorage.getItem('username') || '用户'
const role = localStorage.getItem('role') || 'ADMIN'

function hasRole(roles) {
  return roles.includes(role)
}

function logout() {
  localStorage.clear()
  router.push('/login')
}
</script>

<style scoped>
.layout-container {
  height: 100vh;
  background: #f5f6f8;
}

.aside {
  background: #1f1b1b;
}

.brand {
  height: 84px;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 0 14px;
  background: #171414;
}

.logo {
  width: 64px;
  height: 64px;
  object-fit: contain;
  background: #fff;
  border-radius: 10px;
  padding: 4px;
}

.brand-title {
  font-size: 17px;
  font-weight: 700;
  color: #fff;
}

.brand-subtitle {
  font-size: 12px;
  color: #999;
  margin-top: 3px;
}

.side-menu {
  border-right: none;
}

:deep(.el-menu-item),
:deep(.el-sub-menu__title) {
  height: 48px;
}

:deep(.el-menu-item.is-active) {
  background: rgba(217, 48, 37, 0.15);
  border-right: 4px solid #d93025;
  font-weight: 600;
}

.header {
  height: 60px;
  background: #ffffff;
  border-bottom: 1px solid #eee;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 24px;
}

.system-title {
  font-size: 18px;
  font-weight: 700;
  color: #1f1b1b;
}

.right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-role {
  padding: 3px 10px;
  border-radius: 20px;
  background: #fdecea;
  color: #d93025;
  font-size: 12px;
}

.username {
  color: #303133;
}

.logout-btn {
  background: #d93025;
  color: #fff;
  border: none;
}

.main {
  background: #f5f6f8;
  padding: 20px;
}
</style>
