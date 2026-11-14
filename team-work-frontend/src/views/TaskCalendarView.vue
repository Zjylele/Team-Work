<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Calendar, ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import { projectApi, taskApi } from '../api/client'
import type { Task } from '../api/types'
import { useSessionStore } from '../stores/session'

const session = useSessionStore()
const tasks = ref<Task[]>([])
const loading = ref(true)
const failed = ref(false)
const mode = ref<'month' | 'week' | 'day'>('month')
const mine = ref(false)
const today = new Date()
const selected = ref(today)
const labels = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
const key = (date: Date) => [date.getFullYear(), date.getMonth() + 1, date.getDate()]
  .map((part, index) => String(part).padStart(index === 0 ? 4 : 2, '0')).join('-')
const shift = (date: Date, days: number) => new Date(date.getFullYear(), date.getMonth(), date.getDate() + days)
const monday = (date: Date) => shift(date, -((date.getDay() + 6) % 7))
const month = computed(() => new Date(selected.value.getFullYear(), selected.value.getMonth(), 1))
const monthDays = computed(() => {
  const first = monday(month.value)
  return Array.from({ length: 42 }, (_, index) => shift(first, index))
})
const days = computed(() => mode.value === 'month' ? monthDays.value :
  mode.value === 'week' ? Array.from({ length: 7 }, (_, index) => shift(monday(selected.value), index)) : [selected.value])
