<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { profileApi } from '../api/client'
import { ApiError } from '../api/http'
import { useSessionStore } from '../stores/session'

const session = useSessionStore()
const router = useRouter()
const displayName = ref(session.account?.displayName || '')
const password = reactive({ oldPassword: '', newPassword: '' })

async function saveName() {
  try {
    const result = await profileApi.updateDisplayName(displayName.value)
    if (session.account) {
      session.account.displayName = result.displayName
    }
    ElMessage.success('显示名已更新')
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '无法更新显示名')
  }
}

async function savePassword() {
  try {
    await profileApi.changePassword(password.oldPassword, password.newPassword)
    password.oldPassword = ''
    password.newPassword = ''
    ElMessage.success('密码已更新')
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '无法更新密码')
  }
}

async function regenerate() {
  try {
    await session.regenerateInvite()
    ElMessage.success('邀请码已更新，旧码立即失效')
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '无法重新生成')
  }
}

async function logout() {
  await session.logout()
  await router.push('/login')
}
</script>

<template>
  <div>
    <h1>个人设置</h1>
    <el-card shadow="never" class="block">
      <h2>显示名</h2>
      <el-input v-model="displayName" style="max-width: 320px" />
      <el-button style="margin-left: 8px" type="primary" @click="saveName">保存</el-button>
    </el-card>
    <el-card shadow="never" class="block">
      <h2>修改密码</h2>
      <el-form style="max-width: 320px">
        <el-form-item>
          <el-input v-model="password.oldPassword" type="password" show-password placeholder="原密码" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="password.newPassword" type="password" show-password placeholder="新密码（至少 8 位）" />
        </el-form-item>
        <el-button type="primary" @click="savePassword">更新密码</el-button>
      </el-form>
    </el-card>
    <el-card v-if="session.isCreator" shadow="never" class="block">
      <h2>邀请码</h2>
      <p>当前邀请码：{{ session.team?.inviteCode }}</p>
      <el-button @click="regenerate">重新生成</el-button>
    </el-card>
    <el-card shadow="never" class="block">
      <h2>退出登录</h2>
      <el-button @click="logout">退出</el-button>
    </el-card>
  </div>
</template>

<style scoped>
h1 {
  margin: 0 0 16px;
  font-size: 22px;
}
h2 {
  margin: 0 0 12px;
  font-size: 15px;
}
.block {
  margin-bottom: 12px;
  border-radius: 8px;
}
</style>
