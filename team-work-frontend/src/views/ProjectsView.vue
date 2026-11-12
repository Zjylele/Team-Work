<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { projectApi } from '../api/client'
import { ApiError } from '../api/http'
import type { Project } from '../api/types'
import { useSessionStore } from '../stores/session'

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

onMounted(load)
</script>

<template>
  <div>
    <div class="head">
      <h1>项目</h1>
      <el-button v-if="session.isCreator" type="primary" @click="dialog = true">新建项目</el-button>
    </div>
    <div class="grid">
      <el-card
        v-for="project in projects"
        :key="project.id"
        shadow="never"
        class="item"
        @click="router.push(`/projects/${project.id}`)"
      >
        {{ project.name }}
      </el-card>
      <div v-if="!projects.length" class="empty">还没有项目</div>
    </div>
    <el-dialog v-model="dialog" title="新建项目" width="400px">
      <el-input v-model="form.name" placeholder="项目名称" />
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="create">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
h1 {
  margin: 0;
  font-size: 22px;
}
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
}
.item {
  border-radius: 8px;
  cursor: pointer;
}
.item:hover {
  border-color: #3f76ff;
}
.empty {
  color: #6b7280;
}
</style>
