import type { TaskStatus } from '../api/types'

export const STATUS_LABEL: Record<TaskStatus, string> = {
  TODO: '待开始',
  DOING: '进行中',
  DONE: '已完成',
}

export const STATUS_OPTIONS: { value: TaskStatus; label: string }[] = [
  { value: 'TODO', label: '待开始' },
  { value: 'DOING', label: '进行中' },
  { value: 'DONE', label: '已完成' },
]

export function statusLabel(status: string) {
  if (status === 'TODO' || status === 'DOING' || status === 'DONE') {
    return STATUS_LABEL[status]
  }
  return status
}

export function statusType(status: string) {
  if (status === 'DOING') return 'warning'
  if (status === 'DONE') return 'success'
  return 'info'
}

export function formatTime(value: string | undefined) {
  if (!value) return ''
  return value.replace('T', ' ').slice(0, 16)
}
