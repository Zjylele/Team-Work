<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useSessionStore } from '../stores/session'
import { ApiError } from '../api/http'

const router = useRouter()
const session = useSessionStore()
const loading = ref(false)
const form = reactive({ username: '', password: '' })

async function submit() {
  loading.value = true
  try {
    await session.login(form.username, form.password)
    await router.push(session.team ? '/' : '/join')
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="page">
    <el-card class="card" shadow="never">
      <h1>登录</h1>
      <p class="hint">小型团队项目任务协作</p>
      <el-form @submit.prevent="submit">
        <el-form-item>
          <el-input v-model="form.username" placeholder="用户名" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" show-password placeholder="密码" />
        </el-form-item>
        <el-button type="primary" native-type="submit" :loading="loading" style="width: 100%">
          登录
        </el-button>
      </el-form>
      <p class="link">
        还没有账号？
        <router-link to="/register">注册</router-link>
      </p>
    </el-card>
  </div>
</template>

<style scoped>
.page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  background: #f4f5f7;
}
.card {
  width: 360px;
  border-radius: 8px;
}
h1 {
  margin: 0 0 4px;
  font-size: 20px;
}
.hint,
.link {
  color: #6b7280;
  font-size: 13px;
}
</style>
