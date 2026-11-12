<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AuthShell from '../../components/v2/AuthShell.vue'
import { useSessionStore } from '../../stores/session'
import { ApiError } from '../../api/http'

const router = useRouter()
const session = useSessionStore()
const creating = ref(false)
const joining = ref(false)
const createForm = reactive({ name: '' })
const joinForm = reactive({ code: '' })

async function createTeam() {
  creating.value = true
  try {
    await session.createTeam(createForm.name)
    ElMessage.success('团队已创建')
    await router.push('/')
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '创建失败')
  } finally {
    creating.value = false
  }
}

async function joinTeam() {
  joining.value = true
  try {
    await session.joinTeam(joinForm.code.trim())
    ElMessage.success('已加入团队')
    await router.push('/')
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '加入失败')
  } finally {
    joining.value = false
  }
}

async function logout() {
  await session.logout()
  await router.push('/login')
}
</script>

<template>
  <AuthShell title="进入团队" subtitle="尚未加入团队。可以创建唯一团队，或用 5 位邀请码加入。">
    <el-form @submit.prevent="createTeam">
      <el-form-item>
        <el-input v-model="createForm.name" placeholder="团队名称" />
      </el-form-item>
      <el-button type="primary" native-type="submit" :loading="creating">创建团队</el-button>
    </el-form>
    <el-divider>或</el-divider>
    <el-form @submit.prevent="joinTeam">
      <el-form-item>
        <el-input v-model="joinForm.code" maxlength="5" placeholder="邀请码" />
      </el-form-item>
      <el-button native-type="submit" :loading="joining">加入团队</el-button>
    </el-form>
    <p class="foot">
      当前用户 {{ session.account?.displayName }}
      <button type="button" class="inline" @click="logout">退出登录</button>
    </p>
  </AuthShell>
</template>

<style scoped>
.inline {
  border: 0;
  background: none;
  color: var(--color-primary);
  cursor: pointer;
  padding: 0;
  margin-left: 4px;
}
</style>
