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
  <AuthShell title="注册" subtitle="用户名 3–32 位字母数字下划线，密码至少 8 位">
    <el-form @submit.prevent="submit">
      <el-form-item>
        <el-input v-model="form.username" placeholder="用户名" autocomplete="username" />
      </el-form-item>
      <el-form-item>
        <el-input v-model="form.displayName" placeholder="显示名" autocomplete="nickname" />
      </el-form-item>
      <el-form-item>
        <el-input
          v-model="form.password"
          type="password"
          show-password
          placeholder="密码"
          autocomplete="new-password"
        />
      </el-form-item>
      <el-button type="primary" native-type="submit" :loading="loading">注册并登录</el-button>
    </el-form>
    <p class="foot">
      已有账号？
      <router-link to="/login">登录</router-link>
    </p>
  </AuthShell>
</template>