const title = computed(() => new Intl.DateTimeFormat('zh-CN', {
  year: 'numeric', month: 'long', ...(mode.value === 'month' ? {} : { day: 'numeric' }),
}).format(mode.value === 'week' ? monday(selected.value) : selected.value))
const byDate = computed(() => {
  const dates = new Map<string, Task[]>()
  for (const task of tasks.value) {
    if (!task.dueDate || (mine.value && task.assigneeId !== session.account?.id)) continue
    const group = dates.get(task.dueDate) ?? []
    group.push(task)
    dates.set(task.dueDate, group)
  }
  return dates
})
function move(direction: number) {
  selected.value = mode.value === 'month'
    ? new Date(month.value.getFullYear(), month.value.getMonth() + direction, 1)
    : shift(selected.value, direction * (mode.value === 'week' ? 7 : 1))
}
async function load() {
  loading.value = true
  failed.value = false
  try {
    const projects = await projectApi.list()
    tasks.value = (await Promise.all(projects.map((project) => taskApi.list(project.id)))).flat()
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}
onMounted(load)
</script>

<template>
  <section class="task-calendar">
    <aside class="calendar-side">
      <h2>{{ month.toLocaleDateString('zh-CN', { year: 'numeric', month: 'long' }) }}</h2>
      <div class="mini-grid">
        <span v-for="label in labels" :key="label">{{ label.slice(1) }}</span>
        <button v-for="date in monthDays" :key="key(date)" type="button"
          :class="{ chosen: key(date) === key(selected), outside: date.getMonth() !== selected.getMonth() }"
          :aria-label="key(date)" :aria-pressed="key(date) === key(selected)"
          @click="selected = date">{{ date.getDate() }}</button>
      </div>
      <div class="calendar-scope">
        <button type="button" :aria-pressed="!mine" @click="mine = false">全部任务</button>
        <button type="button" :aria-pressed="mine" @click="mine = true">我的任务</button>
      </div>
    </aside>
    <div class="calendar-main">
      <header class="calendar-header">
        <h1><el-icon><Calendar /></el-icon> 任务日历</h1>
        <el-button @click="selected = today">今天</el-button>
      </header>
      <div class="calendar-tools">
        <el-radio-group v-model="mode" size="small" aria-label="日历视图">
          <el-radio-button value="month">月</el-radio-button>
          <el-radio-button value="week">周</el-radio-button>
          <el-radio-button value="day">日</el-radio-button>
        </el-radio-group>
        <div class="calendar-period">
          <el-button text :icon="ArrowLeft" aria-label="上一时段" @click="move(-1)" />
          <strong>{{ title }}</strong>
          <el-button text :icon="ArrowRight" aria-label="下一时段" @click="move(1)" />
        </div>
      </div>
      <p v-if="loading" class="calendar-feedback" role="status">正在加载任务…</p>
      <p v-else-if="failed" class="calendar-feedback" role="alert">
        任务加载失败 <el-button link @click="load">重试</el-button>
      </p>
      <div v-else class="calendar-scroll">
        <div class="calendar-grid" :class="'is-' + mode">
          <div v-for="label in mode === 'day' ? [] : labels" :key="label" class="calendar-weekday">{{ label }}</div>
          <article v-for="date in days" :key="key(date)" class="calendar-cell"
            :class="{ current: key(date) === key(today), outside: mode === 'month' && date.getMonth() !== selected.getMonth() }">
            <button type="button" class="day-label" :aria-label="'查看 ' + key(date)"
              @click="selected = date; mode = 'day'">{{ mode === 'day' ? key(date) : date.getDate() }}</button>
            <RouterLink v-for="task in byDate.get(key(date))" :key="task.id"
              :to="{ name: 'task', params: { taskId: task.id } }" class="calendar-task"
              :data-status="task.status" :title="task.title">{{ task.title }}</RouterLink>
            <p v-if="mode === 'day' && !byDate.has(key(date))" class="calendar-feedback">当天没有到期任务</p>
          </article>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.task-calendar { display: grid; grid-template-columns: 220px minmax(0, 1fr); background: white; border: 1px solid #e3e8ef; min-height: min(760px, 85vh); }
.calendar-side { padding: 18px 14px; border-right: 1px solid #e3e8ef; }
.calendar-side h2 { text-align: center; font-size: 14px; margin: 0 0 14px; }
.mini-grid { display: grid; grid-template-columns: repeat(7, 1fr); gap: 2px; text-align: center; font-size: 12px; }
.mini-grid button, .day-label, .calendar-scope button { border: 0; background: transparent; cursor: pointer; font: inherit; }
.mini-grid button { aspect-ratio: 1; border-radius: 5px; padding: 0; }
.mini-grid button:hover, .mini-grid .chosen, .calendar-scope button[aria-pressed='true'] { background: #e9f1ff; }
.calendar-scope { display: grid; gap: 4px; border-top: 1px solid #e3e8ef; margin-top: 18px; padding-top: 12px; }
.calendar-scope button { text-align: left; border-radius: 5px; padding: 8px 10px; }
.calendar-main { min-width: 0; }
.calendar-header, .calendar-tools, .calendar-period { display: flex; align-items: center; gap: 12px; }
.calendar-header, .calendar-tools { justify-content: space-between; padding: 10px 16px; border-bottom: 1px solid #e3e8ef; }
.calendar-header h1 { margin: 0; font-size: 17px; display: flex; align-items: center; gap: 8px; }
.calendar-period .el-button + .el-button { margin-left: 0; }
.calendar-scroll { overflow-x: auto; }
.calendar-grid { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); min-width: 525px; }
.calendar-grid.is-day { grid-template-columns: minmax(0, 1fr); min-width: 0; }
.calendar-weekday { text-align: center; padding: 9px 2px; background: #f7f9fc; border-bottom: 1px solid #e3e8ef; font-size: 12px; }
.calendar-cell { min-height: 94px; border-right: 1px solid #e3e8ef; border-bottom: 1px solid #e3e8ef; padding: 6px; min-width: 0; }
.calendar-cell.outside { background: #f7f9fc; }
.calendar-cell.current { background: #f0f6ff; }
.outside .day-label, .mini-grid .outside { opacity: .65; }
.is-week .calendar-cell, .is-day .calendar-cell { min-height: 260px; }
.day-label { display: block; margin-left: auto; padding: 3px 6px; border-radius: 4px; font-size: 12px; }
.day-label:hover { background: #e9f1ff; }
.calendar-task { display: block; font-size: 12px; margin-top: 5px; padding: 4px 6px; border-radius: 3px; border-left: 3px solid #82a7ec; background: #edf3ff; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.calendar-task[data-status='DOING'] { background: #fff3d8; border-color: #dfac4d; }
.calendar-task[data-status='DONE'] { background: #e6f7ee; border-color: #74bd98; }
.calendar-feedback { padding: 14px; font-size: 13px; }
@media (max-width: 800px) {
  .task-calendar { grid-template-columns: minmax(0, 1fr); }
  .calendar-side { border-right: 0; border-bottom: 1px solid #e3e8ef; }
  .mini-grid { max-width: 290px; margin: auto; }
  .calendar-scope { display: flex; }
  .calendar-tools { flex-wrap: wrap; }
}
</style>
