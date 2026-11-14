<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { projectApi, taskApi } from '../../api/client'
import { ApiError } from '../../api/http'
import type { Comment, Stage, Task, TaskStatus } from '../../api/types'
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
const stages = ref<Stage[]>([])
const commentText = ref('')
const description = ref('')
const title = ref('')
const priority = ref<TaskPriority>('NONE')
const dueDate = ref('')
const assigneeId = ref<number>()
const stageId = ref<number>()
const correctStatus = ref<TaskStatus>('TODO')

const isAssignee = computed(() => task.value?.assigneeId === session.account?.id)

async function load() {
  try {
    task.value = await taskApi.get(taskId.value)
    comments.value = await taskApi.comments(taskId.value)
    stages.value = await projectApi.stages(task.value.projectId)
    description.value = task.value.description || ''
    title.value = task.value.title
    priority.value = task.value.priority
    dueDate.value = task.value.dueDate ?? ""
    assigneeId.value = task.value.assigneeId
    stageId.value = task.value.stageId
    correctStatus.value = task.value.status
    session.rememberPerson({ id: task.value.assigneeId, displayName: task.value.assigneeName })
    for (const item of comments.value) {
      session.rememberPerson({ id: item.authorId, displayName: item.authorName })
    }
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '无法加载任务')
  }
}

async function start() {
  try {
    task.value = await taskApi.start(taskId.value)
    ElMessage.success('已开始')
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '无法开始')
  }
}

async function complete() {
  try {
    task.value = await taskApi.complete(taskId.value)
    ElMessage.success('已完成')
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '无法完成')
  }
}

async function saveDescription() {
  try {
    task.value = await taskApi.update(taskId.value, { description: description.value })
    ElMessage.success('说明已保存')
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '无法保存说明')
  }
}

async function saveMeta() {
  try {
    task.value = await taskApi.update(taskId.value, {
      title: title.value,
      priority: priority.value,
      dueDate: dueDate.value,
      assigneeId: Number(assigneeId.value),
      stageId: stageId.value,
    })
    ElMessage.success('任务已更新')
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '无法更新')
  }
}

async function saveStatus() {
  try {
    task.value = await taskApi.correctStatus(taskId.value, correctStatus.value)
    ElMessage.success('状态已纠正')
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '无法改状态')
  }
}

async function addComment() {
  try {
    const created = await taskApi.addComment(taskId.value, commentText.value)
    comments.value = [...comments.value, created]
    commentText.value = ''
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '无法发表评论')
  }
}

onMounted(load)
const deleting = ref(false)

async function removeTask() {
  if (deleting.value) return
  deleting.value = true
  try {
    await ElMessageBox.confirm('删除任务将同时删除相关评论和通知，且无法恢复。', '确认删除任务', {
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await taskApi.remove(taskId.value)
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
  <div v-if="task" class="v2-page">
    <div class="v2-link-back">
      <el-button link @click="router.push(`/projects/${task.projectId}`)">返回项目</el-button>
    </div>
    <header class="v2-page-header head">
      <h1 class="v2-title">{{ task.title }}</h1>
      <p>优先级 {{ PRIORITY_LABELS[task.priority] }} · 截止 {{ task.dueDate || "未设置" }}</p>
      <el-tag :type="statusType(task.status)">{{ statusLabel(task.status) }}</el-tag>
    </header>
    <p class="v2-subtitle">负责人 {{ task.assigneeName }}</p>

    <div class="actions">
      <el-button v-if="isAssignee && task.status === 'TODO'" type="primary" @click="start">开始</el-button>
      <el-button v-if="isAssignee && task.status === 'DOING'" type="success" @click="complete">结束</el-button>
    </div>

    <el-button v-if="session.isCreator" type="danger" plain :loading="deleting" @click="removeTask">删除任务</el-button>
    <el-card v-if="session.isCreator" shadow="never" class="v2-card block">
      <h2 class="section">创建者维护</h2>
      <el-form label-position="top">
        <el-form-item label="标题">
          <el-input v-model="title" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="priority">
            <el-option v-for="(label, value) in PRIORITY_LABELS" :key="value" :value="value" :label="label" />
          </el-select>
        </el-form-item>
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
            <el-option v-for="item in STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-button style="margin-left: 8px" @click="saveStatus">纠正</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="v2-card block">
      <h2 class="section">说明</h2>
      <el-input v-model="description" type="textarea" :rows="5" :disabled="!isAssignee && !session.isCreator" />
      <el-button v-if="isAssignee || session.isCreator" style="margin-top: 8px" @click="saveDescription">
        保存说明
      </el-button>
    </el-card>

    <el-card shadow="never" class="v2-card block">
      <h2 class="section">评论</h2>
      <div v-for="item in comments" :key="item.id" class="comment">
        <div class="who">{{ item.authorName }} · {{ formatTime(item.createdAt) }}</div>
        <div>{{ item.content }}</div>
      </div>
      <el-input v-model="commentText" type="textarea" :rows="3" placeholder="发表评论" />
      <el-button style="margin-top: 8px" type="primary" @click="addComment">评论</el-button>
    </el-card>
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
</style>
