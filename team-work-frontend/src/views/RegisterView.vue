<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useSessionStore } from '../stores/session'
import { ApiError } from '../api/http'

const router = useRouter()
const session = useSessionStore()
const loading = ref(false)
const form = reactive({ username: '', password: '', displayName: '' })

async function submit() {
  loading.value = true
  try {
    await session.register(form.username, form.password, form.displayName)
    await router.push('/join')
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '注册失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="page">
    <el-card class="card" shadow="never">
      <h1>注册</h1>
      <p class="hint">用户名 3–32 位字母数字下划线，密码至少 8 位</p>
      <el-form @submit.prevent="submit">
        <el-form-item>
          <el-input v-model="form.username" placeholder="用户名" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.displayName" placeholder="显示名" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" show-password placeholder="密码" />
        </el-form-item>
        <el-button type="primary" native-type="submit" :loading="loading" style="width: 100%">
          注册并登录
        </el-button>
      </el-form>
      <p class="link">
        已有账号？
        <router-link to="/login">登录</router-link>
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
