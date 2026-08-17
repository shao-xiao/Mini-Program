<template>
  <div class="login-page" :style="loginPageStyle">
    <div class="login-page-brand">
      <img class="login-page-brand-logo" :src="dehuiLogoUrl" alt="" />
      <div>
        <strong>德汇创新中心</strong>
        <span>物业管理系统</span>
      </div>
    </div>

    <el-card class="login-card login-shell" shadow="never">
      <div class="login-card-accent" aria-hidden="true"></div>
      <div class="login-card-frame" aria-hidden="true"></div>

      <section class="login-visual-panel" aria-label="德汇创新中心">
        <div class="login-brand">
          <img class="login-logo" :src="dehuiLogoUrl" alt="德汇创新中心" />
          <div class="login-brand-text">
            <h1>德汇创新中心</h1>
            <p>物业管理系统</p>
          </div>
        </div>

        <div class="login-visual-copy">
          <span class="login-visual-kicker">DHIC Innovation Center</span>
          <div class="login-visual-divider" aria-hidden="true"></div>
          <h2>企业物业运营后台</h2>
          <p>资产、租赁、能耗、工单与财务数据统一管理。</p>
        </div>

        <div class="login-visual-footer">
          <span>Property Operations</span>
          <span>AI + BI</span>
        </div>
      </section>

      <section class="login-form-panel">
        <div class="login-heading">
          <div class="login-form-mark">
            <img :src="dehuiLogoUrl" alt="" />
          </div>
          <div class="login-system-tag">DHIC ADMIN</div>
          <h2>后台管理登录</h2>
          <p class="login-subtitle">统一身份认证入口</p>
        </div>

        <el-form class="login-form" :model="form" @submit.prevent>
          <div class="login-form-divider" aria-hidden="true"></div>

          <el-alert
            v-if="errorMessage"
            class="login-error"
            :title="errorMessage"
            type="error"
            show-icon
            :closable="false"
          />

          <el-form-item>
            <label class="login-field-label">用户名</label>
            <el-input v-model="form.username" placeholder="请输入用户名" @input="clearLoginError" />
          </el-form-item>

          <el-form-item>
            <label class="login-field-label">密码</label>
            <el-input
              v-model="form.password"
              type="password"
              placeholder="请输入密码"
              show-password
              @input="clearLoginError"
              @keyup.enter="handleLogin"
            />
          </el-form-item>

          <el-button type="primary" class="login-button" :loading="loading" @click="handleLogin">
            登录
          </el-button>
          <div class="login-security-line">统一身份认证 · 权限实时生效</div>
        </el-form>

        <div class="login-meta">
          <span>DHIC Property Management</span>
          <span>AI + BI 运营平台</span>
        </div>
      </section>
    </el-card>

    <div class="login-page-footnote">DHIC Property Management Platform</div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../utils/request'
import { getFirstAccessibleRoute } from '../config/access'
import { applyUserAccessToStorage, ensureCurrentUserAccess, resetCurrentUserAccessRefresh } from '../utils/authAccess'
import loginBackgroundUrl from '../assets/login-background.jpg'
import dehuiLogoUrl from '../assets/dehui-logo.png'

const router = useRouter()
const loading = ref(false)
const errorMessage = ref('')
const loginPageStyle = {
  '--login-bg-url': `url(${loginBackgroundUrl})`
}

const form = reactive({
  username: '',
  password: ''
})

const unwrap = (res) => {
  return res?.data || res || {}
}

const loginErrorMessage = (error) => {
  const backendMessage = error?.response?.data?.message || error?.data?.message
  if (backendMessage) return backendMessage

  const message = error?.message || ''
  return message.includes('status code') ? '' : message
}

