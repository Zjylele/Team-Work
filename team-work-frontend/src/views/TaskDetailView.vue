<script setup lang="ts">
import { describeTaskActivity } from '../utils/taskActivity'
import TaskOperations from '../components/TaskOperations.vue'
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { projectApi, taskApi } from '../api/client'
import { ApiError } from '../api/http'
import type { Comment, Stage, Task, TaskStatus, TaskActivity } from '../api/types'
import { useSessionStore } from '../stores/session'
import { STATUS_OPTIONS, formatTime, statusLabel, statusType } from '../utils/status'

import { PRIORITY_LABELS } from '../utils/priority'
import type { TaskPriority } from '../api/types'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const taskId = computed(() => Number(route.params.taskId))
const task = ref<Task | null>(null)
const comments = ref<Comment[]>([])
const activities = ref<TaskActivity[]>([])
const stages = ref<Stage[]>([])
const commentText = ref('')
const description = ref('')
const title = ref('')
const priority = ref<TaskPriority>('NONE')
const startDate = ref('')
const dueDate = ref('')
const assigneeId = ref<number>()
const stageId = ref<number>()
const correctStatus = ref<TaskStatus>('TODO')
const customFields = ref<{id:number;name:string;type:'TEXT'|'SINGLE_SELECT'|'DATE';options:{id:number;label:string}[];required:boolean;visible:boolean}[]>([])
const customValues = ref<Record<string,string>>({})

const isAssignee = computed(() => task.value?.assigneeId === session.account?.id)

let loadVersion = 0
async function load() {
  const id = taskId.value
  const version = ++loadVersion
  try {
    const record = await taskApi.get(id)
    const [nextComments, nextActivities, nextStages, fields, values] = await Promise.all([
      taskApi.comments(id),
      taskApi.activity(id),
      projectApi.stages(record.projectId),
      projectApi.fields(record.projectId).catch(() => []),
      taskApi.fieldValues(id).catch(() => ({})),
    ])
    if (id !== taskId.value || version !== loadVersion) return
    task.value = record
    comments.value = nextComments
    activities.value = nextActivities
    stages.value = nextStages
    customFields.value = fields
    customValues.value = values
    description.value = record.description || ''
    title.value = record.title
    priority.value = record.priority
    startDate.value = record.startDate ?? ''
    dueDate.value = record.dueDate ?? ''
    assigneeId.value = record.assigneeId
    stageId.value = record.stageId
    correctStatus.value = record.status
    session.rememberPerson({ id: record.assigneeId, displayName: record.assigneeName })
    for (const item of nextComments) {
      session.rememberPerson({ id: item.authorId, displayName: item.authorName })
    }
  } catch (error) {
    if (id === taskId.value && version === loadVersion) {
      ElMessage.error(error instanceof ApiError ? error.message : '无法加载任务')
    }
  }
}
async function saveCustomFields() { try { const id = taskId.value; const values = await taskApi.saveFieldValues(id,customValues.value); if (id !== taskId.value) return; customValues.value = values;ElMessage.success('自定义字段已保存') } catch(e) { ElMessage.error(e instanceof ApiError?e.message:'无法保存字段') } }

async function start() {
  try {
    const updated = await taskApi.start(taskId.value)
    if (updated.id !== taskId.value) return
    task.value = updated
    await load()
    ElMessage.success('已开始')
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '无法开始')
  }
}

async function complete() {
  try {
    const completed = await taskApi.complete(taskId.value)
    if (completed.id !== taskId.value) return
    await load()
    ElMessage.success(completed.status === 'REVIEW' ? '已提交验收' : '已完成')
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '无法完成')
  }
}

async function saveDescription() {
  try {
    const updated = await taskApi.update(taskId.value, { description: description.value })
    if (updated.id !== taskId.value) return
    task.value = updated
    ElMessage.success('说明已保存')
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '无法保存说明')
  }
}

async function saveMeta() {
  try {
    const updated = await taskApi.update(taskId.value, {
      title: title.value,
      priority: priority.value,
      startDate: startDate.value,
      dueDate: dueDate.value,
      assigneeId: Number(assigneeId.value),
      stageId: stageId.value,
    })
    if (updated.id !== taskId.value) return
    task.value = updated
    ElMessage.success('任务已更新')
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '无法更新')
  }
}

async function saveStatus() {
  try {
    const updated = await taskApi.correctStatus(taskId.value, correctStatus.value)
    if (updated.id !== taskId.value) return
    task.value = updated
    await load()
    ElMessage.success('状态已纠正')
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '无法改状态')
  }
}

async function addComment() {
  try {
    const id = taskId.value
    const created = await taskApi.addComment(id, commentText.value)
    if (id !== taskId.value) return
    comments.value = [...comments.value, created]
    commentText.value = ''
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '无法发表评论')
  }
}

watch(taskId, () => {
  task.value = null
  commentText.value = ''
  void load()
}, { immediate: true })
const deleting = ref(false)

