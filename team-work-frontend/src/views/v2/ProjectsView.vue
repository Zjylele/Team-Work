<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { projectApi } from '../../api/client'
import { ApiError } from '../../api/http'
import type { Project } from '../../api/types'
import { useSessionStore } from '../../stores/session'

const session = useSessionStore()
const router = useRouter()
const projects = ref<Project[]>([])
const dialog = ref(false)
const form = reactive({ name: '' })

async function load() {
  try {
    projects.value = await projectApi.list()
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '无法加载项目')
  }
}

async function create() {
  try {
    const created = await projectApi.create(form.name)
    dialog.value = false
    form.name = ''
    ElMessage.success('项目已创建')
    await router.push(`/projects/${created.id}`)
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '创建失败')
  }
}

async function rename(project: Project) {
  try {
    const { value } = await ElMessageBox.prompt('输入新的项目名称', '重命名项目', {
      inputValue: project.name,
      inputValidator: (value) => value.trim().length > 0 && value.trim().length <= 64,
      inputErrorMessage: '请输入 1–64 个字符',
      confirmButtonText: '保存',
      cancelButtonText: '取消',
    })
    const updated = await projectApi.rename(project.id, value.trim())
    projects.value = projects.value.map((item) => item.id === updated.id ? updated : item)
    ElMessage.success('项目名称已更新')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof ApiError ? error.message : '重命名失败')
    }
  }
}

onMounted(load)
</script>

<template>
  <div class="v2-page">
    <header class="v2-page-header">
      <div>
        <h1 class="v2-title">项目</h1>
        <p class="v2-subtitle">按项目组织阶段与任务</p>
      </div>
      <el-button v-if="session.isCreator" type="primary" @click="dialog = true">新建项目</el-button>
    </header>

    <div v-if="projects.length" class="v2-grid-projects v2-stagger">
      <div
        v-for="project in projects"
        :key="project.id"
        class="v2-card v2-card--clickable v2-project-tile"
        role="button"
        tabindex="0"
        @click="router.push(`/projects/${project.id}`)"
        @keydown.enter="router.push(`/projects/${project.id}`)"
      >
        {{ project.name }}
        <el-button
          v-if="session.isCreator"
          link
          aria-label="重命名项目"
          @click.stop="rename(project)"
          @keydown.enter.stop
        >
          重命名
        </el-button>
      </div>
    </div>
    <div v-else class="v2-empty">还没有项目</div>

    <el-dialog v-model="dialog" title="新建项目" width="400px">
      <el-input v-model="form.name" placeholder="项目名称" />
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="create">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>
