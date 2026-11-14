import { createRouter, createWebHistory } from 'vue-router'
import { useSessionStore } from '../stores/session'
import { pickLayout, pickView } from './resolveUi'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      component: pickView(
        () => import('../views/LoginView.vue'),
        () => import('../views/v2/LoginView.vue'),
      ),
      meta: { public: true },
    },
    {
      path: '/register',
      component: pickView(
        () => import('../views/RegisterView.vue'),
        () => import('../views/v2/RegisterView.vue'),
      ),
      meta: { public: true },
    },
    {
      path: '/join',
      component: pickView(
        () => import('../views/JoinTeamView.vue'),
        () => import('../views/v2/JoinTeamView.vue'),
      ),
    },
    {
      path: '/',
      component: pickLayout(
        () => import('../layouts/AppLayout.vue'),
        () => import('../layouts/v2/AppLayoutRoot.vue'),
      ),
      meta: { team: true },
      children: [
        {
          path: '',
          name: 'workbench',
          component: pickView(
            () => import('../views/WorkbenchView.vue'),
            () => import('../views/v2/WorkbenchView.vue'),
          ),
        },
        {
          path: 'projects',
          name: 'projects',
          component: pickView(
            () => import('../views/ProjectsView.vue'),
            () => import('../views/v2/ProjectsView.vue'),
          ),
        },
        {
          path: 'projects/:projectId',
          name: 'project',
          component: pickView(
            () => import('../views/ProjectDetailView.vue'),
            () => import('../views/v2/ProjectDetailView.vue'),
          ),
        },
        {
          path: 'projects/:projectId/pages',
          name: 'project-pages',
          component: () => import('../views/ProjectPagesView.vue'),
        },
        {
          path: 'projects/:projectId/planning',
          name: 'project-planning',
          component: () => import('../views/PlanningView.vue'),
        },
        {
          path: 'tasks/:taskId',
          name: 'task',
          component: pickView(
            () => import('../views/TaskDetailView.vue'),
            () => import('../views/v2/TaskDetailView.vue'),
          ),
        },
        {
          path: 'calendar',
          name: 'calendar',
          component: () => import('../views/TaskCalendarView.vue'),
        },
        {
          path: 'directory',
          name: 'directory',
          component: () => import('../views/DirectoryView.vue'),
        },
        {
          path: 'notifications',
          name: 'notifications',
          component: pickView(
            () => import('../views/NotificationsView.vue'),
            () => import('../views/v2/NotificationsView.vue'),
          ),
        },
        {
          path: 'settings',
          name: 'settings',
          component: pickView(
            () => import('../views/SettingsView.vue'),
            () => import('../views/v2/SettingsView.vue'),
          ),
        },
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