const handleLogin = async () => {
  errorMessage.value = ''
  if (!form.username || !form.password) {
    errorMessage.value = '请输入用户名和密码'
    return
  }

  loading.value = true

  try {
    const payload = {
      username: form.username?.trim(),
      password: form.password
    }
    const res = await request.post('/system/login', payload, {
      silent: true,
      headers: { 'Content-Type': 'application/json' }
    })
    const data = unwrap(res)

    const token = data.token
    const roles = Array.isArray(data.roles) ? data.roles : []
    const permissions = Array.isArray(data.permissions) ? data.permissions : []
    const menus = Array.isArray(data.menus) ? data.menus : []

    if (!token) {
      ElMessage.error('登录成功但未返回 token')
      console.log('登录返回结果：', res)
      return
    }

    localStorage.setItem('token', token)
    localStorage.setItem('userId', data.userId || '')
    localStorage.setItem('username', data.username || form.username)
    localStorage.setItem('userInfo', JSON.stringify(data.userInfo || {
      userId: data.userId,
      username: data.username || form.username,
      realName: data.realName || '',
      roles
    }))

    // 新版 RBAC：保存真实角色列表
    localStorage.setItem('roles', JSON.stringify(roles))
    localStorage.setItem('permissions', JSON.stringify(permissions))
    localStorage.setItem('menus', JSON.stringify(menus))

    // 兼容旧版单角色逻辑，后续动态菜单升级完成后再移除
    localStorage.setItem('role', roles[0] || '')
    resetCurrentUserAccessRefresh()
    applyUserAccessToStorage(data)
    const access = await ensureCurrentUserAccess({ force: true, silent: true })

    ElMessage.success('登录成功')
    router.replace(getFirstAccessibleRoute(access?.roles || roles))
  } catch (error) {
    console.error('登录失败：', error)
    errorMessage.value = loginErrorMessage(error) || '账号或密码错误，请重新输入'
  } finally {
    loading.value = false
  }
}

const clearLoginError = () => {
  if (errorMessage.value) {
    errorMessage.value = ''
  }
}
</script>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  position: relative;
  isolation: isolate;
  padding: 32px;
  overflow: hidden;
  background:
    linear-gradient(90deg, rgba(5, 10, 20, 0.68) 0%, rgba(15, 23, 42, 0.32) 50%, rgba(15, 23, 42, 0.42) 100%),
    linear-gradient(180deg, rgba(15, 23, 42, 0.08) 0%, rgba(15, 23, 42, 0.56) 100%),
    var(--login-bg-url) center center / cover no-repeat;
}

.login-page::before {
  content: '';
  position: absolute;
  inset: 0;
  z-index: -1;
  pointer-events: none;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.08) 0%, rgba(255, 255, 255, 0) 32%),
    linear-gradient(90deg, rgba(0, 0, 0, 0.32) 0%, rgba(0, 0, 0, 0) 36%, rgba(0, 0, 0, 0.18) 100%);
}

.login-page::after {
  content: '';
  position: absolute;
  inset: 0;
  z-index: -1;
  pointer-events: none;
  background:
    linear-gradient(115deg, rgba(255, 255, 255, 0.1) 0%, rgba(255, 255, 255, 0) 34%),
    repeating-linear-gradient(90deg, rgba(255, 255, 255, 0.035) 0 1px, transparent 1px 112px);
  opacity: 0.72;
}

.login-page-brand {
  position: absolute;
  top: 28px;
  left: 32px;
  z-index: 2;
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 218px;
  padding: 10px 14px 10px 10px;
  border: 1px solid rgba(255, 255, 255, 0.28);
  border-radius: 8px;
  background: rgba(8, 13, 25, 0.32);
  color: #fff;
  box-shadow: 0 18px 48px rgba(0, 0, 0, 0.16);
  backdrop-filter: blur(12px);
}

.login-page-brand-logo {
  width: 36px;
  height: 36px;
  flex: none;
  object-fit: contain;
  padding: 4px;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.96);
}

.login-page-brand strong,
.login-page-brand span {
  display: block;
  line-height: 1.25;
}

.login-page-brand strong {
  font-size: 15px;
  font-weight: 700;
}

.login-page-brand span {
  margin-top: 3px;
  color: rgba(255, 255, 255, 0.68);
  font-size: 12px;
}

.login-card {
  position: relative;
  overflow: hidden;
  width: min(940px, calc(100vw - 48px));
  border: 1px solid rgba(255, 255, 255, 0.78);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow:
    0 48px 118px rgba(15, 23, 42, 0.44),
    0 16px 36px rgba(15, 23, 42, 0.18),
    0 1px 0 rgba(255, 255, 255, 0.6) inset;
  backdrop-filter: blur(20px);
}

