import type { TaskActivity } from '../api/types'
import { statusLabel } from './status'

export function describeTaskActivity({ action, beforeValue, afterValue }: TaskActivity) {
  const change = (label: string, before = beforeValue, after = afterValue) =>
    `${label}：${before || '未设置'} → ${after || '未设置'}`
  const account = (id: typeof beforeValue) => id ? `成员 #${id}` : undefined

  switch (action) {
    case 'STATUS_CHANGED':
      return change('状态变更', beforeValue && statusLabel(beforeValue), afterValue && statusLabel(afterValue))
    case 'ASSIGNEE_CHANGED':
      return change('负责人变更', account(beforeValue), account(afterValue))
    case 'START_DATE_CHANGED':
      return change('开始日期变更')
    case 'DUE_DATE_CHANGED':
      return change('截止日期变更')
    case 'APPROVAL_APPROVED':
      return '验收通过'
    case 'APPROVAL_REJECTED':
      return '验收退回'
    case 'APPROVAL_REASSIGNED':
      return change('验收审批人变更', account(beforeValue), account(afterValue))
    default:
      return '任务更新'
  }
}
