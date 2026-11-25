<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { projectApi, taskApi } from '../../api/client'
import { ApiError } from '../../api/http'
import type { Stage, Task } from '../../api/types'
import { useSessionStore } from '../../stores/session'
import { STATUS_OPTIONS, statusLabel, statusType } from '../../utils/status'

import { PRIORITY_LABELS } from '../../utils/priority'
import type { TaskPriority, TaskStatus } from '../../api/types'

import { getCalendarContext, isOverdue } from '../../api/calendarContext'
import { useTaskFilters } from '../../utils/taskFilters'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const projectId = computed(() => Number(route.params.projectId))
const projectName = ref('')
const project = ref<{visibility?: 'SHARED'|'PRIVATE'; role?: string}>({})
const sharedViews = ref<{id:number;name:string;scope:'PERSONAL'|'SHARED';configJson:string}[]>([])
const projectFields = ref<{id:number;name:string;type:'TEXT'|'SINGLE_SELECT'|'DATE';required:boolean;visible:boolean;sortOrder:number}[]>([])
const selectedServerView = ref<number | undefined>()
const newFieldType = ref<'TEXT'|'SINGLE_SELECT'|'DATE'>('TEXT')
const memberToAdd = ref<number>()
const projectMembers = ref<{accountId:number;displayName:string;role:'OWNER'|'ADMIN'|'MEMBER'|'VIEWER'}[]>([])
const settingsOpen = ref(false)
const settingsTab = ref('stages')
const draggedTask = ref<Task>()
const moving = reactive(new Set<number>())
const defaultViewKey = computed(() => filterStorageKey.value + ':default')
const defaultView = ref<number>()

function openSettings(tab: string) {
  settingsTab.value = tab
  settingsOpen.value = true
}

function setDefaultView() {
  if (!selectedServerView.value) return
  localStorage.setItem(defaultViewKey.value, String(selectedServerView.value))
  defaultView.value = selectedServerView.value
  ElMessage.success('已设为此浏览器的默认视图')
}

async function moveTask(task: Task, target: number | TaskStatus) {
  if (moving.has(task.id) || (typeof target === 'number' ? task.stageId === target : task.status === target)) return
  moving.add(task.id)
  const id = projectId.value
  try {
    const updated = typeof target === 'number'
      ? await taskApi.update(task.id, { stageId: target })
      : await taskApi.correctStatus(task.id, target)
    if (id === projectId.value) tasks.value = tasks.value.map(item => item.id === updated.id ? { ...item, ...updated } : item)
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '无法移动任务')
  } finally {
    moving.delete(task.id)
  }
}

function dropTask(event: DragEvent, target: number | TaskStatus) {
  if (!draggedTask.value) return
  event.preventDefault()
  void moveTask(draggedTask.value, target)
  draggedTask.value = undefined
}

const newTaskStatus = ref<TaskStatus>('TODO')
const creatingTask = ref(false)
const stages = ref<Stage[]>([])
const tasks = ref<Task[]>([])
const overdueOnly = ref(false)
const mode = ref<'list' | 'board' | 'stage'>('board')
const stageDialog = ref(false)
const taskDialog = ref(false)
const stageName = ref('')
const taskForm = reactive({
  title: '',
  priority: 'NONE' as TaskPriority,
  startDate: '',
  dueDate: '',
  description: '',
  assigneeId: undefined as number | undefined,
  stageId: undefined as number | undefined,
  assigneeInput: '' as string,
})

const filterStorageKey = computed(() => 'team-work:task-views:' + session.account?.id + ':' + projectId.value)
const { filters } =
  useTaskFilters(filterStorageKey)
const filteredTasks = computed(() => {
  const search = filters.search.trim().toLowerCase()
  return tasks.value.filter((task) =>
    (!search || [task.title, task.assigneeName, task.description].some((text) => text?.toLowerCase().includes(search))) &&
    (!filters.status || task.status === filters.status) &&
    (!filters.priority || task.priority === filters.priority),
  ).filter((task) => !overdueOnly.value || isOverdue(task, teamToday.value))
})
const statusCounts = computed(() => STATUS_OPTIONS.map((status) => ({
  ...status,
  count: tasks.value.filter((task) => task.status === status.value).length,
})))
const overdueCount = computed(() => tasks.value.filter((task) => isOverdue(task, teamToday.value)).length)
function drillStatus(status: string) {
  overdueOnly.value = false
  filters.status = filters.status === status ? '' : status
  filters.search = ''
  filters.priority = ''
  mode.value = 'list'
}

