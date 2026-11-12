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
const activeRate = computed(() => {
  if (!openCount.value) return 0
  return Math.round((doingCount.value / openCount.value) * 100)
})

async function load() {
  try {
    data.value = await workbenchApi.get()
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '工作台加载失败')
  }
}

function notifyQuick() {
  ElMessage.info('周报导出可在后续版本接入')
}

onMounted(load)
</script>

<template>
  <div class="v2-page v2-page--wide">
    <header class="v2-page-header">
      <div>
        <h1 class="v2-title">工作台</h1>
        <p class="v2-subtitle">Bento 信息丰富布局 · 你负责的任务与团队动态</p>
      </div>
    </header>

    <section class="bento-grid v2-stagger" aria-label="工作台概览">
      <article class="v2-card bento-tile bento-tile--hero bento-span-5 bento-row-2">
        <div class="bento-label">我负责且未完成</div>
        <div class="bento-value">{{ openCount || '—' }}</div>
        <div v-if="openCount" class="bento-chips">
          <span v-if="todoCount" class="bento-chip bento-chip--plain">{{ todoCount }} 项待办</span>
          <span v-if="doingCount" class="bento-chip">{{ doingCount }} 项进行中</span>
        </div>
      </article>

      <article class="v2-card bento-tile bento-span-3 bento-row-2">
        <div class="bento-label">进行中占比（未完成任务内）</div>
        <div style="display: flex; align-items: center; gap: 14px; margin-top: 10px">
          <div class="bento-ring" :style="{ '--ring-pct': `${activeRate}%` }">{{ activeRate }}%</div>
          <div>
            <strong>共 {{ openCount }} 项未完结</strong>
            <div class="bento-label">基于当前 API 实时计算</div>
          </div>
        </div>
      </article>

      <article class="v2-card bento-tile bento-tile--sub bento-span-4">
        <div class="bento-label">进行中的项目</div>
        <div class="bento-value">{{ data?.inProgressProjectCount ?? '—' }}</div>
        <el-button link style="color: #c7d2fe; margin-top: 8px; padding: 0" @click="router.push('/projects')">
          查看项目列表
        </el-button>
      </article>

      <article class="v2-card bento-tile bento-span-3">
        <div class="bento-label">快捷操作</div>
        <div class="bento-quick">
          <el-button @click="router.push('/projects')">新建项目</el-button>
          <el-button @click="router.push('/projects')">打开看板</el-button>
          <el-button @click="router.push('/notifications')">通知中心</el-button>
          <el-button @click="notifyQuick">导出周报</el-button>
        </div>
      </article>

      <article class="v2-card bento-tile bento-span-12 v2-table-panel" style="padding: 12px 16px 4px">
        <h2 class="v2-section-title" style="margin-top: 0">未完成任务</h2>
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

      <article class="v2-card bento-tile bento-span-5">
        <h2 class="v2-section-title" style="margin-top: 0">最近通知</h2>
        <ul v-if="data?.recentNotifications.length" class="bento-timeline">
          <li v-for="item in data.recentNotifications" :key="item.id">
            {{ item.summary }}
            <time>{{ formatTime(item.createdAt) }}</time>
          </li>
        </ul>
        <p v-else class="v2-empty" style="padding: 12px 0">暂无通知</p>
      </article>

      <article class="v2-card bento-tile bento-span-7">
        <h2 class="v2-section-title" style="margin-top: 0">项目概况</h2>
        <p class="bento-label" style="margin-bottom: 12px">
          当前有 {{ data?.inProgressProjectCount ?? 0 }} 个项目处于进行中，可进入项目页查看阶段与看板。
        </p>
        <div style="display: flex; gap: 12px; flex-wrap: wrap">
          <div class="bento-prog">
            <div class="bento-label">任务处理进度（示意）</div>
            <div class="bento-prog-track">
              <div class="bento-prog-fill" :style="{ width: `${activeRate || 0}%` }" />
            </div>
            <div class="bento-label">进行中任务占未完结 {{ activeRate }}%</div>
          </div>
          <div class="bento-prog">
            <div class="bento-label">项目活跃度</div>
            <div class="bento-prog-track">
              <div
                class="bento-prog-fill"
                :style="{
                  width: `${Math.min(100, (data?.inProgressProjectCount ?? 0) * 35)}%`,
                  background: 'var(--color-accent)',
                }"
              />
            </div>
            <div class="bento-label">{{ data?.inProgressProjectCount ?? 0 }} 个进行中的项目</div>
          </div>
        </div>
        <el-button type="primary" style="margin-top: 14px" @click="router.push('/projects')">前往项目</el-button>
      </article>
    </section>
  </div>
</template>
