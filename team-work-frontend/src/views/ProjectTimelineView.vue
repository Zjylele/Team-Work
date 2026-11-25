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
const rows = computed(() => data.value?.stages.flatMap(stage =>
  (data.value?.tasks ?? []).filter(task => task.stageId === stage.id).map(task => ({ stage, task }))) ?? [])
const scales = [{ label: '日', days: 1 }, { label: '周', days: 3 }, { label: '月', days: 30 }]
const scale = ref(3)
const short = (date: string) => date.slice(5).replace('-', '/')
const dateAt = (index: number) => new Date((day(first.value) + index) * 86400000).toISOString().slice(0, 10)
const ticks = computed(() => {
  const step = Math.max(scale.value, Math.ceil(width.value / 120))
  return Array.from({ length: Math.ceil(width.value / step) }, (_, index) => {
    const start = index * step
    const end = Math.min(start + step - 1, width.value - 1)
    return { start, span: (end - start + 1) / width.value * 100, label: start === end ? short(dateAt(start)) : short(dateAt(start)) + ' – ' + short(dateAt(end)) }
  })
})
const status: Record<string, string> = { TODO: '待开始', DOING: '进行中', REVIEW: '待验收', DONE: '已完成' }
const dependencyForm = ref<HTMLFormElement>()
const connections = computed(() => {
  const indexById = new Map(rows.value.map(({ task }, index) => [task.id, { task, index }]))
  return (data.value?.dependencies ?? []).flatMap(edge => {
    const from = indexById.get(edge.predecessorTaskId)
    const to = indexById.get(edge.successorTaskId)
    const end = from?.task.dueDate || from?.task.startDate
    const start = to?.task.startDate || to?.task.dueDate
    if (!from || !to || !end || !start) return []
    const x1 = (day(end) - day(first.value) + 1) / width.value * 1000
    const x2 = (day(start) - day(first.value)) / width.value * 1000
    const y1 = from.index * 76 + 38
    const y2 = to.index * 76 + 38
    const elbow = Math.min(990, x1 + 18)
    const approach = Math.max(0, x2 - 10)
    return [{ key: from.task.id + ':' + to.task.id, path: `M ${x1} ${y1} H ${elbow} V ${(y1 + y2) / 2} H ${approach} V ${y2} H ${x2}` }]
  })
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
  <section class="timeline" aria-label="时间线与里程碑">
    <header class="toolbar">
      <nav aria-label="项目规划导航">
        <RouterLink class="back-button" :to="{ name: 'project', params: { projectId } }">返回</RouterLink>
        <span aria-hidden="true">·</span>
        <RouterLink :to="{ name: 'project-planning', params: { projectId } }">周期、模块与里程碑</RouterLink>
      </nav>
      <div v-if="data" class="actions">
        <div class="scales" role="group" aria-label="时间刻度">
          <button v-for="item in scales" :key="item.days" type="button" :aria-pressed="scale === item.days"
            @click="scale = item.days">{{ item.label }}</button>
        </div>
        <button v-if="writable" type="button" class="shortcut" @click="dependencyForm?.scrollIntoView({ block: 'center' }); dependencyForm?.querySelector('input')?.focus()">＋ 添加依赖</button>
      </div>
    </header>

    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <p v-else-if="!data" role="status">正在加载…</p>
    <template v-if="data">
      <section class="card chart-card" aria-label="甘特图">
        <div class="chart-scroll">
          <div class="chart" :style="{ minWidth: Math.max(760, ticks.length * 80 + 310) + 'px' }">
            <div class="chart-head">
              <span>任务名称与周期</span><span>负责人 / 状态</span>
              <div class="axis">
                <span v-for="tick in ticks" :key="tick.start" :style="{ width: tick.span + '%' }">{{ tick.label }}</span>
              </div>
            </div>
            <p v-if="!rows.length" class="empty">暂无任务</p>
            <div v-else class="chart-body">
              <div class="task-column">
                <div v-for="({ stage, task }, index) in rows" :key="task.id" class="task-row">
                  <span class="index" :data-status="task.status">{{ String(index + 1).padStart(2, '0') }}</span>
                  <div class="task-info">
                    <RouterLink :to="{ name: 'task', params: { taskId: task.id } }">{{ task.title }}</RouterLink>
                    <small>{{ stage.name }}</small>
                  </div>
                  <div class="owner">{{ task.assigneeName || '未分派' }}<small>{{ status[task.status] }}</small></div>
                </div>
              </div>
              <div class="tracks">
                <div class="gridlines" aria-hidden="true"><i v-for="tick in ticks" :key="tick.start" :style="{ width: tick.span + '%' }" /></div>
                <i class="today" :style="{ left: offset(today) }"><span>今天 {{ short(today) }}</span></i>
                <svg class="connections" aria-hidden="true" :viewBox="'0 0 1000 ' + rows.length * 76" preserveAspectRatio="none">
                  <path v-for="edge in connections" :key="edge.key" :d="edge.path" vector-effect="non-scaling-stroke" />
                </svg>
                <div v-for="{ task } in rows" :key="task.id" class="track">
                  <span v-if="task.startDate || task.dueDate" class="bar" :data-status="task.status"
                    :title="(task.startDate || task.dueDate) + ' 至 ' + (task.dueDate || task.startDate)"
                    :style="{ left: offset((task.startDate || task.dueDate)!), width: size((task.startDate || task.dueDate)!, (task.dueDate || task.startDate)!) }">
                    {{ short((task.startDate || task.dueDate)!) }} – {{ short((task.dueDate || task.startDate)!) }}
                  </span>
                  <small v-else class="unscheduled">未排期</small>
                </div>
              </div>
            </div>
          </div>
        </div>
        <footer class="legend">
          <span v-for="key in ['DONE', 'DOING', 'REVIEW', 'TODO']" :key="key"><i :data-status="key" />{{ status[key] }}</span>
          <span><i class="dashed" />前置依赖</span>
        </footer>
      </section>

      <div class="panels">
        <section class="card panel">
          <header><h2><span aria-hidden="true">⚑</span> 关键里程碑</h2><small>{{ data.milestones.length }} 个节点</small></header>
          <p v-if="!data.milestones.length" class="empty">暂无里程碑</p>
          <ul v-else>
            <li v-for="(item, index) in data.milestones" :key="item.id" class="milestone">
              <span class="milestone-index">M{{ index + 1 }}</span>
              <strong>{{ item.name }}</strong><time v-if="item.endDate" :datetime="item.endDate">{{ item.endDate }}</time>
            </li>
          </ul>
          <RouterLink class="panel-link" :to="{ name: 'project-planning', params: { projectId } }">查看项目规划 →</RouterLink>
        </section>
        <section class="card panel">
          <header><h2><span aria-hidden="true">↳</span> 前置依赖</h2><small>{{ data.dependencies.length }} 条规则</small></header>
          <form v-if="writable" ref="dependencyForm" class="dependency-form" @submit.prevent="change()">
            <label>前置任务
              <el-select v-model="before" aria-label="前置任务" filterable>
                <el-option v-for="task in data.tasks" :key="task.id" :value="task.id" :label="task.title" />
              </el-select>
            </label>
            <label>后续任务
              <el-select v-model="after" aria-label="后续任务" filterable>
                <el-option v-for="task in data.tasks" :key="task.id" :value="task.id" :label="task.title" :disabled="task.id === before" />
              </el-select>
            </label>
            <el-button native-type="submit" :loading="busy" :disabled="!before || !after || before === after">确认添加依赖</el-button>
          </form>
          <p v-if="!data.dependencies.length" class="empty">暂无依赖</p>
          <ul v-else>
            <li v-for="edge in data.dependencies" :key="edge.predecessorTaskId + ':' + edge.successorTaskId" class="dependency">
              <span>{{ titles.get(edge.predecessorTaskId) }} <b>→</b> {{ titles.get(edge.successorTaskId) }}</span>
              <el-button v-if="writable" link type="danger" :disabled="busy" @click="change(edge)">移除</el-button>
            </li>
          </ul>
        </section>
      </div>
    </template>
  </section>
</template>

<style scoped>
.timeline { --accent: #4648d4; --muted: var(--el-text-color-secondary); min-width: 0; padding-block: 8px 28px; }
.toolbar, .toolbar nav, .actions, .legend, .panel header { display: flex; align-items: center; gap: 12px; }
.toolbar, .panel header { justify-content: space-between; }
.toolbar { flex-wrap: wrap; margin-bottom: 28px; }
.toolbar nav { gap: 5px; font-weight: 600; }
.toolbar a:not(.back-button) { color: inherit; text-decoration: none; }
.scales { display: flex; gap: 3px; padding: 4px; border-radius: 9px; background: var(--el-fill-color-light); }
.scales button, .shortcut { font: inherit; color: inherit; border: 0; border-radius: 7px; cursor: pointer; }
.scales button { padding: 5px 9px; background: transparent; }
.scales [aria-pressed="true"], .shortcut { background: var(--el-bg-color); box-shadow: 0 1px 3px #24244b15; }
.scales [aria-pressed="true"] { color: var(--accent); }
.shortcut { padding: 9px 14px; font-weight: 600; }
.card { background: var(--el-bg-color); border: 1px solid var(--el-border-color-lighter); border-radius: 15px; box-shadow: 0 1px 3px #20204008; }
.chart-card { overflow: hidden; }
.chart-scroll { overflow-x: auto; }
.chart-head, .chart-body { display: grid; grid-template-columns: 310px minmax(0, 1fr); }
.chart-head { grid-template-columns: 198px 112px minmax(0, 1fr); align-items: center; min-height: 54px; background: var(--el-fill-color-light); font-size: 12px; }
.chart-head > span { padding-left: 16px; }
.axis, .gridlines { display: flex; }
.axis span { text-align: center; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.task-row { display: grid; grid-template-columns: 25px minmax(0, 1fr) 96px; align-items: center; gap: 9px; height: 76px; padding-inline: 16px 8px; }
.task-row:nth-child(even), .track:nth-of-type(even) { background: var(--el-fill-color-extra-light); }
.index, .milestone-index { display: grid; place-items: center; border-radius: 7px; background: var(--status-bg, #e7e8fc); color: var(--status-fg, #34369e); font-size: 12px; font-weight: 650; min-height: 25px; }
.task-info, .owner { min-width: 0; }
.task-info a, .task-info small, .owner { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.task-info a { font-weight: 700; color: inherit; text-decoration: none; }
.task-info small, .owner small { display: block; margin-top: 3px; color: var(--muted); font-size: 12px; }
.owner { font-size: 12px; }
.tracks, .track { position: relative; }
.track { height: 76px; }
.gridlines, .connections { position: absolute; inset: 0; width: 100%; height: 100%; pointer-events: none; }
.gridlines i { border-inline-start: 1px solid var(--el-border-color-lighter); }
.connections { z-index: 1; fill: none; stroke: #a5a4c1; stroke-width: 1.5; stroke-dasharray: 4 3; }
.today { position: absolute; z-index: 3; inset-block: 0; border-left: 1px solid #a09dff; pointer-events: none; }
.today span { display: block; transform: translate(-50%, -50%); border-radius: 20px; padding: 2px 7px; white-space: nowrap; font: normal 11px/1.5 inherit; font-style: normal; font-size: 11px; background: var(--accent); color: white; }
[data-status="DONE"] { --status-bg: #d8e9ee; --status-fg: #00628d; }
[data-status="DOING"] { --status-bg: #4648d4; --status-fg: #fff; }
[data-status="REVIEW"] { --status-bg: #e9e3ff; --status-fg: #5536a3; }
[data-status="TODO"] { --status-bg: #e5e7e9; --status-fg: #46566a; }
.bar { position: absolute; z-index: 2; top: 23px; height: 30px; min-width: 3px; line-height: 30px; border-radius: 8px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; text-indent: 8px; font-size: 12px; font-weight: 600; background: var(--status-bg, #e5e7e9); color: var(--status-fg, #46566a); }
.unscheduled { position: absolute; top: 26px; left: 12px; color: var(--muted); }
.legend { flex-wrap: wrap; padding: 10px 16px; font-size: 12px; }
.legend span { display: inline-flex; align-items: center; gap: 6px; }
.legend i { width: 10px; height: 10px; border-radius: 4px; background: var(--status-bg); }
.legend .dashed { width: 18px; height: 0; border-top: 1px dashed #a5a4c1; border-radius: 0; }
.panels { display: grid; grid-template-columns: minmax(0, 1.42fr) minmax(0, 1fr); gap: 22px; margin-top: 24px; }
.panel { display: flex; flex-direction: column; padding: 22px; min-width: 0; }
.panel header { margin-bottom: 16px; }
.panel h2 { margin: 0; font-size: 19px; }
.panel h2 span, .panel-link, .dependency b { color: var(--accent); }
.panel small, .panel time, .empty { color: var(--muted); }
.panel ul { margin: 0; padding: 0; list-style: none; }
.milestone, .dependency { display: flex; align-items: center; gap: 12px; padding: 12px; overflow-wrap: anywhere; }
.milestone { margin-bottom: 8px; border-radius: 9px; background: var(--el-fill-color-light); }
.milestone-index { flex: 0 0 32px; height: 32px; border-radius: 50%; }
.milestone time, .dependency button { margin-left: auto; white-space: nowrap; }
.panel-link { align-self: flex-end; margin-top: auto; padding-top: 14px; font-size: 13px; }
.dependency-form { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; background: var(--el-fill-color-light); padding: 13px; border-radius: 9px; margin-bottom: 8px; }
.dependency-form label { display: grid; gap: 4px; font-size: 12px; }
.dependency-form button { grid-column: 1 / -1; }
.empty { padding: 12px 16px; }
@media (max-width: 900px) { .panels { grid-template-columns: minmax(0, 1fr); } }
</style>
