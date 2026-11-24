<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { get, post, put, del } from '../api/http'
import { projectApi } from '../api/client'
import { useSessionStore } from '../stores/session'

const props = defineProps<{ taskId: number; status: string; projectId: number }>()
const emit = defineEmits<{ changed: [] }>()
const session = useSessionStore()
interface Approval { id: number; approverId: number; requesterId: number; status: 'PENDING' | 'APPROVED' | 'REJECTED'; requestedAt: string; decidedAt?: string; comment?: string }
interface Entry { id: number; accountId: number; workDate: string; minutes: number; note: string }
const labels = { PENDING: '待验收', APPROVED: '已通过', REJECTED: '已退回' }
const approvals = ref<Approval[]>([])
const entries = ref<Entry[]>([])
const editingId = ref<number>()
const workDate = ref('')
const minutes = ref(30)
const note = ref('')
const decisionComment = ref('')
const busy = ref(false)
interface PlannedEffort { plannedMinutes: number | null }
const canWrite = ref(false)
const canManage = ref(false)
const configuredApprover = ref<number | null>(null)
watch(() => props.projectId, async (id) => {
  canWrite.value = false
  canManage.value = false
  configuredApprover.value = null
  try {
    const project = (await projectApi.list()).find(item => item.id === id)
    if (id !== props.projectId) return
    canWrite.value = !!project?.role && project.role !== 'VIEWER'
    canManage.value = project?.role === 'OWNER' || project?.role === 'ADMIN'
    if (canManage.value) {
      const workflow = await get<{ approverId: number | null }>('/api/projects/' + id + '/workflow')
      if (id === props.projectId) configuredApprover.value = workflow.approverId
    }
  } catch (error) { report(error) }
}, { immediate: true })

const effort = ref<PlannedEffort>()
const plannedInput = ref<number | undefined>()
const planPath = computed(() => '/api/tasks/' + props.taskId + '/planned-minutes')
async function loadPlan() {
  try {
    effort.value = await get<PlannedEffort>(planPath.value)
    plannedInput.value = effort.value.plannedMinutes ?? undefined
  } catch (error) { report(error) }
}
function savePlan() {
  return act(async () => {
    effort.value = await put<PlannedEffort>(planPath.value, { plannedMinutes: plannedInput.value ?? null })
    plannedInput.value = effort.value.plannedMinutes ?? undefined
    ElMessage.success('计划工时已保存')
  })
}
const approvalPath = computed(() => '/api/tasks/' + props.taskId + '/approvals')
const entryPath = computed(() => '/api/tasks/' + props.taskId + '/time-entries')
function person(id: number) { return session.members.find(member => member.id === id)?.displayName ?? '#' + id }
function report(error: unknown) { ElMessage.error(error instanceof Error ? error.message : '请求失败') }
async function loadApprovals() {
  try { approvals.value = await get<Approval[]>(approvalPath.value) } catch (error) { report(error) }
}
async function loadEntries() {
  try { entries.value = await get<Entry[]>(entryPath.value) } catch (error) { report(error) }
}
async function act(action: () => Promise<void>) {
  if (busy.value) return
  busy.value = true
  try { await action() } catch (error) { report(error) } finally { busy.value = false }
}
function edit(row?: Entry) {
  editingId.value = row?.id
  workDate.value = row?.workDate ?? ''
  minutes.value = row?.minutes ?? 30
  note.value = row?.note ?? ''
}
async function saveEntry() {
  if (!workDate.value || !Number.isInteger(minutes.value) || minutes.value < 1) return
  await act(async () => {
    const body = { workDate: workDate.value, minutes: minutes.value, note: note.value.trim() }
    if (editingId.value) await put(entryPath.value + '/' + editingId.value, body)
    else await post(entryPath.value, body)
    edit()
    await loadEntries()
  })
}
async function removeEntry(row: Entry) {
  try { await ElMessageBox.confirm('确定撤销这条工时记录？', '撤销工时') } catch { return }
  await act(async () => {
    await del(entryPath.value + '/' + row.id)
    if (editingId.value === row.id) edit()
    await loadEntries()
  })
}
async function decide(row: Approval, approved: boolean) {
  await act(async () => {
    await post(approvalPath.value + '/' + row.id + '/decision', { approved, comment: decisionComment.value.trim() })
    decisionComment.value = ''
    await loadApprovals()
    emit('changed')
  })
}
async function reassign(row: Approval) {
  await act(async () => {
    await post(approvalPath.value + '/' + row.id + '/reassign')
    await loadApprovals()
    emit('changed')
    ElMessage.success('已重新指派审批人')
  })
}
watch(() => props.status, loadApprovals, { immediate: true })
watch(entryPath, loadEntries, { immediate: true })
watch(planPath, loadPlan, { immediate: true })
</script>

