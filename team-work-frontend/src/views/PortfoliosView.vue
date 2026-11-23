<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { del, get, post, put } from '../api/http'
import { getCalendarContext, isOverdue, type CalendarContext } from '../api/calendarContext'
import { projectApi } from '../api/client'
import type { Project, Task } from '../api/types'
import { useSessionStore } from '../stores/session'

type Portfolio = { id: number; name: string; projectIds: number[] }
type Load = { accountId: number; accountName: string; total: number; overdue: number; dueThisWeek: number; upcoming: number; withoutDueDate: number }
type Summary = { total: number; completed: number; overdue: number; completedThisWeek: number; memberLoads: Load[] }
type Metric = 'all' | 'completed' | 'overdue' | 'completedThisWeek' | 'memberLoad'

const buckets = { overdue: '逾期', dueThisWeek: '本周待到期', upcoming: '之后到期', withoutDueDate: '无截止日期' } as const

const session = useSessionStore()
const base = '/api/portfolios'
const items = ref<Portfolio[]>([])
const projects = ref<Project[]>([])
const id = ref<number>()
const selected = computed(() => items.value.find(item => item.id === id.value))
const summary = ref<Summary>()
const calendar = ref<CalendarContext>()
const rows = ref<(Task & { projectName: string })[]>()
const heading = ref('')
const error = ref('')
const drillError = ref('')
const loading = ref(true)
const drillLoading = ref(false)
const dialog = ref(false)
const busy = ref(false)
const editing = ref<number>()
const form = ref({ name: '', projectIds: [] as number[] })
let drillVersion = 0

async function load() {
  loading.value = true
  error.value = ''
  try {
    ;[items.value, projects.value] = await Promise.all([get<Portfolio[]>(base), projectApi.list()])
    if (!items.value.some(item => item.id === id.value)) id.value = items.value[0]?.id
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '加载项目集失败'
  } finally { loading.value = false }
}
onMounted(load)

watch(id, async (current, _, onCleanup) => {
  let active = true
  onCleanup(() => { active = false })
  summary.value = undefined
  calendar.value = undefined
  rows.value = undefined
  heading.value = ''
  error.value = ''
  drillError.value = ''
  drillLoading.value = false
  drillVersion++
  if (!current) return
  try {
    const [value, context] = await Promise.all([
      get<Summary>(base + '/' + current + '/summary'),
      getCalendarContext(),
    ])
    if (active) {
      summary.value = value
      calendar.value = context
    }
  } catch (cause) {
    if (active) error.value = cause instanceof Error ? cause.message : '加载统计失败'
  }
})

async function drill(metric: Metric, label: string, accountId?: number, bucket?: keyof typeof buckets) {
  const version = ++drillVersion
  heading.value = label
  rows.value = undefined
  drillError.value = ''
  drillLoading.value = true
  const query = new URLSearchParams({ metric })
  if (accountId !== undefined) query.set('accountId', String(accountId))
  if (bucket) query.set('bucket', bucket)
  try {
    const result = await get<(Task & { projectName: string })[]>(base + '/' + id.value + '/tasks?' + query)
    if (version === drillVersion) rows.value = result
  } catch (cause) {
    if (version === drillVersion) drillError.value = cause instanceof Error ? cause.message : '加载任务失败'
  } finally { if (version === drillVersion) drillLoading.value = false }
}

function open(item?: Portfolio) {
  editing.value = item?.id
  form.value = { name: item?.name ?? '', projectIds: [...(item?.projectIds ?? [])] }
  dialog.value = true
}

async function save() {
  if (busy.value || !form.value.name.trim()) return
  busy.value = true
  try {
    const payload = { ...form.value, name: form.value.name.trim() }
    const saved = editing.value ? await put<Portfolio>(base + '/' + editing.value, payload) : await post<Portfolio>(base, payload)
    dialog.value = false
    id.value = undefined
    await load()
    if (saved?.id) id.value = saved.id
  } catch (cause) {
    ElMessage.error(cause instanceof Error ? cause.message : '保存项目集失败')
  } finally { busy.value = false }
}
async function remove(item: Portfolio) {
  if (busy.value) return
  try {
    await ElMessageBox.confirm('确定删除“' + item.name + '”？项目及任务会保留。', '删除项目集', {
      type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消',
    })
  } catch { return }
  busy.value = true
  try {
    await del(base + '/' + item.id)
    await load()
  } catch (cause) {
    ElMessage.error(cause instanceof Error ? cause.message : '删除项目集失败')
  } finally { busy.value = false }
}
</script>