function drillOverdue() {
  overdueOnly.value = !overdueOnly.value
  filters.status = ''
  filters.search = ''
  filters.priority = ''
  mode.value = 'list'
}
const boardColumns = computed(() => mode.value === 'stage'
  ? stages.value.map((stage) => ({
    value: stage.id,
    label: stage.name,
    tasks: filteredTasks.value.filter((task) => task.stageId === stage.id),
  }))
  : STATUS_OPTIONS.map((status) => ({
    ...status,
    tasks: filteredTasks.value.filter((task) => task.status === status.value),
  })))


function rememberFromTasks() {
  for (const task of tasks.value) {
    session.rememberPerson({ id: task.assigneeId, displayName: task.assigneeName })
  }
}

async function load() {
  try {
    const id = projectId.value
    const [projects, nextStages, nextTasks] = await Promise.all([projectApi.list(), projectApi.stages(id), taskApi.list(id)])
    if (id !== projectId.value) return
    project.value = projects.find((item) => item.id === id) ?? {}
    projectName.value = projects.find((item) => item.id === id)?.name ?? ''
    try { sharedViews.value = await projectApi.views(id) } catch { sharedViews.value = [] }
    try { projectFields.value = await projectApi.fields(id) } catch { projectFields.value = [] }
    try { projectMembers.value = await projectApi.members(id) } catch { projectMembers.value = [] }
    if (id !== projectId.value) return
    defaultView.value = Number(localStorage.getItem(defaultViewKey.value)) || undefined
    selectedServerView.value = sharedViews.value.find(view => view.id === defaultView.value)?.id
    mode.value = 'board'
    if (selectedServerView.value) applyServerView(selectedServerView.value)
    stages.value = nextStages
    tasks.value = nextTasks
    rememberFromTasks()
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '无法加载项目')
  }
}

async function setVisibility(value: 'SHARED'|'PRIVATE') { try { await projectApi.setVisibility(projectId.value, value); project.value = {...project.value, visibility:value}; ElMessage.success('项目可见范围已更新') } catch(e) { ElMessage.error(e instanceof ApiError ? e.message : '无法更新可见范围') } }
async function createSavedView() { try { const {value} = await ElMessageBox.prompt('视图名称','保存当前视图'); const scope=await ElMessageBox.prompt('输入 PERSONAL 或 SHARED','视图范围',{inputValue:'PERSONAL',inputValidator:v=>['PERSONAL','SHARED'].includes(v.toUpperCase())||'请输入 PERSONAL 或 SHARED'}); const view=await projectApi.saveView(projectId.value,{name:value.trim(),scope:scope.value.toUpperCase() as 'PERSONAL'|'SHARED',configJson:JSON.stringify({...filters,mode:mode.value})}); sharedViews.value.push(view); selectedServerView.value = view.id; ElMessage.success('视图已保存') } catch(e) { if(e !== 'cancel' && e !== 'close') ElMessage.error(e instanceof ApiError ? e.message : '保存视图失败') } }
function applyServerView(id:number) { const view=sharedViews.value.find(v=>v.id===id); if(!view) return; try { const cfg=JSON.parse(view.configJson); Object.assign(filters,{search:cfg.search||'',status:cfg.status||'',priority:cfg.priority||''}); if(['list','board','stage'].includes(cfg.mode)) mode.value=cfg.mode } catch { ElMessage.error('视图配置无效') } }
async function deleteServerView() { const view=sharedViews.value.find(v=>v.id===selectedServerView.value); if(!view) return; try { await ElMessageBox.confirm(`删除视图“${view.name}”？`,'删除视图'); await projectApi.removeView(projectId.value,view.id); sharedViews.value=sharedViews.value.filter(v=>v.id!==view.id); selectedServerView.value=undefined; if (defaultView.value === view.id) { localStorage.removeItem(defaultViewKey.value); defaultView.value = undefined }; ElMessage.success('视图已删除') } catch(e) { if(e!=='cancel'&&e!=='close') ElMessage.error(e instanceof ApiError?e.message:'删除视图失败') } }
async function addProjectField() { try { const {value}=await ElMessageBox.prompt('字段名称','添加项目字段'); const field=await projectApi.addField(projectId.value,{name:value.trim(),type:newFieldType.value}); projectFields.value.push(field); ElMessage.success('字段已添加') } catch(e) { if(e !== 'cancel' && e !== 'close') ElMessage.error(e instanceof ApiError ? e.message : '无法添加字段') } }
async function changeMemberRole(accountId:number, role:'OWNER'|'ADMIN'|'MEMBER'|'VIEWER') { try { await projectApi.setMemberRole(projectId.value,accountId,role); projectMembers.value=await projectApi.members(projectId.value); ElMessage.success('成员角色已更新') } catch(e) { ElMessage.error(e instanceof ApiError?e.message:'无法更新成员角色') } }
async function addMember() { if(!memberToAdd.value)return; try { await projectApi.setMemberRole(projectId.value,memberToAdd.value,'MEMBER');projectMembers.value=await projectApi.members(projectId.value);memberToAdd.value=undefined;ElMessage.success('成员已加入项目') } catch(e) { ElMessage.error(e instanceof ApiError?e.message:'无法添加成员') } }

