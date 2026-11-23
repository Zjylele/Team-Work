<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { get, put } from '../api/http'

interface Channels { webhookAvailable: boolean; webhookEnabled: boolean }
const endpoint = '/api/notifications/channels'
const channels = ref<Channels>()
const busy = ref(false)
const errorText = ref('')
async function load() {
  busy.value = true
  errorText.value = ''
  try { channels.value = await get<Channels>(endpoint) }
  catch (error) { errorText.value = error instanceof Error ? error.message : '无法加载通知渠道' }
  finally { busy.value = false }
}
async function change(webhookEnabled: boolean) {
  if (busy.value) return
  busy.value = true
  try {
    channels.value = await put<Channels>(endpoint, { webhookEnabled })
    ElMessage.success('通知渠道已更新')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '无法更新通知渠道') }
  finally { busy.value = false }
}
onMounted(load)
</script>

<template>
  <el-card class="notification-channels">
    <template #header>通知渠道</template>
    <div>站内通知：已启用</div>
    <div v-if="errorText" role="alert">{{ errorText }} <el-button link @click="load">重试</el-button></div>
    <div v-else-if="channels?.webhookAvailable">
      <el-switch :model-value="channels.webhookEnabled" :loading="busy" active-text="Webhook 通知" @update:model-value="change($event === true)" />
    </div>
    <div v-else-if="channels">Webhook 通知：服务端尚未配置</div>
    <div v-else>正在加载通知渠道…</div>
  </el-card>
</template>

<style scoped>
.notification-channels { margin-bottom: 16px; }
</style>
