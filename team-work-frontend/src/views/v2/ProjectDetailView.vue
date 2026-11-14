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
import type { TaskPriority } from '../../api/types'

import { useTaskFilters } from '../../utils/taskFilters'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const projectId = computed(() => Number(route.params.projectId))
const projectName = ref('')
const managingStages = ref(false)
const stages = ref<Stage[]>([])
const tasks = ref<Task[]>([])
const mode = ref<'list' | 'board' | 'stage'>('stage')
const stageDialog = ref(false)
const taskDialog = ref(false)
const stageName = ref('')
const taskForm = reactive({
  title: '',
  priority: 'NONE' as TaskPriority,
  dueDate: '',
  description: '',
  assigneeId: undefined as number | undefined,
  stageId: undefined as number | undefined,
  assigneeInput: '' as string,
})

const filterStorageKey = computed(() => 'team-work:task-views:' + session.account?.id + ':' + projectId.value)
const { filters, savedFilters, selectedFilter, hasSelectedFilter, loadFilter, saveFilter, removeFilter } =
  useTaskFilters(filterStorageKey)
const filteredTasks = computed(() => {
  const search = filters.search.trim().toLowerCase()
  return tasks.value.filter((task) =>
    (!search || [task.title, task.assigneeName, task.description].some((text) => text?.toLowerCase().includes(search))) &&
    (!filters.status || task.status === filters.status) &&
    (!filters.priority || task.priority === filters.priority),
  )
})
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
    projectName.value = projects.find((project) => project.id === id)?.name ?? ''
    stages.value = nextStages
    tasks.value = nextTasks
    rememberFromTasks()
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '无法加载项目')
  }
}

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

function openTaskDialog(stageId = stages.value[0]?.id) {
  taskForm.title = ''
  taskForm.priority = 'NONE'
  taskForm.dueDate = ''
  taskForm.description = ''
  taskForm.assigneeId = session.account?.id
  taskForm.stageId = stageId
  taskForm.assigneeInput = ''
  taskDialog.value = true
}

async function createTask() {
  let assigneeId = taskForm.assigneeId
  if (!session.membersFromApi && taskForm.assigneeInput) {
    assigneeId = Number(taskForm.assigneeInput)
  }
  if (!assigneeId || !taskForm.stageId) {
    ElMessage.error('请指定负责人和阶段')
    return
  }
  try {
    const created = await taskApi.create(projectId.value, {
      title: taskForm.title,
      priority: taskForm.priority,
      dueDate: taskForm.dueDate,
      description: taskForm.description || undefined,
      assigneeId,
      stageId: taskForm.stageId,
    })
    taskDialog.value = false
    await router.push(`/tasks/${created.id}`)
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '无法创建任务')
  }
}

