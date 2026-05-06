<template>
  <div class="login-page">
    <el-card class="login-card">
      <h2>德汇创新中心物业管理系统</h2>

      <el-form :model="form" @submit.prevent>
        <el-form-item>
          <el-input v-model="form.username" placeholder="用户名" />
        </el-form-item>

        <el-form-item>
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            show-password
            @keyup.enter="handleLogin"
          />
        </el-form-item>

        <el-button type="primary" class="login-button" :loading="loading" @click="handleLogin">
          登录
        </el-button>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../utils/request'

const router = useRouter()
const loading = ref(false)

const form = reactive({
  username: 'admin',
  password: '123456'
})

const handleLogin = async () => {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }

  loading.value = true

  try {
    const res = await request.post('/system/login', form)

    const token = res.token || res?.data?.token

    if (!token) {
      ElMessage.error('登录成功但未返回 token')
      console.log('登录返回结果：', res)
      return
    }

    localStorage.setItem('token', token)
    localStorage.setItem('username', res.username || res?.data?.username || form.username)

    ElMessage.success('登录成功')
    router.push('/dashboard')
  } catch (error) {
    console.error('登录失败：', error)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  background: #f5f7fa;
}

.login-card {
  width: 360px;
}

.login-card h2 {
  text-align: center;
  margin-bottom: 24px;
  font-size: 20px;
}

.login-button {
  width: 100%;
}
</style>
