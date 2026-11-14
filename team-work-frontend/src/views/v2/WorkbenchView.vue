<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Bell, Calendar, Collection, Folder, Refresh } from '@element-plus/icons-vue'
import { projectApi, workbenchApi } from '../../api/client'
import type { Project, Workbench } from '../../api/types'
import { formatTime, statusLabel, statusType } from '../../utils/status'

const data = ref<Workbench | null>(null)
const projects = ref<Project[]>([])
const loading = ref(false)
const tasks = computed(() => data.value?.openTasks ?? [])
const projectNames = computed(() => new Map(projects.value.map(({ id, name }) => [id, name])))
const deadlines = computed(() => tasks.value.filter((task) => task.dueDate)
  .sort((a, b) => (a.dueDate ?? '').localeCompare(b.dueDate ?? '')).slice(0, 6))

async function load() {
  if (loading.value) return
  loading.value = true
  try {
    const [workbench, teamProjects] = await Promise.all([workbenchApi.get(), projectApi.list()])
    data.value = workbench
    projects.value = teamProjects
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '工作台加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="dashboard">
    <header class="dashboard-bar">
      <div>
        <h1>工作台</h1>
        <p>查看团队项目、我的任务和最新动态</p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </header>
    <div v-loading="loading" class="dashboard-grid">
      <article class="dashboard-panel">
        <header>
          <span class="panel-icon blue"><el-icon><Folder /></el-icon></span>
          <div><h2>团队项目</h2><p>{{ projects.length }} 个项目 · {{ data?.inProgressProjectCount ?? 0 }} 个进行中</p></div>
          <RouterLink to="/projects">查看全部</RouterLink>
        </header>
        <div v-if="projects.length" class="panel-rows">
          <RouterLink v-for="project in projects" :key="project.id" class="panel-row" :to="'/projects/' + project.id">
            <el-icon><Folder /></el-icon><span>{{ project.name }}</span>
          </RouterLink>
        </div>
        <p v-else class="dashboard-empty"><el-icon><Folder /></el-icon>暂无项目</p>
      </article>

      <article class="dashboard-panel">
        <header>
          <span class="panel-icon green"><el-icon><Collection /></el-icon></span>
          <div><h2>我负责的任务</h2><p>{{ tasks.length }} 项未完成</p></div>
        </header>
        <div v-if="tasks.length" class="panel-rows">
          <RouterLink v-for="task in tasks" :key="task.id" class="panel-row" :to="'/tasks/' + task.id">
            <span>{{ task.title }}</span>
            <el-tag size="small" round :type="statusType(task.status)">{{ statusLabel(task.status) }}</el-tag>
          </RouterLink>
        </div>
        <p v-else class="dashboard-empty"><el-icon><Collection /></el-icon>没有未完成的负责任务</p>
      </article>

      <article class="dashboard-panel">
        <header>
          <span class="panel-icon amber"><el-icon><Calendar /></el-icon></span>
          <div><h2>任务截止日期</h2><p>按截止时间排序</p></div>
        </header>
        <div v-if="deadlines.length" class="panel-rows">
          <RouterLink v-for="task in deadlines" :key="task.id" class="panel-row" :to="'/tasks/' + task.id">
            <span>{{ task.title }}<small v-if="projectNames.has(task.projectId)">{{ projectNames.get(task.projectId) }}</small></span>
            <time>{{ task.dueDate }}</time>
          </RouterLink>
        </div>
        <p v-else class="dashboard-empty"><el-icon><Calendar /></el-icon>暂无设定截止日期的未完成任务</p>
      </article>

      <article class="dashboard-panel">
        <header>
          <span class="panel-icon violet"><el-icon><Bell /></el-icon></span>
          <div><h2>最近通知</h2><p>{{ data?.recentNotifications.length ?? 0 }} 条动态</p></div>
          <RouterLink to="/notifications">查看全部</RouterLink>
        </header>
        <div v-if="data?.recentNotifications.length" class="panel-rows">
          <RouterLink v-for="item in data.recentNotifications" :key="item.id" class="panel-row" :to="'/tasks/' + item.taskId">
            <span>{{ item.summary }}</span><time>{{ formatTime(item.createdAt) }}</time>
          </RouterLink>
        </div>
        <p v-else class="dashboard-empty"><el-icon><Bell /></el-icon>暂无通知</p>
      </article>
    </div>
  </section>
</template>

<style scoped>
.dashboard { max-width: 1680px; margin: 0 auto; }
.dashboard-bar { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 16px; }
.dashboard-bar h1 { font-size: 23px; font-weight: 650; margin: 0 0 4px; }
.dashboard-bar p, .dashboard-panel header p { margin: 0; font-size: 12px; }
.dashboard-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.dashboard-panel { min-height: 246px; background: white; border: 1px solid #e4e8ef; border-radius: 8px; overflow: hidden; }
.dashboard-panel header { display: flex; align-items: center; gap: 10px; padding: 14px 16px; border-bottom: 1px solid #edf0f4; }
.dashboard-panel h2 { margin: 0 0 3px; font-size: 14px; font-weight: 650; }
.dashboard-panel header a { margin-left: auto; font-size: 12px; white-space: nowrap; }
.panel-icon { display: grid; place-items: center; width: 32px; height: 32px; border-radius: 7px; font-size: 17px; }
.blue { background: #eaf1ff; }
.green { background: #e6f6ed; }
.amber { background: #fff1d8; }
.violet { background: #f1ebff; }
.panel-rows { max-height: 260px; overflow: auto; }
.panel-row { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 10px 16px; min-height: 43px; box-sizing: border-box; border-bottom: 1px solid #f0f2f5; font-size: 13px; }
.panel-row:hover { background: #f1f6ff; }
.panel-row > span { flex: 1; min-width: 0; overflow-wrap: anywhere; }
.panel-row > .el-tag { flex: none; }
.panel-row time { flex: none; font-size: 11px; }
.panel-row small { display: block; margin-top: 3px; font-size: 11px; }
.dashboard-empty { min-height: 170px; margin: 0; display: grid; align-content: center; justify-items: center; gap: 10px; text-align: center; font-size: 13px; }
.dashboard-empty .el-icon { padding: 10px; font-size: 20px; border-radius: 10px; background: #f1f5fb; }
@media (max-width: 860px) {
  .dashboard-grid { grid-template-columns: minmax(0, 1fr); }
}
</style>