<template>
  <section class="portfolios" v-loading="loading">
    <h1>项目集</h1>
    <div class="toolbar">
      <el-select v-model="id" aria-label="选择项目集" placeholder="选择项目集" style="width: 240px">
        <el-option v-for="item in items" :key="item.id" :label="item.name" :value="item.id" />
      </el-select>
      <el-button v-if="session.isCreator" type="primary" @click="open()">新建项目集</el-button>
      <el-button v-if="session.isCreator && selected" @click="open(selected)">编辑项目集</el-button>
      <el-button v-if="session.isCreator && selected" type="danger" plain :disabled="busy" @click="remove(selected)">删除项目集</el-button>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-empty v-else-if="!loading && !items.length" description="暂无项目集" />
    <p v-else-if="id && !summary" role="status">正在加载统计…</p>
    <template v-if="summary">
      <div class="metrics">
        <el-card shadow="never">
          完成率：{{ summary.total ? (summary.completed / summary.total * 100).toFixed(1) + '%' : '暂无任务' }}
          <div><el-button link type="primary" @click="drill('completed', '已完成任务')">{{ summary.completed }} 已完成</el-button>
          <el-button link type="primary" @click="drill('all', '全部任务')">{{ summary.total }} 全部</el-button></div>
        </el-card>
        <el-card shadow="never" :title="calendar ? '团队日期 ' + calendar.today + ' (' + calendar.zoneId + ')' : undefined">逾期未完成：
          <el-button link type="primary" @click="drill('overdue', '逾期未完成任务')">{{ summary.overdue }}</el-button>
        </el-card>
        <el-card shadow="never">本周完成量：
          <el-button link type="primary" @click="drill('completedThisWeek', '本周完成任务')">{{ summary.completedThisWeek }}</el-button>
        </el-card>
      </div>
      <h2>成员负载</h2>
      <el-table :data="summary.memberLoads" empty-text="暂无未完成任务">
        <el-table-column prop="accountName" label="成员" />
        <el-table-column label="未完成">
          <template #default="{ row }"><el-button link type="primary" @click="drill('memberLoad', row.accountName + '的未完成任务', row.accountId)">{{ row.total }}</el-button></template>
        </el-table-column>
        <el-table-column v-for="(label, bucket) in buckets" :key="bucket" :label="label">
          <template #default="{ row }">
            <el-button link type="primary" @click="drill('memberLoad', row.accountName + ' · ' + label, row.accountId, bucket)">{{ row[bucket] }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <h2 v-if="heading">{{ heading }}</h2>
      <p v-if="drillLoading" role="status">正在加载任务…</p>
      <el-alert v-if="drillError" :title="drillError" type="error" :closable="false" />
      <el-table v-if="rows" :data="rows" empty-text="暂无符合条件的任务">
        <el-table-column prop="projectName" label="项目" />
        <el-table-column label="任务">
          <template #default="{ row }"><RouterLink :to="{ name: 'task', params: { taskId: row.id } }">{{ row.title }}</RouterLink></template>
        </el-table-column>
        <el-table-column prop="assigneeName" label="负责人" />
        <el-table-column prop="dueDate" label="截止日期">
          <template #default="{ row }">
            {{ row.dueDate || '—' }}
            <el-tag v-if="isOverdue(row, calendar?.today)" size="small" type="danger">逾期</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </template>

    <el-dialog v-model="dialog" :title="editing ? '编辑项目集' : '新建项目集'" width="min(92vw, 540px)">
      <el-form label-position="top" @submit.prevent="save">
        <el-form-item label="名称" required><el-input v-model="form.name" maxlength="80" /></el-form-item>
        <el-form-item label="项目">
          <el-select v-model="form.projectIds" multiple filterable placeholder="选择项目">
            <el-option v-for="project in projects" :key="project.id" :value="project.id" :label="project.name" />
          </el-select>
        </el-form-item>
        <el-button type="primary" native-type="submit" :loading="busy" :disabled="!form.name.trim()">保存</el-button>
      </el-form>
    </el-dialog>
  </section>
</template>

<style scoped>
.portfolios { padding: 20px; }
.toolbar { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 16px; }
.metrics { display: grid; grid-template-columns: repeat(auto-fit, minmax(190px, 1fr)); gap: 12px; }
</style>
