<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { notificationApi } from '../../api/client'
import { ApiError } from '../../api/http'
import type { Notification } from '../../api/types'
import { formatTime } from '../../utils/status'

const router = useRouter()
const list = ref<Notification[]>([])

async function load() {
  try {
    list.value = await notificationApi.list()
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '无法加载通知')
  }
}

async function open(item: Notification) {
  try {
    await notificationApi.read(item.id)
  } catch {
    /* 打开任务优先 */
  }
  await router.push(`/tasks/${item.taskId}`)
}

async function readAll() {
  try {
    await notificationApi.readAll()
    await load()
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '操作失败')
  }
}

onMounted(load)
</script>

<template>
  <div class="v2-page">
    <header class="v2-page-header">
      <div>
        <h1 class="v2-title">通知</h1>
        <p class="v2-subtitle">任务动态与团队消息</p>
      </div>
      <el-button @click="readAll">全部标为已读</el-button>
    </header>
    <div class="v2-table-panel">
      <el-table :data="list" empty-text="暂无通知">
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.read ? 'info' : 'danger'" size="small">{{ row.read ? '已读' : '未读' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="summary" label="内容" min-width="200" />
        <el-table-column label="时间" width="160">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column width="100">
          <template #default="{ row }">
            <el-button link type="primary" @click="open(row)">打开任务</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>
