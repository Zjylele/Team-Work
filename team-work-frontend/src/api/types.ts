export type Role = 'CREATOR' | 'MEMBER'
export type TaskStatus = 'TODO' | 'DOING' | 'REVIEW' | 'DONE'

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
  visibility?: 'SHARED' | 'PRIVATE'
  role?: 'OWNER' | 'ADMIN' | 'MEMBER' | 'VIEWER'
}

export interface ProjectView { id: number; name: string; scope: 'PERSONAL' | 'SHARED'; configJson: string; ownerId: number }
export interface ProjectTemplate { id: number; name: string; description?: string }
export interface ProjectField { id: number; name: string; type: 'TEXT' | 'SINGLE_SELECT' | 'DATE'; options: { id: number; label: string }[]; required: boolean; visible: boolean; sortOrder: number }
export interface ProjectMember { accountId: number; displayName: string; role: 'OWNER'|'ADMIN'|'MEMBER'|'VIEWER' }

export interface Stage {
  id: number
  projectId: number
  name: string
  sortOrder: number
}

export type TaskPriority = "NONE" | "LOW" | "MEDIUM" | "HIGH" | "URGENT"

export interface Task {
  id: number
  projectId: number
  stageId: number
  title: string
  description: string | null
  assigneeId: number
  assigneeName: string
  status: TaskStatus
  priority: TaskPriority
  startDate: string | null
  dueDate: string | null
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

export interface TaskActivity {
  id: number
  taskId: number
  actorId: number
  actorName: string
  action: string
  beforeValue: string | null
  afterValue: string | null
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
