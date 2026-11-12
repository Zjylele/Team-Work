import { computed } from 'vue'
import { useRoute } from 'vue-router'

export const APP_NAV_ITEMS = [
  { path: '/', label: '工作台', short: '工作台' },
  { path: '/projects', label: '项目', short: '项目' },
  { path: '/notifications', label: '通知', short: '通知' },
  { path: '/settings', label: '个人设置', short: '设置' },
] as const

export function useAppNavActive() {
  const route = useRoute()
  return computed(() => {
    if (route.path.startsWith('/projects') || route.path.startsWith('/tasks')) return '/projects'
    if (route.path.startsWith('/notifications')) return '/notifications'
    if (route.path.startsWith('/settings')) return '/settings'
    return '/'
  })
}
