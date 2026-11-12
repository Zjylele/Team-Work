<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useSessionStore } from '../stores/session'
import { ApiError } from '../api/http'

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
  <div class="page">
    <el-card class="card" shadow="never">
      <h1>进入团队</h1>
      <p class="hint">尚未加入团队。可以创建唯一团队，或用 5 位邀请码加入。</p>
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
      <p class="link">
        当前用户 {{ session.account?.displayName }}
        <button class="ghost" type="button" @click="logout">退出登录</button>
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
  width: 420px;
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
.ghost {
  background: none;
  border: 0;
  color: #3f76ff;
  cursor: pointer;
}
</style>
