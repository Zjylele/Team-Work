import { get } from './http'

export interface CalendarContext {
  today: string
  weekStart: string
  /** Exclusive end of the current week. */
  weekEnd: string
  zoneId: string
}

export function getCalendarContext() {
  return get<CalendarContext>('/api/team/calendar-context')
}

export function isOverdue(task: { status: string; dueDate: string | null }, today?: string) {
  return task.status !== 'DONE' && !!today && !!task.dueDate && task.dueDate < today
}
