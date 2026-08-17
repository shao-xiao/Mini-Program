<template>
  <div class="forbidden-page">
    <div class="forbidden-panel">
      <div class="status-code">403</div>
      <div class="title">无权限访问</div>
      <div class="desc">当前账号没有该功能权限，请联系管理员调整角色权限。</div>
      <el-button type="primary" @click="returnHome">返回首页</el-button>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { getCurrentRoles, getFirstAccessibleRoute } from '../../config/access'

const router = useRouter()

function returnHome() {
  router.replace(getFirstAccessibleRoute(getCurrentRoles()))
}
</script>

<style scoped>
.forbidden-page {
  min-height: calc(100vh - 100px);
  display: flex;
  align-items: center;
  justify-content: center;
}

.forbidden-panel {
  width: 360px;
  padding: 36px 32px;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 12px 32px rgba(31, 27, 27, 0.08);
  text-align: center;
}

.status-code {
  color: #d93025;
  font-size: 56px;
  font-weight: 700;
  line-height: 1;
}

.title {
  margin-top: 16px;
  color: #1f2937;
  font-size: 20px;
  font-weight: 600;
}

.desc {
  margin: 10px 0 24px;
  color: #6b7280;
  line-height: 1.6;
}
</style>
