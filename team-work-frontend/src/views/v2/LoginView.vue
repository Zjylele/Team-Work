<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AuthShell from '../../components/v2/AuthShell.vue'
import { useSessionStore } from '../../stores/session'
import { ApiError } from '../../api/http'

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
  <AuthShell title="登录" subtitle="小型团队项目任务协作">
    <el-form @submit.prevent="submit">
      <el-form-item>
        <el-input v-model="form.username" placeholder="用户名" autocomplete="username" />
      </el-form-item>
      <el-form-item>
        <el-input
          v-model="form.password"
          type="password"
          show-password
          placeholder="密码"
          autocomplete="current-password"
        />
      </el-form-item>
      <el-button type="primary" native-type="submit" :loading="loading">登录</el-button>
    </el-form>
    <p class="foot">
      还没有账号？
      <router-link to="/register">注册</router-link>
    </p>
  </AuthShell>
</template>
