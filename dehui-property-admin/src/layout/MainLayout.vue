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
          <el-button class="change-password-btn" size="small" @click="openPasswordDialog">
            修改密码
          </el-button>
          <el-button class="logout-btn" size="small" @click="logout">退出</el-button>
        </div>
      </el-header>

      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>

    <el-dialog
      v-model="passwordDialogVisible"
      title="修改密码"
      width="420px"
      destroy-on-close
    >
      <el-form
        ref="passwordFormRef"
        :model="passwordForm"
        :rules="passwordRules"
        label-width="90px"
      >
        <el-form-item label="原密码" prop="oldPassword">
          <el-input
            v-model="passwordForm.oldPassword"
            type="password"
            show-password
            autocomplete="current-password"
          />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input
            v-model="passwordForm.newPassword"
            type="password"
            show-password
            autocomplete="new-password"
          />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="passwordForm.confirmPassword"
            type="password"
            show-password
            autocomplete="new-password"
            @keyup.enter="submitPasswordChange"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="passwordDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="passwordSubmitting"
          @click="submitPasswordChange"
        >
          保存
        </el-button>
      </template>
    </el-dialog>
  </el-container>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getCurrentRoles, getVisibleMenuSections } from '../config/access'
import request from '../utils/request'

const router = useRouter()

const username = localStorage.getItem('username') || '用户'

const currentRoles = getCurrentRoles()
const visibleMenus = computed(() => getVisibleMenuSections(currentRoles))

const roleText = computed(() => {
  return currentRoles.length > 0 ? currentRoles.join(' / ') : '未分配角色'
})

const passwordDialogVisible = ref(false)
const passwordSubmitting = ref(false)
const passwordFormRef = ref()

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const validateConfirmPassword = (rule, value, callback) => {
  if (!value) {
    callback(new Error('请再次输入新密码'))
    return
  }

  if (value !== passwordForm.newPassword) {
    callback(new Error('两次输入的新密码不一致'))
    return
  }

  callback()
}

const passwordRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '新密码至少6位', trigger: 'blur' }
  ],
  confirmPassword: [{ validator: validateConfirmPassword, trigger: 'blur' }]
}

function resetPasswordForm() {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  passwordFormRef.value?.clearValidate()
}

function openPasswordDialog() {
  resetPasswordForm()
  passwordDialogVisible.value = true
}

async function submitPasswordChange() {
  await passwordFormRef.value?.validate()

  passwordSubmitting.value = true
  try {
    await request.patch('/system/me/password', { ...passwordForm })
    ElMessage.success('密码修改成功，请重新登录')
    passwordDialogVisible.value = false
    logout()
  } finally {
    passwordSubmitting.value = false
  }
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

.change-password-btn {
  color: #d93025;
  border-color: #f0b8b3;
  background: #fff;
}

.change-password-btn:hover,
.change-password-btn:focus {
  color: #fff;
  border-color: #d93025;
  background: #d93025;
}

.main {
  background: #f5f6f8;
  padding: 20px;
}
</style>
