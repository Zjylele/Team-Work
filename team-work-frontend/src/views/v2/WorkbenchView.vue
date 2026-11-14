<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { workbenchApi } from '../../api/client'
import { ApiError } from '../../api/http'
import type { Workbench } from '../../api/types'
import { formatTime, statusLabel, statusType } from '../../utils/status'

const router = useRouter()
const data = ref<Workbench | null>(null)

const openCount = computed(() => data.value?.openTasks.length ?? 0)
const doingCount = computed(() => data.value?.openTasks.filter((t) => t.status === 'DOING').length ?? 0)
const todoCount = computed(() => data.value?.openTasks.filter((t) => t.status === 'TODO').length ?? 0)
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
  <div class="v2-page">
    <header class="v2-page-header">
      <div>
        <h1 class="v2-title">工作台</h1>
        <p class="v2-subtitle">跟进你负责的任务和最近的团队动态。</p>
      </div>
      <el-button type="primary" @click="router.push('/projects')">浏览项目</el-button>
    </header>

    <section class="workspace-metrics" aria-label="工作台概览">
      <article class="v2-card v2-stat">
        <div class="v2-stat__label">我的未完成任务</div>
        <div class="v2-stat__value">{{ data ? openCount : '—' }}</div>
        <p>{{ todoCount }} 项待开始 · {{ doingCount }} 项进行中</p>
      </article>
      <article class="v2-card v2-stat">
        <div class="v2-stat__label">进行中的项目</div>
        <div class="v2-stat__value">{{ data?.inProgressProjectCount ?? '—' }}</div>
        <p>包含进行中任务的项目</p>
      </article>
    </section>

    <section class="workspace-panels">
      <article class="v2-card v2-table-panel">
        <h2>我的任务</h2>
        <el-table :data="data?.openTasks || []" empty-text="没有未完成的负责任务">
          <el-table-column prop="title" label="标题" min-width="160" />
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
      </article>
      <article class="v2-card activity-panel">
        <div class="activity-heading">
          <h2>最近通知</h2>
          <el-button link @click="router.push('/notifications')">查看全部</el-button>
        </div>
        <ul v-if="data?.recentNotifications.length" class="bento-timeline">
          <li v-for="item in data.recentNotifications" :key="item.id">
            {{ item.summary }}
            <time>{{ formatTime(item.createdAt) }}</time>
          </li>
        </ul>
        <p v-else class="v2-empty">暂无通知</p>
      </article>
    </section>
  </div>
</template>

<style scoped>
.workspace-metrics,
.workspace-panels {
  display: grid;
  gap: 16px;
}
.workspace-metrics {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin-bottom: 24px;
}
.workspace-panels {
  grid-template-columns: minmax(0, 2fr) minmax(0, 1fr);
  align-items: start;
}
.workspace-panels h2 {
  margin: 0;
  font-size: 16px;
}
.v2-table-panel > h2,
.activity-panel {
  padding: 16px 20px;
}
.v2-stat p {
  margin: 12px 0 0;
  font-size: 13px;
}
.activity-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
@media (max-width: 840px) {
  .workspace-panels {
    grid-template-columns: minmax(0, 1fr);
  }
}
@media (max-width: 480px) {
  .workspace-metrics {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
