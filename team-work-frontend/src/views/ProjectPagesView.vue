<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { taskApi } from '../api/client'
import { del, get, post, put } from '../api/http'
import type { Task } from '../api/types'
import { useSessionStore } from '../stores/session'

interface Page {
  id: number
  title: string
  content: string
}

const route = useRoute()
const session = useSessionStore()
const projectId = computed(() => Number(route.params.projectId))
const endpoint = computed(() => '/api/projects/' + projectId.value + '/pages')
const pages = ref<Page[]>([])
const tasks = ref<Task[]>([])
const selected = ref<Page>()
const editing = ref(false)
const busy = ref(false)
const loading = ref(false)
const error = ref('')
const title = ref('')
const content = ref('')

async function load() {
  const url = endpoint.value
  loading.value = true
  error.value = ''
  try {
    const [documents, items] = await Promise.all([get<Page[]>(url), taskApi.list(projectId.value)])
    if (url !== endpoint.value) return
    pages.value = documents
    tasks.value = items
  } catch (cause) {
    if (url === endpoint.value) error.value = cause instanceof Error ? cause.message : '加载失败'
  } finally {
    if (url === endpoint.value) loading.value = false
  }
}

function edit(page?: Page) {
  selected.value = page
  title.value = page?.title ?? ''
  content.value = page?.content ?? ''
  editing.value = true
}

async function save() {
  if (!title.value.trim() || busy.value) return
  busy.value = true
  const url = endpoint.value
  try {
    const body = { title: title.value.trim(), content: content.value }
    if (selected.value) {
      await put<void>(url + '/' + selected.value.id, body)
      if (url === endpoint.value) selected.value = { ...selected.value, ...body }
    } else {
      const page = await post<Page>(url, body)
      if (url === endpoint.value) selected.value = page
    }
    if (url !== endpoint.value) return
    editing.value = false
    await load()
  } catch (cause) {
    ElMessage.error(cause instanceof Error ? cause.message : '保存失败')
  } finally {
    busy.value = false
  }
}

async function remove(page: Page) {
  if (busy.value) return
  busy.value = true
  const url = endpoint.value
  try {
    await ElMessageBox.confirm('删除后无法恢复。', '删除项目文档', {
      confirmButtonText: '确认删除', cancelButtonText: '取消', type: 'warning',
    })
    if (url !== endpoint.value) return
    await del<void>(url + '/' + page.id)
    selected.value = undefined
    await load()
  } catch (cause) {
    if (cause !== 'cancel' && cause !== 'close') {
      ElMessage.error(cause instanceof Error ? cause.message : '删除失败')
    }
  } finally {
    busy.value = false
  }
}

watch(projectId, () => {
  pages.value = []
  tasks.value = []
  selected.value = undefined
  editing.value = false
  void load()
}, { immediate: true })
</script>

<template>
  <main v-loading="loading" class="project-pages">
    <router-link :to="{ name: 'project', params: { projectId } }">返回项目</router-link>
    <h1>项目文档</h1>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <section v-else>
      <el-card shadow="never">
        <h2>任务概览</h2>
        <p>共 {{ tasks.length }} 项任务，{{ tasks.filter(task => task.status === 'DOING').length }} 项进行中，{{ tasks.filter(task => task.status === 'DONE').length }} 项已完成。</p>
      </el-card>
      <el-card shadow="never">
        <el-button v-if="session.isCreator" :disabled="editing || busy" type="primary" @click="edit()">新建文档</el-button>
        <el-empty v-if="!pages.length && !editing && !loading" description="暂无项目文档" />
        <ul v-if="pages.length">
          <li v-for="page in pages" :key="page.id">
            <el-button text :disabled="editing || busy" @click="selected = page">{{ page.title }}</el-button>
          </li>
        </ul>
        <el-form v-if="editing" label-position="top" @submit.prevent="save">
          <el-form-item label="标题">
            <el-input v-model="title" maxlength="120" :disabled="busy" />
          </el-form-item>
          <el-form-item label="正文">
            <el-input v-model="content" type="textarea" :rows="10" maxlength="16000" :disabled="busy" />
          </el-form-item>
          <el-button type="primary" native-type="submit" :loading="busy" :disabled="!title.trim()">保存</el-button>
          <el-button :disabled="busy" @click="editing = false">取消</el-button>
        </el-form>
        <article v-else-if="selected">
          <h2>{{ selected.title }}</h2>
          <p class="page-content">{{ selected.content }}</p>
          <template v-if="session.isCreator">
            <el-button :disabled="busy" @click="edit(selected)">编辑</el-button>
            <el-button type="danger" plain :loading="busy" @click="remove(selected)">删除</el-button>
          </template>
        </article>
      </el-card>
    </section>
  </main>
</template>

<style scoped>
.project-pages section {
  display: grid;
  gap: 16px;
}

.page-content {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
</style>