async function addStage() {
  try {
    await projectApi.addStage(projectId.value, stageName.value)
    stageDialog.value = false
    stageName.value = ''
    await load()
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '无法添加阶段')
  }
}

async function renameStage(stage: Stage) {
  try {
    await projectApi.updateStage(stage.id, { name: stage.name })
    ElMessage.success('阶段已更新')
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '无法更新阶段')
  }
}

async function moveStage(stage: Stage, delta: number) {
  try {
    await projectApi.updateStage(stage.id, { sortOrder: stage.sortOrder + delta })
    await load()
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '无法调整顺序')
  }
}

async function removeStage(stage: Stage) {
  try {
    await projectApi.deleteStage(stage.id)
    await load()
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '无法删除阶段')
  }
}

function openTaskDialog(stageId = stages.value[0]?.id, status: TaskStatus = 'TODO') {
  newTaskStatus.value = status
  taskForm.title = ''
  taskForm.priority = 'NONE'
  taskForm.startDate = ''
  taskForm.dueDate = ''
  taskForm.description = ''
  taskForm.assigneeId = session.account?.id
  taskForm.stageId = stageId
  taskForm.assigneeInput = ''
  taskDialog.value = true
}

async function createTask() {
  if (creatingTask.value) return
  let assigneeId = taskForm.assigneeId
  if (!session.membersFromApi && taskForm.assigneeInput) {
    assigneeId = Number(taskForm.assigneeInput)
  }
  if (!assigneeId || !taskForm.stageId) {
    ElMessage.error('请指定负责人和阶段')
    return
  }
  creatingTask.value = true
  try {
    const created = await taskApi.create(projectId.value, {
      title: taskForm.title,
      priority: taskForm.priority,
      startDate: taskForm.startDate,
      dueDate: taskForm.dueDate,
      description: taskForm.description || undefined,
      assigneeId,
      stageId: taskForm.stageId,
    })
    taskDialog.value = false
    await moveTask(created, newTaskStatus.value)
    await router.push(`/tasks/${created.id}`)
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '无法创建任务')
  } finally {
    creatingTask.value = false
  }
}

watch(projectId, load)
const teamToday = ref<string>()
onMounted(() => {
  void load()
  void getCalendarContext().then(context => { teamToday.value = context.today }).catch(() => { teamToday.value = undefined })
})
const deleting = ref(false)

async function removeProject() {
  if (deleting.value) return
  deleting.value = true
  try {
    await ElMessageBox.confirm('删除项目将同时删除所有阶段、任务及其评论和通知，且无法恢复。', '确认删除项目', {
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await projectApi.remove(projectId.value)
    ElMessage.success('项目已删除')
    await router.replace('/projects')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '删除失败')
    }
  } finally {
    deleting.value = false
  }
}
</script>

