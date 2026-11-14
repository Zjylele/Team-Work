import { House, FolderOpened, Calendar, Bell, Setting, User } from '@element-plus/icons-vue'
import { computed } from 'vue'
import { useRoute } from 'vue-router'

export const APP_NAV_ITEMS = [
  { path: '/', icon: House, label: '工作台', short: '工作台' },
  { path: '/projects', icon: FolderOpened, label: '项目', short: '项目' },
  { path: '/calendar', icon: Calendar, label: '任务日历', short: '日历' },
  { path: '/notifications', icon: Bell, label: '通知', short: '通知' },
  { path: '/directory', icon: User, label: '通讯录', short: '通讯录' },
  { path: '/settings', icon: Setting, label: '个人设置', short: '设置' },
] as const

export function useAppNavActive() {
  const route = useRoute()
  return computed(() => {
    if (route.path.startsWith('/projects') || route.path.startsWith('/tasks')) return '/projects'
    if (route.path === '/calendar') return '/calendar'
    if (route.path.startsWith('/notifications')) return '/notifications'
    if (route.path === '/directory') return '/directory'
    if (route.path.startsWith('/settings')) return '/settings'
    return '/'
  })
}
