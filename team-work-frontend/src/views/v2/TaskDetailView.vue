<script setup lang="ts">
import { describeTaskActivity } from '../../utils/taskActivity'
import TaskOperations from '../../components/TaskOperations.vue'
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { projectApi, taskApi } from '../../api/client'
import { ApiError } from '../../api/http'
import type { Comment, Stage, Task, TaskStatus, TaskActivity } from '../../api/types'
import { useSessionStore } from '../../stores/session'
import { STATUS_OPTIONS, formatTime, statusLabel, statusType } from '../../utils/status'

import { PRIORITY_LABELS } from '../../utils/priority'
import type { TaskPriority } from '../../api/types'

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
async function saveCustomFields() { try { const id = taskId.value; const values = await taskApi.saveFieldValues(id,customValues.value); if (id !== taskId.value) return; customValues.value = values; ElMessage.success('自定义字段已保存') } catch(error) { ElMessage.error(error instanceof ApiError?error.message:'无法保存自定义字段') } }

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
  <div v-if="task && task.id === taskId" class="v2-page">
    <div class="v2-link-back task-back">
      <button type="button" class="back-button" @click="router.push(`/projects/${task.projectId}`)">返回</button>
    </div>
    <header class="task-heading">
      <div>
        <p class="task-number">任务 #{{ task.id }}</p>
        <div class="title-line">
          <h1>{{ task.title }}</h1>
          <el-tag :type="statusType(task.status)" round>{{ statusLabel(task.status) }}</el-tag>
        </div>
      </div>
      <div class="heading-actions">
        <el-button v-if="isAssignee && task.status === 'TODO'" type="primary" @click="start">开始任务</el-button>
        <el-button v-if="isAssignee && task.status === 'DOING'" type="success" @click="complete">结束任务</el-button>
        <el-button v-if="session.isCreator" @click="saveMeta">保存任务设置</el-button>
        <el-button v-if="session.isCreator" type="danger" plain :loading="deleting" @click="removeTask">删除任务</el-button>
      </div>
    </header>

    <div class="detail-layout">
    <div class="detail-main">
    <el-card v-if="session.isCreator" shadow="never" class="v2-card block">
      <h2 class="section">基础设置 <small>创建者维护</small></h2>
      <el-form label-position="top" class="settings-form">
        <el-form-item label="任务标题" class="full-width">
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
        <div class="full-width"><el-button type="primary" @click="saveMeta">保存任务设置</el-button></div>
        <el-form-item label="纠正状态" class="full-width status-correction">
          <el-select v-model="correctStatus" style="width: 200px">
            <el-option v-for="item in STATUS_OPTIONS.filter(option => option.value !== 'REVIEW')" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-button style="margin-left: 8px" @click="saveStatus">纠正</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="v2-card block">
      <h2 class="section">任务说明</h2>
      <el-input v-model="description" type="textarea" :rows="5" :disabled="!isAssignee && !session.isCreator" />
      <el-button v-if="isAssignee || session.isCreator" style="margin-top: 8px" @click="saveDescription">
        保存说明
      </el-button>
    </el-card>
    <el-card v-if="customFields.some(field=>field.visible)" shadow="never" class="v2-card block">
      <h2 class="section">自定义字段</h2>
      <el-form label-position="top" class="settings-form">
        <el-form-item v-for="field in customFields.filter(item=>item.visible)" :key="field.id" :label="field.name">
          <el-select v-if="field.type==='SINGLE_SELECT'" v-model="customValues[String(field.id)]" clearable style="width:100%">
            <el-option v-for="option in field.options" :key="option.id" :label="option.label" :value="option.label"/>
          </el-select>
          <el-input v-else v-model="customValues[String(field.id)]" :type="field.type==='DATE'?'date':'text'" :required="field.required"/>
        </el-form-item>
        <el-button type="primary" @click="saveCustomFields">保存字段</el-button>
      </el-form>
    </el-card>

      <div class="task-operations">
        <TaskOperations v-if="task && task.id === taskId" :key="task.id" :task-id="task.id" :status="task.status" :project-id="task.projectId" @changed="load" />
      </div>
    <el-card shadow="never" class="v2-card block discussion">
      <h2 class="section">讨论与评论 <span>{{ comments.length }}</span></h2>
      <p v-if="!comments.length" class="empty-comments">暂无评论</p>
      <div v-for="item in comments" :key="item.id" class="comment">
        <div class="who">{{ item.authorName }} · {{ formatTime(item.createdAt) }}</div>
        <div>{{ item.content }}</div>
      </div>
      <el-input v-model="commentText" type="textarea" :rows="3" placeholder="发表评论" />
      <el-button style="margin-top: 8px" type="primary" @click="addComment">发表评论</el-button>
    </el-card>
    </div>
    <aside aria-label="任务信息">
      <el-card shadow="never" class="v2-card block">
        <h2 class="section">任务属性</h2>
        <dl class="properties">
          <div><dt>负责人</dt><dd>{{ task.assigneeName }}</dd></div>
          <div><dt>优先级</dt><dd>{{ PRIORITY_LABELS[task.priority] }}</dd></div>
          <div><dt>开始日期</dt><dd>{{ task.startDate || "未设置" }}</dd></div>
          <div><dt>截止日期</dt><dd>{{ task.dueDate || "未设置" }}</dd></div>
        </dl>
      </el-card>
    <el-card shadow="never" class="v2-card block discussion">
      <h2 class="section">任务动态</h2>
      <div v-for="item in activities" :key="item.id" class="comment">
        <div class="who">{{ item.actorName }} · {{ formatTime(item.createdAt) }}</div>
        <div>{{ describeTaskActivity(item) }}</div>
      </div>
      <p v-if="!activities.length" class="empty-comments">暂无动态</p>
    </el-card>
    </aside>
    </div>

  </div>
