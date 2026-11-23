<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { get, post, put } from '../api/http'
import { projectApi } from '../api/client'
import type { Project, ProjectMember } from '../api/types'
import { useSessionStore } from '../stores/session'

interface Workflow { approverId: number | null; assignmentReminder: boolean; statusReminder: boolean; dueReminder: boolean }
interface Baseline { id: number; name: string; createdAt: string }
interface Snapshot { taskId: number; title: string; startDate: string | null; dueDate: string | null }
interface Capacity { accountId: number; taskCount: number; loggedMinutes: number; capacityMinutes: number; plannedMinutes: number }
const route = useRoute()
const session = useSessionStore()
const projectId = computed(() => Number(route.params.projectId))
const base = computed(() => '/api/projects/' + projectId.value)
const workflow = ref<Workflow>()
const members = ref<ProjectMember[]>([])
const baselines = ref<Baseline[]>([])
const snapshot = ref<Snapshot[]>([])
const selected = ref<number>()
const name = ref('')
const capacity = ref<Capacity[]>()
const dates = ref<[string, string] | null>(null)
const busy = ref(false)
const role = ref<Project['role']>()
const canManage = computed(() => role.value === 'OWNER' || role.value === 'ADMIN')
function person(id: number) { return members.value.find(m => m.accountId === id)?.displayName ?? session.members.find(m => m.id === id)?.displayName ?? '#' + id }
function report(error: unknown) { ElMessage.error(error instanceof Error ? error.message : '请求失败') }
async function load() {
  const path = base.value
  workflow.value = undefined
  role.value = undefined
  members.value = []
  baselines.value = []
  selected.value = undefined
  snapshot.value = []
  capacity.value = undefined
  const results = await Promise.allSettled([get<Workflow>(path + '/workflow'), projectApi.members(projectId.value), get<Baseline[]>(path + '/baselines'), projectApi.list()])
  if (path !== base.value) return
  const [rules, roster, saved, projects] = results
  if (rules.status === 'fulfilled') workflow.value = rules.value
  if (roster.status === 'fulfilled') members.value = roster.value
  if (saved.status === 'fulfilled') baselines.value = saved.value
  if (projects.status === 'fulfilled') role.value = projects.value.find(project => project.id === projectId.value)?.role
  results.forEach(result => { if (result.status === 'rejected') report(result.reason) })
}
async function act(action: (path: string) => Promise<void>) {
  if (busy.value) return
  busy.value = true
  try { await action(base.value) } catch (error) { report(error) } finally { busy.value = false }
}
function saveWorkflow() {
  if (!workflow.value) return
  const body = { ...workflow.value }
  return act(async path => {
    await put(path + '/workflow', body)
    if (path === base.value) ElMessage.success('配置已保存')
  })
}
function createBaseline() {
  if (!name.value.trim()) return
  const body = { name: name.value.trim() }
  return act(async path => {
    const created = await post<Baseline>(path + '/baselines', body)
    if (path !== base.value) return
    baselines.value.unshift(created)
    name.value = ''
    selected.value = created.id
    await readBaseline(created.id)
  })
}
async function readBaseline(id: number) {
  const path = base.value
  snapshot.value = []
  try {
    const rows = await get<Snapshot[]>(path + '/baselines/' + id)
    if (path === base.value && id === selected.value) snapshot.value = rows
  } catch (error) { report(error) }
}
function readCapacity() {
  if (!dates.value?.length) return
  const [from, to] = dates.value
  return act(async path => {
    const query = new URLSearchParams({ from, to })
    const rows = await get<Capacity[]>(path + '/capacity?' + query)
    if (path === base.value && dates.value?.[0] === from && dates.value[1] === to) capacity.value = rows
  })
}
watch(projectId, load, { immediate: true })
watch(dates, () => { capacity.value = undefined })
</script>

<template>
  <section class="project-operations">
    <router-link :to="{name:'project', params:{projectId}}">返回项目</router-link>
    <h2>审批、工时与基线</h2>
    <el-card>
      <template #header>审批与通知自动化</template>
      <el-form v-if="workflow" label-position="top" :disabled="busy || !canManage">
        <el-form-item label="验收审批人">
          <el-select v-model="workflow.approverId" clearable :value-on-clear="null" placeholder="不启用审批">
            <el-option v-for="member in members.filter(m => m.role !== 'VIEWER')" :key="member.accountId" :value="member.accountId" :label="member.displayName" />
          </el-select>
        </el-form-item>
        <el-form-item label="站内提醒">
          <el-checkbox v-model="workflow.assignmentReminder">任务分派</el-checkbox>
          <el-checkbox v-model="workflow.statusReminder">状态变化</el-checkbox>
          <el-checkbox v-model="workflow.dueReminder">到期提醒</el-checkbox>
        </el-form-item>
        <el-button v-if="canManage" type="primary" @click="saveWorkflow">保存配置</el-button>
      </el-form>
    </el-card>
    <el-card>
      <template #header>成员容量</template>
      <p>可用时长按每个工作日 480 分钟估算；任务数只统计有排期且与所选日期重叠的任务；计划时长汇总这些任务的完整估算；实际时长来自所选日期内的工时记录。</p>
      <el-form inline @submit.prevent="readCapacity">
        <el-form-item label="统计日期">
          <el-date-picker v-model="dates" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" />
        </el-form-item>
        <el-form-item><el-button type="primary" native-type="submit" :disabled="busy || !dates">查询</el-button></el-form-item>
      </el-form>
      <el-table v-if="capacity" :data="capacity" empty-text="暂无成员容量数据">
        <el-table-column label="成员"><template #default="{row}">{{ person(row.accountId) }}</template></el-table-column>
        <el-table-column label="任务数" prop="taskCount" />
        <el-table-column label="可用分钟" prop="capacityMinutes" />
        <el-table-column label="计划分钟" prop="plannedMinutes" />
        <el-table-column label="实际分钟" prop="loggedMinutes" />
      </el-table>
    </el-card>
    <el-card>
      <template #header>排期基线</template>
      <el-form v-if="canManage" inline @submit.prevent="createBaseline">
        <el-form-item label="基线名称"><el-input v-model="name" maxlength="120" /></el-form-item>
        <el-form-item><el-button type="primary" native-type="submit" :disabled="busy || !name.trim()">保存当前排期</el-button></el-form-item>
      </el-form>
      <el-select v-model="selected" placeholder="选择基线查看任务快照" aria-label="选择基线" @change="readBaseline">
        <el-option v-for="item in baselines" :key="item.id" :value="item.id" :label="item.name + ' · ' + item.createdAt?.replace('T', ' ').slice(0, 16)" />
      </el-select>
      <el-table v-if="selected" :data="snapshot" empty-text="该基线没有任务">
        <el-table-column label="任务" prop="title" />
        <el-table-column label="开始日期" prop="startDate" />
        <el-table-column label="截止日期" prop="dueDate" />
      </el-table>
    </el-card>
  </section>
</template>

<style scoped>
.project-operations { display: grid; gap: 16px; }
.project-operations h2 { margin: 0; }
</style>