<template>
  <div class="v2-page">
    <header class="project-header">
      <div class="project-title">
        <router-link to="/projects">← 返回</router-link>
        <h1>{{ projectName || '项目详情' }}</h1>
        <el-tag v-if="project.role" size="small">{{ project.role }}</el-tag>
        <el-tag size="small" :type="project.visibility === 'PRIVATE' ? 'info' : 'success'">{{ project.visibility === 'PRIVATE' ? '私人项目' : '团队共享' }}</el-tag>
      </div>
      <div class="actions">
        <button v-if="projectMembers.length" class="avatar-stack" type="button" aria-label="查看项目成员与角色" @click="openSettings('members')">
          <span v-for="member in projectMembers.slice(0, 4)" :key="member.accountId" class="avatar" :title="member.displayName">{{ member.displayName.slice(0, 2) }}</span>
          <span v-if="projectMembers.length > 4" class="avatar">+{{ projectMembers.length - 4 }}</span>
        </button>
        <el-button @click="openSettings('stages')">⚙ 项目设置</el-button>
        <el-button v-if="session.isCreator" type="primary" @click="openTaskDialog()">＋ 新建任务</el-button>
      </div>
    </header>

    <nav class="project-views" aria-label="项目视图">
      <button v-for="view in [{ key: 'board', label: '状态看板' }, { key: 'list', label: '列表' }, { key: 'stage', label: '阶段看板' }] as const"
        :key="view.key" type="button" :aria-pressed="mode === view.key" @click="mode = view.key">{{ view.label }}</button>
      <router-link :to="{ name: 'project-timeline', params: { projectId } }">时间线与里程碑</router-link>
      <router-link :to="{ name: 'project-pages', params: { projectId } }">文档与统计</router-link>
      <router-link :to="{ name: 'project-operations', params: { projectId } }">工时与审批</router-link>
    </nav>

    <section v-if="mode === 'list'" class="review-panel" aria-label="项目概况">
      <div class="project-overview">
      <button v-for="item in statusCounts" :key="item.value" type="button" class="overview-card"
        :class="{ active: filters.status === item.value && !overdueOnly }"
        :aria-pressed="filters.status === item.value && !overdueOnly" @click="drillStatus(item.value)">
        <span>{{ item.label }}</span><strong>{{ item.count }}</strong>
      </button>
      <button type="button" class="overview-card" :class="{ active: overdueOnly }" :aria-pressed="overdueOnly" @click="drillOverdue">
        <span>逾期任务</span><strong>{{ overdueCount }}</strong>
      </button>
      </div>
    </section>

    <div class="task-filters">
      <el-input v-model="filters.search" aria-label="搜索任务" placeholder="搜索任务、说明或负责人" clearable />
      <el-select v-model="filters.status" aria-label="筛选状态" placeholder="所有状态" clearable>
        <el-option v-for="item in STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-select v-model="filters.priority" aria-label="筛选优先级" placeholder="所有优先级" clearable>
        <el-option v-for="(label, value) in PRIORITY_LABELS" :key="value" :label="label" :value="value" />
      </el-select>
      <el-select v-model="selectedServerView" aria-label="保存的视图" placeholder="保存的视图" @change="applyServerView">
        <el-option v-for="view in sharedViews" :key="view.id" :label="`${view.name} · ${view.scope === 'SHARED' ? '共享' : '个人'}`" :value="view.id" />
      </el-select>
      <el-button @click="createSavedView">保存当前视图</el-button>
      <el-button :disabled="!selectedServerView || selectedServerView === defaultView" @click="setDefaultView">设为默认</el-button>
      <el-button :disabled="!selectedServerView" @click="deleteServerView">移除所选视图</el-button>
      <el-button v-if="overdueOnly" @click="overdueOnly = false">清除逾期筛选</el-button>
    </div>
    <el-drawer v-model="settingsOpen" title="项目设置" size="min(540px, 100vw)">
      <el-tabs v-model="settingsTab">
        <el-tab-pane label="项目阶段" name="stages">
          <el-button v-if="session.isCreator" @click="stageDialog = true">添加阶段</el-button>
          <ul v-else><li v-for="stage in stages" :key="stage.id">{{ stage.name }}</li></ul>
    <div v-if="session.isCreator" class="stages v2-card" style="padding: 12px; margin-bottom: 16px">
      <div v-for="stage in stages" :key="stage.id" class="stage-row">
        <el-input v-model="stage.name" size="small" @change="renameStage(stage)" />
        <el-button size="small" @click="moveStage(stage, -1)">上移</el-button>
        <el-button size="small" @click="moveStage(stage, 1)">下移</el-button>
        <el-button size="small" @click="removeStage(stage)">删除空阶段</el-button>
      </div>
    </div>

        </el-tab-pane>
        <el-tab-pane label="自定义字段" name="fields">
    <section class="v2-card" style="padding:12px;margin:12px 0" aria-label="项目自定义字段">
      <strong>项目字段</strong><el-select v-if="project.role === 'OWNER' || project.role === 'ADMIN'" v-model="newFieldType" size="small" style="width:150px;margin-left:8px"><el-option label="文本" value="TEXT"/><el-option label="单选" value="SINGLE_SELECT"/><el-option label="日期" value="DATE"/></el-select><el-button v-if="project.role === 'OWNER' || project.role === 'ADMIN'" link @click="addProjectField">添加字段</el-button>
      <el-tag v-for="field in projectFields" :key="field.id" style="margin:6px">{{ field.name }} · {{ field.type }}</el-tag>
    </section>
        </el-tab-pane>
        <el-tab-pane label="成员与角色" name="members">
    <section class="v2-card" style="padding:12px;margin:12px 0" aria-label="项目成员">
      <strong>项目成员与角色</strong>
      <el-select v-if="project.role === 'OWNER'" v-model="memberToAdd" filterable placeholder="添加团队成员" size="small" style="margin:8px"><el-option v-for="p in session.members.filter(x=>!projectMembers.some(m=>m.accountId===x.id))" :key="p.id" :label="p.displayName" :value="p.id"/></el-select><el-button v-if="project.role === 'OWNER'" size="small" :disabled="!memberToAdd" @click="addMember">添加</el-button>
      <div v-for="member in projectMembers" :key="member.accountId" style="display:flex;align-items:center;gap:10px;margin-top:8px">
        <span style="min-width:140px">{{ member.displayName }}</span>
        <el-select :model-value="member.role" size="small" style="width:150px" :disabled="project.role !== 'OWNER' || member.role === 'OWNER'" @change="changeMemberRole(member.accountId,$event)">
          <el-option label="管理员" value="ADMIN"/><el-option label="成员" value="MEMBER"/><el-option label="只读" value="VIEWER"/>
        </el-select>
      </div>
    </section>

        </el-tab-pane>
        <el-tab-pane label="常规" name="general">
          <div class="settings-controls">
            <label v-if="project.role === 'OWNER'">
              项目可见范围
              <el-select :model-value="project.visibility || 'SHARED'" @change="setVisibility">
                <el-option label="团队共享" value="SHARED" />
                <el-option label="仅自己可见" value="PRIVATE" />
              </el-select>
            </label>
            <router-link :to="{ name: 'project-planning', params: { projectId } }">周期与模块</router-link>
            <router-link :to="{ name: 'project-operations', params: { projectId } }">计划基线与运营</router-link>
            <el-button v-if="session.isCreator" type="danger" plain :loading="deleting" @click="removeProject">删除项目</el-button>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-drawer>

    <div v-if="mode === 'list'" class="v2-table-panel">
      <el-table :data="filteredTasks" empty-text="没有匹配的任务">
        <el-table-column prop="title" label="标题" min-width="140" />
        <el-table-column label="优先级" width="100">
        <template #default="{ row }">{{ PRIORITY_LABELS[row.priority as TaskPriority] }}</template>
      </el-table-column>
      <el-table-column prop="dueDate" label="截止日期" width="155">
        <template #default="{ row }">
          {{ row.dueDate || '—' }}
          <el-tag v-if="isOverdue(row, teamToday)" size="small" type="danger">逾期</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="assigneeName" label="负责人" width="140" />
        <el-table-column label="阶段" width="140">
          <template #default="{ row }">
            {{ stages.find((item) => item.id === row.stageId)?.name || row.stageId }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column width="90">
          <template #default="{ row }">
            <el-button link type="primary" @click="router.push(`/tasks/${row.id}`)">打开</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div v-else class="board" aria-label="任务看板">
      <section v-for="column in boardColumns" :key="column.value" class="col" :data-status="column.value"
        @dragover="draggedTask && $event.preventDefault()" @drop="dropTask($event, column.value)">
        <div class="col-title">
          <span>{{ column.label }}</span><span class="count">{{ column.tasks.length }}</span>
          <el-button v-if="session.isCreator" link :aria-label="'在' + column.label + '新建任务'"
            @click="typeof column.value === 'number' ? openTaskDialog(column.value) : openTaskDialog(undefined, column.value)">＋</el-button>
        </div>
        <article v-for="task in column.tasks" :key="task.id" class="task-card"
          :draggable="session.isCreator" @dragstart="draggedTask = task; $event.dataTransfer?.setData('text/plain', String(task.id))" @dragend="draggedTask = undefined">
          <div class="task-top">
            <span>#{{ task.id }}</span>
            <el-tag size="small" :type="task.priority === 'URGENT' ? 'danger' : statusType(task.status)">
              {{ task.priority === 'URGENT' ? '紧急' : statusLabel(task.status) }}
            </el-tag>
          </div>
          <router-link class="task-title" :to="`/tasks/${task.id}`">{{ task.title }}</router-link>
          <p v-if="task.description" class="task-description">{{ task.description }}</p>
          <div class="meta">
            <span class="assignee"><span class="avatar" aria-hidden="true">{{ task.assigneeName?.slice(0, 2) || '?' }}</span>{{ task.assigneeName }}</span>
            <span :class="{ overdue: isOverdue(task, teamToday) }">{{ isOverdue(task, teamToday) ? '逾期 · ' : '' }}{{ task.dueDate || '未设置截止日期' }}</span>
          </div>
          <div class="task-top">
            <span>{{ PRIORITY_LABELS[task.priority] }}</span>
            <div v-if="session.isCreator" class="task-actions">
              <el-button v-if="task.status !== 'DONE'" link size="small" :disabled="moving.has(task.id)" @click="moveTask(task, 'DONE')">✓ 完成</el-button>
              <el-dropdown trigger="click" @command="moveTask(task, $event)">
                <el-button link size="small" :disabled="moving.has(task.id)">移动</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item v-for="target in boardColumns" :key="target.value" :command="target.value"
                      :disabled="target.value === column.value">{{ target.label }}</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </div>
        </article>
        <p v-if="!column.tasks.length" class="column-empty">暂无任务</p>
      </section>
      <p v-if="!boardColumns.length" class="column-empty">暂无阶段</p>
    </div>

    <el-dialog v-model="stageDialog" append-to-body title="添加阶段" width="360px">
      <el-input v-model="stageName" placeholder="阶段名称" />
      <template #footer>
        <el-button @click="stageDialog = false">取消</el-button>
        <el-button type="primary" @click="addStage">添加</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="taskDialog" title="新建任务" width="440px">
      <el-form label-position="top">
        <el-form-item label="标题">
          <el-input v-model="taskForm.title" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="taskForm.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="阶段">
          <el-select v-model="taskForm.stageId" style="width: 100%">
            <el-option v-for="stage in stages" :key="stage.id" :label="stage.name" :value="stage.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="taskForm.priority">
            <el-option v-for="(label, value) in PRIORITY_LABELS" :key="value" :value="value" :label="label" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始日期"><el-input v-model="taskForm.startDate" type="date" /></el-form-item>
        <el-form-item label="截止日期">
          <el-input v-model="taskForm.dueDate" type="date" />
        </el-form-item>
        <el-form-item label="负责人">
          <el-select
            v-if="session.membersFromApi || session.members.length"
            v-model="taskForm.assigneeId"
            style="width: 100%"
          >
            <el-option
              v-for="person in session.members"
              :key="person.id"
              :label="`${person.displayName} (#${person.id})`"
              :value="person.id"
            />
          </el-select>
          <el-input
            v-if="!session.membersFromApi"
            v-model="taskForm.assigneeInput"
            placeholder="或填写已知用户 ID"
            style="margin-top: 8px"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="taskDialog = false">取消</el-button>
        <el-button type="primary" :loading="creatingTask" @click="createTask">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.v2-page { max-width: none; min-width: 0; color: #17233a; }
.project-header, .project-title, .actions, .project-views, .project-overview {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}
.project-header { justify-content: space-between; padding: 8px 0 14px; }
.project-title h1 { margin: 0; font-size: 24px; overflow-wrap: anywhere; }
.project-title a { border-radius: 7px; background: #eef2f8; color: #445573; padding: 5px 9px; text-decoration: none; }
.avatar-stack { display: flex; background: none; border: 0; cursor: pointer; padding: 0 4px; }
.avatar {
  display: inline-grid;
  place-items: center;
  flex: none;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: #514bd7;
  color: #fff;
  font-size: 11px;
}
.avatar-stack .avatar { width: 34px; height: 34px; border: 2px solid white; margin-left: -6px; }
.avatar-stack .avatar:nth-child(even) { background: #087f79; }
.project-views { border-bottom: 1px solid #dfe6f1; gap: 6px; }
.project-views button, .project-views a {
  padding: 11px 9px;
  border: 0;
  border-bottom: 2px solid transparent;
  background: transparent;
  font: inherit;
  color: #445573;
  text-decoration: none;
  cursor: pointer;
}
.project-views button[aria-pressed="true"] { border-bottom-color: #514bd7; color: #4039ce; font-weight: 700; }
.review-panel { margin: 14px 0 10px; }
.overview-card {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 5px 10px;
  border: 1px solid #dfe6f1;
  border-radius: 8px;
  background: #fff;
  color: #435777;
  cursor: pointer;
}
.overview-card.active { border-color: #514bd7; color: #4039ce; background: #eef0ff; }
.task-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding-bottom: 14px;
  border-bottom: 1px solid #dfe6f1;
}
.task-filters > .el-select { width: 150px; }
.task-filters > .el-input { width: min(100%, 250px); }
.actions :deep(.el-button), .task-filters :deep(.el-button) { margin-left: 0; }
.board {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  overflow-x: auto;
  padding: 18px 2px 16px;
}
.col {
  flex: 1 0 235px;
  min-width: 0;
  min-height: 112px;
  padding: 11px;
  border: 1px solid #dfe6f1;
  border-radius: 15px;
  background: #f0f3f9;
  --status-color: #7788a3;
}
.col[data-status="DOING"] { --status-color: #347af0; }
.col[data-status="REVIEW"] { --status-color: #bd7200; }
.col[data-status="DONE"] { --status-color: #13805b; }
.col-title, .task-top, .meta, .assignee, .task-actions {
  display: flex;
  align-items: center;
  gap: 7px;
}
.col-title { margin-bottom: 10px; font-size: 14px; font-weight: 700; }
.col-title::before { content: ''; width: 8px; height: 8px; border-radius: 50%; background: var(--status-color); }
.col-title .el-button { margin-left: auto; }
.count { background: #e1e7f3; border-radius: 10px; padding: 1px 7px; }
.task-card {
  padding: 12px;
  margin-bottom: 9px;
  border: 1px solid #dfe6f1;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 1px 2px #2433500d;
}
.task-card:hover { border-color: #aaa7ee; }
.task-card[draggable="true"] { cursor: grab; }
.task-top, .meta { justify-content: space-between; flex-wrap: wrap; color: #526583; font-size: 12px; }
.task-title { display: inline-block; margin-top: 7px; color: #17233a; font-weight: 700; overflow-wrap: anywhere; text-decoration: none; }
.task-description { margin: 6px 0 0; color: #526583; font-size: 13px; overflow: hidden; display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 2; overflow-wrap: anywhere; }
.meta { border-top: 1px solid #edf0f6; margin-top: 10px; padding: 8px 0; }
.overdue { color: #b42639; font-weight: 700; }
.column-empty { color: #526583; font-size: 13px; }
.stage-row, .settings-controls { display: flex; gap: 8px; margin-top: 10px; }
.stage-row { flex-wrap: wrap; }
.settings-controls { flex-direction: column; align-items: flex-start; }
.v2-table-panel { overflow-x: auto; }
</style>