async function removeTask() {
  if (deleting.value) return
  const id = taskId.value
  deleting.value = true
  try {
    await ElMessageBox.confirm('删除任务将同时删除相关评论和通知，且无法恢复。', '确认删除任务', {
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
    if (id !== taskId.value) return
    await taskApi.remove(id)
    if (id !== taskId.value) return
    ElMessage.success('任务已删除')
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
  <div v-if="task && task.id === taskId">
    <button type="button" class="back-button" @click="router.push(`/projects/${task.projectId}`)">返回</button>
    <div class="head">
      <h1>{{ task.title }}</h1>
      <p>优先级 {{ PRIORITY_LABELS[task.priority] }} · 开始 {{ task.startDate || "未设置" }} · 截止 {{ task.dueDate || "未设置" }}</p>
      <el-tag :type="statusType(task.status)">{{ statusLabel(task.status) }}</el-tag>
    </div>
    <p class="meta">负责人 {{ task.assigneeName }}</p>

    <div class="actions">
      <el-button v-if="isAssignee && task.status === 'TODO'" type="primary" @click="start">开始</el-button>
      <el-button v-if="isAssignee && task.status === 'DOING'" type="success" @click="complete">结束</el-button>
    </div>

    <el-button v-if="session.isCreator" type="danger" plain :loading="deleting" @click="removeTask">删除任务</el-button>
    <el-card v-if="session.isCreator" shadow="never" class="block">
      <h2>创建者维护</h2>
      <el-form label-position="top">
        <el-form-item label="标题">
          <el-input v-model="title" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="priority">
            <el-option v-for="(label, value) in PRIORITY_LABELS" :key="value" :value="value" :label="label" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始日期"><el-input v-model="startDate" type="date" /></el-form-item>
        <el-form-item label="截止日期">
          <el-input v-model="dueDate" type="date" />
        </el-form-item>
        <el-form-item label="负责人">
          <el-select v-model="assigneeId" filterable allow-create default-first-option style="width: 100%">
            <el-option
              v-for="person in session.members"
              :key="person.id"
              :label="`${person.displayName} (#${person.id})`"
              :value="person.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="阶段">
          <el-select v-model="stageId" style="width: 100%">
            <el-option v-for="stage in stages" :key="stage.id" :label="stage.name" :value="stage.id" />
          </el-select>
        </el-form-item>
        <el-button type="primary" @click="saveMeta">保存任务设置</el-button>
        <el-form-item label="纠正状态" style="margin-top: 16px">
          <el-select v-model="correctStatus" style="width: 200px">
            <el-option v-for="item in STATUS_OPTIONS.filter(option => option.value !== 'REVIEW')" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-button style="margin-left: 8px" @click="saveStatus">纠正</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="block">
      <h2>说明</h2>
      <el-input v-model="description" type="textarea" :rows="5" :disabled="!isAssignee && !session.isCreator" />
      <el-button v-if="isAssignee || session.isCreator" style="margin-top: 8px" @click="saveDescription">
        保存说明
      </el-button>
    </el-card>
    <el-card v-if="customFields.some(f=>f.visible)" shadow="never" class="block"><h2>自定义字段</h2><el-form label-position="top">
      <el-form-item v-for="field in customFields.filter(f=>f.visible)" :key="field.id" :label="field.name">
        <el-select v-if="field.type==='SINGLE_SELECT'" v-model="customValues[String(field.id)]" clearable style="width:100%"><el-option v-for="o in field.options" :key="o.id" :label="o.label" :value="o.label"/></el-select>
        <el-input v-else v-model="customValues[String(field.id)]" :type="field.type==='DATE'?'date':'text'" :required="field.required"/>
      </el-form-item><el-button type="primary" @click="saveCustomFields">保存字段</el-button>
    </el-form></el-card>

    <el-card shadow="never" class="block">
      <h2>任务动态</h2>
      <div v-for="item in activities" :key="item.id" class="comment">
        <div class="who">{{ item.actorName }} · {{ formatTime(item.createdAt) }}</div>
        <div>{{ describeTaskActivity(item) }}</div>
      </div>
      <p v-if="!activities.length" class="who">暂无动态</p>
    </el-card>
    <el-card shadow="never" class="block">
      <h2>评论</h2>
      <div v-for="item in comments" :key="item.id" class="comment">
        <div class="who">{{ item.authorName }} · {{ formatTime(item.createdAt) }}</div>
        <div>{{ item.content }}</div>
      </div>
      <el-input v-model="commentText" type="textarea" :rows="3" placeholder="发表评论" />
      <el-button style="margin-top: 8px" type="primary" @click="addComment">评论</el-button>
    </el-card>
    <TaskOperations v-if="task && task.id === taskId" :key="task.id" :task-id="task.id" :status="task.status" :project-id="task.projectId" @changed="load" />
  </div>
</template>

<style scoped>
.head {
  display: flex;
  align-items: center;
  gap: 10px;
}
h1 {
  margin: 8px 0;
  font-size: 22px;
}
h2 {
  margin: 0 0 12px;
  font-size: 15px;
}
.meta {
  color: #6b7280;
  margin-top: 0;
}
.actions {
  margin-bottom: 12px;
}
.block {
  margin-bottom: 12px;
  border-radius: 8px;
}
.comment {
  padding: 8px 0;
  border-bottom: 1px solid #eee;
  margin-bottom: 8px;
}
.who {
  font-size: 12px;
  color: #6b7280;
  margin-bottom: 4px;
}
</style>
