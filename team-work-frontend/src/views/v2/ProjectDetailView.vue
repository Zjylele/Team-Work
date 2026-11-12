<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { projectApi, taskApi } from '../../api/client'
import { ApiError } from '../../api/http'
import type { Stage, Task } from '../../api/types'
import { useSessionStore } from '../../stores/session'
import { statusLabel, statusType } from '../../utils/status'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const projectId = computed(() => Number(route.params.projectId))
const stages = ref<Stage[]>([])
const tasks = ref<Task[]>([])
const mode = ref<'list' | 'board'>('list')
const stageDialog = ref(false)
const taskDialog = ref(false)
const stageName = ref('')
const taskForm = reactive({
  title: '',
  description: '',
  assigneeId: undefined as number | undefined,
  stageId: undefined as number | undefined,
  assigneeInput: '' as string,
})

const tasksByStage = computed(() => {
  const map = new Map<number, Task[]>()
  for (const stage of stages.value) {
    map.set(stage.id, [])
  }
  for (const task of tasks.value) {
    const list = map.get(task.stageId) || []
    list.push(task)
    map.set(task.stageId, list)
  }
  return map
})

function rememberFromTasks() {
  for (const task of tasks.value) {
    session.rememberPerson({ id: task.assigneeId, displayName: task.assigneeName })
  }
}

async function load() {
  try {
    stages.value = await projectApi.stages(projectId.value)
    tasks.value = await taskApi.list(projectId.value)
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

function openTaskDialog() {
  taskForm.title = ''
  taskForm.description = ''
  taskForm.assigneeId = session.account?.id
  taskForm.stageId = stages.value[0]?.id
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
</script>

<template>
  <div class="v2-page">
    <div class="v2-link-back">
      <el-button link @click="router.push('/projects')">返回项目</el-button>
    </div>
    <header class="v2-page-header">
      <div>
        <h1 class="v2-title">项目详情</h1>
      </div>
      <div class="actions">
        <el-radio-group v-model="mode" size="small">
          <el-radio-button value="list">列表</el-radio-button>
          <el-radio-button value="board">看板</el-radio-button>
        </el-radio-group>
        <el-button v-if="session.isCreator" @click="stageDialog = true">添加阶段</el-button>
        <el-button v-if="session.isCreator" type="primary" @click="openTaskDialog">新建任务</el-button>
      </div>
    </header>

    <div v-if="session.isCreator" class="stages v2-card" style="padding: 12px; margin-bottom: 16px">
      <div v-for="stage in stages" :key="stage.id" class="stage-row">
        <el-input v-model="stage.name" size="small" @change="renameStage(stage)" />
        <el-button size="small" @click="moveStage(stage, -1)">上移</el-button>
        <el-button size="small" @click="moveStage(stage, 1)">下移</el-button>
        <el-button size="small" @click="removeStage(stage)">删除空阶段</el-button>
      </div>
    </div>

    <div v-if="mode === 'list'" class="v2-table-panel">
      <el-table :data="tasks" empty-text="还没有任务">
        <el-table-column prop="title" label="标题" min-width="140" />
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

    <div v-else class="board">
      <div v-for="stage in stages" :key="stage.id" class="col v2-card">
        <div class="col-title">{{ stage.name }}</div>
        <button
          v-for="task in tasksByStage.get(stage.id) || []"
          :key="task.id"
          type="button"
          class="task-card"
          @click="router.push(`/tasks/${task.id}`)"
        >
          <div>{{ task.title }}</div>
          <div class="meta">
            <span>{{ task.assigneeName }}</span>
            <el-tag :type="statusType(task.status)" size="small">{{ statusLabel(task.status) }}</el-tag>
          </div>
        </button>
      </div>
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
</style>
