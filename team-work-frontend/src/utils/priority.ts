import type { TaskPriority } from '../api/types'

export const PRIORITY_LABELS: Record<TaskPriority, string> = {
  NONE: '未设置',
  LOW: '低',
  MEDIUM: '中',
  HIGH: '高',
  URGENT: '紧急',
}