<template>
  <el-card class="task-operations">
    <template #header>验收记录</template>
    <el-empty v-if="!approvals.length" description="暂无验收记录" :image-size="48" />
    <article v-for="row in approvals" :key="row.id" class="approval">
      <div><el-tag>{{ labels[row.status] }}</el-tag> 发起人：{{ person(row.requesterId) }} · 审批人：{{ person(row.approverId) }}</div>
      <div>发起时间：{{ row.requestedAt?.replace('T', ' ').slice(0, 16) }}</div>
      <div v-if="row.decidedAt">处理时间：{{ row.decidedAt.replace('T', ' ').slice(0, 16) }}</div>
      <div v-if="row.comment">审批意见：{{ row.comment }}</div>
      <div v-if="row.status === 'PENDING' && row.approverId === session.account?.id" class="actions">
        <el-input v-model="decisionComment" aria-label="审批意见" placeholder="审批意见（可选）" maxlength="500" />
        <el-button type="success" :disabled="busy" @click="decide(row, true)">通过</el-button>
        <el-button type="warning" :disabled="busy" @click="decide(row, false)">退回</el-button>
      </div>
      <div v-if="row.status === 'PENDING' && canManage">
        <el-button v-if="configuredApprover && configuredApprover !== row.approverId" :disabled="busy" @click="reassign(row)">改派给当前配置的审批人</el-button>
        <router-link v-else :to="{ name: 'project-operations', params: { projectId: props.projectId } }">如需改派，请先在项目中配置新的审批人</router-link>
      </div>
    </article>
  </el-card>
  <el-card class="task-operations">
    <template #header>工时记录</template>
    <el-form v-if="effort && canWrite" inline @submit.prevent="savePlan">
      <el-form-item label="计划分钟">
        <el-input-number v-model="plannedInput" :min="0" :max="525600" :precision="0" placeholder="未设置" />
      </el-form-item>
      <el-form-item>
        <el-button native-type="submit" :disabled="busy || (plannedInput ?? null) === effort.plannedMinutes">保存计划工时</el-button>
      </el-form-item>
    </el-form>
    <p v-else-if="effort">计划分钟：{{ effort.plannedMinutes ?? '未设置' }}</p>
    <el-form v-if="canWrite" inline @submit.prevent="saveEntry">
      <el-form-item label="日期"><el-date-picker v-model="workDate" value-format="YYYY-MM-DD" /></el-form-item>
      <el-form-item label="分钟"><el-input-number v-model="minutes" :min="1" :max="1440" :precision="0" /></el-form-item>
      <el-form-item label="说明"><el-input v-model="note" maxlength="500" /></el-form-item>
      <el-form-item>
        <el-button type="primary" native-type="submit" :disabled="busy || !workDate">{{ editingId ? '保存更正' : '登记工时' }}</el-button>
        <el-button v-if="editingId" @click="edit()">取消更正</el-button>
      </el-form-item>
    </el-form>
    <el-table :data="entries" empty-text="暂无工时记录">
      <el-table-column label="成员"><template #default="{row}">{{ person(row.accountId) }}</template></el-table-column>
      <el-table-column label="日期" prop="workDate" />
      <el-table-column label="实际分钟" prop="minutes" />
      <el-table-column label="说明" prop="note" show-overflow-tooltip />
      <el-table-column label="操作">
        <template #default="{row}">
          <template v-if="canWrite && row.accountId === session.account?.id">
            <el-button link :disabled="busy" @click="edit(row)">更正</el-button>
            <el-button link type="danger" :disabled="busy" @click="removeEntry(row)">撤销</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<style scoped>
.task-operations { margin-top: 16px; }
.approval { display: grid; gap: 8px; margin-bottom: 12px; }
.actions { display: flex; gap: 8px; align-items: center; }
</style>
