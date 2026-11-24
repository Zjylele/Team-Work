import { del, get, patch, post, put, request, ApiError } from './http'
import type {
  Account,
  Comment,
  Notification,
  Project,
  Stage,
  Task,
  TaskStatus,
  TaskPriority,
  Team,
  TeamMemberView,
  Workbench,
  TaskActivity,
  ProjectView, ProjectTemplate, ProjectField, ProjectMember,
} from './types'

export const authApi = {
  me: () => get<Account>('/api/auth/me'),
  register: (body: { username: string; password: string; displayName: string }) =>
    post<Account>('/api/auth/register', body),
  login: (body: { username: string; password: string }) => post<Account>('/api/auth/login', body),
  logout: () => post<{ message: string }>('/api/auth/logout'),
}

export const teamApi = {
  current: async (): Promise<Team | null> => {
    try {
      return await get<Team>('/api/team')
    } catch (error) {
      if (error instanceof ApiError && error.status === 404) {
        return null
      }
      throw error
    }
  },
  create: (name: string) => post<Team>('/api/team', { name }),
  join: (code: string) => post<Team>('/api/team/join', { code }),
  regenerateInvite: () => post<Team>('/api/team/invite'),
  members: async (): Promise<TeamMemberView[] | null> => {
    try {
      return await get<TeamMemberView[]>('/api/team/members')
    } catch (error) {
      if (error instanceof ApiError && (error.status === 404 || error.status === 405)) {
        return null
      }
      throw error
    }
  },
}

export const projectApi = {
  rename: (id: number, name: string) => patch<Project>(`/api/projects/${id}`, { name }),
  update: (id: number, body: { name?: string; visibility?: 'SHARED' | 'PRIVATE' }) => patch<Project>(`/api/projects/${id}`, body),
  setVisibility: (id: number, visibility: 'SHARED'|'PRIVATE') => patch<Project>(`/api/projects/${id}/visibility`, { visibility }),
  remove: (id: number) => del<void>(`/api/projects/${id}`),
  list: () => get<Project[]>('/api/projects'),
  create: (name: string, templateId?: number, visibility?: 'SHARED' | 'PRIVATE') => post<Project>('/api/projects', { name, templateId, visibility }),
  templates: () => get<ProjectTemplate[]>('/api/project-templates'),
  views: (id: number) => get<ProjectView[]>(`/api/projects/${id}/views`),
  saveView: (id: number, body: { name: string; scope: 'PERSONAL' | 'SHARED'; configJson: string }) => post<ProjectView>(`/api/projects/${id}/views`, body),
  removeView: (projectId: number, viewId: number) => del<void>(`/api/projects/${projectId}/views/${viewId}`),
  updateView: (projectId: number, viewId: number, body: { name?: string; scope?: 'PERSONAL'|'SHARED'; configJson?: string }) => patch<ProjectView>(`/api/projects/${projectId}/views/${viewId}`, body),
  fields: (id: number) => get<ProjectField[]>(`/api/projects/${id}/fields`),
  addField: (id: number, body: { name: string; type: ProjectField['type']; options?: { label: string }[]; required?: boolean; visible?: boolean }) => post<ProjectField>(`/api/projects/${id}/fields`, body),
  members: (id: number) => get<ProjectMember[]>(`/api/projects/${id}/members`),
  setMemberRole: (id: number, accountId: number, role: ProjectMember['role']) => put<{message?:string}>(`/api/projects/${id}/members/${accountId}`, { role }),
  stages: (projectId: number) => get<Stage[]>(`/api/projects/${projectId}/stages`),
  addStage: (projectId: number, name: string) =>
    post<Stage>(`/api/projects/${projectId}/stages`, { name }),
  updateStage: (stageId: number, body: { name?: string; sortOrder?: number }) =>
    patch<Stage>(`/api/stages/${stageId}`, body),
  deleteStage: (stageId: number) => del<{ message: string }>(`/api/stages/${stageId}`),
}

export const taskApi = {
  remove: (id: number) => del<void>(`/api/tasks/${id}`),
  list: (projectId: number) => get<Task[]>(`/api/projects/${projectId}/tasks`),
  create: (
    projectId: number,
    body: { title: string; description?: string; assigneeId: number; stageId: number; priority?: TaskPriority; startDate?: string; dueDate?: string },
  ) => post<Task>(`/api/projects/${projectId}/tasks`, body),
  get: (taskId: number) => get<Task>(`/api/tasks/${taskId}`),
  update: (taskId: number, body: { title?: string; description?: string; assigneeId?: number; stageId?: number; priority?: TaskPriority; startDate?: string; dueDate?: string }) =>
    patch<Task>(`/api/tasks/${taskId}`, body),
  start: (taskId: number) => post<Task>(`/api/tasks/${taskId}/start`),
  complete: (taskId: number) => post<Task>(`/api/tasks/${taskId}/complete`),
  correctStatus: (taskId: number, status: TaskStatus) =>
    put<Task>(`/api/tasks/${taskId}/status`, { status }),
  comments: (taskId: number) => get<Comment[]>(`/api/tasks/${taskId}/comments`),
  activity: (taskId: number) => get<TaskActivity[]>(`/api/tasks/${taskId}/activity`),
  fieldValues: (taskId: number) => get<Record<string, string>>(`/api/tasks/${taskId}/fields`),
  saveFieldValues: (taskId: number, values: Record<string, string>) => put<Record<string, string>>(`/api/tasks/${taskId}/fields`, { values }),
  addComment: (taskId: number, content: string) =>
    post<Comment>(`/api/tasks/${taskId}/comments`, { content }),
}

export const workbenchApi = {
  get: () => get<Workbench>('/api/workbench'),
}

export const notificationApi = {
  list: () => get<Notification[]>('/api/notifications'),
  read: (id: number) => post<Notification>(`/api/notifications/${id}/read`),
  readAll: () => post<{ message: string }>('/api/notifications/read-all'),
}

export const profileApi = {
  updateDisplayName: (displayName: string) => patch<{ displayName: string }>('/api/me', { displayName }),
  changePassword: (oldPassword: string, newPassword: string) =>
    post<{ message: string }>('/api/me/password', { oldPassword, newPassword }),
}

export { request, ApiError }