.login-card::before {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.76) 0%, rgba(255, 255, 255, 0) 38%),
    linear-gradient(90deg, rgba(220, 38, 38, 0.08), rgba(255, 255, 255, 0) 34%);
}

.login-card-accent {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  z-index: 2;
  height: 4px;
  background: linear-gradient(90deg, #991b1b 0%, #dc2626 48%, #ef4444 100%);
}

.login-card-frame {
  position: absolute;
  inset: 12px;
  z-index: 2;
  pointer-events: none;
  border: 1px solid rgba(255, 255, 255, 0.34);
  border-radius: 6px;
}

.login-card :deep(.el-card__body) {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: minmax(300px, 0.95fr) minmax(390px, 1fr);
  min-height: 462px;
  padding: 0;
}

.login-visual-panel,
.login-form-panel {
  position: relative;
  min-width: 0;
}

.login-visual-panel {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 38px 34px 32px;
  overflow: hidden;
  background:
    linear-gradient(160deg, rgba(127, 29, 29, 0.88) 0%, rgba(69, 10, 10, 0.8) 46%, rgba(15, 23, 42, 0.9) 100%),
    var(--login-bg-url) center center / cover no-repeat;
  color: #fff;
}

.login-visual-panel::before {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.14), rgba(255, 255, 255, 0)),
    linear-gradient(90deg, rgba(255, 255, 255, 0.04), rgba(255, 255, 255, 0));
}

.login-form-panel {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 42px 48px 32px;
  border-left: 1px solid rgba(148, 163, 184, 0.22);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(248, 250, 252, 0.96)),
    rgba(255, 255, 255, 0.96);
}

.login-brand {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 12px;
  position: relative;
  z-index: 1;
}

.login-logo {
  width: 50px;
  height: 50px;
  flex: none;
  object-fit: contain;
  padding: 5px;
  border-radius: 8px;
  background: #fff;
  border: 1px solid rgba(226, 232, 240, 0.9);
  box-shadow: 0 12px 26px rgba(0, 0, 0, 0.18);
}

.login-brand-text h1 {
  margin: 0;
  color: #fff;
  font-size: 20px;
  font-weight: 700;
  line-height: 1.25;
}

.login-brand-text p {
  margin: 3px 0 0;
  color: rgba(255, 255, 255, 0.72);
  font-size: 13px;
}

.login-visual-copy {
  position: relative;
  z-index: 1;
  max-width: 260px;
}

.login-visual-kicker {
  display: inline-flex;
  margin-bottom: 14px;
  padding: 4px 9px;
  border: 1px solid rgba(255, 255, 255, 0.22);
  border-radius: 999px;
  color: rgba(255, 255, 255, 0.82);
  font-size: 12px;
  font-weight: 600;
}

.login-visual-divider {
  width: 58px;
  height: 3px;
  margin-bottom: 18px;
  border-radius: 999px;
  background: linear-gradient(90deg, #fff 0%, rgba(255, 255, 255, 0.18) 100%);
}

.login-visual-copy h2 {
  margin: 0;
  color: #fff;
  font-size: 28px;
  font-weight: 700;
  line-height: 1.25;
}

.login-visual-copy p {
  margin: 12px 0 0;
  color: rgba(255, 255, 255, 0.76);
  font-size: 14px;
  line-height: 1.7;
}

.login-visual-footer {
  position: relative;
  z-index: 1;
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding-top: 16px;
  border-top: 1px solid rgba(255, 255, 255, 0.16);
  color: rgba(255, 255, 255, 0.66);
  font-size: 12px;
}

.login-heading {
  margin: 0 0 18px;
  text-align: center;
}

.login-form-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 54px;
  height: 54px;
  margin-bottom: 14px;
  border: 1px solid rgba(220, 38, 38, 0.12);
  border-radius: 8px;
  background: linear-gradient(180deg, #fff, #f8fafc);
  box-shadow:
    0 16px 30px rgba(15, 23, 42, 0.08),
    0 1px 0 rgba(255, 255, 255, 0.9) inset;
}

.login-form-mark img {
  width: 40px;
  height: 40px;
  object-fit: contain;
}

.login-system-tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 22px;
  margin-bottom: 9px;
  padding: 0 10px;
  border: 1px solid rgba(220, 38, 38, 0.14);
  border-radius: 999px;
  background: #fef2f2;
  color: #b91c1c;
  font-size: 12px;
  font-weight: 700;
}