watch(projectId, load)
onMounted(load)
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
    <div class="v2-link-back">
      <el-button link @click="router.push('/projects')">返回项目</el-button>
    </div>
    <header class="v2-page-header">
      <div>
        <h1 class="v2-title">{{ projectName || '项目详情' }}</h1>
      </div>
      <div class="actions">
        <el-button @click="router.push({ name: 'project-planning', params: { projectId } })">周期与模块</el-button>
        <el-button @click="router.push({ name: 'project-pages', params: { projectId } })">文档与统计</el-button>
        <el-radio-group v-model="mode" size="small">
          <el-radio-button value="list">列表</el-radio-button>
          <el-radio-button value="board">状态看板</el-radio-button>
          <el-radio-button value="stage">阶段看板</el-radio-button>
        </el-radio-group>
        <el-button v-if="session.isCreator" type="danger" plain :loading="deleting" @click="removeProject">删除项目</el-button>
        <el-button v-if="session.isCreator" @click="stageDialog = true">添加阶段</el-button>
        <el-button v-if="session.isCreator && stages.length" :aria-expanded="managingStages" @click="managingStages = !managingStages">管理阶段</el-button>
        <el-button v-if="session.isCreator" type="primary" @click="openTaskDialog()">新建任务</el-button>
      </div>
    </header>

    <div class="task-filters">
      <el-input v-model="filters.search" aria-label="搜索任务" placeholder="搜索任务、说明或负责人" clearable />
      <el-select v-model="filters.status" aria-label="筛选状态" placeholder="所有状态" clearable>
        <el-option v-for="item in STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-select v-model="filters.priority" aria-label="筛选优先级" placeholder="所有优先级" clearable>
        <el-option v-for="(label, value) in PRIORITY_LABELS" :key="value" :label="label" :value="value" />
      </el-select>
      <el-select v-model="selectedFilter" aria-label="保存的视图" placeholder="保存的视图" @change="loadFilter">
        <el-option v-for="view in savedFilters" :key="view.name" :label="view.name" :value="view.name" />
      </el-select>
      <el-button @click="saveFilter">保存当前视图</el-button>
      <el-button :disabled="!hasSelectedFilter" @click="removeFilter">移除所选视图</el-button>
    </div>

    <div v-if="session.isCreator && managingStages" class="stages v2-card" style="padding: 12px; margin-bottom: 16px">
      <div v-for="stage in stages" :key="stage.id" class="stage-row">
        <el-input v-model="stage.name" size="small" @change="renameStage(stage)" />
        <el-button size="small" @click="moveStage(stage, -1)">上移</el-button>
        <el-button size="small" @click="moveStage(stage, 1)">下移</el-button>
        <el-button size="small" @click="removeStage(stage)">删除空阶段</el-button>
      </div>
    </div>

    <div v-if="mode === 'list'" class="v2-table-panel">
      <el-table :data="filteredTasks" empty-text="没有匹配的任务">
        <el-table-column prop="title" label="标题" min-width="140" />
        <el-table-column label="优先级" width="100">
        <template #default="{ row }">{{ PRIORITY_LABELS[row.priority as TaskPriority] }}</template>
      </el-table-column>
      <el-table-column prop="dueDate" label="截止日期" width="120" />
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
      <div v-for="column in boardColumns" :key="column.value" class="col">
        <div class="col-title"><span>{{ column.label }}</span><span class="count">{{ column.tasks.length }}</span></div>
        <button
          v-for="task in column.tasks"
          :key="task.id"
          type="button"
          class="task-card"
          :data-status="task.status"
          @click="router.push(`/tasks/${task.id}`)"
        >
          <strong>{{ task.title }}</strong>
          <div class="meta">
            <span>{{ task.assigneeName }} · {{ PRIORITY_LABELS[task.priority] }}<template v-if="task.dueDate"> · {{ task.dueDate }}</template></span>
            <el-tag :type="statusType(task.status)" size="small">{{ statusLabel(task.status) }}</el-tag>
          </div>
        </button>
        <el-button
          v-if="mode === 'stage' && session.isCreator"
          text
          class="add-task"
          @click="openTaskDialog(Number(column.value))"
        >＋ 添加任务</el-button>
        <p v-else-if="!column.tasks.length" class="column-empty">暂无任务</p>
      </div>
      <p v-if="!boardColumns.length" class="column-empty">暂无阶段</p>
    </div>

    <el-dialog v-model="stageDialog" title="添加阶段" width="360px">
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
        <el-button type="primary" @click="createTask">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.task-filters {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(155px, 1fr));
  gap: 8px;
  margin-bottom: 16px;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
.stage-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
  max-width: 720px;
}
.board {
  display: flex;
  gap: 12px;
  overflow-x: auto;
  padding-bottom: 12px;
}
.col {
  min-width: 240px;
  padding: 12px;
}
.col-title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
}
.task-card {
  width: 100%;
  text-align: left;
  background: var(--color-muted);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 10px;
  margin-bottom: 8px;
  cursor: pointer;
  color: inherit;
  transition: border-color var(--motion-duration) var(--motion-ease);
}
.task-card:hover {
  border-color: var(--color-primary);
}
.task-card:focus-visible {
  outline: 2px solid var(--color-ring);
  outline-offset: 2px;
}
.meta {
  display: flex;
  justify-content: space-between;
  margin-top: 8px;
  color: var(--color-muted-foreground);
  font-size: 12px;
}

.v2-page { max-width: none; }
.v2-page-header { align-items: center; }
.task-filters {
  background: white;
  padding: 12px;
  margin-bottom: 0;
  border: 1px solid var(--color-border);
  border-radius: 8px 8px 0 0;
}
.board {
  gap: 0;
  min-height: 420px;
  background: #f3f5f8;
  border: 1px solid var(--color-border);
  border-top: 0;
  border-radius: 0 0 8px 8px;
}
.col {
  flex: 1 0 264px;
  min-width: 0;
  max-width: 360px;
  border-right: 1px solid var(--color-border);
}
.col-title { display: flex; align-items: center; justify-content: space-between; padding: 4px 0 12px; border-bottom: 1px solid var(--color-border); }
.count { background: #e5eaf2; border-radius: 10px; padding: 2px 7px; }
.task-card { background: white; border-radius: 6px; padding: 12px; border-left: 3px solid #95a1b4; }
.task-card[data-status='DOING'] { border-left-color: #e5a23d; }
.task-card[data-status='DONE'] { border-left-color: #53ae88; }
.task-card strong { display: block; overflow-wrap: anywhere; }
.meta { gap: 8px; align-items: center; flex-wrap: wrap; }
.meta :deep(.el-tag) { border-radius: 12px; }
.add-task { width: 100%; justify-content: flex-start; }
.column-empty { padding: 10px; font-size: 13px; }
</style>
