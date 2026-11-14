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
  remove: (id: number) => del<void>(`/api/projects/${id}`),
  list: () => get<Project[]>('/api/projects'),
  create: (name: string) => post<Project>('/api/projects', { name }),
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
    body: { title: string; description?: string; assigneeId: number; stageId: number; priority?: TaskPriority; dueDate?: string },
  ) => post<Task>(`/api/projects/${projectId}/tasks`, body),
  get: (taskId: number) => get<Task>(`/api/tasks/${taskId}`),
  update: (taskId: number, body: { title?: string; description?: string; assigneeId?: number; stageId?: number; priority?: TaskPriority; dueDate?: string }) =>
    patch<Task>(`/api/tasks/${taskId}`, body),
  start: (taskId: number) => post<Task>(`/api/tasks/${taskId}/start`),
  complete: (taskId: number) => post<Task>(`/api/tasks/${taskId}/complete`),
  correctStatus: (taskId: number, status: TaskStatus) =>
    put<Task>(`/api/tasks/${taskId}/status`, { status }),
  comments: (taskId: number) => get<Comment[]>(`/api/tasks/${taskId}/comments`),
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
