<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { taskApi } from '../api/client'
import { del, get, post, put } from '../api/http'
import { useSessionStore } from '../stores/session'

type Kind = 'CYCLE' | 'MODULE'
type Form = {
  kind: Kind
  name: string
  description: string
  startDate: string | null
  endDate: string | null
}
type Entry = { plan: Form & { id: number }; taskIds: number[] }

const route = useRoute()
const session = useSessionStore()
const projectId = computed(() => Number(route.params.projectId))
const endpoint = computed(() => `/api/projects/${projectId.value}/planning`)
const entries = ref<Entry[]>([])
const tasks = ref<Awaited<ReturnType<typeof taskApi.list>>>([])
function completedCount(entry: Entry) {
  return entry.taskIds.filter(id => taskById.value.get(id)?.status === 'DONE').length
}

const taskById = computed(() => new Map(tasks.value.map(task => [task.id, task])))
const kind = ref<Kind>('CYCLE')
const shown = computed(() => entries.value.filter(entry => entry.plan.kind === kind.value))
const loading = ref(false)
const error = ref('')
const dialog = ref(false)
const saving = ref(false)
const editingId = ref<number>()
const form = ref<Form>(blank())
const selected = ref<number[]>([])

function blank(): Form {
  return { kind: 'CYCLE', name: '', description: '', startDate: null, endDate: null }
}

async function load() {
  const id = projectId.value
  loading.value = true
  error.value = ''
  try {
    const [nextEntries, nextTasks] = await Promise.all([get<Entry[]>(endpoint.value), taskApi.list(id)])
    if (id === projectId.value) {
      entries.value = nextEntries
      tasks.value = nextTasks
    }
  } catch (cause) {
    if (id === projectId.value) error.value = cause instanceof Error ? cause.message : '读取项目计划失败'
  } finally {
    if (id === projectId.value) loading.value = false
  }
}

function open(entry?: Entry) {
  editingId.value = entry?.plan.id
  form.value = entry ? { ...entry.plan } : { ...blank(), kind: kind.value }
  selected.value = entry ? [...entry.taskIds] : []
  dialog.value = true
}

async function save() {
  if (saving.value || !form.value.name.trim()) return
  saving.value = true
  const payload = {
    ...form.value,
    name: form.value.name.trim(),
    startDate: form.value.startDate || null,
    endDate: form.value.endDate || null,
  }
  try {
    if (editingId.value) {
      await put(`${endpoint.value}/${editingId.value}`, { form: payload, taskIds: selected.value })
    } else {
      await post(endpoint.value, payload)
    }
    dialog.value = false
    await load()
  } catch (cause) {
    ElMessage.error(cause instanceof Error ? cause.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function remove(entry: Entry) {
  try {
    await ElMessageBox.confirm(`确定删除“${entry.plan.name}”？关联任务会保留。`, '删除计划', {
      type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消',
    })
    await del(`${endpoint.value}/${entry.plan.id}`)
    await load()
  } catch (cause) {
    if (cause instanceof Error) ElMessage.error(cause.message)
  }
}

watch(projectId, load, { immediate: true })
</script>

<template>
  <section class="planning">
    <RouterLink :to="{ name: 'project', params: { projectId } }">返回项目</RouterLink>
    <header>
      <h1>项目计划</h1>
      <el-button v-if="session.isCreator" type="primary" @click="open()">
        新建{{ kind === 'CYCLE' ? '迭代' : '模块' }}
      </el-button>
    </header>
    <el-tabs v-model="kind">
      <el-tab-pane name="CYCLE" label="迭代" />
      <el-tab-pane name="MODULE" label="模块" />
    </el-tabs>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <div v-else v-loading="loading" class="plans">
      <el-empty v-if="!loading && !shown.length" description="暂无计划" />
      <el-card v-for="entry in shown" :key="entry.plan.id" shadow="never">
        <header>
          <h2>{{ entry.plan.name }}</h2>
          <div v-if="session.isCreator">
            <el-button @click="open(entry)">编辑与分配任务</el-button>
            <el-button type="danger" plain @click="remove(entry)">删除</el-button>
          </div>
        </header>
        <p v-if="entry.plan.description">{{ entry.plan.description }}</p>
        <p v-if="entry.plan.startDate || entry.plan.endDate">
          {{ entry.plan.startDate || '未设开始日期' }} 至 {{ entry.plan.endDate || '未设结束日期' }}
        </p>
        <h3>关联任务（{{ completedCount(entry) }} / {{ entry.taskIds.length }} 已完成）</h3>
        <ul v-if="entry.taskIds.length">
          <li v-for="taskId in entry.taskIds" :key="taskId">
            <RouterLink v-if="taskById.has(taskId)" :to="{ name: 'task', params: { taskId } }">
              {{ taskById.get(taskId)?.title }}
            </RouterLink>
          </li>
        </ul>
      </el-card>
    </div>
    <el-dialog v-model="dialog" :title="editingId ? '编辑计划' : '新建计划'" width="min(92vw, 580px)">
      <el-form label-position="top" @submit.prevent="save">
        <el-form-item label="名称" required><el-input v-model="form.name" maxlength="80" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="form.description" type="textarea" maxlength="1000" /></el-form-item>
        <el-form-item label="开始日期" :required="form.kind === 'CYCLE'">
          <el-date-picker v-model="form.startDate" value-format="YYYY-MM-DD" type="date" />
        </el-form-item>
        <el-form-item label="结束日期" :required="form.kind === 'CYCLE'">
          <el-date-picker v-model="form.endDate" value-format="YYYY-MM-DD" type="date" />
        </el-form-item>
        <el-form-item v-if="editingId" label="关联任务">
          <el-select v-model="selected" multiple filterable placeholder="选择当前项目的任务">
            <el-option v-for="task in tasks" :key="task.id" :label="task.title" :value="task.id" />
          </el-select>
        </el-form-item>
        <el-button type="primary" native-type="submit" :loading="saving" :disabled="!form.name.trim()">保存</el-button>
      </el-form>
    </el-dialog>
  </section>
</template>

<style scoped>
.planning { padding: 20px; }
header { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 12px; }
.plans { display: grid; gap: 16px; min-height: 100px; }
p { white-space: pre-wrap; overflow-wrap: anywhere; }
</style>