</template>

<style scoped>
.head {
  align-items: center;
}
.section {
  margin: 0 0 12px;
  font-size: 15px;
  font-weight: 600;
}
.actions {
  margin-bottom: 16px;
}
.block {
  margin-bottom: 14px;
  padding: 4px;
  border: 1px solid var(--color-border);
}
.comment {
  padding: 8px 0;
  border-bottom: 1px solid var(--color-border);
  margin-bottom: 8px;
}
.who {
  font-size: 12px;
  color: var(--color-muted-foreground);
  margin-bottom: 4px;
}
.detail-layout {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(280px, 1fr);
  gap: 24px;
  align-items: start;
  margin-top: 16px;
}
.properties { display: grid; gap: 12px; margin: 0; font-size: 13px; }
.properties div { display: grid; grid-template-columns: 6em minmax(0, 1fr); gap: 8px; }
.properties dt { color: var(--color-muted-foreground); }
.properties dd { margin: 0; overflow-wrap: anywhere; }
.discussion .section { border-bottom: 1px solid var(--color-border); padding-bottom: 12px; }
.discussion .section span { margin-left: 8px; background: var(--color-muted); padding: 2px 7px; border-radius: 10px; font-size: 12px; }
.empty-comments { margin: 20px 0; font-size: 13px; }
@media (max-width: 820px) { .detail-layout { grid-template-columns: minmax(0, 1fr); } }
.v2-page { --task-field-background: #f2f4f6; max-width: 1520px; margin-inline: auto; }
.v2-page :deep(.el-input__wrapper),
.v2-page :deep(.el-select__wrapper),
.v2-page :deep(.el-textarea__inner) {
  --el-input-bg-color: var(--task-field-background);
  --el-fill-color-blank: var(--task-field-background);
}
.task-back { margin-bottom: 14px; }
.task-heading { display: flex; justify-content: space-between; align-items: flex-end; flex-wrap: wrap; gap: 16px; padding: 20px 24px; border-radius: 15px; border: 1px solid var(--color-border); background: var(--color-card); }
.task-number { margin: 0 0 6px; color: var(--color-muted-foreground); font-size: 12px; }
.title-line, .heading-actions { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; }
.title-line h1 { margin: 0; font-size: clamp(22px, 3vw, 32px); overflow-wrap: anywhere; }
.heading-actions :deep(.el-button + .el-button) { margin-left: 0; }
.detail-layout { grid-template-columns: minmax(0, 2.35fr) minmax(240px, 1fr); gap: 20px; margin-top: 20px; }
.detail-main, .detail-layout aside { min-width: 0; }
.block { background: var(--color-card); border-radius: 14px; padding: 0; margin-bottom: 16px; }
.section { display: flex; align-items: center; gap: 8px; margin-bottom: 16px; font-size: 17px; }
.section small { font-size: 12px; font-weight: 500; color: var(--color-muted-foreground); }
.settings-form { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 14px; }
.settings-form :deep(.el-select) { width: 100%; }
.full-width { grid-column: 1 / -1; }
.status-correction { margin-top: 14px; margin-bottom: 0; }
.task-operations { margin-bottom: 16px; }
.task-operations :deep(.el-card) { border-radius: 14px; box-shadow: none; }
.task-operations :deep(.el-empty) { --el-empty-padding: 15px 0; --el-empty-image-width: 50px; }
@media (max-width: 820px) { .detail-layout { grid-template-columns: minmax(0, 1fr); } }
@media (max-width: 520px) { .settings-form { grid-template-columns: minmax(0, 1fr); } }
</style>
