<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { workbenchApi } from '../api/client'
import { ApiError } from '../api/http'
import type { Workbench } from '../api/types'
import { formatTime, statusLabel, statusType } from '../utils/status'

const router = useRouter()
const data = ref<Workbench | null>(null)

async function load() {
  try {
    data.value = await workbenchApi.get()
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '工作台加载失败')
  }
}

onMounted(load)
</script>

<template>
  <div>
    <h1>工作台</h1>
    <div class="cards">
      <el-card shadow="never" class="stat">
        <div class="label">我负责且未完成</div>
        <div class="num">{{ data?.openTasks.length ?? '—' }}</div>
      </el-card>
      <el-card shadow="never" class="stat">
        <div class="label">进行中的项目</div>
        <div class="num">{{ data?.inProgressProjectCount ?? '—' }}</div>
      </el-card>
    </div>

    <h2>未完成任务</h2>
    <el-table :data="data?.openTasks || []" size="small" empty-text="没有未完成的负责任务">
      <el-table-column prop="title" label="标题" />
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="" width="90">
        <template #default="{ row }">
          <el-button link type="primary" @click="router.push(`/tasks/${row.id}`)">打开</el-button>
        </template>
      </el-table-column>
    </el-table>

    <h2>最近通知</h2>
    <el-table :data="data?.recentNotifications || []" size="small" empty-text="暂无通知">
      <el-table-column prop="summary" label="内容" />
      <el-table-column label="时间" width="160">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="" width="90">
        <template #default="{ row }">
          <el-button link type="primary" @click="router.push(`/tasks/${row.taskId}`)">打开</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style scoped>
h1 {
  margin: 0 0 16px;
  font-size: 22px;
}
h2 {
  margin: 24px 0 8px;
  font-size: 16px;
}
.cards {
  display: grid;
  grid-template-columns: repeat(2, minmax(180px, 280px));
  gap: 12px;
}
.stat {
  border-radius: 8px;
}
.label {
  color: #6b7280;
  font-size: 13px;
}
.num {
  font-size: 28px;
  font-weight: 600;
  margin-top: 6px;
}
</style>
