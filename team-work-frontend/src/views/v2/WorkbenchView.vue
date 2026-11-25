<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Bell, Calendar, Folder, Refresh } from '@element-plus/icons-vue'
import { projectApi, workbenchApi } from '../../api/client'
import type { Project, Workbench } from '../../api/types'
import { formatTime, STATUS_OPTIONS } from '../../utils/status'

const data = ref<Workbench | null>(null)
const projects = ref<Project[]>([])
const loading = ref(false)
const tasks = computed(() => data.value?.openTasks ?? [])
const projectNames = computed(() => new Map(projects.value.map(({ id, name }) => [id, name])))
const deadlines = computed(() => tasks.value.filter((task) => task.dueDate)
  .sort((a, b) => (a.dueDate ?? '').localeCompare(b.dueDate ?? '')).slice(0, 6))

const columns = computed(() => STATUS_OPTIONS.filter(({ value }) => value !== 'DONE').map(
  ({ value, label }) => ({ value, label, tasks: tasks.value.filter((task) => task.status === value) }),
))

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
    <div v-loading="loading" class="dashboard-body">
      <dl class="summary">
        <div><dt>我的未完成任务</dt><dd>{{ data ? tasks.length : '—' }}</dd></div>
        <div v-for="column in columns" :key="column.value" :data-status="column.value">
          <dt>{{ column.label }}</dt><dd>{{ data ? column.tasks.length : '—' }}</dd>
        </div>
      </dl>
      <section class="board" aria-labelledby="board-title">
        <header class="board-header">
          <h2 id="board-title">我负责的任务</h2>
          <RouterLink to="/calendar">任务日历</RouterLink>
        </header>
        <div class="board-columns">
          <section v-for="column in columns" :key="column.value" :data-status="column.value" class="board-column">
            <h3>{{ column.label }} <span>{{ column.tasks.length }}</span></h3>
            <RouterLink v-for="task in column.tasks" :key="task.id" :to="'/tasks/' + task.id" class="task-card">
              <strong>{{ task.title }}</strong>
              <span v-if="projectNames.has(task.projectId)">{{ projectNames.get(task.projectId) }}</span>
              <small>{{ task.assigneeName }} <time v-if="task.dueDate" :datetime="task.dueDate">截止 {{ task.dueDate }}</time></small>
            </RouterLink>
            <p v-if="!column.tasks.length" class="no-tasks">{{ data ? '暂无任务' : '—' }}</p>
          </section>
        </div>
      </section>
      <div class="dashboard-grid">
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
    </div>
  </section>
</template>

<style scoped>
.dashboard a { text-decoration: none; }
.dashboard { max-width: 1680px; margin: 0 auto; }
.dashboard-bar { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 16px; }
.dashboard-bar h1 { font-size: clamp(28px, 4vw, 42px); font-weight: 650; margin: 0 0 4px; }
.dashboard-bar p, .dashboard-panel header p { margin: 0; font-size: 12px; }
.dashboard-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
.dashboard-panel { min-height: 246px; background: white; border: 1px solid #e4e8ef; border-radius: 16px; overflow: hidden; }
.dashboard-panel header { display: flex; align-items: center; gap: 10px; padding: 14px 16px; border-bottom: 1px solid #edf0f4; }
.dashboard-panel h2 { margin: 0 0 3px; font-size: 14px; font-weight: 650; }
.dashboard-panel header a { margin-left: auto; font-size: 12px; white-space: nowrap; }
.panel-icon { display: grid; place-items: center; width: 32px; height: 32px; border-radius: 7px; font-size: 17px; }
.blue { background: #eaf1ff; }
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
.dashboard-body { display: grid; gap: 16px; }
.summary { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; margin: 0; }
.summary > div { background: white; border: 1px solid #e1e2e6; border-radius: 16px; padding: 16px; }
.summary dt { font-size: 13px; }
.summary dd { margin: 8px 0 0; font-size: 30px; font-weight: 650; font-variant-numeric: tabular-nums; }
.board { background: #fafafa; border: 1px solid #e1e2e6; border-radius: 20px; padding: 16px; }
.board-header { display: flex; justify-content: space-between; align-items: center; gap: 12px; margin-bottom: 14px; }
.board-header h2 { margin: 0; font-size: 18px; }
.board-header a { border-radius: 999px; border: 1px solid #e1e2e6; background: white; padding: 7px 12px; font-size: 12px; }
.board-columns { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; }
.board-column { background: #ededf0; border-radius: 14px; min-height: 220px; min-width: 0; padding: 10px; }
.board-column h3 { display: flex; align-items: center; gap: 7px; margin: 0 0 10px; font-size: 13px; }
.board-column h3::before { content: ''; width: 7px; height: 7px; border-radius: 50%; background: #696f7a; }
[data-status="DOING"] h3::before { background: #316ac8; }
[data-status="REVIEW"] h3::before { background: #976209; }
.board-column h3 span { margin-left: auto; border-radius: 20px; background: white; padding: 2px 7px; }
.task-card { display: grid; gap: 6px; background: white; border: 1px solid transparent; border-radius: 11px; padding: 12px; margin-bottom: 8px; font-size: 13px; overflow-wrap: anywhere; }
.task-card:hover { border-color: #a3aec2; }
.task-card small { display: flex; justify-content: space-between; flex-wrap: wrap; gap: 4px 10px; }
.no-tasks { font-size: 12px; text-align: center; }
@media (max-width: 640px) {
  .summary { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .board-columns { grid-template-columns: minmax(0, 1fr); }
  .board-column { min-height: 84px; }
}
</style>
