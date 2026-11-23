<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { get, post, request } from '../api/http'
import { getCalendarContext } from '../api/calendarContext'
import { projectApi } from '../api/client'
import type { Stage, Task } from '../api/types'
import { useSessionStore } from '../stores/session'

type Dependency = { predecessorTaskId: number; successorTaskId: number }
type Milestone = { id: number; name: string; endDate: string | null }
type Timeline = { stages: Stage[]; tasks: Task[]; milestones: Milestone[]; dependencies: Dependency[] }

const session = useSessionStore()
const route = useRoute()
const projectId = computed(() => Number(route.params.projectId))
const base = computed(() => '/api/projects/' + projectId.value)
const data = ref<Timeline>()
const writable = ref(false)
const error = ref('')
const busy = ref(false)
const before = ref<number>()
const after = ref<number>()
const today = ref('')
const day = (date: string) => Date.parse(date + 'T00:00:00Z') / 86400000
const dates = computed(() => [today.value, ...(data.value?.tasks.flatMap(t => [t.startDate, t.dueDate]) ?? []),
  ...(data.value?.milestones.map(m => m.endDate) ?? [])].filter((date): date is string => Boolean(date)).sort())
const first = computed(() => dates.value[0] ?? today.value)
const last = computed(() => dates.value.at(-1) ?? today.value)
const width = computed(() => day(last.value) - day(first.value) + 1)
const offset = (date: string) => ((day(date) - day(first.value)) / width.value * 100) + '%'
const size = (start: string, end: string) => ((day(end) - day(start) + 1) / width.value * 100) + '%'
const titles = computed(() => new Map(data.value?.tasks.map(t => [t.id, t.title])))
const tasksByStage = computed(() => {
  const result = new Map<number, Task[]>()
  for (const task of data.value?.tasks ?? []) {
    const tasks = result.get(task.stageId) ?? []
    tasks.push(task)
    result.set(task.stageId, tasks)
  }
  return result
})

async function load() {
  const id = projectId.value
  error.value = ''
  try {
    const [timeline, projects, context] = await Promise.all([get<Timeline>(base.value + '/timeline'), projectApi.list(), getCalendarContext()])
    if (projectId.value !== id) return
    today.value = context.today
    data.value = timeline
    writable.value = session.isCreator && projects.some(p => p.id === id && p.role !== 'VIEWER')
  } catch (cause) {
    if (projectId.value === id) error.value = cause instanceof Error ? cause.message : '加载甘特图失败'
  }
}
watch(projectId, () => {
  data.value = undefined
  writable.value = false
  before.value = after.value = undefined
  void load()
}, { immediate: true })

async function change(edge?: Dependency) {
  if (busy.value || (!edge && (!before.value || !after.value))) return
  const url = base.value + '/dependencies'
  busy.value = true
  try {
    if (edge) await request(url, { method: 'DELETE', body: JSON.stringify(edge) })
    else {
      await post(url, { predecessorTaskId: before.value, successorTaskId: after.value })
      before.value = after.value = undefined
    }
    await load()
  } catch (cause) {
    ElMessage.error(cause instanceof Error ? cause.message : '保存依赖失败')
  } finally { busy.value = false }
}
</script>

<template>
  <section class="timeline">
    <RouterLink :to="{ name: 'project', params: { projectId } }">返回项目</RouterLink> ·
    <RouterLink :to="{ name: 'project-planning', params: { projectId } }">周期、模块与里程碑</RouterLink>
    <h1>项目甘特图</h1>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <p v-else-if="!data" role="status">正在加载…</p>
    <template v-if="data">
      <div class="chart-scroll">
        <div class="chart">
          <div class="row"><strong>阶段与任务</strong><div class="axis"><span>{{ first }}</span><span>今天 {{ today }}</span><span>{{ last }}</span></div></div>
          <template v-for="stage in data.stages" :key="stage.id">
            <div class="stage">{{ stage.name }}</div>
            <div v-for="task in tasksByStage.get(stage.id)" :key="task.id" class="row">
              <RouterLink :to="{ name: 'task', params: { taskId: task.id } }">{{ task.title }}</RouterLink>
              <div class="track">
                <i class="today" :style="{ left: offset(today) }" />
                <span v-if="task.startDate || task.dueDate" class="bar"
                  :aria-label="(task.startDate || task.dueDate) + ' 至 ' + (task.dueDate || task.startDate)"
                  :title="(task.startDate || task.dueDate) + ' 至 ' + (task.dueDate || task.startDate)"
                  :style="{ left: offset((task.startDate || task.dueDate)!), width: size((task.startDate || task.dueDate)!, (task.dueDate || task.startDate)!) }" />
                <small v-else>未排期（点击任务编辑）</small>
              </div>
            </div>
          </template>
          <div v-for="item in data.milestones" :key="item.id" class="row">
            <span>里程碑 · {{ item.name }} · {{ item.endDate }}</span>
            <div class="track"><i class="today" :style="{ left: offset(today) }" />
              <span v-if="item.endDate" class="milestone" :style="{ left: offset(item.endDate) }">◆</span>
            </div>
          </div>
        </div>
      </div>
      <h2>前置依赖</h2>
      <p v-if="!data.dependencies.length">暂无依赖</p>
      <ul v-else>
        <li v-for="edge in data.dependencies" :key="edge.predecessorTaskId + ':' + edge.successorTaskId">
          {{ titles.get(edge.predecessorTaskId) }} 完成后 → {{ titles.get(edge.successorTaskId) }} 开始
          <el-button v-if="writable" link type="danger" :disabled="busy" @click="change(edge)">移除</el-button>
        </li>
      </ul>
      <el-form v-if="writable" inline @submit.prevent="change()">
        <el-form-item label="前置任务"><el-select v-model="before" filterable style="width: 180px">
          <el-option v-for="task in data.tasks" :key="task.id" :value="task.id" :label="task.title" />
        </el-select></el-form-item>
        <el-form-item label="后续任务"><el-select v-model="after" filterable style="width: 180px">
          <el-option v-for="task in data.tasks" :key="task.id" :value="task.id" :label="task.title" :disabled="task.id === before" />
        </el-select></el-form-item>
        <el-button native-type="submit" type="primary" :loading="busy" :disabled="!before || !after || before === after">添加依赖</el-button>
      </el-form>
    </template>
  </section>
</template>

<style scoped>
.timeline { padding: 20px; }
.chart-scroll { overflow-x: auto; }
.chart { min-width: 560px; border: 1px solid var(--el-border-color); }
.row { display: grid; align-items: center; grid-template-columns: 220px minmax(0, 1fr); gap: 8px; padding: 4px 16px; border-bottom: 1px solid var(--el-border-color-lighter); }
.axis { display: flex; justify-content: space-between; gap: 10px; font-size: 12px; }
.stage { padding: 6px 16px; font-weight: bold; background: var(--el-fill-color-light); }
.track { position: relative; min-height: 26px; background: var(--el-fill-color-lighter); }
.track small { position: relative; padding: 8px; }
.bar { position: absolute; top: 6px; height: 14px; min-width: 3px; border-radius: 4px; background: var(--el-color-primary); }
.today { position: absolute; inset-block: 0; border-left: 1px solid var(--el-color-danger); z-index: 1; }
.milestone { position: absolute; color: var(--el-color-warning-dark-2); }
</style>
