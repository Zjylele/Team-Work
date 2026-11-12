import { createRouter, createWebHistory } from 'vue-router'
import { useSessionStore } from '../stores/session'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      component: () => import('../views/LoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/register',
      component: () => import('../views/RegisterView.vue'),
      meta: { public: true },
    },
    {
      path: '/join',
      component: () => import('../views/JoinTeamView.vue'),
    },
    {
      path: '/',
      component: () => import('../layouts/AppLayout.vue'),
      meta: { team: true },
      children: [
        { path: '', name: 'workbench', component: () => import('../views/WorkbenchView.vue') },
        { path: 'projects', name: 'projects', component: () => import('../views/ProjectsView.vue') },
        {
          path: 'projects/:projectId',
          name: 'project',
          component: () => import('../views/ProjectDetailView.vue'),
        },
        { path: 'tasks/:taskId', name: 'task', component: () => import('../views/TaskDetailView.vue') },
        {
          path: 'notifications',
          name: 'notifications',
          component: () => import('../views/NotificationsView.vue'),
        },
        { path: 'settings', name: 'settings', component: () => import('../views/SettingsView.vue') },
      ],
    },
  ],
})

router.beforeEach(async (to) => {
  const session = useSessionStore()
  if (!session.ready) {
    try {
      await session.refresh()
    } catch {
      session.ready = true
    }
  }

  if (to.meta.public) {
    if (session.account) {
      return session.team ? '/' : '/join'
    }
    return true
  }

  if (!session.account) {
    return '/login'
  }

  if (!session.team) {
    return to.path === '/join' ? true : '/join'
  }

  if (to.path === '/join') {
    return '/'
  }

  return true
})

export default router
