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
        <template v-for="item in visibleMenus" :key="item.path || item.index">
          <el-menu-item v-if="item.path" :index="item.path">
            {{ item.title }}
          </el-menu-item>

          <el-sub-menu v-else :index="item.index">
            <template #title>{{ item.title }}</template>
            <el-menu-item
              v-for="child in item.children"
              :key="child.path"
              :index="child.path"
            >
              {{ child.title }}
            </el-menu-item>
          </el-sub-menu>
        </template>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="system-title">德汇创新中心物业管理平台</div>

        <div class="right">
          <span class="user-role">{{ roleText }}</span>
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
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { getCurrentRoles, getVisibleMenuSections } from '../config/access'

const router = useRouter()

const username = localStorage.getItem('username') || '用户'

const currentRoles = getCurrentRoles()
const visibleMenus = computed(() => getVisibleMenuSections(currentRoles))

const roleText = computed(() => {
  return currentRoles.length > 0 ? currentRoles.join(' / ') : '未分配角色'
})

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
