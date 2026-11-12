export type Role = 'CREATOR' | 'MEMBER'
export type TaskStatus = 'TODO' | 'DOING' | 'DONE'

export interface Account {
  id: number
  username: string
  displayName: string
}

export interface Team {
  id: number
  name: string
  role: Role
  inviteCode: string | null
}

export interface Project {
  id: number
  name: string
}

export interface Stage {
  id: number
  projectId: number
  name: string
  sortOrder: number
}

export interface Task {
  id: number
  projectId: number
  stageId: number
  title: string
  description: string | null
  assigneeId: number
  assigneeName: string
  status: TaskStatus
  createdAt: string
}

export interface Comment {
  id: number
  taskId: number
  authorId: number
  authorName: string
  content: string
  createdAt: string
}

export interface Notification {
  id: number
  taskId: number
  type: string
  summary: string
  read: boolean
  createdAt: string
}

export interface Workbench {
  openTasks: Task[]
  inProgressProjectCount: number
  recentNotifications: Notification[]
}

export interface TeamMemberView {
  id: number
  displayName: string
  role?: Role
}