.login-heading h2 {
  margin: 0;
  color: #1f2937;
  font-size: 23px;
  font-weight: 700;
}

.login-subtitle {
  margin: 8px 0 0;
  color: #6b7280;
  font-size: 13px;
  line-height: 1.5;
}

.login-form {
  margin-top: 0;
}

.login-form-divider {
  height: 1px;
  margin: 0 0 18px;
  background: linear-gradient(90deg, transparent 0%, rgba(185, 28, 28, 0.2) 22%, rgba(148, 163, 184, 0.28) 50%, transparent 100%);
}

.login-form :deep(.el-form-item) {
  margin-bottom: 15px;
}

.login-form :deep(.el-form-item__content) {
  display: block;
}

.login-field-label {
  display: flex;
  align-items: center;
  margin-bottom: 7px;
  color: #344054;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.2;
}

.login-form :deep(.el-input__wrapper) {
  min-height: 43px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 0 0 1px rgba(203, 213, 225, 0.72) inset;
  transition: box-shadow 0.2s ease, background-color 0.2s ease;
}

.login-form :deep(.el-input__inner::placeholder) {
  color: #98a2b3;
}

.login-form :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px rgba(185, 28, 28, 0.32) inset;
}

.login-form :deep(.el-input__wrapper.is-focus) {
  background: #fff;
  box-shadow:
    0 0 0 1px rgba(220, 38, 38, 0.8) inset,
    0 0 0 3px rgba(220, 38, 38, 0.1);
}

.login-error {
  margin-bottom: 14px;
  border-radius: 8px;
  line-height: 1.4;
}

.login-error :deep(.el-alert__title) {
  font-size: 13px;
}

.login-button {
  width: 100%;
  height: 46px;
  margin-top: 4px;
  border: 0;
  border-radius: 8px;
  background: linear-gradient(90deg, #e0312b 0%, #bd1f1f 100%);
  font-weight: 600;
  box-shadow: 0 14px 28px rgba(220, 38, 38, 0.24);
  transition: background 0.2s ease, box-shadow 0.2s ease;
}

.login-button:hover,
.login-button:focus {
  background: linear-gradient(90deg, #b91c1c 0%, #991b1b 100%);
  box-shadow: 0 14px 28px rgba(185, 28, 28, 0.28);
}

.login-security-line {
  margin-top: 12px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.5;
  text-align: center;
}

.login-meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-top: auto;
  padding-top: 16px;
  border-top: 1px solid rgba(148, 163, 184, 0.2);
  color: #8a94a6;
  font-size: 12px;
}

.login-page-footnote {
  position: absolute;
  right: 32px;
  bottom: 24px;
  z-index: 2;
  padding: 7px 11px;
  border: 1px solid rgba(255, 255, 255, 0.18);
  border-radius: 8px;
  background: rgba(8, 13, 25, 0.24);
  color: rgba(255, 255, 255, 0.7);
  font-size: 12px;
  line-height: 1;
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.16);
  backdrop-filter: blur(10px);
}

@media (max-width: 760px) {
  .login-page-brand {
    top: 18px;
    left: 20px;
    min-width: 0;
    padding-right: 12px;
  }

  .login-card {
    width: min(440px, calc(100vw - 40px));
  }

  .login-card-frame {
    inset: 10px;
  }

  .login-card :deep(.el-card__body) {
    display: block;
    min-height: 0;
  }

  .login-visual-panel {
    padding: 26px 24px 22px;
  }

  .login-visual-copy {
    margin-top: 28px;
  }

  .login-form-panel {
    padding: 28px 24px 22px;
    border-left: 0;
  }

  .login-heading {
    text-align: center;
  }
}

@media (max-width: 520px) {
  .login-page {
    padding: 20px;
    background:
      linear-gradient(180deg, rgba(11, 18, 32, 0.44), rgba(15, 23, 42, 0.52)),
      var(--login-bg-url) center center / cover no-repeat;
  }

  .login-page-brand {
    display: none;
  }

  .login-page-footnote {
    display: none;
  }

  .login-visual-copy h2 {
    font-size: 22px;
  }

  .login-meta {
    flex-direction: column;
    gap: 4px;
    text-align: center;
  }
}
</style>
